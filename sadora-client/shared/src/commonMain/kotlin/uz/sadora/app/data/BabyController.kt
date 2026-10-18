package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import uz.sadora.app.data.api.BabyApi
import uz.sadora.contract.Baby
import uz.sadora.contract.BabyOverview
import uz.sadora.contract.DiaperKind
import uz.sadora.contract.GrowthChart
import uz.sadora.contract.GrowthEntry
import uz.sadora.contract.GrowthMetric
import uz.sadora.contract.LabPhotoUpload
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.MarkMilestoneRequest
import uz.sadora.contract.MarkVaccineRequest
import uz.sadora.contract.Milestone
import uz.sadora.contract.SaveBabyRequest
import uz.sadora.contract.SaveGrowthRequest
import uz.sadora.contract.StageEventKind
import uz.sadora.contract.VaccineDose

/**
 * Her babies and the one on screen. Every write re-reads the overview behind it, so the
 * strip at the top — feeds, sleep, nappies today — is always the server's arithmetic.
 *
 * The chosen baby is handed to the feeding screen through [tools], so a feed started there
 * lands on the same twin the baby page was showing.
 */
class BabyController(private val api: BabyApi?, private val tools: StageEventsController) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var babies by mutableStateOf<List<Baby>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var activeId by mutableStateOf<String?>(null)
        private set

    val active: Baby? get() = babies.firstOrNull { it.id == activeId } ?: babies.firstOrNull()

    var overview by mutableStateOf<BabyOverview?>(null)
        private set
    var growth by mutableStateOf<List<GrowthEntry>>(emptyList())
        private set
    var chart by mutableStateOf<GrowthChart?>(null)
        private set
    /** The chart was refused for want of Premium: the screen shows the lock, not an error. */
    var chartLocked by mutableStateOf(false)
        private set
    var vaccines by mutableStateOf<List<VaccineDose>>(emptyList())
        private set
    var milestones by mutableStateOf<List<Milestone>>(emptyList())
        private set

    private val photoCache = ImageBytesCache(PhotoCacheSize)

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.list() }?.let { list ->
            babies = list
            loaded = true
            if (list.none { it.id == activeId }) select(list.firstOrNull()?.id)
        }
        refreshOverview()
    }

    fun select(id: String?) {
        activeId = id
        tools.babyId = id
        overview = overview?.takeIf { it.baby.id == id }
        growth = emptyList()
        chart = null
        vaccines = emptyList()
        milestones = emptyList()
    }

    suspend fun refreshOverview() {
        val api = api ?: return
        val id = active?.id ?: return
        calls.run(silent = true) { api.overview(id) }?.let { if (it.baby.id == active?.id) overview = it }
    }

    suspend fun save(id: String?, request: SaveBabyRequest): Baby? {
        val api = api ?: return null
        val saved = calls.run { if (id == null) api.create(request) else api.update(id, request) } ?: return null
        babies = (babies.filterNot { it.id == saved.id } + saved).sortedByDescending { it.birthDate }
        select(saved.id)
        refreshOverview()
        return saved
    }

    suspend fun delete(id: String): Boolean {
        val api = api ?: return false
        calls.run { api.delete(id) } ?: return false
        babies = babies.filterNot { it.id == id }
        select(babies.firstOrNull()?.id)
        refreshOverview()
        return true
    }

    // ---------------------------------------------------------------- the day

    suspend fun startSleep(): Boolean = logNow(LogStageEventRequest(StageEventKind.SLEEP, Clock.System.now()))

    suspend fun endSleep(): Boolean {
        val api = api ?: return false
        val running = overview?.sleeping ?: return false
        calls.run { api.finish(running.id) } ?: return false
        refreshOverview()
        return true
    }

    suspend fun diaper(kind: DiaperKind): Boolean =
        logNow(LogStageEventRequest(StageEventKind.DIAPER, Clock.System.now(), detail = kind.name.lowercase()))

    suspend fun deleteEvent(eventId: String): Boolean {
        val api = api ?: return false
        calls.run { api.deleteEvent(eventId) } ?: return false
        refreshOverview()
        return true
    }

    private suspend fun logNow(request: LogStageEventRequest): Boolean {
        val api = api ?: return false
        val id = active?.id ?: return false
        calls.run { api.log(request.copy(babyId = id)) } ?: return false
        refreshOverview()
        return true
    }

    // ---------------------------------------------------------------- growth

    suspend fun loadGrowth(metric: GrowthMetric) {
        val api = api ?: return
        val id = active?.id ?: return
        calls.run(silent = true) { api.growth(id) }?.let { growth = it }
        loadChart(metric)
    }

    suspend fun loadChart(metric: GrowthMetric) {
        val api = api ?: return
        val id = active?.id ?: return
        when (val result = api.chart(id, metric)) {
            is ApiResult.Success -> {
                chart = result.value
                chartLocked = false
            }
            is ApiResult.Failure -> {
                chart = null
                chartLocked = result.failure is ApiFailure.PremiumRequired
            }
        }
    }

    suspend fun addGrowth(request: SaveGrowthRequest, metric: GrowthMetric): Boolean {
        val api = api ?: return false
        val id = active?.id ?: return false
        calls.run { api.addGrowth(id, request) } ?: return false
        loadGrowth(metric)
        refreshOverview()
        return true
    }

    suspend fun deleteGrowth(entryId: String, metric: GrowthMetric): Boolean {
        val api = api ?: return false
        val id = active?.id ?: return false
        calls.run { api.deleteGrowth(id, entryId) } ?: return false
        loadGrowth(metric)
        refreshOverview()
        return true
    }

    // ---------------------------------------------------------------- vaccines and milestones

    suspend fun loadVaccines() {
        val api = api ?: return
        val id = active?.id ?: return
        calls.run(silent = true) { api.vaccines(id) }?.let { vaccines = it }
    }

    suspend fun markVaccine(code: String, request: MarkVaccineRequest?): Boolean {
        val api = api ?: return false
        val id = active?.id ?: return false
        val updated = calls.run { if (request != null) api.markVaccine(id, code, request) else api.unmarkVaccine(id, code) } ?: return false
        vaccines = updated
        refreshOverview()
        return true
    }

    suspend fun loadMilestones() {
        val api = api ?: return
        val id = active?.id ?: return
        calls.run(silent = true) { api.milestones(id) }?.let { milestones = it }
    }

    suspend fun markMilestone(code: String, request: MarkMilestoneRequest?, photoBytes: ByteArray? = null): Boolean {
        val api = api ?: return false
        val id = active?.id ?: return false
        val updated = calls.run { if (request != null) api.markMilestone(id, code, request) else api.unmarkMilestone(id, code) } ?: return false
        milestones = updated
        // Her own photo is not downloaded back to show it.
        if (photoBytes != null) photoCache.put("$id/$code", photoBytes)
        refreshOverview()
        return true
    }

    suspend fun milestonePhoto(code: String): ByteArray? {
        val api = api ?: return null
        val id = active?.id ?: return null
        return photoCache.getOrFetch("$id/$code") { (api.milestonePhoto(id, code) as? ApiResult.Success)?.value }
    }

    private companion object {
        const val PhotoCacheSize = 12
    }
}

/** A photo she picked, as the milestone request carries it. */
fun uz.sadora.app.ui.components.CapturedPhoto.toUpload() = LabPhotoUpload(base64, mimeType)
