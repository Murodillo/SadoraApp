package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.JournalEntry
import uz.sadora.contract.MindPractice
import uz.sadora.contract.MindPracticeKind
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.JournalEntries
import uz.sadora.server.db.MindPractices
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

/**
 * The journal and the practice log.
 *
 * The journal is the most private thing in the product — the app promises "Faqat siz
 * ko'rasiz" — so every query here is scoped to one user and there is no method that
 * reads across accounts, aggregates bodies, or returns text to anything but its owner.
 */
class MindRepository {

    suspend fun entriesBetween(userId: Uuid, from: LocalDate, to: LocalDate): List<JournalEntry> =
        dbQuery {
            JournalEntries.selectAll()
                .where {
                    (JournalEntries.userId eq userId) and
                        (JournalEntries.entryDate greaterEq from) and
                        (JournalEntries.entryDate lessEq to)
                }
                .orderBy(JournalEntries.entryDate to SortOrder.DESC, JournalEntries.createdAt to SortOrder.DESC)
                .map { it.toEntry() }
        }

    suspend fun recentEntries(userId: Uuid, limit: Int): List<JournalEntry> = dbQuery {
        JournalEntries.selectAll()
            .where { JournalEntries.userId eq userId }
            .orderBy(JournalEntries.entryDate to SortOrder.DESC, JournalEntries.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toEntry() }
    }

    suspend fun entryById(userId: Uuid, id: Uuid): JournalEntry? = dbQuery {
        JournalEntries.selectAll()
            .where { (JournalEntries.id eq id) and (JournalEntries.userId eq userId) }
            .singleOrNull()
            ?.toEntry()
    }

    suspend fun addEntry(userId: Uuid, date: LocalDate, body: String): Uuid = dbQuery {
        val id = Uuid.random()
        val timestamp = now().toOffsetDateTime()
        JournalEntries.insert {
            it[JournalEntries.id] = id
            it[JournalEntries.userId] = userId
            it[entryDate] = date
            it[JournalEntries.body] = body
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
        }
        id
    }

    suspend fun updateEntry(userId: Uuid, id: Uuid, body: String): Boolean = dbQuery {
        JournalEntries.update({ (JournalEntries.id eq id) and (JournalEntries.userId eq userId) }) {
            it[JournalEntries.body] = body
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    suspend fun deleteEntry(userId: Uuid, id: Uuid): Boolean = dbQuery {
        JournalEntries.deleteWhere {
            (JournalEntries.id eq id) and (JournalEntries.userId eq userId)
        } > 0
    }

    suspend fun addPractice(userId: Uuid, kind: MindPracticeKind, durationSeconds: Int): MindPractice =
        dbQuery {
            val id = Uuid.random()
            val completed = now()
            MindPractices.insert {
                it[MindPractices.id] = id
                it[MindPractices.userId] = userId
                it[MindPractices.kind] = kind.dbValue()
                it[MindPractices.durationSeconds] = durationSeconds
                it[completedAt] = completed.toOffsetDateTime()
            }
            MindPractice(id.toString(), kind, durationSeconds, completed)
        }

    suspend fun recentPractices(userId: Uuid, limit: Int): List<MindPractice> = dbQuery {
        MindPractices.selectAll()
            .where { MindPractices.userId eq userId }
            .orderBy(MindPractices.completedAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                MindPractice(
                    id = row[MindPractices.id].toString(),
                    kind = enumFromDb(row[MindPractices.kind], MindPracticeKind.BREATHING),
                    durationSeconds = row[MindPractices.durationSeconds],
                    completedAt = row[MindPractices.completedAt].toKotlinInstant(),
                )
            }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toEntry() = JournalEntry(
        id = this[JournalEntries.id].toString(),
        date = this[JournalEntries.entryDate],
        body = this[JournalEntries.body],
        createdAt = this[JournalEntries.createdAt].toKotlinInstant(),
        updatedAt = this[JournalEntries.updatedAt].toKotlinInstant(),
    )
}
