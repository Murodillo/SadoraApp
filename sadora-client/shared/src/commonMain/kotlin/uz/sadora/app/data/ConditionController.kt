package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import uz.sadora.app.data.api.ConditionApi
import uz.sadora.app.data.api.WearableApi
import uz.sadora.contract.ConditionReport
import uz.sadora.contract.ConditionsState
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.contract.IngestSamplesRequest
import uz.sadora.contract.PainLog
import uz.sadora.contract.SavePainLogRequest
import uz.sadora.contract.SetConditionRequest

/**
 * Her conditions, the pain diary and the report.
 *
 * Marking or removing a condition changes the forecast on the server, so [onChanged] asks
 * the cycle to be read again: a range that appears only after a restart is a bug report.
 */
class ConditionController(private val api: ConditionApi?, private val wearables: WearableApi? = null) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    /** Set at wiring: re-read the cycle status after a condition changes. */
    var onChanged: (suspend () -> Unit)? = null

    var state by mutableStateOf(ConditionsState())
        private set
    var loaded by mutableStateOf(false)
        private set
    var pain by mutableStateOf<List<PainLog>>(emptyList())
        private set
    var report by mutableStateOf<ConditionReport?>(null)
        private set
    var reportLocked by mutableStateOf(false)
        private set

    fun has(condition: HealthCondition) = state.has(condition)

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.state() }?.let {
            state = it
            loaded = true
        }
    }

    suspend fun set(condition: HealthCondition, request: SetConditionRequest?): Boolean {
        val api = api ?: return false
        val updated = calls.run { if (request != null) api.set(condition, request) else api.remove(condition) } ?: return false
        state = updated
        onChanged?.invoke()
        return true
    }

    suspend fun loadPain(days: Int = DIARY_DAYS) {
        val api = api ?: return
        calls.run(silent = true) { api.pain(days) }?.let { pain = it }
    }

    suspend fun savePain(date: LocalDate, request: SavePainLogRequest): Boolean {
        val api = api ?: return false
        val saved = calls.run { api.savePain(date, request) } ?: return false
        pain = (pain.filterNot { it.date == saved.date } + saved).sortedByDescending { it.date }
        return true
    }

    suspend fun deletePain(date: LocalDate): Boolean {
        val api = api ?: return false
        calls.run { api.deletePain(date) } ?: return false
        pain = pain.filterNot { it.date == date }
        return true
    }

    suspend fun loadReport(months: Int) {
        val api = api ?: return
        when (val result = api.report(months)) {
            is ApiResult.Success -> {
                report = result.value
                reportLocked = false
            }
            is ApiResult.Failure -> {
                report = null
                reportLocked = result.failure is ApiFailure.PremiumRequired
            }
        }
    }

    /**
     * A weighing she typed, sent like a smart scale's under the manual provider: it lands in
     * the same daily figures, and the report reads it from there.
     */
    suspend fun logWeight(kg: Double): Boolean {
        val api = wearables ?: return false
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        calls.run {
            api.ingest(
                IngestSamplesRequest(
                    samples = listOf(
                        HealthSampleInput(
                            provider = HealthProvider.MANUAL,
                            externalId = "manual-weight:${now.toEpochMilliseconds()}",
                            metric = "weight",
                            value = kg,
                            unit = "kg",
                            startedAt = now,
                            sourceDevice = "manual",
                        ),
                    ),
                    timezone = zone.id,
                ),
            )
        } ?: return false
        return true
    }

    private companion object {
        const val DIARY_DAYS = 60
    }
}
