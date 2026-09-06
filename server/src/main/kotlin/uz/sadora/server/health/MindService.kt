package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.JournalEntry
import uz.sadora.contract.LogPracticeRequest
import uz.sadora.contract.MindCheckIn
import uz.sadora.contract.MindPractice
import uz.sadora.contract.MindSummary
import uz.sadora.contract.SaveJournalEntryRequest
import uz.sadora.contract.UpdateJournalEntryRequest
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now

/**
 * The Mind tab: a three-dial check-in, a private journal, and a practice log.
 *
 * The check-in shares the daily row with the cycle log rather than getting a table of
 * its own — mood on the 14th is one fact, whichever screen recorded it.
 */
class MindService(
    private val mind: MindRepository,
    private val health: HealthRepository,
    private val access: HealthAccess,
) {

    suspend fun summary(userId: Uuid): MindSummary {
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        val from = today.minus(WINDOW_DAYS - 1, DateTimeUnit.DAY)
        val logs = health.logsBetween(userId, from, today)
        val todayLog = logs.firstOrNull { it.date == today }

        val moods = logs.mapNotNull { it.mood?.score }
        val energies = logs.mapNotNull { it.energy }
        val stresses = logs.mapNotNull { it.stress }

        return MindSummary(
            today = today,
            checkIn = MindCheckIn(todayLog?.mood, todayLog?.energy, todayLog?.stress),
            recentEntries = mind.recentEntries(userId, RECENT_ENTRIES),
            recentPractices = mind.recentPractices(userId, RECENT_PRACTICES),
            averageMood = moods.averageOrNull(),
            averageEnergy = energies.averageOrNull(),
            averageStress = stresses.averageOrNull(),
            daysLogged = logs.count { it.mood != null || it.energy != null || it.stress != null },
            windowDays = WINDOW_DAYS,
        )
    }

    /**
     * Records the check-in without disturbing anything else on the day.
     *
     * The cycle sheet and the Mind tab both write to the same row, so this reads the day
     * and puts it back with the three dials replaced — saving the check-in must not
     * erase symptoms logged an hour earlier.
     */
    suspend fun saveCheckIn(userId: Uuid, checkIn: MindCheckIn): MindCheckIn {
        val user = access.requireWritable(userId, FeatureKeys.MIND_JOURNAL)
        val today = now().dayIn(user.timezone)
        checkIn.energy?.let { validateScale("energy", it) }
        checkIn.stress?.let { validateScale("stress", it) }

        val existing = health.logOn(userId, today)
        health.saveLog(
            userId = userId,
            date = today,
            request = uz.sadora.contract.SaveDailyLogRequest(
                flow = existing?.flow,
                mood = checkIn.mood,
                energy = checkIn.energy,
                stress = checkIn.stress,
                symptoms = existing?.symptoms.orEmpty(),
                note = existing?.note,
            ),
        )
        return checkIn
    }

    // ---------------------------------------------------------------- journal

    suspend fun entries(userId: Uuid, from: LocalDate, to: LocalDate): List<JournalEntry> {
        access.requireUser(userId)
        if (from > to) throw ValidationException("from", "Boshlanish sanasi tugashdan keyin bo'lishi mumkin emas")
        return mind.entriesBetween(userId, from, to)
    }

    suspend fun addEntry(userId: Uuid, request: SaveJournalEntryRequest): JournalEntry {
        val user = access.requireWritable(userId, FeatureKeys.MIND_JOURNAL)
        validateBody(request.body)
        if (request.date > now().dayIn(user.timezone)) {
            throw ValidationException("date", "Kelajakdagi sana bo'lishi mumkin emas")
        }
        val id = mind.addEntry(userId, request.date, request.body.trim())
        return mind.entryById(userId, id) ?: throw NotFoundException("Yozuv topilmadi")
    }

    suspend fun updateEntry(userId: Uuid, id: Uuid, request: UpdateJournalEntryRequest): JournalEntry {
        access.requireWritable(userId, FeatureKeys.MIND_JOURNAL)
        validateBody(request.body)
        if (!mind.updateEntry(userId, id, request.body.trim())) {
            throw NotFoundException("Yozuv topilmadi")
        }
        return mind.entryById(userId, id) ?: throw NotFoundException("Yozuv topilmadi")
    }

    suspend fun deleteEntry(userId: Uuid, id: Uuid) {
        access.requireWritable(userId, FeatureKeys.MIND_JOURNAL)
        if (!mind.deleteEntry(userId, id)) throw NotFoundException("Yozuv topilmadi")
    }

    // ---------------------------------------------------------------- practice

    suspend fun logPractice(userId: Uuid, request: LogPracticeRequest): MindPractice {
        access.requireWritable(userId, FeatureKeys.MIND_JOURNAL)
        if (request.durationSeconds !in 1..MAX_PRACTICE_SECONDS) {
            throw ValidationException("durationSeconds", "1–$MAX_PRACTICE_SECONDS soniya oralig'ida")
        }
        return mind.addPractice(userId, request.kind, request.durationSeconds)
    }

    private fun validateScale(field: String, value: Int) {
        if (value !in 1..5) throw ValidationException(field, "1–5 oralig'ida bo'lishi kerak")
    }

    private fun validateBody(body: String) {
        if (body.isBlank()) throw ValidationException("body", "Bo'sh bo'lishi mumkin emas")
        if (body.length > MAX_BODY_LENGTH) {
            throw ValidationException("body", "Eng ko'pi $MAX_BODY_LENGTH belgi")
        }
    }

    private fun List<Int>.averageOrNull(): Double? =
        if (isEmpty()) null else (sum().toDouble() / size * 10).let { kotlin.math.round(it) / 10 }

    private companion object {
        const val WINDOW_DAYS = 14
        const val RECENT_ENTRIES = 20
        const val RECENT_PRACTICES = 10
        const val MAX_BODY_LENGTH = 5000
        const val MAX_PRACTICE_SECONDS = 7200
    }
}
