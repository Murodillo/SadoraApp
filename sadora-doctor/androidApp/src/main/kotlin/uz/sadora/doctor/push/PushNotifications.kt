package uz.sadora.doctor.push

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
import uz.sadora.doctor.MainActivity
import uz.sadora.doctor.R

/**
 * The one channel push arrives on — "Xabarlar" — and drawing a message while the app is
 * open, the women's app's arrangement.
 *
 * In the background the system draws an FCM notification itself, from the channel and
 * icon the manifest names, and a tap starts [MainActivity] with the message's data as
 * extras — `link` among them. In the foreground FCM hands the message to the service
 * and draws nothing, so [show] builds the same notification by hand, with the same
 * extra on its tap.
 */
object PushNotifications {

    /** The extra a tap carries: `sadora://conversation/{id}`, as the server wrote it. */
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
            // High: a patient waiting on a paid consultation should be a heads-up, not a
            // line in the shade she finds an hour later.
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, title: String?, body: String?, link: String?, notificationId: String?) {
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        if (!canPost(context)) return
        ensureChannel(context)

        // The server's id, so a retried delivery replaces the notification instead of
        // stacking a second copy — and each notification its own request code, so two
        // pushes for two patients do not share one PendingIntent and its link.
        val id = notificationId?.hashCode() ?: System.currentTimeMillis().toInt()
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { link?.let { putExtra(EXTRA_LINK, it) } },
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
