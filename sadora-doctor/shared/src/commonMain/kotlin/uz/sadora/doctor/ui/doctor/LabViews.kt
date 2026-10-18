package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.contract.LabFlag
import uz.sadora.contract.LabPhoto
import uz.sadora.contract.LabReport
import uz.sadora.contract.LabValue
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.LabPhotos
import uz.sadora.doctor.data.labRange
import uz.sadora.doctor.data.labResult
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.resources.ic3d_lab
import uz.sadora.doctor.ui.components.ArtTile
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraBottomSheet
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.SystemBackHandler
import uz.sadora.doctor.ui.components.noRippleClickable

// Her lab sheets as the doctor sees them: a section of her record, a card in the
// consultation, and the sheet opened in full. The rows are one composable everywhere, so
// a value reads the same wherever she meets it.

/** The art beside every lab sheet: the record section's tile and the chat card's. */
internal val LabArt = Res.drawable.ic3d_lab

/** A page opened over everything: where it is read from, which one, and its shape. */
internal data class OpenLabPhoto(val from: LabPhotos, val page: Int, val photo: LabPhoto)

/** "01.10.2026 · Invitro": the day the blood was taken, and where. */
internal fun labHeading(report: LabReport): String =
    listOfNotNull(dotDate(report.takenOn), report.place?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")

private fun dotDate(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}.${(date.month.ordinal + 1).toString().padStart(2, '0')}.${date.year}"

/** "12 ta ko'rsatkich · 3 tasi normadan tashqarida · Rasmdan o'qilgan". */
@Composable
private fun labCounts(report: LabReport): String {
    val l = strings.labs
    return listOfNotNull(
        l.values(report.values.size),
        report.flaggedCount.takeIf { it > 0 }?.let(l::flagged),
        l.scanned.takeIf { report.scanned },
    ).joinToString(" · ")
}

/**
 * One sheet in full: its heading, the counts, her note, every row, and the pages she
 * photographed. [photos] is null where no route serves the pages; they are then left out.
 */
@Composable
internal fun LabSheet(
    report: LabReport,
    photos: LabPhotos?,
    doctors: DoctorController,
    onOpenPhoto: (OpenLabPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(labHeading(report), style = Sadora.type.h3, color = c.text)
        Text(labCounts(report), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
        report.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = Sadora.type.body, color = c.muted) }
        LabRows(report.values)
        if (photos != null && report.photos.isNotEmpty()) {
            LabPhotoStrip(report, photos, doctors, onOpenPhoto)
        }
    }
}

@Composable
internal fun LabRows(values: List<LabValue>, expandable: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        values.forEach { LabRow(it, expandable) }
    }
}

/**
 * One row: the name on the left, the result on the right — in the warning colour when
 * it is outside the laboratory's range — and under them the range and the flag. A row
 * the app knows opens a short line on what the marker is. No range, no flag: a value
 * without one is not called normal.
 */
@Composable
internal fun LabRow(row: LabValue, expandable: Boolean = true) {
    val c = Sadora.colors
    val l = strings.labs
    val about = row.about?.trim()?.takeIf { it.isNotEmpty() && expandable }
    var open by remember(row.id) { mutableStateOf(false) }
    val outside = row.flag == LabFlag.LOW || row.flag == LabFlag.HIGH
    val range = labRange(row)
    val expandedText = strings.common.expanded
    val collapsedText = strings.common.collapsed
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (about != null) {
                    Modifier
                        .noRippleClickable(role = Role.Button, onClick = { open = !open })
                        .semantics { stateDescription = if (open) expandedText else collapsedText }
                } else Modifier,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(row.name, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f, fill = false))
                if (about != null) Icon(SadoraIcons.Info, contentDescription = null, Modifier.size(IconSize.sm), tint = c.muted2)
            }
            Text(
                labResult(row) ?: "—",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (outside) c.warningText else c.text,
                textAlign = TextAlign.End,
            )
        }
        if (range != null || row.flag != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (range != null) {
                    Text(
                        "${l.range}: $range",
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                row.flag?.let { LabFlagMark(it) }
            }
        }
        AnimatedVisibility(
            visible = open && about != null,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(160)),
        ) {
            Text(
                about.orEmpty(),
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.fillMaxWidth().clip(Radius.cardSmall).background(c.surface2).padding(Spacing.xs),
            )
        }
    }
}

/** Past and high in the warning colour, as a chip; in range quietly, as a word. */
@Composable
private fun LabFlagMark(flag: LabFlag) {
    val c = Sadora.colors
    val text = strings.labs.flag(flag)
    if (flag == LabFlag.NORMAL) {
        Text(text, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    } else {
        TintChip(text, c.warningText)
    }
}

/** The photographed pages, small, in order; a tap opens one over everything. */
@Composable
private fun LabPhotoStrip(report: LabReport, from: LabPhotos, doctors: DoctorController, onOpen: (OpenLabPhoto) -> Unit) {
    val c = Sadora.colors
    val l = strings.labs
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(l.sheetPhoto, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            report.photos.forEachIndexed { page, photo ->
                LabPhotoImage(
                    from = from,
                    page = page,
                    photo = photo,
                    doctors = doctors,
                    crop = true,
                    contentDescription = l.sheetPhotoNumber(page + 1),
                    modifier = Modifier
                        .height(ThumbHeight)
                        .clip(Radius.cardSmall)
                        .noRippleClickable(role = Role.Image, onClick = { onOpen(OpenLabPhoto(from, page, photo)) }),
                )
            }
        }
    }
}

/** A page, fetched once and drawn at its own proportions; a shimmer until it arrives. */
@Composable
private fun LabPhotoImage(
    from: LabPhotos,
    page: Int,
    photo: LabPhoto,
    doctors: DoctorController,
    crop: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    // A thumbnail stays a sensible shape; the viewer shows the page as it is.
    val ratio = (photo.width.toFloat() / photo.height.coerceAtLeast(1)).let { if (crop) it.coerceIn(0.5f, 1.5f) else it.coerceIn(0.2f, 5f) }
    val bitmap by produceState<ImageBitmap?>(null, from, page) {
        value = doctors.labPhoto(from, page)?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    val picture = bitmap
    if (picture != null) {
        Image(
            picture,
            contentDescription = contentDescription,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            modifier = modifier.aspectRatio(ratio),
        )
    } else {
        Box(modifier.aspectRatio(ratio).background(c.surface2), contentAlignment = Alignment.Center) {
            Skeleton(Modifier.fillMaxSize())
        }
    }
}

/** A page on its own, over everything, at its full width; a tap or back closes it. */
@Composable
internal fun LabPhotoViewer(open: OpenLabPhoto, doctors: DoctorController, onDismiss: () -> Unit) {
    SystemBackHandler(enabled = true, onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .noRippleClickable(onClickLabel = strings.common.close, onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        LabPhotoImage(
            from = open.from,
            page = open.page,
            photo = open.photo,
            doctors = doctors,
            crop = false,
            contentDescription = strings.labs.sheetPhotoNumber(open.page + 1),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A sheet the patient sent into the consultation, as the thread draws it: what it is,
 * when and where, how many rows and how many outside their range, the first of those,
 * and the way into the whole sheet.
 */
@Composable
internal fun LabMessageCard(report: LabReport, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val l = strings.labs
    val c = Sadora.colors
    val flagged = report.values.filter { it.flag == LabFlag.LOW || it.flag == LabFlag.HIGH }
    Column(
        modifier
            .widthIn(max = 320.dp)
            .clip(Radius.card)
            .background(c.surface)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ArtTile(LabArt, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(l.title, style = Sadora.type.h3, color = c.text)
                Text(labHeading(report), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
            }
        }
        Text(
            listOfNotNull(l.values(report.values.size), flagged.size.takeIf { it > 0 }?.let(l::flagged)).joinToString(" · "),
            style = Sadora.type.body,
            color = if (flagged.isEmpty()) c.muted else c.warningText,
        )
        if (flagged.isNotEmpty()) LabRows(flagged.take(CardFlaggedRows), expandable = false)
        PillButton(l.allValues, onClick = onOpen)
    }
}

/** The sheet behind a chat card, every row and its pages; the pages come by the line's own route. */
@Composable
internal fun LabReportSheet(
    report: LabReport?,
    photos: LabPhotos?,
    doctors: DoctorController,
    onOpenPhoto: (OpenLabPhoto) -> Unit,
    onDismiss: () -> Unit,
) {
    SadoraBottomSheet(visible = report != null, title = strings.labs.title, onDismiss = onDismiss) {
        report?.let { LabSheet(it, photos, doctors, onOpenPhoto) }
    }
}

/** How many out-of-range rows a chat card shows before "Barcha ko'rsatkichlar". */
private const val CardFlaggedRows = 3

private val ThumbHeight = 88.dp
