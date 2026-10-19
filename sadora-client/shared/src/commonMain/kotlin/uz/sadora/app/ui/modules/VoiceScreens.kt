package uz.sadora.app.ui.modules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.app.data.VoiceController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.rememberVoiceRecorder
import uz.sadora.app.ui.components.systemReducesMotion
import uz.sadora.contract.Limits
import uz.sadora.contract.SaveVoiceLogRequest
import uz.sadora.contract.VoiceLogDraft

private enum class VoiceStage { Idle, Recording, Hearing, Draft }

/**
 * "Ovoz bilan belgilash": a tap starts the microphone, a tap ends it (or a minute does), the
 * server hears it, and she sees each thing it understood as a chip she can take away before
 * saving. Nothing reaches her diary until she presses Save.
 */
@Composable
fun VoiceLogScreen(
    voice: VoiceController,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    onSaved: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.voice
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val recorder = rememberVoiceRecorder()
    val reduceMotion = systemReducesMotion()
    LaunchedEffect(Unit) { voice.refresh() }
    var stage by remember { mutableStateOf(VoiceStage.Idle) }
    var seconds by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0f) }
    var draft by remember { mutableStateOf<VoiceLogDraft?>(null) }

    val finish: () -> Unit = {
        val audio = recorder.stop()
        if (audio == null) {
            stage = VoiceStage.Idle
        } else {
            stage = VoiceStage.Hearing
            scope.launch {
                val heard = voice.hear(audio.bytes, audio.mimeType)
                draft = heard
                stage = if (heard != null) VoiceStage.Draft else VoiceStage.Idle
            }
        }
    }

    // While recording: the seconds, the loudness for the circle, and the minute's end.
    LaunchedEffect(stage) {
        if (stage != VoiceStage.Recording) return@LaunchedEffect
        seconds = 0
        var ticks = 0
        while (stage == VoiceStage.Recording) {
            delay(100)
            level = recorder.level().coerceIn(0f, 1f)
            ticks += 1
            if (ticks % 10 == 0) seconds += 1
            if (seconds >= Limits.VOICE_MAX_SECONDS) finish()
        }
    }
    val pulse by animateFloatAsState(if (reduceMotion) 1f else 1f + level * 0.35f, tween(120), label = "level")

    Box(modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.title, onBack = {
                recorder.cancel()
                onClose()
            })
            ScreenContent {
                voice.error?.let { failure -> item { ErrorStrip(failure.readable(), onRetry = voice::clearError) } }
                val options = voice.options
                when {
                    options != null && !options.available -> item { Text(t.unavailable, style = Sadora.type.body, color = c.muted) }
                    voice.locked && stage != VoiceStage.Draft -> item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            Text(t.lockedTitle, style = Sadora.type.h3, color = c.text)
                            Text(t.lockedBody, style = Sadora.type.body, color = c.muted)
                            SadoraButton(t.upgrade, onClick = onUpgrade)
                        }
                    }
                    stage == VoiceStage.Draft && draft != null -> item {
                        DraftCard(
                            draft = draft!!,
                            busy = voice.busy,
                            onSave = { kept ->
                                scope.launch {
                                    if (voice.save(kept)) {
                                        onToast(t.saved)
                                        onSaved()
                                        onClose()
                                    }
                                }
                            },
                            onAgain = {
                                draft = null
                                stage = VoiceStage.Idle
                            },
                        )
                    }
                    else -> {
                        item { Text(t.hint, style = Sadora.type.body, color = c.muted) }
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Box(
                                    Modifier
                                        .size(150.dp)
                                        .scale(if (stage == VoiceStage.Recording) pulse else 1f)
                                        .clip(CircleShape)
                                        .background(if (stage == VoiceStage.Recording) Color(0xFFE0529C) else c.primary)
                                        .noRippleClickable(role = Role.Button, onClick = {
                                            when (stage) {
                                                VoiceStage.Idle -> recorder.start { ok -> if (ok) stage = VoiceStage.Recording }
                                                VoiceStage.Recording -> finish()
                                                else -> Unit
                                            }
                                        }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (stage == VoiceStage.Hearing) {
                                        SadoraLoader(size = 56.dp)
                                    } else {
                                        Icon(SadoraIcons.Mic, contentDescription = t.title, tint = c.onPrimary, modifier = Modifier.size(64.dp))
                                    }
                                }
                                Text(
                                    when (stage) {
                                        VoiceStage.Recording -> "0:" + seconds.toString().padStart(2, '0') + " · " + t.tapToStop
                                        VoiceStage.Hearing -> t.listening
                                        else -> t.tapToSpeak
                                    },
                                    style = Sadora.type.h3,
                                    color = c.text,
                                    textAlign = TextAlign.Center,
                                )
                                options?.allowance?.let { allowance ->
                                    Text(
                                        allowance.remainingToday?.let(t::allowance) ?: t.unlimited,
                                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                                        color = c.muted,
                                    )
                                }
                            }
                        }
                    }
                }
                item { DisclaimerNote(t.privacy) }
            }
        }
    }
}

/** What was heard, as chips she can untick; Save writes only the ticked ones. */
@Composable
private fun DraftCard(draft: VoiceLogDraft, busy: Boolean, onSave: (SaveVoiceLogRequest) -> Unit, onAgain: () -> Unit) {
    val t = strings.voice
    val c = Sadora.colors
    var mood by remember(draft) { mutableStateOf(draft.mood) }
    var symptoms by remember(draft) { mutableStateOf(draft.symptoms) }
    var water by remember(draft) { mutableStateOf(draft.waterMl) }
    var doses by remember(draft) { mutableStateOf(draft.doses) }
    var period by remember(draft) { mutableStateOf(draft.period) }
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        if (draft.transcript.isNotBlank()) {
            CardLabel(t.heard)
            Text("«${draft.transcript}»", style = Sadora.type.body, color = c.muted)
        }
        if (!draft.understood) {
            Text(t.notUnderstood, style = Sadora.type.h3, color = c.text)
            SadoraButton(t.again, onClick = onAgain)
            return@SadoraCard
        }
        CardLabel(t.understood)
        ChipFlowRow {
            draft.mood?.let { m -> SelectChip(t.mood(m), selected = mood != null, onClick = { mood = if (mood == null) m else null }) }
            draft.symptoms.forEach { s -> SelectChip(s.label, selected = s in symptoms, onClick = { symptoms = if (s in symptoms) symptoms - s else symptoms + s }) }
            draft.waterMl?.let { ml -> SelectChip(t.water(ml), selected = water != null, onClick = { water = if (water == null) ml else null }) }
            draft.doses.forEach { d ->
                val time = d.dueAt.hour.toString().padStart(2, '0') + ":" + d.dueAt.minute.toString().padStart(2, '0')
                SelectChip(t.dose(d.name, time), selected = d in doses, onClick = { doses = if (d in doses) doses - d else doses + d })
            }
            draft.period?.let { p -> SelectChip(t.period(p), selected = period != null, onClick = { period = if (period == null) p else null }) }
        }
        val anything = mood != null || symptoms.isNotEmpty() || water != null || doses.isNotEmpty() || period != null
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(
                t.save,
                enabled = anything && !busy,
                onClick = { onSave(SaveVoiceLogRequest(mood, symptoms.map { it.key }, water, doses, period)) },
                modifier = Modifier.weight(1f),
            )
            SadoraButton(t.again, onClick = onAgain, tone = ButtonTone.Secondary, modifier = Modifier.weight(1f))
        }
    }
}
