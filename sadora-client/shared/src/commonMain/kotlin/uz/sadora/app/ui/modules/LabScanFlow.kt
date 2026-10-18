package uz.sadora.app.ui.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.app.data.ApiFailure
import uz.sadora.app.data.LabController
import uz.sadora.app.data.readable
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.deviceToday
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CameraAccess
import uz.sadora.app.ui.components.CameraShutter
import uz.sadora.app.ui.components.CapturedPhoto
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ClayHopDots
import uz.sadora.app.ui.components.CompanionOr
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.LiveCamera
import uz.sadora.app.ui.components.LocalCompanion
import uz.sadora.app.ui.components.PhotoEdge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.parseTypedDate
import uz.sadora.app.ui.components.pressable
import uz.sadora.app.ui.components.rememberOpenAppSettings
import uz.sadora.app.ui.components.rememberPhotoCapture
import uz.sadora.app.ui.components.typedDateError
import uz.sadora.contract.LabPhotoUpload
import uz.sadora.contract.LabRanges
import uz.sadora.contract.LabReport
import uz.sadora.contract.LabScanResult
import uz.sadora.contract.Limits
import uz.sadora.contract.PetPose
import uz.sadora.contract.SaveLabReportRequest
import uz.sadora.contract.SaveLabValue

/**
 * "Tahlilni skanerlash" — the pages, the wait, and the rows to check, in one screen.
 *
 * Unlike the food scanner a sheet can be more than one page, so the shutter collects
 * pages and "O'qish" sends them together, as one use of the allowance. What comes back
 * is never saved as it is: it opens in the editor, with the photos beside it, and only
 * her "Saqlash" writes it down. Typing it in is offered at every step that can fail.
 */
@Composable
fun LabScanScreen(
    labs: LabController,
    onManualEntry: () -> Unit,
    onSaved: (LabReport) -> Unit,
    onUpgrade: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val l = strings.labs
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<CapturedPhoto>() }
    var stage by remember { mutableStateOf<LabStage>(LabStage.Framing) }
    // An answer to pages she gave up waiting on cannot land over the next attempt.
    var attempt by remember { mutableIntStateOf(0) }
    val addPage: (CapturedPhoto) -> Unit = { if (pages.size < Limits.LAB_PHOTOS_MAX) pages.add(it) }
    val picker = rememberPhotoCapture(maxEdge = PhotoEdge.Document, onCaptured = addPage)
    val errors = strings.errors

    fun read() {
        val mine = ++attempt
        stage = LabStage.Reading
        scope.launch {
            val result = labs.scan(pages.map { LabPhotoUpload(it.base64, it.mimeType) })
            if (mine != attempt) return@launch
            stage = when {
                result == null && (labs.error is ApiFailure.LimitReached || labs.error is ApiFailure.PremiumRequired) -> LabStage.OutOfScans
                result == null -> LabStage.Failed(labs.error?.readable(errors))
                !result.isLabReport -> LabStage.Failed(result.message ?: l.notLab, notLab = true)
                else -> LabStage.Review(result)
            }
        }
    }

    when (val current = stage) {
        LabStage.Framing -> PageCollector(
            pages = pages,
            onCaptured = addPage,
            gallery = picker::pickFromGallery,
            onRemove = { pages.removeAt(it) },
            onRead = ::read,
            onManualEntry = onManualEntry,
            onClose = onClose,
            modifier = modifier,
        )

        LabStage.Reading -> ReadingSheet(
            onCancel = {
                attempt++
                stage = LabStage.Framing
            },
            modifier = modifier,
        )

        is LabStage.Failed -> LabScanProblem(
            title = if (current.notLab) l.notLab else l.failed,
            body = current.message?.takeUnless { it == l.notLab } ?: l.failedBody,
            actionText = strings.common.retry,
            onAction = {
                pages.clear()
                stage = LabStage.Framing
            },
            onManualEntry = onManualEntry,
            onClose = onClose,
            modifier = modifier,
        )

        LabStage.OutOfScans -> LabScanProblem(
            title = l.outOfScans,
            body = l.outOfScansBody,
            actionText = l.upgrade,
            onAction = onUpgrade,
            onManualEntry = onManualEntry,
            onClose = onClose,
            modifier = modifier,
        )

        is LabStage.Review -> LabEditor(
            title = l.reviewTitle,
            note = l.reviewNote,
            initial = current.result.toDraft(),
            photos = pages.toList(),
            labs = labs,
            onSave = { request ->
                val saved = labs.save(null, request)
                if (saved != null) {
                    // Her own pages are not downloaded back to show them on the sheet.
                    pages.forEachIndexed { index, page ->
                        runCatching { Base64.decode(page.base64) }.getOrNull()?.let { labs.rememberPhoto(saved.id, index, it) }
                    }
                    onSaved(saved)
                }
            },
            onClose = onClose,
            modifier = modifier,
        )
    }
}

private sealed interface LabStage {
    data object Framing : LabStage
    data object Reading : LabStage
    data class Failed(val message: String?, val notLab: Boolean = false) : LabStage
    data object OutOfScans : LabStage
    data class Review(val result: LabScanResult) : LabStage
}

@Composable
private fun PageCollector(
    pages: List<CapturedPhoto>,
    onCaptured: (CapturedPhoto) -> Unit,
    gallery: () -> Unit,
    onRemove: (Int) -> Unit,
    onRead: () -> Unit,
    onManualEntry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val l = strings.labs
    val shutter = remember { CameraShutter() }
    var access by remember { mutableStateOf(CameraAccess.Starting) }
    val full = pages.size >= Limits.LAB_PHOTOS_MAX
    val live = access == CameraAccess.Live && !full

    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar(l.scanTitle, onBack = onClose)

        Column(
            Modifier.weight(1f).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(l.frameHint, style = Sadora.type.h3, color = c.text, textAlign = TextAlign.Center)
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(Radius.card).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                LiveCamera(
                    shutter = shutter,
                    onAccess = { access = it },
                    onCaptured = onCaptured,
                    modifier = Modifier.fillMaxSize(),
                    maxEdge = PhotoEdge.Document,
                )
                when (access) {
                    // A page is taller than it is wide: the frame says so.
                    CameraAccess.Live -> Box(
                        Modifier
                            .fillMaxHeight(0.9f)
                            .aspectRatio(0.71f)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), Radius.cardSmall),
                    )

                    CameraAccess.Starting -> SadoraLoader(size = 36.dp)

                    CameraAccess.Denied -> Column(
                        Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(strings.modules.cameraDenied, style = Sadora.type.h3, color = Color.White, textAlign = TextAlign.Center)
                        Text(strings.modules.cameraDeniedBody, style = Sadora.type.body, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        SadoraButton(strings.modules.cameraOpenSettings, rememberOpenAppSettings(), tone = ButtonTone.Secondary)
                    }

                    CameraAccess.Missing -> Text(
                        strings.modules.cameraMissing,
                        style = Sadora.type.body,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }
            Text(l.lightHint, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, textAlign = TextAlign.Center)

            if (pages.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    pages.forEachIndexed { index, page -> PageChip(page, index) { onRemove(index) } }
                    Box(Modifier.weight(1f))
                    Text(l.pages(pages.size, Limits.LAB_PHOTOS_MAX), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                }
            }
        }

        Column(
            Modifier.padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SideAction(SadoraIcons.Document, l.gallery, enabled = !full, onClick = gallery)
                Box(
                    Modifier
                        .size(76.dp)
                        .graphicsLayer { alpha = if (live) 1f else 0.4f }
                        .clip(Radius.chip)
                        .background(c.heroGradient)
                        .pressable(enabled = live, pressedScale = 0.92f, role = Role.Button, onClick = shutter::fire),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SadoraIcons.Camera, contentDescription = if (pages.isEmpty()) l.shutter else l.addPage, Modifier.size(28.dp), tint = c.onPrimary)
                }
                SideAction(SadoraIcons.Pencil, l.manual, onClick = onManualEntry)
            }
            SadoraButton(l.read, onClick = onRead, enabled = pages.isNotEmpty())
        }
    }
}

/** A page she took, small, with the cross that takes it back out. */
@Composable
private fun PageChip(page: CapturedPhoto, index: Int, onRemove: (() -> Unit)?) {
    val c = Sadora.colors
    val label = if (onRemove != null) strings.labs.removePage + " ${index + 1}" else strings.labs.photo(index + 1)
    val bitmap by produceState<ImageBitmap?>(null, page) {
        value = withContext(Dispatchers.Default) {
            runCatching { Base64.decode(page.base64).decodeToImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(width = 40.dp, height = 52.dp)
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .then(if (onRemove != null) Modifier.noRippleClickable(role = Role.Button, onClick = onRemove) else Modifier)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.TopEnd,
    ) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        if (onRemove != null) Box(
            Modifier.padding(2.dp).size(16.dp).clip(Radius.chip).background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = Color.White)
        }
    }
}

@Composable
private fun SideAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Sadora.colors
    Column(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .noRippleClickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(52.dp).clip(Radius.chip).background(c.surface2), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, Modifier.size(24.dp), tint = c.text)
        }
        Text(label, style = Sadora.type.body, color = c.muted)
    }
}

@Composable
private fun ReadingSheet(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val l = strings.labs
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar("", onBack = onCancel)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompanionOr(PetPose.THINK, size = 132.dp, fallback = { SadoraLoader(size = 44.dp) })
            if (LocalCompanion.current != null) ClayHopDots()
            Text(l.reading, style = Sadora.type.h3, color = c.text)
            Text(l.readingWait, style = Sadora.type.body, color = c.muted)
        }
        Box(Modifier.padding(Spacing.screen)) {
            SadoraButton(strings.common.cancel, onCancel, tone = ButtonTone.Ghost)
        }
    }
}

@Composable
private fun LabScanProblem(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    onManualEntry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val l = strings.labs
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar(l.scanTitle, onBack = onClose)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        ) {
            EmptyState(
                title = title,
                body = body,
                actionText = actionText,
                failed = true,
                onAction = onAction,
                art = Res.drawable.ic3d_camera,
            )
            SadoraButton(l.manual, onManualEntry, tone = ButtonTone.Secondary)
        }
    }
}

// ---------------------------------------------------------------- typing a sheet in, or correcting one

/** One row as the editor holds it: text, because half-typed numbers are text. */
internal data class RowDraft(
    val key: Int,
    val markerCode: String? = null,
    val name: String = "",
    val value: String = "",
    val unit: String = "",
    val low: String = "",
    val high: String = "",
    /** The range as printed when it is not two numbers; carried, not edited. */
    val refText: String? = null,
) {
    val complete: Boolean get() = name.isNotBlank() && value.isNotBlank()

    fun toRequest() = LabFormat.parse(value).let { number ->
        SaveLabValue(
            markerCode = markerCode,
            name = name.trim(),
            value = number,
            valueText = if (number == null) value.trim() else null,
            unit = unit.trim().takeIf { it.isNotEmpty() },
            refLow = LabFormat.parse(low),
            refHigh = LabFormat.parse(high),
            refText = refText,
        )
    }
}

internal data class SheetDraft(
    val date: String,
    val place: String = "",
    val note: String = "",
    val rows: List<RowDraft> = emptyList(),
)

private fun LocalDate.typed() = "${day}.${month.ordinal + 1}.$year"

private fun SaveLabValue.toDraft(key: Int) = RowDraft(
    key = key,
    markerCode = markerCode,
    name = name,
    value = value?.let(LabFormat::number) ?: valueText.orEmpty(),
    unit = unit.orEmpty(),
    low = refLow?.let(LabFormat::number).orEmpty(),
    high = refHigh?.let(LabFormat::number).orEmpty(),
    refText = refText,
)

private fun LabScanResult.toDraft() = SheetDraft(
    date = (takenOn ?: deviceToday()).typed(),
    place = place.orEmpty(),
    rows = values.mapIndexed { index, value -> value.toDraft(index) },
)

private fun LabReport.toDraft() = SheetDraft(
    date = takenOn.typed(),
    place = place.orEmpty(),
    note = note.orEmpty(),
    rows = values.mapIndexed { index, v ->
        // The name she sees is the marker's common one; saving it back keeps the code.
        RowDraft(
            key = index,
            markerCode = v.markerCode,
            name = v.name,
            value = v.value?.let(LabFormat::number) ?: v.valueText.orEmpty(),
            unit = v.unit.orEmpty(),
            low = v.refLow?.let(LabFormat::number).orEmpty(),
            high = v.refHigh?.let(LabFormat::number).orEmpty(),
            refText = v.refText,
        )
    },
)

/** A sheet typed in from nothing, or one of hers corrected. */
@Composable
fun LabEditScreen(
    id: String?,
    labs: LabController,
    onClose: () -> Unit,
    onSaved: (LabReport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val l = strings.labs
    var existing by remember(id) { mutableStateOf(id?.let { labs.cached(it) }) }
    LaunchedEffect(id) { if (id != null && existing == null) existing = labs.report(id) }
    if (id != null && existing == null) {
        Column(modifier) {
            SadoraTopBar(l.editTitle, onBack = onClose)
            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) }
        }
        return
    }
    LabEditor(
        title = if (id == null) l.newTitle else l.editTitle,
        note = null,
        initial = existing?.toDraft() ?: SheetDraft(date = deviceToday().typed(), rows = listOf(RowDraft(key = 0))),
        photos = emptyList(),
        labs = labs,
        onSave = { request -> labs.save(id, request)?.let(onSaved) },
        onClose = onClose,
        modifier = modifier,
    )
}

/**
 * The sheet as fields: the date, the laboratory, a note, and a card per row.
 *
 * A row is complete with a name and a result; the range is optional, because a sheet
 * without one is still a sheet — it just gets no flag, and the editor shows the flag a
 * row will get as she types the range, so "past" is never a surprise after saving.
 */
@Composable
private fun LabEditor(
    title: String,
    note: String?,
    initial: SheetDraft,
    photos: List<CapturedPhoto>,
    labs: LabController,
    onSave: suspend (SaveLabReportRequest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val l = strings.labs
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(initial.date) }
    var place by remember { mutableStateOf(initial.place) }
    var sheetNote by remember { mutableStateOf(initial.note) }
    val rows = remember { mutableStateListOf<RowDraft>().apply { addAll(initial.rows) } }
    var nextKey by remember { mutableIntStateOf((initial.rows.maxOfOrNull { it.key } ?: -1) + 1) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { labs.loadMarkers() }

    val parsedDate = parseTypedDate(date)
    val dateError = typedDateError(date, allowFuture = false)
    val ready = parsedDate != null && dateError == null && rows.isNotEmpty() && rows.all { it.complete }

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(title, onBack = onClose)
            ScreenContent {
                if (note != null) {
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
                            Text(note, style = Sadora.type.body, color = c.text)
                        }
                    }
                }
                if (photos.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            photos.forEachIndexed { index, photo -> PageChip(photo, index, onRemove = null) }
                        }
                    }
                }
                item {
                    SadoraCard(padding = Spacing.sm) {
                        SadoraTextField(
                            date,
                            { date = acceptText(it, DateFieldMax) },
                            label = l.date,
                            placeholder = l.dateHint,
                            error = dateError,
                        )
                        SadoraTextField(
                            place,
                            { place = acceptText(it, Limits.LAB_PLACE_MAX) },
                            label = l.place,
                            placeholder = l.placeHint,
                        )
                    }
                }
                item { CardLabel(l.rows) }
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    RowEditor(
                        row = row,
                        onChange = { rows[index] = it },
                        onRemove = { rows.removeAt(index) },
                    )
                }
                item {
                    SadoraButton(l.addRow, onClick = { picking = true }, tone = ButtonTone.Secondary, icon = SadoraIcons.Plus)
                }
                item {
                    SadoraTextField(
                        sheetNote,
                        { sheetNote = acceptText(it, Limits.LAB_NOTE_MAX) },
                        label = l.note,
                        placeholder = l.noteHint,
                        singleLine = false,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        labs.error?.takeIf { !saving }?.let { ErrorStrip(it.readable(strings.errors)) }
                        when {
                            rows.isEmpty() -> Text(l.noRows, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                            rows.any { !it.complete } -> Text(l.rowIncomplete, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                        }
                        SadoraButton(
                            strings.common.save,
                            enabled = ready && !saving,
                            onClick = {
                                val takenOn = parsedDate ?: return@SadoraButton
                                saving = true
                                labs.clearError()
                                scope.launch {
                                    onSave(
                                        SaveLabReportRequest(
                                            takenOn = takenOn,
                                            place = place.trim().takeIf { it.isNotEmpty() },
                                            note = sheetNote.trim().takeIf { it.isNotEmpty() },
                                            values = rows.map { it.toRequest() },
                                            photos = photos.map { LabPhotoUpload(it.base64, it.mimeType) },
                                            scanned = photos.isNotEmpty(),
                                        ),
                                    )
                                    saving = false
                                }
                            },
                        )
                        Text(l.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                    }
                }
            }
        }
        SadoraBottomSheet(visible = picking, title = l.pickMarker, onDismiss = { picking = false }) {
            val have = rows.mapNotNull { it.markerCode }.toSet()
            labs.markers.groupBy { it.group }.forEach { (group, markers) ->
                Text(l.group(group), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                ChipFlowRow {
                    markers.forEach { marker ->
                        SelectChip(
                            label = marker.name,
                            selected = marker.code in have,
                            onClick = {
                                rows.add(RowDraft(key = nextKey++, markerCode = marker.code, name = marker.name, unit = marker.unit))
                                picking = false
                            },
                        )
                    }
                }
            }
            SadoraButton(
                l.customMarker,
                onClick = {
                    rows.add(RowDraft(key = nextKey++))
                    picking = false
                },
                tone = ButtonTone.Outline,
            )
        }
    }
}

@Composable
private fun RowEditor(row: RowDraft, onChange: (RowDraft) -> Unit, onRemove: () -> Unit) {
    val c = Sadora.colors
    val l = strings.labs
    val flag = LabRanges.flagOf(LabFormat.parse(row.value), LabFormat.parse(row.low), LabFormat.parse(row.high))
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraTextField(
                row.name,
                { onChange(row.copy(name = acceptText(it, Limits.LAB_NAME_MAX))) },
                label = l.name,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .clip(Radius.chip)
                    .background(c.surface2)
                    .noRippleClickable(role = Role.Button, onClick = onRemove)
                    .clearAndSetSemantics { contentDescription = l.removeRow },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", style = Sadora.type.h2, color = c.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraTextField(
                row.value,
                { onChange(row.copy(value = acceptText(it, Limits.LAB_VALUE_TEXT_MAX))) },
                label = l.value,
                placeholder = l.valueHint,
                modifier = Modifier.weight(1.3f),
            )
            SadoraTextField(
                row.unit,
                { onChange(row.copy(unit = acceptText(it, Limits.LAB_UNIT_MAX))) },
                label = l.unit,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraTextField(
                row.low,
                { onChange(row.copy(low = acceptText(it, NumberFieldMax))) },
                label = l.low,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            SadoraTextField(
                row.high,
                { onChange(row.copy(high = acceptText(it, NumberFieldMax))) },
                label = l.high,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            row.refText?.let { Text("${l.range}: $it", style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2, modifier = Modifier.weight(1f)) }
                ?: Box(Modifier.weight(1f))
            flag?.let { FlagBadge(it) }
        }
    }
}

/** `27.08.2026` — nothing longer is a date. */
private const val DateFieldMax = 10
private const val NumberFieldMax = 12
