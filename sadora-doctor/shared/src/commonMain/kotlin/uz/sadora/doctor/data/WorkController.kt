package uz.sadora.doctor.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.contract.DoctorEarnings
import uz.sadora.contract.DoctorHours
import uz.sadora.contract.DoctorSettings
import uz.sadora.contract.DoctorStats
import uz.sadora.contract.PatientHistory
import uz.sadora.contract.PatientNote
import uz.sadora.contract.QuickReply
import uz.sadora.contract.SaveQuickReplyRequest
import uz.sadora.contract.UpdateDoctorSettingsRequest

/**
 * Her working day, beside [DoctorController]: her price, hours and the "busy" switch,
 * her numbers and earnings, her quick replies, and what she keeps on a patient — the
 * private note and the history of their consultations.
 *
 * Each page reads its own [ApiCallState], as the other controller's do. A null API is a
 * preview, and every call then does nothing.
 */
class WorkController(private val api: DoctorApi?) {

    /** The settings page and the busy switch, wherever it is drawn. */
    val settingsCalls = ApiCallState()

    /** Her numbers on Home. */
    val statsCalls = ApiCallState()

    /** The earnings card and its page. */
    val earningsCalls = ApiCallState()

    /** The quick replies: the composer's sheet and their own page. */
    val replyCalls = ApiCallState()

    /** A patient's page: the note and the history. */
    val patientCalls = ApiCallState()

    // ---------------------------------------------------------------- price, hours, busy

    var settings by mutableStateOf<DoctorSettings?>(null)
        private set

    suspend fun loadSettings(silent: Boolean = settings != null) {
        val api = api ?: return
        settingsCalls.run(silent = silent) { api.settings() }?.let { settings = it }
    }

    /**
     * The switch moves under her finger and the server follows; if it refuses, the
     * switch goes back, so it never shows a state she is not in.
     */
    suspend fun setBusy(busy: Boolean): Boolean {
        val api = api ?: return false
        val before = settings
        settings = before?.copy(busy = busy)
        val saved = settingsCalls.run { api.updateSettings(UpdateDoctorSettingsRequest(busy = busy)) }
        if (saved == null) {
            settings = before
            return false
        }
        settings = saved
        return true
    }

    /** The price and the whole week, saved together from the settings page. */
    suspend fun saveWork(priceMinor: Long, hours: List<DoctorHours>): Boolean {
        val api = api ?: return false
        val saved = settingsCalls.run {
            api.updateSettings(UpdateDoctorSettingsRequest(priceMinor = priceMinor, hours = hours))
        } ?: return false
        settings = saved
        return true
    }

    // ---------------------------------------------------------------- numbers and money

    var stats by mutableStateOf<DoctorStats?>(null)
        private set

    suspend fun loadStats() {
        val api = api ?: return
        statsCalls.run(silent = stats != null) { api.stats() }?.let { stats = it }
    }

    /**
     * The totals over everything, with her consultations and payouts as far as she has
     * scrolled: the first page of each comes with the totals, the rest through
     * [loadMoreLines] and [loadMorePayouts].
     */
    var earnings by mutableStateOf<DoctorEarnings?>(null)
        private set

    /** More consultations, or payouts, than [earnings] holds are on the server. */
    var linesHaveMore by mutableStateOf(false)
        private set
    var payoutsHaveMore by mutableStateOf(false)
        private set

    /**
     * Rows the server has given of each list — the next page's offset, overlaps included.
     * Observable: the screen asks again on it, so a page that was all overlap still
     * brings the next one.
     */
    var linesRead by mutableStateOf(0)
        private set
    var payoutsRead by mutableStateOf(0)
        private set
    private var loadingLines = false
    private var loadingPayouts = false

    /** The totals and the first pages; what was scrolled to starts over from the top. */
    suspend fun loadEarnings(silent: Boolean = earnings != null) {
        val api = api ?: return
        earningsCalls.run(silent = silent) { api.earnings() }?.let {
            earnings = it
            linesRead = it.lines.size
            payoutsRead = it.payouts.size
            linesHaveMore = it.linesHaveMore
            payoutsHaveMore = it.payoutsHaveMore
        }
    }

    /** The next page of her paid consultations, under the last one on screen. */
    suspend fun loadMoreLines() {
        val api = api ?: return
        if (!linesHaveMore || loadingLines) return
        loadingLines = true
        try {
            val page = earningsCalls.run(silent = true) { api.earningLines(offset = linesRead) } ?: return
            val shown = earnings ?: return
            // A consultation paid in between shifts the offsets; the overlap is dropped by id.
            val known = shown.lines.mapTo(HashSet()) { it.sessionId }
            earnings = shown.copy(lines = shown.lines + page.items.filter { it.sessionId !in known })
            linesRead += page.items.size
            linesHaveMore = page.hasMore && page.items.isNotEmpty()
        } finally {
            loadingLines = false
        }
    }

    /** The next page of her payouts. */
    suspend fun loadMorePayouts() {
        val api = api ?: return
        if (!payoutsHaveMore || loadingPayouts) return
        loadingPayouts = true
        try {
            val page = earningsCalls.run(silent = true) { api.payouts(offset = payoutsRead) } ?: return
            val shown = earnings ?: return
            val known = shown.payouts.mapTo(HashSet()) { it.id }
            earnings = shown.copy(payouts = shown.payouts + page.items.filter { it.id !in known })
            payoutsRead += page.items.size
            payoutsHaveMore = page.hasMore && page.items.isNotEmpty()
        } finally {
            loadingPayouts = false
        }
    }

    // ---------------------------------------------------------------- quick replies

    var quickReplies by mutableStateOf<List<QuickReply>>(emptyList())
        private set
    var quickRepliesLoaded by mutableStateOf(false)
        private set

    suspend fun loadQuickReplies() {
        val api = api ?: return
        replyCalls.run(silent = quickRepliesLoaded) { api.quickReplies() }?.let {
            quickReplies = it.sortedBy(QuickReply::position)
            quickRepliesLoaded = true
        }
    }

    /** A new reply when [id] is null, otherwise that one changed; false keeps the form open. */
    suspend fun saveQuickReply(id: String?, title: String, body: String): Boolean {
        val api = api ?: return false
        val position = quickReplies.firstOrNull { it.id == id }?.position ?: (quickReplies.maxOfOrNull { it.position }?.plus(1) ?: 0)
        val request = SaveQuickReplyRequest(title = title, body = body, position = position)
        val saved = replyCalls.run {
            if (id == null) api.addQuickReply(request) else api.updateQuickReply(id, request)
        } ?: return false
        quickReplies = (quickReplies.filterNot { it.id == saved.id } + saved).sortedBy(QuickReply::position)
        return true
    }

    suspend fun deleteQuickReply(id: String): Boolean {
        val api = api ?: return false
        replyCalls.run { api.deleteQuickReply(id) } ?: return false
        quickReplies = quickReplies.filterNot { it.id == id }
        return true
    }

    // ---------------------------------------------------------------- a patient

    /** Her note on the patient of [patientFor]; null until it has been read. */
    var note by mutableStateOf<PatientNote?>(null)
        private set
    var history by mutableStateOf<PatientHistory?>(null)
        private set

    /** The consultation the note and history belong to, so another patient's never shows. */
    var patientFor by mutableStateOf<String?>(null)
        private set

    suspend fun openPatient(conversationId: String) {
        val api = api ?: return
        if (patientFor != conversationId) {
            patientFor = conversationId
            note = null
            history = null
        }
        val loaded = patientCalls.run(silent = note != null) {
            when (val read = api.note(conversationId)) {
                is ApiResult.Failure -> read
                is ApiResult.Success -> api.history(conversationId).map { read.value to it }
            }
        } ?: return
        // She may have opened another patient while this one loaded.
        if (patientFor != conversationId) return
        note = loaded.first
        history = loaded.second
    }

    /** An empty note deletes it; the server keeps nothing for a blank. */
    suspend fun saveNote(conversationId: String, body: String): Boolean {
        val api = api ?: return false
        val saved = patientCalls.run { api.saveNote(conversationId, body) } ?: return false
        if (patientFor == conversationId) note = saved
        return true
    }

    /** Forgets everything: another account may sign in on this phone next. */
    fun reset() {
        settings = null
        stats = null
        earnings = null
        linesHaveMore = false
        payoutsHaveMore = false
        linesRead = 0
        payoutsRead = 0
        quickReplies = emptyList()
        quickRepliesLoaded = false
        note = null
        history = null
        patientFor = null
        listOf(settingsCalls, statsCalls, earningsCalls, replyCalls, patientCalls).forEach { it.clearError() }
    }
}
