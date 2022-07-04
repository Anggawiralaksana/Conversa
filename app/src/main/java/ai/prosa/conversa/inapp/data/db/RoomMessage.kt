package ai.prosa.conversa.inapp.data.db

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.inapp.data.model.MessageType
import ai.prosa.conversa.inapp.data.model.RoomMessageState
import android.util.Log
import android.util.Xml
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.StandardExtensionElement
import org.jivesoftware.smackx.delay.packet.DelayInformation
import org.xmlpull.v1.XmlPullParser
import java.util.*

@Entity(tableName = "room_messages")
data class RoomMessage(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "room_id") val roomId: String, // This is *ONLY* the localpart
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "type") val type: MessageType,
    @ColumnInfo(name = "timestamp") val timestamp: Date,
    @ColumnInfo(name = "attributes") var attrs: Map<String, String>,
    @ColumnInfo(name = "direction") val direction: MessageDirection,
    @ColumnInfo(name = "state") var state: RoomMessageState = RoomMessageState.NEW
) {
    companion object {
        private fun getMessageTimestamp(msg: Message): Date {
            val delay = msg.getExtension("urn:xmpp:delay")
            return if (delay == null) {
                Date()
            } else {
                (delay as DelayInformation).stamp
            }

        }

        fun from(msg: Message, currentUserId: String, isFromArchive: Boolean): RoomMessage {
            val imageExtRaw = msg.getExtension(MessageType.IMAGE.namespace)
            val audioExtRaw = msg.getExtension(MessageType.AUDIO.namespace)

            var attributes = mutableMapOf<String, String>()
            val type = when {
                imageExtRaw != null -> {
                    val ext = imageExtRaw as StandardExtensionElement
                    attributes = ext.attributes
                    MessageType.IMAGE
                }
                audioExtRaw != null -> {
                    val ext = audioExtRaw as StandardExtensionElement
                    attributes = ext.attributes
                    MessageType.AUDIO
                }
                else -> {
                    MessageType.TEXT
                }
            }

            val timestamp = if (isFromArchive) {
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setInput(msg.toXML().toString().byteInputStream(), null)
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) {
                        continue
                    }

                    if (parser.name == "archived") {
                        val milliseconds = parser.getAttributeValue(null, "id").toLong() / 1000
                        Date(milliseconds)
                        break
                    }
                }
                // Should not be reached
                Date()
            } else {
                getMessageTimestamp(msg)
            }

            val from = msg.from.resourceOrEmpty.toString()
            val direction = when {
                currentUserId == from -> {
                    MessageDirection.SEND
                }
                from != "admin" -> {
                    MessageDirection.RECEIVE
                }
                // Receive but from user is "admin"
                // TODO: should've used message type "headline" instead of basing it of userid
                else -> {
                    MessageDirection.EVENT
                }
            }


            Log.d(TAG, "from: $msg $from $direction")
            return RoomMessage(
                msg.stanzaId,
                msg.to.localpartOrNull.toString(),
                msg.body,
                msg.from.resourceOrEmpty.toString(),
                type,
                timestamp,
                attributes,
                direction
            )
        }

        private const val TAG = "RoomMessage"
    }
}