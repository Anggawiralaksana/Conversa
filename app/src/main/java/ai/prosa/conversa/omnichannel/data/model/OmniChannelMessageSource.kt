package ai.prosa.conversa.omnichannel.data.model

enum class OmniChannelMessageSource {
    AGENT,
    USER,
    SYSTEM
}

fun String.toChatSource(): OmniChannelMessageSource? {
    return when {
        this == "AGENT" -> {
            OmniChannelMessageSource.AGENT
        }
        this == "USER" -> {
            OmniChannelMessageSource.USER
        }
        this == "SYSTEM" -> {
            OmniChannelMessageSource.SYSTEM
        }
        else -> null
    }
}