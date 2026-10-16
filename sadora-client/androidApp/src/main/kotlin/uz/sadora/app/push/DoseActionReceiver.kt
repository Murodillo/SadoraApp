package uz.sadora.app.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.app.nav.AppLink
import uz.sadora.app.widget.sendAndRedraw
import uz.sadora.contract.DoseStatus
import uz.sadora.widget.WidgetHost

/**
 * "Taken" or "Skipped" pressed on a medication reminder. Recorded through the widgets'
 * queue, so it counts even with no connection and shows on the widget at once.
 */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dose = doseOf(intent.getStringExtra(EXTRA_LINK)) ?: return
        val taken = intent.getBooleanExtra(EXTRA_TAKEN, true)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val host = WidgetHost.get(context)
                host.sync.markDose(
                    medicationId = dose.medicationId,
                    dueOn = LocalDate.parse(dose.day),
                    dueAt = LocalTime.parse(dose.time),
                    status = if (taken) DoseStatus.TAKEN else DoseStatus.SKIPPED,
                )
                sendAndRedraw(context, host)
            } catch (_: Exception) {
                // A link with a malformed day or time: nothing to record.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_LINK = "link"
        private const val EXTRA_TAKEN = "taken"
        private const val EXTRA_NOTIFICATION = "notification"

        fun doseOf(link: String?): AppLink.Dose? = link?.let(AppLink::parse) as? AppLink.Dose

        fun intent(context: Context, notificationId: Int, dose: AppLink.Dose, taken: Boolean): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                // One per button per notification, or the second button would carry the first's extras.
                notificationId * 2 + if (taken) 0 else 1,
                Intent(context, DoseActionReceiver::class.java)
                    .putExtra(EXTRA_LINK, "sadora://dose/${dose.medicationId}/${dose.day}/${dose.time}")
                    .putExtra(EXTRA_TAKEN, taken)
                    .putExtra(EXTRA_NOTIFICATION, notificationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
