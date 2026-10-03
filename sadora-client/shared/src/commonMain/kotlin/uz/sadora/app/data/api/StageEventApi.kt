package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind

/** Feeds, kick counts, contractions, hot flushes and mood questionnaires. */
class StageEventApi(private val caller: ApiCaller) {

    suspend fun list(kind: StageEventKind, days: Int): ApiResult<List<StageEvent>> =
        caller.authenticated("v1/stage-events?kind=${kind.name.lowercase()}&days=$days", HttpMethodKind.GET)

    suspend fun add(request: LogStageEventRequest): ApiResult<StageEvent> =
        caller.authenticated("v1/stage-events", HttpMethodKind.POST) { setBody(request) }

    suspend fun delete(id: String): ApiResult<Ack> =
        caller.authenticated("v1/stage-events/$id", HttpMethodKind.DELETE)
}
