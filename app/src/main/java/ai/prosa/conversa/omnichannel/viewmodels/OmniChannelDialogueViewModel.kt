package ai.prosa.conversa.omnichannel.viewmodels

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessage
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessageRepository
import ai.prosa.conversa.omnichannel.data.model.OmniChannelChatStateUpdate
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageSource
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageState
import ai.prosa.conversa.omnichannel.data.model.SystemNotification
import ai.prosa.conversa.omnichannel.data.remote.OmniChannelMessageTextRepository
import ai.prosa.conversa.omnichannel.data.remote.SocketEventListener
import ai.prosa.conversa.omnichannel.data.remote.SocketService
import ai.prosa.conversa.omnichannel.data.remote.dto.EventReadDto
import ai.prosa.conversa.omnichannel.data.sharedPreferences.OmniChannelPreferenceHelper
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.util.*

val json = Json { ignoreUnknownKeys = true }

class OmniChannelViewModelFactory(
    private val messageRepository: OmniChannelMessageRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OmniChannelViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OmniChannelViewModel(messageRepository, dispatcher) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class OmniChannelViewModel(
    val messageRepository: OmniChannelMessageRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private var socketService = SocketService()

    private var _session: MutableLiveData<Int> = MutableLiveData()
    val session: LiveData<Int> get() = _session

    private var _history: MutableLiveData<List<OmniChannelMessage>> = MutableLiveData()
    val history: LiveData<List<OmniChannelMessage>> get() = _history

    private var _chatStateUpdate: MutableLiveData<OmniChannelChatStateUpdate> = MutableLiveData()
    val chatStateUpdate: LiveData<OmniChannelChatStateUpdate> get() = _chatStateUpdate

    private var _newChats: MutableLiveData<List<OmniChannelMessage>> = MutableLiveData()
    val newChats: LiveData<List<OmniChannelMessage>> get() = _newChats

    private var _unreadChats: MutableLiveData<Set<String>> = MutableLiveData(setOf())
    val unreadChats: LiveData<Set<String>> get() = _unreadChats

    private var unconfirmedRead = mutableSetOf<String>()

    private var _systemNotifications = MutableLiveData(listOf<SystemNotification>())
    val systemNotifications: LiveData<List<SystemNotification>> get() = _systemNotifications

    private var talkingWith = OmniChannelMessageSource.SYSTEM
    private var talkingWithName = "Chatbot"

    var clientServerMappings = mutableMapOf<String, String>()

    private var lastTimestamp = Date()

    private val testSocketDelayMs = 30000L
    private val testSocketHandler = Handler(Looper.getMainLooper())
    private val testSocketTask = object : Runnable {
        override fun run() {
            socketService.ping()
            testSocketHandler.postDelayed(this, testSocketDelayMs)
        }
    }

    private var cityName = "Others"

    private val socketListener = object : SocketEventListener {
        override fun onConnect() {
            Log.d(TAG, "[[CONNECTED]] $cityName")
            testSocketHandler.removeCallbacks(testSocketTask)
            testSocketHandler.postDelayed(testSocketTask, testSocketDelayMs)
            socketService.sendLocation(cityName)
        }

        override fun onSessionId(sessionId: Int, isNew: Boolean) {
            _session.postValue(sessionId)
            Log.d(TAG, "[[SESSION ID]] [$sessionId] [$isNew]")
        }

        override fun onHistory(history: List<OmniChannelMessage>) {
            Log.d(TAG, "onHistory: [HISTORY SERVER] ${history.size}")

            viewModelScope.launch(dispatcher) {
                if (history.isEmpty()) {
                    messageRepository.deleteAll()
                    messageRepository.insert(history)
                    _history.postValue(history)
                } else {
                    val cachedHistory = messageRepository.getMessagesOnce()
                    Log.d(TAG, "onHistory: [CACHED HISTORY] ${cachedHistory.size}")
                    history.forEach {
                        if (it.direction == MessageDirection.SEND) {
                            Log.d(TAG, "onHistory: [H] [${it.state}] ${it.text}")
                        }
                    }
                    when {
                         history.size < cachedHistory.size -> {
                             // TODO: resend if there are unsent message
                             Log.d(
                                 TAG,
                                 "onHistory: ${
                                     cachedHistory.subList(
                                         history.size,
                                         cachedHistory.size
                                     )
                                 }"
                             )
                            messageRepository.deleteAll()
                            messageRepository.insert(history)
                            _history.postValue(history)
                        }
                        history.size > cachedHistory.size -> {
                            messageRepository.insert(history)
                            _history.postValue(history)
                        }
                        history.size == cachedHistory.size -> {
                            // TODO: if history and cached is the same; don't post history
                            cachedHistory.zip(history).forEach {
                                Log.d(TAG, "onHistory: [H] ${it.first.state} || ${it.second.state} || ${it.first}")
                            }
                            messageRepository.insert(history)
                        }
                        else -> {
                            _history.postValue(history)
                            messageRepository.insert(history)
                        }
                    }
                }
            }
        }

        override fun onServerChatID(serverChatID: String, clientChatID: String) {
            Log.d(
                TAG,
                "[[SERVER CHAT ID]]: [serverChatID]: $serverChatID | [clientChatID]: $clientChatID"
            )

            clientServerMappings[clientChatID] = serverChatID
            OmniChannelMessageTextRepository.get(clientChatID)?.let {
                OmniChannelMessageTextRepository.set(serverChatID, it)
            }
            try {
                viewModelScope.launch(dispatcher) {
                    // TODO: find better solution, sometimes the message is not yet saved; so the delay is practically necessary
                    try {
                        delay(2000)
                        val savedChat = messageRepository.getByClientID(clientChatID)
                        messageRepository.updateID(
                            oldId = savedChat.id,
                            id = serverChatID
                        )
                    } catch (e: java.lang.NullPointerException) {
                        try {
                            delay(1000)
                            val savedChat = messageRepository.getByClientID(clientChatID)
                            messageRepository.updateID(
                                oldId = savedChat.id,
                                id = serverChatID
                            )
                        } catch (e: java.lang.NullPointerException) {
                            delay(2000)
                            val savedChat = messageRepository.getByClientID(clientChatID)
                            messageRepository.updateID(
                                oldId = savedChat.id,
                                id = serverChatID
                            )
                        }
                    }
                }
            } catch (e: NullPointerException) {
                // Agent message
            }

            if (serverChatID.isNotEmpty() && clientChatID.isNotEmpty()) {
                if (unconfirmedRead.contains(clientChatID)) {
                    readUnreadChats(setOf(serverChatID))
                    unconfirmedRead.remove(clientChatID)
                }
            }
        }

        override fun onChat(chats: List<OmniChannelMessage>, source: OmniChannelMessageSource) {
            viewModelScope.launch(dispatcher) {
                // Update chat; if the server time is behind
                val updatedChats = chats.map {
                    if (it.timestamp.before(lastTimestamp)) {
                        it.copy(timestamp = Date(lastTimestamp.time + 1000))
                    } else {
                        it
                    }
                }
                messageRepository.insert(updatedChats)
                OmniChannelMessageTextRepository.set(chats[0].id, chats.map { it.text })
                _newChats.postValue(chats)
                addUnreadChat(chats[0].id)
                Log.d(TAG, "[[CHAT]] [$source] [$chats] ${Date()}")
            }
        }

        override fun onRead(readDto: EventReadDto) {
            Log.d(TAG, "[[READ]] $readDto")
            if (readDto.clientChatId.isNotEmpty() && readDto.serverChatId.isEmpty()) {
                unconfirmedRead.add(readDto.clientChatId)
            }
            try {
                _chatStateUpdate.postValue(
                    OmniChannelChatStateUpdate(
                        readDto.clientChatId,
                        OmniChannelMessageState.READ
                    )
                )
                viewModelScope.launch(dispatcher) {
                    try {
                        delay(1000)
                        val savedChat = messageRepository.getByClientID(readDto.clientChatId)
                        Log.d(TAG, "onRead: SAVED CHAT $savedChat")
                        messageRepository.upgradeStateByClientID(readDto.clientChatId, OmniChannelMessageState.READ)
                    } catch (e: java.lang.NullPointerException) {
                        delay(5000)
                        val savedChat = messageRepository.getByClientID(readDto.clientChatId)
                        Log.d(TAG, "onRead: *SAVED CHAT $savedChat")
                        messageRepository.upgradeStateByClientID(readDto.clientChatId, OmniChannelMessageState.READ)
                    }
                }

            } catch (e: NullPointerException) {
                // Agent message
            }
        }

        override fun onHandled(
            name: String,
            source: OmniChannelMessageSource,
            isFirstNotification: Boolean
        ) {
            val currentSystemNotifications = _systemNotifications.value!!.toMutableList()
            if (isFirstNotification) {
                if ((talkingWith == OmniChannelMessageSource.AGENT && talkingWithName != name) || talkingWith == OmniChannelMessageSource.SYSTEM) {
                    talkingWith = source
                    talkingWithName = name
                    currentSystemNotifications.add(
                        SystemNotification.HandledSession(
                            source,
                            name,
                            true
                        )
                    )
                }
            } else {
                talkingWith = source
                talkingWithName = name
                currentSystemNotifications.add(
                    SystemNotification.HandledSession(
                        source,
                        name,
                        false
                    )
                )
            }
            _systemNotifications.postValue(currentSystemNotifications)
            Log.d(
                TAG,
                "[[NOTIFICATION]] [$source] [$name] [firsTimeNotification = $isFirstNotification]"
            )
        }

        override fun onDoneSession(name: String) {
            val currentSystemNotifications = _systemNotifications.value!!.toMutableList()
            currentSystemNotifications.add(SystemNotification.DoneSession(name))
            _systemNotifications.postValue(currentSystemNotifications)

            talkingWith = OmniChannelMessageSource.SYSTEM
            talkingWithName = ""

            Log.d(TAG, "configureSocket: [[DONE]] $name")
        }

        override fun onTest() {
//            Log.d(TAG, "onTest: [[TEST]]")
        }
    }

    fun softDeleteServerID(id: String) {
        viewModelScope.launch(dispatcher) {
            messageRepository.softDeleteServerID(id)
        }
    }

    fun softDeleteClientID(id: String) {
        if (clientServerMappings.containsKey(id)) {
            viewModelScope.launch(dispatcher) {
                messageRepository.softDeleteClientID(id)
            }
        }
    }

    fun chat(chat: OmniChannelMessage) {
        OmniChannelMessageTextRepository.set(chat.id, listOf(chat.text))
        viewModelScope.launch(dispatcher) {
            messageRepository.insert(chat)
            lastTimestamp = chat.timestamp
            socketService.sendChat(chat, session.value!!)
        }
    }

    fun socketConnect(accessToken: String, cityName: String) {
        this.cityName = cityName
        viewModelScope.launch(dispatcher) {
            val cachedHistory = messageRepository.getMessagesOnce()
            cachedHistory.forEach {
                OmniChannelMessageTextRepository.set(it.id, listOf(it.text))
            }
            _history.postValue(cachedHistory)
        }
        socketService.registerListener(socketListener)
        socketService.startListening(accessToken)
    }

    fun deleteAllChat() = viewModelScope.launch(dispatcher) {
        messageRepository.deleteAll()
    }

    fun socketDisconnect() {
        OmniChannelMessageTextRepository.clear()
        testSocketHandler.removeCallbacks(testSocketTask)
        socketService.stopListening()
    }

    fun logout(context: Context) {
        OmniChannelPreferenceHelper(context).clearCache()
        socketDisconnect()
        viewModelScope.launch(dispatcher) {
            messageRepository.deleteAll()
        }
    }

    fun readUnreadChats(serverChatIDs: Set<String>) {
        socketService.read(serverChatIDs, session.value!!)
        _unreadChats.postValue(setOf())

        Log.d(TAG, "readUnreadChats: $serverChatIDs")
    }

    fun resetSystemNotifications() {
        _systemNotifications.postValue(listOf())
    }

    private fun addUnreadChat(serverChatId: String) {
        val unreadServerChatIds = unreadChats.value!!.toMutableSet()
        if (!unreadServerChatIds.contains(serverChatId)) {
            unreadServerChatIds.add(serverChatId)
        }
        _unreadChats.postValue(unreadServerChatIds)
    }

    companion object {
        private const val TAG = "OmniChannelViewModel"
    }
}