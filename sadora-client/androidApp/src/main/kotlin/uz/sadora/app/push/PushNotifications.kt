package uz.sadora.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uz.sadora.app.MainActivity
import uz.sadora.app.R

/**
 * The one channel push arrives on, and drawing a message while the app is open.
 *
 * In the background the system draws an FCM notification itself, from the channel and
 * icon the manifest names. In the foreground FCM hands the message to the service and
 * draws nothing, so [show] builds the same notification by hand.
 */
object PushNotifications {

    /**
     * Where a tap lands, as the server put it in the data payload: `sadora://conversation/{id}`.
     * The same key in both paths — in the background the system copies the data payload
     * onto the launch intent as extras, and [show] puts it there by hand.
     */
    const val EXTRA_LINK = "link"

    /**
     * Created before anything is posted. A notification naming a channel that does not
     * exist is dropped on Android 8+, and FCM would fall back to a channel of its own
     * called "Miscellaneous".
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            context.getString(R.string.push_channel_id),
            context.getString(R.string.push_channel_name),
            // High, so a medication reminder at the time she chose shows as a heads-up
            // rather than waiting in the shade.
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, title: String?, body: String?, notificationId: String?, link: String? = null) {
        if (!canPost(context)) return
        ensureChannel(context)

        // The server's id, so a retried delivery replaces the notification instead of
        // stacking a second copy of the same reminder.
        val id = notificationId?.hashCode() ?: System.currentTimeMillis().toInt()
        // One request code per notification: with a shared one, FLAG_UPDATE_CURRENT gave
        // every notification still in the shade the newest one's link.
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { if (link != null) putExtra(EXTRA_LINK, link) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, context.getString(R.string.push_channel_id))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Refused permission on Android 13+, or notifications switched off in settings. */
    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
