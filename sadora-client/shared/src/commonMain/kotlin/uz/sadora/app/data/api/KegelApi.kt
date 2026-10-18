package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.KegelSessionResult
import uz.sadora.contract.KegelSettingsRequest
import uz.sadora.contract.KegelState
import uz.sadora.contract.LogKegelSessionRequest
import uz.sadora.contract.SaveKegelCheckRequest

/** Kegel exercises: her program and levels, a finished session, reminders and the leak check. */
class KegelApi(private val caller: ApiCaller) {

    suspend fun state(): ApiResult<KegelState> =
        caller.authenticated("v1/kegel", HttpMethodKind.GET)

    suspend fun logSession(request: LogKegelSessionRequest): ApiResult<KegelSessionResult> =
        caller.authenticated("v1/kegel/sessions", HttpMethodKind.POST) { setBody(request) }

    suspend fun saveSettings(request: KegelSettingsRequest): ApiResult<KegelState> =
        caller.authenticated("v1/kegel/settings", HttpMethodKind.PUT) { setBody(request) }

    suspend fun saveCheck(request: SaveKegelCheckRequest): ApiResult<KegelState> =
        caller.authenticated("v1/kegel/checks", HttpMethodKind.POST) { setBody(request) }
}
