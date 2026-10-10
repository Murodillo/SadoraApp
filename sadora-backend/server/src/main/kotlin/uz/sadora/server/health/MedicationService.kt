package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.Medication
import uz.sadora.contract.MedicationDay
import uz.sadora.contract.MedicationDose
import uz.sadora.contract.MedicationHistory
import uz.sadora.contract.MedicationSchedule
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.RefillRequest
import uz.sadora.contract.SaveMedicationRequest
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.Limits
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.contract.CoinReasons
import uz.sadora.server.core.RewardHooks
import uz.sadora.server.core.now
import uz.sadora.server.core.resolveTimeZone

/**
 * Medications and their intake log.
 *
 * The product rule that shapes this file: the app never counsels on a missed dose. So
 * nothing here computes an adherence score, produces guidance, or ranks a user's
 * behaviour — it returns what was due, what she recorded, and nothing more. The screen
 * points at the prescription or a pharmacist, and that is the right place for it.
 */
class MedicationService(
    private val repository: MedicationRepository,
    private val access: HealthAccess,
    /**
     * The reward scheme, as one call it can ignore.
     *
     * Defaulted to the no-op so nothing in this service's tests has to know the scheme
     * exists, and so a coin that cannot be written never costs a log entry.
     */
    private val rewards: RewardHooks = RewardHooks.None,
    /** The doctor's name behind each prescription id; nobody is named in tests without it. */
    private val prescribers: suspend (List<Uuid>) -> Map<Uuid, String> = { emptyMap() },
) {

    suspend fun list(userId: Uuid, includeArchived: Boolean): List<Medication> {
        access.requireUser(userId)
        return dtos(repository.listOf(userId, includeArchived))
    }

    /** Courses as the app reads them, with the prescribing doctor named where there is one. */
    suspend fun dtos(records: List<MedicationRecord>): List<Medication> {
        val ids = records.mapNotNull { it.prescriptionId }.distinct()
        val names = if (ids.isEmpty()) emptyMap() else prescribers(ids)
        return records.map { it.toDto(names[it.prescriptionId]) }
    }

    private suspend fun dto(record: MedicationRecord): Medication = dtos(listOf(record)).single()

    suspend fun day(userId: Uuid, date: LocalDate?): MedicationDay {
        val user = access.requireUser(userId)
        val target = date ?: now().dayIn(user.timezone)
        val medications = repository.listOf(userId, includeArchived = true)
        val intakes = repository.intakesBetween(userId, target, target)
        return MedicationDay(target, buildDoses(medications, intakes, target, target, resolveTimeZone(user.timezone)))
    }

    suspend fun history(userId: Uuid, medicationId: Uuid, days: Int): MedicationHistory {
        val user = access.requireUser(userId)
        val medication = repository.byId(userId, medicationId)
            ?: throw NotFoundException("Dori topilmadi")
        if (days !in 1..MAX_HISTORY_DAYS) {
            throw ValidationException("days", "1–$MAX_HISTORY_DAYS oralig'ida")
        }

        val to = now().dayIn(user.timezone)
        val from = to.plus(-(days - 1), DateTimeUnit.DAY)
        val intakes = repository.intakesFor(userId, medicationId, from, to)
        val zone = resolveTimeZone(user.timezone)

        val byDay = (0..from.daysUntil(to)).map { offset ->
            val date = from.plus(offset, DateTimeUnit.DAY)
            MedicationDay(date, buildDoses(listOf(medication), intakes, date, date, zone))
        }
        val allDoses = byDay.flatMap { it.doses }

        return MedicationHistory(
            medicationId = medicationId.toString(),
            from = from,
            to = to,
            days = byDay.reversed(),
            takenCount = allDoses.count { it.status == DoseStatus.TAKEN },
            skippedCount = allDoses.count { it.status == DoseStatus.SKIPPED },
            pendingCount = allDoses.count { it.status == DoseStatus.PENDING },
        )
    }

    // ---------------------------------------------------------------- writes

    suspend fun add(userId: Uuid, request: SaveMedicationRequest): Medication {
        val user = access.requireWritable(userId, FeatureKeys.MEDS_REMINDERS)
        validate(request)
        val startedOn = request.startedOn ?: now().dayIn(user.timezone)
        val id = repository.add(userId, request, startedOn)
        return repository.byId(userId, id)?.let { dto(it) } ?: throw NotFoundException("Dori topilmadi")
    }

    /** A course from a doctor's prescription, linked to it; the prescription service checked the rest. */
    suspend fun addPrescribed(userId: Uuid, request: SaveMedicationRequest, startedOn: LocalDate, prescriptionId: Uuid): Medication {
        access.requireWritable(userId, FeatureKeys.MEDS_REMINDERS)
        validate(request.copy(startedOn = startedOn))
        val id = repository.add(userId, request, startedOn, prescriptionId)
        return repository.byId(userId, id)?.let { dto(it) } ?: throw NotFoundException("Dori topilmadi")
    }

    /** A cancelled prescription's courses stop: archived, so they no longer ring. */
    suspend fun archivePrescribed(prescriptionId: Uuid, today: LocalDate): Int =
        repository.archiveForPrescription(prescriptionId, today)

    suspend fun update(userId: Uuid, id: Uuid, request: SaveMedicationRequest): Medication {
        access.requireWritable(userId, FeatureKeys.MEDS_REMINDERS)
        validate(request)
        val stored = repository.byId(userId, id) ?: throw NotFoundException("Dori topilmadi")
        if (!repository.update(userId, id, lockPrescribed(stored, request))) throw NotFoundException("Dori topilmadi")
        return repository.byId(userId, id)?.let { dto(it) } ?: throw NotFoundException("Dori topilmadi")
    }

    /**
     * A course from a doctor's prescription keeps what the doctor decided — the name,
     * dose, food relation, note, how often and how long — whatever the edit sends. Her
     * own are the times (as many as the doctor gave), reminders, the pack and the emoji.
     */
    private fun lockPrescribed(stored: MedicationRecord, request: SaveMedicationRequest): SaveMedicationRequest {
        if (stored.prescriptionId == null) return request
        if (request.schedule.times.size != stored.times.size) {
            throw ValidationException("schedule.times", "Shifokor kuniga ${stored.times.size} marta yozgan — shuncha vaqt tanlang")
        }
        return request.copy(
            name = stored.name,
            dosage = stored.dosage,
            unit = stored.unit,
            foodRelation = stored.foodRelation,
            note = stored.note,
            schedule = MedicationSchedule(stored.scheduleKind, request.schedule.times, stored.weekdays, stored.intervalDays),
            startedOn = stored.startedOn,
            endedOn = stored.endedOn,
        )
    }

    suspend fun archive(userId: Uuid, id: Uuid) {
        val user = access.requireWritable(userId, FeatureKeys.MEDS_REMINDERS)
        if (!repository.archive(userId, id, now().dayIn(user.timezone))) {
            throw NotFoundException("Dori topilmadi")
        }
    }

    /**
     * Records a dose and reconciles the pack.
     *
     * Stock moves only on the transition into or out of `taken`, so tapping "Qabul
     * qildim" twice does not take two tablets off the count, and correcting a mistaken
     * tap puts one back.
     */
    suspend fun recordDose(userId: Uuid, id: Uuid, request: RecordDoseRequest): MedicationDay {
        val user = access.requireWritable(userId, FeatureKeys.MEDS_REMINDERS)
        val medication = repository.byId(userId, id) ?: throw NotFoundException("Dori topilmadi")

        val today = now().dayIn(user.timezone)
        if (request.dueOn > today) {
            throw ValidationException("dueOn", "Kelajakdagi qabulni belgilab bo'lmaydi")
        }
        if (request.dueAt !in DoseSchedule.dosesOn(medication, request.dueOn)) {
            throw ValidationException("dueAt", "Bu vaqtda qabul rejalashtirilmagan")
        }

        if (request.status == DoseStatus.PENDING) {
            val previous = repository.intakesFor(userId, id, request.dueOn, request.dueOn)
                .firstOrNull { it.dueAt == request.dueAt }
            repository.clearIntake(userId, id, request.dueOn, request.dueAt)
            if (previous?.status == DoseStatus.TAKEN && medication.stockUnits != null) {
                repository.adjustStock(userId, id, +1)
            }
        } else {
            val previous = repository.recordIntake(userId, id, request.dueOn, request.dueAt, request.status)
            if (medication.stockUnits != null) {
                val delta = when {
                    previous != DoseStatus.TAKEN && request.status == DoseStatus.TAKEN -> -1
                    previous == DoseStatus.TAKEN && request.status != DoseStatus.TAKEN -> +1
                    else -> 0
                }
                if (delta != 0) repository.adjustStock(userId, id, delta)
            }
            // Only a confirmed dose pays, and only once per scheduled dose: the
            // reference is the dose itself, so re-confirming it earns nothing.
            if (request.status == DoseStatus.TAKEN) {
                rewards.logged(userId, CoinReasons.DOSE_TAKEN, "$id:${request.dueOn}:${request.dueAt}")
            }
        }
        return day(userId, request.dueOn)
    }

    suspend fun refill(userId: Uuid, id: Uuid, request: RefillRequest): Medication {
        access.requireWritable(userId, FeatureKeys.MEDS_REMINDERS)
        repository.byId(userId, id) ?: throw NotFoundException("Dori topilmadi")
        if (request.units == 0) throw ValidationException("units", "Nol bo'lishi mumkin emas")
        if (kotlin.math.abs(request.units) > MAX_REFILL) {
            throw ValidationException("units", "Eng ko'pi $MAX_REFILL")
        }
        repository.adjustStock(userId, id, request.units)
        return repository.byId(userId, id)?.let { dto(it) } ?: throw NotFoundException("Dori topilmadi")
    }

    // ---------------------------------------------------------------- plumbing

    private fun buildDoses(
        medications: List<MedicationRecord>,
        intakes: List<IntakeRecord>,
        from: LocalDate,
        to: LocalDate,
        zone: kotlinx.datetime.TimeZone,
    ): List<MedicationDose> {
        val recorded = intakes.associateBy { Triple(it.medicationId, it.dueOn, it.dueAt) }
        return (0..from.daysUntil(to)).flatMap { offset ->
            val date = from.plus(offset, DateTimeUnit.DAY)
            medications.flatMap { medication ->
                DoseSchedule.dosesOn(medication, date).map { dueAt ->
                    val intake = recorded[Triple(medication.id, date, dueAt)]
                    MedicationDose(
                        medicationId = medication.id.toString(),
                        name = medication.name,
                        emoji = medication.emoji,
                        dosage = medication.dosage,
                        foodRelation = medication.foodRelation,
                        dueOn = date,
                        dueAt = dueAt,
                        status = intake?.status ?: DoseStatus.PENDING,
                        takenAt = intake?.takeIf { it.status == DoseStatus.TAKEN }?.recordedAt,
                        isLate = intake?.isLate(date, dueAt, zone) ?: false,
                    )
                }
            }
        }.sortedWith(compareBy({ it.dueOn }, { it.dueAt }, { it.name }))
    }

    /**
     * Late is a fact for the history chip, not a judgement: recorded more than an hour
     * after it was due. Nothing acts on it.
     */
    private fun IntakeRecord.isLate(date: LocalDate, dueAt: LocalTime, zone: kotlinx.datetime.TimeZone): Boolean {
        if (status != DoseStatus.TAKEN) return false
        // Due times are hers, so the hour of grace is measured in her zone — read as
        // UTC, a Tashkent dose due at 08:00 was "on time" until 14:00.
        val dueInstant = kotlinx.datetime.LocalDateTime(date, dueAt).toInstant(zone)
        return recordedAt > dueInstant + kotlin.time.Duration.parse("1h")
    }

    private fun validate(request: SaveMedicationRequest) {
        if (request.name.isBlank()) throw ValidationException("name", "Dori nomini yozing")
        if (request.name.length > MAX_NAME) throw ValidationException("name", "Dori nomi eng ko'pi $MAX_NAME belgi bo'lsin")
        validateSchedule(request.schedule)
        request.stockUnits?.let {
            if (it < 0) throw ValidationException("stockUnits", "Manfiy bo'lishi mumkin emas")
            if (it > MAX_STOCK) throw ValidationException("stockUnits", "Eng ko'pi $MAX_STOCK")
        }
        val startedOn = request.startedOn
        val endedOn = request.endedOn
        if (startedOn != null && endedOn != null && endedOn < startedOn) {
            throw ValidationException("endedOn", "Boshlanishdan oldin tugashi mumkin emas")
        }
    }

    private fun validateSchedule(schedule: MedicationSchedule) {
        if (schedule.times.isEmpty()) {
            throw ValidationException("schedule.times", "Kamida bitta qabul vaqti kerak")
        }
        if (schedule.times.size > MAX_TIMES_PER_DAY) {
            throw ValidationException("schedule.times", "Kuniga eng ko'pi $MAX_TIMES_PER_DAY marta")
        }
        if (schedule.times.distinct().size != schedule.times.size) {
            throw ValidationException("schedule.times", "Vaqtlar takrorlanmasligi kerak")
        }
        when (schedule.kind) {
            ScheduleKind.WEEKDAYS -> if (schedule.weekdays.isEmpty()) {
                throw ValidationException("schedule.weekdays", "Kamida bitta kun tanlanishi kerak")
            }

            ScheduleKind.INTERVAL -> {
                val interval = schedule.intervalDays
                    ?: throw ValidationException("schedule.intervalDays", "Ko'rsatilishi shart")
                if (interval !in 1..MAX_INTERVAL_DAYS) {
                    throw ValidationException("schedule.intervalDays", "1–$MAX_INTERVAL_DAYS oralig'ida")
                }
            }

            ScheduleKind.DAILY -> Unit
        }
    }

    private fun MedicationRecord.toDto(prescribedBy: String?) = Medication(
        id = id.toString(),
        name = name,
        emoji = emoji,
        dosage = dosage,
        unit = unit,
        foodRelation = foodRelation,
        note = note,
        schedule = MedicationSchedule(scheduleKind, times, weekdays, intervalDays),
        remindersEnabled = remindersEnabled,
        startedOn = startedOn,
        endedOn = endedOn,
        stockUnits = stockUnits,
        stockDaysLeft = DoseSchedule.stockDaysLeft(this),
        active = active,
        createdAt = createdAt,
        prescriptionId = prescriptionId?.toString(),
        prescribedBy = prescribedBy,
    )

    private companion object {
        const val MAX_NAME = Limits.MEDICATION_NAME_MAX
        const val MAX_TIMES_PER_DAY = Limits.MEDICATION_TIMES_PER_DAY_MAX
        const val MAX_INTERVAL_DAYS = 90
        const val MAX_STOCK = Limits.MEDICATION_STOCK_MAX
        const val MAX_REFILL = 10_000
        const val MAX_HISTORY_DAYS = 180
    }
}
