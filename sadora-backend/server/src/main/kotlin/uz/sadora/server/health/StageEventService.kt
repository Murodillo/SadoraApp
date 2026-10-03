package uz.sadora.server.health

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import uz.sadora.contract.Epds
import uz.sadora.contract.FeedingSide
import uz.sadora.contract.HotFlushTrigger
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.server.core.ConsentRequiredException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/**
 * Feeds, kick counts, contractions, hot flushes and mood questionnaires.
 *
 * Writing needs the health-storage consent and nothing else, as with appointments: a
 * mother timing contractions or a woman answering a depression questionnaire is not
 * asked for a subscription first.
 *
 * Each kind is held to what it means. A feed at the breast has a length and a side; from
 * a bottle it has millilitres. A questionnaire is stored as its answers, and its score
 * is worked out here — never taken from the request.
 */
class StageEventService(
    private val repository: StageEventRepository,
    private val access: HealthAccess,
) {

    suspend fun list(userId: Uuid, kind: StageEventKind?, days: Int?): List<StageEvent> {
        access.requireUser(userId)
        val window = (days ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)
        return repository.list(userId, kind, now() - window.days, LIST_LIMIT)
    }

    suspend fun add(userId: Uuid, request: LogStageEventRequest): StageEvent {
        access.requireUser(userId)
        if (!access.hasStorageConsent(userId)) throw ConsentRequiredException("store_health")

        val at = request.startedAt
        // A few minutes of slack: a phone's clock a little ahead is not a future event.
        if (at > now() + CLOCK_SLACK) throw ValidationException("startedAt", "Kelajakdagi sana bo'lishi mumkin emas")
        if (at < now() - MAX_DAYS.days) throw ValidationException("startedAt", "Sana juda eski")

        return when (request.kind) {
            StageEventKind.FEEDING -> feeding(userId, request)
            StageEventKind.KICK_COUNT -> {
                val kicks = within("value", request.value, 1..MAX_KICKS)
                val seconds = within("durationSeconds", request.durationSeconds, 1..MAX_KICK_SECONDS)
                repository.add(userId, request.kind, at, seconds, kicks, null)
            }
            StageEventKind.CONTRACTION -> {
                val seconds = within("durationSeconds", request.durationSeconds, 1..MAX_CONTRACTION_SECONDS)
                repository.add(userId, request.kind, at, seconds, null, null)
            }
            StageEventKind.HOT_FLUSH -> {
                val intensity = within("value", request.value, 1..3)
                val trigger = request.detail?.let { raw ->
                    enumFromDb<HotFlushTrigger>(raw)?.dbValue() ?: throw ValidationException("detail", "Noto'g'ri format")
                }
                repository.add(userId, request.kind, at, null, intensity, trigger)
            }
            StageEventKind.MOOD_SCREEN -> {
                val answers = request.answers
                if (answers == null || !Epds.isComplete(answers)) {
                    throw ValidationException("answers", "Hamma savollarga javob bering")
                }
                repository.add(userId, request.kind, at, null, Epds.score(answers), answers.joinToString(","))
            }
        }
    }

    suspend fun delete(userId: Uuid, id: Uuid) {
        access.requireUser(userId)
        if (!repository.delete(userId, id)) throw NotFoundException("Yozuv topilmadi")
    }

    private suspend fun feeding(userId: Uuid, request: LogStageEventRequest): StageEvent {
        val side = enumFromDb<FeedingSide>(request.detail)
            ?: throw ValidationException("detail", "Noto'g'ri format")
        return if (side.isBreast) {
            val seconds = within("durationSeconds", request.durationSeconds, 1..MAX_FEED_SECONDS)
            repository.add(userId, request.kind, request.startedAt, seconds, null, side.dbValue())
        } else {
            val ml = within("value", request.value, 1..MAX_FEED_ML)
            val seconds = request.durationSeconds?.let { within("durationSeconds", it, 1..MAX_FEED_SECONDS) }
            repository.add(userId, request.kind, request.startedAt, seconds, ml, side.dbValue())
        }
    }

    private fun within(field: String, value: Int?, range: IntRange): Int {
        if (value == null || value !in range) {
            throw ValidationException(field, "${range.first}–${range.last} oralig'ida bo'lishi kerak")
        }
        return value
    }

    private companion object {
        const val DEFAULT_DAYS = 7
        /** Enough for a whole trimester of kick counts, or a season of hot flushes. */
        const val MAX_DAYS = 120
        const val LIST_LIMIT = 1000
        val CLOCK_SLACK = 5.minutes

        const val MAX_KICKS = 100
        /** Six hours: a count left running all afternoon is still a count. */
        const val MAX_KICK_SECONDS = 21_600
        /** Ten minutes. Longer is a timer left running, not a contraction. */
        const val MAX_CONTRACTION_SECONDS = 600
        const val MAX_FEED_SECONDS = 7_200
        const val MAX_FEED_ML = 500
    }
}
