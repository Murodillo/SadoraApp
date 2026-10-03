package uz.sadora.app.ui.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.StageEventsController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.StageToolsStrings
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.LifeStage
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.StageEventKind

// ---------------------------------------------------------------- conception

/**
 * Days her period is late, or null when it is not.
 *
 * Worked out here because the server's forecast cannot say it: with no new period
 * recorded it rolls the prediction on a whole cycle, and a period three days late read
 * as "next period in 25 days".
 */
internal fun periodLateDays(status: CycleStatus?): Int? {
    val cycle = status ?: return null
    if (cycle.currentPeriod?.isOngoing == true) return null
    val last = cycle.lastPeriodStart ?: return null
    val length = cycle.prediction.averageCycleLength ?: return null
    val late = last.plus(length, DateTimeUnit.DAY).daysUntil(cycle.today)
    // Past two months it is not "late" any more; the record has simply stopped.
    return late.takeIf { it in 1..MaxLateDays }
}

private const val MaxLateDays = 60

/** A late period: in trying to conceive, the test and the way on to pregnancy. */
@Composable
internal fun PeriodLateCard(state: AppState, late: Int, onOpen: (Route) -> Unit) {
    val t = strings.tools
    val c = Sadora.colors
    val trying = state.lifeStage == LifeStage.TryingToConceive
    SadoraCard {
        Text(t.periodLate(late), style = Sadora.type.h3, color = c.text)
        Text(if (trying) t.periodLateBody else t.periodLateCycleBody, style = Sadora.type.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (trying) {
                SadoraButton(t.pregnantButton, onClick = {
                    state.pendingStage = LifeStage.Pregnancy
                    onOpen(Route.LifeStageSettings)
                }, modifier = Modifier.weight(1f))
            }
            SadoraButton(
                t.periodStartedButton,
                onClick = { onOpen(Route.CycleDay(state.today.toString())) },
                tone = ButtonTone.Secondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Trying to conceive: where she is against her fertile window — in it, how many days to
 * it, or past it and waiting for the next period.
 */
@Composable
internal fun FertileWindowCard(status: CycleStatus) {
    val t = strings.tools
    val c = Sadora.colors
    val p = status.prediction
    val from = p.fertileFrom ?: return
    val until = p.fertileUntil ?: return
    val today = status.today
    val dates = strings.dates
    val ovulation = p.ovulationOn?.let(dates::dayMonth).orEmpty()
    SadoraCard {
        when {
            today in from..until -> {
                Text(t.fertileTodayTitle, style = Sadora.type.h3, color = c.text)
                Text(t.fertileTodayBody(ovulation), style = Sadora.type.body, color = c.muted)
            }
            today < from -> {
                Text(t.fertileInDays(today.daysUntil(from)), style = Sadora.type.h3, color = c.text)
                Text(t.fertileWindow(dates.dayMonth(from), dates.dayMonth(until), ovulation), style = Sadora.type.body, color = c.muted)
            }
            else -> {
                Text(t.fertilePassedTitle, style = Sadora.type.h3, color = c.text)
                p.nextPeriodStart?.let {
                    Text(t.fertilePassedBody(dates.dayMonth(it)), style = Sadora.type.body, color = c.muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- doctor flags

/**
 * Patterns in her own records worth raising with a doctor: cycles shorter than 21 or
 * longer than 35 days, lengths that vary by a week or more, periods longer than seven
 * days — and in menopause, any bleeding in the last year. Nothing is said until two
 * cycles have been measured.
 */
internal fun doctorFlags(stage: LifeStage, status: CycleStatus?, t: StageToolsStrings): List<String> {
    val cycle = status ?: return emptyList()
    if (stage == LifeStage.Menopause) {
        val last = cycle.lastPeriodStart ?: return emptyList()
        return if (last.daysUntil(cycle.today) <= 365) listOf(t.bleedingAfterMenopause) else emptyList()
    }
    if (!stage.predictsCycle) return emptyList()
    val p = cycle.prediction
    if (p.basedOnCycles < 2) return emptyList()
    return buildList {
        p.averageCycleLength?.let {
            if (it < 21) add(t.shortCycles(it))
            if (it > 35) add(t.longCycles(it))
        }
        p.variationDays?.takeIf { it >= 8 }?.let { add(t.irregularCycles(it)) }
        p.averagePeriodLength?.takeIf { it > 7 }?.let { add(t.longPeriods(it)) }
    }
}

@Composable
internal fun DoctorFlagsCard(flags: List<String>, onOpen: (Route) -> Unit) {
    if (flags.isEmpty()) return
    val t = strings.tools
    val c = Sadora.colors
    SadoraCard {
        CardLabel(t.flagsTitle)
        flags.forEach { Text("• $it", style = Sadora.type.body, color = c.text) }
        Text(t.flagsNote, style = Sadora.type.caption, color = c.muted)
        SadoraButton(t.askDoctor, onClick = { onOpen(Route.Doctors) }, tone = ButtonTone.Secondary)
    }
}

// ---------------------------------------------------------------- moving on

/** From week 37, or once the due date has passed: has the baby come? */
@Composable
internal fun BirthPromptCard(state: AppState, onOpen: (Route) -> Unit) {
    val t = strings.tools
    val c = Sadora.colors
    SadoraCard {
        Text(t.birthPromptTitle, style = Sadora.type.h3, color = c.text)
        Text(t.birthPromptBody, style = Sadora.type.body, color = c.muted)
        SadoraButton(t.birthPromptButton, onClick = {
            state.pendingStage = LifeStage.Postpartum
            onOpen(Route.LifeStageSettings)
        })
    }
}

/** After a birth: a period recorded since, or a year gone by. */
internal fun cycleIsBack(state: AppState, status: CycleStatus?): Boolean {
    val born: LocalDate = state.childBirthDate ?: state.babyBirthDate ?: return state.postpartumWeek >= 52
    val last = status?.lastPeriodStart
    return (last != null && last > born) || state.postpartumWeek >= 52
}

@Composable
internal fun CycleBackCard(state: AppState, onOpen: (Route) -> Unit) {
    val t = strings.tools
    val c = Sadora.colors
    SadoraCard {
        Text(t.cycleBackTitle, style = Sadora.type.h3, color = c.text)
        Text(t.cycleBackBody, style = Sadora.type.body, color = c.muted)
        SadoraButton(t.cycleBackButton, onClick = {
            state.pendingStage = LifeStage.Cycle
            onOpen(Route.LifeStageSettings)
        })
    }
}

// ---------------------------------------------------------------- tools

/** A card that opens a tool, with a line of what it last recorded. */
@Composable
private fun ToolCard(title: String, body: String, status: String?, onClick: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(onClick = onClick) {
        Text(title, style = Sadora.type.h3, color = c.text)
        Text(status ?: body, style = Sadora.type.body, color = if (status != null) c.text else c.muted)
    }
}

@Composable
internal fun FeedingCard(tools: StageEventsController, onOpen: (Route) -> Unit) {
    val t = strings.tools
    LaunchedEffect(Unit) { tools.load(StageEventKind.FEEDING, days = 2) }
    val now = rememberNow(tools.feeding != null)
    val running = tools.feeding
    val feeds = tools.of(StageEventKind.FEEDING)
    val today = feeds.count { it.startedAt.localDate() == now.localDate() }
    val status = when {
        running != null -> t.feedRunning(t.side(running.first)) + " · " + (now - running.second).minutesSeconds()
        feeds.isNotEmpty() -> t.lastFeed(agoLabel(feeds.first().startedAt, now), feeds.first().sideLabel()) + " · " + t.feedsToday(today)
        else -> null
    }
    ToolCard(t.feedingTitle, t.noFeedsToday, status) { onOpen(Route.Feeding) }
}

@Composable
internal fun MoodScreenCard(tools: StageEventsController, onOpen: (Route) -> Unit) {
    val t = strings.tools
    LaunchedEffect(Unit) { tools.load(StageEventKind.MOOD_SCREEN, days = 120) }
    val last = tools.of(StageEventKind.MOOD_SCREEN).firstOrNull()
    ToolCard(
        t.screenCardTitle,
        t.screenCardBody,
        last?.let { t.lastScreen(strings.dates.dayMonth(it.startedAt.localDate()), it.value ?: 0) },
    ) { onOpen(Route.MoodScreen) }
}

@Composable
internal fun KickCounterCard(tools: StageEventsController, onOpen: (Route) -> Unit) {
    val t = strings.tools
    LaunchedEffect(Unit) { tools.load(StageEventKind.KICK_COUNT, days = 14) }
    val last = tools.of(StageEventKind.KICK_COUNT).firstOrNull()
    ToolCard(
        t.kicksTitle,
        t.kicksCardBody,
        last?.let { t.kicksResult(it.value ?: 0, (it.durationSeconds ?: 0).seconds.minutesSeconds()) + " · " + strings.dates.dayMonth(it.startedAt.localDate()) },
    ) { onOpen(Route.KickCounter) }
}

@Composable
internal fun ContractionsCard(onOpen: (Route) -> Unit) {
    val t = strings.tools
    ToolCard(t.contractionsTitle, t.contractionsCardBody, null) { onOpen(Route.Contractions) }
}

@Composable
internal fun HotFlushCard(tools: StageEventsController, onOpen: (Route) -> Unit) {
    val t = strings.tools
    LaunchedEffect(Unit) { tools.load(StageEventKind.HOT_FLUSH, days = 30) }
    val flushes = tools.of(StageEventKind.HOT_FLUSH)
    val now = rememberNow(false)
    val status = if (flushes.isEmpty()) null else hotFlushCountsLine(flushes, now) +
        (commonTriggerOf(flushes)?.let { " · " + t.commonTrigger(t.trigger(it)) }.orEmpty())
    ToolCard(t.hotFlushTitle, t.hotFlushLog, status) { onOpen(Route.HotFlushes) }
}

/** Menopause: where bleeding is recorded, and why it matters. */
@Composable
internal fun MenopauseBleedingCard(state: AppState, onOpen: (Route) -> Unit) {
    val t = strings.tools
    val c = Sadora.colors
    SadoraCard(onClick = { onOpen(Route.CycleDay(state.today.toString())) }) {
        Text(t.menopauseBleedingTitle, style = Sadora.type.h3, color = c.text)
        Text(t.menopauseBleedingBody, style = Sadora.type.body, color = c.muted)
    }
}
