package uz.sadora.app.data

import kotlinx.datetime.todayIn
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import uz.sadora.app.model.AppStateSync
import uz.sadora.app.model.Meal
import uz.sadora.app.model.Mood
import uz.sadora.app.model.PracticeKind
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.MindPracticeKind
import uz.sadora.contract.SymptomEntry

/**
 * Carries local edits to the server.
 *
 * Every method returns immediately and launches the request, because these are called
 * from the middle of a Compose click handler: the screen has already applied the change
 * optimistically, and the controller's refresh replaces it with whatever the server
 * actually stored.
 */
class HealthSync(
    private val health: HealthController,
    private val scope: CoroutineScope,
) : AppStateSync {

    /**
     * One write to the day at a time, in the order she made them.
     *
     * The day goes up whole, so two chips tapped quickly each used to send a list built
     * from the same stale record — `[A]`, then `[B]` — and the server kept whichever
     * landed last while the other chip deselected itself. The mutex is fair, and each
     * write is built inside it from what the previous one left behind.
     */
    private val dayWrites = Mutex()

    override fun symptomToggled(label: String, nowSelected: Boolean) {
        val date = health.selectedDate ?: return
        val key = resolveSymptomKey(label) ?: return

        scope.launch {
            dayWrites.withLock {
                // The day goes up whole, built from the record in hand. With no record —
                // the first load failed offline — a one-chip write used to replace the
                // server's row with a day holding that chip and nothing else.
                if (!health.hasDayRecord(date)) return@withLock
                val current = health.day?.symptoms.orEmpty()
                val updated = if (nowSelected) {
                    if (current.any { it.key == key }) current else current + SymptomEntry(key)
                } else {
                    current.filterNot { it.key == key }
                }
                health.saveDay(date, symptomKeys = updated)
            }
        }
    }

    /**
     * The store holds labels because that is what the sheet renders; the catalogue turns
     * one back into the key the API expects. An unknown label is a symptom the server
     * does not offer, so there is nothing to send.
     */
    internal fun resolveSymptomKey(label: String): String? =
        health.symptoms.firstOrNull { it.label == label }?.key

    private val nutritionWrites = Mutex()

    override fun waterAdded(ml: Int) {
        // In order, and one at a time: two quick glasses used to read the total back
        // between them and the number on screen went 500, 250, 500. A write that fails
        // reads the server's total back, so the optimistic glass does not stay on screen.
        scope.launch { nutritionWrites.withLock { if (!health.addWater(ml)) health.refreshNutrition() } }
    }

    /**
     * The store's id packs the course and the time together, because a twice-daily
     * course is two rows on the screen and one medication on the server.
     */
    override fun doseTaken(doseId: String) = recordDose(doseId, DoseStatus.TAKEN)

    override fun doseSkipped(doseId: String) = recordDose(doseId, DoseStatus.SKIPPED)

    private fun recordDose(doseId: String, status: DoseStatus) {
        val date = health.doses?.date ?: health.selectedDate
        val medicationId = doseId.substringBefore('@')
        val dueAt = runCatching { LocalTime.parse(doseId.substringAfter('@', "")) }.getOrNull()
        scope.launch {
            // The store ticked the dose off before this ran. When the write cannot be
            // made, or fails, the row goes back to what the server last said — a dose
            // shown as taken that the server never heard of would also keep its reminder.
            val saved = date != null && dueAt != null && health.recordDose(medicationId, date, dueAt, status)
            if (!saved) health.refreshMedications()
        }
    }

    override fun mealLogged(meal: Meal) {
        // The server's today when it is known, the phone's otherwise: with no date this
        // returned early, and a meal logged after a failed first load vanished silently
        // at the next refresh.
        val date = health.selectedDate ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
        scope.launch { nutritionWrites.withLock {
            val saved = health.addMeal(
                LogMealRequest(
                    date = date,
                    slot = meal.slot,
                    eatenAt = runCatching { LocalTime.parse(meal.time) }.getOrNull(),
                    description = meal.description,
                    kcal = meal.calories,
                    proteinG = meal.protein,
                    fatG = meal.fat,
                    carbsG = meal.carbs,
                ),
            )
            if (!saved) health.refreshNutrition()
        } }
    }

    override fun checkInChanged(mood: Mood?, energy: Int?, stress: Int?) {
        // Same queue as the symptoms: a check-in also rewrites the day, and an older one
        // landing after a newer one put the dial back where it had been.
        scope.launch { dayWrites.withLock { health.saveCheckIn(mood?.toWire(), energy, stress) } }
    }

    override fun mealDeleted(id: String) {
        // Queued behind the meal writes: a delete used to race the add it belonged to,
        // and the add's own refresh put the deleted meal back on the screen.
        scope.launch {
            nutritionWrites.withLock {
                // "scan-3" and "search-1" are the store's own ids for a meal whose upload
                // may still be in flight; the server has no such row to delete. Reading the
                // day back settles it either way, and also restores the row if the delete
                // failed.
                val provisional = id.startsWith("scan-") || id.startsWith("search-")
                if (provisional || !health.deleteMeal(id)) health.refreshNutrition()
            }
        }
    }

    override fun practiceLogged(kind: PracticeKind, seconds: Int) {
        val wire = when (kind) {
            PracticeKind.Breathing -> MindPracticeKind.BREATHING
            PracticeKind.Meditation -> MindPracticeKind.MEDITATION
        }
        scope.launch { health.logPractice(wire, seconds) }
    }

    override fun journalSaved(body: String) {
        // Dated by the day the app is showing, not by the device clock at send time: an
        // entry written just before midnight belongs to the day she was writing about.
        val date = health.selectedDate ?: health.mind?.today ?: return
        scope.launch { health.addJournalEntry(date, body) }
    }

    override fun journalDeleted(id: String) {
        scope.launch { health.deleteJournalEntry(id) }
    }

}
