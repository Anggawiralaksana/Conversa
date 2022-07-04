package ai.prosa.conversa.inapp

import ai.prosa.conversa.MainActivity
import ai.prosa.conversa.R
import ai.prosa.conversa.common.CAMERA_INTENT_ID
import ai.prosa.conversa.common.GALLERY_INTENT_ID
import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.common.data.model.UserInfo
import ai.prosa.conversa.common.formatLocaleDatetime
import ai.prosa.conversa.common.resizeToMaxWidth
import ai.prosa.conversa.common.utils.ImageUtils
import ai.prosa.conversa.common.utils.ViewUtils
import ai.prosa.conversa.databinding.ConversaFragmentInappDialogueBinding
import ai.prosa.conversa.inapp.core.InappChat
import ai.prosa.conversa.inapp.data.adapter.InappItemAdapter
import ai.prosa.conversa.inapp.data.model.InappItemModel
import ai.prosa.conversa.inapp.data.model.MessageType
import ai.prosa.conversa.inapp.data.model.RoomMessageState
import ai.prosa.conversa.inapp.data.sharedPreferences.InappPreferenceHelper
import ai.prosa.conversa.inapp.utils.AudioProcessingInterface
import ai.prosa.conversa.inapp.utils.AudioRecording
import ai.prosa.conversa.inapp.viewmodels.InappViewModel
import ai.prosa.conversa.inapp.viewmodels.InappViewModelFactory
import ai.prosa.conversa.inapp.views.resetSelectedTemplate
import ai.prosa.conversa.inapp.views.setupMessageTemplates
import android.Manifest
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jivesoftware.smackx.chatstates.ChatState
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*


fun Date.toCalendar(): Calendar {
    val calendar = Calendar.getInstance()
    calendar.time = this
    return calendar
}

fun Date.isSameDay(other: Date): Boolean {
    return this.toCalendar().isSameDay(other.toCalendar())
}

fun Calendar.isSameDay(other: Calendar): Boolean {
    return (
            this.get(Calendar.YEAR) == other.get(Calendar.YEAR)
                    && this.get(Calendar.MONTH) == other.get(Calendar.MONTH)
                    && this.get(Calendar.DAY_OF_MONTH) == other.get(Calendar.DAY_OF_MONTH))
}

class InappDialogueFragment : Fragment() {
    private var _binding: ConversaFragmentInappDialogueBinding? = null
    val binding get() = _binding!!
    val args: InappDialogueFragmentArgs by navArgs()

    private lateinit var recordAudioPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var audioRecording: AudioRecording

    var isDriver = false
    var isDriving = false

    private var isTyping = false

    private var user1: UserInfo? = null
    private var user2: UserInfo? = null

    val vm: InappViewModel by activityViewModels {
        InappViewModelFactory(
            InappChat.room!!,
            InappChat.repository,
            InappChat.templateRepository
        )
    }

    private val messageAdapter = InappItemAdapter()
    val templateMessageAdapter = GroupAdapter<GroupieViewHolder>()
    var selectedTemplateIndex = -1

    private var isRecording = false
    private lateinit var currentPhotoAbsolutePath: String

    private lateinit var uploadImageDialog: AlertDialog

    companion object {
        const val USER_1_TAG = "USER_1_TAG"
        const val USER_2_TAG = "USER_2_TAG"

        private const val TAG = "ConversaInappDialogue"
    }

    override fun onAttach(context: Context) {
        recordAudioPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    audioRecording.start()
                }
            }
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ConversaFragmentInappDialogueBinding.inflate(inflater, container, false)

        val bundleArguments = arguments

        user1 = bundleArguments?.getSerializable(USER_1_TAG) as UserInfo?
        user2 = bundleArguments?.getSerializable(USER_2_TAG) as UserInfo?

        if (user1 == null || user2 == null) {
            user1 = args.userInfo1
            user2 = args.userInfo2
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val room = InappChat.room!!
        isDriver = !user1!!.customizableMessageTemplate

        vm.init()

        // Toolbar
        binding.toolbarTitle.text = user2!!.name
        binding.toolbarSubTitle.text = user2!!.subtitle
        Glide.with(view)
            .asBitmap()
            .load(user1!!.avatarUrl)
            .circleCrop()
            .placeholder(R.drawable.conversa_ic_avatar)
            .error(R.drawable.conversa_ic_avatar)
            .into(binding.toolbarImage)

        binding.chatRecView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        binding.chatRecView.adapter = messageAdapter


        lifecycleScope.launch(Dispatchers.IO) {
            val it = room.getHistory(false)
            requireActivity().runOnUiThread {
                val history = it.cachedHistory
                Log.d(TAG, "onViewCreated: [History] ${history.size}")

                val msgs = mutableListOf<InappItemModel>()
                var currentDate: Calendar? = null

                for (m in history) {
                    if (msgs.isEmpty() || !m.timestamp.toCalendar().isSameDay(currentDate!!)) {
                        msgs.add(InappItemModel.DateMessage(m.timestamp))
                        currentDate = m.timestamp.toCalendar()
                    }
                    when (m.direction) {
                        MessageDirection.RECEIVE -> {
                            msgs.add(InappItemModel.ReceivedMessage(m))
                        }
                        MessageDirection.SEND -> {
                            msgs.add(InappItemModel.SentMessage(m))
                        }
                        MessageDirection.EVENT -> {
                            msgs.add(InappItemModel.EventMessage(m))
                        }
                    }
                }
                messageAdapter.submitList(msgs)
                scrollToBottom(500)

                room.newMessages.observe(viewLifecycleOwner) { newMessages ->
                    if (newMessages.isEmpty()) {
                        return@observe
                    }

                    Log.d(TAG, "onViewCreated: [New Messages] ${newMessages.size}")

                    val messages = mutableListOf<InappItemModel>()
                    for (m in newMessages) {
                        val msg: InappItemModel = when(m.direction) {
                            MessageDirection.SEND -> {
                                InappItemModel.SentMessage(m)
                            }
                            MessageDirection.RECEIVE -> {
                                InappItemModel.ReceivedMessage(m)
                            }
                            MessageDirection.EVENT -> {
                                InappItemModel.EventMessage(m)
                            }
                        }
                        messages.add(msg)
                    }

                    addItems(messages)
                    scrollToBottom(500)

                    for (m in newMessages.filter { it.direction == MessageDirection.RECEIVE }) {
                        vm.markAs(m, MessageDirection.RECEIVE, RoomMessageState.READ)
                        // Set unread sent messages to read
                        vm.markAllAsRead(MessageDirection.SEND) { unreadMessages ->
                            unreadMessages.forEach { m ->
                                setMessagesItemState(m.id, RoomMessageState.READ)
                            }
                        }
                    }
                    room.newMessages.postValue(listOf())
                }
            }
        }

        room.newDate.observe(viewLifecycleOwner) {
            if (it != null) {
                addItem(InappItemModel.DateMessage(it))
            }
        }

        room.chatStateUpdate.observe(viewLifecycleOwner) {
            val newList = mutableListOf<InappItemModel>()
            Log.d(TAG, "onViewCreated: [chatStateUpdate] $it")

            messageAdapter.currentList.forEach { message ->
                if (message is InappItemModel.SentMessage && message.message.id == it.id) {
                    newList.add(InappItemModel.SentMessage(message.message.copy(state = it.state)))
                } else {
                    newList.add(message)
                }
            }
            messageAdapter.submitList(newList.toList())
        }

        room.disableChatUI.observe(viewLifecycleOwner) {
            if (!isRecording) {
                if (it) {
                    binding.audioOnlyMessagePanel.visibility = View.VISIBLE
                    binding.normalMessagePanel.visibility = View.GONE
                } else {
                    binding.audioOnlyMessagePanel.visibility = View.GONE
                    binding.normalMessagePanel.visibility = View.VISIBLE
                }
            }
        }

        vm.setMessageStateHandler { state, id ->
            setMessagesItemState(id, state)
        }

        vm.markAllAsRead(MessageDirection.RECEIVE) {}

        binding.chatRecView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        binding.chatRecView.adapter = messageAdapter

        if (room.disableChatUI.value!!) {
            binding.normalMessagePanel.visibility = View.GONE
            binding.audioOnlyMessagePanel.visibility = View.VISIBLE
        }

        setUpRecording()
        setUpImageUploader()
        setupMessageTemplates()

        binding.editMessageInput.doAfterTextChanged {
            if (it!!.trim().isNotEmpty()) {
                binding.btnSend.visibility = View.VISIBLE
                binding.audioButtons.visibility = View.GONE
            } else {
                binding.btnSend.visibility = View.GONE
                binding.audioButtons.visibility = View.VISIBLE
            }
        }

        binding.btnSend.setOnClickListener {
            val input = binding.editMessage.editText?.text.toString()
            if (input.isNotBlank()) {
                val text = binding.editMessage.editText?.text.toString().trim()
                isTyping = false
                if (text == "logout.prosa") {
                    logout()
                    try {
                        val action = InappDialogueFragmentDirections.actionInappDialogueToLogin()
                        view.findNavController().navigate(action)
                    } catch (e: Exception) {}
                } else {
                    vm.sendText(text) {
                        scrollToBottom(500)
                    }

                    binding.editMessage.editText?.setText("")
                    binding.editMessage.clearFocus()
                    this.resetSelectedTemplate()
                }
            }
        }

        binding.editMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length != 0) {
                    isTyping = true
                    vm.setState(ChatState.composing)
                } else {
                    isTyping = false
                    vm.setState(ChatState.active)
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        binding.back.setOnClickListener {
            activity?.onBackPressed()
        }

        room.setStateListener { username, state ->
            // TODO: when changing theme; this throw exceptions
            requireActivity().runOnUiThread {
                when (state) {
                    ChatState.composing -> {
                        binding.typingIndicatorText.text =
                            getString(R.string.typing_indicator_text, username)
                        binding.typingIndicator.visibility = View.VISIBLE
                    }

                    ChatState.active -> {
                        binding.typingIndicator.visibility = View.GONE
                        vm.markAllAsRead(MessageDirection.SEND) { unreadMessages ->
                            unreadMessages.forEach { um ->
                                setMessagesItemState(um.id, RoomMessageState.READ)
                            }
                        }
                    }

                    ChatState.inactive -> {
                        binding.typingIndicator.visibility = View.GONE
                        vm.markAllAsDelivered(MessageDirection.SEND) { undeliveredMessages ->
                            undeliveredMessages.forEach { um ->
                                setMessagesItemState(um.id, RoomMessageState.DELIVERED)
                            }
                        }
                    }
                    else -> {
                    }
                }
            }
        }
    }

    private fun setUpRecording() {
        audioRecording = AudioRecording(requireContext(), object : AudioProcessingInterface {
            override fun onStart() {
                binding.normalMessagePanel.visibility = View.VISIBLE
                binding.audioOnlyMessagePanel.visibility = View.GONE

                binding.chatContainer.visibility = View.GONE
                binding.recordingContainer.visibility = View.VISIBLE
                binding.btnStopRecording.visibility = View.VISIBLE

                binding.duration.base = SystemClock.elapsedRealtime()
                binding.duration.start()

                isRecording = true
            }

            override fun onSuccess(transcript: String) {
                vm.sendText(transcript) {}
            }

            override fun onFinishedRecording(filePath: String, millisecondsDuration: Int) {
                binding.chatContainer.visibility = View.VISIBLE
                binding.recordingContainer.visibility = View.GONE
                binding.duration.start()
                isRecording = false

                if (InappChat.room!!.disableChatUI.value!!) {
                    binding.audioOnlyMessagePanel.visibility = View.VISIBLE
                    binding.normalMessagePanel.visibility = View.GONE
                }

                val url =
                    lifecycleScope.launch {
                        vm.sendFile(
                            FileInputStream(filePath),
                            "Audio recording",
                            "audio.wav",
                            MessageType.AUDIO,
                            mapOf("duration" to millisecondsDuration.toString()),
                            filePath
                        )
                    }
                Log.d(
                    TAG,
                    "onFinishedRecording: Uploaded audio @(${url}) Duration($millisecondsDuration)"
                )
            }

            override fun onError(t: Throwable) {
                throw t
            }
        })

        val record = {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: fix this part: -> what to do when app first time asking for permission
                // Granted
                audioRecording.start()
            }
            //                shouldShowRequestPermissionRationale(...) -> {
            //                // In an educational UI, explain to the user why your app requires this
            //                // permission for a specific feature to behave as expected. In this UI,
            //                // include a "cancel" or "no thanks" button that allows the user to
            //                // continue using your app without granting the permission.
            //                showInContextUI(...)
            //            }
            else {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                recordAudioPermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }

        binding.btnRecord.setOnClickListener {
            record()
        }

        binding.btnRecordOnly.setOnClickListener {
            record()
        }

        binding.sendBtn.setOnClickListener {
            binding.duration.base = SystemClock.elapsedRealtime()
            binding.duration.stop()
            audioRecording.end()
        }

        binding.cancelRecordingBtn.setOnClickListener {
            if (InappChat.room!!.disableChatUI.value!!) {
                binding.audioOnlyMessagePanel.visibility = View.VISIBLE
                binding.normalMessagePanel.visibility = View.GONE
            }
            binding.duration.base = SystemClock.elapsedRealtime()
            binding.duration.stop()
            audioRecording.cancel()
            binding.chatContainer.visibility = View.VISIBLE
            binding.recordingContainer.visibility = View.GONE
            isRecording = false
        }
    }

    private fun setUpImageUploader() {
        binding.btnUploadImage.setOnClickListener {
            launchImageSelectorDialog()
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
                    }
                    currentPhotoAbsolutePath = photoFile!!.absolutePath
                    startActivityForResult(takePictureIntent, CAMERA_INTENT_ID)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_CANCELED) {
            when (requestCode) {
                // Camera
                CAMERA_INTENT_ID -> if (resultCode == RESULT_OK) {
                    uploadImageDialog.dismiss()

                    lifecycleScope.launch {
                        val stream = FileInputStream(
                            ImageUtils.compress(
                                requireContext(),
                                File(currentPhotoAbsolutePath)
                            )
                        )
                        val name =
                            currentPhotoAbsolutePath.substring(
                                currentPhotoAbsolutePath.lastIndexOf(
                                    '/'
                                ) + 1
                            )
                        vm.sendFile(
                            stream,
                            "Caption",
                            name,
                            MessageType.IMAGE,
                            mapOf(),
                            currentPhotoAbsolutePath
                        )
                    }
                }


                // Photo
                GALLERY_INTENT_ID -> if (resultCode == RESULT_OK && data != null) {
                    uploadImageDialog.dismiss()
                    // TODO: get file name from disk not uri
                    val uri = data.data

                    val stream = requireActivity().contentResolver.openInputStream(uri!!)
                    val name = uri.path!!.substring(uri.path!!.lastIndexOf('/') + 1)
                    if (stream != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            vm.sendFile(
                                stream,
                                "Caption",
                                name,
                                MessageType.IMAGE,
                                mapOf(),
                                uri.toString()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createIncomingMessageNotification(text: String) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Create Notification
            val builder =
                NotificationCompat.Builder(requireContext(), MainActivity.MESSAGE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.conversa_ic_group)
                    .setContentTitle("Receive Message")
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(requireContext())) {
                notify(1000, builder.build())
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

    private fun logout() {
        InappPreferenceHelper(requireContext()).clearCache()
        InappChat.clearUser()
    }

    private fun scrollToBottom(delay: Long = 1000) {
        if (messageAdapter.itemCount - 1 > 2) {
            binding.chatRecView.postDelayed({
                try {
                    binding.chatRecView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                } catch (e: IllegalArgumentException) {
                }

            }, delay)
        }
    }

    private fun addItem(item: InappItemModel) {
        val newList = mutableListOf<InappItemModel>()
        var replace = false
        messageAdapter.currentList.forEach {
            if (it is InappItemModel.SentMessage && item is InappItemModel.SentMessage && (item.message.id == it.message.id)) {
                newList.add(item)
                replace = true
            } else {
                newList.add(it)
            }
        }
        if (!replace) {
            newList.add(item)
        }

        requireActivity().runOnUiThread {
            messageAdapter.submitList(newList.toList())
        }
    }

    private fun addItems(items: List<InappItemModel>) {
        val newList = messageAdapter.currentList.toMutableList()
        newList.addAll(items)
        messageAdapter.submitList(newList.toList())

        requireActivity().runOnUiThread {
            messageAdapter.submitList(newList.toList())
        }
    }

    private fun setMessagesItemState(ids: Set<String>, state: RoomMessageState) {
        val newList = mutableListOf<InappItemModel>()
        messageAdapter.currentList.forEach {
            if (it is InappItemModel.SentMessage && ids.contains(it.message.id)) {
                newList.add(it.newState(state))
            } else {
                newList.add(it)
            }
        }
        requireActivity().runOnUiThread {
            messageAdapter.submitList(newList.toList())
        }
    }

    private fun setMessagesItemState(id: String, state: RoomMessageState) {
        val newList = mutableListOf<InappItemModel>()
        messageAdapter.currentList.forEach {
            if (it is InappItemModel.SentMessage && id == it.message.id) {
                newList.add(it.newState(state))
            } else {
                newList.add(it)
            }
        }
        requireActivity().runOnUiThread {
            messageAdapter.submitList(newList.toList())
        }
    }

    override fun onPause() {
        super.onPause()
        vm.setState(ChatState.inactive)
    }

    override fun onResume() {
        super.onResume()
        if (!isTyping) {
            vm.setState(ChatState.active)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}