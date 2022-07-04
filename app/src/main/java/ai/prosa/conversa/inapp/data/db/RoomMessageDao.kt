package ai.prosa.conversa.inapp.data.db

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.inapp.data.model.RoomMessageState
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomMessageDao {
    @Query("SELECT * FROM room_messages WHERE id = :id ORDER BY timestamp ASC")
    suspend fun get(id: String): RoomMessage

    @Query("SELECT * FROM room_messages WHERE room_id=:roomId ORDER BY timestamp ASC ")
    fun getAllByRoom(roomId: String): Flow<List<RoomMessage>>

    @Query("DELETE FROM room_messages")
    fun  deleteAll()

    // Get list once; not as `Flow`
    @Query("SELECT * FROM room_messages WHERE room_id = (:roomId) ORDER BY timestamp ASC")
    suspend fun getAllByRoomOnce(roomId: String): List<RoomMessage>

    @Query("SELECT EXISTS(SELECT 1 FROM room_messages WHERE id = (:id))")
    suspend fun isExists(id: String): Boolean

    @Query("SELECT * FROM room_messages WHERE room_id = (:roomId) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLast(roomId: String): RoomMessage

    @Query("SELECT * FROM room_messages WHERE room_id = (:roomId) AND direction = (:direction) AND state != 'READ' ORDER BY timestamp DESC")
    suspend fun getUnreadMessages(
        roomId: String,
        direction: MessageDirection
    ): List<RoomMessage>

    @Query("SELECT * FROM room_messages WHERE room_id = (:roomId) AND direction = (:direction) AND state != 'DELIVERED' AND state != 'READ' ORDER BY timestamp DESC")
    suspend fun getUndeliveredMessages(
        roomId: String,
        direction: MessageDirection
    ): List<RoomMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(roomMessage: RoomMessage)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vararg roomMessages: RoomMessage)

    @Query("UPDATE room_messages SET state = :newState WHERE id = :id")
    suspend fun updateState(id: String, newState: RoomMessageState)
}