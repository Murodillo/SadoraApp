package uz.sadora.server.kegel

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import uz.sadora.contract.CoinAward
import uz.sadora.contract.CoinReasons
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.KegelLevel
import uz.sadora.contract.KegelProgram
import uz.sadora.contract.KegelSessionResult
import uz.sadora.contract.KegelSettingsRequest
import uz.sadora.contract.KegelState
import uz.sadora.contract.Limits
import uz.sadora.contract.LogKegelSessionRequest
import uz.sadora.contract.SaveKegelCheckRequest
import uz.sadora.contract.SharedPelvicFloor
import uz.sadora.server.core.ConsentRequiredException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * Kegel exercises: the program for her stage, the levels she has opened, her reminders and
 * the four-weekly leak check.
 *
 * Nothing is stored about her progress but the sessions themselves: the open level is
 * counted from them every time, so a deleted session or a changed program can never leave
 * a level open that she did not earn, or shut one she did.
 */
class KegelService(
    private val kegel: KegelRepository,
    private val access: HealthAccess,
    private val users: UserRepository,
    /** Gul for a session; null in tests without the rewards service. */
    private val coins: (suspend (Uuid, String) -> CoinAward?)? = null,
) {

    suspend fun state(userId: Uuid): KegelState {
        val user = access.requireUser(userId)
        return stateOf(user, now().dayIn(user.timezone))
    }

    suspend fun logSession(userId: Uuid, request: LogKegelSessionRequest): KegelSessionResult {
        val user = requireConsent(userId)
        val today = now().dayIn(user.timezone)
        val levels = KegelPrograms.levels(request.program)
        val sessions = kegel.sessions(userId)
        val before = unlocked(levels, sessions, request.program)
        if (request.level !in 1..before) throw ValidationException("level", "Bu daraja hali ochilmagan")
        if (request.level > Limits.KEGEL_FREE_LEVELS) access.requireFeature(userId, FeatureKeys.KEGEL_ADVANCED)
        if (request.reps !in 1..MAX_REPS) throw ValidationException("reps", "1–$MAX_REPS oralig'ida")
        if (request.durationSeconds !in 1..MAX_SECONDS) {
            throw ValidationException("durationSeconds", "1–$MAX_SECONDS soniya oralig'ida")
        }

        kegel.addSession(userId, request.program, request.level, today, request.reps, request.durationSeconds)
        val paid = runCatching { coins?.invoke(userId, CoinReasons.KEGEL_SESSION) }.getOrNull()
        val state = stateOf(user, today)
        return KegelSessionResult(
            state = state,
            coins = paid,
            unlockedLevel = state.unlockedLevel.takeIf { state.program == request.program && it > before },
        )
    }

    suspend fun saveSettings(userId: Uuid, request: KegelSettingsRequest): KegelState {
        val user = requireConsent(userId)
        val reminders = request.reminders.distinct().sorted()
        if (reminders.size > Limits.KEGEL_MAX_REMINDERS) {
            throw ValidationException("reminders", "Eslatmalar ko'pi bilan ${Limits.KEGEL_MAX_REMINDERS} ta bo'ladi")
        }
        // Her stage's program is stored as "none", so a stage change later takes her along.
        val program = request.program?.takeIf { it != KegelProgram.forStage(user.lifeStage) }
        kegel.saveSettings(userId, program, reminders)
        return stateOf(user, now().dayIn(user.timezone))
    }

    suspend fun saveCheck(userId: Uuid, request: SaveKegelCheckRequest): KegelState {
        val user = requireConsent(userId)
        if (request.frequency !in 0..4) throw ValidationException("frequency", "0–4 oralig'ida")
        if (request.amount !in 0..3) throw ValidationException("amount", "0–3 oralig'ida")
        if (request.bother !in 0..10) throw ValidationException("bother", "0–10 oralig'ida")
        // "Never" has no amount and no situations, whatever the form still held.
        val cleaned = if (request.frequency == 0) request.copy(amount = 0, situations = emptyList()) else request
        val today = now().dayIn(user.timezone)
        kegel.saveCheck(userId, today, cleaned)
        return stateOf(user, today)
    }

    /** For the doctor: the window's training and checks; null when there is neither. */
    suspend fun shared(userId: Uuid, from: LocalDate, to: LocalDate): SharedPelvicFloor? {
        val user = users.findById(userId) ?: return null
        val all = kegel.sessions(userId)
        val inWindow = all.filter { it.doneOn in from..to }
        val checks = kegel.checks(userId, from, to)
        if (inWindow.isEmpty() && checks.isEmpty()) return null
        val program = programOf(user)
        return SharedPelvicFloor(
            program = program,
            level = inWindow.lastOrNull()?.level ?: unlocked(KegelPrograms.levels(program), all, program),
            sessions = inWindow.size,
            activeDays = inWindow.map { it.doneOn }.distinct().size,
            checks = checks,
        )
    }

    // ---------------------------------------------------------------- counting

    private suspend fun stateOf(user: UserRecord, today: LocalDate): KegelState {
        val settings = kegel.settings(user.id)
        val program = settings?.program ?: KegelProgram.forStage(user.lifeStage)
        val levels = KegelPrograms.levels(program)
        val sessions = kegel.sessions(user.id)
        val unlocked = unlocked(levels, sessions, program)
        val days = sessions.map { it.doneOn }.distinct().sorted()
        val checks = kegel.checks(user.id)
        val reminders = settings?.reminders.orEmpty()
        val baseline = if (program == KegelProgram.POSTPARTUM) users.stageBaselineOf(user.id) else null
        return KegelState(
            program = program,
            stageProgram = KegelProgram.forStage(user.lifeStage),
            levels = levels,
            unlockedLevel = unlocked,
            daysAtLevel = daysAt(sessions, program, unlocked),
            todaySessions = sessions.count { it.doneOn == today },
            dailyGoal = reminders.size.takeIf { it > 0 } ?: DEFAULT_DAILY_GOAL,
            streakDays = streak(days, today),
            totalSessions = sessions.size,
            recentDays = days.filter { it >= today.minus(RECENT_DAYS - 1, DateTimeUnit.DAY) },
            reminders = reminders,
            checkDue = checks.firstOrNull()?.let { it.takenOn.daysUntil(today) >= Limits.KEGEL_CHECK_DAYS } ?: true,
            checks = checks.take(MAX_CHECKS),
            postpartumEarly = baseline?.birthDate?.let { it.daysUntil(today) in 0 until POSTPARTUM_WAIT_DAYS } ?: false,
            advanced = access.feature(user.id, FeatureKeys.KEGEL_ADVANCED, user.timezone)?.available == true,
        )
    }

    private suspend fun programOf(user: UserRecord): KegelProgram =
        kegel.settings(user.id)?.program ?: KegelProgram.forStage(user.lifeStage)

    private suspend fun requireConsent(userId: Uuid): UserRecord = access.requireUser(userId).also {
        if (!access.hasStorageConsent(userId)) throw ConsentRequiredException("store_health")
    }

    companion object {
        const val MAX_REPS = 500
        const val MAX_SECONDS = 3600
        const val DEFAULT_DAILY_GOAL = 2
        const val RECENT_DAYS = 28
        const val MAX_CHECKS = 12
        const val POSTPARTUM_WAIT_DAYS = 42

        /** Level 1 is always open; each next one after [Limits.KEGEL_DAYS_TO_UNLOCK] days at the one below. */
        fun unlocked(levels: List<KegelLevel>, sessions: List<KegelSessionRow>, program: KegelProgram): Int {
            var level = 1
            while (level < levels.size && daysAt(sessions, program, level) >= Limits.KEGEL_DAYS_TO_UNLOCK) level++
            return level
        }

        fun daysAt(sessions: List<KegelSessionRow>, program: KegelProgram, level: Int): Int =
            sessions.filter { it.program == program && it.level >= level }.map { it.doneOn }.distinct().size

        /** Days in a row ending today, or yesterday when today's session is still to come. */
        fun streak(days: List<LocalDate>, today: LocalDate): Int {
            val set = days.toSet()
            var day = if (today in set) today else today.minus(1, DateTimeUnit.DAY)
            var count = 0
            while (day in set) {
                count++
                day = day.minus(1, DateTimeUnit.DAY)
            }
            return count
        }
    }
}

/**
 * The level tables. Pregnancy keeps holds short and relaxing long — relaxing the floor is half
 * of what birth asks of it; postpartum starts lower than anyone, at the strength of a body that
 * has just carried and delivered; menopause ends with the longest holds, where the loss of
 * oestrogen has thinned what holds the bladder.
 */
object KegelPrograms {
    private fun table(vararg rows: IntArray): List<KegelLevel> = rows.mapIndexed { i, r ->
        KegelLevel(
            level = i + 1,
            holdSeconds = r[0],
            relaxSeconds = r[1],
            reps = r[2],
            sets = r[3],
            quickFlicks = r[4],
            premium = i + 1 > Limits.KEGEL_FREE_LEVELS,
        )
    }

    private val cycle = table(
        intArrayOf(3, 3, 10, 2, 0),
        intArrayOf(5, 5, 10, 2, 5),
        intArrayOf(7, 7, 10, 2, 10),
        intArrayOf(10, 10, 10, 3, 10),
        intArrayOf(10, 10, 12, 3, 15),
    )
    private val pregnancy = table(
        intArrayOf(3, 6, 8, 2, 0),
        intArrayOf(5, 8, 10, 2, 0),
        intArrayOf(6, 8, 10, 3, 5),
        intArrayOf(8, 8, 10, 3, 5),
    )
    private val postpartum = table(
        intArrayOf(2, 4, 5, 2, 0),
        intArrayOf(3, 5, 8, 2, 0),
        intArrayOf(5, 5, 10, 2, 5),
        intArrayOf(7, 7, 10, 3, 10),
        intArrayOf(10, 10, 10, 3, 10),
    )
    private val menopause = table(
        intArrayOf(3, 3, 10, 2, 0),
        intArrayOf(5, 5, 10, 2, 5),
        intArrayOf(8, 8, 10, 2, 10),
        intArrayOf(10, 10, 12, 3, 10),
        intArrayOf(10, 10, 15, 3, 15),
    )

    fun levels(program: KegelProgram): List<KegelLevel> = when (program) {
        KegelProgram.CYCLE -> cycle
        KegelProgram.PREGNANCY -> pregnancy
        KegelProgram.POSTPARTUM -> postpartum
        KegelProgram.MENOPAUSE -> menopause
    }
}
