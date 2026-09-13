package uz.sadora.app.data.health

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.HealthSampleInput
import uz.sadora.contract.Limits
import uz.sadora.contract.PeriodEntry

/** What a stretch of a night was, in the terms both stores can be reduced to. */
enum class SleepStage { ASLEEP, LIGHT, DEEP, REM, AWAKE, IN_BED }

/** One stretch of sleep as a store records it, tagged with the app or device that wrote it. */
data class SleepSegment(
    val source: String,
    val start: Instant,
    val end: Instant,
    val stage: SleepStage,
)

/** The provider's own names for the five figures a night is sent as. */
data class SleepMetricNames(
    val asleep: String,
    val deep: String,
    val rem: String,
    val light: String,
    val awake: String,
)

/** A night, reduced. Seconds throughout, because both stores' mappings are in seconds. */
data class SleepNight(
    val date: LocalDate,
    val source: String,
    val wakeAt: Instant,
    val asleepSeconds: Long,
    val deepSeconds: Long,
    val remSeconds: Long,
    val lightSeconds: Long,
    val awakeSeconds: Long,
    val hasStages: Boolean,
)

/**
 * Sleep from segments to one figure per night.
 *
 * A phone on the nightstand and a watch on the wrist both write a night, and adding
 * them doubles it. So the segments are grouped per source into sessions, each session
 * belongs to the day she woke on, and for each day only the source that saw the most
 * sleep is kept — the same rule the server applies across providers, applied here
 * across the apps inside one store.
 */
object SleepNights {

    /** Two stretches further apart than this are two sleeps, not one interrupted one. */
    private val SessionGap: Duration = 2.hours

    fun from(segments: List<SleepSegment>, zone: TimeZone): List<SleepNight> {
        val sessions = segments
            .filter { it.end > it.start }
            .groupBy { it.source }
            .flatMap { (source, ofSource) -> sessionsOf(ofSource.sortedBy { it.start }).map { source to it } }

        return sessions
            .map { (source, session) -> night(source, session, zone) }
            .groupBy { it.date }
            .map { (_, candidates) ->
                // Per day, one source: the one that staged the night (a watch) over one
                // that only saw time in bed (a phone), then the one that saw more
                // sleep. Several sessions of the winner (a nap) add up.
                val winner = candidates
                    .groupBy { it.source }
                    .values
                    .maxWith(compareBy({ nights -> nights.any { it.hasStages } }, { nights -> nights.sumOf { it.asleepSeconds } }))
                winner.reduce { a, b ->
                    a.copy(
                        wakeAt = maxOf(a.wakeAt, b.wakeAt),
                        asleepSeconds = a.asleepSeconds + b.asleepSeconds,
                        deepSeconds = a.deepSeconds + b.deepSeconds,
                        remSeconds = a.remSeconds + b.remSeconds,
                        lightSeconds = a.lightSeconds + b.lightSeconds,
                        awakeSeconds = a.awakeSeconds + b.awakeSeconds,
                        hasStages = a.hasStages || b.hasStages,
                    )
                }
            }
            .filter { it.asleepSeconds > 0 }
            .sortedBy { it.date }
    }

    fun samples(nights: List<SleepNight>, provider: HealthProvider, names: SleepMetricNames): List<HealthSampleInput> =
        nights.flatMap { night ->
            fun sample(metric: String, seconds: Long) = HealthSampleInput(
                provider = provider,
                // Per day rather than per record: a re-read night replaces itself even
                // when the store has since merged or split its segments.
                externalId = "sleep:${night.date}:$metric",
                metric = metric,
                value = seconds.toDouble(),
                unit = "s",
                // The wake time, so the night lands on the morning she woke up — the
                // day she will look at it.
                startedAt = night.wakeAt,
                sourceDevice = night.source,
            )
            buildList {
                add(sample(names.asleep, night.asleepSeconds))
                if (night.hasStages) {
                    add(sample(names.deep, night.deepSeconds))
                    add(sample(names.rem, night.remSeconds))
                    add(sample(names.light, night.lightSeconds))
                    add(sample(names.awake, night.awakeSeconds))
                }
            }
        }

    private fun sessionsOf(sorted: List<SleepSegment>): List<List<SleepSegment>> {
        val sessions = mutableListOf<MutableList<SleepSegment>>()
        var lastEnd: Instant? = null
        sorted.forEach { segment ->
            val current = sessions.lastOrNull()
            if (current == null || lastEnd == null || segment.start - lastEnd!! > SessionGap) {
                sessions += mutableListOf(segment)
                lastEnd = segment.end
            } else {
                current += segment
                lastEnd = maxOf(lastEnd!!, segment.end)
            }
        }
        return sessions
    }

    private fun night(source: String, session: List<SleepSegment>, zone: TimeZone): SleepNight {
        fun seconds(vararg stages: SleepStage) =
            session.filter { it.stage in stages }.sumOf { (it.end - it.start).inWholeSeconds }

        val deep = seconds(SleepStage.DEEP)
        val rem = seconds(SleepStage.REM)
        val light = seconds(SleepStage.LIGHT)
        val hasStages = deep + rem + light > 0
        val wakeAt = session.maxOf { it.end }
        return SleepNight(
            date = wakeAt.toLocalDateTime(zone).date,
            source = source,
            wakeAt = wakeAt,
            asleepSeconds = deep + rem + light + seconds(SleepStage.ASLEEP),
            deepSeconds = deep,
            remSeconds = rem,
            lightSeconds = light,
            awakeSeconds = seconds(SleepStage.AWAKE),
            hasStages = hasStages,
        )
    }
}

/** A period found in the store, as the days it spans. */
data class PeriodSpan(val start: LocalDate, val endInclusive: LocalDate)

/**
 * Bleeding days from the store, turned into periods the cycle can use.
 *
 * The server does not refuse overlapping periods, so this is where a period she already
 * logged in SADORA — or one imported last week — is recognised and left alone. Hers
 * always wins: nothing here edits or deletes a period, it only adds the ones missing.
 */
object PeriodImport {

    /** One clear day inside a period is still the same period; two are a new one. */
    private const val MaxGapDays = 1

    /** Days either side of an existing period that still count as that period. */
    private const val OverlapMarginDays = 2

    fun spans(flowDays: Set<LocalDate>): List<PeriodSpan> {
        if (flowDays.isEmpty()) return emptyList()
        val spans = mutableListOf<PeriodSpan>()
        val sorted = flowDays.sorted()
        var start = sorted.first()
        var end = start
        sorted.drop(1).forEach { day ->
            if (end.daysUntil(day) <= MaxGapDays + 1) {
                end = day
            } else {
                spans += PeriodSpan(start, end)
                start = day
                end = day
            }
        }
        spans += PeriodSpan(start, end)
        // A run longer than a period can be is not one period: it is logged spotting, or
        // two periods close together. The server would refuse it whole, so it is cut.
        return spans.map { span ->
            val longest = span.start.plus(Limits.PERIOD_LENGTH_MAX - 1, DateTimeUnit.DAY)
            if (span.endInclusive > longest) span.copy(endInclusive = longest) else span
        }
    }

    /** The spans to log: those touching no existing period, her own or an earlier import. */
    fun missing(spans: List<PeriodSpan>, existing: List<PeriodEntry>, today: LocalDate): List<PeriodSpan> {
        val taken = existing.map { entry ->
            entry.startedOn.minus(OverlapMarginDays, DateTimeUnit.DAY)..(entry.endedOn ?: today)
                .plus(OverlapMarginDays, DateTimeUnit.DAY)
        }
        return spans
            .filter { it.start <= today }
            .filter { span -> taken.none { range -> span.start <= range.endInclusive && span.endInclusive >= range.start } }
    }

    /**
     * Whether [span] should go up without an end date.
     *
     * A span that reaches today or yesterday may not have finished, and ending it on the
     * last recorded day would close a period that is still going. Only one period can be
     * open, so not when she already has one.
     */
    fun isOngoing(span: PeriodSpan, existing: List<PeriodEntry>, today: LocalDate): Boolean =
        span.endInclusive >= today.minus(1, DateTimeUnit.DAY) && existing.none { it.isOngoing }
}
