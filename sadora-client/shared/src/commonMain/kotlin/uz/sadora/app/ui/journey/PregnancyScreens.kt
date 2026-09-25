package uz.sadora.app.ui.journey

import uz.sadora.app.data.readable
import uz.sadora.app.ui.components.ErrorStrip
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
import uz.sadora.app.data.HealthController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.Mood
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SegmentedControl
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.SettingsRow
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.contract.Appointment
import uz.sadora.contract.SaveAppointmentRequest
import uz.sadora.app.i18n.JourneyStrings
import uz.sadora.app.data.toWire
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.LifeStage
import uz.sadora.contract.SymptomEntry
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.parseTypedDate
import uz.sadora.app.ui.components.parseTypedTime
import uz.sadora.app.ui.components.requiredTextError
import uz.sadora.app.ui.components.typedDateError
import uz.sadora.app.ui.components.typedTimeError
import uz.sadora.contract.Limits

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
    val t = strings.journey
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
            t.appointmentsTitle,
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
                    options = listOf(t.filterUpcoming, t.filterPast, t.filterAll),
                    selectedIndex = filter,
                    onSelect = { filter = it },
                )
            }

            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        title = if (all.isEmpty()) t.listEmpty else t.nothingInThisFilter,
                        body = t.appointmentsEmptyBody,
                        actionText = t.addAppointment,
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
                            Text(t.nextCaps, style = Sadora.type.caption, color = c.muted)
                            countdownLabel(next.scheduledOn, today, t)?.let {
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
                DisclaimerNote(t.appointmentsNote)
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
        failure = health.error?.readable(),
        onSave = { request ->
            scope.launch {
                val saved = if (sheetFor != null) health.updateAppointment(sheetFor.id, request)
                else health.addAppointment(request)
                // Closed only once it is kept: the sheet used to shut before the request
                // had gone, and an appointment entered offline was simply gone.
                if (saved) {
                    composing = false
                    editing = null
                }
            }
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
private fun countdownLabel(date: LocalDate, today: LocalDate?, t: JourneyStrings): String? {
    if (today == null) return null
    val days = today.daysUntil(date)
    return when {
        days < 0 -> null
        days == 0 -> t.todayCaps
        days == 1 -> t.tomorrowCaps
        else -> t.inDaysCaps(days)
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
    val t = strings.journey
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
            when {
                appointment.isDone -> SadoraBadge(t.appointmentDone, BadgeTone.Neutral, leading = "✓")
                appointment.remindHoursBefore != null ->
                    SadoraBadge(
                        t.reminderSet(t.reminderOffset(appointment.remindHoursBefore!!)),
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

/** The reminder offsets the sheet offers; each language says them its own way. */
private val reminderChoices = listOf<Int?>(null, 2, 24, 48)

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
    /** Why the last save did not go through, shown over the buttons while the sheet stays open. */
    failure: String? = null,
) {
    val c = Sadora.colors
    val t = strings.journey
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

    val date = parseTypedDate(day)
    val titleError = requiredTextError(title, Limits.APPOINTMENT_TITLE_MAX)
    val dateError = typedDateError(day)
    // Optional, but not "anything": an unreadable time was silently dropped before, so
    // an appointment saved with "half ten" in it lost the time without saying so.
    val timeError = typedTimeError(time, required = false)
    val placeError = requiredTextError(place, Limits.APPOINTMENT_PLACE_MAX)
    val ready = title.isNotBlank() && date != null &&
        listOf(titleError, dateError, timeError, placeError).all { it == null }

    SadoraBottomSheet(
        visible = visible,
        title = if (existing == null) t.addAppointment else t.editAppointment,
        onDismiss = onDismiss,
    ) {
        SadoraTextField(
            title,
            { title = acceptText(it, Limits.APPOINTMENT_TITLE_MAX) },
            label = t.appointmentName,
            placeholder = t.appointmentNameHint,
            error = titleError,
        )
        SadoraTextField(
            day,
            { day = acceptText(it, DateFieldMax) },
            label = t.appointmentDate,
            placeholder = t.appointmentDateHint,
            error = dateError,
        )
        SadoraTextField(
            time,
            { time = acceptText(it, TimeFieldMax) },
            label = t.appointmentTime,
            placeholder = "10:30",
            error = timeError,
        )
        SadoraTextField(
            place,
            { place = acceptText(it, Limits.APPOINTMENT_PLACE_MAX) },
            label = t.appointmentPlace,
            placeholder = t.appointmentPlaceHint,
            error = placeError,
        )

        CardLabel(t.reminder)
        ChipFlowRow {
            reminderChoices.forEach { hours ->
                SelectChip(
                    label = hours?.let { t.reminderOffset(it) } ?: t.noReminder,
                    selected = remind == hours,
                    onClick = { remind = hours },
                )
            }
        }

        if (failure != null) ErrorStrip(failure)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (onDelete != null) {
                SadoraButton(
                    strings.common.delete,
                    onClick = onDelete,
                    tone = ButtonTone.Destructive,
                    modifier = Modifier.weight(1f),
                )
            }
            SadoraButton(
                strings.common.save,
                onClick = {
                    val parsed = date ?: return@SadoraButton
                    onSave(
                        SaveAppointmentRequest(
                            title = title.trim(),
                            scheduledOn = parsed,
                            scheduledAt = parseTypedTime(time),
                            place = place.trim().takeIf { it.isNotEmpty() },
                            remindHoursBefore = remind,
                        ),
                    )
                },
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
        }
        Text(t.appointmentDateNote, style = Sadora.type.caption, color = c.muted2)
    }
}

/** `27.08.2026` and `10:30` — nothing longer is a date or a time of day. */
private const val DateFieldMax = 10
private const val TimeFieldMax = 5

/**
 * "Homiladorlik · o'zini his qilish" — the daily check-in.
 *
 * Everything on it is saved. It used to offer five symptoms written into the file, a
 * movement question and a note, and then throw all three away when "Saqlash" closed
 * the screen — which is worse than not asking, because she believes it was recorded.
 * The symptoms are now the server's own pregnancy catalogue, and the answer goes up as
 * the day's log.
 *
 * Foetal movement is the one place the app escalates: a marked drop gets an explicit
 * "see a doctor without delay" warning rather than a reassuring interpretation.
 */
@Composable
fun PregnancyCheckInScreen(
    state: AppState,
    health: HealthController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.journey
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        health.loadSymptoms(LifeStage.PREGNANCY)
        health.loadDay(state.today)
    }

    val catalogue = health.symptoms
    val today = health.day
    val chosen = remember(today) {
        mutableStateListOf<String>().apply { addAll(today?.symptoms.orEmpty().map { it.key }) }
    }
    // Both start from what the day already holds, or from nothing. They used to start at
    // "usual" and "Xotirjam", and Save on an untouched form recorded two answers she had
    // not given — one of them about whether her baby was moving.
    var movement by remember(today) { mutableStateOf(today?.fetalMovement) }
    var mood by remember(today) { mutableStateOf(state.mood.takeIf { state.moodLoggedToday }) }
    var note by remember(today) { mutableStateOf(today?.note.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                Text(t.checkInTitle, style = Sadora.type.h1, color = c.text)
            }

            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Mood.entries.forEach { option ->
                            MoodCell(
                                emoji = option.emoji,
                                label = strings.common.mood(option),
                                selected = mood == option,
                                // Held here until Save: writing the store directly changed
                                // the mood on every other screen even when she backed out.
                                onClick = { mood = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (catalogue.isNotEmpty()) {
                item {
                    SadoraCard {
                        CardLabel(t.todaysSymptomsLabel)
                        ChipFlowRow {
                            catalogue.forEach { definition ->
                                SelectChip(
                                    label = definition.label,
                                    selected = definition.key in chosen,
                                    onClick = {
                                        if (!chosen.remove(definition.key)) chosen.add(definition.key)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.babyMovement)
                    ChipFlowRow {
                        FetalMovement.entries.forEach { option ->
                            SelectChip(
                                label = t.movement(option),
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
                        Text(t.movementWarning, style = Sadora.type.body, color = c.danger)
                    }
                }
            }

            item {
                SadoraTextField(
                    note,
                    { note = it },
                    label = t.privateNote,
                    placeholder = t.privateNoteHint,
                    singleLine = false,
                )
            }

            health.error?.let { failure -> item { ErrorStrip(failure.readable()) } }

            item {
                SadoraButton(
                    if (saving) strings.common.saving else strings.common.save,
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val saved = health.saveDay(
                                date = state.today,
                                mood = mood?.toWire(),
                                symptomKeys = chosen.map { SymptomEntry(it) },
                                note = note.trim().takeIf { it.isNotEmpty() },
                                fetalMovement = movement,
                            )
                            saving = false
                            // Offline, the form stays with everything she entered and the
                            // reason above the button; it used to close as if it had saved.
                            if (saved) onClose()
                        }
                    },
                )
            }
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
