package ai.prosa.conversa.inapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "template_messages")
data class TemplateMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Date,
)