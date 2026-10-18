package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.LabMarker
import uz.sadora.contract.LabOverview
import uz.sadora.contract.LabReport
import uz.sadora.contract.LabScanRequest
import uz.sadora.contract.LabScanResult
import uz.sadora.contract.Page
import uz.sadora.contract.SaveLabReportRequest

/** Her lab sheets: typed in, or read from photos and checked by her before they are saved. */
class LabApi(private val caller: ApiCaller) {

    suspend fun overview(): ApiResult<LabOverview> =
        caller.authenticated("v1/labs/overview", HttpMethodKind.GET)

    suspend fun list(limit: Int, offset: Int): ApiResult<Page<LabReport>> =
        caller.authenticated("v1/labs?limit=$limit&offset=$offset", HttpMethodKind.GET)

    suspend fun get(id: String): ApiResult<LabReport> =
        caller.authenticated("v1/labs/$id", HttpMethodKind.GET)

    suspend fun markers(): ApiResult<List<LabMarker>> =
        caller.authenticated("v1/labs/markers", HttpMethodKind.GET)

    suspend fun create(request: SaveLabReportRequest): ApiResult<LabReport> =
        caller.authenticated("v1/labs", HttpMethodKind.POST) { setBody(request) }

    suspend fun update(id: String, request: SaveLabReportRequest): ApiResult<LabReport> =
        caller.authenticated("v1/labs/$id", HttpMethodKind.PUT) { setBody(request) }

    suspend fun delete(id: String): ApiResult<Ack> =
        caller.authenticated("v1/labs/$id", HttpMethodKind.DELETE)

    /** Reading the pages. Nothing is saved: [create] is what writes the sheet down. */
    suspend fun scan(request: LabScanRequest): ApiResult<LabScanResult> =
        caller.authenticated("v1/labs/scan", HttpMethodKind.POST) { setBody(request) }

    /** A page's bytes, never cached by the server's headers; the phone keeps them in memory. */
    suspend fun photo(id: String, page: Int): ApiResult<ByteArray> =
        caller.authenticated("v1/labs/$id/photos/$page", HttpMethodKind.GET)
}
