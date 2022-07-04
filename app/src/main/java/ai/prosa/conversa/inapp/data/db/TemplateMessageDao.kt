package ai.prosa.conversa.inapp.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TemplateMessageDao {
    @Query("SELECT * FROM template_messages ORDER BY created_at ASC")
    fun getAll(): LiveData<List<TemplateMessage>>

    @Query("SELECT * FROM template_messages ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<TemplateMessage>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(vararg templateMessage: TemplateMessage)

    @Query("UPDATE template_messages SET text = :newText WHERE id = :id")
    suspend fun updateText(id: Long, newText: String)

    @Query("DELETE from template_messages WHERE id = :id")
    suspend fun delete(id: Long)
}