package ai.prosa.conversa

import ai.prosa.conversa.common.data.db.ConversaDB
import ai.prosa.conversa.inapp.data.db.RoomMessageRepository
import ai.prosa.conversa.inapp.data.db.TemplateMessageRepository
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessageRepository
import android.app.Application

class MainApplication : Application() {
    private val conversaDB by lazy {
        ConversaDB.getDatabase(this)
    }
    val roomMessageRepository by lazy { RoomMessageRepository(conversaDB.roomMessageDao()) }
    val templateMessageRepository by lazy { TemplateMessageRepository(conversaDB.templateMessageDao()) }
    val omniChannelMessageRepository by lazy { OmniChannelMessageRepository(conversaDB.omniChannelMessageDao())}
}