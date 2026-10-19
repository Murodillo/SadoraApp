package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.SaveVoiceLogRequest
import uz.sadora.contract.VoiceLogDraft
import uz.sadora.contract.VoiceLogRequest
import uz.sadora.contract.VoiceOptions

/** Logging by voice: options, hearing a recording, saving what she kept. */
class VoiceApi(private val caller: ApiCaller) {

    suspend fun options(): ApiResult<VoiceOptions> =
        caller.authenticated("v1/voice/options", HttpMethodKind.GET)

    suspend fun hear(request: VoiceLogRequest): ApiResult<VoiceLogDraft> =
        caller.authenticated("v1/voice/hear", HttpMethodKind.POST) { setBody(request) }

    suspend fun save(request: SaveVoiceLogRequest): ApiResult<Ack> =
        caller.authenticated("v1/voice/save", HttpMethodKind.POST) { setBody(request) }
}
