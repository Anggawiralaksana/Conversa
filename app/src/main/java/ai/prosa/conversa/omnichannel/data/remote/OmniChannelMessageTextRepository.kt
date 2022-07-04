package ai.prosa.conversa.omnichannel.data.remote

object OmniChannelMessageTextRepository {
    private var chats = mutableMapOf<String, List<String>>()

    fun get(id: String): List<String>? {
        return chats[id]
    }

    fun set(id: String, chats: List<String>) {
        this.chats[id] = chats
    }

    fun contains(id: String): Boolean {
        return chats.containsKey(id)
    }

    fun isEmpty(): Boolean {
        return chats.isEmpty()
    }

    fun clear() {
        chats.clear()
    }
}