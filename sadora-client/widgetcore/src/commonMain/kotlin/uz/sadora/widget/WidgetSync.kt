package uz.sadora.widget

import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.WidgetSummary

/** How a widget's call to the server went, in the only terms the queue cares about. */
sealed interface WidgetCall<out T> {
    data class Ok<T>(val value: T) : WidgetCall<T>
    /** No connection, a timeout, a 5xx: try again later. */
    data object Offline : WidgetCall<Nothing>
    /** The server said no (a medicine since deleted, a day out of range): drop it. */
    data object Rejected : WidgetCall<Nothing>
    /** The session is gone: she has to open the app. */
    data object SignedOut : WidgetCall<Nothing>
}

/**
 * What a widget needs from the server. The app answers it with its own session; the iOS
 * widget extension and the watch with a companion session of their own.
 */
interface WidgetApi {
    suspend fun summary(): WidgetCall<WidgetSummary>
    suspend fun send(action: PendingAction): WidgetCall<Unit>
}

/**
 * The widget's side of the conversation: taps are queued and drawn at once, sent in the
 * order she made them, and the summary is fetched after the queue has drained — never
 * before, or it would arrive without her last glass and undo it on screen.
 */
class WidgetSync(
    private val api: WidgetApi,
    private val store: WidgetStore,
    private val clock: () -> Instant,
    private val ids: () -> String,
) {
    enum class Outcome { Updated, Offline, SignedOut }

    /** The app, a widget's button and a push may all refresh at once; one at a time. */
    private val lock = Mutex()

    suspend fun refresh(): Outcome = lock.withLock {
        val drained = drain()
        if (drained != Outcome.Updated) return@withLock drained
        when (val answer = api.summary()) {
            is WidgetCall.Ok -> {
                // Taps made while the summary was in flight stay queued on top of it.
                val pending = store.snapshot()?.pending.orEmpty()
                store.saveSnapshot(WidgetSnapshot(answer.value, clock(), pending))
                Outcome.Updated
            }
            WidgetCall.SignedOut -> signedOut()
            WidgetCall.Offline, WidgetCall.Rejected -> Outcome.Offline
        }
    }

    /** Saves a summary the app already has, so a widget is current the moment the app is. */
    suspend fun saveFromApp(summary: WidgetSummary) = lock.withLock {
        store.saveSnapshot(WidgetSnapshot(summary, clock(), store.snapshot()?.pending.orEmpty()))
    }

    suspend fun addWater(ml: Int, day: LocalDate) =
        enqueue(PendingAction.Water(ml, ids(), day))

    suspend fun markDose(medicationId: String, dueOn: LocalDate, dueAt: LocalTime, status: DoseStatus) =
        enqueue(PendingAction.Dose(medicationId, dueOn, dueAt, status))

    private suspend fun enqueue(action: PendingAction) = lock.withLock {
        val snapshot = store.snapshot() ?: return@withLock
        val kept = if (action is PendingAction.Dose) {
            // A dose is a status: the last answer is the one that counts.
            snapshot.pending.filterNot {
                it is PendingAction.Dose && it.medicationId == action.medicationId &&
                    it.dueOn == action.dueOn && it.dueAt == action.dueAt
            }
        } else {
            snapshot.pending
        }
        store.saveSnapshot(snapshot.copy(pending = kept + action))
    }

    private suspend fun drain(): Outcome {
        while (true) {
            val snapshot = store.snapshot() ?: return Outcome.Updated
            val next = snapshot.pending.firstOrNull() ?: return Outcome.Updated
            when (api.send(next)) {
                is WidgetCall.Ok, WidgetCall.Rejected -> {
                    val now = store.snapshot() ?: return Outcome.Updated
                    store.saveSnapshot(now.copy(pending = now.pending - next))
                }
                WidgetCall.Offline -> return Outcome.Offline
                WidgetCall.SignedOut -> return signedOut()
            }
        }
    }

    private fun signedOut(): Outcome {
        store.clear()
        return Outcome.SignedOut
    }
}
