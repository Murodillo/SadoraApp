package uz.sadora.app.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.app.data.LabController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.nav.Route
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.LoadMoreRow
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.pressable
import uz.sadora.contract.LabFlag
import uz.sadora.contract.LabReport
import uz.sadora.contract.LabTrend
import uz.sadora.contract.LabTrendPoint
import uz.sadora.contract.LabValue

/**
 * "Tahlil natijalari" — a chart for every marker she has a number for, and her sheets.
 *
 * The two ways in sit at the top, side by side: the scanner is the quick one, typing is
 * always there, and the scanner is not offered at all when the server has no model. The
 * flags are the laboratory's own range and the page says so, every time it shows one.
 */
@Composable
fun LabsScreen(
    state: AppState,
    labs: LabController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val l = strings.labs
    LaunchedEffect(Unit) { labs.refresh() }
    val overview = labs.overview

    Column(modifier) {
        SadoraTopBar(l.title, onBack = onClose)
        ScreenContent {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (overview.scanAvailable) {
                        WayIn(Res.drawable.ic3d_camera, l.scan, l.scanNote, Modifier.weight(1f)) { onOpen(Route.LabScan) }
                    }
                    WayIn(Res.drawable.ic3d_notebook, l.manual, l.manualNote, Modifier.weight(1f)) { onOpen(Route.LabEdit(null)) }
                }
            }
            // Only while the month is counted: an allowance with no monthly cap needs no line.
            overview.scanAllowance?.remainingThisMonth?.let { left ->
                item { Text(l.scansLeft(left, premiumHint = !state.isPremium), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
            }

            if (overview.trends.isNotEmpty()) {
                item { CardLabel(l.markersTitle) }
                items(overview.trends.chunked(2), key = { row -> "trend-" + row.joinToString { it.markerCode } }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        row.forEach { trend ->
                            TrendTile(trend, Modifier.weight(1f)) { onOpen(Route.LabTrend(trend.markerCode)) }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            if (labs.reports.isNotEmpty()) {
                item { CardLabel(l.reportsTitle) }
                items(labs.reports, key = { it.id }) { report ->
                    ReportCard(report) { onOpen(Route.LabReport(report.id)) }
                }
                if (labs.hasMore) {
                    item(key = "more") { LoadMoreRow(labs.loadedCount, onLoadMore = labs::loadMore) }
                }
                item { Text(l.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
            } else if (labs.loaded) {
                item {
                    EmptyState(
                        title = l.emptyTitle,
                        body = l.emptyBody,
                        actionText = null,
                        onAction = {},
                        art = Res.drawable.ic3d_lab,
                    )
                }
            } else {
                item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) } }
            }
        }
    }
}

@Composable
private fun WayIn(
    art: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    note: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    SadoraCard(modifier, padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xs) {
        ArtTile(art, tint = c.primary, size = 40.dp)
        Text(title, style = Sadora.type.h3, color = c.text)
        Text(note, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
    }
}

@Composable
private fun TrendTile(trend: LabTrend, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sadora.colors
    val latest = trend.latest ?: return
    SadoraCard(modifier, padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xxs) {
        Text(trend.name, style = Sadora.type.body, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(LabFormat.number(latest.value), style = Sadora.type.h2, color = c.text)
            latest.unit?.let { Text(it, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, modifier = Modifier.padding(bottom = 3.dp)) }
        }
        latest.flag?.let { FlagBadge(it) }
        val points = sameUnit(trend.points)
        if (points.size > 1) {
            TrendChart(points, Modifier.fillMaxWidth().height(36.dp), compact = true)
        }
    }
}

@Composable
private fun ReportCard(report: LabReport, onClick: () -> Unit) {
    val c = Sadora.colors
    val l = strings.labs
    SadoraCard(padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xxs) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.dates.dayMonthYear(report.takenOn),
                style = Sadora.type.h3,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            if (report.flaggedCount > 0) SadoraBadge("${report.flaggedCount}", BadgeTone.Warning)
        }
        report.place?.let { Text(it, style = Sadora.type.body, color = c.muted) }
        Text(l.countLine(report.values.size, report.flaggedCount), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    }
}

@Composable
internal fun FlagBadge(flag: LabFlag) {
    val l = strings.labs
    SadoraBadge(
        l.flag(flag),
        when (flag) {
            LabFlag.NORMAL -> BadgeTone.Success
            LabFlag.LOW, LabFlag.HIGH -> BadgeTone.Warning
        },
    )
}

// ---------------------------------------------------------------- one sheet

/**
 * One sheet: every row against its own range, the photographed pages, and the two
 * things she can do with it besides reading — correct it, or delete it.
 */
@Composable
fun LabReportScreen(
    id: String,
    labs: LabController,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onAskDoctor: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val l = strings.labs
    val scope = rememberCoroutineScope()
    var report by remember(id) { mutableStateOf(labs.cached(id)) }
    var failed by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(id, labs.reports) {
        val found = labs.report(id)
        report = found
        failed = found == null
    }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(l.title, onBack = onClose)
            val sheet = report
            if (sheet == null) {
                Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                    if (failed) {
                        labs.error?.let { ErrorStrip(it.readable(strings.errors)) }
                    } else {
                        SadoraLoader(size = 40.dp)
                    }
                }
                return@Column
            }
            ScreenContent {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(strings.dates.dayMonthYear(sheet.takenOn), style = Sadora.type.h2, color = c.text)
                        sheet.place?.let { Text(it, style = Sadora.type.body, color = c.muted) }
                        Text(l.countLine(sheet.values.size, sheet.flaggedCount), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                        if (sheet.scanned) SadoraBadge(l.scanned, BadgeTone.Estimated)
                    }
                }
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = 0.dp) {
                        sheet.values.forEachIndexed { index, value ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                            ValueRow(value)
                        }
                    }
                }
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        Text(l.disclaimer, style = Sadora.type.body, color = c.muted)
                        SadoraButton(l.askDoctor, onClick = onAskDoctor, tone = ButtonTone.Secondary)
                    }
                }
                if (sheet.photos.isNotEmpty()) {
                    item {
                        CardLabel(l.photos)
                        Row(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            sheet.photos.indices.forEach { page ->
                                PageThumb(labs, sheet.id, page, Modifier.weight(1f)) { viewing = page }
                            }
                            repeat(uz.sadora.contract.Limits.LAB_PHOTOS_MAX - sheet.photos.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                sheet.note?.let { note ->
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                            Text(l.note, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                            Text(note, style = Sadora.type.body, color = c.text)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SadoraButton(l.delete, onClick = { confirmDelete = true }, tone = ButtonTone.Destructive, modifier = Modifier.weight(1f))
                        SadoraButton(l.edit, onClick = onEdit, tone = ButtonTone.Outline, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        SadoraBottomSheet(visible = viewing != null, title = viewing?.let { l.photo(it + 1) }.orEmpty(), onDismiss = { viewing = null }) {
            val shown = report
            if (viewing != null && shown != null) PagePhoto(labs, shown.id, viewing!!, Modifier.fillMaxWidth())
        }

        SadoraDialog(
            visible = confirmDelete,
            title = l.deleteTitle,
            body = l.deleteBody,
            confirmText = l.delete,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    if (labs.delete(id)) {
                        onToast(l.deleted)
                        onClose()
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** A row: the name, the result and its flag; the range under them; the explanation on tap. */
@Composable
internal fun ValueRow(value: LabValue) {
    val c = Sadora.colors
    val l = strings.labs
    var open by remember(value.id) { mutableStateOf(false) }
    val about = value.about
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (about != null) Modifier.noRippleClickable(role = Role.Button) { open = !open } else Modifier)
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                value.name + if (about != null) " ⓘ" else "",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Text(LabFormat.result(value), style = Sadora.type.body, color = c.text)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                LabFormat.range(value)?.let { "${l.range}: $it" } ?: l.noRange,
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.muted2,
                modifier = Modifier.weight(1f),
            )
            value.flag?.let { FlagBadge(it) }
        }
        if (open && about != null) {
            Text(about, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
        }
    }
}

@Composable
private fun PageThumb(labs: LabController, id: String, page: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sadora.colors
    val label = strings.labs.photo(page + 1)
    Box(
        modifier
            .aspectRatio(0.75f)
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .pressable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap by pageBitmap(labs, id, page)
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            ?: SadoraLoader(size = 24.dp)
    }
}

@Composable
private fun PagePhoto(labs: LabController, id: String, page: Int, modifier: Modifier = Modifier) {
    val bitmap by pageBitmap(labs, id, page)
    val current = bitmap
    if (current == null) {
        Box(modifier.height(240.dp), contentAlignment = Alignment.Center) { SadoraLoader(size = 36.dp) }
    } else {
        Image(
            current,
            contentDescription = strings.labs.photo(page + 1),
            contentScale = ContentScale.Fit,
            modifier = modifier.aspectRatio(current.width.toFloat() / current.height.coerceAtLeast(1)).clip(Radius.cardSmall),
        )
    }
}

@Composable
private fun pageBitmap(labs: LabController, id: String, page: Int) = produceState<ImageBitmap?>(null, id, page) {
    val bytes = labs.photo(id, page)
    value = bytes?.let { withContext(Dispatchers.Default) { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
}

// ---------------------------------------------------------------- one marker

/**
 * One marker across her sheets: the latest result, the chart, and every result with
 * its date. Only results in the latest one's unit are drawn — a ferritin in µg/L and
 * one in ng/mL are the same number, but a chart cannot know that, and a line that jumps
 * a thousandfold is worse than a list.
 */
@Composable
fun LabTrendScreen(
    code: String,
    labs: LabController,
    onClose: () -> Unit,
    onOpenReport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val l = strings.labs
    LaunchedEffect(code) { if (labs.overview.trends.none { it.markerCode == code }) labs.refresh() }
    val trend = labs.overview.trends.firstOrNull { it.markerCode == code }

    Column(modifier) {
        SadoraTopBar(trend?.name ?: l.title, onBack = onClose)
        if (trend == null) {
            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) }
            return@Column
        }
        val latest = trend.latest
        val drawn = sameUnit(trend.points)
        ScreenContent {
            if (latest != null) {
                item {
                    SadoraCard(padding = Spacing.md, verticalGap = Spacing.xs) {
                        Text(l.latest, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(LabFormat.number(latest.value), style = Sadora.type.h1, color = c.text)
                            latest.unit?.let { Text(it, style = Sadora.type.body, color = c.muted, modifier = Modifier.padding(bottom = 4.dp)) }
                            Box(Modifier.weight(1f))
                            latest.flag?.let { FlagBadge(it) }
                        }
                        Text(
                            LabFormat.range(latest.refLow, latest.refHigh)?.let { "${l.range}: $it" } ?: l.noRange,
                            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                            color = c.muted2,
                        )
                        if (drawn.size > 1) {
                            TrendChart(
                                drawn,
                                Modifier.fillMaxWidth().height(160.dp).padding(top = Spacing.xs).semantics { contentDescription = l.chartLabel },
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Text(strings.dates.dayMonthYear(drawn.first().takenOn), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2, modifier = Modifier.weight(1f))
                                Text(strings.dates.dayMonthYear(drawn.last().takenOn), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                            }
                        }
                        if (drawn.size < trend.points.size) Text(l.otherUnits, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                    }
                }
            }
            item {
                SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                    Text(trend.about, style = Sadora.type.body, color = c.muted)
                }
            }
            item { CardLabel(l.history) }
            items(trend.points.asReversed(), key = { it.reportId + it.takenOn + it.value }) { point ->
                SadoraCard(padding = Spacing.sm, onClick = { onOpenReport(point.reportId) }, verticalGap = Spacing.xxs) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(strings.dates.dayMonthYear(point.takenOn), style = Sadora.type.body, color = c.muted, modifier = Modifier.weight(1f))
                        Text(listOfNotNull(LabFormat.number(point.value), point.unit).joinToString(" "), style = Sadora.type.h3, color = c.text)
                        point.flag?.let { FlagBadge(it) }
                    }
                }
            }
            item { Text(l.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
        }
    }
}

/** The points that share the latest one's unit, compared without case or spaces. */
private fun sameUnit(points: List<LabTrendPoint>): List<LabTrendPoint> {
    val unit = points.lastOrNull()?.unit?.unitKey()
    return points.filter { it.unit?.unitKey() == unit }
}

private fun String.unitKey() = lowercase().replace(" ", "")

/**
 * The line, with the latest result's range as a band behind it. A point outside its
 * own range is drawn in the warning colour; the band is the latest one's, because a
 * chart can show one band and the newest range is the one she is being read against.
 */
@Composable
private fun TrendChart(points: List<LabTrendPoint>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = Sadora.colors
    val line = c.primary
    val warn = c.warning
    val band = c.success.copy(alpha = if (c.isDark) 0.18f else 0.12f)
    val track = c.line
    val latest = points.last()
    Canvas(modifier) {
        val values = points.map { it.value } + listOfNotNull(latest.refLow, latest.refHigh)
        var low = values.min()
        var high = values.max()
        if (high - low < 1e-9) { low -= 1.0; high += 1.0 }
        val pad = (high - low) * 0.12
        low -= pad
        high += pad
        val dot = if (compact) 2.5.dp.toPx() else 4.dp.toPx()
        val left = dot
        val right = size.width - dot
        val top = dot
        val bottom = size.height - dot
        fun y(v: Double) = (bottom - (v - low) / (high - low) * (bottom - top)).toFloat()
        fun x(i: Int) = if (points.size == 1) size.width / 2 else left + (right - left) * i / (points.size - 1)

        val from = latest.refLow
        val to = latest.refHigh
        if (from != null || to != null) {
            val yTop = y(to ?: high)
            val yBottom = y(from ?: low)
            drawRect(band, topLeft = Offset(0f, yTop), size = androidx.compose.ui.geometry.Size(size.width, yBottom - yTop))
        }
        if (!compact) drawLine(track, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())

        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value)) }
        drawPath(path, line, style = Stroke(width = if (compact) 1.5.dp.toPx() else 2.5.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { i, p ->
            val colour = if (p.flag == LabFlag.LOW || p.flag == LabFlag.HIGH) warn else line
            drawCircle(Color.White, radius = dot, center = Offset(x(i), y(p.value)))
            drawCircle(colour, radius = dot * 0.7f, center = Offset(x(i), y(p.value)))
        }
    }
}

// ---------------------------------------------------------------- picking one for a doctor

/** Her sheets as a list to send one from — the consultation's attach sheet. */
@Composable
fun LabPicker(
    reports: List<LabReport>,
    loaded: Boolean,
    sending: Boolean,
    onPick: (LabReport) -> Unit,
    onAdd: () -> Unit,
) {
    val c = Sadora.colors
    val l = strings.labs
    when {
        reports.isEmpty() && !loaded -> Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) { SadoraLoader(size = 32.dp) }
        reports.isEmpty() -> {
            Text(l.pickEmpty, style = Sadora.type.body, color = c.muted)
            SadoraButton(l.title, onClick = onAdd, tone = ButtonTone.Secondary)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            reports.take(PickerMax).forEach { report ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.cardSmall)
                        .background(c.surface2)
                        .pressable(enabled = !sending, role = Role.Button) { onPick(report) }
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ArtTile(Res.drawable.ic3d_lab, tint = c.primary, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(strings.dates.dayMonthYear(report.takenOn), report.place).joinToString(" · "),
                            style = Sadora.type.h3,
                            color = c.text,
                        )
                        Text(l.countLine(report.values.size, report.flaggedCount), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                    }
                }
            }
            if (sending) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SadoraLoader(size = 28.dp) }
        }
    }
}

/** The latest sheets are the ones a consultation is about; older ones are a scroll away in the section. */
private const val PickerMax = 10

// ---------------------------------------------------------------- formatting

/** Numbers and ranges as the app writes them: a comma for decimals, no trailing zeros. */
internal object LabFormat {
    fun number(value: Double): String {
        val rounded = kotlin.math.round(value * 1000) / 1000
        val whole = rounded.toLong()
        if (rounded == whole.toDouble()) return whole.toString()
        return rounded.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }

    fun result(value: LabValue): String =
        listOfNotNull(value.value?.let(::number) ?: value.valueText, value.unit).joinToString(" ")

    fun range(value: LabValue): String? = range(value.refLow, value.refHigh) ?: value.refText

    fun range(low: Double?, high: Double?): String? = when {
        low != null && high != null -> "${number(low)}–${number(high)}"
        high != null -> "< ${number(high)}"
        low != null -> "> ${number(low)}"
        else -> null
    }

    /** "4,5", "4.5" and " 4.5 " are all a number; anything else is a result in words. */
    fun parse(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
