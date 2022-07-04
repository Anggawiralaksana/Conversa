package ai.prosa.conversa.common.services

import ai.prosa.conversa.MainActivity
import ai.prosa.conversa.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class ConversaFirebaseMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.d(Companion.TAG, "Refreshed token: $token")

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        sendRegistrationToServer(token)
    }

    companion object {
        private const val TAG = "ConversaFirebaseM"
    }

    private fun sendRegistrationToServer(token: String) {
        //
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Check if message contains a data payload.
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${message.data} $message")
            // Check if message contains a notification payload.
            message.notification?.let {
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pendingIntent =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                    } else {
                        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)
                    }
                val channelId = "Default"
                val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(message.notification!!.title)
                    .setContentText(message.notification!!.body).setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Default channel",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    manager.createNotificationChannel(channel)
                }
                manager.notify(0, builder.build())
            }
        }
    }
}