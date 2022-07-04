package ai.prosa.conversa.common.data.model

import com.google.gson.annotations.SerializedName

data class UserAuth(
    val id: Int,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String
)