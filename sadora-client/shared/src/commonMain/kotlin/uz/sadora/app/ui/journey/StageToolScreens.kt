package uz.sadora.app.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.StageEventsController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.contract.Epds
import uz.sadora.contract.FeedingSide
import uz.sadora.contract.HotFlushTrigger
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind

// ---------------------------------------------------------------- shared

/** The current instant, ticking each second while [running]. */
@Composable
internal fun rememberNow(running: Boolean): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(running) {
        now = Clock.System.now()
        while (running) {
            delay(1_000)
            now = Clock.System.now()
        }
    }
    return now
}

@Composable
internal fun Duration.minutesSeconds(): String {
    val total = inWholeSeconds.coerceAtLeast(0)
    return strings.tools.minutesSeconds((total / 60).toInt(), (total % 60).toInt())
}

/** "14:05" on her clock. */
internal fun Instant.clock(): String =
    Fmt.clock(toLocalDateTime(TimeZone.currentSystemDefault()).time)

internal fun Instant.localDate() = toLocalDateTime(TimeZone.currentSystemDefault()).date

/** "1 soat 20 daq" since [then]. */
@Composable
internal fun agoLabel(then: Instant, now: Instant): String {
    val minutes = (now - then).inWholeMinutes.coerceAtLeast(0)
    return strings.tools.ago((minutes / 60).toInt(), (minutes % 60).toInt())
}

/** A big running figure: the timer of a feed, a contraction, a kick count. */
@Composable
private fun BigFigure(text: String, caption: String? = null) {
    val c = Sadora.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = Sadora.type.data.copy(fontSize = 48.sp, lineHeight = 52.sp), color = c.text)
        caption?.let { Text(it, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center) }
    }
}

/** One recorded event: what, when, and a way to take it back. */
@Composable
private fun EventLine(title: String, detail: String, onDelete: () -> Unit) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            Text(detail, style = Sadora.type.caption, color = c.muted)
        }
        Text(
            "✕",
            style = Sadora.type.body,
            color = c.muted,
            modifier = Modifier.clip(Radius.chip).noRippleClickable(onClick = onDelete).padding(Spacing.xs),
        )
    }
}

/** A warning in the danger colour — the screens' few escalations. */
@Composable
private fun Warning(text: String) {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.danger.copy(alpha = 0.12f))
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("⚠", style = Sadora.type.h3, color = c.danger)
        Text(text, style = Sadora.type.body, color = c.danger)
    }
}

// ---------------------------------------------------------------- feeding

/**
 * The feeding log. A breast feed is timed — she taps the side she starts on and stops
 * it when done; a bottle is a number of millilitres.
 */
@Composable
fun FeedingScreen(tools: StageEventsController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val t = strings.tools
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { tools.load(StageEventKind.FEEDING, days = 2) }

    val running = tools.feeding
    val now = rememberNow(running != null)
    val today = now.localDate()
    val feeds = tools.of(StageEventKind.FEEDING)
    val todays = feeds.filter { it.startedAt.localDate() == today }
    var bottleSide by remember { mutableStateOf(FeedingSide.BOTTLE) }
    var ml by remember { mutableStateOf(90) }

    Column(modifier) {
        SadoraTopBar(t.feedingTitle, onBack = onClose)
        ScreenContent {
            item {
                SadoraCard {
                    if (running != null) {
                        BigFigure((now - running.second).minutesSeconds(), t.feedRunning(t.side(running.first)))
                        SadoraButton(t.stopFeed, onClick = {
                            val (side, started) = running
                            tools.feeding = null
                            scope.launch {
                                tools.log(
                                    LogStageEventRequest(
                                        kind = StageEventKind.FEEDING,
                                        startedAt = started,
                                        durationSeconds = (Clock.System.now() - started).inWholeSeconds.toInt().coerceAtLeast(1),
                                        detail = side.wire(),
                                    ),
                                )
                            }
                        })
                    } else {
                        Text(t.feedingIntro, style = Sadora.type.body, color = c.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            listOf(FeedingSide.LEFT, FeedingSide.RIGHT).forEach { side ->
                                SadoraButton(
                                    t.side(side),
                                    onClick = { tools.feeding = side to Clock.System.now() },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.bottleTitle)
                    ChipFlowRow {
                        listOf(FeedingSide.BOTTLE, FeedingSide.PUMP).forEach { side ->
                            SelectChip(t.side(side), selected = bottleSide == side, onClick = { bottleSide = side })
                        }
                    }
                    ChipFlowRow {
                        listOf(30, 60, 90, 120, 150, 180).forEach { amount ->
                            SelectChip("$amount ${t.millilitres}", selected = ml == amount, onClick = { ml = amount })
                        }
                    }
                    SadoraButton(
                        t.saveBottle,
                        tone = ButtonTone.Secondary,
                        enabled = !tools.busy,
                        onClick = {
                            scope.launch {
                                tools.log(
                                    LogStageEventRequest(
                                        kind = StageEventKind.FEEDING,
                                        startedAt = Clock.System.now(),
                                        value = ml,
                                        detail = bottleSide.wire(),
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            tools.error?.let { item { ErrorStrip(it.readable()) } }

            item {
                SadoraCard {
                    CardLabel(t.feedsToday(todays.size))
                    if (todays.isEmpty()) Text(t.nothingToday, style = Sadora.type.body, color = c.muted)
                    todays.forEach { feed ->
                        EventLine(
                            title = "${feed.startedAt.clock()} · ${feed.sideLabel()}",
                            detail = feed.value?.let { "$it ${t.millilitres}" }
                                ?: feed.durationSeconds?.seconds?.minutesSeconds().orEmpty(),
                            onDelete = { scope.launch { tools.delete(feed) } },
                        )
                    }
                }
            }
        }
    }
}

private fun FeedingSide.wire(): String = name.lowercase()

@Composable
internal fun StageEvent.sideLabel(): String =
    FeedingSide.entries.firstOrNull { it.name.equals(detail, ignoreCase = true) }?.let { strings.tools.side(it) }.orEmpty()

// ---------------------------------------------------------------- kick counter

/**
 * "Count to ten": the time it takes to feel ten movements, once a day from week 28.
 * Ten not reached within two hours is the one result that says to call someone.
 */
@Composable
fun KickCounterScreen(tools: StageEventsController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val t = strings.tools
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { tools.load(StageEventKind.KICK_COUNT, days = 14) }

    val started = tools.kicksStartedAt
    val now = rememberNow(started != null)
    val elapsed = started?.let { now - it } ?: Duration.ZERO
    val slow = started != null && elapsed >= SlowKicks && tools.kicks < KickGoal

    Column(modifier) {
        SadoraTopBar(t.kicksTitle, onBack = onClose)
        ScreenContent {
            item {
                SadoraCard {
                    Text(t.kicksIntro, style = Sadora.type.body, color = c.muted)
                    BigFigure(t.kicksCount(tools.kicks, KickGoal), started?.let { elapsed.minutesSeconds() })
                    SadoraButton(t.kickTap, onClick = {
                        if (tools.kicksStartedAt == null) tools.kicksStartedAt = Clock.System.now()
                        tools.kicks++
                    })
                    if (started != null && tools.kicks > 0) {
                        SadoraButton(t.kicksFinish, tone = ButtonTone.Secondary, enabled = !tools.busy, onClick = {
                            val count = tools.kicks
                            val seconds = (Clock.System.now() - started).inWholeSeconds.toInt().coerceAtLeast(1)
                            scope.launch {
                                val saved = tools.log(
                                    LogStageEventRequest(StageEventKind.KICK_COUNT, started, durationSeconds = seconds, value = count),
                                )
                                if (saved != null) {
                                    tools.kicksStartedAt = null
                                    tools.kicks = 0
                                }
                            }
                        })
                    }
                    if (slow) Warning(t.kicksSlow)
                }
            }

            tools.error?.let { item { ErrorStrip(it.readable()) } }

            val previous = tools.of(StageEventKind.KICK_COUNT)
            if (previous.isNotEmpty()) item {
                SadoraCard {
                    CardLabel(t.previousCounts)
                    previous.forEach { count ->
                        EventLine(
                            title = t.kicksResult(count.value ?: 0, (count.durationSeconds ?: 0).seconds.minutesSeconds()),
                            detail = "${strings.dates.dayMonth(count.startedAt.localDate())} · ${count.startedAt.clock()}",
                            onDelete = { scope.launch { tools.delete(count) } },
                        )
                    }
                }
            }
        }
    }
}

private const val KickGoal = 10
private val SlowKicks = 2.hours

// ---------------------------------------------------------------- contractions

/**
 * Times each contraction and the gap since the one before, and says when the pattern is
 * the one to leave for the maternity unit: closer than five minutes, longer than one,
 * for an hour.
 */
@Composable
fun ContractionTimerScreen(tools: StageEventsController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val t = strings.tools
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { tools.load(StageEventKind.CONTRACTION, days = 1) }

    val started = tools.contractionStartedAt
    // Ticks while a contraction runs; otherwise it moves on as contractions are added.
    val now = rememberNow(started != null)
    // Oldest first, for the gaps between them.
    val all = tools.of(StageEventKind.CONTRACTION).sortedBy { it.startedAt }
    val lastHour = all.filter { now - it.startedAt <= 1.hours }
    val summary = contractionSummary(lastHour)

    Column(modifier) {
        SadoraTopBar(t.contractionsTitle, onBack = onClose)
        ScreenContent {
            item {
                SadoraCard {
                    Text(t.contractionsIntro, style = Sadora.type.body, color = c.muted)
                    if (started != null) {
                        BigFigure((now - started).minutesSeconds())
                        SadoraButton(t.contractionStop, onClick = {
                            tools.contractionStartedAt = null
                            val seconds = (Clock.System.now() - started).inWholeSeconds.toInt().coerceIn(1, MaxContractionSeconds)
                            scope.launch {
                                tools.log(LogStageEventRequest(StageEventKind.CONTRACTION, started, durationSeconds = seconds))
                            }
                        })
                    } else {
                        SadoraButton(t.contractionStart, onClick = { tools.contractionStartedAt = Clock.System.now() })
                    }
                }
            }

            if (summary != null) item {
                SadoraCard {
                    Text(
                        t.contractionsSummary(lastHour.size, summary.averageDuration.minutesSeconds(), summary.averageGap.minutesSeconds()),
                        style = Sadora.type.body,
                        color = c.text,
                    )
                    if (summary.timeToGo) Warning(t.contractionsGo)
                }
            }

            item { Warning(t.contractionsUrgent) }
            tools.error?.let { item { ErrorStrip(it.readable()) } }

            if (all.isNotEmpty()) item {
                SadoraCard {
                    all.reversed().take(12).forEach { contraction ->
                        val index = all.indexOf(contraction)
                        val gap = all.getOrNull(index - 1)?.let { contraction.startedAt - it.startedAt }
                        EventLine(
                            title = "${contraction.startedAt.clock()} · ${t.contractionLasted((contraction.durationSeconds ?: 0).seconds.minutesSeconds())}",
                            detail = gap?.let { t.contractionApart(it.minutesSeconds()) }.orEmpty(),
                            onDelete = { scope.launch { tools.delete(contraction) } },
                        )
                    }
                }
            }
        }
    }
}

internal data class ContractionSummary(val averageDuration: Duration, val averageGap: Duration, val timeToGo: Boolean)

/**
 * The last hour's contractions, oldest first, as averages — and whether they meet the
 * 5-1-1 pattern: at most five minutes apart, at least a minute long, kept up for an hour.
 */
internal fun contractionSummary(lastHour: List<StageEvent>): ContractionSummary? {
    if (lastHour.size < 2) return null
    val durations = lastHour.mapNotNull { it.durationSeconds }
    val gaps = lastHour.zipWithNext { a, b -> (b.startedAt - a.startedAt).inWholeSeconds }
    val duration = durations.average().seconds
    val gap = gaps.average().seconds
    val span = lastHour.last().startedAt - lastHour.first().startedAt
    val timeToGo = gap <= 5.minutes && duration >= 1.minutes && span >= SustainedFor
    return ContractionSummary(duration, gap, timeToGo)
}

// An hour of them, give or take the first one: the earliest in the hour started up to
// ten minutes after the hour began.
private val SustainedFor = 50.minutes
private const val MaxContractionSeconds = 600

// ---------------------------------------------------------------- hot flushes

/** A tap to log a hot flush, how strong, and what might have set it off. */
@Composable
fun HotFlushScreen(tools: StageEventsController, onClose: () -> Unit, onToast: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = strings.tools
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { tools.load(StageEventKind.HOT_FLUSH, days = 30) }

    var intensity by remember { mutableStateOf(2) }
    var trigger by remember { mutableStateOf<HotFlushTrigger?>(null) }
    val now = rememberNow(false)
    val flushes = tools.of(StageEventKind.HOT_FLUSH)

    Column(modifier) {
        SadoraTopBar(t.hotFlushTitle, onBack = onClose)
        ScreenContent {
            item {
                SadoraCard {
                    Text(hotFlushCountsLine(flushes, now), style = Sadora.type.h3, color = c.text)
                    commonTriggerOf(flushes)?.let { Text(t.commonTrigger(t.trigger(it)), style = Sadora.type.body, color = c.muted) }
                }
            }
            item {
                SadoraCard {
                    ChipFlowRow {
                        (1..3).forEach { level ->
                            SelectChip(t.intensity(level), selected = intensity == level, onClick = { intensity = level })
                        }
                    }
                    CardLabel(t.triggerQuestion)
                    ChipFlowRow {
                        HotFlushTrigger.entries.forEach { option ->
                            SelectChip(
                                t.trigger(option),
                                selected = trigger == option,
                                onClick = { trigger = if (trigger == option) null else option },
                            )
                        }
                    }
                    SadoraButton(t.hotFlushLog, enabled = !tools.busy, onClick = {
                        scope.launch {
                            val saved = tools.log(
                                LogStageEventRequest(
                                    StageEventKind.HOT_FLUSH,
                                    Clock.System.now(),
                                    value = intensity,
                                    detail = trigger?.name?.lowercase(),
                                ),
                            )
                            if (saved != null) {
                                trigger = null
                                onToast(t.hotFlushSaved)
                            }
                        }
                    })
                }
            }
            tools.error?.let { item { ErrorStrip(it.readable()) } }
            if (flushes.isNotEmpty()) item {
                SadoraCard {
                    flushes.take(20).forEach { flush ->
                        val cause = flush.trigger()?.let { " · ${t.trigger(it)}" }.orEmpty()
                        EventLine(
                            title = t.intensity(flush.value ?: 2) + cause,
                            detail = "${strings.dates.dayMonth(flush.startedAt.localDate())} · ${flush.startedAt.clock()}",
                            onDelete = { scope.launch { tools.delete(flush) } },
                        )
                    }
                }
            }
        }
    }
}

internal fun StageEvent.trigger(): HotFlushTrigger? =
    HotFlushTrigger.entries.firstOrNull { it.name.equals(detail, ignoreCase = true) }

@Composable
internal fun hotFlushCountsLine(flushes: List<StageEvent>, now: Instant): String {
    val today = now.localDate()
    val week = flushes.count { now - it.startedAt <= 7.days }
    return strings.tools.hotFlushCounts(flushes.count { it.startedAt.localDate() == today }, week)
}

/** The trigger named most often in the last month, once it has been named at least twice. */
internal fun commonTriggerOf(flushes: List<StageEvent>): HotFlushTrigger? =
    flushes.mapNotNull { it.trigger() }.groupingBy { it }.eachCount()
        .filterValues { it >= 2 }.maxByOrNull { it.value }?.key

// ---------------------------------------------------------------- mood questionnaire

/**
 * The Edinburgh Postnatal Depression Scale. Ten questions about the past week; the
 * answer goes to the server, which scores it, and the result says what to do next.
 * Any answer but "never" to the last question is acted on whatever the total.
 */
@Composable
fun MoodScreenScreen(
    tools: StageEventsController,
    onClose: () -> Unit,
    onAskDoctor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tools
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val answers = remember { mutableStateListOf<Int?>().apply { repeat(Epds.ITEMS) { add(null) } } }
    var result by remember { mutableStateOf<StageEvent?>(null) }

    Column(modifier) {
        SadoraTopBar(t.screenTitle, onBack = onClose)
        ScreenContent {
            val done = result
            if (done != null) {
                item { EpdsResult(done, onAskDoctor) }
                item { SadoraButton(strings.common.done, onClick = onClose, tone = ButtonTone.Secondary) }
            } else {
                item { Text(t.screenIntro, style = Sadora.type.body, color = c.muted) }
                items(Epds.ITEMS) { item ->
                    SadoraCard {
                        Text("${item + 1}. ${t.questions[item]}", style = Sadora.type.h3, color = c.text)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            t.options[item].forEachIndexed { index, option ->
                                SelectChip(
                                    option,
                                    selected = answers[item] == index,
                                    onClick = { answers[item] = index },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                tools.error?.let { item { ErrorStrip(it.readable()) } }
                item {
                    val answered = answers.count { it != null }
                    SadoraButton(
                        if (answered < Epds.ITEMS) t.answeredOf(answered, Epds.ITEMS) else t.screenSubmit,
                        enabled = answered == Epds.ITEMS && !tools.busy,
                        onClick = {
                            scope.launch {
                                result = tools.log(
                                    LogStageEventRequest(
                                        StageEventKind.MOOD_SCREEN,
                                        Clock.System.now(),
                                        answers = answers.map { it ?: 0 },
                                    ),
                                )
                            }
                        },
                    )
                }
                item { DisclaimerNote(t.screenSource) }
            }
        }
    }
}

/** What a score means, and the self-harm answer above everything else. */
@Composable
internal fun EpdsResult(screen: StageEvent, onAskDoctor: () -> Unit) {
    val t = strings.tools
    val c = Sadora.colors
    val score = screen.value ?: 0
    val answers = screen.detail?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
    SadoraCard {
        Text(t.screenScore(score), style = Sadora.type.h2, color = c.text)
        if (Epds.selfHarm(answers)) Warning(t.screenSelfHarm)
        when {
            score >= Epds.LIKELY -> Warning(t.screenLikely)
            score >= Epds.POSSIBLE -> Text(t.screenPossible, style = Sadora.type.body, color = c.text)
            else -> Text(t.screenLow, style = Sadora.type.body, color = c.muted)
        }
        if (score >= Epds.POSSIBLE || Epds.selfHarm(answers)) {
            SadoraButton(t.askDoctor, onClick = onAskDoctor)
        }
        Text(t.screenSource, style = Sadora.type.caption, color = c.muted2)
    }
}
