package org.example.project.data.api

import io.ktor.client.request.setBody
import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.Appointment
import uz.sadora.contract.CompleteAppointmentRequest
import uz.sadora.contract.SaveAppointmentRequest

/** Visits, scans and tests she has written down herself. */
class AppointmentApi(private val caller: ApiCaller) {

    suspend fun list(): ApiResult<List<Appointment>> =
        caller.authenticated("v1/appointments", HttpMethodKind.GET)

    suspend fun add(request: SaveAppointmentRequest): ApiResult<Appointment> =
        caller.authenticated("v1/appointments", HttpMethodKind.POST) { setBody(request) }

    suspend fun update(id: String, request: SaveAppointmentRequest): ApiResult<Appointment> =
        caller.authenticated("v1/appointments/$id", HttpMethodKind.PUT) { setBody(request) }

    suspend fun setCompleted(id: String, done: Boolean): ApiResult<Appointment> =
        caller.authenticated("v1/appointments/$id/completed", HttpMethodKind.PUT) {
            setBody(CompleteAppointmentRequest(done))
        }

    suspend fun delete(id: String): ApiResult<Ack> =
        caller.authenticated("v1/appointments/$id", HttpMethodKind.DELETE)
}
