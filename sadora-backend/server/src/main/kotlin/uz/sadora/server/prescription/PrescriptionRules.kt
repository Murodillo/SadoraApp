package uz.sadora.server.prescription

import uz.sadora.contract.DoctorAuthor
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.Limits
import uz.sadora.contract.Prescription
import uz.sadora.contract.PrescriptionItem
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.SendPrescriptionRequest
import uz.sadora.server.core.Photos
import uz.sadora.server.core.ValidationException
import uz.sadora.server.doctor.DoctorRecord

/** What a doctor may send, checked in one place for the app and the panel alike. */
object PrescriptionRules {

    /** The request with every text trimmed and blank optionals dropped; throws on the first fault. */
    fun clean(request: SendPrescriptionRequest): SendPrescriptionRequest {
        if (request.items.isEmpty()) throw ValidationException("items", "Kamida bitta dori kerak")
        if (request.items.size > Limits.PRESCRIPTION_ITEMS_MAX) {
            throw ValidationException("items", "Eng ko'pi ${Limits.PRESCRIPTION_ITEMS_MAX} ta dori")
        }
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > Limits.PRESCRIPTION_NOTE_MAX) {
            throw ValidationException("note", "Eng ko'pi ${Limits.PRESCRIPTION_NOTE_MAX} belgi")
        }
        return SendPrescriptionRequest(request.items.mapIndexed { index, item -> clean(index, item) }, note)
    }

    private fun clean(index: Int, item: PrescriptionItem): PrescriptionItem {
        fun at(name: String) = itemField(index, name)
        val name = item.name.trim()
        if (name.isEmpty()) throw ValidationException(at("name"), "Dori nomi kerak")
        if (name.length > Limits.MEDICATION_NAME_MAX) throw ValidationException(at("name"), "Eng ko'pi ${Limits.MEDICATION_NAME_MAX} belgi")
        val dose = item.dose.trim()
        if (dose.isEmpty()) throw ValidationException(at("dose"), "Doza kerak")
        if (dose.length > Limits.PRESCRIPTION_DOSE_MAX) throw ValidationException(at("dose"), "Eng ko'pi ${Limits.PRESCRIPTION_DOSE_MAX} belgi")
        val unit = item.unit?.trim()?.takeIf { it.isNotEmpty() }
        if (unit != null && unit.length > Limits.PRESCRIPTION_UNIT_MAX) {
            throw ValidationException(at("unit"), "Eng ko'pi ${Limits.PRESCRIPTION_UNIT_MAX} belgi")
        }
        val note = item.note?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > Limits.PRESCRIPTION_ITEM_NOTE_MAX) {
            throw ValidationException(at("note"), "Eng ko'pi ${Limits.PRESCRIPTION_ITEM_NOTE_MAX} belgi")
        }

        val schedule = item.schedule
        val times = schedule.times.sorted()
        if (times.isEmpty()) throw ValidationException(at("schedule.times"), "Kamida bitta qabul vaqti kerak")
        if (times.size > Limits.MEDICATION_TIMES_PER_DAY_MAX) {
            throw ValidationException(at("schedule.times"), "Kuniga eng ko'pi ${Limits.MEDICATION_TIMES_PER_DAY_MAX} marta")
        }
        if (times.distinct().size != times.size) throw ValidationException(at("schedule.times"), "Vaqtlar takrorlanmasligi kerak")
        val cleanSchedule = when (schedule.kind) {
            ScheduleKind.DAILY -> schedule.copy(times = times, weekdays = emptyList(), intervalDays = null)
            ScheduleKind.INTERVAL -> {
                val every = schedule.intervalDays
                if (every == null || every !in 2..INTERVAL_MAX) {
                    throw ValidationException(at("schedule.intervalDays"), "2–$INTERVAL_MAX oralig'ida")
                }
                schedule.copy(times = times, weekdays = emptyList())
            }
            ScheduleKind.WEEKDAYS -> throw ValidationException(at("schedule.kind"), "Har kuni yoki har N kunda")
        }

        if (item.startDay !in 1..Limits.PRESCRIPTION_START_DAY_MAX) {
            throw ValidationException(at("startDay"), "1–${Limits.PRESCRIPTION_START_DAY_MAX} oralig'ida")
        }
        item.days?.let {
            if (it !in 1..Limits.PRESCRIPTION_DAYS_MAX) throw ValidationException(at("days"), "1–${Limits.PRESCRIPTION_DAYS_MAX} oralig'ida")
        }
        return item.copy(name = name, dose = dose, unit = unit, note = note, schedule = cleanSchedule)
    }

    /**
     * The prescription as a plain message body, in Uzbek: what moderation reads, what a
     * push previews, and what an app that does not know the kind shows as text.
     */
    fun plainText(items: List<PrescriptionItem>, note: String?): String = buildString {
        append("💊 Retsept")
        items.forEachIndexed { index, item ->
            append("\n").append(index + 1).append(". ").append(item.name)
            append(" — ").append(listOfNotNull(item.dose, item.unit).joinToString(" "))
            append(", ").append(item.schedule.times.joinToString(", "))
            if (item.schedule.kind == ScheduleKind.INTERVAL) append(" (har ${item.schedule.intervalDays} kunda)")
            append(", ").append(FOOD[item.foodRelation])
            append(", ")
            append(if (item.startDay > 1) "${item.startDay}-kundan " else "")
            append(item.days?.let { "$it kun" } ?: "doimiy")
            item.note?.let { append(". ").append(it) }
        }
        note?.let { append("\n").append(it) }
    }

    fun author(doctor: DoctorRecord) = DoctorAuthor(
        doctor.id.toString(),
        doctor.fullName,
        doctor.specialty,
        doctor.photoUpdatedAt?.let { Photos.doctorUrl(doctor.id, it) },
    )

    fun dto(record: PrescriptionRecord, doctor: DoctorRecord, patientName: String?) = Prescription(
        id = record.id.toString(),
        conversationId = record.conversationId.toString(),
        messageId = record.messageId.toString(),
        doctor = author(doctor),
        patientName = patientName,
        items = record.items,
        note = record.note,
        createdAt = record.createdAt,
        cancelledAt = record.cancelledAt,
        cancelReason = record.cancelReason,
        addedAt = record.addedAt,
    )

    /** "items[2].dose": which medicine a refusal is about, built apart from the sentence. */
    fun itemField(index: Int, name: String): String = "items[" + index + "]." + name

    const val INTERVAL_MAX = 90

    private val FOOD = mapOf(
        FoodRelation.ANY to "ovqatdan qat'i nazar",
        FoodRelation.BEFORE to "ovqatdan oldin",
        FoodRelation.WITH to "ovqat bilan",
        FoodRelation.AFTER to "ovqatdan keyin",
    )
}
