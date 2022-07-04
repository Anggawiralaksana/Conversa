package ai.prosa.conversa.inapp.viewmodels

import ai.prosa.conversa.common.api.ConversaSDKApi
import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.inapp.core.ChatRoom
import ai.prosa.conversa.inapp.core.InappChat
import ai.prosa.conversa.inapp.data.db.RoomMessage
import ai.prosa.conversa.inapp.data.db.RoomMessageRepository
import ai.prosa.conversa.inapp.data.db.TemplateMessage
import ai.prosa.conversa.inapp.data.db.TemplateMessageRepository
import ai.prosa.conversa.inapp.data.model.MessageType
import ai.prosa.conversa.inapp.data.model.RoomMessageState
import ai.prosa.conversa.inapp.data.model.RoomMessageState.*
import ai.prosa.conversa.inapp.isSameDay
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jivesoftware.smackx.chat_markers.ChatMarkersState
import org.jivesoftware.smackx.chatstates.ChatState
import java.io.InputStream
import java.util.*


class InappViewModelFactory(
    private val room: ChatRoom,
    private val messageRepository: RoomMessageRepository,
    private val templateMessageRepository: TemplateMessageRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InappViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InappViewModel(room, messageRepository, templateMessageRepository, dispatcher) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// TODO:
// - login with messages, logout, login again (message is empty)

class InappViewModel(
    private val room: ChatRoom,
    private val messageRepository: RoomMessageRepository,
    private val templateMessageRepository: TemplateMessageRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val templateMessages = templateMessageRepository.getAll()
    private var driverTemplateMessages: List<TemplateMessage>? = null


    private var messageStateHandler: (state: RoomMessageState, messageId: String) -> Unit =
        { _, _ -> }

    private var currentState = ChatState.inactive

    // Save room message to database
    private suspend fun saveMessage(m: RoomMessage) {
        messageRepository.insert(m)
    }

    init {
        viewModelScope.launch(dispatcher) {
            InappChat.addChatMarkerListener { from, state, id ->
                Log.d(TAG, "MARKER: [${state}] - [${from}] - [${id}]")
                when (state) {
                    ChatMarkersState.received -> viewModelScope.launch {
                        messageRepository.upgradeState(id, DELIVERED)
                        messageStateHandler(DELIVERED, id)
                    }
                    ChatMarkersState.displayed, ChatMarkersState.acknowledged -> viewModelScope.launch {
                        val msg = messageRepository.get(id)
                        if (msg.state != READ) {
                            messageRepository.upgradeState(id, READ)
                            messageStateHandler(READ, id)
                        }
                    }
                    ChatMarkersState.markable -> TODO()
                }
            }
        }

        // Periodically broadcast state
        val delayMs: Long = 15000
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                room.setState(currentState)
                handler.postDelayed(this, delayMs)
            }
        }
        handler.postDelayed(runnable, delayMs)
    }

    fun setMessageStateHandler(listener: (state: RoomMessageState, messageId: String) -> Unit) {
        messageStateHandler = listener
    }

    fun markAs(message: RoomMessage, direction: MessageDirection, state: RoomMessageState) =
        viewModelScope.launch(dispatcher) {
            if (message.direction == direction) {
                messageRepository.upgradeState(message.id, state)
            }

            if (direction == MessageDirection.RECEIVE) {
                room.sendChatMarker(
                    message.sender,
                    message.id,
                    ChatMarkersState.displayed
                )
            }
        }

    fun markAllAsRead(direction: MessageDirection, callback: (List<RoomMessage>) -> Unit) =
        viewModelScope.launch(dispatcher) {
            delay(500)
            val unreadMessages = messageRepository.getUnreadMessages(room.id, direction)
            unreadMessages.forEach {
                markAs(it, direction, READ)
            }
            callback(unreadMessages)
        }

    fun markAllAsDelivered(direction: MessageDirection, callback: (List<RoomMessage>) -> Unit) =
        viewModelScope.launch(dispatcher) {
            delay(500)
            val unreadMessages = messageRepository.getUndeliveredMessages(room.id, direction)
            unreadMessages.forEach {
                markAs(it, direction, DELIVERED)
            }
            callback(unreadMessages)
        }

    fun sendText(text: String, callback: () -> Unit) = viewModelScope.launch(dispatcher) {
        var roomMessage = RoomMessage(
            uuid4().toString(),
            room.id,
            text,
            InappChat.userid,
            MessageType.TEXT,
            Date(),
            mapOf(),
            MessageDirection.SEND,
            NEW
        )
        saveMessage(roomMessage)
        if (room.getCurrentDate() == null || !roomMessage.timestamp.isSameDay(room.getCurrentDate()!!)) {
            room.setCurrentDate(roomMessage.timestamp)
            room.newDate.postValue(roomMessage.timestamp)
        }
        room.postNewMessage(roomMessage)

        delay(200)
        callback()

        Log.d(TAG, "sendText: ID ${roomMessage.id}")

        room.sendMessage(roomMessage)
    }

    fun sendFile(
        stream: InputStream,
        caption: String,
        name: String,
        type: MessageType,
        attributes: Map<String, String>,
        tmpUrl: String
    ) = viewModelScope.launch(dispatcher) {
        val messageId = uuid4().toString()

        val mergedAttributesTmp = attributes + mapOf(
            "name" to name,
            "url" to tmpUrl
        )
        val roomMessage = RoomMessage(
            messageId,
            room.id,
            caption,
            InappChat.userid,
            type,
            Date(),
            mergedAttributesTmp,
            MessageDirection.SEND,
            NEW
        )
        saveMessage(roomMessage)
        if (room.getCurrentDate() == null || !roomMessage.timestamp.isSameDay(room.getCurrentDate()!!)) {
            room.setCurrentDate(roomMessage.timestamp)
            room.newDate.postValue(roomMessage.timestamp)
        }
        room.postNewMessage(roomMessage)

        delay(200)

        val (url, size) = room.uploadFile(stream, name)
        Log.d(TAG, "sendFile: $url")
        val mergedAttributes = attributes + mapOf(
            "name" to name,
            "url" to url.toString()
        )
        roomMessage.attrs = mergedAttributes
        saveMessage(roomMessage)

        delay(2000)
        room.sendFile(
            messageId,
            room.id,
            url,
            size,
            caption,
            name,
            type,
            attributes
        )
    }

    fun getDriverTemplateMessages(callback: (List<TemplateMessage>) -> Unit) {
        viewModelScope.launch(dispatcher) {
            if (driverTemplateMessages == null) {
                ConversaSDKApi.getDriverTemplateMessages {
                    if (it.isSuccessful && it.body() != null) {
                        driverTemplateMessages = it.body()!!.templateMessages.map { text ->
                            TemplateMessage(text = text, createdAt = Date())
                        }
                        callback(driverTemplateMessages!!)
                    }
                }
            } else {
                callback(driverTemplateMessages!!)
            }
        }
    }

    fun init() = viewModelScope.launch(dispatcher) {
        if (templateMessageRepository.getAllOnce().isEmpty()) {
            templateMessageRepository.insertAll(
                TemplateMessage(text = "Apakah sesuai map ?", createdAt = Date()),
                TemplateMessage(text = "Saya sedang menuju ke sana ?", createdAt = Date()),
            )
        }
    }

    fun insertTemplateMessage(text: String) = viewModelScope.launch(dispatcher) {
        templateMessageRepository.insertAll(
            TemplateMessage(text = text, createdAt = Date())
        )
    }

    fun deleteTemplateMessage(id: Long) = viewModelScope.launch(dispatcher) {
        templateMessageRepository.delete(id)
    }

    fun updateTemplateMessage(id: Long, newText: String) = viewModelScope.launch(dispatcher) {
        templateMessageRepository.updateText(id, newText)
    }

    fun setState(chatState: ChatState) = viewModelScope.launch(dispatcher) {
        currentState = chatState
        room.setState(chatState)
    }

    companion object {
        private const val TAG = "UIViewModel"
    }
}
