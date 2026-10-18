package uz.sadora.server.kegel

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.KegelCheck
import uz.sadora.contract.KegelProgram
import uz.sadora.contract.LeakSituation
import uz.sadora.contract.SaveKegelCheckRequest
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.KegelChecks
import uz.sadora.server.db.KegelSessions
import uz.sadora.server.db.KegelSettings
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/** A session as the counting needs it: which program and level, on which day. */
data class KegelSessionRow(val program: KegelProgram, val level: Int, val doneOn: LocalDate)

data class KegelSettingsRow(val userId: Uuid, val program: KegelProgram?, val reminders: List<LocalTime>)

/** Her Kegel sessions, settings and checks, scoped to her in every query. */
class KegelRepository {

    suspend fun sessions(userId: Uuid): List<KegelSessionRow> = dbQuery {
        KegelSessions.selectAll()
            .where { KegelSessions.userId eq userId }
            .orderBy(KegelSessions.doneOn to SortOrder.ASC)
            .mapNotNull { row ->
                val program = enumFromDb<KegelProgram>(row[KegelSessions.program]) ?: return@mapNotNull null
                KegelSessionRow(program, row[KegelSessions.level], row[KegelSessions.doneOn])
            }
    }

    suspend fun sessionsBetween(userId: Uuid, from: LocalDate, to: LocalDate): List<KegelSessionRow> =
        sessions(userId).filter { it.doneOn in from..to }

    suspend fun countOn(userId: Uuid, day: LocalDate): Int = dbQuery {
        KegelSessions.selectAll()
            .where { (KegelSessions.userId eq userId) and (KegelSessions.doneOn eq day) }
            .count()
            .toInt()
    }

    suspend fun addSession(userId: Uuid, program: KegelProgram, level: Int, day: LocalDate, reps: Int, seconds: Int) = dbQuery {
        KegelSessions.insert {
            it[id] = Uuid.random()
            it[KegelSessions.userId] = userId
            it[KegelSessions.program] = program.dbValue()
            it[KegelSessions.level] = level
            it[doneOn] = day
            it[KegelSessions.reps] = reps
            it[durationSeconds] = seconds
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    suspend fun settings(userId: Uuid): KegelSettingsRow? = dbQuery {
        KegelSettings.selectAll()
            .where { KegelSettings.userId eq userId }
            .singleOrNull()
            ?.toSettings()
    }

    /** Everyone with at least one reminder, for the reminder job. */
    suspend fun withReminders(): List<KegelSettingsRow> = dbQuery {
        KegelSettings.selectAll()
            .where { KegelSettings.reminders neq "" }
            .map { it.toSettings() }
    }

    suspend fun saveSettings(userId: Uuid, program: KegelProgram?, reminders: List<LocalTime>) = dbQuery {
        KegelSettings.upsert {
            it[KegelSettings.userId] = userId
            it[KegelSettings.program] = program?.dbValue()
            it[KegelSettings.reminders] = reminders.joinToString(",") { time -> wire(time) }
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun checks(userId: Uuid, from: LocalDate? = null, to: LocalDate? = null): List<KegelCheck> = dbQuery {
        KegelChecks.selectAll()
            .where {
                var condition = KegelChecks.userId eq userId
                if (from != null) condition = condition and (KegelChecks.takenOn greaterEq from)
                if (to != null) condition = condition and (KegelChecks.takenOn lessEq to)
                condition
            }
            .orderBy(KegelChecks.takenOn to SortOrder.DESC)
            .map { row ->
                KegelCheck(
                    takenOn = row[KegelChecks.takenOn],
                    frequency = row[KegelChecks.frequency],
                    amount = row[KegelChecks.amount],
                    situations = row[KegelChecks.situations].split(',').filter { it.isNotBlank() }
                        .mapNotNull { enumFromDb<LeakSituation>(it) },
                    bother = row[KegelChecks.bother],
                )
            }
    }

    suspend fun saveCheck(userId: Uuid, day: LocalDate, request: SaveKegelCheckRequest) = dbQuery {
        KegelChecks.upsert(onUpdateExclude = listOf(KegelChecks.createdAt)) {
            it[KegelChecks.userId] = userId
            it[takenOn] = day
            it[frequency] = request.frequency
            it[amount] = request.amount
            it[situations] = request.situations.distinct().joinToString(",") { s -> s.dbValue() }
            it[bother] = request.bother
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toSettings() = KegelSettingsRow(
        userId = this[KegelSettings.userId],
        program = enumFromDb<KegelProgram>(this[KegelSettings.program]),
        reminders = this[KegelSettings.reminders].split(',').mapNotNull { parse(it) }.sorted(),
    )

    private fun wire(time: LocalTime) = "%02d:%02d".format(time.hour, time.minute)

    private fun parse(text: String): LocalTime? =
        runCatching { text.trim().takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) } }.getOrNull()
}
