package ai.prosa.conversa.omnichannel.data.db

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageSource
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageState
import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.Flow

class OmniChannelMessageRepository(private val omniChannelMessageDao: OmniChannelMessageDao) {
    @WorkerThread
    suspend fun get(messageId: String): OmniChannelMessage {
        return omniChannelMessageDao.get(messageId)
    }

    @WorkerThread
    suspend fun getMessagesOnce(): List<OmniChannelMessage> {
        return omniChannelMessageDao.getAllOnce()
    }

    @WorkerThread
    fun getMessages(): Flow<List<OmniChannelMessage>> {
        return omniChannelMessageDao.getAll()
    }

    @WorkerThread
    suspend fun getUnreadMessages(direction: MessageDirection): List<OmniChannelMessage> {
        return omniChannelMessageDao.getMessagesByDirectionAndMaxState(
            direction,
            OmniChannelMessageState.READ
        )
    }

    @WorkerThread
    suspend fun isSavedLocally(messageId: String): Boolean {
        return omniChannelMessageDao.isExists(messageId)
    }

    @WorkerThread
    suspend fun insert(message: OmniChannelMessage) {
        omniChannelMessageDao.insert(message)
    }

    @WorkerThread
    suspend fun insert(vararg messages: OmniChannelMessage) {
        omniChannelMessageDao.insert(*messages)
    }

    @WorkerThread
    suspend fun insert(messages: List<OmniChannelMessage>) {
        omniChannelMessageDao.insert(*messages.toTypedArray())
    }

    @WorkerThread
    suspend fun upgradeState(messageId: String, newState: OmniChannelMessageState) {
        // Only upgrade the state
        val currentMessage = get(messageId)
        if (currentMessage.state < newState) {
            omniChannelMessageDao.updateState(messageId, newState)
        }
    }

    @WorkerThread
    suspend fun upgradeStateByClientID(clientID: String, newState: OmniChannelMessageState) {
        omniChannelMessageDao.updateStateByClientID(clientID, newState)
    }

    @WorkerThread
    suspend fun getByClientID(clientID: String): OmniChannelMessage {
        return omniChannelMessageDao.getByClientID(clientID)
    }

    @WorkerThread
    suspend fun updateMessage(
        oldId: String,
        id: String,
        text: String,
        name: String,
        source: OmniChannelMessageSource,
        direction: MessageDirection,
        state: OmniChannelMessageState,
        clientChatID: String,
        replyTo: String?,
        isDeleted: Boolean
    ) {
        return omniChannelMessageDao.updateMessage(
            oldId,
            id,
            text,
            name,
            source,
            direction,
            state,
            clientChatID,
            replyTo,
            isDeleted
        )
    }

    @WorkerThread
    suspend fun updateID(
        oldId: String,
        id: String
    ) {
        return omniChannelMessageDao.updateID(
            oldId,
            id
        )
    }

    @WorkerThread
    suspend fun softDeleteServerID(
        id: String
    ) {
        return omniChannelMessageDao.softDeleteServerID(id)
    }

    @WorkerThread
    suspend fun softDeleteClientID(
        clientChatID: String
    ) {
        return omniChannelMessageDao.softDeleteClientID(clientChatID)
    }


    @WorkerThread
    suspend fun deleteAll() {
        omniChannelMessageDao.deleteAll()
    }
}