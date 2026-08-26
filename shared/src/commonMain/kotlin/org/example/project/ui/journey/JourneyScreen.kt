package org.example.project.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.LifeStage
import org.example.project.model.SampleData
import org.example.project.nav.Route
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.LabeledProgress
import org.example.project.ui.components.PremiumGradientBadge
import org.example.project.ui.components.ProgressRing
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SectionHeader
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.WeeklyBars
import org.example.project.ui.components.noRippleClickable

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
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (state.lifeStage) {
            LifeStage.Cycle, LifeStage.TryingToConceive -> CycleJourney(state, onOpen)
            LifeStage.Pregnancy -> PregnancyJourney(state, onOpen)
            LifeStage.Postpartum -> PostpartumJourney(state, onOpen)
            LifeStage.Perimenopause -> PerimenopauseJourney(state, onOpen)
            LifeStage.Menopause -> MenopauseJourney(state, onOpen)
        }
    }
}

// ---------------------------------------------------------------- cycle

@Composable
private fun CycleJourney(state: AppState, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    SadoraTopBar(
        "Sikl",
        trailing = {
            Text(
                "Kalendar",
                style = Sadora.type.body,
                color = c.textAccent,
                modifier = Modifier.noRippleClickable { onOpen(Route.CycleCalendar) },
            )
        },
    )

    ScreenContent {
        item {
            WeekStrip(selectedIndex = 3, onDayClick = { onOpen(Route.CycleDay(it)) })
        }

        item {
            SadoraCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ProgressRing(
                        progress = state.cycleDay / state.averageCycleLength.toFloat(),
                        size = 128.dp,
                        strokeWidth = 12.dp,
                        segments = cycleSegments(state, c.primary, c.secondary, c.accent),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SIKL KUNI", style = Sadora.type.caption, color = c.muted)
                            Text("${state.cycleDay}", style = Sadora.type.data, color = c.text)
                        }
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Ovulyatsiya", style = Sadora.type.h2, color = c.text)
                        Text("Keyingi hayz — 14 kun", style = Sadora.type.body, color = c.muted)
                        SadoraBadge("TAXMINIY", BadgeTone.Estimated, leading = "◷")
                    }
                }
                PhaseLegend()
            }
        }

        item { DisclaimerNote(SampleData.predictionDisclaimer) }

        item {
            SadoraCard {
                CardLabel("Bugungi simptomlar")
                ChipFlowRow {
                    SampleData.cycleSymptoms.forEach { symptom ->
                        SelectChip(
                            label = symptom,
                            selected = symptom in state.symptoms,
                            onClick = { state.toggleSymptom(symptom) },
                        )
                    }
                    SelectChip("+ Qo'shish", selected = false, onClick = {})
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard("O'rtacha sikl", "${state.averageCycleLength} kun", Modifier.weight(1f))
                StatCard("O'rtacha hayz", "${state.averagePeriodLength} kun", Modifier.weight(1f))
            }
        }
    }
}

/** Period / follicular / fertile / luteal as four arcs of a single ring. */
private fun cycleSegments(
    state: AppState,
    period: Color,
    follicular: Color,
    fertile: Color,
): List<Pair<Float, Color>> {
    val total = state.averageCycleLength.toFloat()
    val periodDays = state.averagePeriodLength.toFloat()
    val fertileDays = 5f
    val follicularDays = 12f - periodDays
    val lutealDays = total - periodDays - follicularDays - fertileDays
    return listOf(
        periodDays / total to period,
        follicularDays / total to follicular.copy(alpha = 0.55f),
        fertileDays / total to fertile,
        lutealDays / total to follicular.copy(alpha = 0.3f),
    )
}

@Composable
private fun PhaseLegend() {
    val c = Sadora.colors
    val phases = listOf(
        "Hayz" to c.primary,
        "Follikulyar" to c.secondary.copy(alpha = 0.55f),
        "Unumdor" to c.accent,
        "Lyuteal" to c.secondary.copy(alpha = 0.3f),
    )
    ChipFlowRow(horizontalGap = Spacing.sm) {
        phases.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(color))
                Text(label, style = Sadora.type.body, color = c.muted)
            }
        }
    }
}

/** The seven-day strip above the ring. */
@Composable
private fun WeekStrip(selectedIndex: Int, onDayClick: (String) -> Unit) {
    val c = Sadora.colors
    val days = listOf("D" to 11, "S" to 12, "C" to 13, "P" to 14, "J" to 15, "Sh" to 16, "Y" to 17)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEachIndexed { index, (label, date) ->
            val selected = index == selectedIndex
            Column(
                Modifier
                    .weight(1f)
                    .clip(Radius.field)
                    .background(if (selected) c.primary else c.surface)
                    .noRippleClickable { onDayClick("$date-avgust") }
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    label,
                    style = Sadora.type.caption,
                    color = if (selected) c.onPrimary else c.muted,
                )
                Text(
                    "$date",
                    style = Sadora.type.h3,
                    color = if (selected) c.onPrimary else c.text,
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
private fun PregnancyJourney(state: AppState, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    val palette = LifeStage.Pregnancy.palette

    SadoraTopBar(
        "Homiladorlik",
        trailing = { Text("2-trimestr", style = Sadora.type.body, color = c.muted) },
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
                val onWarm = Color(0xFF2A1145)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.pregnancyWeek}", style = Sadora.type.data, color = onWarm)
                    Text(
                        "  HAFTA",
                        style = Sadora.type.caption,
                        color = onWarm.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Text("${state.pregnancyWeek}-hafta, 3-kun", style = Sadora.type.h3, color = onWarm)
                Text(
                    "Tug'ish sanasi — 12-dekabr · 112 kun qoldi",
                    style = Sadora.type.body,
                    color = onWarm.copy(alpha = 0.85f),
                )
            }
        }

        item {
            SadoraCard {
                ImagePlaceholder(
                    Modifier.fillMaxWidth().aspectRatio(2.1f),
                    colors = listOf(palette.start, palette.end),
                )
                Text("Bolaning rivojlanishi", style = Sadora.type.h3, color = c.text)
                Text(
                    "Taxminan 30 sm, 600 g. Eshitish sezgirligi ortadi.",
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
                    SelectChip("+ Qo'shish", selected = false, onClick = {})
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

        items(SampleData.appointments.size) { index ->
            val appointment = SampleData.appointments[index]
            SadoraCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(
                        Modifier
                            .clip(Radius.field)
                            .background(c.surface2)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(appointment.day, style = Sadora.type.h2, color = c.text)
                        Text(appointment.month, style = Sadora.type.caption, color = c.muted)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(appointment.title, style = Sadora.type.h3, color = c.text)
                        Text(
                            "${appointment.time} · ${appointment.who}",
                            style = Sadora.type.body,
                            color = c.muted,
                        )
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
    val onGradient = if (c.isDark) c.bg else Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(Brush.linearGradient(listOf(c.secondary, c.primary)))
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
                    .clip(RoundedCornerShape(999.dp))
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
                        Text("🙂", style = Sadora.type.h1)
                        Text("Barqaror", style = Sadora.type.h3, color = c.text)
                    }
                }
                SadoraCard(modifier = Modifier.weight(1f), padding = Spacing.sm) {
                    CardLabel("Uyqu")
                    Text("5s 10d", style = Sadora.type.h2, color = c.text)
                    Text("Bo'lingan uyqu", style = Sadora.type.body, color = c.muted)
                }
            }
        }

        item {
            SadoraCard {
                CardLabel("Emizish va suv")
                LabeledProgress("Suv", "2,1 / 3,0 L", 0.7f, color = c.accent)
                LabeledProgress("Kaloriya", "1 180 / 2 250", 0.52f)
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
            SadoraCard {
                ImagePlaceholder(Modifier.fillMaxWidth().aspectRatio(2.4f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraBadge("MAQOLA", BadgeTone.Neutral)
                    SadoraBadge("6 daqiqa", BadgeTone.Neutral)
                }
                Text(
                    "Tiklanish davrida jismoniy faollik",
                    style = Sadora.type.h3,
                    color = c.text,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- perimenopause

@Composable
private fun PerimenopauseJourney(state: AppState, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    SadoraTopBar("Perimenopauza")

    ScreenContent {
        item {
            SadoraCard {
                CardLabel(
                    "Sikl muntazamligi",
                    trailing = { Text("oxirgi 6 oy", style = Sadora.type.body, color = c.muted) },
                )
                // A regularity chart replaces prediction entirely at this stage.
                WeeklyBars(
                    values = listOf(0.62f, 0.85f, 0.5f, 0.95f, 0.42f, 0.7f),
                    labels = listOf("Mar", "Apr", "May", "Iyun", "Iyul", "Avg"),
                    color = c.secondary,
                )
                Text(
                    "Siklingiz uzunligi o'zgarib turadi — bu bosqich uchun kutilgan holat. " +
                        "Bashorat ko'rsatilmaydi.",
                    style = Sadora.type.body,
                    color = c.muted,
                )
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
                StatCard("Uyqu", "6s 05d", Modifier.weight(1f))
                StatCard("Energiya", "3,2 / 5", Modifier.weight(1f))
            }
        }

        item {
            SadoraCard(onClick = { onOpen(Route.StageSleepMood) }) {
                CardLabel("Kuzatish")
                Text(
                    "Issiqlik to'lqinlari qayd etilgan kunlarda uyqu qisqaroq bo'lgan. " +
                        "Bu holatlar ko'pincha birga kuzatilgan.",
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
                StatCard("Uyqu", "7s 10d", Modifier.weight(1f))
                StatCard("Faollik", Fmt.int(8240), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCard("Kayfiyat", "Yaxshi", Modifier.weight(1f))
                StatCard("Suv", "1,8 L", Modifier.weight(1f))
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
                    SelectChip("+ Qo'shish", selected = false, onClick = {})
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
                LabeledProgress("Kuch mashqlari", "2 / 3", 2f / 3f, color = c.secondary)
                LabeledProgress("Kalsiy va D vitamini", "5 / 7", 5f / 7f, color = c.secondary)
            }
        }
    }
}
