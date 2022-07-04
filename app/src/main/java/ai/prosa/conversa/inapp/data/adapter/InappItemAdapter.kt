package ai.prosa.conversa.inapp.data.adapter

import ai.prosa.conversa.common.formatLocaleDatetime
import ai.prosa.conversa.common.utils.ImageUtils.showImage
import ai.prosa.conversa.common.utils.ViewUtils
import ai.prosa.conversa.databinding.ConversaItemMessageDateBinding
import ai.prosa.conversa.databinding.ConversaItemMessageReceiveBinding
import ai.prosa.conversa.databinding.ConversaItemMessageSendBinding
import ai.prosa.conversa.inapp.data.db.RoomMessage
import ai.prosa.conversa.inapp.data.model.InappItemModel
import ai.prosa.conversa.inapp.data.model.MessageType
import ai.prosa.conversa.inapp.data.model.RoomMessageState
import ai.prosa.conversa.inapp.isSameDay
import ai.prosa.conversa.inapp.utils.MediaCache
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

const val TEXT_MESSAGE_LENGTH_RATIO = 0.6
const val AUDIO_MESSAGE_LENGTH_RATIO = 0.45

var AUDIO_WIDTH = 0
var IMAGE_WIDTH = 0
var TEXT_WIDTH = 0

class InappItemAdapter : ListAdapter<InappItemModel, InappItemAdapter.DataAdapterViewHolder>(
    InappItemItemDiffCallback()
) {

    companion object {
        const val TYPE_SENT_MESSAGE = 0
        const val TYPE_RECEIVED_MESSAGE = 1
        const val TYPE_DATE_MESSAGE = 2
        const val TYPE_EVENT_MESSAGE = 3
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DataAdapterViewHolder {
        when (viewType) {
            TYPE_SENT_MESSAGE -> {
                val viewBinding = ConversaItemMessageSendBinding.inflate(
                    LayoutInflater
                        .from(parent.context), parent, false
                )
                return DataAdapterViewHolder(viewBinding)
            }
            TYPE_RECEIVED_MESSAGE -> {
                val viewBinding = ConversaItemMessageReceiveBinding.inflate(
                    LayoutInflater
                        .from(parent.context), parent, false
                )
                return DataAdapterViewHolder(viewBinding)
            }
            TYPE_DATE_MESSAGE -> {
                val viewBinding = ConversaItemMessageDateBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                return DataAdapterViewHolder(viewBinding)
            }
            TYPE_EVENT_MESSAGE -> {
                val viewBinding = ConversaItemMessageDateBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                return DataAdapterViewHolder(viewBinding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: DataAdapterViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is InappItemModel.SentMessage -> TYPE_SENT_MESSAGE
            is InappItemModel.ReceivedMessage -> TYPE_RECEIVED_MESSAGE
            is InappItemModel.DateMessage -> TYPE_DATE_MESSAGE
            is InappItemModel.EventMessage -> TYPE_EVENT_MESSAGE
            else -> -1
        }
    }

    // https://stackoverflow.com/questions/49726385/listadapter-not-updating-item-in-recyclerview
    override fun submitList(list: List<InappItemModel>?) {
        super.submitList(if (list == this.currentList) list.toList() else list)
    }

    class DataAdapterViewHolder(private val viewBinding: ViewBinding) :
        RecyclerView.ViewHolder(viewBinding.root) {

        private fun calculateMaxWidth(): Int {
            return (TEXT_MESSAGE_LENGTH_RATIO * ViewUtils.getScreenWidth(viewBinding.root.context)).toInt()
        }

        private fun calculateMaxAudioWidth(): Int {
            return (AUDIO_MESSAGE_LENGTH_RATIO * ViewUtils.getScreenWidth(viewBinding.root.context)).toInt()
        }


        private fun bindSentMessage(item: InappItemModel.SentMessage, position: Int) {
            val viewBinding = viewBinding as ConversaItemMessageSendBinding

            viewBinding.audioPlayerContainer.visibility = View.GONE
            viewBinding.image.visibility = View.GONE
            viewBinding.overlayImage.visibility = View.GONE
            viewBinding.progress.visibility = View.GONE
            viewBinding.message = item.message

            when (item.message.type) {
                MessageType.IMAGE -> {
                    if (IMAGE_WIDTH == 0) {
                        val screenWidth = ViewUtils.getScreenWidth(viewBinding.root.context)
                        IMAGE_WIDTH = (TEXT_MESSAGE_LENGTH_RATIO * screenWidth).toInt()
                    }

                    viewBinding.image.visibility = View.VISIBLE

                    if (item.message.state == RoomMessageState.NEW) {
                        viewBinding.overlayImage.visibility = View.VISIBLE
                        viewBinding.progress.visibility = View.VISIBLE
                    } else {
                        viewBinding.overlayImage.visibility = View.GONE
                        viewBinding.progress.visibility = View.GONE
                    }

                    viewBinding.image.layoutParams.width = IMAGE_WIDTH
                    viewBinding.image.requestLayout()
                    showImage(
                        item.message.attrs["url"],
                        viewBinding.root,
                        viewBinding.image
                    )
                }

                MessageType.AUDIO -> {
                    viewBinding.audioPlayerContainer.visibility = View.VISIBLE
                    showAudio(
                        viewBinding.root.context,
                        item.message,
                        viewBinding.btnPlay,
                        viewBinding.btnPause,
                        viewBinding.audioSeekbar,
                        viewBinding.remaining
                    )
                    if (AUDIO_WIDTH == 0) {
                        AUDIO_WIDTH = calculateMaxAudioWidth()
                    }
                    viewBinding.audioSeekbar.layoutParams.width = AUDIO_WIDTH

                    viewBinding.audioSeekbar.requestLayout()
                }
                MessageType.TEXT -> {
                    if (TEXT_WIDTH == 0) {
                        TEXT_WIDTH = calculateMaxWidth()
                    }
                    viewBinding.textMessage.maxWidth = TEXT_WIDTH
                }
            }

            viewBinding.timestamp.text =
                item.message.timestamp.formatLocaleDatetime(viewBinding.root.context, "HH:mm")
        }


        private fun bindReceivedMessage(item: InappItemModel.ReceivedMessage) {
            val viewBinding = viewBinding as ConversaItemMessageReceiveBinding

            viewBinding.audioPlayerContainer.visibility = View.GONE
            viewBinding.image.visibility = View.GONE
            viewBinding.message = item.message

            when (item.message.type) {
                MessageType.IMAGE -> {
                    if (IMAGE_WIDTH == 0) {
                        val screenWidth = ViewUtils.getScreenWidth(viewBinding.root.context)
                        IMAGE_WIDTH = (TEXT_MESSAGE_LENGTH_RATIO * screenWidth).toInt()
                    }

                    viewBinding.image.visibility = View.VISIBLE
                    viewBinding.image.layoutParams.width = IMAGE_WIDTH
                    viewBinding.image.requestLayout()
                    showImage(
                        item.message.attrs["url"],
                        viewBinding.root,
                        viewBinding.image
                    )
                }
                MessageType.AUDIO -> {
                    viewBinding.audioPlayerContainer.visibility = View.VISIBLE
                    showAudio(
                        viewBinding.root.context,
                        item.message,
                        viewBinding.btnPlay,
                        viewBinding.btnPause,
                        viewBinding.audioSeekbar,
                        viewBinding.remaining
                    )
                    if (AUDIO_WIDTH == 0) {
                        AUDIO_WIDTH = calculateMaxAudioWidth()
                    }
                    viewBinding.audioSeekbar.layoutParams.width = AUDIO_WIDTH

                    viewBinding.audioSeekbar.requestLayout()
                }
                MessageType.TEXT -> {
                    if (TEXT_WIDTH == 0) {
                        TEXT_WIDTH = calculateMaxWidth()
                    }
                    viewBinding.textMessage.maxWidth = TEXT_WIDTH
                }
            }

            viewBinding.timestamp.text =
                item.message.timestamp.formatLocaleDatetime(viewBinding.root.context, "HH:mm")
        }

        private fun bindDateMessage(item: InappItemModel.DateMessage) {
            val viewBinding = viewBinding as ConversaItemMessageDateBinding

            viewBinding.date.text =
                SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(item.date)
        }

        private fun bindEventMessage(item: InappItemModel.EventMessage) {
            val viewBinding = viewBinding as ConversaItemMessageDateBinding
            viewBinding.date.text = item.message.body
        }

        fun bind(dataModel: InappItemModel, position: Int) {
            when (dataModel) {
                is InappItemModel.SentMessage -> bindSentMessage(dataModel, position)
                is InappItemModel.ReceivedMessage -> bindReceivedMessage(dataModel)
                is InappItemModel.DateMessage -> bindDateMessage(dataModel)
                is InappItemModel.EventMessage -> bindEventMessage(dataModel)
            }
        }
    }
}

class InappItemItemDiffCallback : DiffUtil.ItemCallback<InappItemModel>() {
    override fun areItemsTheSame(oldItem: InappItemModel, newItem: InappItemModel): Boolean {
        if (oldItem is InappItemModel.SentMessage && newItem is InappItemModel.SentMessage) {
            return oldItem.message.id == newItem.message.id
        } else if (oldItem is InappItemModel.ReceivedMessage && newItem is InappItemModel.ReceivedMessage) {
            return oldItem.message.id == newItem.message.id
        } else if (oldItem is InappItemModel.DateMessage && newItem is InappItemModel.DateMessage) {
            return oldItem.date.isSameDay(newItem.date)
        }
        return false
    }

    override fun areContentsTheSame(oldItem: InappItemModel, newItem: InappItemModel): Boolean {
        if (oldItem is InappItemModel.SentMessage && newItem is InappItemModel.SentMessage) {
            return (
                    (oldItem.message.body == newItem.message.body) &&
                            (oldItem.message.state == newItem.message.state) &&
                            (oldItem.message.roomId == newItem.message.roomId)
                    )
        } else if (oldItem is InappItemModel.ReceivedMessage && newItem is InappItemModel.ReceivedMessage) {
            return (
                    (oldItem.message.body == newItem.message.body) &&
                            (oldItem.message.state == newItem.message.state) &&
                            (oldItem.message.roomId == newItem.message.roomId)
                    )
        } else if (oldItem is InappItemModel.DateMessage && newItem is InappItemModel.DateMessage) {
            return oldItem.date.isSameDay(newItem.date)
        }
        return false
    }
}


fun showAudio(
    context: Context,
    message: RoomMessage,
    btnPlay: MaterialButton,
    btnPause: MaterialButton,
    audioSeekbar: SeekBar,
    remaining: TextView
) {
    val duration = message.attrs["duration"]!!.toInt()
    val mp = MediaPlayer().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        } else {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
        // TODO: handle if url is empty
        if (message.attrs["url"]!!.contains("http")) {
            setDataSource(MediaCache.getProxyUrl(context, message.attrs["url"]!!))
        } else {
            setDataSource(message.attrs["url"]!!)
        }

        prepareAsync()
        remaining.text =
            formatMillisecondsToHourMinutes(duration)
    }

    mp.setOnCompletionListener {
        audioSeekbar.progress = duration
        mp.seekTo(0)
        audioSeekbar.progress = 0
        remaining.text =
            formatMillisecondsToHourMinutes(duration)
        mp.pause()
        btnPlay.visibility = View.VISIBLE
        btnPause.visibility = View.GONE
    }

    btnPlay.setOnClickListener {
        remaining.text =
            formatMillisecondsToHourMinutes(mp.currentPosition)
        mp.apply {
            btnPlay.visibility = View.GONE
            btnPause.visibility = View.VISIBLE
            start()
        }
    }
    btnPause.setOnClickListener {
        audioSeekbar.progress = mp.currentPosition
        remaining.text =
            formatMillisecondsToHourMinutes(duration)
        mp.pause()
        btnPlay.visibility = View.VISIBLE
        btnPause.visibility = View.GONE
    }

    audioSeekbar.max = message.attrs["duration"]!!.toInt()
    val handler = Handler(Looper.myLooper()!!)
    handler.postDelayed(object : Runnable {
        override fun run() {
            handler.postDelayed(this, 500)
            if (mp.isPlaying) {
                audioSeekbar.progress = mp.currentPosition
                remaining.text =
                    formatMillisecondsToHourMinutes(mp.currentPosition)
            }
        }
    }, 0)
    audioSeekbar.setOnSeekBarChangeListener(object :
        SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: SeekBar?,
            progress: Int,
            fromUser: Boolean
        ) {
            if (fromUser) {
                mp.seekTo(progress)
                remaining.text =
                    formatMillisecondsToHourMinutes(duration - mp.currentPosition)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            if (seekBar != null) {
                mp.seekTo(seekBar.progress)
                remaining.text =
                    formatMillisecondsToHourMinutes(duration - mp.currentPosition)
            }
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            if (seekBar != null) {
                mp.seekTo(seekBar.progress)
                remaining.text =
                    formatMillisecondsToHourMinutes(duration - mp.currentPosition)
            }
        }
    })
}

fun formatMillisecondsToHourMinutes(milliseconds: Int): String {
    val minutes = (milliseconds / 1000 / 60).toString().padStart(2, '0')
    val seconds = (milliseconds / 1000 % 60).toString().padStart(2, '0')

    return "$minutes:$seconds"
}