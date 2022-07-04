package ai.prosa.conversa.omnichannel.data.sharedPreferences

import ai.prosa.conversa.R
import ai.prosa.conversa.omnichannel.data.model.OmniChannelCache
import android.content.Context


class OmniChannelPreferenceHelper(val context: Context) {
    private val prefFile = context.getString(R.string.PREF_OMNICHANNEL_FILE)

    fun getCache(): OmniChannelCache {
        val sharedPref = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)

        return OmniChannelCache(
            userid = sharedPref.getString(
                context.getString(R.string.PREF_OMNICHANNEL_USERID),
                ""
            )!!,
            name = sharedPref.getString(
                context.getString(R.string.PREF_OMNICHANNEL_NAME),
                ""
            )!!,
            accessToken = sharedPref.getString(
                context.getString(R.string.PREF_OMNICHANNEL_ACCESS_TOKEN),
                ""
            )!!,
            refreshToken = sharedPref.getString(
                context.getString(R.string.PREF_OMNICHANNEL_REFRESH_TOKEN),
                ""
            )!!
        )
    }

    fun saveCache(cache: OmniChannelCache) {
        val sharedPref = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_USERID),
                cache.userid
            )
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_NAME),
                cache.name
            )
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_ACCESS_TOKEN),
                cache.accessToken
            )
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_REFRESH_TOKEN),
                cache.refreshToken
            )
            apply()
        }
    }

    fun clearCache() {
        val sharedPref = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_USERID),
                ""
            )
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_NAME),
                ""
            )
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_ACCESS_TOKEN),
                ""
            )
            putString(
                context.getString(R.string.PREF_OMNICHANNEL_REFRESH_TOKEN),
                ""
            )
            apply()
        }
    }

    fun getSession(): Int {
        val sharedPref = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)

        return sharedPref.getInt(
            context.getString(R.string.PREF_OMNICHANNEL_SESSION),
            -1
        )
    }

    fun setSession(session: Int) {
        val sharedPref = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            putInt(
                context.getString(R.string.PREF_OMNICHANNEL_SESSION),
                session
            )
            apply()
        }
    }

    fun resetSession() {
        setSession(-1)
    }
}