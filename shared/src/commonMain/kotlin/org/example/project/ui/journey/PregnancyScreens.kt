package org.example.project.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import org.example.project.data.HealthController
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.model.Fmt
import org.example.project.model.Mood
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.SettingsRow
import org.example.project.ui.components.noRippleClickable
import uz.sadora.contract.Appointment
import uz.sadora.contract.SaveAppointmentRequest

/**
 * "Homiladorlik · Tadbirlar".
 *
 * The app does not prescribe a screening schedule — the user fills this list
 * themselves, and the footnote says so plainly.
 */
@Composable
fun PregnancyAppointmentsScreen(
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var composing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { health.loadAppointments() }

    val today = health.cycle?.today ?: health.mind?.today
    val all = health.appointments
    val upcoming = all.filter { !it.isDone && (today == null || it.scheduledOn >= today) }
    val past = all.filter { it.isDone || (today != null && it.scheduledOn < today) }
    val shown = when (filter) {
        0 -> upcoming
        1 -> past.asReversed()
        else -> all
    }

    Column(modifier) {
        SadoraTopBar(
            "Tadbirlar",
            onBack = onClose,
            trailing = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(Radius.chip)
                        .background(c.surface2)
                        .noRippleClickable { composing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", style = Sadora.type.h2, color = c.text)
                }
            },
        )

        ScreenContent {
            item {
                SegmentedControl(
                    options = listOf("Yaqin", "O'tgan", "Barchasi"),
                    selectedIndex = filter,
                    onSelect = { filter = it },
                )
            }

            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        title = if (all.isEmpty()) "Ro'yxat bo'sh" else "Bu bo'limda tadbir yo'q",
                        body = "Shifokor ko'rigi, UTT yoki tahlil sanasini yozib qo'ying — " +
                            "eslatma ham shu yerdan sozlanadi.",
                        actionText = "Tadbir qo'shish",
                        onAction = { composing = true },
                        glyph = "🗓",
                    )
                }
            }

            // The nearest one is called out: it is the only entry she needs today.
            val next = if (filter == 0) shown.firstOrNull() else null
            if (next != null) {
                item {
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("KEYINGI", style = Sadora.type.caption, color = c.muted)
                            countdownLabel(next.scheduledOn, today)?.let {
                                Text(it, style = Sadora.type.caption, color = c.textAccent)
                            }
                        }
                        EventRow(
                            appointment = next,
                            highlighted = true,
                            onEdit = { editing = next },
                            onToggleDone = { scope.launch { health.setAppointmentDone(next.id, !next.isDone) } },
                        )
                    }
                }
            }

            val rest = if (next != null) shown.drop(1) else shown
            items(rest.size) { index ->
                val appointment = rest[index]
                SadoraCard(padding = Spacing.sm) {
                    EventRow(
                        appointment = appointment,
                        highlighted = false,
                        onEdit = { editing = appointment },
                        onToggleDone = {
                            scope.launch { health.setAppointmentDone(appointment.id, !appointment.isDone) }
                        },
                    )
                }
            }

            item {
                DisclaimerNote(
                    "Tadbirlar ro'yxatini o'zingiz to'ldirasiz. SADORA tekshiruv " +
                        "jadvalini tayinlamaydi.",
                )
            }
        }
    }

    val sheetFor = editing
    AppointmentSheet(
        visible = composing || sheetFor != null,
        existing = sheetFor,
        onDismiss = {
            composing = false
            editing = null
        },
        onSave = { request ->
            scope.launch {
                if (sheetFor != null) health.updateAppointment(sheetFor.id, request)
                else health.addAppointment(request)
            }
            composing = false
            editing = null
        },
        onDelete = sheetFor?.let { existing ->
            {
                scope.launch { health.deleteAppointment(existing.id) }
                editing = null
            }
        },
    )
}

/** "8 kundan keyin", "Ertaga", "Bugun" — how far off the next one is. */
private fun countdownLabel(date: LocalDate, today: LocalDate?): String? {
    if (today == null) return null
    val days = today.daysUntil(date)
    return when {
        days < 0 -> null
        days == 0 -> "BUGUN"
        days == 1 -> "ERTAGA"
        else -> "$days KUNDAN KEYIN"
    }
}

@Composable
private fun EventRow(
    appointment: Appointment,
    highlighted: Boolean,
    onEdit: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            Modifier
                .clip(Radius.field)
                .background(if (highlighted) c.primary.copy(alpha = 0.16f) else c.surface2)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .noRippleClickable(onClick = onToggleDone),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${appointment.scheduledOn.day}",
                style = Sadora.type.h2,
                color = if (highlighted) c.textAccent else c.text,
            )
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
            when {
                appointment.isDone -> SadoraBadge("Bo'lib o'tdi", BadgeTone.Neutral, leading = "✓")
                appointment.remindHoursBefore != null ->
                    SadoraBadge(
                        "Eslatma ${reminderLabel(appointment.remindHoursBefore!!)}",
                        BadgeTone.Neutral,
                        leading = "🔔",
                    )
            }
        }
        Box(
            Modifier.size(32.dp).noRippleClickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Text("✎", style = Sadora.type.h3, color = c.muted2)
        }
    }
}

/** The reminder offsets the sheet offers, and how they are said. */
private val reminderChoices = listOf<Int?>(null, 2, 24, 48)

private fun reminderLabel(hours: Int): String = when (hours) {
    in 0..2 -> "2 soat oldin"
    in 3..24 -> "1 kun oldin"
    else -> "2 kun oldin"
}

/**
 * Adding or editing one.
 *
 * The date is typed rather than picked from a calendar widget: the app has no date
 * picker of its own yet, and a free-text day/month is something she can complete in two
 * taps rather than none.
 */
@Composable
private fun AppointmentSheet(
    visible: Boolean,
    existing: Appointment?,
    onDismiss: () -> Unit,
    onSave: (SaveAppointmentRequest) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Sadora.colors
    // Re-keyed on the entry being edited so opening a different one refills the fields.
    var title by remember(existing, visible) { mutableStateOf(existing?.title.orEmpty()) }
    var day by remember(existing, visible) {
        mutableStateOf(existing?.scheduledOn?.let { "${it.day}.${it.month.ordinal + 1}.${it.year}" }.orEmpty())
    }
    var time by remember(existing, visible) {
        mutableStateOf(existing?.scheduledAt?.let { Fmt.clock(it) }.orEmpty())
    }
    var place by remember(existing, visible) { mutableStateOf(existing?.place.orEmpty()) }
    var remind by remember(existing, visible) { mutableStateOf(existing?.remindHoursBefore) }

    val date = parseDay(day)
    val ready = title.isNotBlank() && date != null

    SadoraBottomSheet(
        visible = visible,
        title = if (existing == null) "Tadbir qo'shish" else "Tadbirni tahrirlash",
        onDismiss = onDismiss,
    ) {
        SadoraTextField(title, { title = it }, label = "Nomi", placeholder = "Skrining UTT")
        SadoraTextField(
            day,
            { day = it },
            label = "Sana",
            placeholder = "27.8.2026",
            error = if (day.isNotBlank() && date == null) "Sana kun.oy.yil ko'rinishida" else null,
        )
        SadoraTextField(time, { time = it }, label = "Vaqti (ixtiyoriy)", placeholder = "10:30")
        SadoraTextField(place, { place = it }, label = "Joyi (ixtiyoriy)", placeholder = "Respublika markazi")

        CardLabel("Eslatma")
        ChipFlowRow {
            reminderChoices.forEach { hours ->
                SelectChip(
                    label = hours?.let { reminderLabel(it) } ?: "Kerak emas",
                    selected = remind == hours,
                    onClick = { remind = hours },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (onDelete != null) {
                SadoraButton(
                    "O'chirish",
                    onClick = onDelete,
                    tone = ButtonTone.Destructive,
                    modifier = Modifier.weight(1f),
                )
            }
            SadoraButton(
                "Saqlash",
                onClick = {
                    val parsed = date ?: return@SadoraButton
                    onSave(
                        SaveAppointmentRequest(
                            title = title.trim(),
                            scheduledOn = parsed,
                            scheduledAt = parseClock(time),
                            place = place.trim().takeIf { it.isNotEmpty() },
                            remindHoursBefore = remind,
                        ),
                    )
                },
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Sana kun.oy.yil ko'rinishida yoziladi, masalan 27.8.2026.",
            style = Sadora.type.caption,
            color = c.muted2,
        )
    }
}

/** "27.8.2026" -> a date, or null when it is not one yet. */
private fun parseDay(raw: String): LocalDate? {
    val parts = raw.trim().split('.', '/', '-').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size != 3) return null
    val (day, month, year) = parts
    if (month !in 1..12 || day !in 1..31 || year < 2000 || year > 2100) return null
    return runCatching { LocalDate(year, month, day) }.getOrNull()
}

/** "10:30" -> a time, or null when it is blank or malformed. */
private fun parseClock(raw: String): LocalTime? {
    val parts = raw.trim().split(':', '.').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size != 2) return null
    val (hour, minute) = parts
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

/**
 * "Homiladorlik · o'zini his qilish" — the daily check-in.
 *
 * Foetal movement is the one place the app escalates: a marked drop gets an explicit
 * "see a doctor without delay" warning rather than a reassuring interpretation.
 */
@Composable
fun PregnancyCheckInScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val symptoms = remember { mutableStateListOf<String>() }
    var movement by remember { mutableStateOf("Odatdagidek") }
    var note by remember { mutableStateOf("") }

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                Text(
                    "O'zingizni qanday his qilyapsiz?",
                    style = Sadora.type.h1,
                    color = c.text,
                )
            }

            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Mood.entries.forEach { mood ->
                            val label = strings.common.mood(mood)
                            MoodCell(
                                emoji = mood.emoji,
                                label = label,
                                selected = state.mood == mood,
                                onClick = { state.mood = mood },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bugungi simptomlar")
                    ChipFlowRow {
                        listOf(
                            "Belda og'riq",
                            "Ko'ngil aynishi",
                            "Shish",
                            "Nafas qisishi",
                            "Uyqusizlik",
                        ).forEach { symptom ->
                            SelectChip(
                                label = symptom,
                                selected = symptom in symptoms,
                                onClick = {
                                    if (!symptoms.remove(symptom)) symptoms.add(symptom)
                                },
                            )
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bolaning harakati")
                    ChipFlowRow {
                        listOf("Odatdagidek", "Kamroq", "Ko'proq").forEach { option ->
                            SelectChip(
                                label = option,
                                selected = movement == option,
                                onClick = { movement = option },
                            )
                        }
                    }
                    // The one escalation in the whole app.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radius.cardSmall)
                            .background(c.danger.copy(alpha = 0.12f))
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text("⚠", style = Sadora.type.h3, color = c.danger)
                        Text(
                            "Harakat sezilarli kamaysa yoki umuman sezilmasa, " +
                                "kechiktirmasdan shifokorga murojaat qiling.",
                            style = Sadora.type.body,
                            color = c.danger,
                        )
                    }
                }
            }

            item {
                SadoraTextField(
                    note,
                    { note = it },
                    label = "Izoh — faqat siz ko'rasiz",
                    placeholder = "Yozib qo'ying…",
                )
            }

            item { SadoraButton("Saqlash", onClose) }
        }
    }
}

@Composable
internal fun MoodCell(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Column(
        modifier
            .clip(Radius.field)
            .background(
                if (selected) c.primary.copy(alpha = if (c.isDark) 0.2f else 0.1f) else c.surface2,
            )
            .noRippleClickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(emoji, style = Sadora.type.h2)
        Text(
            label,
            style = Sadora.type.caption.copy(
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
            color = if (selected) c.textAccent else c.muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}
