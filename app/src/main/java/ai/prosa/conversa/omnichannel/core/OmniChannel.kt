package ai.prosa.conversa.omnichannel.core

import ai.prosa.conversa.common.api.ConversaApi
import ai.prosa.conversa.inapp.exceptions.ConversaInvalidCredentialsException
import ai.prosa.conversa.omnichannel.data.db.OmniChannelMessageRepository
import ai.prosa.conversa.omnichannel.data.sharedPreferences.OmniChannelPreferenceHelper
import android.content.Context
import android.util.Log

object OmniChannel {
    private var appID_: String = ""
    val appID get() = appID_
    lateinit var userid: String
    private lateinit var password: String
    private lateinit var name: String
    private lateinit var avatarUrl: String
    lateinit var accessToken: String
    private lateinit var refreshToken: String

    lateinit var repository: OmniChannelMessageRepository

    private const val TAG = "OmniChannel"

    fun initialize(
        appId: String,
        repository: OmniChannelMessageRepository,
    ): OmniChannel {
        this.appID_ = appId
        this.repository = repository

        return this
    }

    suspend fun setUser(
        context: Context,
        userid: String,
        password: String,
        name: String,
        avatarUrl: String = ""
    ): OmniChannel {
        this.userid = userid
        this.password = password
        this.name = name
        this.avatarUrl = avatarUrl

        val cache = OmniChannelPreferenceHelper(context).getCache()
        try {
            val tokenAuth = ConversaApi.omnichannelAuth(userid, password, name, appID_, avatarUrl)
            cache.accessToken = tokenAuth.accessToken
            cache.refreshToken = tokenAuth.refreshToken
            cache.name = name
            cache.userid = userid
            OmniChannelPreferenceHelper(context).saveCache(cache)

            this.accessToken = tokenAuth.accessToken
            this.refreshToken = tokenAuth.refreshToken

            Log.d(TAG, "setUser: Get Access TOKEN")
        } catch (e: retrofit2.HttpException) {
            Log.d(TAG, "setUser: ${e.code()}")
            Log.d(TAG, "setUser: ${e.response()}")
            Log.d(TAG, "setUser: ${e.message()}")

            throw ConversaInvalidCredentialsException()
        }

        return this
    }
}