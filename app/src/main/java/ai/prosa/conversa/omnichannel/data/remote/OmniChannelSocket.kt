package ai.prosa.conversa.omnichannel.data.remote

import ai.prosa.conversa.BuildConfig
import ai.prosa.conversa.common.BaseObservable
import ai.prosa.conversa.common.utils.Random
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessage
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageSource
import ai.prosa.conversa.omnichannel.data.model.toChatSource
import ai.prosa.conversa.omnichannel.data.remote.dto.*
import ai.prosa.conversa.omnichannel.viewmodels.json
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit


val json = Json { ignoreUnknownKeys = true }

const val EVENT_CONNECT = Socket.EVENT_CONNECT
const val EVENT_CONNECT_ERROR = Socket.EVENT_CONNECT_ERROR
const val EVENT_SESSION_ID = "session_id"
const val EVENT_HISTORY = "history"
const val EVENT_SERVER_CHAT_ID = "server-chat-id"
const val EVENT_CHAT = "chat"
const val EVENT_READ = "read"
const val EVENT_HANDLED = "handled"
const val EVENT_DONE_SESSION = "done-session"
const val EVENT_TEST = "test"


interface SocketEventListener {
    fun onConnect()
    fun onSessionId(sessionId: Int, isNew: Boolean)
    fun onHistory(history: List<OmniChannelMessage>)
    fun onServerChatID(serverChatID: String, clientChatID: String)
    fun onChat(chats: List<OmniChannelMessage>, source: OmniChannelMessageSource)
    fun onRead(readDto: EventReadDto)

    // isFirstNotification: Agent just handled it now
    fun onHandled(name: String, source: OmniChannelMessageSource, isFirstNotification: Boolean)
    fun onDoneSession(name: String)

    fun onTest()
}

@Serializable
class LocationPayload(
    val location: String
)

class SocketInterceptor(val accessToken: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val newRequest = request.newBuilder()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return chain.proceed(newRequest)
    }
}

class SocketService : BaseObservable<SocketEventListener>() {
    private lateinit var socket: Socket
    private var isConnected = false

    fun startListening(accessToken: String) {
        val socketOptions = IO.Options().apply {
            callFactory = OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(SocketInterceptor(accessToken))
                .build()
            path = BuildConfig.CHATBOT_BACKEND_SOCKET_PATH
        }

        socket = IO.socket(
            BuildConfig.CHATBOT_BACKEND_HOST,
            socketOptions
        )

        socket.on(EVENT_CONNECT, connectListener)
        socket.on(EVENT_SESSION_ID, sessionIdListener)
        socket.on(EVENT_HISTORY, onHistoryListener)
        socket.on(EVENT_SERVER_CHAT_ID, onServerChatIDListener)
        socket.on(EVENT_CHAT, onChatListener)
        socket.on(EVENT_READ, onReadListener)
        socket.on(EVENT_HANDLED, onHandledListener)
        socket.on(EVENT_DONE_SESSION, onDoneSessionListener)
        socket.on(EVENT_TEST, onTest)
        socket.connect()
    }

    private val connectListener = Emitter.Listener {
        isConnected = true
        for (listener in listeners) listener.onConnect()
    }

    private val sessionIdListener = Emitter.Listener { args ->
        try {
            for (listener in listeners) listener.onSessionId(args[1] as Int, false)
        } catch (e: ArrayIndexOutOfBoundsException) {
            for (listener in listeners) listener.onSessionId(args[0] as Int, false)
        }
    }

    // TODO: return unread chats
    private val onHistoryListener = Emitter.Listener { args: Array<Any> ->
        val histRaw = args[1] as JSONArray
        Log.d(TAG, "[onHistoryListener]: ${args[0]} - ${histRaw.length()}")
        val history = ((0 until histRaw.length()).map { it ->
            val chat = json.decodeFromString<OmniChannelMessageDto>((histRaw[it]).toString())
            OmniChannelMessageTextRepository.set(
                chat.id,
                listOf(chat.toOmniChannelMessage()).map { it.text })
            chat.toOmniChannelMessage()
        }).toList()
        for (listener in listeners) listener.onHistory(history)
    }


    private val onServerChatIDListener = Emitter.Listener { args ->
        val obj = args[0] as JSONObject
        val clientChatID = obj.getString("client_chat_id")
        val serverChatID = obj.getString("server_chat_id")

        for (listener in listeners) listener.onServerChatID(serverChatID, clientChatID)
    }

    private val onChatListener = Emitter.Listener { args ->
        val o = try {
            args[0] as JSONObject
        } catch (e: ClassCastException) {
            args[1] as JSONObject
        }

        if (o.has("chatbot_response")) {
            val chatbotChats = OmniChannelMessage.parseChatbotResponse(o).toMutableList()
            chatbotChats.forEach {
                if (it.id.isEmpty()) {
                    it.id = "srv-${Random.randomInteger()}"
                }
            }
            OmniChannelMessageTextRepository.set(chatbotChats[0].id, chatbotChats.map { it.text })
            for (listener in listeners) listener.onChat(
                chatbotChats,
                source = OmniChannelMessageSource.SYSTEM
            )
        } else {
            val chat = json.decodeFromString<OmniChannelMessageDto>(o.toString())
            // TODO: message received by server
            if (chat.source == OmniChannelMessageSource.AGENT) {
                OmniChannelMessageTextRepository.set(
                    chat.serverChatId,
                    listOf(chat.toOmniChannelMessage()).map { it.text })
                for (listener in listeners) listener.onChat(
                    listOf(chat.toOmniChannelMessage()),
                    source = chat.source
                )
            }
        }
    }

    private val onReadListener = Emitter.Listener { args ->
        val readEventData = json.decodeFromString<EventReadDto>((args[0] as JSONObject).toString())
        for (listener in listeners) listener.onRead(readEventData)
    }

    private val onHandledListener = Emitter.Listener { args ->
        try {
            val obj = args[0] as JSONObject
            val name = obj.getString("name")
            obj.getString("type").toChatSource()?.let {
                for (listener in listeners) listener.onHandled(name, it, true)
            }
        } catch (e: ClassCastException) {
            val obj = args[1] as JSONObject
            val name = obj.getString("name")
            obj.getString("type").toChatSource()?.let {
                for (listener in listeners) listener.onHandled(name, it, false)
            }
        }
    }

    private val onDoneSessionListener = Emitter.Listener { args ->
        val name = args[0] as String
        for (listener in listeners) listener.onDoneSession(name)
    }

    private val onTest = Emitter.Listener { args ->
        for (listener in listeners) listener.onTest()
    }


    fun sendChat(messageSerialized: OmniChannelMessage, sessionID: Int) {
        val chatPayload =
            EventChatPayload(
                messageSerialized.text,
                sessionID,
                messageSerialized.clientChatId,
                replyTo = messageSerialized.replyTo
            )
        socket.emit(EVENT_CHAT, Json.encodeToString(chatPayload))
    }

    fun read(serverChatIds: Set<String>, sessionID: Int) {
        serverChatIds.forEach {
            socket.emit(
                "read",
                Json.encodeToString(EventMessageStatePayload(sessionID.toString(), it))
            )
        }
    }

    fun ping() {
        socket.emit(EVENT_TEST)
    }

    fun stopListening() {
        if (isConnected) {
            Log.d(TAG, "Try to disconnect socket")
            socket.disconnect()
        }
    }

    fun sendLocation(cityName: String) {
        socket.emit(
            "handle-location",
            Json.encodeToString(LocationPayload(cityName))
        )
    }

    companion object {
        private const val TAG = "SocketService"
    }
}