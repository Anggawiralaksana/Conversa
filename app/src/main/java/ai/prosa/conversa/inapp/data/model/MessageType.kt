package ai.prosa.conversa.inapp.data.model

enum class MessageType(val tag: String, val namespace: String) {
    IMAGE("image", "file:image"),
    AUDIO("audio", "file:audio"),
    TEXT("text", "file:text")
}