package ai.prosa.conversa.omnichannel.data.model

sealed class SystemNotification {
    data class HandledSession(
        val source: OmniChannelMessageSource,
        val name: String,
        val isFirstNotification: Boolean
    ) : SystemNotification()

    data class DoneSession(val name: String) : SystemNotification()
}