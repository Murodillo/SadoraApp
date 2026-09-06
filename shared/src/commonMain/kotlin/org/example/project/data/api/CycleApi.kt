package org.example.project.data.api

import io.ktor.client.request.setBody
import kotlinx.datetime.LocalDate
import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.CycleCalendar
import uz.sadora.contract.CycleHistory
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyLog
import uz.sadora.contract.LifeStage
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.PeriodEntry
import uz.sadora.contract.SaveDailyLogRequest
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.UpdatePeriodRequest

/** Cycle, the day sheet and the symptom catalogue. */
class CycleApi(private val caller: ApiCaller) {

    suspend fun status(): ApiResult<CycleStatus> =
        caller.authenticated("v1/cycle/status", HttpMethodKind.GET)

    suspend fun calendar(from: LocalDate, to: LocalDate): ApiResult<CycleCalendar> =
        caller.authenticated("v1/cycle/calendar?from=$from&to=$to", HttpMethodKind.GET)

    suspend fun history(): ApiResult<CycleHistory> =
        caller.authenticated("v1/cycle/history", HttpMethodKind.GET)

    suspend fun periods(): ApiResult<List<PeriodEntry>> =
        caller.authenticated("v1/cycle/periods", HttpMethodKind.GET)

    suspend fun logPeriod(request: LogPeriodRequest): ApiResult<PeriodEntry> =
        caller.authenticated("v1/cycle/periods", HttpMethodKind.POST) { setBody(request) }

    suspend fun updatePeriod(id: String, request: UpdatePeriodRequest): ApiResult<PeriodEntry> =
        caller.authenticated("v1/cycle/periods/$id", HttpMethodKind.PATCH) { setBody(request) }

    suspend fun deletePeriod(id: String): ApiResult<Ack> =
        caller.authenticated("v1/cycle/periods/$id", HttpMethodKind.DELETE)

    suspend fun day(date: LocalDate): ApiResult<DailyLog> =
        caller.authenticated("v1/days/$date", HttpMethodKind.GET)

    suspend fun saveDay(date: LocalDate, request: SaveDailyLogRequest): ApiResult<DailyLog> =
        caller.authenticated("v1/days/$date", HttpMethodKind.PUT) { setBody(request) }

    /** Scoped to the stage when given: a hot flush is not offered to a cycle tracker. */
    suspend fun symptoms(lifeStage: LifeStage? = null): ApiResult<List<SymptomDefinition>> {
        val query = lifeStage?.let { "?lifeStage=${it.name}" }.orEmpty()
        return caller.authenticated("v1/symptoms$query", HttpMethodKind.GET)
    }
}
