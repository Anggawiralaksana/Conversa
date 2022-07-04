package ai.prosa.conversa.common.data.db

import ai.prosa.conversa.inapp.data.db.RoomMessage
import ai.prosa.conversa.inapp.data.db.RoomMessageDao
import ai.prosa.conversa.inapp.data.db.TemplateMessage
import ai.prosa.conversa.inapp.data.db.TemplateMessageDao
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessage
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessageDao
import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class DateConverter {
    @TypeConverter
    fun to(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun from(date: Date?): Long? {
        return date?.time
    }
}

class HashMapConverter {
    @TypeConverter
    fun to(value: String): Map<String, String> =
        Gson().fromJson(value, object : TypeToken<Map<String, String>>() {}.type)

    @TypeConverter
    fun from(value: Map<String, String>): String =
        Gson().toJson(value)
}

@Database(
    entities = [RoomMessage::class, TemplateMessage::class, OmniChannelMessage::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    DateConverter::class,
    HashMapConverter::class
)
abstract class ConversaDB : RoomDatabase() {
    abstract fun roomMessageDao(): RoomMessageDao
    abstract fun templateMessageDao(): TemplateMessageDao
    abstract fun omniChannelMessageDao(): OmniChannelMessageDao

    companion object {
        @Volatile
        private var INSTANCE: ConversaDB? = null

        fun getDatabase(context: Context): ConversaDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConversaDB::class.java,
                    "conversa_db"
                )
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}