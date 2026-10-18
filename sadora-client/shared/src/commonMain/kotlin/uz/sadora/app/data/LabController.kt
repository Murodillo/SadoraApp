package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.LabApi
import uz.sadora.contract.LabMarker
import uz.sadora.contract.LabOverview
import uz.sadora.contract.LabPhotoUpload
import uz.sadora.contract.LabReport
import uz.sadora.contract.LabScanRequest
import uz.sadora.contract.LabScanResult
import uz.sadora.contract.SaveLabReportRequest

/**
 * Her lab sheets: the front page's charts, the list, one sheet, and the scanner.
 *
 * A saved or edited sheet is put into the list from the server's answer, and the charts
 * are read again behind it, so a number she corrected is the number every screen shows.
 */
class LabController(private val api: LabApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    var overview by mutableStateOf(LabOverview())
        private set

    /** Her sheets, newest first, as many pages as she has scrolled. */
    var reports by mutableStateOf<List<LabReport>>(emptyList())
        private set
    var hasMore by mutableStateOf(false)
        private set
    /** Rows the server has given so far: what [LoadMoreRow] keys on. */
    var loadedCount by mutableStateOf(0)
        private set
    /** True once the first page has come back, so an empty list can say it is empty. */
    var loaded by mutableStateOf(false)
        private set

    var markers by mutableStateOf<List<LabMarker>>(emptyList())
        private set

    private val photoCache = ImageBytesCache(PhotoCacheSize)

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.overview() }?.let { overview = it }
        calls.run(silent = true) { api.list(PageSize, 0) }?.let { page ->
            reports = page.items
            hasMore = page.hasMore
            loadedCount = page.items.size
            loaded = true
        }
    }

    suspend fun loadMore() {
        val api = api ?: return
        val page = calls.run(silent = true) { api.list(PageSize, loadedCount) } ?: return
        val known = reports.map { it.id }.toSet()
        reports = reports + page.items.filterNot { it.id in known }
        hasMore = page.hasMore
        loadedCount += page.items.size
    }

    suspend fun loadMarkers() {
        val api = api ?: return
        if (markers.isNotEmpty()) return
        calls.run(silent = true) { api.markers() }?.let { markers = it }
    }

    /** One sheet: from the list when it is there, otherwise from the server. */
    suspend fun report(id: String): LabReport? {
        reports.firstOrNull { it.id == id }?.let { return it }
        val api = api ?: return null
        return calls.run { api.get(id) }
    }

    fun cached(id: String): LabReport? = reports.firstOrNull { it.id == id }

    suspend fun scan(pages: List<LabPhotoUpload>): LabScanResult? {
        val api = api ?: return null
        val result = calls.run { api.scan(LabScanRequest(pages)) }
        // A read spends a use whatever she does next; the count on the front page follows.
        if (result != null) calls.run(silent = true) { api.overview() }?.let { overview = it }
        return result
    }

    suspend fun save(id: String?, request: SaveLabReportRequest): LabReport? {
        val api = api ?: return null
        val saved = calls.run { if (id == null) api.create(request) else api.update(id, request) } ?: return null
        reports = (listOf(saved) + reports.filterNot { it.id == saved.id })
            .sortedWith(compareByDescending<LabReport> { it.takenOn }.thenByDescending { it.createdAt })
        if (id == null) loadedCount += 1
        calls.run(silent = true) { api.overview() }?.let { overview = it }
        return saved
    }

    suspend fun delete(id: String): Boolean {
        val api = api ?: return false
        calls.run { api.delete(id) } ?: return false
        reports = reports.filterNot { it.id == id }
        calls.run(silent = true) { api.overview() }?.let { overview = it }
        return true
    }

    suspend fun photo(id: String, page: Int): ByteArray? {
        val api = api ?: return null
        return photoCache.getOrFetch("$id/$page") { (api.photo(id, page) as? ApiResult.Success)?.value }
    }

    /** The pages she just took, kept so her own sheet does not download them back. */
    suspend fun rememberPhoto(id: String, page: Int, bytes: ByteArray) = photoCache.put("$id/$page", bytes)

    private companion object {
        const val PageSize = 20
        const val PhotoCacheSize = 12
    }
}
