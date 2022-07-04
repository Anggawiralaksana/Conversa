package ai.prosa.conversa.omnichannel.data.adapter

import ai.prosa.conversa.R
import ai.prosa.conversa.common.EXTENSION_ICON_MAPPING
import ai.prosa.conversa.common.formatLocaleDatetime
import ai.prosa.conversa.common.resizeToMaxWidth
import ai.prosa.conversa.common.utils.HtmlParser
import ai.prosa.conversa.common.utils.ImageUtils.showImage
import ai.prosa.conversa.common.utils.TextUtils
import ai.prosa.conversa.common.utils.ViewUtils
import ai.prosa.conversa.databinding.ConversaItemOmnichannelMessageSendBinding
import ai.prosa.conversa.databinding.ConversaItemOmnichannelSystemNotificationBinding
import ai.prosa.conversa.omnichannel.data.model.OmniChannelItemModel
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageState
import ai.prosa.conversa.omnichannel.data.remote.OmniChannelMessageTextRepository
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import ai.prosa.conversa.databinding.ConversaItemOmnichannelMessageReceiveBinding as ConversaItemOmnichannelMessageReceiveBinding1


const val SCREEN_MESSAGE_LENGTH_RATIO = 0.6
var IMAGE_WIDTH = 0
var TEXT_WIDTH = 0

fun ConversaItemOmnichannelMessageSendBinding.applyMessageView(
    item: OmniChannelItemModel.SentMessage,
    position: Int
) {
    this.isAttachment = false
    this.isImage = false
    this.isText = false

    val message = message!!
    if (HtmlParser.isAttachment(message.text)) {
        this.isAttachment = true
        this.isImage = false
        this.isText = false

        val uri = Uri.parse(HtmlParser.extractAttachmentUrl(message.text))
        val name = HtmlParser.extractAttachmentName(message.text)
        val extension = HtmlParser.extractExtension(name)

        EXTENSION_ICON_MAPPING[extension]?.let { icon ->
            this.attachmentIcon.setImageResource(icon)
        }
        this.attachmentText.text = name
        this.attachmentContainer.setOnClickListener {
            item.attachmentCallback(uri, name, extension)
        }
    } else if (HtmlParser.isImage(message.text)) {
        this.isAttachment = false
        this.isImage = true
        this.isText = false

        val src = HtmlParser.extractImageSrc(message.text)

        if (this.message!!.state == OmniChannelMessageState.NOT_SENT) {
            this.overlayImage.visibility = View.VISIBLE
            this.progress.visibility = View.VISIBLE
        } else {
            this.overlayImage.visibility = View.GONE
            this.progress.visibility = View.GONE
        }

        if (IMAGE_WIDTH == 0) {
            val screenWidth = ViewUtils.getScreenWidth(this.root.context)
            IMAGE_WIDTH = (SCREEN_MESSAGE_LENGTH_RATIO * screenWidth).toInt()
        }

        (this.image.layoutParams as ConstraintLayout.LayoutParams).apply {
            width = IMAGE_WIDTH
        }
        this.image.requestLayout()

        showImage(src, this.root, this.image)
    } else {
        this.isAttachment = false
        this.isImage = false
        this.isText = true

        if (HtmlParser.doesContainUrl(message.text)) {
            this.normalMessageText.setOnClickListener {
                val url = HtmlParser.extractUrl(message.text)
                if (url.isNotEmpty()) {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(url)
                    try {
                        root.context.startActivity(i)
                    } catch (e: ActivityNotFoundException) {
                        Log.d(
                            "OmniChannelItemAdapter",
                            "applyMessageView: [E|ActivityNotFoundException] Try to open $url"
                        )
                    }
                }
            }
        }

        this.normalMessageText.text = TextUtils.cleanHtmlTag(message.text)
    }

    this.btnMessageAction.setOnClickListener {
        val (builder, view) = ViewUtils.createDialog(
            this.root.context,
            this.root,
            R.layout.conversa_dialogue_omnichannel_message_action
        )
        builder.setCancelable(true)
        val dialog = builder.show().also {
            it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.resizeToMaxWidth(0.5)
        }

        view.findViewById<TextView>(R.id.deleteBtn).setOnClickListener {
            item.deleteCallback(message, position)
            dialog.cancel()
        }

        view.findViewById<TextView>(R.id.replyBtn).setOnClickListener {
            item.replyCallback(message)
            dialog.cancel()
        }

        if (this.isText!!) {
            view.findViewById<TextView>(R.id.copyBtn).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.copyBtn).setOnClickListener {
                item.copyCallback(message)
                dialog.cancel()
            }
        } else {
            view.findViewById<TextView>(R.id.copyBtn).visibility = View.GONE
        }
    }
}


fun ConversaItemOmnichannelMessageReceiveBinding1.applyMessageView(
    item: OmniChannelItemModel.ReceivedMessage,
    position: Int
) {
    this.isAttachment = false
    this.isImage = false
    this.isText = false

    val message = message!!
    if (HtmlParser.isAttachment(message.text)) {
        this.isAttachment = true
        this.isImage = false
        this.isText = false

        val uri = Uri.parse(HtmlParser.extractAttachmentUrl(message.text))
        val name = HtmlParser.extractAttachmentName(message.text)
        val extension = HtmlParser.extractExtension(name)

        EXTENSION_ICON_MAPPING[extension]?.let { icon ->
            this.attachmentIcon.setImageResource(icon)
        }
        this.attachmentText.text = name
        this.attachmentContainer.setOnClickListener {
            item.attachmentCallback(uri, name, extension)
        }
    } else if (HtmlParser.isImage(message.text)) {
        val src = HtmlParser.extractImageSrc(message.text)
        showImage(src, this.root, this.image)

        this.isAttachment = false
        this.isImage = true
        this.isText = false
    } else {
        this.isAttachment = false
        this.isImage = false
        this.isText = true

        if (HtmlParser.doesContainUrl(message.text)) {
            this.normalMessageText.setOnClickListener {
                val url = HtmlParser.extractUrl(message.text)
                if (url.isNotEmpty()) {
                    val i = Intent(Intent.ACTION_VIEW)
                    i.data = Uri.parse(url)
                    try {
                        root.context.startActivity(i)
                    } catch (e: ActivityNotFoundException) {
                        Log.d(
                            "OmniChannelItemAdapter",
                            "applyMessageView: [E|ActivityNotFoundException] Try to open $url"
                        )
                    }
                }
            }
        }

        this.normalMessageText.text = TextUtils.cleanHtmlTag(message.text)
    }
    this.btnMessageAction.setOnClickListener {
        val (builder, view) = ViewUtils.createDialog(
            this.root.context,
            this.root,
            R.layout.conversa_dialogue_omnichannel_message_action_received
        )
        builder.setCancelable(true)
        val dialog = builder.show().also {
            it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.resizeToMaxWidth(0.5)
        }

        view.findViewById<TextView>(R.id.replyBtn).setOnClickListener {
            item.replyCallback(message)
            dialog.cancel()
        }

        if (this.isText!!) {
            view.findViewById<TextView>(R.id.copyBtn).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.copyBtn).setOnClickListener {
                item.copyCallback(message)
                dialog.cancel()
            }
        } else {
            view.findViewById<TextView>(R.id.copyBtn).visibility = View.GONE
        }
    }
}

// get-nonce
//   - userid
//   > nonce
// register /inapp-chat
//   - jwt
//   - userid
//   > accessToken

class OmniChannelItemAdapter :
    ListAdapter<OmniChannelItemModel, OmniChannelItemAdapter.DataAdapterViewHolder>(
        OmniChannelItemDiffCallback()
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataAdapterViewHolder {

        when (viewType) {
            TYPE_SENT_MESSAGE -> {
                val viewBinding = ConversaItemOmnichannelMessageSendBinding.inflate(
                    LayoutInflater
                        .from(parent.context), parent, false
                )
                return DataAdapterViewHolder(viewBinding)
            }
            TYPE_RECEIVED_MESSAGE -> {
                val viewBinding = ConversaItemOmnichannelMessageReceiveBinding1.inflate(
                    LayoutInflater
                        .from(parent.context), parent, false
                )
                return DataAdapterViewHolder(viewBinding)
            }
            TYPE_SYSTEM_NOTIFICATION -> {
                val viewBinding = ConversaItemOmnichannelSystemNotificationBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                return DataAdapterViewHolder(viewBinding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }


    //-----------onBindViewHolder: bind view with data model---------
    override fun onBindViewHolder(holder: DataAdapterViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }


    // https://stackoverflow.com/questions/49726385/listadapter-not-updating-item-in-recyclerview
    override fun submitList(list: List<OmniChannelItemModel>?) {
        super.submitList(list?.let { ArrayList(it) })
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is OmniChannelItemModel.SentMessage -> TYPE_SENT_MESSAGE
            is OmniChannelItemModel.ReceivedMessage -> TYPE_RECEIVED_MESSAGE
            is OmniChannelItemModel.SystemNotification -> TYPE_SYSTEM_NOTIFICATION
            else -> -1
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    companion object {
        const val TYPE_SENT_MESSAGE = 0
        const val TYPE_RECEIVED_MESSAGE = 1
        const val TYPE_SYSTEM_NOTIFICATION = 2
    }

    // TODO: reduce binding load
    class DataAdapterViewHolder(private val viewBinding: ViewBinding) :
        RecyclerView.ViewHolder(viewBinding.root) {

        private fun calculateMaxWidth(): Int {
            return (SCREEN_MESSAGE_LENGTH_RATIO * ViewUtils.getScreenWidth(viewBinding.root.context)).toInt()
        }

        private fun bindSentMessage(item: OmniChannelItemModel.SentMessage, position: Int) {
            val viewBinding = viewBinding as ConversaItemOmnichannelMessageSendBinding

            viewBinding.repliedAttachmentContainer.visibility = View.GONE
            viewBinding.normalMessageText.background = ResourcesCompat.getDrawable(
                viewBinding.root.context.resources,
                R.drawable.conversa_bg_send_message,
                null
            )
            viewBinding.deletedMessage.visibility = View.GONE
            viewBinding.btnMessageAction.visibility = View.VISIBLE
            viewBinding.textMessage.visibility = View.VISIBLE

            if (TEXT_WIDTH == 0) {
                TEXT_WIDTH = calculateMaxWidth()
            }
            viewBinding.normalMessageText.maxWidth = TEXT_WIDTH
            viewBinding.repliedAttachmentContainer.maxWidth = TEXT_WIDTH
            viewBinding.deletedMessage.maxWidth = TEXT_WIDTH
            viewBinding.attachmentText.maxWidth = TEXT_WIDTH

            viewBinding.isRepliedAttachment = false
            viewBinding.isRepliedImage = false
            viewBinding.isRepliedText = false

            viewBinding.message = item.message
            when {
                item.message.isDeleted -> {
                    viewBinding.deletedMessage.text =
                        viewBinding.root.context.getString(
                            R.string.deleted_message_txt,
                            item.message.name
                        )
                    viewBinding.deletedMessage.visibility = View.VISIBLE

                    viewBinding.btnMessageAction.visibility = View.GONE
                    viewBinding.textMessage.visibility = View.GONE
                }
                item.message.replyTo != null -> {
                    viewBinding.normalMessageText.background = ResourcesCompat.getDrawable(
                        viewBinding.root.context.resources,
                        R.drawable.conversa_bg_rounded_bottom_send,
                        null
                    )
                    val repliedChat = OmniChannelMessageTextRepository.get(item.message.replyTo!!)

                    val repliedText = repliedChat!![0]

                    // TODO: attachment text is long, the text and icon will collide
                    // TODO: bug from BE, for welcome message
                    if (HtmlParser.isAttachment(repliedText)) {
                        viewBinding.isRepliedAttachment = true
                        viewBinding.isRepliedImage = false
                        viewBinding.isRepliedText = false

                        val uri = Uri.parse(HtmlParser.extractAttachmentUrl(repliedText))
                        val name = HtmlParser.extractAttachmentName(repliedText)
                        val extension = HtmlParser.extractExtension(name)

                        EXTENSION_ICON_MAPPING[extension]?.let { icon ->
                            viewBinding.repliedAttachmentIcon.setImageResource(icon)
                        }
                        viewBinding.repliedAttachmentText.text = name
                        viewBinding.repliedAttachmentContainer.setOnClickListener {
                            item.attachmentCallback(uri, name, extension)
                        }
                    } else if (HtmlParser.isImage(repliedText)) {
                        viewBinding.isRepliedAttachment = false
                        viewBinding.isRepliedImage = true
                        viewBinding.isRepliedText = false

                        val src = HtmlParser.extractImageSrc(repliedText)
                        if (IMAGE_WIDTH == 0) {
                            val screenWidth =
                                ViewUtils.getScreenWidth(this.viewBinding.root.context)
                            IMAGE_WIDTH = (SCREEN_MESSAGE_LENGTH_RATIO * screenWidth).toInt()
                        }

                        (viewBinding.repliedImage.layoutParams as ConstraintLayout.LayoutParams).apply {
                            width = IMAGE_WIDTH
                        }
                        viewBinding.repliedImage.requestLayout()

                        showImage(src, this.viewBinding.root, viewBinding.repliedImage)
                    } else {
                        viewBinding.isRepliedAttachment = false
                        viewBinding.isRepliedImage = false
                        viewBinding.isRepliedText = true

                        if (HtmlParser.doesContainUrl(repliedText)) {
                            viewBinding.repliedNormalMessageText.setOnClickListener {
                                val url = HtmlParser.extractUrl(repliedText)
                                if (url.isNotEmpty()) {
                                    val i = Intent(Intent.ACTION_VIEW)
                                    i.data = Uri.parse(url)
                                    try {
                                        viewBinding.root.context.startActivity(i)
                                    } catch (e: ActivityNotFoundException) {
                                        Log.d(
                                            "OmniChannelItemAdapter",
                                            "applyMessageView: [E|ActivityNotFoundException] Try to open $url"
                                        )
                                    }
                                }
                            }
                        }
                        viewBinding.repliedNormalMessageText.text =
                            TextUtils.cleanHtmlTag(repliedText)
                    }
                    viewBinding.repliedMessageContainer.visibility = View.VISIBLE
                    viewBinding.applyMessageView(item, position)
                }
                else -> {
                    viewBinding.applyMessageView(item, position)
                }
            }

            viewBinding.timestamp.text =
                item.message.timestamp.formatLocaleDatetime(viewBinding.root.context, "HH:mm")

        }


        private fun bindReceivedMessage(item: OmniChannelItemModel.ReceivedMessage) {
            val viewBinding = viewBinding as ConversaItemOmnichannelMessageReceiveBinding1

            viewBinding.repliedMessageText.visibility = View.GONE
            viewBinding.normalMessageText.background = ResourcesCompat.getDrawable(
                viewBinding.root.context.resources,
                R.drawable.conversa_bg_receive_message,
                null
            )
            viewBinding.deletedMessage.visibility = View.GONE
            viewBinding.textMessage.visibility = View.VISIBLE
            viewBinding.btnMessageAction.visibility = View.VISIBLE

            if (TEXT_WIDTH == 0) {
                TEXT_WIDTH = calculateMaxWidth()
            }

            viewBinding.normalMessageText.maxWidth = TEXT_WIDTH
            viewBinding.repliedMessageText.maxWidth = TEXT_WIDTH
            viewBinding.deletedMessage.maxWidth = TEXT_WIDTH
            viewBinding.attachmentText.maxWidth = TEXT_WIDTH

            viewBinding.message = item.message

            when {
                item.message.isDeleted -> {
                    viewBinding.deletedMessage.text =
                        viewBinding.root.context.getString(
                            R.string.deleted_message_txt,
                            item.message.name
                        )
                    viewBinding.deletedMessage.visibility = View.VISIBLE
                    viewBinding.textMessage.visibility = View.GONE
                    viewBinding.btnMessageAction.visibility = View.GONE
                }
                item.message.replyTo != null -> {
                    viewBinding.normalMessageText.background = ResourcesCompat.getDrawable(
                        viewBinding.root.context.resources,
                        R.drawable.conversa_bg_rounded_bottom_receive,
                        null
                    )
                    viewBinding.normalMessageText

                    val repliedChat =
                        OmniChannelMessageTextRepository.get(item.message.replyTo!!)!!
                    viewBinding.repliedMessageText.text = TextUtils.cleanHtmlTag(repliedChat[0])
                    viewBinding.repliedMessageText.visibility = View.VISIBLE

                    viewBinding.applyMessageView(item, position)
                }
                else -> {
                    viewBinding.applyMessageView(item, position)
                }
            }

            viewBinding.timestamp.text =
                item.message.timestamp.formatLocaleDatetime(viewBinding.root.context, "HH:mm")
        }

        private fun bindSystemNotification(item: OmniChannelItemModel.SystemNotification) {
            val viewBinding = viewBinding as ConversaItemOmnichannelSystemNotificationBinding
            viewBinding.systemNotificationText.text = item.message
        }

        fun bind(dataModel: OmniChannelItemModel, position: Int) {
            when (dataModel) {
                is OmniChannelItemModel.SentMessage -> bindSentMessage(dataModel, position)
                is OmniChannelItemModel.ReceivedMessage -> bindReceivedMessage(dataModel)
                is OmniChannelItemModel.SystemNotification -> bindSystemNotification(dataModel)
            }
        }
    }
}


class OmniChannelItemDiffCallback : DiffUtil.ItemCallback<OmniChannelItemModel>() {
    override fun areItemsTheSame(
        oldItem: OmniChannelItemModel,
        newItem: OmniChannelItemModel
    ): Boolean {
        if (oldItem is OmniChannelItemModel.ReceivedMessage && newItem is OmniChannelItemModel.ReceivedMessage) {
            return oldItem.message.id == newItem.message.id
        } else if (oldItem is OmniChannelItemModel.SentMessage && newItem is OmniChannelItemModel.SentMessage) {
            return (oldItem.message.id == newItem.message.id) && (oldItem.message.clientChatId == newItem.message.clientChatId)
        } else if (oldItem is OmniChannelItemModel.SystemNotification && newItem is OmniChannelItemModel.SystemNotification) {
            return oldItem.message == newItem.message
        }
        return false
    }

    override fun areContentsTheSame(
        oldItem: OmniChannelItemModel,
        newItem: OmniChannelItemModel
    ): Boolean {
        val TAG = "OmniChannelItem"
        if (oldItem is OmniChannelItemModel.ReceivedMessage && newItem is OmniChannelItemModel.ReceivedMessage)
            return (oldItem.message.text == newItem.message.text) &&
                    (oldItem.message.source == newItem.message.source) &&
                    (oldItem.message.state == newItem.message.state) &&
                    (oldItem.message.replyTo == newItem.message.replyTo) &&
                    (oldItem.message.isDeleted == newItem.message.isDeleted)
        else if (oldItem is OmniChannelItemModel.SentMessage && newItem is OmniChannelItemModel.SentMessage) {
            val v = (oldItem.message.source == newItem.message.source) &&
                    (oldItem.message.state == newItem.message.state) &&
                    (oldItem.message.replyTo == newItem.message.replyTo) &&
                    (oldItem.message.isDeleted == newItem.message.isDeleted)
            return v
        } else if (oldItem is OmniChannelItemModel.SystemNotification && newItem is OmniChannelItemModel.SystemNotification)
            return (areItemsTheSame(oldItem, newItem)) && (oldItem.message == newItem.message)
        return false
    }
}