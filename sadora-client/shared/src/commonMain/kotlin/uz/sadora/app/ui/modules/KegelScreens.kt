package uz.sadora.app.ui.modules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import uz.sadora.app.data.KegelController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.deviceToday
import uz.sadora.app.nav.Route
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.KeepScreenOn
import uz.sadora.app.ui.components.Pulse
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraProgressBar
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.parseTypedTime
import uz.sadora.app.ui.components.rememberPulse
import uz.sadora.app.ui.components.systemReducesMotion
import uz.sadora.contract.KegelLevel
import uz.sadora.contract.KegelProgram
import uz.sadora.contract.KegelSessionResult
import uz.sadora.contract.LeakSituation
import uz.sadora.contract.Limits
import uz.sadora.contract.SaveKegelCheckRequest

private val captionStyle @Composable get() = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified)

private fun LocalTime.wire() = hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

/**
 * "Tos tubi mashqlari": her program and level, the button that starts a session, her
 * reminders, the four-weekly check, and how to do it safely.
 */
@Composable
fun KegelScreen(
    kegel: KegelController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    onUpgrade: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.kegel
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { kegel.refresh() }
    val state = kegel.state
    var checking by remember { mutableStateOf(false) }
    var addingTime by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.title, onBack = onClose)
            if (state == null) {
                Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) }
                return@Column
            }
            val playable = if (state.advanced) state.unlockedLevel else minOf(state.unlockedLevel, Limits.KEGEL_FREE_LEVELS)
            val chosen = kegel.selectedLevel?.takeIf { it in 1..state.levels.size } ?: playable
            val level = state.levels.getOrNull(chosen - 1)
            ScreenContent {
                kegel.error?.let { failure -> item { ErrorStrip(failure.readable(), onRetry = kegel::clearError) } }
                if (state.postpartumEarly) {
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                SadoraBadge("!", BadgeTone.Warning)
                                Text(t.postpartumEarlyTitle, style = Sadora.type.h3, color = c.text)
                            }
                            Text(t.postpartumEarlyBody, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }

                // ---- the hero: level, today, streak, start
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ArtTile(Res.drawable.ic3d_kegel, tint = c.primary, size = 56.dp)
                            Column(Modifier.weight(1f)) {
                                Text(t.program(state.program), style = captionStyle, color = c.muted)
                                Text(t.level(chosen), style = Sadora.type.h2, color = c.text)
                            }
                        }
                        level?.let { Text(t.levelDetail(it.holdSeconds, it.relaxSeconds, it.reps, it.sets, (it.durationSeconds + 59) / 60), style = Sadora.type.body, color = c.muted) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t.today(state.todaySessions, state.dailyGoal), style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                            if (state.streakDays > 0) Text("🔥 " + t.streak(state.streakDays), style = Sadora.type.body, color = c.textAccent)
                        }
                        SadoraProgressBar((state.todaySessions.toFloat() / state.dailyGoal).coerceIn(0f, 1f), gradient = true)
                        val premiumLocked = level?.premium == true && !state.advanced
                        SadoraButton(
                            if (premiumLocked) t.premium + " · " + t.level(chosen) else t.start,
                            onClick = {
                                if (premiumLocked) {
                                    onUpgrade()
                                } else {
                                    kegel.selectedLevel = chosen
                                    onOpen(Route.KegelSession)
                                }
                            },
                        )
                        Text(t.eyesClosed, style = captionStyle, color = c.muted)
                    }
                }

                // ---- levels
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        ChipFlowRow {
                            state.levels.forEach { l ->
                                val open = l.level <= state.unlockedLevel
                                SelectChip(
                                    t.level(l.level) + when {
                                        !open -> " 🔒"
                                        l.premium && !state.advanced -> " ★"
                                        else -> ""
                                    },
                                    selected = l.level == chosen,
                                    onClick = { if (open) kegel.selectedLevel = l.level },
                                )
                            }
                        }
                        if (state.unlockedLevel < state.levels.size) {
                            Text(t.nextLevel(state.daysAtLevel, state.daysToUnlock), style = Sadora.type.body, color = c.muted)
                            SadoraProgressBar((state.daysAtLevel.toFloat() / state.daysToUnlock).coerceIn(0f, 1f))
                        } else {
                            Text(t.allLevels, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }

                // ---- the last four weeks
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        CardLabel(t.lastWeeks)
                        WeeksStrip(state.recentDays.toSet())
                    }
                }

                // ---- the check
                if (state.checkDue && !kegel.checkSnoozed) {
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            Text(t.checkTitle, style = Sadora.type.h3, color = c.text)
                            Text(t.checkNote, style = Sadora.type.body, color = c.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                SadoraButton(t.checkStart, onClick = { checking = true }, modifier = Modifier.weight(1f))
                                SadoraButton(t.later, onClick = kegel::snoozeCheck, tone = ButtonTone.Secondary, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (state.checks.isNotEmpty()) {
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                            CardLabel(t.checksHistory)
                            state.checks.take(6).forEach { check ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(strings.dates.dayMonth(check.takenOn), style = Sadora.type.body, color = c.muted)
                                    Text(t.frequency(check.frequency), style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
                                    Text(t.bother(check.bother), style = captionStyle, color = c.muted)
                                }
                            }
                        }
                    }
                }

                // ---- reminders
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        CardLabel(t.remindersTitle)
                        if (state.reminders.isEmpty()) Text(t.noReminders, style = Sadora.type.body, color = c.muted)
                        ChipFlowRow {
                            state.reminders.forEach { time ->
                                SelectChip(time.wire() + "  ✕", selected = false, onClick = {
                                    scope.launch {
                                        if (kegel.saveSettings(state.program.takeIf { it != state.stageProgram }, state.reminders - time)) onToast(t.saved)
                                    }
                                })
                            }
                        }
                        if (state.reminders.size < Limits.KEGEL_MAX_REMINDERS) {
                            SadoraButton(t.addReminder, onClick = { addingTime = true }, tone = ButtonTone.Secondary)
                        }
                        Text(t.remindersNote, style = captionStyle, color = c.muted)
                    }
                }

                // ---- program
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        CardLabel(t.programLabel)
                        ChipFlowRow {
                            KegelProgram.entries.forEach { program ->
                                SelectChip(
                                    t.program(program) + if (program == state.stageProgram) " · " + t.yourStage else "",
                                    selected = program == state.program,
                                    onClick = {
                                        if (program != state.program) {
                                            kegel.selectedLevel = null
                                            scope.launch { kegel.saveSettings(program.takeIf { it != state.stageProgram }, state.reminders) }
                                        }
                                    },
                                )
                            }
                        }
                        Text(t.programNote(state.program), style = Sadora.type.body, color = c.muted)
                    }
                }

                // ---- how, and when to stop
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        Text(t.howTitle, style = Sadora.type.h3, color = c.text)
                        t.howSteps.forEachIndexed { i, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text("${i + 1}.", style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.textAccent)
                                Text(step, style = Sadora.type.body, color = c.text)
                            }
                        }
                    }
                }
                item { DisclaimerNote(t.safety) }
            }
        }

        SadoraBottomSheet(visible = checking, title = t.checkTitle, onDismiss = { checking = false }) {
            KegelCheckForm(
                busy = kegel.busy,
                onSave = { request ->
                    scope.launch {
                        if (kegel.saveCheck(request)) {
                            checking = false
                            onToast(t.checkSaved)
                        }
                    }
                },
            )
        }

        SadoraBottomSheet(visible = addingTime, title = t.addReminder, onDismiss = { addingTime = false }) {
            val current = state
            var typed by remember { mutableStateOf("") }
            val presets = listOf(LocalTime(8, 0), LocalTime(13, 0), LocalTime(20, 0), LocalTime(21, 30))
            val add = { time: LocalTime ->
                if (current != null) {
                    scope.launch {
                        if (kegel.saveSettings(current.program.takeIf { it != current.stageProgram }, (current.reminders + time).distinct())) {
                            addingTime = false
                            onToast(t.saved)
                        }
                    }
                }
            }
            ChipFlowRow {
                presets.filterNot { current?.reminders?.contains(it) == true }.forEach { time ->
                    SelectChip(time.wire(), selected = false, onClick = { add(time) })
                }
            }
            SadoraTextField(typed, { typed = it }, label = t.reminderTime, placeholder = "07:30")
            SadoraButton(t.add, onClick = { parseTypedTime(typed)?.let(add) }, enabled = parseTypedTime(typed) != null)
        }
    }
}

/** Four rows of seven: the days with a session lit, today ringed. */
@Composable
private fun WeeksStrip(days: Set<kotlinx.datetime.LocalDate>) {
    val c = Sadora.colors
    val today = deviceToday()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (3 downTo 0).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (6 downTo 0).forEach { d ->
                    val day = today.minus(week * 7 + d, DateTimeUnit.DAY)
                    val done = day in days
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (done) c.primary else c.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day == today) Box(Modifier.size(8.dp).clip(CircleShape).background(if (done) c.onPrimary else c.primary))
                    }
                }
            }
        }
    }
}

/** The four-weekly check: how often, how much, when, and how much it gets in the way. */
@Composable
private fun KegelCheckForm(busy: Boolean, onSave: (SaveKegelCheckRequest) -> Unit) {
    val t = strings.kegel
    val c = Sadora.colors
    var frequency by remember { mutableStateOf<Int?>(null) }
    var amount by remember { mutableIntStateOf(1) }
    var situations by remember { mutableStateOf(emptySet<LeakSituation>()) }
    var bother by remember { mutableStateOf<Int?>(null) }

    Text(t.qFrequency, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
    ChipFlowRow { (0..4).forEach { v -> SelectChip(t.frequency(v), selected = frequency == v, onClick = { frequency = v }) } }
    if ((frequency ?: 0) > 0) {
        Text(t.qAmount, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
        ChipFlowRow { (1..3).forEach { v -> SelectChip(t.amount(v), selected = amount == v, onClick = { amount = v }) } }
        Text(t.qWhen, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
        ChipFlowRow {
            LeakSituation.entries.forEach { s ->
                SelectChip(t.situation(s), selected = s in situations, onClick = { situations = if (s in situations) situations - s else situations + s })
            }
        }
    }
    Text(t.qBother, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
    ChipFlowRow { (0..10).forEach { v -> SelectChip(v.toString(), selected = bother == v, onClick = { bother = v }) } }
    Text(t.botherScale, style = captionStyle, color = c.muted)
    Spacer(Modifier.height(Spacing.xs))
    val f = frequency
    val b = bother
    SadoraButton(
        t.save,
        enabled = f != null && b != null && !busy,
        onClick = {
            if (f != null && b != null) {
                onSave(SaveKegelCheckRequest(f, if (f == 0) 0 else amount, if (f == 0) emptyList() else LeakSituation.entries.filter { it in situations }, b))
            }
        },
    )
}

// ---------------------------------------------------------------- the session

/** One step of a session: what to do, for how long, and which squeeze it counts as. */
internal data class KegelStep(val kind: Kind, val seconds: Int, val rep: Int = 0, val set: Int = 0) {
    enum class Kind { Ready, Squeeze, Relax, Flick, FlickRelax, Rest }
}

/** The level laid out: get ready, then each set's holds, its quick flicks, and a rest between sets. */
internal fun KegelLevel.steps(): List<KegelStep> = buildList {
    add(KegelStep(KegelStep.Kind.Ready, READY_SECONDS))
    for (set in 1..sets) {
        for (rep in 1..reps) {
            add(KegelStep(KegelStep.Kind.Squeeze, holdSeconds, rep, set))
            add(KegelStep(KegelStep.Kind.Relax, relaxSeconds, rep, set))
        }
        for (flick in 1..quickFlicks) {
            add(KegelStep(KegelStep.Kind.Flick, 1, flick, set))
            add(KegelStep(KegelStep.Kind.FlickRelax, 1, flick, set))
        }
        if (set < sets) add(KegelStep(KegelStep.Kind.Rest, KegelLevel.SET_REST_SECONDS, 0, set))
    }
}

private const val READY_SECONDS = 3

/**
 * The guided session: a clay circle that draws in on "Qising" and opens on "Bo'shating",
 * the seconds counting down, and a buzz at each change so she can do it with her eyes shut.
 * The screen stays on until she finishes or leaves.
 */
@Composable
fun KegelSessionScreen(
    kegel: KegelController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.kegel
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val pulse = rememberPulse()
    val reduceMotion = systemReducesMotion()
    KeepScreenOn()

    val state = kegel.state
    val levelNumber = kegel.selectedLevel ?: state?.unlockedLevel ?: 1
    val level = state?.levels?.getOrNull(levelNumber - 1)
    if (state == null || level == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val steps = remember(level) { level.steps() }
    val totalSeconds = remember(steps) { steps.sumOf { it.seconds } }
    var index by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(steps.first().seconds) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var squeezes by remember { mutableIntStateOf(0) }
    var askStop by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<KegelSessionResult?>(null) }
    var finished by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }

    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            pulse(Pulse.Done)
            if (squeezes > 0) {
                scope.launch { result = kegel.finish(state.program, levelNumber, squeezes, elapsed.coerceAtLeast(1)) ?: KegelSessionResult(state) }
            } else {
                onClose()
            }
        }
    }

    // The clock: a tick a second, a buzz and a new circle target at each step.
    LaunchedEffect(steps) {
        while (index < steps.size) {
            val step = steps[index]
            when (step.kind) {
                KegelStep.Kind.Squeeze, KegelStep.Kind.Flick -> pulse(Pulse.Squeeze)
                KegelStep.Kind.Relax, KegelStep.Kind.FlickRelax -> pulse(Pulse.Relax)
                else -> Unit
            }
            if (!reduceMotion) {
                val target = when (step.kind) {
                    KegelStep.Kind.Squeeze, KegelStep.Kind.Flick -> 0.62f
                    else -> 1f
                }
                launch { scale.animateTo(target, tween(durationMillis = (minOf(step.seconds, 2) * 600), easing = LinearEasing)) }
            }
            left = step.seconds
            while (left > 0) {
                delay(1000)
                if (!paused && !finished) {
                    left -= 1
                    elapsed += 1
                }
                if (finished) return@LaunchedEffect
            }
            if (step.kind == KegelStep.Kind.Squeeze || step.kind == KegelStep.Kind.Flick) squeezes += 1
            index += 1
        }
        finish()
    }

    SystemBackHandler(enabled = !finished) { if (squeezes > 0) askStop = true else onClose() }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(c.heroColors.map { it.copy(alpha = 0.18f) })).navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.level(levelNumber), onBack = { if (squeezes > 0 && !finished) askStop = true else onClose() })
            val step = steps.getOrNull(index) ?: steps.last()
            val word = when (step.kind) {
                KegelStep.Kind.Ready -> t.getReady
                KegelStep.Kind.Squeeze -> t.squeeze
                KegelStep.Kind.Relax, KegelStep.Kind.FlickRelax -> t.relax
                KegelStep.Kind.Flick -> t.flick
                KegelStep.Kind.Rest -> t.rest
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                    val primary = c.primary
                    val accent = c.heroColors.last()
                    Canvas(Modifier.size(260.dp).scale(scale.value)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.95f), primary.copy(alpha = 0.85f), primary.copy(alpha = 0.55f)),
                                center = Offset(size.width * 0.38f, size.height * 0.34f),
                                radius = size.minDimension * 0.7f,
                            ),
                        )
                        drawCircle(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f), radius = size.minDimension * 0.07f, center = Offset(size.width * 0.27f, size.height * 0.24f))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(word, style = Sadora.type.h2, color = c.onPrimary, textAlign = TextAlign.Center)
                        Text(left.toString(), style = Sadora.type.h1.copy(fontSize = 56.sp), color = c.onPrimary)
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                if (step.kind != KegelStep.Kind.Ready && step.rep > 0) {
                    val of = if (step.kind == KegelStep.Kind.Flick || step.kind == KegelStep.Kind.FlickRelax) level.quickFlicks else level.reps
                    Text("${step.rep} / $of", style = Sadora.type.h3, color = c.text)
                }
                if (step.set > 0) Text(t.set(step.set, level.sets), style = Sadora.type.body, color = c.muted)
                Spacer(Modifier.height(Spacing.sm))
                SadoraProgressBar((elapsed.toFloat() / totalSeconds).coerceIn(0f, 1f), gradient = true)
            }
            Row(
                Modifier.fillMaxWidth().padding(Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SadoraButton(if (paused) t.resume else t.pause, onClick = { paused = !paused }, tone = ButtonTone.Secondary, modifier = Modifier.weight(1f))
                SadoraButton(t.stop, onClick = { if (squeezes > 0) askStop = true else onClose() }, modifier = Modifier.weight(1f))
            }
        }

        SadoraDialog(
            visible = askStop,
            title = t.stopTitle,
            body = t.stopBody,
            confirmText = t.stop,
            destructive = false,
            onConfirm = {
                askStop = false
                finish()
            },
            onDismiss = { askStop = false },
        )

        SadoraBottomSheet(visible = result != null, title = t.doneTitle, onDismiss = onClose) {
            val done = result
            Text(t.doneBody(squeezes, (elapsed + 59) / 60), style = Sadora.type.body, color = c.text)
            done?.coins?.let { Text(t.coins(it.amount), style = Sadora.type.h3, color = c.textAccent) }
            done?.unlockedLevel?.let { Text("🎉 " + t.levelOpened(it), style = Sadora.type.h3, color = c.text) }
            done?.state?.let { Text(t.today(it.todaySessions, it.dailyGoal), style = Sadora.type.body, color = c.muted) }
            Spacer(Modifier.height(Spacing.xs))
            SadoraButton(t.close, onClick = onClose)
        }
    }
}
