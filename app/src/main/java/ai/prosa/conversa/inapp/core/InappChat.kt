package ai.prosa.conversa.inapp.core

import ai.prosa.conversa.BuildConfig
import ai.prosa.conversa.common.api.ConversaSDKApi
import ai.prosa.conversa.inapp.data.db.RoomMessageRepository
import ai.prosa.conversa.inapp.data.db.TemplateMessageRepository
import ai.prosa.conversa.inapp.exceptions.ConversaCannotConnectException
import ai.prosa.conversa.inapp.exceptions.ConversaInvalidCredentialsException
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jivesoftware.smack.*
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.sasl.SASLErrorException
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smackx.chat_markers.ChatMarkersManager
import org.jivesoftware.smackx.chat_markers.ChatMarkersState
import org.jivesoftware.smackx.chat_markers.element.ChatMarkersElements
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.impl.JidCreate
import retrofit2.HttpException
import java.io.File
import java.io.InputStream
import java.net.URL


fun String.toRoomJid(): EntityBareJid {
    return JidCreate.entityBareFrom("$this@${BuildConfig.XMPP_MULTI_CHAT_SUBDOMAIN}.${BuildConfig.XMPP_DOMAIN}")
}

fun String.toUserJid(): EntityBareJid {
    return JidCreate.entityBareFrom("$this@${BuildConfig.XMPP_DOMAIN}")
}

object InappChat {
    lateinit var userid: String
    private lateinit var password: String
    private lateinit var nickname: String
    private lateinit var avatarUrl: String
    private lateinit var chatManager: ChatManager
    lateinit var repository: RoomMessageRepository
    lateinit var templateRepository: TemplateMessageRepository
    private var _room: ChatRoom? = null
    val room get() = _room
    private var fcmToken: String? = null

    private var appID_: String = ""
    val appID get() = appID_

    // TODO: calling create chat room before this connection is established
    private lateinit var connection: AbstractXMPPConnection

    fun initialize(
        context: Context,
        appId: String,
        repository: RoomMessageRepository,
        templateRepository: TemplateMessageRepository
    ): InappChat {
        InappChat.appID_ = appId
        InappChat.repository = repository
        InappChat.templateRepository = templateRepository

        AndroidSmackInitializer.initialize(context)
        File.createTempFile("audio.wav", null, context.cacheDir)
        Log.d(TAG, "initialize: INIT")

        // Setup notification
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            fcmToken = task.result

            Log.d(TAG, "initialize: FCM TOKEN [$fcmToken]")
        })

        return this
    }

    @Throws(
        XMPPException::class,
        ConversaCannotConnectException::class,
        ConversaInvalidCredentialsException::class
    )
    suspend fun setUser(
        userid: String,
        password: String,
        name: String,
        avatarUrl: String = ""
    ): InappChat {
        InappChat.userid = userid
        InappChat.password = password
        InappChat.nickname = name
        InappChat.avatarUrl = avatarUrl

        // Set FCM token
        if (fcmToken != null) {
            ConversaSDKApi.setFCMToken(userid, fcmToken!!) {}
        }

        // Create or login user
        GlobalScope.launch(Dispatchers.IO) {
            try {
                ConversaSDKApi.registerOrLogin(userid, password, name, avatarUrl)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    // TODO: can't catch this exception from outer scope
                    throw ConversaInvalidCredentialsException()
                }
            }
            Log.d(
                TAG,
                "setUser: $userid ${BuildConfig.XMPP_HOST}:${BuildConfig.XMPP_PORT} Domain = ${BuildConfig.XMPP_DOMAIN}$"
            )
            val connectionConfiguration = XMPPTCPConnectionConfiguration.builder()
                .setHost(BuildConfig.XMPP_HOST)
                .setXmppDomain(BuildConfig.XMPP_DOMAIN)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
                .setConnectTimeout(20000)
                .build()

            SASLAuthentication.unBlacklistSASLMechanism("PLAIN")
            SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5")

            try {
                val connection: AbstractXMPPConnection =
                    XMPPTCPConnection(connectionConfiguration).connect()
                InappChat.connection = connection

                try {
                    InappChat.connection.login(userid, password)
                    chatManager = ChatManager.getInstanceFor(InappChat.connection)
                } catch (e: SASLErrorException) {
                    throw ConversaInvalidCredentialsException()
                }
            } catch (e: SmackException.SmackMessageException) {
                e.localizedMessage?.let {
                    if (it.contains("END_DOCUMENT")) {
                        throw ConversaCannotConnectException(
                            BuildConfig.XMPP_HOST,
                            BuildConfig.XMPP_PORT
                        )
                    }
                }
            }
        }.join()
        return this
    }

    fun clearUser() {
        GlobalScope.launch(Dispatchers.IO) {
            repository.deleteAll()
            _room?.leave()
        }
    }

    suspend fun setRoom(roomId: String): ChatRoom {
        if (_room == null) {
            _room = ChatRoom(roomId, nickname, connection, repository)
        }
        _room!!.join()
        _room!!.getHistory(true)
        return _room!!
    }

    fun addChatMarkerListener(processMarker: (from: String, state: ChatMarkersState, messageId: String) -> Unit) {
        val manager = ChatMarkersManager.getInstanceFor(connection)
        manager.addIncomingChatMarkerMessageListener { state, message, _ ->
            val id = when (state) {
                ChatMarkersState.received -> ChatMarkersElements.ReceivedExtension.from(message).id
                ChatMarkersState.displayed -> ChatMarkersElements.DisplayedExtension.from(message).id
                ChatMarkersState.acknowledged -> ChatMarkersElements.AcknowledgedExtension.from(
                    message
                ).id
                else -> ""
            }

            val from = message.from.resourceOrEmpty.toString()
            processMarker(from, state, id)
        }
    }

    internal fun httpUpload(
        stream: InputStream,
        fileName: String,
        fileSize: Int
    ): URL {
        val uploadManager = HttpFileUploadManager.getInstanceFor(connection)

        // Example of put url: https://localhost:5443/upload/5a240f4821c60a0a3871e85c4cdbb329fc8571e6/FJwbHF6MWDTpU4cWyqUSxandkXA61Tifg2GRozK3/image.png
        return uploadManager.uploadFile(
            stream,
            fileName,
            fileSize.toLong()
        )
        { _, _ ->
            // params: uploadedBytes, totalBytes
        }
    }

    private const val TAG = "Conversa"
}