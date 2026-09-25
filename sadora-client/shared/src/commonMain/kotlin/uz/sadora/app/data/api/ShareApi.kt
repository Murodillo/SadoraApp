package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.CreateShareRequest
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.ProfileShare

/** The QR code for a doctor: make a link, list the ones she made, take one back. */
class ShareApi(private val caller: ApiCaller) {

    suspend fun create(ttlHours: Int): ApiResult<ProfileShare> =
        caller.authenticated("v1/me/shares", HttpMethodKind.POST) { setBody(CreateShareRequest(ttlHours)) }

    suspend fun list(): ApiResult<List<ProfileShare>> =
        caller.authenticated("v1/me/shares", HttpMethodKind.GET)

    suspend fun revoke(id: String): ApiResult<Ack> =
        caller.authenticated("v1/me/shares/$id", HttpMethodKind.DELETE)

    /** Her own copy of the same document, for the export button. */
    suspend fun export(language: String): ApiResult<DoctorSummary> =
        caller.authenticated("v1/me/export?lang=$language", HttpMethodKind.GET)
}
