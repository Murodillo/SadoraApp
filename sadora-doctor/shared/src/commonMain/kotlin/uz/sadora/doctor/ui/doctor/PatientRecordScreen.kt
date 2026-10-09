package uz.sadora.doctor.ui.doctor

import uz.sadora.doctor.resources.ic3d_lock
import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.ui.components.ArtTile
import uz.sadora.doctor.resources.ic3d_baby
import uz.sadora.doctor.resources.ic3d_calendar
import uz.sadora.doctor.resources.ic3d_clock
import uz.sadora.doctor.resources.ic3d_doctor
import uz.sadora.doctor.resources.ic3d_health
import uz.sadora.doctor.resources.ic3d_heart
import uz.sadora.doctor.resources.ic3d_meds
import uz.sadora.doctor.resources.ic3d_mood
import uz.sadora.doctor.resources.ic3d_nutrition
import uz.sadora.doctor.resources.ic3d_period
import uz.sadora.doctor.resources.ic3d_pregnancy
import uz.sadora.doctor.resources.ic3d_record
import uz.sadora.doctor.resources.ic3d_sym_hot_flush
import uz.sadora.doctor.resources.ic3d_watch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Epds
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.Language
import uz.sadora.contract.SharedStageRecords
import uz.sadora.doctor.data.ApiFailure
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.attachedKey
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.Skeleton

/**
 * What a patient's QR code opens: the record her app assembles for a doctor, drawn
 * natively instead of as the web page the code also links to.
 *
 * The order is a consultation's — who she is, her cycle or pregnancy, what she has felt,
 * the last days, how she has been, what she takes, her visits, food, and the devices.
 * A section with nothing in it is left out rather than drawn empty. Nothing here is a
 * verdict, and the page says so.
 */
/** Where a record comes from: a scanned QR code, or a patient's attachment in a consultation. */
sealed interface RecordSource {
    val key: String

    data class Share(val token: String) : RecordSource {
        override val key: String get() = token
    }

    data class Attached(val conversationId: String, val messageId: String) : RecordSource {
        override val key: String get() = attachedKey(messageId)
    }
}

@Composable
fun PatientRecordScreen(
    source: RecordSource,
    doctors: DoctorController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tabs
    val scope = rememberCoroutineScope()
    val language: Language = strings.language.wire
    val calls = doctors.patientCalls

    suspend fun load() = when (source) {
        is RecordSource.Share -> doctors.openPatient(source.token, language)
        is RecordSource.Attached -> doctors.openAttachedRecord(source.conversationId, source.messageId, language)
    }

    LaunchedEffect(source, language) {
        calls.clearError()
        load()
    }
    val record = doctors.patientFor(source.key)
    val failure = calls.error

    Column(modifier) {
        SadoraTopBar(t.recordTitle, onBack = onClose)
        ScreenContent {
            if (record == null) {
                when {
                    failure is ApiFailure.NotFound -> item {
                        EmptyState(title = t.recordGone, body = t.recordGoneBody, actionText = null, onAction = {}, art = Res.drawable.ic3d_lock)
                    }
                    // An attached record closes with its consultation.
                    failure is ApiFailure.Forbidden -> item {
                        EmptyState(title = t.consultationClosed, body = t.recordClosed, actionText = null, onAction = {}, art = Res.drawable.ic3d_lock)
                    }
                    failure != null -> item {
                        ErrorStrip(failure.readable(), onRetry = { scope.launch { load() } })
                    }
                    else -> items(4) { Skeleton(Modifier.fillMaxWidth().height(110.dp), shape = Radius.card) }
                }
                return@ScreenContent
            }
            recordSections(record)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recordSections(record: DoctorSummary) {
    item(key = "person") { PersonCard(record) }

    record.cycle?.let { cycle ->
        item(key = "cycle") {
            val t = strings.tabs
            val h = cycle.history
            val facts = listOfNotNull(
                cycle.cycleDay?.let { t.cycleDay to it.toString() },
                cycle.phase?.let { t.phaseLabel to t.phase(it) },
                cycle.lastPeriodStart?.let { t.lastPeriod to fullDate(it) },
                h.averageCycleLength?.let { t.averageCycle to t.days(it) },
                h.averagePeriodLength?.let { t.averagePeriod to t.days(it) },
                if (h.shortestCycle != null && h.longestCycle != null) {
                    t.cycleRange to "${h.shortestCycle}–${h.longestCycle}"
                } else null,
                h.prediction.nextPeriodStart?.let { t.nextPeriod to "${fullDate(it)} (${t.estimated})" },
            )
            RecordSection(t.cycleTitle, Res.drawable.ic3d_period) { Facts(facts) }
        }
    }

    record.pregnancy?.let { pregnancy ->
        item(key = "pregnancy") {
            val t = strings.tabs
            val facts = listOfNotNull(
                pregnancy.week?.let { t.weekLabel to it.toString() },
                pregnancy.dueDate?.let { t.dueDate to fullDate(it) },
                pregnancy.childBirthDate?.let { t.birthDate to fullDate(it) },
            )
            RecordSection(t.pregnancyTitle, Res.drawable.ic3d_pregnancy) {
                Facts(facts)
                if (pregnancy.lessMovementDays.isNotEmpty()) {
                    Text(t.lessMovement(pregnancy.lessMovementDays.size), style = Sadora.type.body, color = Sadora.colors.dangerText)
                }
            }
        }
    }

    record.stageRecords?.let { stageSections(it) }

    if (record.symptomCounts.isNotEmpty()) {
        item(key = "symptoms") {
            val t = strings.tabs
            RecordSection(t.symptomsTitle, Res.drawable.ic3d_record, subtitle = t.symptomsWindow(DoctorSummary.SHARE_WINDOW_DAYS)) {
                Facts(record.symptomCounts.take(10).map { it.label to t.days(it.days) })
            }
        }
    }

    // The days with anything on them, the latest two weeks' worth.
    val noted = record.days.filter { it.flow != null || it.symptoms.isNotEmpty() }.take(14)
    if (noted.isNotEmpty()) {
        item(key = "days") {
            val t = strings.tabs
            RecordSection(t.recentDaysTitle, Res.drawable.ic3d_calendar) {
                Facts(
                    noted.map { day ->
                        strings.dates.dayMonth(day.date) to
                            (listOfNotNull(day.flow?.let(t::flow)) + day.symptoms.map { it.label }).joinToString(", ")
                    },
                )
            }
        }
    }

    record.mind?.takeIf { it.daysLogged > 0 }?.let { mind ->
        item(key = "mind") {
            val t = strings.tabs
            RecordSection(t.mindTitle, Res.drawable.ic3d_mood) {
                Facts(
                    listOfNotNull(
                        t.daysLogged to "${mind.daysLogged} / ${mind.windowDays}",
                        mind.averageMood?.let { t.averageMood to "${oneDecimal(it)} / 5" },
                        mind.averageEnergy?.let { t.averageEnergy to "${oneDecimal(it)} / 5" },
                        mind.averageStress?.let { t.averageStress to "${oneDecimal(it)} / 5" },
                    ),
                )
            }
        }
    }

    if (record.medications.isNotEmpty()) {
        item(key = "meds") {
            val t = strings.tabs
            val c = Sadora.colors
            RecordSection(t.medsTitle, Res.drawable.ic3d_meds) {
                record.medications.forEach { med ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            listOfNotNull(med.name, listOfNotNull(med.dosage, med.unit).joinToString(" ").ifBlank { null })
                                .joinToString(" · "),
                            style = Sadora.type.h3,
                            color = if (med.active) c.text else c.muted,
                        )
                        Text(
                            listOfNotNull(
                                fullDate(med.startedOn) + (med.endedOn?.let { " – " + fullDate(it) } ?: ""),
                                med.adherencePercent?.let(t::adherence),
                                t.medFinished.takeUnless { med.active },
                            ).joinToString(" · "),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }
        }
    }

    if (record.appointments.isNotEmpty()) {
        item(key = "appointments") {
            val t = strings.tabs
            val c = Sadora.colors
            RecordSection(t.appointmentsTitle, Res.drawable.ic3d_doctor) {
                record.appointments.forEach { visit ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(visit.title, style = Sadora.type.h3, color = c.text)
                        Text(
                            listOfNotNull(fullDate(visit.scheduledOn), visit.place).joinToString(" · "),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }
        }
    }

    record.nutrition?.takeIf { it.daysLogged > 0 }?.let { food ->
        item(key = "nutrition") {
            val t = strings.tabs
            RecordSection(t.nutritionTitle, Res.drawable.ic3d_nutrition) {
                Facts(
                    listOfNotNull(
                        t.daysLogged to "${food.daysLogged} / ${food.windowDays}",
                        food.averageKcal?.let { t.averageKcal to t.kcal(it) },
                        food.averageWaterMl?.let { t.averageWater to t.ml(it) },
                    ),
                )
            }
        }
    }

    record.wearable?.let { wearable ->
        item(key = "wearable") {
            val t = strings.tabs
            val facts = wearable.averages.mapNotNull { average ->
                t.metric(average.metric)?.let { label ->
                    label to if (average.metric == HealthMetric.SLEEP_DURATION) {
                        t.hoursMinutes(average.value.roundToInt())
                    } else {
                        listOf(metricValue(average.metric, average.value), t.unit(average.metric))
                            .filter { it.isNotEmpty() }.joinToString(" ")
                    }
                }
            }
            if (facts.isNotEmpty()) RecordSection(t.wearableTitle, Res.drawable.ic3d_watch) { Facts(facts) }
        }
    }

    item(key = "disclaimer") {
        Text(
            strings.tabs.recordDisclaimer,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = Sadora.colors.muted2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What her stage tools recorded: feeds, kick counts, contractions, hot flushes and the
 * mood questionnaire. A slow kick count, a high EPDS score and a self-harm answer are in
 * the danger colour — they are what a doctor must not scroll past.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.stageSections(r: SharedStageRecords) {
    r.feeding?.let { f ->
        item(key = "feeding") {
            val t = strings.tabs
            RecordSection(t.feedingTitle, Res.drawable.ic3d_baby, subtitle = t.lastDays(f.windowDays)) {
                Facts(
                    listOfNotNull(
                        t.feedsPerDay to oneDecimal(f.feeds.toDouble() / f.windowDays),
                        t.breastFeeds to (f.breastFeeds.toString() + (f.averageBreastMinutes?.let { " · " + t.averageMinutes(it) } ?: "")),
                        if (f.bottleFeeds > 0) t.bottleFeeds to "${f.bottleFeeds} · ${t.ml(f.bottleMl)}" else null,
                        t.lastOne to moment(f.lastAt),
                    ),
                )
            }
        }
    }
    if (r.kickCounts.isNotEmpty()) {
        item(key = "kicks") {
            val t = strings.tabs
            RecordSection(t.kicksTitle, Res.drawable.ic3d_heart, subtitle = t.lastDays(SharedStageRecords.KICK_DAYS)) {
                Facts(r.kickCounts.map { moment(it.at) to t.kicksResult(it.kicks, minutesSeconds(it.durationSeconds)) })
                if (r.kickCounts.any { it.isSlow }) Text(t.kicksSlow, style = Sadora.type.body, color = Sadora.colors.dangerText)
            }
        }
    }
    r.contractions?.let { c ->
        item(key = "contractions") {
            val t = strings.tabs
            RecordSection(t.contractionsTitle, Res.drawable.ic3d_clock, subtitle = t.lastHours(c.windowHours)) {
                Facts(
                    listOfNotNull(
                        t.count to c.count.toString(),
                        c.averageDurationSeconds?.let { t.averageLength to minutesSeconds(it) },
                        c.averageIntervalSeconds?.let { t.averageInterval to minutesSeconds(it) },
                        t.lastOne to moment(c.lastAt),
                    ),
                )
            }
        }
    }
    r.hotFlushes?.let { h ->
        item(key = "hot-flushes") {
            val t = strings.tabs
            RecordSection(t.hotFlushTitle, Res.drawable.ic3d_sym_hot_flush, subtitle = t.lastDays(h.windowDays)) {
                Facts(
                    listOf(
                        t.count to h.count.toString(),
                        t.perDay to oneDecimal(h.count.toDouble() / h.windowDays),
                        t.strong to h.strong.toString(),
                    ) + h.triggers.map { t.trigger(it.trigger) to it.count.toString() },
                )
            }
        }
    }
    if (r.moodScreens.isNotEmpty()) {
        item(key = "epds") {
            val t = strings.tabs
            val c = Sadora.colors
            RecordSection(t.epdsTitle, Res.drawable.ic3d_health) {
                if (r.moodScreens.any { it.selfHarm }) Text(t.epdsSelfHarm, style = Sadora.type.body, color = c.dangerText)
                r.moodScreens.forEach { screen ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fullDate(screen.takenOn), style = Sadora.type.body, color = c.muted)
                        Text(
                            t.epdsResult(screen.score),
                            style = Sadora.type.body,
                            color = if (screen.score >= Epds.LIKELY || screen.selfHarm) c.dangerText else c.text,
                        )
                    }
                }
            }
        }
    }
}

/** "4-oktabr 03:14" on her phone's clock. */
@Composable
private fun moment(at: kotlin.time.Instant): String {
    val local = at.toLocalDateTime(TimeZone.currentSystemDefault())
    return strings.dates.dayMonth(local.date) + " " + local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0')
}

private fun minutesSeconds(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** Her name and age, her stage of life, height and weight, and when the page was made. */
@Composable
private fun PersonCard(record: DoctorSummary) {
    val t = strings.tabs
    val c = Sadora.colors
    val person = record.person
    val generated = record.generatedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AliasAvatar(person.name, tint = person.name.hashCode(), size = 56.dp)
            Column(Modifier.weight(1f)) {
                Text(person.name, style = Sadora.type.h2, color = c.text)
                Text(
                    listOfNotNull(person.age?.let(t::age), t.lifeStage(person.lifeStage)).joinToString(" · "),
                    style = Sadora.type.body,
                    color = c.textAccent,
                )
            }
        }
        Facts(
            listOfNotNull(
                person.heightCm?.let { t.heightLabel to t.cm(it) },
                person.weightKg?.let { t.weightLabel to t.kg(it) },
            ),
        )
        Text(
            listOf(
                t.memberSince(strings.dates.monthYear(person.memberSince.year, person.memberSince.month.ordinal + 1)),
                t.generatedAt(
                    "${strings.dates.dayMonth(generated.date)}, " +
                        "${generated.hour.toString().padStart(2, '0')}:${generated.minute.toString().padStart(2, '0')}",
                ),
            ).joinToString(" · "),
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = c.muted2,
        )
    }
}

@Composable
private fun RecordSection(
    title: String,
    art: DrawableResource,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val c = Sadora.colors
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(art, size = 40.dp, artSize = 30.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = Sadora.type.h3, color = c.text)
                subtitle?.let { Text(it, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
            }
        }
        content()
    }
}

/** Label on the left, value on the right, one line each. */
@Composable
private fun Facts(facts: List<Pair<String, String>>) {
    val c = Sadora.colors
    if (facts.isEmpty()) {
        Text(strings.tabs.nothingYet, style = Sadora.type.body, color = c.muted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        facts.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(label, style = Sadora.type.body, color = c.muted, modifier = Modifier.weight(1f))
                Text(value, style = Sadora.type.body, color = c.text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "4-sentabr 2026": a day inside its month, and the year, which a record needs. */
@Composable
private fun fullDate(date: LocalDate): String = "${strings.dates.dayMonth(date)} ${date.year}"

private fun oneDecimal(value: Double): String {
    val tenths = abs((value * 10).roundToInt())
    return (if (value < 0 && tenths > 0) "-" else "") + "${tenths / 10}.${tenths % 10}"
}

/** Counts and heart rates are whole numbers; the rest keeps one decimal. */
private fun metricValue(metric: HealthMetric, value: Double): String = when (metric) {
    HealthMetric.STEPS, HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE,
    HealthMetric.SLEEP_DURATION, HealthMetric.SPO2, HealthMetric.HRV,
    -> value.roundToInt().toString()
    else -> oneDecimal(value)
}
