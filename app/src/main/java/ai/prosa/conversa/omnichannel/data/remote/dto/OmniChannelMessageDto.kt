package ai.prosa.conversa.omnichannel.data.remote.dto

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.common.toDate
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessage
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageSource
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OmniChannelMessageDto(
    val text: String,
    val voice: String = "",
    // TODO: use date instead of String
    val timestamp: String,
    val name: String = "",
    val source: OmniChannelMessageSource = OmniChannelMessageSource.USER,
    var status: OmniChannelMessageState = OmniChannelMessageState.NOT_SENT,
    @SerialName("client_chat_id") var clientChatId: String = "",
    @SerialName("server_chat_id") var serverChatId: String = "",
    @SerialName("reply_to") var replyTo: String? = null,
    @SerialName("is_deleted") var isDeleted: Boolean = false
) {
    val id: String
        get() = serverChatId


}

fun OmniChannelMessageDto.toOmniChannelMessage(): OmniChannelMessage {
    var direction = MessageDirection.RECEIVE
    if (source == OmniChannelMessageSource.USER) {
        direction = MessageDirection.SEND
    }

    return OmniChannelMessage(
        id = serverChatId,
        text = text,
        timestamp = timestamp.toDate()!!,
        name = name,
        source = source,
        direction = direction,
        state = status,
        clientChatId = clientChatId,
        replyTo = replyTo,
        isDeleted = isDeleted
    )
}