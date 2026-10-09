package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.deviceToday
import uz.sadora.app.resources.Res
import uz.sadora.app.resources.ic3d_meds
import uz.sadora.app.ui.components.ArtIcon
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CircleIconButton
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.encodePng
import uz.sadora.app.ui.components.noRippleToggleable
import uz.sadora.app.ui.components.rememberImageShareAction
import uz.sadora.contract.AddPrescriptionItem
import uz.sadora.contract.AddPrescriptionRequest
import uz.sadora.contract.Prescription

/**
 * A doctor's prescription as she reads it: the doctor and the day, each medicine with
 * how to take it, the doctor's note, and the line saying it is advice rather than a
 * pharmacy document.
 *
 * The document part is drawn into a layer as well as onto the screen, so "Rasm sifatida
 * ulashish" hands over exactly what she sees — without the buttons under it.
 */
@Composable
fun PrescriptionCard(
    prescription: Prescription,
    modifier: Modifier = Modifier,
    /** Shown while it can still be added; null hides the button. */
    onAdd: (() -> Unit)? = null,
) {
    val p = strings.prescriptions
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val share = rememberImageShareAction()
    val layer = rememberGraphicsLayer()
    val cancelled = prescription.cancelledAt != null

    Column(
        modifier
            .widthIn(max = 340.dp)
            .clip(Radius.card)
            .background(c.surface),
    ) {
        Column(
            Modifier
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .background(c.surface)
                .padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.size(40.dp).clip(Radius.chip).background(c.primary.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    ArtIcon(Res.drawable.ic3d_meds, 30.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = Sadora.type.h3, color = c.text)
                    Text(
                        p.byline(prescription.doctor.fullName, dayOf(prescription)),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted,
                    )
                }
            }
            prescription.items.forEachIndexed { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${index + 1}. ${item.name}",
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.text,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    )
                    Text(p.summary(item), style = Sadora.type.body, color = c.muted)
                    item.note?.let { Text(it, style = Sadora.type.body, color = c.muted2) }
                }
            }
            prescription.note?.let {
                Text(
                    it,
                    style = Sadora.type.body,
                    color = c.text,
                    modifier = Modifier.fillMaxWidth().clip(Radius.cardSmall).background(c.surface2).padding(Spacing.xs),
                )
            }
            if (cancelled) {
                Text(p.cancelled, style = Sadora.type.h3, color = c.danger)
                prescription.cancelReason?.let { Text(p.cancelReason(it), style = Sadora.type.body, color = c.muted) }
            }
            Text(p.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
        }

        Column(
            Modifier.padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            when {
                cancelled -> Unit
                prescription.addedAt != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(16.dp), tint = c.success)
                    Text(p.added, style = Sadora.type.body, color = c.success)
                }
                onAdd != null -> SadoraButton(p.addToPills, onClick = onAdd)
            }
            PillButton(
                p.shareImage,
                onClick = {
                    scope.launch {
                        val png = layer.toImageBitmap().encodePng()
                        if (png.isNotEmpty()) share(png, "retsept-${prescription.id.take(8)}.png")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Adding a prescription to her medications: every medicine ticked to start with, its
 * times movable half an hour a tap, and the day she starts — today unless she says
 * otherwise. The dose and the length of the course are the doctor's and are only shown.
 */
@Composable
fun AddPrescriptionSheet(
    prescription: Prescription?,
    health: HealthController,
    onAdded: (Prescription) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = strings.prescriptions
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val key = prescription?.id
    val chosen = remember(key) { mutableStateMapOf<Int, Boolean>() }
    val times = remember(key) { mutableStateMapOf<Int, List<Int>>() }
    var startOffset by remember(key) { mutableStateOf(0) }
    var busy by remember(key) { mutableStateOf(false) }
    val today = remember(key) { deviceToday() }

    SadoraBottomSheet(visible = prescription != null, title = p.addTitle, onDismiss = onDismiss) {
        val rx = prescription ?: return@SadoraBottomSheet
        Text(p.addBody, style = Sadora.type.body, color = c.muted)

        rx.items.forEachIndexed { index, item ->
            val on = chosen[index] ?: true
            val minutes = times[index] ?: item.schedule.times.map { it.hour * 60 + it.minute }
            Column(
                Modifier.fillMaxWidth().clip(Radius.card).background(c.surface2).padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    Modifier.fillMaxWidth().noRippleToggleable(on, role = androidx.compose.ui.semantics.Role.Checkbox) { chosen[index] = !on },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        Modifier.size(24.dp).clip(Radius.chip).background(if (on) c.primary else c.line),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(14.dp), tint = c.onPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                        Text(p.summary(item), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                    }
                }
                if (on) {
                    minutes.forEachIndexed { slot, minute ->
                        TimeStepRow(
                            minute = minute,
                            onStep = { steps ->
                                val moved = (minute + steps * StepMinutes).coerceIn(0, DayMinutes - StepMinutes)
                                if (minutes.withIndex().none { (other, value) -> other != slot && value == moved }) {
                                    times[index] = minutes.toMutableList().also { it[slot] = moved }
                                }
                            },
                        )
                    }
                }
            }
        }

        Text(p.startOn.uppercase(), style = Sadora.type.caption, color = c.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = p.earlier, onClick = { startOffset = (startOffset - 1).coerceAtLeast(0) })
            val start = today.plus(startOffset, DateTimeUnit.DAY)
            Text(
                when (startOffset) {
                    0 -> "${p.today}, ${strings.dates.dayMonth(start)}"
                    1 -> "${p.tomorrow}, ${strings.dates.dayMonth(start)}"
                    else -> strings.dates.dayMonth(start)
                },
                style = Sadora.type.h3,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(SadoraIcons.ChevronRight, contentDescription = p.later, onClick = { startOffset = (startOffset + 1).coerceAtMost(StartAheadDays) })
        }

        val picked = rx.items.indices.filter { chosen[it] ?: true }
        if (picked.isEmpty()) Text(p.nothingChosen, style = Sadora.type.body, color = c.danger)
        health.prescriptionCalls.error?.let { ErrorStrip(it.readable(strings.errors)) }
        SadoraButton(
            if (busy) p.adding else p.addConfirm,
            enabled = picked.isNotEmpty() && !busy,
            onClick = {
                busy = true
                health.prescriptionCalls.clearError()
                val request = AddPrescriptionRequest(
                    startOn = today.plus(startOffset, DateTimeUnit.DAY),
                    items = picked.map { index ->
                        val minutes = times[index] ?: rx.items[index].schedule.times.map { it.hour * 60 + it.minute }
                        AddPrescriptionItem(index, minutes.sorted().map { LocalTime(it / 60, it % 60) })
                    },
                )
                scope.launch {
                    health.addPrescription(rx.id, request)?.let(onAdded)
                    busy = false
                }
            },
        )
        SadoraButton(strings.common.cancel, onClick = onDismiss, tone = ButtonTone.Ghost)
    }
}

/** "🕘   ‹ 09:00 ›" */
@Composable
private fun TimeStepRow(minute: Int, onStep: (Int) -> Unit) {
    val p = strings.prescriptions
    val c = Sadora.colors
    val clock = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(SadoraIcons.Clock, contentDescription = null, Modifier.size(18.dp), tint = c.muted)
        Box(Modifier.weight(1f))
        CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = "$clock: ${p.earlier}", onClick = { onStep(-1) })
        Text(clock, style = Sadora.type.h3, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
        CircleIconButton(SadoraIcons.ChevronRight, contentDescription = "$clock: ${p.later}", onClick = { onStep(1) })
    }
}

@Composable
private fun dayOf(prescription: Prescription): String =
    strings.dates.dayMonth(prescription.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date)

private const val StepMinutes = 30
private const val DayMinutes = 24 * 60
private const val StartAheadDays = 30
