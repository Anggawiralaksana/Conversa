package ai.prosa.conversa.omnichannel.data.db

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageSource
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageState
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Dao
interface OmniChannelMessageDao {
    @Query("SELECT * FROM omnichannel_messages WHERE id = :id")
    suspend fun get(id: String): OmniChannelMessage

    @Query("SELECT * FROM omnichannel_messages ORDER BY timestamp ASC ")
    fun getAll(): Flow<List<OmniChannelMessage>>

    @Query("SELECT * FROM omnichannel_messages ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<OmniChannelMessage>

    @Query("SELECT EXISTS(SELECT 1 FROM omnichannel_messages WHERE id = (:id))")
    suspend fun isExists(id: String): Boolean

    @Query("SELECT * FROM omnichannel_messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLast(): OmniChannelMessage

    @Query("SELECT * FROM omnichannel_messages WHERE client_chat_id = (:clientID) ORDER BY timestamp DESC LIMIT 1")
    suspend fun getByClientID(clientID: String): OmniChannelMessage

    @Query("SELECT * FROM omnichannel_messages WHERE direction = (:direction) AND state != (:state) ORDER BY timestamp DESC")
    suspend fun getMessagesByDirectionAndMaxState(
        direction: MessageDirection,
        state: OmniChannelMessageState
    ): List<OmniChannelMessage>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: OmniChannelMessage)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(vararg messages: OmniChannelMessage)

    @Query("UPDATE omnichannel_messages SET state = :newState WHERE id = :id")
    suspend fun updateState(id: String, newState: OmniChannelMessageState)

    @Query("UPDATE omnichannel_messages SET state = :newState WHERE id = :clientChatID")
    suspend fun updateStateByClientID(clientChatID: String, newState: OmniChannelMessageState)

    @Query("UPDATE omnichannel_messages SET id = :id, text = :text, name = :name, source = :source, direction = :direction, state = :state, client_chat_id = :clientChatID, reply_to = :replyTo, is_deleted = :isDeleted WHERE id = :oldId")
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
    )

    @Query("UPDATE omnichannel_messages SET id = :id WHERE id = :oldId")
    suspend fun updateID(
        oldId: String,
        id: String,
    )

    @Query("UPDATE omnichannel_messages SET is_deleted = 1 WHERE id = :id")
    suspend fun softDeleteServerID(
        id: String,
    )

    @Query("UPDATE omnichannel_messages SET is_deleted = 1 WHERE client_chat_id = :clientChatID")
    suspend fun softDeleteClientID(
        clientChatID: String,
    )

    @Query("DELETE FROM omnichannel_messages")
    suspend fun deleteAll()
}