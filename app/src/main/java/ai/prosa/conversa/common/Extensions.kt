package ai.prosa.conversa.common

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Context.getCurrentLocale(): Locale? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.resources.configuration.locales[0]
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.resources.configuration.locales.get(0)
        } else {
            this.resources.configuration.locale
        }
    }
}

fun AlertDialog.resizeToMaxWidth(factor: Double) {
    val metrics = DisplayMetrics()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        this.context.display?.getRealMetrics(metrics)
    } else {
        val windowManager =
            this.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay?.getMetrics(metrics)
    }
    val viewWidth = metrics.widthPixels
    val layoutParams = WindowManager.LayoutParams()
    layoutParams.copyFrom(this.window?.attributes)
    layoutParams.width = (factor * viewWidth).toInt()
    this.window?.attributes = layoutParams
}

fun Date.formatLocaleDatetime(context: Context, format: String): String {
    val locale = context.getCurrentLocale()
    return SimpleDateFormat(format, locale).format(this)
}

fun String.toDate(context: Context): Date? {
    return SimpleDateFormat("MMMM dd, yyyy HH:mm:ss", context.getCurrentLocale()).parse(this)
}

fun String.toDate(): Date? {
    return SimpleDateFormat("MMMM dd, yyyy HH:mm:ss").parse(this)
}

val File.size get() = if (!exists()) 0.0 else length().toDouble()
val File.sizeInKb get() = size / 1024
val File.sizeInMb get() = sizeInKb / 1024
val File.sizeInGb get() = sizeInMb / 1024