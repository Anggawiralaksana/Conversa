package ai.prosa.conversa.inapp.utils

import android.content.Context
import com.danikula.videocache.HttpProxyCacheServer

object MediaCache {
    var proxy: HttpProxyCacheServer? = null

    fun getProxyUrl(context: Context, url: String): String {
        if (proxy == null) {
            proxy = HttpProxyCacheServer(context)
        }
        return proxy!!.getProxyUrl(url)
    }
}