package ai.prosa.conversa.common.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager


object ViewUtils {
    fun createDialog(context: Context, view: View, layout: Int): Pair<AlertDialog.Builder, View> {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        val dialogView = LayoutInflater.from(context)
            .inflate(layout, view as ViewGroup, false)
        builder.setView(dialogView)
        return Pair(builder, dialogView)
    }

    fun hideKeyboard(activity: Activity, view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun showKeyboard(activity: Activity) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm!!.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }

    fun getScreenWidth(context: Context): Int {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.getRealMetrics(metrics)
        } else {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay?.getMetrics(metrics)
        }
        return metrics.widthPixels
    }

    fun getScreenHeight(context: Context): Int {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.getRealMetrics(metrics)
        } else {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay?.getMetrics(metrics)
        }
        return metrics.heightPixels
    }
}