package uz.sadora.app.ui.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import uz.sadora.app.data.ApiFailure
import uz.sadora.app.data.PartnerController
import uz.sadora.app.data.readable
import uz.sadora.app.data.toAppMood
import uz.sadora.app.design.PhaseColors
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.forWeek
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.CyclePhase
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.PregnancyWeeks
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ArtIcon
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.contract.LifeStage
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PartnerView
import uz.sadora.contract.CyclePhase as WirePhase
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import org.jetbrains.compose.resources.DrawableResource

/**
 * One person this account follows, as a pushed screen — for an account that also tracks
 * herself. The follower-only app draws the same body without the back chevron.
 */
@Composable
fun PartnerViewScreen(
    linkId: String,
    partner: PartnerController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = partner.views[linkId]
    Column(modifier.fillMaxSize()) {
        SadoraTopBar(view?.name.orEmpty(), onBack = onClose)
        PartnerViewBody(linkId, partner, onLeft = { onToast(it); onClose() }, onToast = onToast)
    }
}

/**
 * What a follower sees: today in plain words, what would help, and the dates ahead.
 *
 * Every section is drawn only when the server sent it — a part she did not tick is not
 * in the answer — so the layout is whatever she chose to show, in a fixed order.
 */
@Composable
fun PartnerViewBody(
    linkId: String,
    partner: PartnerController,
    onLeft: (String) -> Unit,
    onToast: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    /** Drawn above the body — the follower-only app puts its person picker here. */
    header: (LazyListScope.() -> Unit)? = null,
) {
    val t = strings.partner
    val scope = rememberCoroutineScope()
    var confirmLeave by remember { mutableStateOf(false) }

    // Read again when the list says her status moved — a yes, a pause — not only on open.
    val listed = partner.following.firstOrNull { it.linkId == linkId }?.status
    val unread = partner.following.firstOrNull { it.linkId == linkId }?.unread ?: 0
    LaunchedEffect(linkId, listed) { partner.loadView(linkId, silent = partner.views[linkId] != null) }
    // The messages too, and again whenever the list says she sent something new.
    LaunchedEffect(linkId, unread) { partner.loadMessages(linkId) }
    // Her requests to pay, read with the view; a push about one lands here too.
    LaunchedEffect(linkId, listed) { partner.loadIncoming() }

    val view = partner.views[linkId]

    Box(modifier.fillMaxSize()) {
        ScreenContent {
            header?.invoke(this)
            partner.error?.let { failure ->
                item { ErrorStrip(failure.partnerReadable(), onRetry = { scope.launch { partner.loadView(linkId) } }) }
            }
            partner.incoming.filter { it.linkId == linkId }.forEach { request ->
                item(key = "payreq-${request.id}") { IncomingRequestCard(request, partner, onToast) }
            }

            when {
                view == null -> item { Skeleton(Modifier.fillMaxWidth().size(width = 0.dp, height = 180.dp)) }
                view.status == PartnerLinkStatus.PENDING -> item {
                    StatusCard(Res.drawable.ic3d_clock, t.pendingTitle(view.name), t.pendingBody)
                }
                view.status == PartnerLinkStatus.PAUSED -> item {
                    StatusCard(Res.drawable.ic3d_sleep, t.pausedViewTitle(view.name), t.pausedViewBody)
                }
                view.isEmpty && view.stage == null -> item {
                    StatusCard(Res.drawable.ic3d_heart, t.nothingShared(view.name), t.pausedViewBody)
                }
                else -> activeView(view) {
                    PartnerMessagesCard(
                        linkId = linkId,
                        partner = partner,
                        title = t.sendTo(firstName(view.name)),
                        otherName = view.name,
                        kinds = FollowerKinds,
                        answersRequests = true,
                        onSent = onToast,
                    )
                }
            }

            if (view != null) {
                if (view.status == PartnerLinkStatus.ACTIVE) item { AcceptRequestsRow(linkId, partner) }
                item {
                    SadoraButton(t.leave, { confirmLeave = true }, tone = ButtonTone.Ghost)
                }
            }
        }
    }

    SadoraDialog(
        visible = confirmLeave,
        title = t.leave,
        body = t.leaveConfirmBody(view?.name.orEmpty()),
        confirmText = t.leave,
        onConfirm = {
            confirmLeave = false
            scope.launch { if (partner.leave(linkId)) onLeft(t.disconnected) }
        },
        onDismiss = { confirmLeave = false },
    )
}

private fun LazyListScope.activeView(view: PartnerView, messages: @Composable () -> Unit) {
    val cycle = view.cycle
    val pregnancy = view.pregnancy

    item { TodayCard(view) }
    item { messages() }

    view.day?.let { day ->
        item {
            val t = strings.partner
            val c = Sadora.colors
            SadoraCard {
                CardLabel(t.todayHeading)
                day.mood?.let { mood -> Fact(t.moodLabel, strings.common.mood(mood.toAppMood())) }
                day.energy?.let { energy -> Fact(t.energyLabel, "$energy / 5") }
                if (day.symptoms.isNotEmpty()) {
                    Text(t.symptomsLabel, style = Sadora.type.body, color = c.muted)
                    ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xs) {
                        day.symptoms.forEach { SadoraBadge(it, BadgeTone.Neutral) }
                    }
                }
            }
        }
    }

    view.care?.let { care ->
        item {
            val t = strings.partner
            SadoraCard {
                care.feedsToday?.let { Fact(t.feedsToday(it), care.lastFeedAt?.let { at -> t.lastFeed(strings.dates.ago(at, Clock.System.now())) }) }
                care.hotFlushesToday?.let { Fact(t.hotFlushesToday(it), null) }
            }
        }
    }

    if (view.appointments.isNotEmpty()) item {
        val t = strings.partner
        val c = Sadora.colors
        SadoraCard {
            CardLabel(t.appointmentsTitle)
            view.appointments.forEach { visit ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    ArtTile(Res.drawable.ic3d_doctor, tint = c.primary, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(visit.title, style = Sadora.type.h3, color = c.text)
                        val time = visit.scheduledAt?.let { " · " + Fmt.clock(it) }.orEmpty()
                        Text(
                            strings.dates.relativeDay(visit.scheduledOn, view.today) + time + (visit.place?.let { " · $it" }.orEmpty()),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }
        }
    }

    item {
        val t = strings.partner
        val c = Sadora.colors
        val tips = tipsFor(view)
        if (tips.isNotEmpty()) SadoraCard {
            CardLabel(t.helpTitle)
            tips.forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
                    Text("♥", style = Sadora.type.body, color = c.primary)
                    Text(tip, style = Sadora.type.body, color = c.text)
                }
            }
        }
    }

    if (cycle != null || pregnancy?.dueDate != null) item { DisclaimerNote(strings.partner.estimatedNote) }
}

/** The headline: where she is today, in the largest type on the screen. */
@Composable
private fun TodayCard(view: PartnerView) {
    val t = strings.partner
    val c = Sadora.colors
    val dates = strings.dates
    val cycle = view.cycle
    val pregnancy = view.pregnancy
    val phase = cycle?.phase?.toAppPhase()

    SadoraCard {
        when {
            cycle != null && phase != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PhaseDot(phase.color())
                    Text(t.phaseTitle(phase), style = Sadora.type.h2, color = c.text, modifier = Modifier.weight(1f))
                }
                val day = if (cycle.periodNow) cycle.periodDay?.let(t::periodDay) else cycle.cycleDay?.let(t::cycleDay)
                day?.let { Text(it, style = Sadora.type.h3.copy(fontWeight = FontWeight.SemiBold), color = phase.color()) }
                Text(t.phaseFeel(phase), style = Sadora.type.body, color = c.muted)
            }
            pregnancy?.week != null -> {
                val week = pregnancy.week!!
                Text(t.pregnancyWeek(week), style = Sadora.type.h2, color = c.text)
                if (week >= PregnancyWeeks.FIRST) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        ArtIcon(uz.sadora.app.ui.journey.weekArt(week), 28.dp)
                        Text(
                            t.babySize(strings.pregnancyWeeks.fruits.forWeek(week)),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
                pregnancy.daysToGo?.let { Text(t.daysToGo(it), style = Sadora.type.h3, color = c.textAccent) }
            }
            pregnancy?.babyAgeDays != null -> {
                Text(strings.stages.title(uz.sadora.app.model.LifeStage.Postpartum), style = Sadora.type.h2, color = c.text)
                Text(t.babyAge(pregnancy.babyAgeDays!!), style = Sadora.type.h3, color = c.textAccent)
            }
            view.stage == LifeStage.PERIMENOPAUSE || view.stage == LifeStage.MENOPAUSE -> {
                val stage = if (view.stage == LifeStage.MENOPAUSE) uz.sadora.app.model.LifeStage.Menopause else uz.sadora.app.model.LifeStage.Perimenopause
                Text(strings.stages.title(stage), style = Sadora.type.h2, color = c.text)
                Text(strings.stages.subtitle(stage), style = Sadora.type.body, color = c.muted)
            }
            else -> Text(view.name, style = Sadora.type.h2, color = c.text)
        }

        if (cycle != null) {
            val next = cycle.nextPeriodStart
            val days = cycle.daysUntilNextPeriod
            if (!cycle.periodNow && next != null && days != null) {
                Fact(t.periodIn(days), t.periodAround(dates.dayMonth(next)))
            }
            val from = cycle.fertileFrom
            val until = cycle.fertileUntil
            if (from != null && until != null) {
                val today = view.today
                Text(
                    if (today in from..until) t.fertileToday else t.fertileWindow(dates.dayMonth(from), dates.dayMonth(until)),
                    style = Sadora.type.body,
                    color = PhaseColors.fertile,
                )
            }
        }
    }
}

@Composable
private fun tipsFor(view: PartnerView): List<String> {
    val t = strings.partner
    val phase = view.cycle?.phase?.toAppPhase()
    return when {
        phase != null -> t.phaseTips(phase)
        view.pregnancy?.week != null -> t.pregnancyTips(view.pregnancy!!.week!!)
        view.pregnancy?.babyAgeDays != null || view.stage == LifeStage.POSTPARTUM -> t.postpartumTips
        view.stage == LifeStage.PERIMENOPAUSE || view.stage == LifeStage.MENOPAUSE -> t.menopauseTips
        else -> emptyList()
    }
}

@Composable
private fun StatusCard(art: DrawableResource, title: String, body: String) {
    val c = Sadora.colors
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(art, tint = c.primary, size = 46.dp)
            Text(title, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
        }
        Text(body, style = Sadora.type.body, color = c.muted)
    }
}

@Composable
private fun Fact(title: String, detail: String?) {
    val c = Sadora.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = Sadora.type.body, color = c.muted)
    }
}

@Composable
private fun PhaseDot(color: Color) {
    Box(Modifier.size(14.dp).clip(Radius.chip).background(color))
}

internal fun WirePhase.toAppPhase(): CyclePhase = when (this) {
    WirePhase.PERIOD -> CyclePhase.Period
    WirePhase.FOLLICULAR -> CyclePhase.Follicular
    WirePhase.FERTILE -> CyclePhase.Fertile
    WirePhase.LUTEAL -> CyclePhase.Luteal
}

private fun CyclePhase.color(): Color = when (this) {
    CyclePhase.Period -> PhaseColors.period
    CyclePhase.Follicular -> PhaseColors.follicular
    CyclePhase.Fertile -> PhaseColors.fertile
    CyclePhase.Luteal -> PhaseColors.luteal
}

/**
 * A failure as these screens word it. A refused code is the one case where the server's
 * sentence is the useful one — "not found or expired" — and it arrives as a plain 404,
 * which the shared wording would turn into "something went wrong".
 */
@Composable
internal fun ApiFailure.partnerReadable(): String = when (this) {
    is ApiFailure.Unexpected -> if (requestId != null && message.isNotBlank()) message else readable()
    else -> readable()
}
