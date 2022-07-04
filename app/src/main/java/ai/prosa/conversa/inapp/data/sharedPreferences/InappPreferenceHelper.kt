package ai.prosa.conversa.inapp.data.sharedPreferences


import ai.prosa.conversa.R
import ai.prosa.conversa.inapp.data.model.InappCache
import android.content.Context


class InappPreferenceHelper(val context: Context) {
    fun getCache(): InappCache {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.PREF_INAPP_FILE),
            Context.MODE_PRIVATE
        )

        return InappCache(
            userid = sharedPref.getString(context.getString(R.string.PREF_INAPP_USERID), "") ?: "",
            name = sharedPref.getString(context.getString(R.string.PREF_INAPP_NAME), "") ?: "",
            roomid = sharedPref.getString(context.getString(R.string.PREF_INAPP_ROOMID), "") ?: "",
            password = sharedPref.getString(context.getString(R.string.PREF_INAPP_PASSWORD), "") ?: "",
            host = sharedPref.getString(context.getString(R.string.PREF_INAPP_HOST), "") ?: "",
            port = sharedPref.getInt(context.getString(R.string.PREF_INAPP_PORT), 5222),
            domain = sharedPref.getString(context.getString(R.string.PREF_INAPP_DOMAIN), "") ?: ""
        )
    }

    fun saveCache(cache: InappCache) {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.PREF_INAPP_FILE),
            Context.MODE_PRIVATE
        )

        with(sharedPref.edit()) {
            putString(context.getString(R.string.PREF_INAPP_USERID), cache.userid)
            putString(context.getString(R.string.PREF_INAPP_NAME), cache.name)
            putString(context.getString(R.string.PREF_INAPP_ROOMID), cache.roomid)
            putString(context.getString(R.string.PREF_INAPP_PASSWORD), cache.password)
            putString(context.getString(R.string.PREF_INAPP_HOST), cache.host)
            putInt(context.getString(R.string.PREF_INAPP_PORT), cache.port)
            putString(context.getString(R.string.PREF_INAPP_DOMAIN), cache.domain)
            apply()
        }
    }

    fun clearCache() {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.PREF_INAPP_FILE),
            Context.MODE_PRIVATE
        )

        with(sharedPref.edit()) {
            putString(context.getString(R.string.PREF_INAPP_USERID), "")
            putString(context.getString(R.string.PREF_INAPP_NAME), "")
            putString(context.getString(R.string.PREF_INAPP_ROOMID), "")
            putString(context.getString(R.string.PREF_INAPP_PASSWORD), "")
            putString(context.getString(R.string.PREF_INAPP_HOST), "")
            putInt(context.getString(R.string.PREF_INAPP_PORT), 5222)
            putString(context.getString(R.string.PREF_INAPP_DOMAIN), "")
            apply()
        }
    }
}