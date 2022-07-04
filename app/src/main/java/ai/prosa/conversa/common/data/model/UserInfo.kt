package ai.prosa.conversa.common.data.model

import java.io.Serializable

data class UserInfo(
    val userId: String,
    val name: String,
    val customizableMessageTemplate: Boolean,
    val avatarUrl: String = "",
    val subtitle: String = ""
): Serializable