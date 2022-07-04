package ai.prosa.conversa.common.api

import ai.prosa.conversa.BuildConfig
import ai.prosa.conversa.inapp.core.InappChat
import android.util.Log
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST


private interface ConversaSDKService {
    @POST("register-or-login")
    suspend fun registerOrLogin(@Body input: RegisterOrLoginPayload): RegisterOrLoginResult

    @GET("driver-template-messages")
    fun getDriverTemplateMessages(): Call<TemplateMessages>

    @GET("customer-template-messages")
    fun getCustomerTemplateMessages(): Call<TemplateMessages>

    @POST("broadcast/token")
    fun setFcmToken(@Body input: FCMTokenPayload): Call<FCMTokenResult>
}

data class FCMTokenPayload(
    @SerializedName("user_id") val userId: String,
    val token: String
)

data class FCMTokenResult(
    val version: Int
)

data class RegisterOrLoginResult(
    val id: String,
    val name: String,
    @SerializedName("avatar_url") val avatarURL: String,
    @SerializedName("application_id") val applicationID: String,
)

data class RegisterOrLoginPayload(
    val id: String,
    val password: String,
    val name: String,
    @SerializedName("avatar_url") val avatarURL: String,
    @SerializedName("application_id") val applicationID: String,
)

data class TemplateMessages(@SerializedName("template_messages") val templateMessages: List<String>)

private data class DummyTokenInput(
    val userid: String,
    @SerializedName("application_id") val applicationID: String
)

private data class NonceInput(
    val userid: String,
    @SerializedName("application_id") val applicationID: String
)


object ConversaSDKApi {
    private const val TAG = "ConversaSDKApi"

    private val conversaSdkService = Retrofit.Builder()
        .baseUrl(BuildConfig.SDK_API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ConversaSDKService::class.java)

    suspend fun registerOrLogin(
        userid: String,
        password: String,
        name: String,
        avatarURL: String,
        applicationID: String? = null
    ): Boolean {
        val appId = if (applicationID.isNullOrEmpty()) {
            InappChat.appID
        } else {
            applicationID
        }

        val result = conversaSdkService.registerOrLogin(
            RegisterOrLoginPayload(
                id = userid,
                password = password,
                name = name,
                avatarURL = avatarURL,
                applicationID = appId
            )
        )
        return true
    }

    fun setFCMToken(userid: String, token: String, callback: (Response<FCMTokenResult>) -> Unit) {
        conversaSdkService.setFcmToken(FCMTokenPayload(userid, token))
            .enqueue(object : Callback<FCMTokenResult> {
                override fun onResponse(
                    call: Call<FCMTokenResult>,
                    response: Response<FCMTokenResult>
                ) {
                    callback(response)
                }

                override fun onFailure(call: Call<FCMTokenResult>, t: Throwable) {
                    Log.d(TAG, "onFailure GET TEMPLATES: $t")
                }
            })
    }

    fun getDriverTemplateMessages(callback: (Response<TemplateMessages>) -> Unit) {
        conversaSdkService.getDriverTemplateMessages()
            .enqueue(object : Callback<TemplateMessages> {
                override fun onResponse(
                    call: Call<TemplateMessages>,
                    response: Response<TemplateMessages>
                ) {
                    callback(response)
                }

                override fun onFailure(call: Call<TemplateMessages>, t: Throwable) {
                    Log.d(TAG, "onFailure GET TEMPLATES: $t")
                }
            })
    }

    fun getCustomerTemplateMessages(callback: (Response<TemplateMessages>) -> Unit) {
        conversaSdkService.getCustomerTemplateMessages()
            .enqueue(object : Callback<TemplateMessages> {
                override fun onResponse(
                    call: Call<TemplateMessages>,
                    response: Response<TemplateMessages>
                ) {
                    callback(response)
                }

                override fun onFailure(call: Call<TemplateMessages>, t: Throwable) {
                    Log.d(TAG, "onFailure GET TEMPLATES: $t")
                }
            })
    }
}