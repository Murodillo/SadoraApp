package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.SaveSosContactsRequest
import uz.sadora.contract.SosAlertRequest
import uz.sadora.contract.SosAlertResult
import uz.sadora.contract.SosState

/** SOS: her contacts and the alert. */
class SosApi(private val caller: ApiCaller) {

    suspend fun state(): ApiResult<SosState> =
        caller.authenticated("v1/sos", HttpMethodKind.GET)

    suspend fun saveContacts(request: SaveSosContactsRequest): ApiResult<SosState> =
        caller.authenticated("v1/sos/contacts", HttpMethodKind.PUT) { setBody(request) }

    suspend fun alert(request: SosAlertRequest): ApiResult<SosAlertResult> =
        caller.authenticated("v1/sos/alert", HttpMethodKind.POST) { setBody(request) }
}
