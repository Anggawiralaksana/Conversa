package ai.prosa.conversa.omnichannel.data.model

enum class OmniChannelMessageState {
    NOT_SENT,
    SENT,
    DELIVERED,
    READ
}

data class OmniChannelChatStateUpdate(val clientChatID: String, val state: OmniChannelMessageState)