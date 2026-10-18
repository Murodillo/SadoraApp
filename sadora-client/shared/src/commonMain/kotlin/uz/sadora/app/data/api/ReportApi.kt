package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.PdfReportRequest
import uz.sadora.contract.ReportOptions

/** The doctor's report as a PDF, made on the server from what she chose. */
class ReportApi(private val caller: ApiCaller) {

    suspend fun options(): ApiResult<ReportOptions> =
        caller.authenticated("v1/reports/options", HttpMethodKind.GET)

    /** The file's bytes. Each one spends her month's free report unless she has Premium. */
    suspend fun pdf(request: PdfReportRequest): ApiResult<ByteArray> =
        caller.authenticated("v1/reports/pdf", HttpMethodKind.POST) { setBody(request) }
}
