package uz.sadora.app.ui.journey

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.app.data.HealthController
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.PhaseColors
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.design.StagePalettes
import kotlinx.datetime.daysUntil
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CyclePhase
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.LifeStage
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.AnimatedNumber
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.CircleIconButton
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ImagePlaceholder
import uz.sadora.app.ui.components.LabeledProgress
import uz.sadora.app.ui.components.LotusIllustration
import uz.sadora.app.ui.components.Motion
import uz.sadora.app.ui.components.ProgressRing
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SectionHeader
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.WeeklyBars
import uz.sadora.app.ui.components.animatedProgress
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.pressable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import uz.sadora.contract.SymptomDefinition

/**
 * "Yo'l" — the tab that changes completely with the life stage.
 *
 * This is not one screen with features toggled off. Pregnancy, postpartum,
 * perimenopause and menopause each get their own layout, headline metric and
 * language; only cycle-based stages show predictions at all.
 */
@Composable
fun JourneyScreen(
    state: AppState,
    health: HealthController,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.journey
    Column(modifier) {
        when (state.lifeStage) {
            LifeStage.Cycle, LifeStage.TryingToConceive -> CycleJourney(state, health, onOpen)
            LifeStage.Pregnancy -> PregnancyJourney(state, health, onOpen)
            LifeStage.Postpartum -> PostpartumJourney(state, onOpen)
            LifeStage.Perimenopause -> PerimenopauseJourney(state, health, onOpen)
            LifeStage.Menopause -> MenopauseJourney(state, health, onOpen)
        }
    }
}

// ---------------------------------------------------------------- cycle

/** The dial colour of a phase, as the deck's legend draws it. */
private fun CyclePhase.dialColor(): Color = when (this) {
    CyclePhase.Period -> PhaseColors.period
    CyclePhase.Follicular -> PhaseColors.follicular
    CyclePhase.Fertile -> PhaseColors.fertile
    CyclePhase.Luteal -> PhaseColors.luteal
}

/**
 * t.cycleTitle — the deck's cycle screen: month header, this week's dates, the dial
 * with every day of the cycle around it as its own bead, the legend, today's reading
 * with the lotus, and the symptom tiles.
 */
@Composable
private fun CycleJourney(state: AppState, health: HealthController, onOpen: (Route) -> Unit) {
    val t = strings.journey
    val c = Sadora.colors
    val phase = state.currentPhase()

    SadoraTopBar(
        t.cycleTitle,
        centered = true,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CircleIconButton(SadoraIcons.Info, contentDescription = t.info) { onOpen(Route.Knowledge) }
                CircleIconButton(SadoraIcons.Calendar, contentDescription = t.calendar) { onOpen(Route.CycleCalendar) }
            }
        },
    )

    ScreenContent {
        item {
            Row(
                Modifier.fillMaxWidth().noRippleClickable { onOpen(Route.CycleCalendar) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    strings.dates.monthYear(state.today.year, state.today.month.ordinal + 1),
                    style = Sadora.type.h3,
                    color = c.text,
                )
                Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.sm).padding(start = 2.dp), tint = c.muted)
            }
        }

        item { CycleWeekStrip(state, onOpen) }

        item {
            if (state.hasCyclePrediction) {
                CycleDial(state, Modifier.fillMaxWidth(0.92f).aspectRatio(1f).align(Alignment.CenterHorizontally))
            } else {
                SadoraCard {
                    Text(t.noPredictionTitle, style = Sadora.type.h3, color = c.text)
                    Text(
                        t.noPredictionBody,
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                    SadoraButton(t.markPeriod, onClick = { onOpen(Route.CycleCalendar) }, tone = ButtonTone.Secondary)
                }
            }
        }

        item { PhaseLegend() }

        item {
            SadoraCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(t.today, style = Sadora.type.h3, color = c.text)
                        Text(strings.common.phaseFertility(phase), style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                        Text(strings.common.phaseEnergy(phase), style = Sadora.type.body, color = c.muted)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            // The badge is the part that must survive a narrow card: a
                            // prediction shown without it reads as a fact, so the line
                            // beside it gives way first.
                            Text(
                                t.daysToNextPeriod(state.daysToNextPeriod()),
                                style = Sadora.type.body,
                                color = c.muted,
                                maxLines = 2,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            SadoraBadge(t.estimatedCaps, BadgeTone.Estimated, icon = SadoraIcons.Clock)
                        }
                    }
                    LotusIllustration(Modifier.size(96.dp))
                }
            }
        }

        item {
            SadoraCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(t.symptoms, style = Sadora.type.h3, color = c.text)
                    Text(
                        t.change,
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textAccent,
                        modifier = Modifier.noRippleClickable { onOpen(Route.StageSymptoms) },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // The catalogue, not a list written here: what she can tap has to be
                    // something the rest of the app can count, and the severity shown is
                    // the one she recorded rather than a number chosen for the picture.
                    health.symptoms.take(SymptomTiles).forEach { definition ->
                        val selected = definition.label in state.symptoms
                        SymptomTileView(
                            emoji = definition.glyph(),
                            label = definition.label,
                            severity = health.severityOf(definition.key),
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = { state.toggleSymptom(definition.label) },
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard(t.averageCycle, t.daysValue(state.averageCycleLength), Modifier.weight(1f))
                StatCard(t.averagePeriod, t.daysValue(state.averagePeriodLength), Modifier.weight(1f))
            }
        }

        item { DisclaimerNote(t.predictionDisclaimer) }
    }
}

/**
 * The dial: every day of the cycle around a ring, the ring coloured by phase, today
 * marked on it, and the day count in the middle.
 *
 * Days after today are drawn paler — they are the prediction, and the design never
 * lets a forecast look like a recorded fact.
 */
@Composable
private fun CycleDial(state: AppState, modifier: Modifier = Modifier) {
    val t = strings.journey
    val c = Sadora.colors
    val n = state.averageCycleLength.coerceAtLeast(1)
    val today = state.cycleDay.coerceIn(1, n)
    val phase = state.currentPhase()
    val fertile = state.fertileWindowDays()

    fun phaseOf(day: Int): CyclePhase = when {
        day <= state.averagePeriodLength -> CyclePhase.Period
        day in fertile -> CyclePhase.Fertile
        day < fertile.first -> CyclePhase.Follicular
        else -> CyclePhase.Luteal
    }

    // The ring writes itself day by day rather than appearing whole — the same
    // left-to-right reading the numbers under it have.
    val reveal = animatedProgress(1f, durationMillis = Motion.Reveal)
    // Today's bead keeps breathing after the ring has settled, so the eye returns to it.
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1800, easing = Motion.Gentle), RepeatMode.Reverse),
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val ringR = r * 0.82f
            val dotR = (r * 0.052f).coerceAtMost(ringR * PI.toFloat() / n)
            val step = 360f / n
            // The wave passes over a handful of days at a time; a longer tail on a long
            // cycle would take the same total time but arrive in a visible clump.
            val tail = 6f

            for (day in 1..n) {
                // 0 until this day's turn comes, 1 once it has fully arrived.
                val t = (((reveal * (n + tail)) - day) / tail).coerceIn(0f, 1f)
                if (t <= 0f) continue

                val angle = (-90f + (day - 1) * step) * PI.toFloat() / 180f
                val at = Offset(center.x + cos(angle) * ringR, center.y + sin(angle) * ringR)
                val isToday = day == today
                // Days after today are the prediction, and the design never lets a
                // forecast look like a recorded fact.
                val future = day > today
                val colour = phaseOf(day).dialColor().copy(alpha = (if (future) 0.4f else 1f) * t)

                if (isToday) {
                    drawCircle(colour.copy(alpha = 0.25f * t), radius = dotR * 2.6f * pulse, center = at)
                    drawCircle(c.surface, radius = dotR * 1.7f, center = at)
                    drawCircle(colour, radius = dotR * 1.15f * t, center = at)
                } else {
                    drawCircle(colour, radius = dotR * t, center = at)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth(0.55f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(t.day, style = Sadora.type.body, color = c.muted)
            AnimatedNumber(
                today,
                Sadora.type.data.copy(fontSize = 56.sp, lineHeight = 60.sp),
                c.text,
            )
            Text(strings.common.phase(phase), style = Sadora.type.h3, color = c.text, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/**
 * The week above the dial: seven days ending on today's week, each with its phase
 * colour under the date and today in a filled disc.
 *
 * It is what the dial gives up by dropping the day numbers off the ring — the dial
 * shows the shape of the cycle, this shows which actual dates those days are.
 */
@Composable
private fun CycleWeekStrip(state: AppState, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    // Monday of the current week; DayOfWeek.ordinal is 0 for Monday.
    val monday = state.today.minus(state.today.dayOfWeek.ordinal, DateTimeUnit.DAY)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(7) { index ->
            val date = monday.plus(index, DateTimeUnit.DAY)
            val isToday = date == state.today
            val phase = state.phaseForDate(date)

            Column(
                Modifier
                    .weight(1f)
                    .clip(Radius.chip)
                    .pressable { onOpen(Route.CycleDay(date.toString())) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    strings.dates.weekdays[index].take(2).replaceFirstChar { it.uppercase() },
                    style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                    color = c.muted2,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(Radius.chip)
                        .background(if (isToday) c.heroGradient else SolidColor(Color.Transparent)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${date.day}",
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isToday) c.onPrimary else c.text,
                        maxLines = 1,
                    )
                }
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(Radius.chip)
                        .background(phase?.dialColor() ?: Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun PhaseLegend() {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CyclePhase.entries.forEach { phase ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(8.dp).clip(Radius.chip).background(phase.dialColor()))
                Text(
                    strings.common.phase(phase).substringBefore(" "),
                    style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                    color = c.muted,
                )
            }
        }
    }
}

/** One symptom tile: a round icon disc, the label, and three severity dots. */
@Composable
private fun SymptomTileView(
    emoji: String,
    label: String,
    severity: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Column(
        modifier.noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(Radius.chip)
                .background(if (selected) c.primary.copy(alpha = 0.16f) else c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, style = Sadora.type.h2)
        }
        Text(
            label,
            style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = if (selected) c.text else c.muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(Radius.chip)
                        .background(if (i < severity) c.primary else c.line),
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm) {
        Text(label, style = Sadora.type.body, color = c.muted)
        Text(value, style = Sadora.type.h2, color = c.text)
    }
}

// ---------------------------------------------------------------- pregnancy

@Composable
private fun PregnancyJourney(state: AppState, health: HealthController, onOpen: (Route) -> Unit) {
    val t = strings.journey
    LaunchedEffect(Unit) { health.loadAppointments() }
    val c = Sadora.colors
    val palette = LifeStage.Pregnancy.palette

    SadoraTopBar(
        t.pregnancyTitle,
        trailing = {
            Text(t.trimester(state.pregnancyWeek), style = Sadora.type.body, color = c.muted)
        },
    )

    ScreenContent {
        item {
            // Warm gradient header — the stage's own identity, not the cycle palette.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.card)
                    .background(Brush.linearGradient(listOf(palette.start, palette.end)))
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val onWarm = StagePalettes.warmInk
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.pregnancyWeek}", style = Sadora.type.data, color = onWarm)
                    Text(
                        t.weekCaps,
                        style = Sadora.type.caption,
                        color = onWarm.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                // The day within the week, counted from the same anchor as the week.
                val due = state.dueDate
                val dayOfWeek = due?.let {
                    ((280 - state.today.daysUntil(it)) % 7).coerceIn(0, 6) + 1
                }
                Text(
                    if (dayOfWeek != null) {
                        t.weekAndDay(state.pregnancyWeek, dayOfWeek)
                    } else {
                        t.weekOnly(state.pregnancyWeek)
                    },
                    style = Sadora.type.h3,
                    color = onWarm,
                )
                if (due != null) {
                    val left = state.today.daysUntil(due)
                    Text(
                        if (left >= 0) {
                            t.dueOn(strings.dates.dayMonth(due), left)
                        } else {
                            t.dueOnPast(strings.dates.dayMonth(due))
                        },
                        style = Sadora.type.body,
                        color = onWarm.copy(alpha = 0.85f),
                    )
                }
            }
        }

        item {
            SadoraCard {
                ImagePlaceholder(
                    Modifier.fillMaxWidth().aspectRatio(2.1f),
                    emoji = "🤰",
                    colors = listOf(palette.start.copy(alpha = 0.35f), palette.end.copy(alpha = 0.35f)),
                )
                Text(t.babyDevelopment, style = Sadora.type.h3, color = c.text)
                // The app carries no week-by-week medical table of its own, and inventing
                // one is not an option — the library the clinicians write is where this
                // belongs, so the card leads there rather than stating a size.
                Text(
                    t.babyDevelopmentBody,
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item {
            SadoraCard {
                CardLabel(t.todaysSymptoms)
                ChipFlowRow {
                    health.symptoms.forEach { definition ->
                        SelectChip(
                            label = definition.label,
                            selected = definition.label in state.symptoms,
                            onClick = { state.toggleSymptom(definition.label) },
                        )
                    }
                    SelectChip(t.addSymptom, selected = false, onClick = { onOpen(Route.StageSymptoms) })
                }
            }
        }

        item {
            SectionHeader(
                t.upcomingAppointments,
                action = t.all,
                onAction = { onOpen(Route.PregnancyAppointments) },
            )
        }

        val today = health.cycle?.today ?: state.today
        val upcoming = health.appointments.filter { !it.isDone && it.scheduledOn >= today }.take(2)

        if (upcoming.isEmpty()) {
            item {
                SadoraCard(onClick = { onOpen(Route.PregnancyAppointments) }) {
                    Text(t.noAppointments, style = Sadora.type.h3, color = c.text)
                    Text(
                        t.noAppointmentsBody,
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }
        }

        items(upcoming.size) { index ->
            val appointment = upcoming[index]
            SadoraCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(
                        Modifier
                            .clip(Radius.cardSmall)
                            .background(c.surface2)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${appointment.scheduledOn.day}", style = Sadora.type.h2, color = c.text)
                        Text(
                            strings.dates.months[appointment.scheduledOn.month.ordinal].take(3).uppercase(),
                            style = Sadora.type.caption,
                            color = c.muted,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(appointment.title, style = Sadora.type.h3, color = c.text)
                        val detail = listOfNotNull(
                            appointment.scheduledAt?.let { Fmt.clock(it) },
                            appointment.place,
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(detail, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }
            }
        }

        item {
            SadoraButton(
                t.logToday,
                onClick = { onOpen(Route.PregnancyCheckIn) },
                tone = ButtonTone.Secondary,
            )
        }

        item {
            AiAdviceCard(
                t.aiAdvice,
            )
        }
    }
}

/** Stage-level AI recommendation — gradient, premium-badged, explicitly general. */
@Composable
private fun AiAdviceCard(body: String) {
    val t = strings.journey
    val c = Sadora.colors
    val onGradient = c.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(c.heroGradient)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(t.aiBadge, style = Sadora.type.caption, color = onGradient)
            Box(
                Modifier
                    .clip(Radius.chip)
                    .background(onGradient.copy(alpha = 0.2f))
                    .padding(horizontal = Spacing.xs, vertical = 3.dp),
            ) {
                Text(strings.journey.premiumCaps, style = Sadora.type.caption, color = onGradient)
            }
        }
        Text(body, style = Sadora.type.body, color = onGradient)
    }
}

// ---------------------------------------------------------------- postpartum

@Composable
private fun PostpartumJourney(state: AppState, onOpen: (Route) -> Unit) {
    val t = strings.journey
    val c = Sadora.colors
    SadoraTopBar(t.postpartumTitle)

    ScreenContent {
        item {
            SadoraCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.postpartumWeek}", style = Sadora.type.data, color = c.text)
                    Text(
                        t.recoveryWeeks,
                        style = Sadora.type.body,
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                // No prediction here at all — recovery is not forecast.
                Text(
                    t.recoveryNote,
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                    CardLabel(t.mood)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(state.mood.emoji, style = Sadora.type.h1)
                        Text(strings.common.mood(state.mood), style = Sadora.type.h3, color = c.text)
                    }
                }
                SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                    CardLabel(t.sleep)
                    Text(state.sleepLabel(format = strings.common::hoursMinutes), style = Sadora.type.h2, color = c.text)
                    Text(t.brokenSleep, style = Sadora.type.body, color = c.muted)
                }
            }
        }

        item {
            SadoraCard {
                CardLabel(t.feedingAndWater)
                LabeledProgress(
                    t.water,
                    "${Fmt.litres(state.waterMl)} / ${Fmt.litres(state.waterGoalMl)} " +
                        strings.common.litres,
                    state.waterMl / state.waterGoalMl.coerceAtLeast(1).toFloat(),
                    color = c.accent,
                )
                LabeledProgress(
                    t.calories,
                    "${Fmt.int(state.caloriesEaten)} / ${Fmt.int(state.calorieGoal)}",
                    state.caloriesEaten / state.calorieGoal.coerceAtLeast(1).toFloat(),
                )
            }
        }

        item {
            SadoraCard {
                CardLabel(t.moodWatch)
                Text(
                    t.moodWatchBody,
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item { SectionHeader(t.postpartumLibrary) }

        item {
            // The library is the server's, so this opens it rather than naming an
            // article that may not be published.
            SadoraCard(onClick = { onOpen(Route.Knowledge) }) {
                ImagePlaceholder(Modifier.fillMaxWidth().aspectRatio(2.4f), emoji = "🧘‍♀️")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraBadge(t.libraryCaps, BadgeTone.Neutral)
                }
                Text(
                    t.postpartumLibraryBody,
                    style = Sadora.type.h3,
                    color = c.text,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- perimenopause

@Composable
private fun PerimenopauseJourney(state: AppState, health: HealthController, onOpen: (Route) -> Unit) {
    val t = strings.journey
    LaunchedEffect(Unit) { health.loadHistory() }
    val c = Sadora.colors
    SadoraTopBar(t.perimenopauseTitle)

    ScreenContent {
        item {
            SadoraCard {
                // A regularity chart replaces prediction entirely at this stage: the
                // value is in seeing the spread, and its bars are the lengths of her own
                // last six cycles rather than a shape drawn to look like variation.
                val cycles = health.history?.cycles.orEmpty().takeLast(6)
                CardLabel(
                    t.cycleRegularity,
                    trailing = {
                        Text(
                            if (cycles.isEmpty()) t.noData else t.lastCycles(cycles.size),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    },
                )
                if (cycles.isEmpty()) {
                    Text(
                        t.regularityEmpty,
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                } else {
                    // Scaled against the longest cycle so the bars compare with each
                    // other, which is the only comparison that means anything here.
                    val longest = cycles.maxOf { it.cycleLength }.coerceAtLeast(1)
                    WeeklyBars(
                        values = cycles.map { it.cycleLength / longest.toFloat() },
                        labels = cycles.map {
                            strings.dates.months[it.startedOn.month.ordinal].take(3)
                        },
                        color = c.primary,
                    )
                    val shortest = cycles.minOf { it.cycleLength }
                    Text(
                        if (longest - shortest >= 7) {
                            t.regularitySpread(shortest, longest)
                        } else {
                            t.regularitySteady(shortest, longest)
                        },
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }
        }

        item {
            SadoraCard {
                CardLabel(t.symptoms)
                ChipFlowRow {
                    health.symptoms.forEach { definition ->
                        SelectChip(
                            label = definition.label,
                            selected = definition.label in state.symptoms,
                            onClick = { state.toggleSymptom(definition.label) },
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard(t.sleep, state.sleepLabel(format = strings.common::hoursMinutes), Modifier.weight(1f))
                StatCard(t.energy, "${state.energy} / 5", Modifier.weight(1f))
            }
        }

        item {
            SadoraCard(onClick = { onOpen(Route.StageSleepMood) }) {
                CardLabel(t.observation)
                // The observation is the insights service's, or there is none: a
                // correlation nobody measured is the one thing this card must not say.
                Text(
                    t.observationBody,
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item {
            SadoraButton(
                t.seeSymptoms,
                onClick = { onOpen(Route.StageSymptoms) },
                tone = ButtonTone.Secondary,
            )
        }
    }
}

// ---------------------------------------------------------------- menopause

@Composable
private fun MenopauseJourney(state: AppState, health: HealthController, onOpen: (Route) -> Unit) {
    val t = strings.journey
    val c = Sadora.colors
    SadoraTopBar(t.menopauseTitle)

    ScreenContent {
        item {
            SadoraCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // Balance score, not a cycle count, is the headline here — and it is
                    // the same score the Balance screen works out, not a fixed 72.
                    val score = state.balanceScore()
                    ProgressRing(
                        progress = score / 100f,
                        size = 120.dp,
                        strokeWidth = 11.dp,
                        color = c.accent,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$score", style = Sadora.type.data, color = c.text)
                            Text(t.balanceCaps, style = Sadora.type.caption, color = c.muted)
                        }
                    }
                    Text(
                        t.scoreNote,
                        style = Sadora.type.body,
                        color = c.muted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard(t.sleep, state.sleepLabel(format = strings.common::hoursMinutes), Modifier.weight(1f))
                StatCard(t.activity, Fmt.int(state.steps), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard(t.mood, strings.common.mood(state.mood), Modifier.weight(1f))
                StatCard(t.water, "${Fmt.litres(state.waterMl)} ${strings.common.litres}", Modifier.weight(1f))
            }
        }

        item {
            SadoraCard {
                CardLabel(t.symptoms)
                ChipFlowRow {
                    health.symptoms.forEach { definition ->
                        SelectChip(
                            label = definition.label,
                            selected = definition.label in state.symptoms,
                            onClick = { state.toggleSymptom(definition.label) },
                        )
                    }
                    SelectChip(t.addSymptom, selected = false, onClick = { onOpen(Route.StageSymptoms) })
                }
            }
        }

        item {
            SadoraButton(
                t.seeSymptoms,
                onClick = { onOpen(Route.StageSymptoms) },
                tone = ButtonTone.Secondary,
            )
        }

    }
}

/** Keeps [Modifier.align] usable inside a LazyColumn item. */
private fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this


/** How many symptom tiles fit the cycle card's row. */
private const val SymptomTiles = 4

/**
 * A face for a symptom tile.
 *
 * The catalogue is a list of words; the tiles want a picture. Anything the design has
 * not drawn falls back to a neutral dot rather than to a wrong one.
 */
private fun SymptomDefinition.glyph(): String = when (key) {
    "discharge" -> "💧"
    "cramps" -> "🌀"
    "headache" -> "🤕"
    "back_pain" -> "🪢"
    "joint_pain" -> "🦴"
    "breast_tender" -> "🎀"
    "nausea" -> "🤢"
    "bloating" -> "🎈"
    "swelling" -> "🫧"
    "acne" -> "✨"
    "mood_swings" -> "🎭"
    "anxiety" -> "😟"
    "insomnia" -> "🌙"
    "night_sweats" -> "💦"
    "hot_flush" -> "🔥"
    "fatigue" -> "🔋"
    "cravings" -> "🍫"
    else -> "•"
}
