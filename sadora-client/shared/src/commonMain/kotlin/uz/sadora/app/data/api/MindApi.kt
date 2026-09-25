package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import kotlinx.datetime.LocalDate
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.JournalEntry
import uz.sadora.contract.LogPracticeRequest
import uz.sadora.contract.MindCheckIn
import uz.sadora.contract.MindPractice
import uz.sadora.contract.MindSummary
import uz.sadora.contract.SaveJournalEntryRequest
import uz.sadora.contract.UpdateJournalEntryRequest

class MindApi(private val caller: ApiCaller) {

    suspend fun summary(): ApiResult<MindSummary> =
        caller.authenticated("v1/mind/today", HttpMethodKind.GET)

    suspend fun saveCheckIn(checkIn: MindCheckIn): ApiResult<MindCheckIn> =
        caller.authenticated("v1/mind/check-in", HttpMethodKind.PUT) { setBody(checkIn) }

    suspend fun journal(from: LocalDate, to: LocalDate): ApiResult<List<JournalEntry>> =
        caller.authenticated("v1/mind/journal?from=$from&to=$to", HttpMethodKind.GET)

    suspend fun addEntry(request: SaveJournalEntryRequest): ApiResult<JournalEntry> =
        caller.authenticated("v1/mind/journal", HttpMethodKind.POST) { setBody(request) }

    suspend fun updateEntry(id: String, request: UpdateJournalEntryRequest): ApiResult<JournalEntry> =
        caller.authenticated("v1/mind/journal/$id", HttpMethodKind.PATCH) { setBody(request) }

    suspend fun deleteEntry(id: String): ApiResult<Ack> =
        caller.authenticated("v1/mind/journal/$id", HttpMethodKind.DELETE)

    suspend fun logPractice(request: LogPracticeRequest): ApiResult<MindPractice> =
        caller.authenticated("v1/mind/practices", HttpMethodKind.POST) { setBody(request) }
}
