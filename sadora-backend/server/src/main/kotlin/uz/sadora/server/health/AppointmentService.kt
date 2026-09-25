package uz.sadora.server.health

import kotlin.uuid.Uuid
import uz.sadora.contract.Appointment
import uz.sadora.contract.Limits
import uz.sadora.contract.SaveAppointmentRequest
import uz.sadora.server.core.ConsentRequiredException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException

/**
 * Appointments she keeps for herself.
 *
 * Writing one needs the health-storage consent, like every other health record. Reading
 * does not: withdrawing consent stops the app collecting more, it does not take away
 * what she already has — the same rule the cycle log follows.
 *
 * There is no entitlement gate. A free user must be able to write down when her scan is;
 * putting a doctor's appointment behind a subscription is not a product decision this
 * app is willing to make.
 */
class AppointmentService(
    private val repository: AppointmentRepository,
    private val access: HealthAccess,
) {

    suspend fun list(userId: Uuid): List<Appointment> {
        access.requireUser(userId)
        return repository.list(userId)
    }

    suspend fun add(userId: Uuid, request: SaveAppointmentRequest): Appointment {
        access.requireUser(userId)
        requireConsent(userId)
        validate(request)
        return repository.add(userId, request)
    }

    suspend fun update(userId: Uuid, id: Uuid, request: SaveAppointmentRequest): Appointment {
        access.requireUser(userId)
        requireConsent(userId)
        validate(request)
        if (!repository.update(userId, id, request)) throw notFound()
        return repository.byId(userId, id) ?: throw notFound()
    }

    suspend fun setCompleted(userId: Uuid, id: Uuid, done: Boolean): Appointment {
        access.requireUser(userId)
        if (!repository.setCompleted(userId, id, done)) throw notFound()
        return repository.byId(userId, id) ?: throw notFound()
    }

    suspend fun delete(userId: Uuid, id: Uuid) {
        access.requireUser(userId)
        if (!repository.delete(userId, id)) throw notFound()
    }

    private suspend fun requireConsent(userId: Uuid) {
        if (!access.hasStorageConsent(userId)) throw ConsentRequiredException("store_health")
    }

    private fun validate(request: SaveAppointmentRequest) {
        if (request.title.isBlank()) {
            throw ValidationException("title", "Nomi bo'sh bo'lishi mumkin emas")
        }
        if (request.title.length > Limits.APPOINTMENT_TITLE_MAX) {
            throw ValidationException(
                "title",
                "Nomi ${Limits.APPOINTMENT_TITLE_MAX} ta belgidan oshmasligi kerak",
            )
        }
        // The place was unbounded, which is a free text column open to anything.
        val place = request.place
        if (place != null && place.length > Limits.APPOINTMENT_PLACE_MAX) {
            throw ValidationException(
                "place",
                "Joyi ${Limits.APPOINTMENT_PLACE_MAX} ta belgidan oshmasligi kerak",
            )
        }
        val remind = request.remindHoursBefore
        if (remind != null && remind !in 0..MAX_REMIND_HOURS) {
            throw ValidationException("remindHoursBefore", "0 dan $MAX_REMIND_HOURS gacha bo'lishi kerak")
        }
        // A visit in 1990 or in 2150 is a typo, not a record; both used to be accepted.
        if (request.scheduledOn < EARLIEST_DATE || request.scheduledOn > LATEST_DATE) {
            throw ValidationException("scheduledOn", "Sana noto'g'ri")
        }
    }

    private fun notFound() = NotFoundException("Tadbir topilmadi")

    private companion object {
        /** A week. Reminding someone a month early about a scan is not a reminder. */
        const val MAX_REMIND_HOURS = 168
        val EARLIEST_DATE = kotlinx.datetime.LocalDate(2000, 1, 1)
        val LATEST_DATE = kotlinx.datetime.LocalDate(2099, 12, 31)
    }
}
