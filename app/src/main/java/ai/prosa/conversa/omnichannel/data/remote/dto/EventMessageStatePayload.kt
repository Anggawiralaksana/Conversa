package ai.prosa.conversa.omnichannel.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EventMessageStatePayload(
    val room: String,
    @SerialName("server_chat_id") val serverChatId: String
)