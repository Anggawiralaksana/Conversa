package ai.prosa.conversa.omnichannel.data.model

import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessage
import android.net.Uri

sealed class OmniChannelItemModel {
    data class SentMessage(
        val message: OmniChannelMessage,
        val replyCallback: (chat: OmniChannelMessage) -> Unit,
        val attachmentCallback: (uri: Uri, name: String, extension: String) -> Unit,
        val deleteCallback: (chat: OmniChannelMessage, position: Int) -> Unit,
        val copyCallback: (chat: OmniChannelMessage) -> Boolean
    ) : OmniChannelItemModel() {
        fun softDelete(): OmniChannelItemModel {
            val newMessage = message.copy(isDeleted = true)
            return SentMessage(
                newMessage,
                replyCallback, attachmentCallback, deleteCallback, copyCallback
            )
        }
    }

    data class ReceivedMessage(
        val message: OmniChannelMessage,
        val replyCallback: (chat: OmniChannelMessage) -> Unit,
        val attachmentCallback: (uri: Uri, name: String, extension: String) -> Unit,
        val copyCallback: (chat: OmniChannelMessage) -> Boolean
    ) : OmniChannelItemModel()

    data class SystemNotification(
        val message: String
    ) : OmniChannelItemModel()
}