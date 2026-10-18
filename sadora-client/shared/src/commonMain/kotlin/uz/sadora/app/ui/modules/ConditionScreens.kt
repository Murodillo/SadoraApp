package uz.sadora.app.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import uz.sadora.app.data.ConditionController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.deviceToday
import uz.sadora.app.nav.Route
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.parseTypedDate
import uz.sadora.app.ui.components.typedDateError
import uz.sadora.contract.ConditionInfo
import uz.sadora.contract.ConditionStatus
import uz.sadora.contract.CycleAttention
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.Limits
import uz.sadora.contract.PainArea
import uz.sadora.contract.PainKind
import uz.sadora.contract.SavePainLogRequest
import uz.sadora.contract.SetConditionRequest
import uz.sadora.contract.UserCondition

private fun LocalDate.typed() = "$day.${month.ordinal + 1}.$year"

// ---------------------------------------------------------------- Holatlarim

/**
 * "Holatlarim" — each condition with its three answers: none, suspected, diagnosed. What
 * the condition is and what marking it turns on are said before she chooses, and a
 * suspicion is met with a way to a doctor rather than with anything that sounds like one.
 */
@Composable
fun ConditionsScreen(
    conditions: ConditionController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.conditions
    val scope = rememberCoroutineScope()
    var weighing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { conditions.refresh() }
    val savedToast = t.weightSaved

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.title, onBack = onClose)
            ScreenContent {
                item { Text(t.intro, style = Sadora.type.body, color = c.muted) }
                if (!conditions.loaded) {
                    item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) } }
                }
                conditions.state.catalog.forEach { info ->
                    item(key = info.condition.name) {
                        ConditionCard(
                            info = info,
                            marked = conditions.state.conditions.firstOrNull { it.condition == info.condition },
                            busy = conditions.busy,
                            onSet = { request -> scope.launch { conditions.set(info.condition, request) } },
                            onFindDoctor = { onOpen(Route.Doctors) },
                        )
                    }
                }
                conditions.error?.let { item { ErrorStrip(it.readable(strings.errors)) } }
                if (conditions.state.conditions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            if (conditions.has(HealthCondition.ENDOMETRIOSIS) || conditions.has(HealthCondition.PCOS)) {
                                LinkCard(Res.drawable.ic3d_notebook, t.painTitle, t.painNote) { onOpen(Route.PainDiary) }
                            }
                            LinkCard(Res.drawable.ic3d_insights, t.reportTitle, t.reportNote) { onOpen(Route.ConditionReport) }
                            if (conditions.has(HealthCondition.PCOS)) {
                                LinkCard(Res.drawable.ic3d_scale, t.logWeight, t.weightNote) { weighing = true }
                                Text(t.pcosSigns, style = noteStyle, color = c.muted2)
                            }
                        }
                    }
                }
            }
        }

        SadoraBottomSheet(visible = weighing, title = t.logWeight, onDismiss = { weighing = false }) {
            var kg by remember(weighing) { mutableStateOf("") }
            val value = LabFormat.parse(kg)?.takeIf { it in 25.0..300.0 }
            SadoraTextField(kg, { kg = acceptText(it, 6) }, label = t.weightKg, placeholder = "62,5", keyboardType = KeyboardType.Decimal)
            SadoraButton(
                strings.common.save,
                enabled = value != null && !conditions.busy,
                onClick = {
                    val v = value ?: return@SadoraButton
                    scope.launch {
                        if (conditions.logWeight(v)) {
                            weighing = false
                            onToast(savedToast)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ConditionCard(
    info: ConditionInfo,
    marked: UserCondition?,
    busy: Boolean,
    onSet: (SetConditionRequest?) -> Unit,
    onFindDoctor: () -> Unit,
) {
    val c = Sadora.colors
    val t = strings.conditions
    var diagnosedOn by remember(marked) { mutableStateOf(marked?.diagnosedOn?.typed().orEmpty()) }
    val dateError = typedDateError(diagnosedOn, allowFuture = false)
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        Text(info.name, style = Sadora.type.h3, color = c.text)
        Text(info.about, style = Sadora.type.body, color = c.muted)
        ChipFlowRow {
            SelectChip(t.none, selected = marked == null, onClick = { if (marked != null && !busy) onSet(null) })
            SelectChip(t.suspected, selected = marked?.status == ConditionStatus.SUSPECTED, onClick = {
                if (!busy) onSet(SetConditionRequest(ConditionStatus.SUSPECTED))
            })
            SelectChip(t.diagnosed, selected = marked?.status == ConditionStatus.DIAGNOSED, onClick = {
                if (!busy) onSet(SetConditionRequest(ConditionStatus.DIAGNOSED, parseTypedDate(diagnosedOn)))
            })
        }
        if (marked != null) {
            CardLabel(t.whatChanges)
            Text(info.tracks, style = noteStyle, color = c.muted)
        }
        if (marked?.status == ConditionStatus.DIAGNOSED) {
            SadoraTextField(
                diagnosedOn,
                { diagnosedOn = acceptText(it, 10) },
                label = t.diagnosedOn,
                placeholder = "12.03.2025",
                error = dateError,
            )
            val parsed = parseTypedDate(diagnosedOn)
            if (parsed != marked.diagnosedOn && dateError == null) {
                SadoraButton(strings.common.save, onClick = { onSet(SetConditionRequest(ConditionStatus.DIAGNOSED, parsed)) }, tone = ButtonTone.Secondary)
            }
        }
        if (marked?.status == ConditionStatus.SUSPECTED) {
            Text(info.suspectedNote, style = Sadora.type.body, color = c.text)
            SadoraButton(t.findDoctor, onClick = onFindDoctor, tone = ButtonTone.Secondary)
        }
    }
}

@Composable
internal fun LinkCard(art: org.jetbrains.compose.resources.DrawableResource, title: String, body: String, onClick: () -> Unit) {
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick, verticalGap = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(art, tint = c.primary, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = Sadora.type.h3, color = c.text)
                Text(body, style = noteStyle, color = c.muted)
            }
        }
    }
}

// ---------------------------------------------------------------- the pain diary

/**
 * "Og'riq kundaligi" — one day at a time, today unless she taps an earlier day. The level
 * is a row of eleven taps rather than a slider, because a slider's value is a guess at the
 * edge of a thumb; where it hurt is a body map, with the same areas as chips under it.
 */
@Composable
fun PainDiaryScreen(
    conditions: ConditionController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.conditions
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { conditions.loadPain() }
    var day by remember { mutableStateOf(deviceToday()) }
    val existing = conditions.pain.firstOrNull { it.date == day }
    var level by remember(day, existing) { mutableStateOf(existing?.level) }
    var areas by remember(day, existing) { mutableStateOf(existing?.areas.orEmpty().toSet()) }
    var kinds by remember(day, existing) { mutableStateOf(existing?.kinds.orEmpty().toSet()) }
    var painkiller by remember(day, existing) { mutableStateOf(existing?.painkiller ?: false) }
    var painkillerName by remember(day, existing) { mutableStateOf(existing?.painkillerName.orEmpty()) }
    var missed by remember(day, existing) { mutableStateOf(existing?.missedActivities ?: false) }
    var note by remember(day, existing) { mutableStateOf(existing?.note.orEmpty()) }
    val savedToast = t.saved

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.painTitle, onBack = onClose, subtitle = strings.dates.dayMonthYear(day))
            ScreenContent {
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        CardLabel(t.todayLevel)
                        LevelRow(level) { level = it }
                        level?.let { Text(t.levelWord(it), style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text) }
                    }
                }
                if ((level ?: 0) > 0) {
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            CardLabel(t.where)
                            BodyMap(areas) { area -> areas = if (area in areas) areas - area else areas + area }
                            ChipFlowRow {
                                PainArea.entries.forEach { area ->
                                    SelectChip(t.area(area), selected = area in areas, onClick = { areas = if (area in areas) areas - area else areas + area })
                                }
                            }
                        }
                    }
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            CardLabel(t.whenLabel)
                            ChipFlowRow {
                                PainKind.entries.forEach { kind ->
                                    SelectChip(t.kind(kind), selected = kind in kinds, onClick = { kinds = if (kind in kinds) kinds - kind else kinds + kind })
                                }
                            }
                        }
                    }
                }
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        ChipFlowRow {
                            SelectChip(t.painkiller, selected = painkiller, onClick = { painkiller = !painkiller })
                            SelectChip(t.missed, selected = missed, onClick = { missed = !missed })
                        }
                        if (painkiller) {
                            SadoraTextField(painkillerName, { painkillerName = acceptText(it, Limits.PAINKILLER_NAME_MAX) }, label = t.painkillerName)
                        }
                        SadoraTextField(note, { note = acceptText(it, Limits.PAIN_NOTE_MAX) }, label = t.note)
                    }
                }
                conditions.error?.let { item { ErrorStrip(it.readable(strings.errors)) } }
                item {
                    SadoraButton(
                        strings.common.save,
                        enabled = level != null && !conditions.busy,
                        onClick = {
                            val l = level ?: return@SadoraButton
                            scope.launch {
                                val ok = conditions.savePain(
                                    day,
                                    SavePainLogRequest(
                                        level = l,
                                        areas = if (l > 0) areas.toList() else emptyList(),
                                        kinds = if (l > 0) kinds.toList() else emptyList(),
                                        painkiller = painkiller,
                                        painkillerName = painkillerName.trim().takeIf { painkiller && it.isNotEmpty() },
                                        missedActivities = missed,
                                        note = note.trim().takeIf { it.isNotEmpty() },
                                    ),
                                )
                                if (ok) onToast(savedToast)
                            }
                        },
                    )
                }
                item { CardLabel(t.history) }
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                        HistoryStrip(conditions, selected = day) { day = it }
                        Text(t.painNote, style = noteStyle, color = c.muted2)
                    }
                }
            }
        }
    }
}

/** 0 to 10 as eleven taps, coloured from calm to the warning colour. */
@Composable
private fun LevelRow(level: Int?, onPick: (Int) -> Unit) {
    val c = Sadora.colors
    val t = strings.conditions
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (0..10).forEach { n ->
            val chosen = level == n
            val tint = when {
                n == 0 -> c.success
                n <= 3 -> c.primary.copy(alpha = 0.55f)
                n <= 6 -> c.primary
                else -> c.warning
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(Radius.chip)
                    .background(if (chosen) tint else tint.copy(alpha = 0.16f))
                    .noRippleClickable(role = Role.RadioButton) { onPick(n) }
                    .semantics {
                        contentDescription = t.levelShort(n) + ", " + t.levelWord(n)
                        selected = chosen
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("$n", style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = if (chosen) c.onPrimary else c.text)
            }
        }
    }
}

/**
 * A figure seen from the front with the six areas on it. The lower back is drawn as a band
 * behind the hips, since a front view cannot show it; each spot is also a chip below, so the
 * map is a convenience and never the only way to choose.
 */
@Composable
private fun BodyMap(chosen: Set<PainArea>, onToggle: (PainArea) -> Unit) {
    val c = Sadora.colors
    val t = strings.conditions
    val outline = c.lineStrong
    val fill = c.surface2
    BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.TopCenter) {
        val w = 180.dp
        Box(Modifier.width(w).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2
                // head, torso, hips, legs: a plain figure, never a realistic body
                drawCircle(fill, radius = size.width * 0.09f, center = Offset(cx, size.height * 0.08f))
                drawCircle(outline, radius = size.width * 0.09f, center = Offset(cx, size.height * 0.08f), style = Stroke(1.5.dp.toPx()))
                val torso = Size(size.width * 0.56f, size.height * 0.40f)
                val torsoTop = Offset(cx - torso.width / 2, size.height * 0.17f)
                drawRoundRect(fill, torsoTop, torso, CornerRadius(28.dp.toPx()))
                drawRoundRect(outline, torsoTop, torso, CornerRadius(28.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                val leg = Size(size.width * 0.2f, size.height * 0.43f)
                listOf(cx - leg.width - 3.dp.toPx(), cx + 3.dp.toPx()).forEach { x ->
                    val top = Offset(x, size.height * 0.55f)
                    drawRoundRect(fill, top, leg, CornerRadius(16.dp.toPx()))
                    drawRoundRect(outline, top, leg, CornerRadius(16.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            Spot(PainArea.LOWER_BACK, chosen, t.area(PainArea.LOWER_BACK), x = 0.5f, y = 0.29f, w, onToggle)
            Spot(PainArea.LOWER_ABDOMEN, chosen, t.area(PainArea.LOWER_ABDOMEN), x = 0.5f, y = 0.41f, w, onToggle)
            // Her left is on the viewer's right: the figure faces her.
            Spot(PainArea.PELVIS_LEFT, chosen, t.area(PainArea.PELVIS_LEFT), x = 0.67f, y = 0.5f, w, onToggle)
            Spot(PainArea.PELVIS_RIGHT, chosen, t.area(PainArea.PELVIS_RIGHT), x = 0.33f, y = 0.5f, w, onToggle)
            Spot(PainArea.RECTUM, chosen, t.area(PainArea.RECTUM), x = 0.5f, y = 0.6f, w, onToggle)
            Spot(PainArea.LEGS, chosen, t.area(PainArea.LEGS), x = 0.5f, y = 0.8f, w, onToggle)
        }
    }
}

@Composable
private fun Spot(area: PainArea, chosen: Set<PainArea>, label: String, x: Float, y: Float, width: androidx.compose.ui.unit.Dp, onToggle: (PainArea) -> Unit) {
    val c = Sadora.colors
    val on = area in chosen
    val size = 28.dp
    Box(
        Modifier
            .offset(x = width * x - size / 2, y = 220.dp * y - size / 2)
            .size(size)
            .clip(Radius.chip)
            .background(if (on) c.warning.copy(alpha = 0.85f) else c.primary.copy(alpha = 0.14f))
            .border(1.5.dp, if (on) c.warning else c.primary.copy(alpha = 0.5f), Radius.chip)
            .noRippleClickable(role = Role.Checkbox) { onToggle(area) }
            .semantics {
                contentDescription = label
                selected = on
            },
    )
}

/** The last fortnight as small bars; tapping a day opens it in the form above. */
@Composable
private fun HistoryStrip(conditions: ConditionController, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = Sadora.colors
    val t = strings.conditions
    val today = deviceToday()
    val days = (13 downTo 0).map { today.minusDays(it) }
    val byDay = conditions.pain.associateBy { it.date }
    if (conditions.pain.isEmpty()) Text(t.noEntries, style = Sadora.type.body, color = c.muted)
    Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            val log = byDay[d]
            val label = strings.dates.dayMonth(d) + (log?.let { ": " + t.levelShort(it.level) } ?: "")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .noRippleClickable(role = Role.Button) { onPick(d) }
                    .semantics { contentDescription = label },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = ((log?.level ?: 0) / 10f).coerceAtLeast(if (log != null) 0.06f else 0.02f)
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .fillMaxHeight(fraction * 0.75f)
                        .clip(Radius.chip)
                        .background(
                            when {
                                log == null -> c.line
                                log.level >= 7 -> c.warning
                                else -> c.primary.copy(alpha = 0.7f)
                            },
                        ),
                )
                Text("${d.day}", style = noteStyle, color = if (d == selected) c.text else c.muted2, fontWeight = if (d == selected) FontWeight.Bold else null)
            }
        }
    }
}

private fun LocalDate.minusDays(days: Int): LocalDate = minus(days, kotlinx.datetime.DateTimeUnit.DAY)

// ---------------------------------------------------------------- the report

/** "Holat hisoboti" — 3 or 6 months, Premium; a lock that says what it would show otherwise. */
@Composable
fun ConditionReportScreen(
    conditions: ConditionController,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    onShowDoctor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.conditions
    var months by remember { mutableStateOf(3) }
    LaunchedEffect(months) { conditions.loadReport(months) }
    val report = conditions.report

    Column(modifier) {
        SadoraTopBar(t.reportTitle, onBack = onClose)
        ScreenContent {
            item {
                ChipFlowRow {
                    listOf(3, 6).forEach { m -> SelectChip(t.months(m), selected = months == m, onClick = { months = m }) }
                }
            }
            when {
                conditions.reportLocked -> item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        Text(t.lockedTitle, style = Sadora.type.h3, color = c.text)
                        Text(t.lockedBody, style = Sadora.type.body, color = c.muted)
                        SadoraButton(t.upgrade, onClick = onUpgrade)
                    }
                }
                report == null || report.months != months -> item {
                    Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) }
                }
                else -> {
                    item {
                        Text(
                            strings.dates.dayMonthYear(report.from) + " – " + strings.dates.dayMonthYear(report.to),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            CardLabel(t.cycles)
                            if (report.cycles.isEmpty()) Text(t.noData, style = Sadora.type.body, color = c.muted)
                            val longest = (report.cycles.mapNotNull { it.length }.maxOrNull() ?: 35).coerceAtLeast(35)
                            report.cycles.forEach { cycle ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(strings.dates.dayMonth(cycle.startedOn), style = noteStyle, color = c.muted, modifier = Modifier.width(72.dp))
                                    Box(
                                        Modifier
                                            .weight(((cycle.length ?: 0).toFloat() / longest).coerceIn(0.02f, 1f))
                                            .height(14.dp)
                                            .clip(Radius.chip)
                                            .background(if ((cycle.length ?: 0) > 35) c.warning else c.primary.copy(alpha = 0.7f)),
                                    )
                                    Box(Modifier.weight((1f - ((cycle.length ?: 0).toFloat() / longest)).coerceAtLeast(0.001f)))
                                    Text(cycle.length?.let(t::cycleLength) ?: t.running, style = noteStyle, color = c.text)
                                }
                            }
                        }
                    }
                    report.pain?.let { pain ->
                        item {
                            SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                                CardLabel(t.painByMonth)
                                report.monthly.forEach { m ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Text(monthLabel(m.month), style = Sadora.type.body, color = c.text, modifier = Modifier.width(90.dp))
                                        Text(
                                            listOfNotNull(
                                                t.painDays(m.painDays),
                                                m.averagePain?.let { t.averagePain(LabFormat.number(kotlin.math.round(it * 10) / 10)) },
                                                m.missedDays.takeIf { it > 0 }?.let { "$it ${t.missedShort}" },
                                            ).joinToString(" · "),
                                            style = noteStyle,
                                            color = c.muted,
                                        )
                                    }
                                }
                                CardLabel(t.summary)
                                Text(
                                    listOfNotNull(
                                        t.painDays(pain.painDays),
                                        pain.averageLevel?.let { t.averagePain(LabFormat.number(kotlin.math.round(it * 10) / 10)) },
                                        "max ${pain.maxLevel}/10",
                                    ).joinToString(" · "),
                                    style = Sadora.type.body,
                                    color = c.text,
                                )
                                if (pain.byKind.isNotEmpty()) {
                                    Text(
                                        pain.byKind.entries.sortedByDescending { it.value }.joinToString(", ") { (k, n) -> t.kind(k) + " — " + n },
                                        style = noteStyle,
                                        color = c.muted,
                                    )
                                }
                            }
                        }
                    }
                    if (report.signs.isNotEmpty()) {
                        item {
                            SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                                CardLabel(t.signs)
                                report.signs.forEach { sign ->
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(sign.label, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
                                        Text(t.signDays(sign.days), style = Sadora.type.body, color = c.muted)
                                    }
                                }
                            }
                        }
                    }
                    if (report.weights.isNotEmpty()) {
                        item {
                            SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                                CardLabel(t.weight)
                                val first = report.weights.first()
                                val last = report.weights.last()
                                Text(
                                    "${LabFormat.number(first.kg)} → ${LabFormat.number(last.kg)} kg",
                                    style = Sadora.type.h3,
                                    color = c.text,
                                )
                                Text(strings.dates.dayMonth(first.date) + " – " + strings.dates.dayMonth(last.date), style = noteStyle, color = c.muted)
                            }
                        }
                    }
                    item { SadoraButton(t.showDoctor, onClick = onShowDoctor, tone = ButtonTone.Secondary) }
                }
            }
        }
    }
}

@Composable
private fun monthLabel(wire: String): String {
    val (year, month) = wire.split('-').map { it.toInt() }
    return strings.dates.monthYear(year, month)
}

// ---------------------------------------------------------------- on the cycle page

/** The once-in-a-while notice: long cycles or three months without a period, with a way to a doctor. */
@Composable
fun CycleAttentionCard(attention: CycleAttention, onFindDoctor: () -> Unit) {
    val c = Sadora.colors
    val t = strings.conditions
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraBadge("!", BadgeTone.Warning)
            Text(t.attentionTitle(attention), style = Sadora.type.h3, color = c.text)
        }
        Text(t.attentionBody(attention), style = Sadora.type.body, color = c.muted)
        SadoraButton(t.findDoctor, onClick = onFindDoctor, tone = ButtonTone.Secondary)
    }
}

/** The way from the cycle page to "Holatlarim", once she has marked something. */
@Composable
fun ConditionsJourneyCard(conditions: ConditionController, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    val t = strings.conditions
    LaunchedEffect(Unit) { if (!conditions.loaded) conditions.refresh() }
    if (conditions.state.conditions.isEmpty()) return
    val names = conditions.state.conditions.mapNotNull { mark -> conditions.state.catalog.firstOrNull { it.condition == mark.condition }?.name }
    SadoraCard(padding = Spacing.sm, onClick = { onOpen(Route.PainDiary) }, verticalGap = Spacing.xxs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.ic3d_notebook, tint = c.primary, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(t.painTitle, style = Sadora.type.h3, color = c.text)
                Text(names.joinToString(" · "), style = noteStyle, color = c.muted)
            }
        }
    }
}
