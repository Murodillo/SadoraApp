package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import kotlinx.datetime.LocalDate
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.ConditionReport
import uz.sadora.contract.ConditionsState
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.PainLog
import uz.sadora.contract.SavePainLogRequest
import uz.sadora.contract.SetConditionRequest

/** "Holatlarim", the pain diary and the condition report. */
class ConditionApi(private val caller: ApiCaller) {

    suspend fun state(): ApiResult<ConditionsState> = caller.authenticated("v1/conditions", HttpMethodKind.GET)

    suspend fun set(condition: HealthCondition, request: SetConditionRequest): ApiResult<ConditionsState> =
        caller.authenticated("v1/conditions/${condition.name.lowercase()}", HttpMethodKind.PUT) { setBody(request) }

    suspend fun remove(condition: HealthCondition): ApiResult<ConditionsState> =
        caller.authenticated("v1/conditions/${condition.name.lowercase()}", HttpMethodKind.DELETE)

    suspend fun pain(days: Int): ApiResult<List<PainLog>> = caller.authenticated("v1/pain?days=$days", HttpMethodKind.GET)

    suspend fun savePain(date: LocalDate, request: SavePainLogRequest): ApiResult<PainLog> =
        caller.authenticated("v1/pain/$date", HttpMethodKind.PUT) { setBody(request) }

    suspend fun deletePain(date: LocalDate): ApiResult<Ack> = caller.authenticated("v1/pain/$date", HttpMethodKind.DELETE)

    /** Premium: a free account gets the entitlement error, which the screen turns into a lock. */
    suspend fun report(months: Int): ApiResult<ConditionReport> =
        caller.authenticated("v1/conditions/report?months=$months", HttpMethodKind.GET)
}
