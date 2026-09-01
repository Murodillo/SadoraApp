package org.example.project.data.api

import io.ktor.client.request.setBody
import kotlinx.datetime.LocalDate
import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.Medication
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.MedicationHistory
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.RefillRequest
import uz.sadora.contract.SaveMedicationRequest

class MedicationApi(private val caller: ApiCaller) {

    suspend fun list(includeArchived: Boolean = false): ApiResult<List<Medication>> =
        caller.authenticated("v1/meds?includeArchived=$includeArchived", HttpMethodKind.GET)

    suspend fun today(): ApiResult<MedicationDay> =
        caller.authenticated("v1/meds/today", HttpMethodKind.GET)

    suspend fun day(date: LocalDate): ApiResult<MedicationDay> =
        caller.authenticated("v1/meds/days/$date", HttpMethodKind.GET)

    suspend fun add(request: SaveMedicationRequest): ApiResult<Medication> =
        caller.authenticated("v1/meds", HttpMethodKind.POST) { setBody(request) }

    suspend fun update(id: String, request: SaveMedicationRequest): ApiResult<Medication> =
        caller.authenticated("v1/meds/$id", HttpMethodKind.PUT) { setBody(request) }

    suspend fun archive(id: String): ApiResult<Ack> =
        caller.authenticated("v1/meds/$id", HttpMethodKind.DELETE)

    /** Sending `pending` undoes a mistaken tap and puts the dose back in the pack. */
    suspend fun recordDose(id: String, request: RecordDoseRequest): ApiResult<MedicationDay> =
        caller.authenticated("v1/meds/$id/doses", HttpMethodKind.POST) { setBody(request) }

    suspend fun refill(id: String, units: Int): ApiResult<Medication> =
        caller.authenticated("v1/meds/$id/stock", HttpMethodKind.POST) { setBody(RefillRequest(units)) }

    suspend fun history(id: String, days: Int = 14): ApiResult<MedicationHistory> =
        caller.authenticated("v1/meds/$id/history?days=$days", HttpMethodKind.GET)
}
