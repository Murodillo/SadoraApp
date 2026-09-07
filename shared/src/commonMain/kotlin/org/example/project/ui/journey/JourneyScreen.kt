package org.example.project.ui.journey

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
import org.example.project.data.HealthController
import org.example.project.design.IconSize
import org.example.project.design.PhaseColors
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.design.StagePalettes
import kotlinx.datetime.daysUntil
import org.example.project.model.AppState
import org.example.project.model.CyclePhase
import org.example.project.model.Fmt
import org.example.project.model.LifeStage
import org.example.project.model.SampleData
import org.example.project.nav.Route
import org.example.project.ui.components.AnimatedNumber
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.CircleIconButton
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.LabeledProgress
import org.example.project.ui.components.LotusIllustration
import org.example.project.ui.components.Motion
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SectionHeader
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.WeeklyBars
import org.example.project.ui.components.animatedProgress
import org.example.project.ui.components.noRippleClickable
import org.example.project.ui.components.pressable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    Column(modifier) {
        when (state.lifeStage) {
            LifeStage.Cycle, LifeStage.TryingToConceive -> CycleJourney(state, onOpen)
            LifeStage.Pregnancy -> PregnancyJourney(state, health, onOpen)
            LifeStage.Postpartum -> PostpartumJourney(state, onOpen)
            LifeStage.Perimenopause -> PerimenopauseJourney(state, health, onOpen)
            LifeStage.Menopause -> MenopauseJourney(state, onOpen)
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
 * "Mening siklim" — the deck's cycle screen: month header, this week's dates, the dial
 * with every day of the cycle around it as its own bead, the legend, today's reading
 * with the lotus, and the symptom tiles.
 */
@Composable
private fun CycleJourney(state: AppState, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    val phase = state.currentPhase()

    SadoraTopBar(
        "Mening siklim",
        centered = true,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CircleIconButton(SadoraIcons.Info, contentDescription = "Ma'lumot") { onOpen(Route.Knowledge) }
                CircleIconButton(SadoraIcons.Calendar, contentDescription = "Kalendar") { onOpen(Route.CycleCalendar) }
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
                    Fmt.monthYear(state.today.year, state.today.month.ordinal + 1),
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
                    Text("Prognoz uchun ma'lumot yetarli emas", style = Sadora.type.h3, color = c.text)
                    Text(
                        "Kamida ikkita hayz sanasi kiritilgach, sikl fazalari va keyingi hayz taxmini shu yerda ko'rinadi.",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                    SadoraButton("Hayzni belgilash", onClick = { onOpen(Route.CycleCalendar) }, tone = ButtonTone.Secondary)
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
                        Text("Bugun", style = Sadora.type.h3, color = c.text)
                        Text(phase.fertilityNote, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                        Text(phase.energyNote, style = Sadora.type.body, color = c.muted)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            // The badge is the part that must survive a narrow card: a
                            // prediction shown without it reads as a fact, so the line
                            // beside it gives way first.
                            Text(
                                "Keyingi hayz — ${state.daysToNextPeriod()} kun",
                                style = Sadora.type.body,
                                color = c.muted,
                                maxLines = 2,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            SadoraBadge("TAXMINIY", BadgeTone.Estimated, icon = SadoraIcons.Clock)
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
                    Text("Simptomlar", style = Sadora.type.h3, color = c.text)
                    Text(
                        "O'zgartirish",
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textAccent,
                        modifier = Modifier.noRippleClickable { onOpen(Route.StageSymptoms) },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SampleData.cycleSymptomTiles.forEach { tile ->
                        val selected = tile.label in state.symptoms
                        SymptomTileView(
                            emoji = tile.emoji,
                            label = tile.label,
                            severity = if (selected) tile.severity else 0,
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = { state.toggleSymptom(tile.label) },
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard("O'rtacha sikl", "${state.averageCycleLength} kun", Modifier.weight(1f))
                StatCard("O'rtacha hayz", "${state.averagePeriodLength} kun", Modifier.weight(1f))
            }
        }

        item { DisclaimerNote(SampleData.predictionDisclaimer) }
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
            Text("Kun", style = Sadora.type.body, color = c.muted)
            AnimatedNumber(
                today,
                Sadora.type.data.copy(fontSize = 56.sp, lineHeight = 60.sp),
                c.text,
            )
            Text(phase.label, style = Sadora.type.h3, color = c.text, textAlign = TextAlign.Center, maxLines = 2)
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
                    Fmt.weekdays[index].take(2).replaceFirstChar { it.uppercase() },
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
                    phase.label.substringBefore(" "),
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
    LaunchedEffect(Unit) { health.loadAppointments() }
    val c = Sadora.colors
    val palette = LifeStage.Pregnancy.palette

    SadoraTopBar(
        "Homiladorlik",
        trailing = {
            Text(trimesterLabel(state.pregnancyWeek), style = Sadora.type.body, color = c.muted)
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
                        "  HAFTA",
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
                        "${state.pregnancyWeek}-hafta, $dayOfWeek-kun"
                    } else {
                        "${state.pregnancyWeek}-hafta"
                    },
                    style = Sadora.type.h3,
                    color = onWarm,
                )
                if (due != null) {
                    val left = state.today.daysUntil(due)
                    Text(
                        if (left >= 0) {
                            "Tug'ish sanasi — ${Fmt.dayMonth(due)} · $left kun qoldi"
                        } else {
                            "Tug'ish sanasi — ${Fmt.dayMonth(due)}"
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
                Text("Bolaning rivojlanishi", style = Sadora.type.h3, color = c.text)
                // The app carries no week-by-week medical table of its own, and inventing
                // one is not an option — the library the clinicians write is where this
                // belongs, so the card leads there rather than stating a size.
                Text(
                    "Bu haftada nima o'zgarayotgani haqida Bilim kutubxonasida o'qing.",
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item {
            SadoraCard {
                CardLabel("Bugungi simptomlar")
                ChipFlowRow {
                    SampleData.pregnancySymptoms.forEach { symptom ->
                        SelectChip(
                            label = symptom,
                            selected = symptom in state.symptoms,
                            onClick = { state.toggleSymptom(symptom) },
                        )
                    }
                    SelectChip("+ Qo'shish", selected = false, onClick = { onOpen(Route.StageSymptoms) })
                }
            }
        }

        item {
            SectionHeader(
                "Yaqin uchrashuvlar",
                action = "Barchasi",
                onAction = { onOpen(Route.PregnancyAppointments) },
            )
        }

        val today = health.cycle?.today ?: state.today
        val upcoming = health.appointments.filter { !it.isDone && it.scheduledOn >= today }.take(2)

        if (upcoming.isEmpty()) {
            item {
                SadoraCard(onClick = { onOpen(Route.PregnancyAppointments) }) {
                    Text("Tadbir qo'shilmagan", style = Sadora.type.h3, color = c.text)
                    Text(
                        "Ko'rik yoki tahlil sanasini yozib qo'ying — eslatma yuboriladi.",
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
                            Fmt.months[appointment.scheduledOn.month.ordinal].take(3).uppercase(),
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
                "Bugungi holatni qayd etish",
                onClick = { onOpen(Route.PregnancyCheckIn) },
                tone = ButtonTone.Secondary,
            )
        }

        item {
            AiAdviceCard(
                "Bu haftada temirga boy ovqatlar va yengil cho'zilish mashqlari foydali " +
                    "bo'lishi mumkin. Umumiy salomatlik ma'lumoti.",
            )
        }
    }
}

/** Stage-level AI recommendation — gradient, premium-badged, explicitly general. */
@Composable
private fun AiAdviceCard(body: String) {
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
            Text("SADORA AI · TAVSIYA", style = Sadora.type.caption, color = onGradient)
            Box(
                Modifier
                    .clip(Radius.chip)
                    .background(onGradient.copy(alpha = 0.2f))
                    .padding(horizontal = Spacing.xs, vertical = 3.dp),
            ) {
                Text("PREMIUM", style = Sadora.type.caption, color = onGradient)
            }
        }
        Text(body, style = Sadora.type.body, color = onGradient)
    }
}

// ---------------------------------------------------------------- postpartum

@Composable
private fun PostpartumJourney(state: AppState, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    SadoraTopBar("Tug'ruqdan keyin")

    ScreenContent {
        item {
            SadoraCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.postpartumWeek}", style = Sadora.type.data, color = c.text)
                    Text(
                        "  hafta · tiklanish davri",
                        style = Sadora.type.body,
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                // No prediction here at all — recovery is not forecast.
                Text(
                    "Tiklanish har bir ayolda turlicha kechadi. Bu shkala faqat yo'naltiruvchi.",
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                    CardLabel("Kayfiyat")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(state.mood.emoji, style = Sadora.type.h1)
                        Text(state.mood.label, style = Sadora.type.h3, color = c.text)
                    }
                }
                SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                    CardLabel("Uyqu")
                    Text(state.sleepLabel(), style = Sadora.type.h2, color = c.text)
                    Text("Bo'lingan uyqu", style = Sadora.type.body, color = c.muted)
                }
            }
        }

        item {
            SadoraCard {
                CardLabel("Emizish va suv")
                LabeledProgress(
                    "Suv",
                    "${Fmt.litres(state.waterMl)} / ${Fmt.litres(state.waterGoalMl)} l",
                    state.waterMl / state.waterGoalMl.coerceAtLeast(1).toFloat(),
                    color = c.accent,
                )
                LabeledProgress(
                    "Kaloriya",
                    "${Fmt.int(state.caloriesEaten)} / ${Fmt.int(state.calorieGoal)}",
                    state.caloriesEaten / state.calorieGoal.coerceAtLeast(1).toFloat(),
                )
            }
        }

        item {
            SadoraCard {
                CardLabel("Kayfiyat kuzatuvi")
                Text(
                    "Uzoq davom etgan tushkunlik yoki tashvish bo'lsa, mutaxassisga murojaat " +
                        "qilish tavsiya etiladi. SADORA tashxis qo'ymaydi.",
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item { SectionHeader("Bilim — tug'ruqdan keyin") }

        item {
            // The library is the server's, so this opens it rather than naming an
            // article that may not be published.
            SadoraCard(onClick = { onOpen(Route.Knowledge) }) {
                ImagePlaceholder(Modifier.fillMaxWidth().aspectRatio(2.4f), emoji = "🧘‍♀️")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraBadge("KUTUBXONA", BadgeTone.Neutral)
                }
                Text(
                    "Tug'ruqdan keyingi materiallar",
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
    LaunchedEffect(Unit) { health.loadHistory() }
    val c = Sadora.colors
    SadoraTopBar("Perimenopauza")

    ScreenContent {
        item {
            SadoraCard {
                // A regularity chart replaces prediction entirely at this stage: the
                // value is in seeing the spread, and its bars are the lengths of her own
                // last six cycles rather than a shape drawn to look like variation.
                val cycles = health.history?.cycles.orEmpty().takeLast(6)
                CardLabel(
                    "Sikl muntazamligi",
                    trailing = {
                        Text(
                            if (cycles.isEmpty()) "ma'lumot yo'q" else "oxirgi ${cycles.size} sikl",
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    },
                )
                if (cycles.isEmpty()) {
                    Text(
                        "Hayz sanalarini belgilay boshlaganingizda sikl uzunligi shu yerda " +
                            "ko'rinadi. Bu bosqichda bashorat ko'rsatilmaydi.",
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
                            Fmt.months[it.startedOn.month.ordinal].take(3)
                        },
                        color = c.primary,
                    )
                    val shortest = cycles.minOf { it.cycleLength }
                    Text(
                        if (longest - shortest >= 7) {
                            "Sikl uzunligi $shortest–$longest kun orasida o'zgargan — bu " +
                                "bosqich uchun kutilgan holat. Bashorat ko'rsatilmaydi."
                        } else {
                            "Sikl uzunligi $shortest–$longest kun orasida. Bu bosqichda " +
                                "bashorat ko'rsatilmaydi."
                        },
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }
        }

        item {
            SadoraCard {
                CardLabel("Simptomlar")
                ChipFlowRow {
                    SampleData.perimenopauseSymptoms.forEach { symptom ->
                        SelectChip(
                            label = symptom,
                            selected = symptom in state.symptoms,
                            onClick = { state.toggleSymptom(symptom) },
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard("Uyqu", state.sleepLabel(), Modifier.weight(1f))
                StatCard("Energiya", "${state.energy} / 5", Modifier.weight(1f))
            }
        }

        item {
            SadoraCard(onClick = { onOpen(Route.StageSleepMood) }) {
                CardLabel("Kuzatish")
                // The observation is the insights service's, or there is none: a
                // correlation nobody measured is the one thing this card must not say.
                Text(
                    "Uyqu, kayfiyat va simptomlar orasidagi bog'liqliklarni ko'rish.",
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }

        item {
            SadoraButton(
                "Simptomlarni ko'rish",
                onClick = { onOpen(Route.StageSymptoms) },
                tone = ButtonTone.Secondary,
            )
        }
    }
}

// ---------------------------------------------------------------- menopause

@Composable
private fun MenopauseJourney(state: AppState, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    SadoraTopBar("Salomatlik")

    ScreenContent {
        item {
            SadoraCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // Balance score, not a cycle count, is the headline here.
                    ProgressRing(
                        progress = 0.72f,
                        size = 120.dp,
                        strokeWidth = 11.dp,
                        color = c.accent,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("72", style = Sadora.type.data, color = c.text)
                            Text("BALANS", style = Sadora.type.caption, color = c.muted)
                        }
                    }
                    Text(
                        "Uyqu, faollik, ovqatlanish va kayfiyat asosida. Bu ball tibbiy " +
                            "ko'rsatkich emas.",
                        style = Sadora.type.body,
                        color = c.muted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard("Uyqu", state.sleepLabel(), Modifier.weight(1f))
                StatCard("Faollik", Fmt.int(state.steps), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard("Kayfiyat", state.mood.label, Modifier.weight(1f))
                StatCard("Suv", "${Fmt.litres(state.waterMl)} l", Modifier.weight(1f))
            }
        }

        item {
            SadoraCard {
                CardLabel("Simptomlar")
                ChipFlowRow {
                    SampleData.menopauseSymptoms.forEach { symptom ->
                        SelectChip(
                            label = symptom,
                            selected = symptom in state.symptoms,
                            onClick = { state.toggleSymptom(symptom) },
                        )
                    }
                    SelectChip("+ Qo'shish", selected = false, onClick = { onOpen(Route.StageSymptoms) })
                }
            }
        }

        item {
            SadoraButton(
                "Simptomlarni ko'rish",
                onClick = { onOpen(Route.StageSymptoms) },
                tone = ButtonTone.Secondary,
            )
        }

        item {
            SadoraCard {
                CardLabel("Haftalik maqsadlar")
                LabeledProgress("Kuch mashqlari", "2 / 3", 2f / 3f, color = c.primary)
                LabeledProgress("Kalsiy va D vitamini", "5 / 7", 5f / 7f, color = c.primary)
            }
        }
    }
}

/** Keeps [Modifier.align] usable inside a LazyColumn item. */
private fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this

/** Which third of the pregnancy a week falls in, as the header says it. */
private fun trimesterLabel(week: Int): String = when {
    week <= 13 -> "1-trimestr"
    week <= 27 -> "2-trimestr"
    else -> "3-trimestr"
}
