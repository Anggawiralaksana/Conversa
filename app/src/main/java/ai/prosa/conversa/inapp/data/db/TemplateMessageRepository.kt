package ai.prosa.conversa.inapp.data.db

import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData

class TemplateMessageRepository(private val templateMessageDao: TemplateMessageDao) {
    fun getAll(): LiveData<List<TemplateMessage>> {
        return templateMessageDao.getAll()
    }

    @WorkerThread
    suspend fun getAllOnce(): List<TemplateMessage> {
        return templateMessageDao.getAllOnce()
    }

    @WorkerThread
    suspend fun insertAll(vararg templateMessage: TemplateMessage) {
        templateMessageDao.insertAll(*templateMessage)
    }

    @WorkerThread
    suspend fun updateText(id: Long, newText: String) {
        templateMessageDao.updateText(id, newText)
    }

    @WorkerThread
    suspend fun delete(id: Long) {
        templateMessageDao.delete(id)
    }
}