package uz.sadora.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.contract.DoseStatus
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uz.sadora.widget.WidgetHost
import uz.sadora.widget.WidgetSync

/**
 * A tap on a widget's button. The glass or the dose is counted and drawn at once, then
 * sent; with no connection it waits in the queue and the widget already shows it.
 */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val host = WidgetHost.get(context)
        host.sync.addWater(parameters[Ml] ?: return, host.today())
        sendAndRedraw(context, host)
    }

    companion object {
        val Ml = ActionParameters.Key<Int>("ml")
    }
}

class MarkDoseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val host = WidgetHost.get(context)
        host.sync.markDose(
            medicationId = parameters[Medication] ?: return,
            dueOn = LocalDate.parse(parameters[Day] ?: return),
            dueAt = LocalTime.parse(parameters[Time] ?: return),
            status = DoseStatus.TAKEN,
        )
        sendAndRedraw(context, host)
    }

    companion object {
        val Medication = ActionParameters.Key<String>("medication")
        val Day = ActionParameters.Key<String>("day")
        val Time = ActionParameters.Key<String>("time")
    }
}

/**
 * Drawn twice: once with the tap queued, once with the server's answer. With no answer
 * the tap stays queued and [FlushWorker] sends it once the network is back.
 */
internal suspend fun sendAndRedraw(context: Context, host: WidgetHost) {
    SadoraWidget.redrawAll(context)
    val outcome = withTimeoutOrNull(SEND_BUDGET_MS) { host.sync.refresh() }
    if (outcome != WidgetSync.Outcome.Updated && host.store.snapshot()?.pending?.isNotEmpty() == true) {
        FlushWorker.schedule(context)
    }
    SadoraWidget.redrawAll(context)
}

/** Sends the widgets' queued taps once there is a connection, then redraws. */
class FlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val host = WidgetHost.get(applicationContext)
        val outcome = host.sync.refresh()
        SadoraWidget.redrawAll(applicationContext)
        return if (outcome == WidgetSync.Outcome.Offline) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<FlushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("widget_flush", ExistingWorkPolicy.KEEP, request)
        }
    }
}

private const val SEND_BUDGET_MS = 15_000L
