package uz.sadora.server.ai

import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AiUsageLog
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue

/** One answer's cost. Deliberately carries no question and no answer. */
data class AiUsageEntry(
    val userId: Uuid?,
    val source: AiSource,
    val model: String?,
    val feature: String = "ai_chat",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val costMicros: Long = 0,
    val latencyMs: Int? = null,
    val outcome: String = "ok",
    val errorCode: String? = null,
)

/** A day of spend, as the admin panel's chart draws it. */
@Serializable
data class AiUsageDay(
    val date: String,
    val calls: Int,
    val modelCalls: Int,
    val fallbacks: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val costMicros: Long,
)

@Serializable
data class AiUsageReport(
    val days: Int,
    val calls: Int,
    val modelCalls: Int,
    val fallbacks: Int,
    val ruleCalls: Int,
    val costMicros: Long,
    val promptTokens: Long,
    val completionTokens: Long,
    /** Milliseconds, over the model calls that succeeded. Null when there were none. */
    val averageLatencyMs: Int?,
    val perDay: List<AiUsageDay>,
    /** Why the model could not answer, most common first. Codes, not messages. */
    val failures: List<AiFailureCount>,
    /** Whether a model is wired up at all, so the page can say so instead of showing zeros. */
    val modelConfigured: Boolean,
    val modelEnabled: Boolean,
    val model: String?,
)

@Serializable
data class AiFailureCount(val code: String, val count: Int)

/**
 * Where a call's cost goes.
 *
 * An interface so the gateway's rules — which of the three sources answered, and what is
 * recorded when the model fails — can be tested without a database, since those rules are
 * the part that decides what an operator sees.
 */
fun interface AiUsageRecorder {
    suspend fun record(entry: AiUsageEntry)
}

class AiUsageRepository : AiUsageRecorder {

    override suspend fun record(entry: AiUsageEntry): Unit = dbQuery {
        AiUsageLog.insert {
            it[id] = Uuid.random()
            it[userId] = entry.userId
            it[feature] = entry.feature
            it[answerSource] = entry.source.dbValue()
            it[model] = entry.model
            it[promptTokens] = entry.promptTokens
            it[completionTokens] = entry.completionTokens
            it[costMicros] = entry.costMicros
            it[latencyMs] = entry.latencyMs
            it[outcome] = entry.outcome
            it[errorCode] = entry.errorCode
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    /**
     * The window, grouped by the operator's own calendar day.
     *
     * Tashkent rather than UTC, for the same reason usage limits reset there: an operator
     * reading "bugun" means their today, and a UTC boundary would move five hours of
     * spend into yesterday.
     */
    suspend fun report(days: Int): AiUsageReport = dbQuery {
        val since = (now() - days.days).toOffsetDateTime()
        val rows = AiUsageLog
            .selectAll()
            .where { AiUsageLog.createdAt greaterEq since }
            .orderBy(AiUsageLog.createdAt to SortOrder.ASC)
            .map { row ->
                Row(
                    day = row[AiUsageLog.createdAt].toKotlinInstant().dayIn(DEFAULT_TIMEZONE),
                    source = row[AiUsageLog.answerSource],
                    promptTokens = row[AiUsageLog.promptTokens]?.toLong() ?: 0,
                    completionTokens = row[AiUsageLog.completionTokens]?.toLong() ?: 0,
                    costMicros = row[AiUsageLog.costMicros],
                    latencyMs = row[AiUsageLog.latencyMs],
                    outcome = row[AiUsageLog.outcome],
                    errorCode = row[AiUsageLog.errorCode],
                )
            }

        val perDay = rows
            .groupBy { it.day }
            .map { (day, dayRows) -> dayRows.toDay(day) }
            .sortedBy { it.date }

        val modelRows = rows.filter { it.source == AiSource.MODEL.dbValue() }
        AiUsageReport(
            days = days,
            calls = rows.size,
            modelCalls = modelRows.size,
            fallbacks = rows.count { it.source == AiSource.FALLBACK.dbValue() },
            ruleCalls = rows.count { it.source == AiSource.RULES.dbValue() },
            costMicros = rows.sumOf { it.costMicros },
            promptTokens = rows.sumOf { it.promptTokens },
            completionTokens = rows.sumOf { it.completionTokens },
            averageLatencyMs = modelRows.mapNotNull { it.latencyMs }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toInt(),
            perDay = perDay,
            failures = rows
                .filter { it.outcome != "ok" }
                .mapNotNull { it.errorCode }
                .groupingBy { it }
                .eachCount()
                .map { (code, count) -> AiFailureCount(code, count) }
                .sortedByDescending { it.count },
            // Filled in by the service, which knows the configuration.
            modelConfigured = false,
            modelEnabled = false,
            model = null,
        )
    }

    private data class Row(
        val day: LocalDate,
        val source: String,
        val promptTokens: Long,
        val completionTokens: Long,
        val costMicros: Long,
        val latencyMs: Int?,
        val outcome: String,
        val errorCode: String?,
    )

    private fun List<Row>.toDay(day: LocalDate) = AiUsageDay(
        date = day.toString(),
        calls = size,
        modelCalls = count { it.source == AiSource.MODEL.dbValue() },
        fallbacks = count { it.source == AiSource.FALLBACK.dbValue() },
        promptTokens = sumOf { it.promptTokens },
        completionTokens = sumOf { it.completionTokens },
        costMicros = sumOf { it.costMicros },
    )
}
