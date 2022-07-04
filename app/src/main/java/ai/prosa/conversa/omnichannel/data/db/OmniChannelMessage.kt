package ai.prosa.conversa.omnichannel.data.db

import ai.prosa.conversa.common.data.model.MessageDirection
import ai.prosa.conversa.common.toDate
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageSource
import ai.prosa.conversa.omnichannel.data.model.OmniChannelMessageState
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.util.*

@Entity(tableName = "omnichannel_messages")
data class OmniChannelMessage(
    @PrimaryKey var id: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "timestamp") val timestamp: Date,
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "source") val source: OmniChannelMessageSource,
    @ColumnInfo(name = "direction") val direction: MessageDirection,
    @ColumnInfo(name = "state") var state: OmniChannelMessageState = OmniChannelMessageState.NOT_SENT,
    @ColumnInfo(name = "client_chat_id") var clientChatId: String = "",
    @ColumnInfo(name = "reply_to") var replyTo: String? = null,
    @ColumnInfo(name = "is_deleted") var isDeleted: Boolean = false
) {
    companion object {
        private const val TAG = "OmniChannelMessage"

        // All fields: https://codebeautify.org/jsonviewer/cb605805
        // TODO: simplify this process if possible
        // TODO: add fail cases
        fun parseChatbotResponse(o: JSONObject): List<OmniChannelMessage> {
            val texts = mutableListOf<String>()
            val voices = mutableListOf<String>()
            var source = ""
            var timestamp = ""
            var name = ""
            var serverChatId = ""
            if (o.has("chatbot_response")) {
                val a = o.getJSONObject("chatbot_response")
                if (a.has("response")) {
                    val b = a.getJSONObject("response")
                    if (b.has("chatbot_response")) {
                        val c = b.getJSONArray("chatbot_response")
                        (0 until c.length()).forEach { i ->
                            texts.add(c[i] as String)
                        }
                    } else {
                        texts.add(o.getString("text"))
                    }

                    if (b.has("chatbot_voice_response")) {
                        val c = b.getJSONArray("chatbot_voice_response")
                        (0 until c.length()).forEach { i ->
                            // TODO: sometimes json
                            // "chat_id":"bc1-115-0",
                            // "text":"<p>Jalan Dr. Otten 10<\/p>"
//                            voices.add(c[i] as String)
//                            Log.d(TAG, "parseChatbotResponse: VOICES [${c[i]}]")
                        }
                    }
                }
            }

            if (o.has("source")) {
                source = o.getString("source")
            }
            if (o.has("timestamp")) {
                timestamp = o.getString("timestamp")
            }
            if (o.has("name")) {
                name = o.getString("name")
            }
            if (o.has("server_chat_id")) {
                serverChatId = o.getString("server_chat_id")
            }

            var direction = MessageDirection.RECEIVE
            if (source == "USER") {
                direction = MessageDirection.SEND
            }

            return texts.map { text ->
                OmniChannelMessage(
                    id = serverChatId,
                    text = text,
                    timestamp = timestamp.toDate()!!,
                    name = name,
                    source = OmniChannelMessageSource.valueOf(source),
                    direction = direction,
                    state = OmniChannelMessageState.READ,
                    clientChatId = "",
                    replyTo = null,
                    isDeleted = false
                )
            }
        }
    }
}