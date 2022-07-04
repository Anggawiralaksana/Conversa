package ai.prosa.conversa.omnichannel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventChatPayload(
    val text: String,
    val room: Int,
    @SerialName("client_chat_id") val clientChatId: String,
    val source: String = "USER",
    @SerialName("reply_to") var replyTo: String? = null
)