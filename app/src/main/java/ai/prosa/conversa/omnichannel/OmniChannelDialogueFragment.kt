package ai.prosa.conversa.omnichannel

import ai.prosa.conversa.R
import ai.prosa.conversa.common.*
import ai.prosa.conversa.common.api.ConversaApi
import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.common.data.model.UserInfo
import ai.prosa.conversa.common.utils.FileUtils
import ai.prosa.conversa.common.utils.Random
import ai.prosa.conversa.common.utils.TextUtils
import ai.prosa.conversa.common.utils.ViewUtils
import ai.prosa.conversa.databinding.ConversaFragmentOmnichannelDialogueBinding
import ai.prosa.conversa.inapp.isSameDay
import ai.prosa.conversa.inapp.utils.CreateTemplateMessage
import ai.prosa.conversa.omnichannel.core.OmniChannel
import ai.prosa.conversa.omnichannel.data.adapter.OmniChannelItemAdapter
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessage
import ai.prosa.conversa.omnichannel.data.model.*
import ai.prosa.conversa.omnichannel.data.sharedPreferences.OmniChannelPreferenceHelper
import ai.prosa.conversa.omnichannel.viewmodels.OmniChannelViewModel
import ai.prosa.conversa.omnichannel.viewmodels.OmniChannelViewModelFactory
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.*
import android.content.Context.LOCATION_SERVICE
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.hbisoft.pickit.PickiT
import com.hbisoft.pickit.PickiTCallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.*


const val FILE_INTENT_ID = 3

// The flow is based on https://app.diagrams.net/#G1-xkIkgDXd0UEJd7SsiZ6gqZyISf6nF9v
class OmniChannelDialogueFragment : Fragment(), LocationListener {
    private var _binding: ConversaFragmentOmnichannelDialogueBinding? = null
    private val binding get() = _binding!!
    val args: OmniChannelDialogueFragmentArgs by navArgs()

    private val vm: OmniChannelViewModel by activityViewModels {
        OmniChannelViewModelFactory(OmniChannel.repository)
    }

    private val uiMessages = OmniChannelItemAdapter()

    // Replying chat
    private var isReplying: MutableLiveData<Boolean> = MutableLiveData(false)
    private var repliedMessage: OmniChannelMessage? = null

    // Reviewing file
    private var usePreview = false
    private var isReviewingAttachment: MutableLiveData<Boolean> = MutableLiveData(false)
    private var isReviewingImage: MutableLiveData<Boolean> = MutableLiveData(false)

    private var reviewedFile: File? = null
    private var reviewedImage: File? = null

    // File uri getter
    private lateinit var filePicker: PickiT

    // Upload image
    private lateinit var currentPhotoAbsolutePath: String
    private lateinit var uploadImageDialog: AlertDialog

    // Permissions
    private lateinit var imageRequestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var fileRequestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var writeExternalStoragePermissionLauncher: ActivityResultLauncher<String>

    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadRequest: DownloadManager.Request

    private var userInfo: UserInfo? = null

    private lateinit var pref: OmniChannelPreferenceHelper
    private lateinit var cache: OmniChannelCache


    private val deleteCallback = { message: OmniChannelMessage, position: Int ->
        val serverChatID = if (message.id == message.clientChatId) {
            vm.clientServerMappings[message.id]
        } else {
            message.id
        }

        if (repliedMessage != null && (message.id == repliedMessage!!.clientChatId || message.id == repliedMessage!!.id)) {
            isReplying.postValue(false)
            repliedMessage = null
        }


        Log.d(TAG, "onViewCreated: <DELETE> ${message.text} $serverChatID")
        if (serverChatID != null) {
            ConversaApi.deleteChat(cache.accessToken, serverChatID) {
                val newList = uiMessages.currentList.toMutableList()
                Log.d(TAG, "DELETED: $position")

                if (message.id == message.clientChatId) {
                    vm.softDeleteClientID(message.clientChatId)
                } else {
                    vm.softDeleteServerID(message.id)
                }
                newList[position] =
                    (newList[position] as OmniChannelItemModel.SentMessage).softDelete()

                uiMessages.submitList(newList)
            }
        }
    }

    private val replyCallback = { message: OmniChannelMessage ->
        repliedMessage = message
        binding.repliedChatName.text = message.name
        binding.repliedChatText.text = TextUtils.cleanHtmlTag(message.text)
        isReplying.value = true
    }

    private val attachmentCallback = { uri: Uri, name: String, extension: String ->
        Log.d(TAG, "applyMessageView: [DOWNLOAD]")
        val downloadManager = ContextCompat.getSystemService(
            requireContext(),
            DownloadManager::class.java
        )
        downloadRequest = DownloadManager.Request(uri).apply {
            setTitle(name)
            setDescription("Downloading")
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver: ContentResolver = requireActivity().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, EXTENSION_MIME_MAPPING[extension]!!)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val fileUri = FileUtils.getUri(
                FileUtils.getFile(
                    requireContext(),
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                )
            )
            val file = File(fileUri!!.path!!)

//            downloadRequest.setDestinationUri(fileUri)
//            downloadManager!!.enqueue(downloadRequest)

        } else {
            // TODO: handle other android version
        }
    }

    private val copyCallback = { message: OmniChannelMessage ->
        val clipboard: ClipboardManager? =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("label", message.text)
        clipboard?.setPrimaryClip(clip)
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val location = getLocation()
        val cityName = if (location == null) {
            "Others"
        } else {
            getCityName(location.latitude, location.longitude)
        }
        vm.socketConnect(OmniChannel.accessToken, cityName)

        pref = OmniChannelPreferenceHelper(requireContext())
        cache = pref.getCache()

        filePicker = PickiT(requireContext(), object : PickiTCallbacks {
            override fun PickiTonUriReturned() {}
            override fun PickiTonStartListener() {}
            override fun PickiTonProgressUpdate(progress: Int) {}

            override fun PickiTonCompleteListener(
                path: String?,
                wasDriveFile: Boolean,
                wasUnknownProvider: Boolean,
                wasSuccessful: Boolean,
                Reason: String?
            ) {
                // TODO: handle error
                if (wasSuccessful) {
                    val file = File(path!!)
                    lifecycleScope.launch(Dispatchers.IO) {
                        Log.d(TAG, "onActivityResult: [FILE] $file")
                        reviewedFile = file
                        isReviewingAttachment.postValue(true)
                    }
                }
            }
        }, requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ConversaFragmentOmnichannelDialogueBinding.inflate(inflater, container, false)
        val bundleArguments = arguments

        userInfo = bundleArguments?.getSerializable(USER_TAG) as UserInfo?

        if (userInfo == null) {
            userInfo = args.userInfo
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val location = getLocation()

        binding.chatRecView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        binding.chatRecView.adapter = uiMessages
        binding.chatRecView.itemAnimator?.changeDuration = 0
        binding.chatRecView.itemAnimator?.addDuration = 0

        binding.editMessageInput.doAfterTextChanged {
            if (it!!.trim().isNotEmpty() || isReviewingAttachment.value!!) {
                binding.btnSend.iconTint =
                    ColorStateList.valueOf(resources.getColor(R.color.light_message_button_fg))
            } else {
                binding.btnSend.iconTint =
                    ColorStateList.valueOf(resources.getColor(R.color.light_message_button_fg_disabled))
            }
        }

        binding.btnSend.setOnClickListener {
            val input = binding.editMessage.editText?.text.toString()
            if (isReviewingAttachment.value!!) {
                lifecycleScope.launch(Dispatchers.IO) {
                    sendAttachment(reviewedFile!!)
                }
            } else if (usePreview && isReviewingImage.value!! && reviewedImage != null) {
                sendImage(reviewedImage!!)
            } else if (input.isNotBlank()) {
                sendChat(input.trim())
            }
        }

        isReplying.observe(viewLifecycleOwner) {
            if (it) {
                binding.extension.visibility = View.VISIBLE
                binding.replyExtension.visibility = View.VISIBLE
                binding.editMessageInput.requestFocus()
                ViewUtils.showKeyboard(requireActivity())
            } else {
                repliedMessage = null
                binding.extension.visibility = View.GONE
                binding.replyExtension.visibility = View.GONE
            }
        }

        isReviewingImage.observe(viewLifecycleOwner) {
            if (it) {
                if (usePreview) {
                    binding.reviewedImageFilename.text = reviewedImage!!.name
                    // TODO: refactor, same with attachment
                    val filesizeKB = reviewedImage!!.sizeInKb
                    if (filesizeKB >= 1024) {
                        binding.reviewedImageFilesize.text =
                            getString(
                                R.string.file_size,
                                "%.1f".format(reviewedImage!!.sizeInMb),
                                "Mb"
                            )
                    } else {
                        binding.reviewedImageFilesize.text =
                            getString(R.string.file_size, "%.0f".format(filesizeKB), "Kb")
                    }
                    binding.imageReviewImage.setImageURI(reviewedImage!!.toUri())
                    binding.imageReview.visibility = View.VISIBLE
                } else {
                    binding.imageReview.visibility = View.GONE
                    sendImage(reviewedImage!!)
                }
            } else {
                binding.imageReview.visibility = View.GONE
            }
        }

        isReviewingAttachment.observe(viewLifecycleOwner) {
            if (it) {
                binding.reviewedFilename.text = reviewedFile!!.name
                val filesizeKB = reviewedFile!!.sizeInKb
                if (filesizeKB >= 1024) {
                    binding.reviewedFilesize.text =
                        getString(R.string.file_size, "%.1f".format(reviewedFile!!.sizeInMb), "Mb")
                } else {
                    binding.reviewedFilesize.text =
                        getString(R.string.file_size, "%.0f".format(filesizeKB), "Kb")
                }
                val extension = FileUtils.getExtension(reviewedFile!!.toURI().toString())!!
                Log.d(TAG, "onViewCreated: [Extension] $extension")

                EXTENSION_ICON_MAPPING[extension]?.let { icon ->
                    binding.fileIcon.setImageResource(icon)
                }

                binding.attachmentReview.visibility = View.VISIBLE
                binding.btnSend.iconTint =
                    ColorStateList.valueOf(resources.getColor(R.color.light_message_button_fg))
            } else {
                binding.attachmentReview.visibility = View.GONE
                if (binding.editMessageInput.text.toString().isBlank()) {
                    binding.btnSend.iconTint =
                        ColorStateList.valueOf(resources.getColor(R.color.light_message_button_fg_disabled))
                }
            }
        }
        binding.cancelReviewBtn.setOnClickListener {
            reviewedFile = null
            isReviewingAttachment.postValue(false)
        }

        binding.cancelReplyBtn.setOnClickListener {
            repliedMessage = null
            isReplying.postValue(false)
        }

        vm.session.observe(viewLifecycleOwner) { session ->
            Log.d(TAG, "onViewCreated: [${session}] [${pref.getSession()}]")

            if (session != pref.getSession()) {
                // Changed session
                pref.setSession(session)
                vm.deleteAllChat()
                uiMessages.submitList(listOf())
            }
        }

        vm.newChats.observe(viewLifecycleOwner) { newChats ->
            newChats.forEach { chat ->
                addItemToAdapter(
                    OmniChannelItemModel.ReceivedMessage(
                        chat,
                        replyCallback,
                        attachmentCallback,
                        copyCallback
                    )
                )
                scrollToBottom()
            }
        }

        vm.history.observe(viewLifecycleOwner) { history ->
            Log.d(TAG, "onViewCreated: [HISTORY] ${history.size}")
            if (history.isEmpty()) {
                uiMessages.submitList(listOf<OmniChannelItemModel>())
                return@observe
            }

            var messagesCount = 0
            uiMessages.currentList.forEach {
                if (it is OmniChannelItemModel.SentMessage || it is OmniChannelItemModel.ReceivedMessage) {
                    messagesCount += 1
                }
            }

            val today = Date()
            var lastDate = "January 12, 1990 10:47:25".toDate(requireContext())!!
            val includeDifferentDay = !history[0].timestamp.isSameDay(today)

            val msgs = mutableListOf<OmniChannelItemModel>()
            history.forEach {
                // Add date separator
                if (includeDifferentDay) {
                    val msgTimestamp = it.timestamp
                    if (!msgTimestamp.isSameDay(lastDate)) {
                        if (msgTimestamp.isSameDay(today)) {
                            lastDate = today
                            msgs.add(OmniChannelItemModel.SystemNotification("Today"))
                        } else {
                            lastDate = msgTimestamp
                            msgs.add(
                                OmniChannelItemModel.SystemNotification(
                                    msgTimestamp.formatLocaleDatetime(
                                        requireContext(),
                                        "dd MMMM yyyy"
                                    )
                                )
                            )
                        }
                    }
                }

                if (it.direction == MessageDirection.SEND) {
                    msgs.add(
                        OmniChannelItemModel.SentMessage(
                            it,
                            replyCallback,
                            attachmentCallback,
                            deleteCallback,
                            copyCallback
                        )
                    )
                } else {
                    msgs.add(
                        OmniChannelItemModel.ReceivedMessage(
                            it,
                            replyCallback,
                            attachmentCallback,
                            copyCallback
                        )
                    )
                }
            }

            uiMessages.submitList(null)
            uiMessages.submitList(msgs.toList())

            vm.systemNotifications.observe(viewLifecycleOwner) {
                it.forEach { notification ->
                    if (notification is SystemNotification.HandledSession) {
                        if (notification.isFirstNotification) {
                            addItemToAdapter(
                                OmniChannelItemModel.SystemNotification(
                                    "Agent ${notification.name} is entering the chat"
                                )
                            )
                        } else {
                            addAgentConnectedNotification(notification.name)
                        }
                    } else if (notification is SystemNotification.DoneSession) {
                        addItemToAdapter(
                            OmniChannelItemModel.SystemNotification(
                                "Agent ${notification.name} has resolved this conversation"
                            )
                        )
                        Log.d(TAG, "onViewCreated: [DONE] $notification")
                    }
                }
                vm.resetSystemNotifications()
            }
            scrollToBottom(700)
        }

        vm.unreadChats.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                vm.readUnreadChats(it)
            }
        }

        vm.chatStateUpdate.observe(viewLifecycleOwner) {
            val newList = mutableListOf<OmniChannelItemModel>()

            uiMessages.currentList.forEach { message ->
                if (message is OmniChannelItemModel.SentMessage && message.message.clientChatId == it.clientChatID) {
                    val newMessage = message.message.copy(state = it.state)
                    val newItem = OmniChannelItemModel.SentMessage(
                        newMessage,
                        message.replyCallback,
                        message.attachmentCallback,
                        message.deleteCallback,
                        message.copyCallback
                    )
                    newList.add(newItem)
                } else {
                    newList.add(message)
                }
            }
            uiMessages.submitList(newList.toList())
        }

        setupFileUploader()
        setUpImageUploader()
    }

    private fun sendChat(
        msg: String,
        dryRun: Boolean = false,
        id: String = ""
    ): OmniChannelMessage {
        var clientChatId = Random.randomInteger()
        Log.d(TAG, "sendChat: [CLIENT CHAT] $clientChatId")
        if (id.isNotEmpty()) {
            clientChatId = id
        }

        val chat = OmniChannelMessage(
            id = clientChatId,
            text = msg,
            timestamp = Date(),
            name = userInfo!!.name,
            source = OmniChannelMessageSource.USER,
            direction = MessageDirection.SEND,
            state = OmniChannelMessageState.NOT_SENT,
            clientChatId = clientChatId,
            replyTo = null,
            isDeleted = false
        )

        if (isReplying.value!!) {
            // TODO: sanity check
            if (repliedMessage?.id == repliedMessage?.clientChatId) {
                chat.replyTo = vm.clientServerMappings[repliedMessage?.clientChatId]
            } else {
                chat.replyTo = repliedMessage?.id
                isReplying.value = false
            }
        }

        val newMessages = mutableListOf<OmniChannelItemModel>()
        if (uiMessages.currentList.isNotEmpty()) {
            uiMessages.currentList.last()?.let { last ->
                if (last is OmniChannelItemModel.SentMessage && !last.message.timestamp.isSameDay(
                        Date()
                    )
                ) {
                    newMessages.add(OmniChannelItemModel.SystemNotification("Today"))
                } else if (last is OmniChannelItemModel.ReceivedMessage && !last.message.timestamp.isSameDay(
                        Date()
                    )
                ) {
                    newMessages.add(OmniChannelItemModel.SystemNotification("Today"))
                } else {

                }
            }
        }

        val message = OmniChannelItemModel.SentMessage(
            chat,
            replyCallback,
            attachmentCallback,
            deleteCallback,
            copyCallback
        )
        if (id != clientChatId) {
            newMessages.add(message)
        }
        if (newMessages.isNotEmpty()) {
            addItemsToAdapter(newMessages)
        }

        binding.editMessage.editText?.setText("")

        if (msg.trim().lowercase() == "logout") {
            vm.logout(requireContext())

            val action =
                OmniChannelDialogueFragmentDirections.actionOmniChannelChatToOmniChannelLoginFragment()
            requireView().findNavController().navigate(action)
        } else {
            isReplying.postValue(false)
            repliedMessage = null

            if (!dryRun) {
                vm.chat(chat)
            }
        }
        if (id != clientChatId) {
            scrollToBottom(600)
        }
        return chat
    }

    private fun sendImage(imageFile: File) {
        val chat = sendChat(CreateTemplateMessage.withImage(imageFile.path), dryRun = true)
        lifecycleScope.launch(Dispatchers.IO) {
            ConversaApi.uploadFile(
                requireContext(),
                cache.accessToken,
                imageFile,
                compress = true
            ) { url ->
                sendChat(CreateTemplateMessage.withImage(url), id = chat.clientChatId)
                isReviewingImage.postValue(false)
                reviewedImage = null
            }
        }
    }

    private fun sendAttachment(attachmentFile: File) {
        reviewedFile = null
        isReviewingAttachment.postValue(false)

        val chat = sendChat(
            CreateTemplateMessage.withAttachment(attachmentFile.path, attachmentFile.name),
            dryRun = true
        )
        lifecycleScope.launch(Dispatchers.IO) {
            ConversaApi.uploadFile(
                requireContext(),
                cache.accessToken,
                attachmentFile,
                compress = false
            ) { url ->
                sendChat(
                    CreateTemplateMessage.withAttachment(url, attachmentFile.name),
                    id = chat.clientChatId
                )
            }
        }
    }

    private fun launchImageSelectorDialog() {
        val (builder, dialogView) = ViewUtils.createDialog(
            requireContext(),
            requireView(),
            R.layout.conversa_dialogue_image_upload
        )

        val takePhoto: ConstraintLayout? = dialogView.findViewById(R.id.photo)
        takePhoto?.setOnClickListener {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(requireActivity().packageManager)?.also {
                    val photoFile = try {
                        createImageFile()
                    } catch (ex: IOException) {
                        // Error occurred while creating the File
                        null
                    }

                    photoFile?.also { file ->
                        val photoUri: Uri = FileProvider.getUriForFile(
                            requireActivity(),
                            "ai.prosa.conversa.provider",
                            file
                        )
                        currentPhotoAbsolutePath = photoFile.absolutePath
                        takePictureIntent.putExtra(
                            MediaStore.EXTRA_OUTPUT,
                            photoUri
                        )
                        startActivityForResult(takePictureIntent, CAMERA_INTENT_ID)
                    }
                }
            }
        }

        val chooseGallery: ConstraintLayout? = dialogView.findViewById(R.id.gallery)
        chooseGallery?.setOnClickListener {
            val pickPhoto =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickPhoto, GALLERY_INTENT_ID)
        }

        uploadImageDialog = builder.show().also {
            it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.resizeToMaxWidth(0.7)
        }
    }

    private fun setUpImageUploader() {
        binding.btnUploadImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                launchImageSelectorDialog()
            } else {
                Log.d(TAG, "setUpImageUploader: ${imageRequestPermissionLauncher}")
                imageRequestPermissionLauncher.launch(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    private fun launchFileSelectorDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                EXTENSION_MIME_MAPPING.values.toTypedArray()
            )
        }
        startActivityForResult(intent, FILE_INTENT_ID)
    }

    private fun setupFileUploader() {
        binding.btnUploadDocument.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                launchFileSelectorDialog()
            } else {
                fileRequestPermissionLauncher.launch(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    override fun onAttach(context: Context) {
        imageRequestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    launchImageSelectorDialog()
                }
            }

        fileRequestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    launchFileSelectorDialog()
                }
            }

        writeExternalStoragePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                downloadManager.enqueue(downloadRequest)
            }
        }

        super.onAttach(context)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_CANCELED) {
            when (requestCode) {
                // Camera
                CAMERA_INTENT_ID -> if (resultCode == Activity.RESULT_OK) {
                    uploadImageDialog.dismiss()
                    reviewedImage = File(currentPhotoAbsolutePath)
                    Log.d(TAG, "onActivityResult: [FILE CAMERA] $reviewedImage")
                    isReviewingAttachment.postValue(false)
                    isReviewingImage.postValue(true)
                }

                // Photo
                GALLERY_INTENT_ID -> if (resultCode == Activity.RESULT_OK && data != null) {
                    uploadImageDialog.dismiss()
                    // TODO: fix this part: -> what to do when app first time asking for permission
                    // Granted
                    reviewedImage = FileUtils.getFile(requireContext(), data.data)
                    Log.d(TAG, "onActivityResult: [FILE GALLERY] $reviewedImage")
                    isReviewingAttachment.postValue(false)
                    isReviewingImage.postValue(true)
                }

                // File
                FILE_INTENT_ID -> if (resultCode == Activity.RESULT_OK && data != null) {
                    filePicker.getPath(data.data!!, Build.VERSION.SDK_INT)
                    isReviewingImage.postValue(false)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String =
            Date().formatLocaleDatetime(requireContext(), "yyyyMMdd_HHmmss")
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, "JPEG_${timeStamp}_" + ".jpg")
    }

    private fun addItemToAdapter(item: OmniChannelItemModel) {
        val currentList = uiMessages.currentList.toMutableList()
        currentList.add(item)
        uiMessages.submitList(currentList.toList())
    }

    private fun addItemsToAdapter(items: List<OmniChannelItemModel>) {
        val currentList = uiMessages.currentList.toMutableList()
        currentList.addAll(items)
        uiMessages.submitList(currentList.toList())
    }

    private fun addAgentConnectedNotification(name: String) {
        val data = OmniChannelItemModel.SystemNotification("You're connected with Agent $name")

        val currentList = uiMessages.currentList.toMutableList()
        Log.d(TAG, "addAgentConnectedNotification: [${currentList.size}]")
        var i = 0
        var isWaiting = false
        for (msg in currentList.reversed()) {
            if (msg is OmniChannelItemModel.ReceivedMessage) {
                if (msg.message.source == OmniChannelMessageSource.AGENT && msg.message.name == name) {
                    isWaiting = true
                } else if (isWaiting) {
                    currentList.add(
                        currentList.size - i, data
                    )
                    isWaiting = true
                    break
                } else if (msg.message.source == OmniChannelMessageSource.SYSTEM) {
                    currentList.add(
                        currentList.size - i, data
                    )
                    isWaiting = true
                    break
                }
            } else if (msg is OmniChannelItemModel.SystemNotification && msg.message == data.message) {
                break
            }
            i += 1
        }
        Log.d("OmniChannelItemAdapter", "addDataAfterAgent: $i $isWaiting [$name]")

        if (!isWaiting) {
            currentList.add(data)
        }

        uiMessages.submitList(currentList)
    }

    private fun scrollToBottom(delay: Long = 1000) {
        if (uiMessages.itemCount > 2) {
            binding.chatRecView.postDelayed({
                try {
                    binding.chatRecView.smoothScrollToPosition(uiMessages.itemCount - 1)
                } catch (e: IllegalArgumentException) {
                }

            }, delay)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.socketDisconnect()
        filePicker.deleteTemporaryFile(requireContext())
    }

    private fun getCityName(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses: List<Address> =
                geocoder.getFromLocation(latitude, longitude, 1)
            val subAdmin = addresses[0].subAdminArea
            val admin = addresses[0].adminArea

            val cityName = when {
                """\bJakarta\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Jakarta"
                }
                """\bBandung\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Bandung"
                }
                """\bMedan\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Medan"
                }
                """\bPadang\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Padang"
                }
                """\bPalembang\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Palembang"
                }
                """\bBatam\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Batam"
                }
                """\bPekanbaru\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Pekanbaru"
                }
                """\bCilegon\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Cilegon"
                }
                """\bSemarang\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Semarang"
                }
                """\bSurabaya\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Surabaya"
                }
                """\bManado\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Manado"
                }
                """\bMakassar\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Makassar"
                }
                """\bLombok\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Lombok"
                }
                """\bBali\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Bali"
                }
                """\bBangka Belitung\b""".toRegex().containsMatchIn("$subAdmin $admin") -> {
                    "Bangka Belitung"
                }
                else -> {
                    "Other"
                }
            }
            return cityName
        } catch (e: java.io.IOException) {
            // TODO: java.io.IOException: grpc failed
            return "Other"
        }
    }

    private fun getLocation(): Location? {
        // The minimum distance to change Updates in meters
        val MIN_DISTANCE_CHANGE_FOR_UPDATES = 10f // 10 meters

        // The minimum time between updates in milliseconds
        val MIN_TIME_BW_UPDATES: Long = 1000 * 60 * 1 // 1 minute

        var location: Location? = null

        try {
            val locationManager =
                requireContext().getSystemService(LOCATION_SERVICE) as LocationManager

            // getting GPS status
            val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (!isGPSEnabled) {
                // no network provider is enabled
            } else {
                // if GPS Enabled get lat/long using GPS Services
                if (isGPSEnabled) {
                    if (location == null) {
                        //check the network permission
                        if (ActivityCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                requireActivity(), arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ), 101
                            )
                        }
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this
                        )
                        Log.d("GPS Enabled", "GPS Enabled")
                        location = locationManager
                            .getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return location
    }


    companion object {
        private const val TAG = "OmniChannelChat"
        const val USER_TAG = "USER_TAG"
    }

    override fun onLocationChanged(p0: Location) {
        // Log.d(TAG, "onLocationChanged: $p0")
    }
}