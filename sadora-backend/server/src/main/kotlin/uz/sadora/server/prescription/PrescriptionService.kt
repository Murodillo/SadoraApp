package uz.sadora.server.prescription

import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import uz.sadora.contract.AddPrescriptionRequest
import uz.sadora.contract.AddPrescriptionResult
import uz.sadora.contract.CancelPrescriptionRequest
import uz.sadora.contract.Limits
import uz.sadora.contract.Medication
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.Prescription
import uz.sadora.contract.SaveMedicationRequest
import uz.sadora.server.community.MessagingRepository
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.doctor.DoctorRepository
import uz.sadora.server.health.MedicationService
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.notify.TARGET_CLIENT
import uz.sadora.server.user.UserRepository

/**
 * A prescription after it was sent: read by the two people in the consultation,
 * cancelled by the doctor, added to the patient's medications by the patient.
 *
 * Sending lives with the other messages in `MessagingService`, so the same gates — an
 * open window, a verified doctor, the daily ceiling, the push — apply to it.
 */
class PrescriptionService(
    private val prescriptions: PrescriptionRepository,
    private val messages: MessagingRepository,
    private val doctors: DoctorRepository,
    private val users: UserRepository,
    private val medications: MedicationService,
    private val notifications: NotificationRepository,
    private val clock: Clock = Clock.System,
) {

    /** One prescription, for either side of its consultation. */
    suspend fun get(userId: Uuid, id: Uuid): Prescription = view(userId, requireReadable(userId, id))

    /** A consultation's prescriptions, newest first: the doctor's history of a patient. */
    suspend fun ofConversation(userId: Uuid, conversationId: Uuid): List<Prescription> {
        val thread = messages.conversationById(conversationId)
        if (thread == null || !thread.has(userId) || !thread.isConsultation) throw NotFoundException("Suhbat topilmadi")
        val records = prescriptions.ofConversation(conversationId, LIST_MAX)
        return records.map { view(userId, it) }
    }

    /** Hers, from every doctor, newest first: "Shifokor retseptlari". */
    suspend fun mine(userId: Uuid): List<Prescription> {
        val records = prescriptions.ofPatient(userId, LIST_MAX)
        val doctorsById = doctors.byIds(records.map { it.doctorId }.distinct())
        return records.mapNotNull { record ->
            doctorsById[record.doctorId]?.let { PrescriptionRules.dto(record, it, patientName = null) }
        }
    }

    /**
     * The doctor cancels what she wrote — at any time, the window open or not, because a
     * mistaken prescription is a safety matter rather than a conversation. Whatever the
     * patient added from it is archived and stops ringing, and she is told why.
     */
    suspend fun cancel(userId: Uuid, id: Uuid, request: CancelPrescriptionRequest): Prescription {
        val record = prescriptions.byId(id) ?: throw NotFoundException("Retsept topilmadi")
        val doctor = doctors.byUser(userId)
        if (doctor == null || doctor.id != record.doctorId) throw ForbiddenException(message = "Retseptni faqat uni yozgan shifokor bekor qiladi")
        val reason = request.reason.trim()
        if (reason.isEmpty()) throw ValidationException("reason", "Sababini yozing")
        if (reason.length > Limits.PRESCRIPTION_CANCEL_REASON_MAX) {
            throw ValidationException("reason", "Eng ko'pi ${Limits.PRESCRIPTION_CANCEL_REASON_MAX} belgi")
        }
        val at = clock.now()
        if (!prescriptions.cancel(id, reason, at)) throw ConflictException("Retsept allaqachon bekor qilingan")

        val patient = users.findById(record.patientId)
        val today = at.dayIn(patient?.timezone ?: uz.sadora.server.core.DEFAULT_TIMEZONE)
        medications.archivePrescribed(id, today)
        notifications.enqueue(
            userId = record.patientId,
            category = NotificationCategory.SYSTEM,
            title = "${doctor.fullName} ✓: retsept bekor qilindi",
            body = reason.take(PUSH_PREVIEW),
            scheduledFor = at,
            dedupeKey = "rx-cancel:$id",
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
            targetApp = TARGET_CLIENT,
            link = "sadora://conversation/${record.conversationId}",
        )
        return view(userId, prescriptions.byId(id)!!)
    }

    /**
     * The patient adds the medicines she keeps to her own list, with reminders.
     *
     * Day 1 is [AddPrescriptionRequest.startOn]; an item from day N starts N−1 days
     * later and runs for its days, or with no end. The times are hers to shift, but as
     * many as the doctor wrote. It can be added once: the claim is taken first, so two
     * taps never make two courses.
     */
    suspend fun add(userId: Uuid, id: Uuid, request: AddPrescriptionRequest): AddPrescriptionResult {
        val record = prescriptions.byId(id)
        if (record == null || record.patientId != userId) throw NotFoundException("Retsept topilmadi")
        if (record.cancelledAt != null) throw ConflictException("Retsept bekor qilingan")
        if (record.addedAt != null) throw ConflictException("Retsept allaqachon qo'shilgan")

        if (request.items.isEmpty()) throw ValidationException("items", "Kamida bitta dorini tanlang")
        if (request.items.map { it.index }.distinct().size != request.items.size) {
            throw ValidationException("items", "Dorilar takrorlanmasligi kerak")
        }
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val today = clock.now().dayIn(user.timezone)
        if (request.startOn < today.plus(-1, DateTimeUnit.DAY) || request.startOn > today.plus(START_AHEAD_DAYS, DateTimeUnit.DAY)) {
            throw ValidationException("startOn", "Bugundan $START_AHEAD_DAYS kun ichida")
        }
        val courses = request.items.map { chosen ->
            val item = record.items.getOrNull(chosen.index) ?: throw ValidationException("items", "Bunday dori yo'q")
            val times = chosen.times.sorted()
            if (times.size != item.schedule.times.size || times.distinct().size != times.size) {
                throw ValidationException(PrescriptionRules.itemField(chosen.index, "times"), "Shifokor kuniga ${item.schedule.times.size} marta yozgan")
            }
            val startedOn = request.startOn.plus(item.startDay - 1, DateTimeUnit.DAY)
            val endedOn = item.days?.let { startedOn.plus(it - 1, DateTimeUnit.DAY) }
            startedOn to SaveMedicationRequest(
                name = item.name,
                dosage = item.dose,
                unit = item.unit,
                foodRelation = item.foodRelation,
                note = item.note,
                schedule = item.schedule.copy(times = times),
                remindersEnabled = true,
                startedOn = startedOn,
                endedOn = endedOn,
            )
        }

        if (!prescriptions.markAdded(id, clock.now())) throw ConflictException("Retsept allaqachon qo'shilgan")
        val added = mutableListOf<Medication>()
        try {
            courses.forEach { (startedOn, course) -> added += medications.addPrescribed(userId, course, startedOn, id) }
        } catch (failure: Exception) {
            // Half a prescription is worse than none: undo what was added and let her retry.
            if (added.isNotEmpty()) medications.archivePrescribed(id, today)
            prescriptions.clearAdded(id)
            throw failure
        }
        return AddPrescriptionResult(view(userId, prescriptions.byId(id)!!), added)
    }

    private suspend fun requireReadable(userId: Uuid, id: Uuid): PrescriptionRecord {
        val record = prescriptions.byId(id) ?: throw NotFoundException("Retsept topilmadi")
        if (record.patientId == userId) return record
        val doctor = doctors.byUser(userId)
        if (doctor != null && doctor.id == record.doctorId) return record
        throw NotFoundException("Retsept topilmadi")
    }

    /** The patient's name rides along only for the doctor who wrote it. */
    private suspend fun view(viewer: Uuid, record: PrescriptionRecord): Prescription {
        val doctor = doctors.byId(record.doctorId) ?: throw NotFoundException("Retsept topilmadi")
        val patientName = if (doctor.userId == viewer) users.findById(record.patientId)?.name?.ifBlank { null } else null
        return PrescriptionRules.dto(record, doctor, patientName)
    }

    private companion object {
        const val LIST_MAX = 100
        const val PUSH_PREVIEW = 80
        const val START_AHEAD_DAYS = 30
    }
}
