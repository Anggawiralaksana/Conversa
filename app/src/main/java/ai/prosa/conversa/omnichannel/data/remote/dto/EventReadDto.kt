package ai.prosa.conversa.omnichannel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventReadDto(
    @SerialName("client_chat_id") val clientChatId: String,
    @SerialName("server_chat_id") val serverChatId: String,
    val source: String,
    val room: Int
)