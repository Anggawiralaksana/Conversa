package ai.prosa.conversa.inapp.core

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.inapp.data.db.RoomMessage
import ai.prosa.conversa.inapp.data.db.RoomMessageRepository
import ai.prosa.conversa.inapp.data.model.MessageType
import ai.prosa.conversa.inapp.data.model.RoomMessageState
import ai.prosa.conversa.inapp.data.model.RoomMessageStateUpdate
import ai.prosa.conversa.inapp.exceptions.RoomMembershipRequiredException
import ai.prosa.conversa.inapp.exceptions.RoomNotFoundException
import ai.prosa.conversa.inapp.isSameDay
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.*
import org.jivesoftware.smack.AbstractXMPPConnection
import org.jivesoftware.smack.MessageListener
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.packet.*
import org.jivesoftware.smackx.chat_markers.ChatMarkersState
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements
import org.jivesoftware.smackx.chat_markers.filter.ChatMarkersFilter
import org.jivesoftware.smackx.chatstates.ChatState
import org.jivesoftware.smackx.mam.MamManager
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.pubsub.*
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import java.io.InputStream
import java.net.URL
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ChatRoom(
    val id: String,
    private val userId: String,
    val connection: AbstractXMPPConnection,
    private val repository: RoomMessageRepository
) {
    private val jid = id.toRoomJid()
    private val mucManager = MultiUserChatManager.getInstanceFor(connection)
    private val muc = mucManager.getMultiUserChat(jid)
    private val joinedRooms = mucManager.joinedRooms
    private val nick: Resourcepart get() = Resourcepart.from(userId)
    private var alreadyJoined = false
    private lateinit var node: LeafNode
    private var history: History? = null

    private var currentDate: Date? = null

    private var _chatStateUpdate: MutableLiveData<RoomMessageStateUpdate> = MutableLiveData()
    val chatStateUpdate: LiveData<RoomMessageStateUpdate> get() = _chatStateUpdate

    private var _disableChatUI: MutableLiveData<Boolean> = MutableLiveData(false)
    val disableChatUI: LiveData<Boolean> get() = _disableChatUI

    private var _unreadMessagesCounts: MutableLiveData<Int> = MutableLiveData(0)
    val unreadMessagesCount: LiveData<Int> get() = _unreadMessagesCounts

    var newMessages: MutableLiveData<List<RoomMessage>> = MutableLiveData(listOf())

    var newDate: MutableLiveData<Date> = MutableLiveData(null)


    data class History(val cachedHistory: List<RoomMessage>, val remoteHistory: List<RoomMessage>) {
        fun unreadMessages(): List<RoomMessage> {
            return listOf()
        }
    }

    companion object {
        private const val TAG = "Conversa"
        val listeners = mutableMapOf<EntityBareJid, MessageListener>()

        private fun getListener(
            jid: EntityBareJid,
            userId: String,
            listener: (roomMessage: RoomMessage, requestChatMark: Boolean) -> Unit
        ): MessageListener {
            if (jid in listeners) {
                return listeners[jid]!!
            } else {
                val lst = MessageListener {
                    it?.let {
                        if (it.stanzaId == null) {
                            Log.d(
                                TAG,
                                "addMessageListener: NULL STANZA ID [body=${it.body}] (${it})"
                            )
                        } else {
                            it.to = jid
                            val requestChatMark = ChatMarkersFilter.INSTANCE.accept(it)
                            listener(
                                RoomMessage.from(it, userId, false),
                                requestChatMark
                            )
                        }
                    }
                }
                listeners[jid] = lst
                return lst
            }
        }
    }

    suspend fun join() {
        for (it in joinedRooms) {
            if (it == jid) {
                alreadyJoined = true
                break
            }
        }

        // Only join if room is already created, otherwise raise error
        // TODO: why the `join` will method create new room when the room is not already created ?
        // TODO: remove this check if join will not create new room
        try {
            mucManager.getRoomInfo(jid)
        } catch (e: XMPPException.XMPPErrorException) {
            if (!e.message.isNullOrEmpty() && e.message!!.contains("item-not-found")) {
                throw RoomNotFoundException()
            }
        }

        try {
            muc.join(nick)
        } catch (e: XMPPException.XMPPErrorException) {
            if (!e.message.isNullOrEmpty() && e.message!!.contains("registration-required")) {
                throw RoomMembershipRequiredException()
            }
        }

        val mgr = PubSubManager.getInstanceFor(connection)
        try {
            val config = mgr.defaultConfiguration.fillableForm
            config.isDeliverPayloads = false
            config.setPersistentItems(false)
            // TODO: maybe open is not the best option
            config.accessModel = AccessModel.open
            config.publishModel = PublishModel.open

            node = mgr.createNode(id, config) as LeafNode
            node.sendConfigurationForm(config)
        } catch (e: XMPPException.XMPPErrorException) {
            if (!e.message.isNullOrEmpty() && e.message!!.contains("Node already exists")) {
                node = mgr.getLeafNode(id)
            }
        }

        try {
            node.subscribe(InappChat.userid.toUserJid())
        } catch (e: XMPPException.XMPPErrorException) {
            e.localizedMessage?.let {
                if (it.contains("modify")) {
                    Log.d(TAG, "join [E]: Already subscribed to Node")
                }
            }
        }


        this.addMessageListener { it, withChatMarkRequest ->
            // Already saved locally means that this listener confirmed that the
            // message is sent to server
            GlobalScope.launch(Dispatchers.IO) {
                if (repository.isSavedLocally(it.id)) {
                    if (it.direction == MessageDirection.SEND) {
                        repository.upgradeState(
                            it.id,
                            RoomMessageState.SENT_SERVER
                        )
                        _chatStateUpdate.postValue(
                            RoomMessageStateUpdate(
                                it.id,
                                RoomMessageState.SENT_SERVER
                            )
                        )
                    } else {
                        repository.upgradeState(it.id, RoomMessageState.DELIVERED)
                    }
                } else {
                    Log.d(
                        TAG,
                        "NEW MESSAGE: ${it.sender} [${it.state}]: ${it.body} [${it.id}]"
                    )

                    if (currentDate == null || !it.timestamp.isSameDay(currentDate!!)) {
                        currentDate = it.timestamp
                        newDate.postValue(it.timestamp)
                    }

                    postNewMessage(it)

                    // Chat marker
                    if (it.direction == MessageDirection.RECEIVE) {
                        it.state = RoomMessageState.DELIVERED
                        repository.insert(it)
                        if (withChatMarkRequest) {
                            this@ChatRoom.sendChatMarker(
                                it.sender,
                                it.id,
                                ChatMarkersState.received
                            )
                        }
                    }
                }
            }
        }
    }

    fun leave() {
        try {
            node.unsubscribe(InappChat.userid.toUserJid().toString())
        } catch (e: XMPPException.XMPPErrorException) {
            // when logout and login
        }
        muc.leave()
    }

    fun setStateListener(listener: (username: String, state: ChatState) -> Unit) {
        node.addItemEventListener {
            val data = it.items.last().id.split("-")
            val username = data[0]
            val state = ChatState.valueOf(data[1])

            if (username != InappChat.userid) {
                listener(username, state)
            }
        }
    }

    private suspend fun fetchRoomArchivedHistory(): List<RoomMessage> {
        val queryArgs = MamManager.MamQueryArgs.builder().setResultPageSize(10000).build()
        return withContext(Dispatchers.IO) {
            MamManager.getInstanceFor(muc)
                .queryArchive(queryArgs).messages.filter { it.stanzaId != null }.map {
                    it.to = jid
                    RoomMessage.from(it, userId, true)
                }
        }
        // http://34.101.69.15:5443/upload/20d64c59ea0da4bdf88637183eb6eb895cb8e04c/7PO24fd1uQEnXdMSkvcDH0Zo7sNayuSo6HTL3cKd/43899
        // https://blue-bird-dev.prosa.ai/dev/xmpp/upload/20d64c59ea0da4bdf88637183eb6eb895cb8e04c/YGsbZxBRJHCpvqkRci0a4x0lUM61pJUqkOf9iQXa/43899
    }

    suspend fun getHistory(forceSyncWithRemote: Boolean = false): History =
        suspendCoroutine { cont ->
            if (history == null || forceSyncWithRemote) {
                GlobalScope.launch(Dispatchers.IO) {
                    val (cached, remote) = awaitAll(
                        async {
                            repository.getInitRoomMessages(id)
                        },
                        async {
                            fetchRoomArchivedHistory()
                        }
                    )

                    Log.d(TAG, "getHistory: ORI CACHE = ${cached.size} | REMOTE = ${remote.size}")

                    // Sync remote and cached messages
                    when {
                        remote.isEmpty() -> {
                            repository.deleteAll()
                            history = History(listOf(), listOf())
                        }
                        remote.size == cached.size -> {
                            // TODO: check whether all lined up
                            val updatedCachedMessages = mutableListOf<RoomMessage>()
                            for (pair in cached.zip(remote)) {
                                if (pair.first.id != pair.second.id) {
                                    updatedCachedMessages.clear()
                                    // TODO: refactor this as this is the same with the else below
                                    repository.deleteAll()
                                    remote.forEach {
                                        if (it.direction == MessageDirection.SEND) {
                                            val newMessage =
                                                it.copy(state = RoomMessageState.SENT_SERVER)
                                            updatedCachedMessages.add(newMessage)
                                            repository.insert(newMessage)
                                        } else {
                                            val newMessage = it.copy(state = RoomMessageState.READ)
                                            updatedCachedMessages.add(newMessage)
                                            repository.insert(newMessage)
                                        }
                                    }
                                    break
                                } else if (pair.first.state == RoomMessageState.NEW) {
                                    updatedCachedMessages.add(pair.first)
                                    repository.upgradeState(
                                        pair.first.id,
                                        RoomMessageState.SENT_SERVER
                                    )
                                } else {
                                    updatedCachedMessages.add(pair.first)
                                }
                            }
                            history = History(updatedCachedMessages, remote)
                        }
                        else -> {
                            repository.deleteAll()
                            val updatedCachedMessages = mutableListOf<RoomMessage>()
                            remote.forEach {
                                if (it.direction == MessageDirection.SEND) {
                                    val newMessage = it.copy(state = RoomMessageState.SENT_SERVER)
                                    updatedCachedMessages.add(newMessage)
                                    repository.insert(newMessage)
                                } else {
                                    val newMessage = it.copy(state = RoomMessageState.DELIVERED)
                                    updatedCachedMessages.add(newMessage)
                                    repository.insert(newMessage)
                                }
                            }
                            history = History(updatedCachedMessages, remote)
                        }
                    }

                    _unreadMessagesCounts.postValue(history!!.cachedHistory.filter {
                        it.direction == MessageDirection.RECEIVE && it.state != RoomMessageState.READ
                    }.size)

                    cont.resume(history!!)
                }
            } else {
                _unreadMessagesCounts.postValue(history!!.cachedHistory.filter {
                    it.direction == MessageDirection.RECEIVE && it.state != RoomMessageState.READ
                }.size)

                cont.resume(history!!)
            }
        }


    private fun addMessageListener(messageListener: (msg: RoomMessage, requestChatMark: Boolean) -> Unit) {
        // Make sure only one listener per room
        if (jid !in listeners) {
            muc.addMessageListener(getListener(jid, userId, messageListener))
        }
    }

    fun setState(state: ChatState) {
        try {
            node.publish(Item("${InappChat.userid}-${state.name}"))
        } catch (e: SmackException) {
            // Logout but this still connected
        } catch (e: XMPPException) {

        }
    }

    fun sendMessage(roomMessage: RoomMessage) {
        if (roomMessage.roomId != id) {
            throw Exception("Wrong room")
        }

        if (roomMessage.type == MessageType.TEXT) {
            val message = MessageBuilder.buildMessage(roomMessage.id).setBody(roomMessage.body)
            message.addExtension(ChatMarkersElements.MarkableExtension.INSTANCE)
            muc.sendMessage(message)
        }
        setState(ChatState.active)
    }

    fun uploadFile(stream: InputStream, name: String): Pair<URL, Int> {
        val size = stream.available()
        return Pair(InappChat.httpUpload(stream, name, size), size)
    }

    // For extended message, caption is put in the body of the message
    suspend fun sendFile(
        messageId: String,
        roomId: String,
        url: URL,
        size: Int,
        caption: String,
        name: String,
        type: MessageType,
        additionalAttributes: Map<String, String>,
    ) {
        if (roomId != id) {
            throw Exception("Wrong room")
        }

        val extendedMessage = StandardExtensionElement.builder(type.tag, type.namespace)
            .addAttribute("name", name)
            .addAttribute("url", url.toString())
            .addAttribute("size", size.toString())
        extendedMessage.addAttributes(additionalAttributes)

        // Create message with specified ID
        val stanza = StanzaBuilder
            .buildMessage(messageId)
            .ofType(Message.Type.groupchat)
            .setBody(caption)
            .addExtension(extendedMessage.build())
            .addExtension(ChatMarkersElements.MarkableExtension.INSTANCE)
        DeliveryReceiptRequest.addTo(stanza)

        Log.d(TAG, "sendExtendedMessage: STANZA ${stanza.build().toXML()}")

        withContext(Dispatchers.IO) {
            muc.sendMessage(stanza)
            setState(ChatState.active)
        }
    }

    fun sendChatMarker(to: String, messageId: String, mark: ChatMarkersState) {
        val toJid = JidCreate.entityFullFrom(jid, Resourcepart.from(to))
        val chat = muc.createPrivateChat(toJid) { chat, message ->
            Log.d(TAG, "sendChatMarker: RECEIVE $chat $message")
        }

        // TODO: use smaller message id
        val message = StanzaBuilder.buildMessage(uuid4().toString())
            .ofType(Message.Type.chat)
            .to(toJid)

        val extension: ExtensionElement = when (mark) {
            ChatMarkersState.received -> ChatMarkersElements.ReceivedExtension(messageId)
            ChatMarkersState.displayed -> {
                ChatMarkersElements.DisplayedExtension(messageId)
            }
            else -> ChatMarkersElements.AcknowledgedExtension(messageId)
        }

        if (mark == ChatMarkersState.displayed) {
            var currentUnreadCount = unreadMessagesCount.value!!
            val unreadCount = if (currentUnreadCount - 1 < 0) {
                0
            } else {
                currentUnreadCount - 1
            }
            _unreadMessagesCounts.postValue(unreadCount)
        }

        message.addExtension(extension)
        Log.d(TAG, "sendChatMarker [${messageId}] $mark")
        chat.sendMessage(message)
    }

    // TODO: use get set
    fun getCurrentDate(): Date? {
        return currentDate
    }

    fun setCurrentDate(date: Date) {
        currentDate = date
    }

    fun postNewMessage(newMessage: RoomMessage) {
        val messages = newMessages.value!!.toMutableList()
        messages.add(newMessage)
        newMessages.postValue(messages.toList())
    }

    fun disableChatUI() {
        _disableChatUI.postValue(true)
    }

    fun enableChatUI() {
        _disableChatUI.postValue(false)
    }
}