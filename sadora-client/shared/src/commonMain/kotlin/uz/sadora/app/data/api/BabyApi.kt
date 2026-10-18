package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.Baby
import uz.sadora.contract.BabyOverview
import uz.sadora.contract.GrowthChart
import uz.sadora.contract.GrowthEntry
import uz.sadora.contract.GrowthMetric
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.MarkMilestoneRequest
import uz.sadora.contract.MarkVaccineRequest
import uz.sadora.contract.Milestone
import uz.sadora.contract.SaveBabyRequest
import uz.sadora.contract.SaveGrowthRequest
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.contract.VaccineDose

/** Her babies: profiles, the day, growth, vaccines and milestones. Feeds, sleeps and nappies go through stage events. */
class BabyApi(private val caller: ApiCaller, private val events: StageEventApi) {

    suspend fun list(): ApiResult<List<Baby>> = caller.authenticated("v1/babies", HttpMethodKind.GET)

    suspend fun create(request: SaveBabyRequest): ApiResult<Baby> =
        caller.authenticated("v1/babies", HttpMethodKind.POST) { setBody(request) }

    suspend fun update(id: String, request: SaveBabyRequest): ApiResult<Baby> =
        caller.authenticated("v1/babies/$id", HttpMethodKind.PUT) { setBody(request) }

    suspend fun delete(id: String): ApiResult<Ack> = caller.authenticated("v1/babies/$id", HttpMethodKind.DELETE)

    suspend fun overview(id: String): ApiResult<BabyOverview> =
        caller.authenticated("v1/babies/$id/overview", HttpMethodKind.GET)

    suspend fun events(id: String, kind: StageEventKind, days: Int): ApiResult<List<StageEvent>> = events.list(kind, days, id)

    suspend fun log(request: LogStageEventRequest): ApiResult<StageEvent> = events.add(request)

    suspend fun finish(eventId: String): ApiResult<StageEvent> = events.finish(eventId)

    suspend fun deleteEvent(eventId: String): ApiResult<Ack> = events.delete(eventId)

    suspend fun growth(id: String): ApiResult<List<GrowthEntry>> = caller.authenticated("v1/babies/$id/growth", HttpMethodKind.GET)

    suspend fun addGrowth(id: String, request: SaveGrowthRequest): ApiResult<GrowthEntry> =
        caller.authenticated("v1/babies/$id/growth", HttpMethodKind.POST) { setBody(request) }

    suspend fun deleteGrowth(id: String, entryId: String): ApiResult<Ack> =
        caller.authenticated("v1/babies/$id/growth/$entryId", HttpMethodKind.DELETE)

    /** Premium: a free account gets the entitlement error, which the screen turns into the paywall. */
    suspend fun chart(id: String, metric: GrowthMetric): ApiResult<GrowthChart> =
        caller.authenticated("v1/babies/$id/growth/chart?metric=${metric.name.lowercase()}", HttpMethodKind.GET)

    suspend fun vaccines(id: String): ApiResult<List<VaccineDose>> = caller.authenticated("v1/babies/$id/vaccines", HttpMethodKind.GET)

    suspend fun markVaccine(id: String, code: String, request: MarkVaccineRequest): ApiResult<List<VaccineDose>> =
        caller.authenticated("v1/babies/$id/vaccines/$code", HttpMethodKind.PUT) { setBody(request) }

    suspend fun unmarkVaccine(id: String, code: String): ApiResult<List<VaccineDose>> =
        caller.authenticated("v1/babies/$id/vaccines/$code", HttpMethodKind.DELETE)

    suspend fun milestones(id: String): ApiResult<List<Milestone>> = caller.authenticated("v1/babies/$id/milestones", HttpMethodKind.GET)

    suspend fun markMilestone(id: String, code: String, request: MarkMilestoneRequest): ApiResult<List<Milestone>> =
        caller.authenticated("v1/babies/$id/milestones/$code", HttpMethodKind.PUT) { setBody(request) }

    suspend fun unmarkMilestone(id: String, code: String): ApiResult<List<Milestone>> =
        caller.authenticated("v1/babies/$id/milestones/$code", HttpMethodKind.DELETE)

    suspend fun milestonePhoto(id: String, code: String): ApiResult<ByteArray> =
        caller.authenticated("v1/babies/$id/milestones/$code/photo", HttpMethodKind.GET)
}
