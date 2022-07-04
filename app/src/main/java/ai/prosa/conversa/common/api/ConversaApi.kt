package ai.prosa.conversa.common.api

import ai.prosa.conversa.BuildConfig
import ai.prosa.conversa.common.data.model.AsrTranscript
import ai.prosa.conversa.common.data.model.UserAuth
import ai.prosa.conversa.common.utils.ImageUtils
import android.content.Context
import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.net.URLEncoder

private interface ConversaService {
    @POST("login")
    fun login(@Body usernamePassword: UsernamePassword): Call<UserAuth>

    @Multipart
    @POST("speech-transcript")
    fun transcript(
        @Header("Authorization") authorization: String,
        @Part audio: MultipartBody.Part
    ): Call<List<List<AsrTranscript>>>

    @POST("chat-inapp-omnichannel")
    suspend fun omnichannelAuth(
        @Body useridName: OmniChannelAuthPayload
    ): OmnichannelTokenAuth

    @POST("token/check")
    fun checkToken(
        @Header("Authorization") authorization: String
    ): Call<Unit>

    @POST("token/refresh")
    fun refreshToken(
        @Header("Authorization") authorization: String
    ): Call<AccessToken>

    @DELETE("delete-chat")
    fun deleteChat(
        @Header("Authorization") authorization: String,
        @Query("chat_id") serverChatId: String
    ): Call<ApiMessageStatus>

    @Multipart
    @POST("upload-file")
    fun uploadFile(
        @Header("Authorization") authorization: String,
        @PartMap params: MutableMap<String, RequestBody>
    ): Call<Url>
}

private data class UsernamePassword(val username: String, val password: String)
private data class OmniChannelAuthPayload(
    val userid: String,
    val password: String,
    val name: String,
    @SerializedName("application_id") val applicationID: String,
    @SerializedName("avatar_url") val avatarUrl: String
)

data class OmnichannelTokenAuth(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("knowledge_groups") val knowledgeGroups: List<KnowledgeGroup>
)

private data class AccessToken(@SerializedName("access_token") val accessToken: String)
private data class Url(val url: String)

enum class JWTStatus {
    VALID,
    EXPIRED
}

data class KnowledgeGroup(val id: Int, val name: String, val description: String)
private data class ApiMessageStatus(val message: String)

fun interface ConversaApiCallback<T> {
    fun process(data: T)
}


object ConversaApi {
    private val conversaService = Retrofit.Builder()
        .baseUrl(BuildConfig.CONVERSA_API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ConversaService::class.java)
    private var userAuth: UserAuth? = null

    fun login(username: String, password: String) {
        if (userAuth == null) {
            conversaService.login(UsernamePassword(username, password))
                .enqueue(object : Callback<UserAuth> {
                    override fun onResponse(call: Call<UserAuth>, response: Response<UserAuth>) {
                        Log.d(TAG, "onResponse: ${response.raw()}")
                        userAuth = response.body()!!
                        Log.d(TAG, "onResponse: USER AUTH $userAuth")
                    }

                    // TODO: improve handling of failure
                    override fun onFailure(call: Call<UserAuth>, t: Throwable) {
                        TODO("Not yet implemented")
                    }
                })
        } else {
            Log.d(TAG, "login: Already logged in conversa")
        }
    }

    suspend fun omnichannelAuth(
        userid: String,
        password: String,
        name: String,
        applicationID: String,
        avatarUrl: String = ""
    ): OmnichannelTokenAuth {
        return conversaService.omnichannelAuth(
            OmniChannelAuthPayload(
                userid,
                password,
                name,
                applicationID,
                avatarUrl = avatarUrl
            )
        )
    }

    suspend fun uploadFile(
        context: Context,
        token: String,
        file: File,
        compress: Boolean,
        callback: (String) -> Unit
    ) {
        val theFile = if (compress) {
            ImageUtils.compress(context, file)
        } else {
            file
        }

        // TODO: use correct media type
        val fbody = RequestBody.create(MediaType.parse("image/*"), theFile)
        val map = mutableMapOf<String, RequestBody>()
        map["file\"; filename=\"" + URLEncoder.encode(file.name, "utf-8")] = fbody
        conversaService.uploadFile("Bearer $token", map)
            .enqueue(object : Callback<Url> {
                override fun onResponse(call: Call<Url>, response: Response<Url>) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "onResponse: [URL] ${response.body()!!.url}")
                        callback(response.body()!!.url)
                    } else {
                        Log.d(TAG, "onResponse: [ERROR API] ${response.errorBody()!!.string()}")
                    }
                }

                override fun onFailure(call: Call<Url>, t: Throwable) {
                    Log.d(TAG, "onFailure: $t")
                }
            })
    }

    fun deleteChat(token: String, serverChatId: String, callback: (Boolean) -> Unit) {
        conversaService.deleteChat("Bearer $token", serverChatId)
            .enqueue(object : Callback<ApiMessageStatus> {
                override fun onResponse(
                    call: Call<ApiMessageStatus>,
                    response: Response<ApiMessageStatus>
                ) {
                    callback(response.isSuccessful)
                }

                override fun onFailure(call: Call<ApiMessageStatus>, t: Throwable) {
                    TODO("Not yet implemented")
                }

            })
    }

    fun checkToken(token: String, callback: (JWTStatus) -> Unit) {
        conversaService.checkToken("Bearer $token")
            .enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    if (response.isSuccessful) {
                        callback(JWTStatus.VALID)
                    } else {
                        if (response.errorBody()?.string()!!.contains("expired")) {
                            callback(JWTStatus.EXPIRED)
                        }
                    }
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    throw t
                }
            })
    }

    fun refreshToken(refreshToken: String, callback: (JWTStatus, String) -> Unit) {
        conversaService.refreshToken("Bearer $refreshToken")
            .enqueue(object : Callback<AccessToken> {
                override fun onResponse(call: Call<AccessToken>, response: Response<AccessToken>) {
                    if (response.isSuccessful) {
                        callback(JWTStatus.VALID, response.body()!!.accessToken)
                    } else {
                        if (response.errorBody()?.string()!!.contains("expired")) {
                            callback(JWTStatus.EXPIRED, "")
                        }
                    }
                }

                override fun onFailure(call: Call<AccessToken>, t: Throwable) {
                    TODO("Not yet implemented")
                }

            })
    }

    fun transcript(path: String, apiCallback: ConversaApiCallback<List<List<AsrTranscript>>?>) {
        val auth = "Bearer ${userAuth!!.accessToken}"
        val file = File(path)
        val audioFile = MultipartBody.Part.createFormData(
            "file",
            file.name,
            RequestBody.create(MediaType.parse("audio/wav"), file)
        )
        conversaService.transcript(auth, audioFile)
            .enqueue(object : Callback<List<List<AsrTranscript>>> {
                override fun onResponse(
                    call: Call<List<List<AsrTranscript>>>,
                    response: Response<List<List<AsrTranscript>>>
                ) {
                    apiCallback.process(response.body())
                }

                override fun onFailure(call: Call<List<List<AsrTranscript>>>, t: Throwable) {
                    Log.d(TAG, "onFailure: ERROR")
                }
            })
    }

    private const val TAG = "ConversaAPI"
}