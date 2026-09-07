package org.example.project.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.example.project.model.AppState
import org.example.project.data.api.CycleApi
import org.example.project.data.api.MedicationApi
import org.example.project.data.api.MindApi
import org.example.project.data.api.NutritionApi
import uz.sadora.contract.CycleCalendar
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyHealth
import uz.sadora.contract.DailyLog
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.FlowLevel
import uz.sadora.contract.LifeStage
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.LogPracticeRequest
import uz.sadora.contract.Medication
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.MindCheckIn
import uz.sadora.contract.MindPracticeKind
import uz.sadora.contract.MindSummary
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.NutritionDay
import uz.sadora.contract.ProviderStatus
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.SaveDailyLogRequest
import uz.sadora.contract.SaveJournalEntryRequest
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.SymptomEntry

/**
 * What the health screens call.
 *
 * Holds the loaded data as Compose state so a screen reads a field instead of running
 * its own request, and so two screens showing the same day cannot disagree. Writes
 * refresh what they touched: recording a dose returns the day, adding water returns the
 * total, and the server stays the one that decides.
 *
 * A null API set means no backend — previews and the prototype build. Every read then
 * returns null and every write succeeds locally, which keeps the app usable rather than
 * dead behind a sign-in wall.
 */
class HealthController(
    private val cycleApi: CycleApi?,
    private val mindApi: MindApi?,
    private val nutritionApi: NutritionApi?,
    private val medicationApi: MedicationApi?,
    private val wearableApi: org.example.project.data.api.WearableApi? = null,
    private val appointmentApi: org.example.project.data.api.AppointmentApi? = null,
    /**
     * Mirrored onto here after every load, so the existing screens show server data
     * without any of them having to learn a wire type.
     */
    private val state: AppState? = null,
) {
    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: String? get() = calls.error
    val isOffline: Boolean get() = cycleApi == null

    fun clearError() = calls.clearError()

    // ---------------------------------------------------------------- state

    var cycle by mutableStateOf<CycleStatus?>(null)
        private set
    var calendar by mutableStateOf<CycleCalendar?>(null)
        private set
    var day by mutableStateOf<DailyLog?>(null)
        private set
    var symptoms by mutableStateOf<List<SymptomDefinition>>(emptyList())
        private set
    var mind by mutableStateOf<MindSummary?>(null)
        private set
    var nutrition by mutableStateOf<NutritionDay?>(null)
        private set
    var medications by mutableStateOf<List<Medication>>(emptyList())
        private set
    var doses by mutableStateOf<MedicationDay?>(null)
        private set

    /** Today's wearable metrics — sleep stages, resting heart rate, and which device sent them. */
    var wearableToday by mutableStateOf<DailyHealth?>(null)
        private set

    /** What the Data Sources screen lists: which providers have sent anything, and when. */
    var sources by mutableStateOf<List<ProviderStatus>>(emptyList())
        private set

    /** The day the screens are looking at. Defaults to the server's idea of today. */
    var selectedDate by mutableStateOf<LocalDate?>(null)

    /** The window [loadRecentLogs] filled, newest last. Empty until it is asked for. */
    var recentLogs by mutableStateOf<List<DailyLog>>(emptyList())

    /** Her appointments, in the order they fall. Loaded by the screens that show them. */
    var appointments by mutableStateOf<List<uz.sadora.contract.Appointment>>(emptyList())

    /**
     * The dose history behind the adherence screen, newest day last.
     *
     * Merged across every medication she takes, because the screen asks "how have I
     * been doing", not "how have I been doing with the iron".
     */
    var doseHistory by mutableStateOf<List<uz.sadora.contract.MedicationDay>>(emptyList())
        private set

    /** Completed cycles, oldest first. The regularity view is built from their lengths. */
    var history by mutableStateOf<uz.sadora.contract.CycleHistory?>(null)
        private set

    // ---------------------------------------------------------------- loading

    /**
     * Everything the health tabs need, on entering the app.
     *
     * Failures are silent: a tab that could not load shows its empty state rather than
     * a banner over a screen the user has not opened yet.
     */
    suspend fun loadAll() {
        val api = cycleApi ?: return
        calls.run(silent = true) { api.status() }?.let {
            cycle = it
            selectedDate = it.today
            state?.applyCycle(it)
        }
        loadCalendarAroundToday()
        // The catalogue comes first: the day's symptoms are stored as keys and shown as
        // labels, so loading the day without it would map every one to nothing.
        // Null lets the server scope the catalogue to her own life stage.
        loadSymptoms(lifeStage = null)
        selectedDate?.let { loadDay(it) }
        refreshMind()
        refreshNutrition()
        refreshMedications()
        refreshWearables()
    }

    suspend fun refreshWearables() {
        val api = wearableApi ?: return
        calls.run(silent = true) { api.today() }?.let {
            wearableToday = it
            state?.applyWearables(it)
        }
    }

    /**
     * The connected providers. Loaded on demand rather than with the tabs: only two
     * screens show it, and both of them are a tap away from Profile.
     */
    suspend fun loadSources() {
        val api = wearableApi ?: return
        calls.run(silent = true) { api.sources() }?.let { sources = it }
    }

    suspend fun refreshCycle() {
        val api = cycleApi ?: return
        calls.run(silent = true) { api.status() }?.let {
            cycle = it
            state?.applyCycle(it)
        }
    }

    private suspend fun loadCalendarAroundToday() {
        val today = cycle?.today ?: return
        loadCalendar(today.minus(35, DateTimeUnit.DAY), today.plus(35, DateTimeUnit.DAY))
    }

    suspend fun loadCalendar(from: LocalDate, to: LocalDate) {
        val api = cycleApi ?: return
        calls.run(silent = true) { api.calendar(from, to) }?.let { calendar = it }
    }

    /**
     * One day's log, read without touching the store.
     *
     * [loadDay] applies what it reads to [state], which is right for today and wrong
     * for any other day: opening last Tuesday in the calendar would otherwise replace
     * today's symptoms on every screen with that Tuesday's.
     */
    /**
     * How strongly today's log records one symptom, on the tiles' 0-3 scale, or 0 when
     * it is not recorded at all. The tile used to carry a severity written into the
     * file, so every phone showed the same three-out-of-three headache.
     */
    fun severityOf(key: String): Int = when (day?.symptoms?.firstOrNull { it.key == key }?.severity) {
        uz.sadora.contract.SymptomSeverity.MILD -> 1
        uz.sadora.contract.SymptomSeverity.MODERATE -> 2
        uz.sadora.contract.SymptomSeverity.SEVERE -> 3
        null -> 0
    }

    suspend fun dayAt(date: LocalDate): DailyLog? {
        val api = cycleApi ?: return null
        return calls.run(silent = true) { api.day(date) }
    }

    suspend fun loadDay(date: LocalDate) {
        val api = cycleApi ?: return
        selectedDate = date
        calls.run(silent = true) { api.day(date) }?.let {
            day = it
            state?.applyDay(it, symptoms)
        }
    }

    /**
     * The last [days] days of records, for the screens that count how often something
     * happened rather than showing one day.
     *
     * Held separately from [day] because it answers a different question and is loaded
     * by the screens that ask it, not with the tabs.
     */
    suspend fun loadRecentLogs(days: Int = 30) {
        val api = cycleApi ?: return
        val to = cycle?.today ?: mind?.today ?: selectedDate
            ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
        val from = to.minus(days - 1, DateTimeUnit.DAY)
        calls.run(silent = true) { api.days(from, to) }?.let { recentLogs = it.logs }
    }

    /**
     * Every medication's history for [days], merged by day.
     *
     * One request per course rather than one for everything: the API is per-medication,
     * and a woman on two courses is the common case, not twenty.
     */
    suspend fun loadDoseHistory(days: Int) {
        val api = medicationApi ?: return
        if (medications.isEmpty()) refreshMedications()
        val merged = mutableMapOf<LocalDate, MutableList<uz.sadora.contract.MedicationDose>>()
        medications.forEach { medication ->
            val history = calls.run(silent = true) { api.history(medication.id, days) } ?: return@forEach
            history.days.forEach { day ->
                merged.getOrPut(day.date) { mutableListOf() }.addAll(day.doses)
            }
        }
        doseHistory = merged.entries
            .sortedBy { it.key }
            .map { (date, doses) -> uz.sadora.contract.MedicationDay(date, doses) }
    }

    suspend fun loadHistory() {
        val api = cycleApi ?: return
        calls.run(silent = true) { api.history() }?.let { history = it }
    }

    // ---------------------------------------------------------------- appointments

    suspend fun loadAppointments() {
        val api = appointmentApi ?: return
        calls.run(silent = true) { api.list() }?.let { appointments = it }
    }

    suspend fun addAppointment(request: uz.sadora.contract.SaveAppointmentRequest): Boolean {
        val api = appointmentApi ?: return true
        calls.run { api.add(request) } ?: return false
        loadAppointments()
        return true
    }

    suspend fun updateAppointment(id: String, request: uz.sadora.contract.SaveAppointmentRequest): Boolean {
        val api = appointmentApi ?: return true
        calls.run { api.update(id, request) } ?: return false
        loadAppointments()
        return true
    }

    suspend fun setAppointmentDone(id: String, done: Boolean): Boolean {
        val api = appointmentApi ?: return true
        calls.run { api.setCompleted(id, done) } ?: return false
        loadAppointments()
        return true
    }

    suspend fun deleteAppointment(id: String): Boolean {
        val api = appointmentApi ?: return true
        calls.run { api.delete(id) } ?: return false
        loadAppointments()
        return true
    }

    suspend fun loadSymptoms(lifeStage: LifeStage?) {
        val api = cycleApi ?: return
        calls.run(silent = true) { api.symptoms(lifeStage) }?.let {
            symptoms = it
            // A day loaded before the catalogue arrived has unresolved labels.
            day?.let { loaded -> state?.applyDay(loaded, it) }
        }
    }

    suspend fun refreshMind() {
        val api = mindApi ?: return
        calls.run(silent = true) { api.summary() }?.let {
            mind = it
            state?.applyMind(it)
        }
    }

    suspend fun refreshNutrition() {
        val api = nutritionApi ?: return
        calls.run(silent = true) { api.today() }?.let {
            nutrition = it
            state?.applyNutrition(it)
        }
    }

    suspend fun refreshMedications() {
        val api = medicationApi ?: return
        calls.run(silent = true) { api.today() }?.let { doses = it }
        calls.run(silent = true) { api.list() }?.let { medications = it }
        val today = doses
        if (today != null) state?.applyMedications(today, medications)
    }

    // ---------------------------------------------------------------- cycle writes

    /**
     * Sends the period starts collected by the onboarding calendar, once.
     *
     * They are logged as real periods rather than left in the cycle baseline because
     * the predictor counts observed cycles: three logged starts give it two measured
     * cycle lengths and a spread, where the baseline can only ever supply an anchor and
     * an assumed length. The list is cleared whether or not the calls land, so a failed
     * flush cannot duplicate her periods on the next launch — the baseline still
     * carries the anchor in that case.
     */
    suspend fun flushOnboardingPeriods() {
        val store = state ?: return
        val pending = store.takeMarkedPeriods()
        if (pending.isEmpty()) return
        val api = cycleApi ?: return
        pending.forEach { period ->
            calls.run(silent = true) {
                // The span she actually marked, not an assumed length — so the server
                // can measure period length as well as cycle length, instead of
                // falling back to the baseline for it.
                api.logPeriod(
                    LogPeriodRequest(
                        startedOn = period.start,
                        endedOn = period.endInclusive,
                    ),
                )
            }
        }
        // One refresh for the batch rather than one per date.
        refreshCycle()
        loadCalendarAroundToday()
    }

    suspend fun logPeriodStart(date: LocalDate): Boolean {
        val api = cycleApi ?: return true
        calls.run { api.logPeriod(LogPeriodRequest(startedOn = date)) } ?: return false
        refreshCycle()
        loadCalendarAroundToday()
        return true
    }

    suspend fun endPeriod(periodId: String, endedOn: LocalDate): Boolean {
        val api = cycleApi ?: return true
        calls.run {
            api.updatePeriod(periodId, uz.sadora.contract.UpdatePeriodRequest(endedOn = endedOn))
        } ?: return false
        refreshCycle()
        loadCalendarAroundToday()
        return true
    }

    /**
     * Saves the day sheet.
     *
     * The whole day goes up as one request — the server replaces it wholesale — so a
     * symptom removed on screen is removed on the server rather than lingering.
     */
    suspend fun saveDay(
        date: LocalDate,
        flow: FlowLevel? = day?.flow,
        mood: MoodLevel? = day?.mood,
        energy: Int? = day?.energy,
        stress: Int? = day?.stress,
        symptomKeys: List<SymptomEntry> = day?.symptoms.orEmpty(),
        note: String? = day?.note,
        fetalMovement: FetalMovement? = day?.fetalMovement,
    ): Boolean {
        val api = cycleApi ?: return true
        val saved = calls.run {
            api.saveDay(
                date,
                SaveDailyLogRequest(
                    flow = flow,
                    mood = mood,
                    energy = energy,
                    stress = stress,
                    symptoms = symptomKeys,
                    note = note,
                    fetalMovement = fetalMovement,
                ),
            )
        } ?: return false
        day = saved
        state?.applyDay(saved, symptoms)
        refreshCycle()
        loadCalendarAroundToday()
        return true
    }

    suspend fun toggleSymptom(date: LocalDate, key: String): Boolean {
        val current = day?.symptoms.orEmpty()
        val updated = if (current.any { it.key == key }) {
            current.filterNot { it.key == key }
        } else {
            current + SymptomEntry(key)
        }
        return saveDay(date, symptomKeys = updated)
    }

    // ---------------------------------------------------------------- mind

    suspend fun saveCheckIn(mood: MoodLevel?, energy: Int?, stress: Int?): Boolean {
        val api = mindApi ?: return true
        calls.run { api.saveCheckIn(MindCheckIn(mood, energy, stress)) } ?: return false
        refreshMind()
        selectedDate?.let { loadDay(it) }
        return true
    }

    suspend fun addJournalEntry(date: LocalDate, body: String): Boolean {
        val api = mindApi ?: return true
        calls.run { api.addEntry(SaveJournalEntryRequest(date, body)) } ?: return false
        refreshMind()
        return true
    }

    suspend fun deleteJournalEntry(id: String): Boolean {
        val api = mindApi ?: return true
        calls.run { api.deleteEntry(id) } ?: return false
        refreshMind()
        return true
    }

    suspend fun logBreathing(seconds: Int): Boolean = logPractice(MindPracticeKind.BREATHING, seconds)

    suspend fun logPractice(kind: MindPracticeKind, seconds: Int): Boolean {
        val api = mindApi ?: return true
        calls.run { api.logPractice(LogPracticeRequest(kind, seconds)) } ?: return false
        refreshMind()
        return true
    }

    // ---------------------------------------------------------------- nutrition

    /** The server returns the new total, so the undo path cannot drift from it. */
    suspend fun addWater(ml: Int): Boolean {
        val api = nutritionApi ?: return true
        calls.run { api.addWater(ml) } ?: return false
        refreshNutrition()
        return true
    }

    /**
     * Reads a photo. Null when the call failed — the screen offers to try again or to
     * enter it by hand rather than showing a dish the model did not identify.
     */
    suspend fun scanFood(imageBase64: String, mimeType: String): uz.sadora.contract.FoodScanResult? {
        val api = nutritionApi ?: return null
        return calls.run { api.scan(uz.sadora.contract.FoodScanRequest(imageBase64, mimeType)) }
    }

    suspend fun addMeal(request: LogMealRequest): Boolean {
        val api = nutritionApi ?: return true
        calls.run { api.addMeal(request) } ?: return false
        refreshNutrition()
        return true
    }

    suspend fun deleteMeal(id: String): Boolean {
        val api = nutritionApi ?: return true
        calls.run { api.deleteMeal(id) } ?: return false
        refreshNutrition()
        return true
    }

    suspend fun searchFoods(query: String): List<uz.sadora.contract.FoodItem> {
        val api = nutritionApi ?: return emptyList()
        return calls.run(silent = true) { api.searchFoods(query) }.orEmpty()
    }

    // ---------------------------------------------------------------- medications

    suspend fun recordDose(
        medicationId: String,
        dueOn: LocalDate,
        dueAt: kotlinx.datetime.LocalTime,
        status: DoseStatus,
    ): Boolean {
        val api = medicationApi ?: return true
        val updated = calls.run {
            api.recordDose(medicationId, RecordDoseRequest(dueOn, dueAt, status))
        } ?: return false
        doses = updated
        // The pack count changes with the dose, so the list is stale the moment it does.
        calls.run(silent = true) { api.list() }?.let { medications = it }
        state?.applyMedications(updated, medications)
        return true
    }

    suspend fun addMedication(request: uz.sadora.contract.SaveMedicationRequest): Boolean {
        val api = medicationApi ?: return true
        calls.run { api.add(request) } ?: return false
        refreshMedications()
        return true
    }

    suspend fun archiveMedication(id: String): Boolean {
        val api = medicationApi ?: return true
        calls.run { api.archive(id) } ?: return false
        refreshMedications()
        return true
    }
}
