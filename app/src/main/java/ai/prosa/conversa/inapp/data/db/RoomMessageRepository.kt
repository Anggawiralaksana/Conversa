package ai.prosa.conversa.inapp.data.db

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.inapp.data.model.RoomMessageState
import android.util.Log
import androidx.annotation.WorkerThread

class RoomMessageRepository(private val roomMessageDao: RoomMessageDao) {
    @WorkerThread
    suspend fun get(messageId: String): RoomMessage {
        return roomMessageDao.get(messageId)
    }

    @WorkerThread
    suspend fun getInitRoomMessages(roomId: String): List<RoomMessage> {
        return roomMessageDao.getAllByRoomOnce(roomId)
    }

    @WorkerThread
    suspend fun getUnreadMessages(roomId: String, direction: MessageDirection): List<RoomMessage> {
        return roomMessageDao.getUnreadMessages(
            roomId,
            direction
        )
    }

    @WorkerThread
    suspend fun getUndeliveredMessages(
        roomId: String,
        direction: MessageDirection
    ): List<RoomMessage> {
        return roomMessageDao.getUndeliveredMessages(
            roomId,
            direction
        )
    }

    @WorkerThread
    suspend fun isSavedLocally(messageId: String): Boolean {
        return roomMessageDao.isExists(messageId)
    }

    @WorkerThread
    suspend fun insert(roomMessage: RoomMessage) {
        roomMessageDao.insert(roomMessage)
    }

    @WorkerThread
    suspend fun upgradeState(messageId: String, newState: RoomMessageState) {
        // Only upgrade the state
        try {
            val currentMessage = get(messageId)
            if (currentMessage.state < newState) {
                roomMessageDao.updateState(messageId, newState)
            }
        } catch (e: NullPointerException) {
            Log.d(TAG, "upgradeState: [E] $e")
        }

    }

    fun deleteAll() {
        roomMessageDao.deleteAll()
    }

    companion object {
        private const val TAG = "RoomMessageRepository"
    }
}