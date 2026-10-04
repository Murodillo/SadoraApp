package uz.sadora.server.share

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import uz.sadora.contract.Epds
import uz.sadora.contract.FeedingSide
import uz.sadora.contract.HotFlushTrigger
import uz.sadora.contract.SharedContractions
import uz.sadora.contract.SharedFeeding
import uz.sadora.contract.SharedHotFlushes
import uz.sadora.contract.SharedKickCount
import uz.sadora.contract.SharedMoodScreen
import uz.sadora.contract.SharedStageRecords
import uz.sadora.contract.SharedTriggerCount
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.server.core.dayIn
import uz.sadora.server.db.enumFromDb

/**
 * Her stage events, as the few numbers a doctor reads them by. Pure, so each window and
 * each average can be pinned in a test without a database.
 */
object StageRecordSummary {

    fun of(events: List<StageEvent>, now: Instant, timezone: String): SharedStageRecords? {
        fun recent(kind: StageEventKind, window: kotlin.time.Duration) =
            events.filter { it.kind == kind && now - it.startedAt <= window }.sortedByDescending { it.startedAt }

        val records = SharedStageRecords(
            feeding = feeding(recent(StageEventKind.FEEDING, SharedStageRecords.FEEDING_DAYS.days)),
            kickCounts = recent(StageEventKind.KICK_COUNT, SharedStageRecords.KICK_DAYS.days).map {
                SharedKickCount(it.startedAt, it.value ?: 0, it.durationSeconds ?: 0)
            },
            contractions = contractions(recent(StageEventKind.CONTRACTION, SharedStageRecords.CONTRACTION_HOURS.hours)),
            hotFlushes = hotFlushes(recent(StageEventKind.HOT_FLUSH, SharedStageRecords.HOT_FLUSH_DAYS.days)),
            moodScreens = recent(StageEventKind.MOOD_SCREEN, SharedStageRecords.MOOD_SCREEN_DAYS.days).map { screen ->
                val answers = screen.detail?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
                SharedMoodScreen(screen.startedAt.dayIn(timezone), screen.value ?: 0, Epds.selfHarm(answers))
            },
        )
        return records.takeUnless { it.isEmpty }
    }

    private fun feeding(feeds: List<StageEvent>): SharedFeeding? {
        if (feeds.isEmpty()) return null
        val sides = feeds.map { enumFromDb<FeedingSide>(it.detail) }
        val breast = feeds.filterIndexed { i, _ -> sides[i]?.isBreast == true }
        val bottle = feeds.filterIndexed { i, _ -> sides[i]?.isBreast == false }
        return SharedFeeding(
            windowDays = SharedStageRecords.FEEDING_DAYS,
            feeds = feeds.size,
            breastFeeds = breast.size,
            averageBreastMinutes = breast.mapNotNull { it.durationSeconds }.takeIf { it.isNotEmpty() }
                ?.average()?.let { (it / 60).toInt() },
            bottleFeeds = bottle.size,
            bottleMl = bottle.sumOf { it.value ?: 0 },
            lastAt = feeds.first().startedAt,
        )
    }

    /** Newest first in; the gaps are between consecutive starts. */
    private fun contractions(contractions: List<StageEvent>): SharedContractions? {
        if (contractions.isEmpty()) return null
        val oldestFirst = contractions.reversed()
        val gaps = oldestFirst.zipWithNext { a, b -> (b.startedAt - a.startedAt).inWholeSeconds }
        return SharedContractions(
            windowHours = SharedStageRecords.CONTRACTION_HOURS,
            count = contractions.size,
            averageDurationSeconds = contractions.mapNotNull { it.durationSeconds }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            averageIntervalSeconds = gaps.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            lastAt = contractions.first().startedAt,
        )
    }

    private fun hotFlushes(flushes: List<StageEvent>): SharedHotFlushes? {
        if (flushes.isEmpty()) return null
        return SharedHotFlushes(
            windowDays = SharedStageRecords.HOT_FLUSH_DAYS,
            count = flushes.size,
            strong = flushes.count { (it.value ?: 0) >= 3 },
            triggers = flushes.mapNotNull { enumFromDb<HotFlushTrigger>(it.detail) }
                .groupingBy { it }.eachCount()
                .map { (trigger, count) -> SharedTriggerCount(trigger, count) }
                .sortedByDescending { it.count },
        )
    }
}
