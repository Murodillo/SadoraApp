package uz.sadora.app.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.app.data.BabyController
import uz.sadora.app.data.readable
import uz.sadora.app.data.toUpload
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.deviceToday
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CapturedPhoto
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.parseTypedDate
import uz.sadora.app.ui.components.rememberPhotoCapture
import uz.sadora.app.ui.components.typedDateError
import uz.sadora.app.ui.journey.EventLine
import uz.sadora.contract.GrowthChart
import uz.sadora.contract.GrowthMetric
import uz.sadora.contract.Limits
import uz.sadora.contract.MarkMilestoneRequest
import uz.sadora.contract.MarkVaccineRequest
import uz.sadora.contract.Milestone
import uz.sadora.contract.SaveGrowthRequest
import uz.sadora.contract.VaccineDose
import uz.sadora.contract.VaccineStatus

private fun kotlinx.datetime.LocalDate.typed() = "$day.${month.ordinal + 1}.$year"

// ---------------------------------------------------------------- growth

/**
 * "O'sish" — her measurements, free, and above them the WHO chart for the metric chosen:
 * Premium, and when it is not hers the card says what Premium would draw instead of
 * leaving a blank.
 */
@Composable
fun BabyGrowthScreen(
    babies: BabyController,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val b = strings.babies
    val scope = rememberCoroutineScope()
    var metric by remember { mutableStateOf(GrowthMetric.WEIGHT) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(metric, babies.active?.id) { babies.loadGrowth(metric) }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(b.growth, onBack = onClose, subtitle = babies.active?.name)
            ScreenContent {
                item {
                    ChipFlowRow {
                        GrowthMetric.entries.forEach { m -> SelectChip(b.metric(m), selected = metric == m, onClick = { metric = m }) }
                    }
                }
                item {
                    val chart = babies.chart
                    when {
                        babies.chartLocked -> SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            Text(b.chartLockedTitle, style = Sadora.type.h3, color = c.text)
                            Text(b.chartLockedBody, style = Sadora.type.body, color = c.muted)
                            SadoraButton(b.upgrade, onClick = onUpgrade)
                        }
                        chart != null && chart.metric == metric -> ChartCard(chart)
                        else -> Unit
                    }
                }
                item { SadoraButton(b.addMeasurement, onClick = { adding = true }, tone = ButtonTone.Secondary) }
                if (babies.growth.isNotEmpty()) {
                    item { CardLabel(b.measurements) }
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                            babies.growth.asReversed().forEach { entry ->
                                EventLine(
                                    title = strings.dates.dayMonthYear(entry.measuredOn) + " · " + b.age(entry.ageDays),
                                    detail = listOfNotNull(
                                        entry.weightG?.let { LabFormat.number(it / 1000.0) + " " + b.kg },
                                        entry.lengthCm?.let { LabFormat.number(it) + " " + b.cm },
                                        entry.headCm?.let { b.metric(GrowthMetric.HEAD) + " " + LabFormat.number(it) + " " + b.cm },
                                    ).joinToString(" · "),
                                    onDelete = { scope.launch { babies.deleteGrowth(entry.id, metric) } },
                                )
                            }
                        }
                    }
                }
            }
        }
        SadoraBottomSheet(visible = adding, title = b.addMeasurement, onDismiss = { adding = false }) {
            var day by remember(adding) { mutableStateOf(deviceToday().typed()) }
            var weight by remember(adding) { mutableStateOf("") }
            var length by remember(adding) { mutableStateOf("") }
            var head by remember(adding) { mutableStateOf("") }
            val date = parseTypedDate(day)
            val dateError = typedDateError(day, allowFuture = false)
            val ready = date != null && dateError == null && (weight.isNotBlank() || length.isNotBlank() || head.isNotBlank())
            SadoraTextField(day, { day = acceptText(it, 10) }, label = b.measuredOn, error = dateError)
            SadoraTextField(weight, { weight = acceptText(it.filter(Char::isDigit), 5) }, label = b.weightField, placeholder = "5600", keyboardType = KeyboardType.Number)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SadoraTextField(length, { length = acceptText(it, 5) }, label = b.lengthField, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                SadoraTextField(head, { head = acceptText(it, 5) }, label = b.headField, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
            }
            babies.error?.let { ErrorStrip(it.readable(strings.errors)) }
            SadoraButton(
                strings.common.save,
                enabled = ready && !babies.busy,
                onClick = {
                    val on = date ?: return@SadoraButton
                    scope.launch {
                        val ok = babies.addGrowth(
                            SaveGrowthRequest(on, weight.toIntOrNull(), LabFormat.parse(length), LabFormat.parse(head)),
                            metric,
                        )
                        if (ok) adding = false
                    }
                },
            )
        }
    }
}

/** The WHO curves as bands, her measurements as dots on them, and the latest percentile. */
@Composable
private fun ChartCard(chart: GrowthChart) {
    val c = Sadora.colors
    val b = strings.babies
    val latest = chart.points.lastOrNull()
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        if (latest != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(LabFormat.number(latest.value), style = Sadora.type.h1, color = c.text)
                Text(if (chart.unit == "kg") b.kg else b.cm, style = Sadora.type.body, color = c.muted, modifier = Modifier.padding(bottom = 4.dp))
                Box(Modifier.weight(1f))
                latest.percentile?.let { SadoraBadge(b.percentile(it.toInt().coerceIn(1, 99)), BadgeTone.Neutral) }
            }
        }
        val outer = c.primary.copy(alpha = if (c.isDark) 0.14f else 0.08f)
        val inner = c.primary.copy(alpha = if (c.isDark) 0.24f else 0.14f)
        val median = c.primary.copy(alpha = 0.6f)
        val dot = c.secondary
        val axis = c.line
        val description = b.metric(chart.metric) + ": " + chart.points.joinToString { LabFormat.number(it.value) }
        // The window follows the baby: two years of curves would squeeze a newborn's dots into the corner.
        val oldest = chart.points.maxOfOrNull { it.ageMonths } ?: 0.0
        val maxMonth = (kotlin.math.ceil(oldest * 1.5) + 2).coerceIn(6.0, (chart.curves.lastOrNull()?.month ?: 24).toDouble())
        val curves = chart.curves.filter { it.month <= maxMonth + 1 }
        Canvas(Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
            if (curves.isEmpty()) return@Canvas
            val low = minOf(curves.minOf { it.p3 }, chart.points.minOfOrNull { it.value } ?: Double.MAX_VALUE) * 0.95
            val high = maxOf(curves.maxOf { it.p97 }, chart.points.maxOfOrNull { it.value } ?: 0.0) * 1.03
            fun x(month: Double) = (month / maxMonth * size.width).toFloat()
            fun y(v: Double) = (size.height - (v - low) / (high - low) * size.height).toFloat()
            fun band(top: (uz.sadora.contract.GrowthCurvePoint) -> Double, bottom: (uz.sadora.contract.GrowthCurvePoint) -> Double, colour: androidx.compose.ui.graphics.Color) {
                val path = Path()
                curves.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.month.toDouble()), y(top(p))) else path.lineTo(x(p.month.toDouble()), y(top(p))) }
                curves.asReversed().forEach { p -> path.lineTo(x(p.month.toDouble()), y(bottom(p))) }
                path.close()
                drawPath(path, colour)
            }
            band({ it.p97 }, { it.p3 }, outer)
            band({ it.p85 }, { it.p15 }, inner)
            val mid = Path()
            curves.forEachIndexed { i, p -> if (i == 0) mid.moveTo(x(p.month.toDouble()), y(p.p50)) else mid.lineTo(x(p.month.toDouble()), y(p.p50)) }
            drawPath(mid, median, style = Stroke(width = 1.5.dp.toPx()))
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (chart.points.size > 1) {
                val line = Path()
                chart.points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.ageMonths), y(p.value)) else line.lineTo(x(p.ageMonths), y(p.value)) }
                drawPath(line, dot, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            chart.points.forEach { p -> drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x(p.ageMonths), y(p.value))) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0", style = noteStyle, color = c.muted2, modifier = Modifier.weight(1f))
            Text(b.months(maxMonth.toInt()), style = noteStyle, color = c.muted2)
        }
        Text(b.curvesLegend, style = noteStyle, color = c.muted2)
        Text(b.percentileNote, style = noteStyle, color = c.muted)
        Text(b.whoSource, style = noteStyle, color = c.muted2)
    }
}

// ---------------------------------------------------------------- vaccines

/** "Emlash taqvimi" — the national calendar from the birth date, each dose markable. */
@Composable
fun BabyVaccinesScreen(babies: BabyController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val b = strings.babies
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<VaccineDose?>(null) }
    LaunchedEffect(babies.active?.id) { babies.loadVaccines() }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(b.vaccinesTitle, onBack = onClose, subtitle = babies.active?.name)
            ScreenContent {
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                        Text(b.vaccinesNote, style = Sadora.type.body, color = c.muted)
                        Text(b.vaccinesSource, style = noteStyle, color = c.muted2)
                    }
                }
                babies.vaccines.groupBy { it.ageLabel }.forEach { (age, doses) ->
                    item(key = "age-$age") { CardLabel(age) }
                    items(doses, key = { it.code }) { dose -> VaccineRow(dose) { open = dose } }
                }
            }
        }
        SadoraBottomSheet(visible = open != null, title = open?.name.orEmpty(), onDismiss = { open = null }) {
            val dose = open ?: return@SadoraBottomSheet
            var day by remember(dose) { mutableStateOf((dose.givenOn ?: minOf(dose.dueOn, deviceToday())).typed()) }
            val date = parseTypedDate(day)
            val dateError = typedDateError(day, allowFuture = false)
            dose.note?.let { Text(it, style = Sadora.type.body, color = c.muted) }
            Text(b.dueOn(strings.dates.dayMonthYear(dose.dueOn)), style = Sadora.type.body, color = c.text)
            SadoraTextField(day, { day = acceptText(it, 10) }, label = b.givenDate, error = dateError)
            babies.error?.let { ErrorStrip(it.readable(strings.errors)) }
            SadoraButton(
                b.markGiven,
                enabled = date != null && dateError == null && !babies.busy,
                onClick = {
                    val on = date ?: return@SadoraButton
                    scope.launch { if (babies.markVaccine(dose.code, MarkVaccineRequest(on))) open = null }
                },
            )
            if (dose.givenOn != null) {
                SadoraButton(b.unmark, onClick = { scope.launch { if (babies.markVaccine(dose.code, null)) open = null } }, tone = ButtonTone.Ghost)
            }
        }
    }
}

@Composable
private fun VaccineRow(dose: VaccineDose, onClick: () -> Unit) {
    val c = Sadora.colors
    val b = strings.babies
    SadoraCard(padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xxs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(dose.name, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
            SadoraBadge(
                b.status(dose.status),
                when (dose.status) {
                    VaccineStatus.DONE -> BadgeTone.Success
                    VaccineStatus.OVERDUE -> BadgeTone.Warning
                    VaccineStatus.DUE -> BadgeTone.Premium
                    VaccineStatus.UPCOMING -> BadgeTone.Neutral
                },
            )
        }
        Text(
            dose.givenOn?.let { b.givenOn(strings.dates.dayMonthYear(it)) } ?: b.dueOn(strings.dates.dayMonthYear(dose.dueOn)),
            style = noteStyle,
            color = c.muted,
        )
    }
}

// ---------------------------------------------------------------- milestones

/** "Rivojlanish bosqichlari" — by group, each with its usual window, a date and a photo once reached. */
@Composable
fun BabyMilestonesScreen(babies: BabyController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val b = strings.babies
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Milestone?>(null) }
    LaunchedEffect(babies.active?.id) { babies.loadMilestones() }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(b.milestonesTitle, onBack = onClose, subtitle = babies.active?.name)
            ScreenContent {
                item {
                    SadoraCard(padding = Spacing.sm) { Text(b.milestonesNote, style = Sadora.type.body, color = c.muted) }
                }
                babies.milestones.groupBy { it.group }.forEach { (group, list) ->
                    item(key = "group-$group") { CardLabel(b.group(group)) }
                    items(list, key = { it.code }) { milestone -> MilestoneRow(milestone) { open = milestone } }
                }
            }
        }
        SadoraBottomSheet(visible = open != null, title = open?.name.orEmpty(), onDismiss = { open = null }) {
            val milestone = open ?: return@SadoraBottomSheet
            var day by remember(milestone) { mutableStateOf((milestone.achievedOn ?: deviceToday()).typed()) }
            var note by remember(milestone) { mutableStateOf(milestone.note.orEmpty()) }
            var photo by remember(milestone) { mutableStateOf<CapturedPhoto?>(null) }
            val picker = rememberPhotoCapture { photo = it }
            val date = parseTypedDate(day)
            val dateError = typedDateError(day, allowFuture = false)
            Text(milestone.about, style = Sadora.type.body, color = c.muted)
            Text(b.window(milestone.fromMonth, milestone.toMonth), style = noteStyle, color = c.muted2)
            SadoraTextField(day, { day = acceptText(it, 10) }, label = b.reachedDate, error = dateError)
            SadoraTextField(note, { note = acceptText(it, Limits.BABY_NOTE_MAX) }, label = b.note)
            when {
                photo != null -> PickedPhoto(photo!!)
                milestone.hasPhoto -> MilestonePhoto(babies, milestone.code)
            }
            if (picker.available) {
                SadoraButton(
                    if (photo != null || milestone.hasPhoto) b.changePhoto else b.addPhoto,
                    onClick = picker::pickFromGallery,
                    tone = ButtonTone.Outline,
                )
            }
            babies.error?.let { ErrorStrip(it.readable(strings.errors)) }
            SadoraButton(
                b.markReached,
                enabled = date != null && dateError == null && !babies.busy,
                onClick = {
                    val on = date ?: return@SadoraButton
                    val picked = photo
                    scope.launch {
                        val bytes = picked?.let { runCatching { Base64.decode(it.base64) }.getOrNull() }
                        val ok = babies.markMilestone(
                            milestone.code,
                            MarkMilestoneRequest(on, note.trim().takeIf { it.isNotEmpty() }, picked?.toUpload()),
                            bytes,
                        )
                        if (ok) open = null
                    }
                },
            )
            if (milestone.achievedOn != null) {
                SadoraButton(b.unmark, onClick = { scope.launch { if (babies.markMilestone(milestone.code, null)) open = null } }, tone = ButtonTone.Ghost)
            }
        }
    }
}

@Composable
private fun MilestoneRow(milestone: Milestone, onClick: () -> Unit) {
    val c = Sadora.colors
    val b = strings.babies
    val reached = milestone.achievedOn
    SadoraCard(padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xxs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(milestone.name, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
            if (reached != null) SadoraBadge(b.reachedOn(strings.dates.dayMonth(reached)), BadgeTone.Success)
        }
        Text(b.window(milestone.fromMonth, milestone.toMonth), style = noteStyle, color = c.muted)
    }
}

@Composable
private fun PickedPhoto(photo: CapturedPhoto) {
    val bitmap by produceState<ImageBitmap?>(null, photo) {
        value = withContext(Dispatchers.Default) { runCatching { Base64.decode(photo.base64).decodeToImageBitmap() }.getOrNull() }
    }
    bitmap?.let { PhotoBox(it) }
}

@Composable
private fun MilestonePhoto(babies: BabyController, code: String) {
    val bitmap by produceState<ImageBitmap?>(null, code) {
        val bytes = babies.milestonePhoto(code)
        value = bytes?.let { withContext(Dispatchers.Default) { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
    }
    bitmap?.let { PhotoBox(it) }
}

@Composable
private fun PhotoBox(bitmap: ImageBitmap) {
    Image(
        bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(Radius.cardSmall),
    )
}
