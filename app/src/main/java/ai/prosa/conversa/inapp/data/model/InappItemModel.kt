package ai.prosa.conversa.inapp.data.model

import ai.prosa.conversa.inapp.data.db.RoomMessage
import java.util.*

sealed class InappItemModel {
    data class SentMessage(val message: RoomMessage): InappItemModel() {
        fun newState(state: RoomMessageState): SentMessage {
            return SentMessage(message.copy(state = state))
        }
    }
    data class ReceivedMessage(val message: RoomMessage): InappItemModel()
    data class DateMessage(val date: Date): InappItemModel()
    data class EventMessage(val message: RoomMessage): InappItemModel()
}