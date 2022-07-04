package ai.prosa.conversa.common.utils

import android.os.Build
import android.text.Html

object TextUtils {
    fun cleanHtmlTag(text: String): CharSequence {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).trim()
        } else {
            Html.fromHtml(text).trim()
        }
    }
}