package uz.sadora.app.ui.journey

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.data.readable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import uz.sadora.app.data.HealthController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.LifeStage
import uz.sadora.app.model.Fmt
import uz.sadora.app.data.toAppMood
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.contract.DailyLog
import uz.sadora.app.resources.*
import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.app.ui.components.ArtIcon
import uz.sadora.app.ui.components.art

/**
 * "Sikl · kun tafsiloti" — everything recorded for one day.
 *
 * [date] is the ISO date the calendar tapped. The day is read from the server rather
 * than from the store, because the store only ever holds today: before this the screen
 * told anyone who opened last Tuesday that nothing had been logged, however much she
 * had written that day. It is read without applying it, so opening a past day does not
 * replace today's entries everywhere else in the app.
 *
 * Device-sourced figures are grouped separately and carry their source badge, so it
 * is always clear what the user entered and what a wearable supplied.
 */
@Composable
fun CycleDayScreen(
    state: AppState,
    health: HealthController,
    date: String,
    /** Opens the symptom sheet for the day this page shows, not for today. */
    onOpenSymptomSheet: (LocalDate) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** True while that sheet is up; the record is read again when it closes. */
    sheetOpen: Boolean = false,
) {
    val c = Sadora.colors
    val t = strings.journey
    val common = strings.common
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: state.today
    val isToday = day == state.today
    // Only where there is a forecast. In perimenopause or after a birth this page is
    // reached to record a period, and the store's default — day 14 — put "ovulation"
    // over it for a woman nothing had been predicted for.
    val forecast = state.lifeStage.predictsCycle && state.hasCyclePrediction
    val cycleDay = if (!forecast) null else if (isToday) state.cycleDay else state.cycleDayFor(day)
    val phase = if (!forecast) null else if (isToday) state.currentPhase() else state.phaseForDate(day)

    var log by remember(day) { mutableStateOf<DailyLog?>(null) }
    // Read again when the sheet closes: the card under it kept showing the record from
    // before the edit.
    LaunchedEffect(day, sheetOpen) {
        if (sheetOpen) return@LaunchedEffect
        health.loadSymptoms(null)
        log = health.dayAt(day)
    }
    val entry = log
    val scope = rememberCoroutineScope()
    // Nothing can be recorded about a day that has not happened.
    val editable = day <= state.today
    // The period that is still running, if this day falls inside it.
    val openPeriod = health.cycle?.currentPeriod?.takeIf { it.isOngoing && day >= it.startedOn }
    var savingPeriod by remember { mutableStateOf(false) }

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.dates.dayMonthWeekday(day), style = Sadora.type.h1, color = c.text)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (cycleDay != null) {
                            Text(t.cycleDayOrdinal(cycleDay), style = Sadora.type.body, color = c.muted)
                        }
                        if (phase != null) SadoraBadge(common.phase(phase), BadgeTone.Estimated)
                    }
                }
            }

            if (cycleDay != null && phase != null) {
                item {
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t.cycleDayCaps, style = Sadora.type.caption, color = c.muted)
                                Text("$cycleDay", style = Sadora.type.data, color = c.text)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(common.phase(phase), style = Sadora.type.h2, color = c.text)
                                Text(common.phaseEnergy(phase), style = Sadora.type.body, color = c.muted)
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(if (isToday) t.loggedToday else t.logged)
                    if (entry == null || entry.isEmpty) {
                        Text(
                            if (isToday) t.noSymptomsLogged else t.nothingLoggedForDay,
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    } else {
                        entry.symptoms.forEach { symptom ->
                            LoggedLine("•", health.symptoms.labelFor(symptom.key), symptomArt(symptom.key))
                        }
                        entry.mood?.let { level ->
                            val mood = level.toAppMood()
                            LoggedLine(mood.emoji, t.moodLine(common.mood(mood).lowercase()), mood.art())
                        }
                        entry.energy?.let { LoggedLine("⚡", t.energyLine(it), Res.drawable.ic3d_energy) }
                        entry.note?.takeIf { it.isNotBlank() }?.let { LoggedLine("📝", it, Res.drawable.ic3d_notebook) }
                    }
                }
            }

            if (isToday) {
                item {
                    // Wearable data is kept visually distinct from self-reported entries.
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text("⌚", style = Sadora.type.h3)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.sleepAndSteps(
                                        state.sleepLabel(format = common::hoursMinutes),
                                        state.stepsLabel(),
                                    ),
                                    style = Sadora.type.h3,
                                    color = c.text,
                                )
                            }
                        }
                        SadoraBadge(t.fromDevice, BadgeTone.Connected)
                    }
                }
            }

            // The one thing the cycle is predicted from. After onboarding nothing in the
            // app could record it: "Hayzni belgilash" led here, and here there were only
            // symptoms. Also in perimenopause, whose regularity chart is drawn from these,
            // and after a birth, for the first period coming back — neither predicts.
            if (editable && state.lifeStage.recordsPeriods) {
                item {
                    // After menopause it is not a period but bleeding, and the card says
                    // what to do about it.
                    val menopause = state.lifeStage == LifeStage.Menopause
                    SadoraCard {
                        Text(if (menopause) strings.tools.menopauseBleedingTitle else t.periodCardTitle, style = Sadora.type.h3, color = c.text)
                        Text(
                            if (openPeriod != null) {
                                t.periodRunningSince(strings.dates.dayMonth(openPeriod.startedOn))
                            } else if (menopause) {
                                strings.tools.menopauseBleedingBody
                            } else if (state.lifeStage.predictsCycle) {
                                t.periodCardBody
                            } else {
                                t.periodCardBodyNoForecast
                            },
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                        health.error?.let { ErrorStrip(it.readable()) }
                        SadoraButton(
                            when {
                                savingPeriod -> strings.common.saving
                                openPeriod != null -> t.periodEndedThisDay
                                menopause -> strings.tools.menopauseBleedingButton
                                else -> t.periodStartedThisDay
                            },
                            onClick = {
                                savingPeriod = true
                                scope.launch {
                                    if (openPeriod != null) health.endPeriod(openPeriod.id, day)
                                    else health.logPeriodStart(day)
                                    savingPeriod = false
                                }
                            },
                            enabled = !savingPeriod,
                            tone = if (openPeriod != null) ButtonTone.Secondary else ButtonTone.Primary,
                        )
                    }
                }
            }

            if (editable) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SadoraButton(
                            t.editEntry,
                            onClick = { onOpenSymptomSheet(day) },
                            tone = ButtonTone.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                        SadoraButton(
                            t.addSymptom,
                            onClick = { onOpenSymptomSheet(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggedLine(emoji: String, text: String, art: DrawableResource? = null) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.clip(Radius.chip).background(c.surface2).padding(if (art != null) 4.dp else 6.dp),
        ) {
            if (art != null) ArtIcon(art, 24.dp) else Text(emoji, style = Sadora.type.body)
        }
        Text(text, style = Sadora.type.body, color = c.text)
    }
}
