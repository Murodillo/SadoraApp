package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.app.data.DoctorController
import uz.sadora.app.data.readable
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.doctorPriceLabel
import uz.sadora.app.i18n.ratingLine
import uz.sadora.app.i18n.replyTimeLabel
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.DoctorSort
import uz.sadora.app.model.hasEnoughRatings
import uz.sadora.app.model.specialtiesIn
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.cardSurface
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.pressable
import uz.sadora.contract.DoctorListItem

// "Shifokorlar": every verified doctor in one list, and the two small doors into it —
// the strip at the top of the chat's feed and the card on Bugun. Before this a doctor
// could only be reached by tapping her name on a post she happened to write.

/**
 * The directory: a card per doctor in the server's recommended order, with chips to
 * narrow it and a menu to reorder it. The filter and the sort live on [doctors], so
 * opening a doctor's page and coming back finds the list as she left it.
 */
@Composable
fun DoctorDirectoryScreen(
    doctors: DoctorController,
    onOpenDoctor: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = strings.doctors
    val scope = rememberCoroutineScope()
    // A list already in hand (from Bugun or the chat) is shown at once and refreshed
    // behind it; only a first read may put an error in her way.
    LaunchedEffect(doctors) { doctors.loadDirectory(quiet = doctors.directory.isNotEmpty()) }
    val reload: () -> Unit = { scope.launch { doctors.loadDirectory() } }

    val all = doctors.directory
    val shown = doctors.arrangedDirectory
    val error = doctors.directoryCalls.error

    Column(modifier) {
        SadoraTopBar(d.directoryTitle, onBack = onClose)
        if (all.isNotEmpty()) DirectoryFilters(doctors)
        when {
            !doctors.directoryLoaded && error == null -> DirectorySkeleton()
            // Not an empty directory — one that did not arrive. The way on is to ask again.
            !doctors.directoryLoaded -> EmptyState(
                title = error?.readable().orEmpty(),
                body = "",
                actionText = strings.common.retry,
                onAction = reload,
                glyph = "📡",
            )
            all.isEmpty() -> EmptyState(
                title = d.directoryEmpty,
                body = d.directoryEmptyBody,
                actionText = null,
                onAction = {},
                glyph = "🩺",
            )
            shown.isEmpty() -> EmptyState(
                title = d.filteredEmpty,
                body = d.filteredEmptyBody,
                actionText = d.resetFilters,
                onAction = doctors::resetDirectoryFilter,
                glyph = "🔍",
            )
            else -> ScreenContent {
                error?.let { failure -> item { ErrorStrip(failure.readable(), onRetry = reload) } }
                items(shown.size, key = { shown[it].id }) { index ->
                    val doctor = shown[index]
                    DoctorListCard(doctor, onClick = { onOpenDoctor(doctor.id) })
                }
            }
        }
    }
}

/**
 * The sort menu first, then "Hozir onlayn", "Bepul" and a chip for each specialty the
 * list has. A specialty chip is a toggle: tapping the chosen one again lets it go.
 */
@Composable
private fun DirectoryFilters(doctors: DoctorController) {
    val d = strings.doctors
    val filter = doctors.directoryFilter
    // The chosen specialty keeps its chip even when a refresh took its last doctor
    // away — otherwise the filter would be stuck on with no way to see or clear it.
    val specialties = (specialtiesIn(doctors.directory) + listOfNotNull(filter.specialty)).distinct()
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortMenu(doctors.directorySort, onSelect = { doctors.directorySort = it })
        SelectChip(
            label = d.onlineNow,
            selected = filter.onlineOnly,
            onClick = { doctors.directoryFilter = filter.copy(onlineOnly = !filter.onlineOnly) },
        )
        SelectChip(
            label = d.free,
            selected = filter.freeOnly,
            onClick = { doctors.directoryFilter = filter.copy(freeOnly = !filter.freeOnly) },
        )
        specialties.forEach { specialty ->
            SelectChip(
                label = d.specialty(specialty),
                selected = filter.specialty == specialty,
                onClick = {
                    doctors.directoryFilter = filter.copy(specialty = specialty.takeIf { it != filter.specialty })
                },
            )
        }
    }
}

/**
 * "⇅ Tavsiya etilgan" — a chip that opens the four orders. Drawn like the filter chips
 * beside it, and tinted once the order is not the server's own, so a list that looks
 * reshuffled says why.
 */
@Composable
private fun SortMenu(sort: DoctorSort, onSelect: (DoctorSort) -> Unit) {
    val c = Sadora.colors
    val d = strings.doctors
    var open by remember { mutableStateOf(false) }
    val changed = sort != DoctorSort.Recommended
    Box {
        Row(
            Modifier
                .clip(Radius.chip)
                .background(if (changed) c.primary.copy(alpha = if (c.isDark) 0.22f else 0.12f) else c.surface)
                .border(1.dp, if (changed) c.primary else c.line, Radius.chip)
                .defaultMinSize(minHeight = MinTouchTarget)
                .noRippleClickable(role = Role.Button) { open = true }
                .padding(horizontal = 14.dp, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val fg = if (changed) c.textAccent else c.text
            Text("⇅", style = Sadora.type.body, color = fg)
            Text(d.sortLabel(sort), style = Sadora.type.body.copy(fontWeight = FontWeight.Medium), color = fg)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = Radius.cardSmall,
            containerColor = c.surface,
        ) {
            Text(
                d.sortTitle,
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.muted,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
            )
            DoctorSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            d.sortLabel(option),
                            style = Sadora.type.body.copy(fontWeight = if (option == sort) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (option == sort) c.textAccent else c.text,
                        )
                    },
                    trailingIcon = if (option == sort) {
                        { Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(IconSize.sm), tint = c.textAccent) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * One doctor in the directory: who she is, what patients made of her, what she costs,
 * and how soon she tends to answer. The whole card opens her page.
 */
@Composable
private fun DoctorListCard(doctor: DoctorListItem, onClick: () -> Unit) {
    val c = Sadora.colors
    val d = strings.doctors
    val rated = hasEnoughRatings(doctor.ratingCount)
    SadoraCard(onClick = onClick, verticalGap = Spacing.xs) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
            DoctorAvatar(doctor.fullName, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(doctor.fullName, style = Sadora.type.h3, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    d.specialty(doctor.specialty) + " · " + doctor.workplace,
                    style = Sadora.type.body,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ratingLine(doctor.rating, doctor.ratingCount, d),
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (rated) c.text else c.muted2,
                )
            }
            Text(
                doctorPriceLabel(doctor.priceMinor, d),
                style = Sadora.type.h3,
                color = if (doctor.priceMinor > 0) c.text else c.successText,
            )
        }
        if (doctor.onlineNow || doctor.fastReply) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (doctor.onlineNow) OnlineLabel()
                if (doctor.fastReply) FastReplyChip()
            }
        }
        val facts = listOfNotNull(
            doctor.avgFirstReplyMinutes?.let { replyTimeLabel(it, d) },
            doctor.consultationsTotal.takeIf { it > 0 }?.let { d.consultations(it) },
        )
        if (facts.isNotEmpty()) {
            Text(
                facts.joinToString(" · "),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.muted,
            )
        }
    }
}

/** The green dot and "Onlayn", for a doctor inside her hours and not marked busy. */
@Composable
private fun OnlineLabel() {
    val c = Sadora.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(Radius.chip).background(c.success))
        Text(
            strings.doctors.online,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Medium),
            color = c.successText,
        )
    }
}

/** "Tez javob beradi": earned by the server's rule, never guessed here. */
@Composable
private fun FastReplyChip() {
    val c = Sadora.colors
    Row(
        Modifier
            .clip(Radius.chip)
            .background(c.primary.copy(alpha = if (c.isDark) 0.22f else 0.10f))
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(SadoraIcons.Clock, contentDescription = null, Modifier.size(12.dp), tint = c.textAccent)
        Text(
            strings.doctors.fastReply,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
            color = c.textAccent,
        )
    }
}

/** Three card-shaped placeholders while the first list is on its way. */
@Composable
private fun DirectorySkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(3) {
            SadoraCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(Modifier.size(52.dp), shape = Radius.chip)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skeleton(Modifier.height(16.dp).fillMaxWidth(0.6f))
                        Skeleton(Modifier.height(12.dp).fillMaxWidth(0.8f))
                        Skeleton(Modifier.height(12.dp).fillMaxWidth(0.4f))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- the ways in

/** How many doctors the chat's strip and Bugun's card show before "Hammasi". */
internal const val DoctorStripSize = 4
private const val AskDoctorAvatars = 3

/**
 * The top of the chat's feed: the first few doctors of the recommended order as small
 * cards, and "Hammasi →" into the directory. Drawn edge to edge — a row that scrolls
 * sideways reads as one when it runs off the screen, not when it stops at the margin.
 */
@Composable
internal fun DoctorStrip(
    doctors: List<DoctorListItem>,
    onOpenDoctor: (String) -> Unit,
    onOpenAll: () -> Unit,
) {
    val c = Sadora.colors
    val d = strings.doctors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(d.directoryTitle, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
            Text(
                d.seeAll + " →",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier
                    .clip(Radius.chip)
                    .noRippleClickable(role = Role.Button, onClick = onOpenAll)
                    .padding(vertical = Spacing.xxs),
            )
        }
        Row(
            Modifier
                .bleed(Spacing.screen)
                .horizontalScroll(rememberScrollState())
                .height(IntrinsicSize.Min)
                .padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            doctors.take(DoctorStripSize).forEach { doctor ->
                CompactDoctorCard(doctor, onClick = { onOpenDoctor(doctor.id) })
            }
            Column(
                Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .pressable(role = Role.Button, onClick = onOpenAll)
                    .cardSurface(c, shape = Radius.cardSmall, elevation = 6.dp)
                    .padding(Spacing.sm),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.lg), tint = c.textAccent)
                Text(
                    d.seeAll,
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textAccent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A doctor in the strip: the avatar, her name, her specialty, and online or price. */
@Composable
private fun CompactDoctorCard(doctor: DoctorListItem, onClick: () -> Unit) {
    val c = Sadora.colors
    val d = strings.doctors
    Column(
        Modifier
            .width(148.dp)
            .fillMaxHeight()
            .pressable(onClick = onClick)
            .cardSurface(c, shape = Radius.cardSmall, elevation = 6.dp)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DoctorAvatar(doctor.fullName, size = 40.dp)
        Text(doctor.fullName, style = Sadora.type.h3, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            d.specialty(doctor.specialty),
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (doctor.onlineNow) Box(Modifier.size(8.dp).clip(Radius.chip).background(c.success))
            Text(
                doctorPriceLabel(doctor.priceMinor, d),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
                color = if (doctor.priceMinor > 0) c.text else c.successText,
                maxLines = 1,
            )
        }
    }
}

/**
 * Bugun's "Shifokordan so'rang": one line on what a consultation is, a few faces from
 * the top of the recommended order, and the way into the directory.
 */
@Composable
internal fun AskDoctorCard(doctors: List<DoctorListItem>, onOpen: () -> Unit) {
    val c = Sadora.colors
    val d = strings.doctors
    SadoraCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(d.askDoctorTitle, style = Sadora.type.h3, color = c.text)
                Text(d.askDoctorBody, style = Sadora.type.body, color = c.muted)
            }
            // Overlapping, like a row of people rather than a row of buttons.
            val faces = doctors.take(AskDoctorAvatars)
            val face = 34.dp
            val step = 22.dp
            Box(Modifier.width(face + 4.dp + step * (faces.size - 1).coerceAtLeast(0)).height(face + 4.dp)) {
                faces.forEachIndexed { index, doctor ->
                    Box(Modifier.offset(x = step * index)) { DoctorAvatar(doctor.fullName, size = face) }
                }
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted)
        }
    }
}

/**
 * Widens a child of a padded list by [gutter] on each side, so a sideways row can
 * run to the screen's edges while everything around it keeps the margin.
 */
private fun Modifier.bleed(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}
