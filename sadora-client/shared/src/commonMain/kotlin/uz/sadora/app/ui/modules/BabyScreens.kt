package uz.sadora.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.daysUntil
import uz.sadora.app.data.BabyController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.deviceToday
import uz.sadora.app.nav.Route
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.parseTypedDate
import uz.sadora.app.ui.components.typedDateError
import uz.sadora.app.ui.journey.agoLabel
import uz.sadora.app.ui.journey.rememberNow
import uz.sadora.contract.Baby
import uz.sadora.contract.BabyDay
import uz.sadora.contract.BabySex
import uz.sadora.contract.DiaperKind
import uz.sadora.contract.Limits
import uz.sadora.contract.SaveBabyRequest
import uz.sadora.contract.VaccineStatus

/** A caption that reads as a sentence: the caption style's tracking is for labels. */
internal val noteStyle @Composable get() = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified)

/**
 * "Chaqaloq" — the chosen baby's day.
 *
 * The three things done most, many times a day, are one tap each at the top: a feed
 * opens the feeding timer, a sleep starts and stops in place, a nappy is two taps. Under
 * them the day's counts, the week's bars, and the three slower pages — growth, vaccines,
 * milestones — each with the one fact worth seeing without opening it.
 */
@Composable
fun BabyScreen(
    babies: BabyController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val b = strings.babies
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { babies.refresh() }
    var diaperSheet by remember { mutableStateOf(false) }
    val savedToast = b.saved

    val overview = babies.overview
    val sleeping = overview?.sleeping
    val now = rememberNow(sleeping != null)

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(
                b.title,
                onBack = onClose,
                trailing = babies.active?.let { baby -> { SadoraButton(b.edit, onClick = { onOpen(Route.BabyEdit(baby.id)) }, tone = ButtonTone.Ghost, fillWidth = false) } },
            )
            if (babies.loaded && babies.babies.isEmpty()) {
                ScreenContent {
                    item {
                        EmptyState(
                            title = b.emptyTitle,
                            body = b.emptyBody,
                            actionText = b.addBaby,
                            onAction = { onOpen(Route.BabyEdit(null)) },
                            art = Res.drawable.ic3d_baby,
                        )
                    }
                }
                return@Column
            }
            val baby = babies.active
            if (baby == null) {
                Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) }
                return@Column
            }
            ScreenContent {
                item {
                    ChipFlowRow {
                        babies.babies.forEach { each ->
                            SelectChip(
                                label = each.name,
                                selected = each.id == baby.id,
                                onClick = {
                                    babies.select(each.id)
                                    scope.launch { babies.refreshOverview() }
                                },
                            )
                        }
                        SelectChip(label = "+ " + b.addBaby, selected = false, onClick = { onOpen(Route.BabyEdit(null)) })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ArtTile(Res.drawable.ic3d_baby, tint = c.primary, size = 48.dp)
                        Column {
                            Text(baby.name, style = Sadora.type.h2, color = c.text)
                            Text(b.age(baby.birthDate.daysUntil(deviceToday())), style = Sadora.type.body, color = c.muted)
                        }
                    }
                }

                // ---- the three quick actions
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        QuickTile(Res.drawable.ic3d_bottle, b.feed, Modifier.weight(1f)) { onOpen(Route.Feeding) }
                        QuickTile(
                            Res.drawable.ic3d_sleep,
                            if (sleeping != null) b.awake else b.asleep,
                            Modifier.weight(1f),
                            highlighted = sleeping != null,
                        ) {
                            scope.launch {
                                val ok = if (sleeping != null) babies.endSleep() else babies.startSleep()
                                if (ok) onToast(savedToast)
                            }
                        }
                        QuickTile(Res.drawable.ic3d_diaper, b.diaper, Modifier.weight(1f)) { diaperSheet = true }
                    }
                }
                if (sleeping != null) {
                    item {
                        SadoraCard(padding = Spacing.sm) {
                            Text(b.sleepingFor(hoursMinutes((now - sleeping.startedAt).inWholeMinutes)), style = Sadora.type.h3, color = c.text)
                        }
                    }
                }
                babies.error?.let { item { ErrorStrip(it.readable(strings.errors)) } }

                // ---- today
                overview?.let { ov ->
                    val today = ov.days.lastOrNull()
                    item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            CardLabel(b.today)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Figure(b.feeds, "${today?.feeds ?: 0}", Modifier.weight(1f))
                                Figure(b.sleep, hoursMinutes((today?.sleepMinutes ?: 0).toLong()), Modifier.weight(1f))
                                Figure(b.diapers, "${(today?.wetDiapers ?: 0) + (today?.dirtyDiapers ?: 0)}", Modifier.weight(1f))
                            }
                            ov.lastFeed?.let { Text(b.lastFeed(sinceLabel(it.startedAt, now)), style = noteStyle, color = c.muted) }
                            if (sleeping == null) {
                                ov.lastSleepEndedAt?.takeIf { (now - it).inWholeMinutes >= 1 }?.let { Text(b.awakeFor(agoLabel(it, now)), style = noteStyle, color = c.muted) }
                            }
                            ov.lastDiaper?.let { Text(b.lastDiaper(sinceLabel(it.startedAt, now)), style = noteStyle, color = c.muted) }
                        }
                    }
                    if (ov.days.size > 1) {
                        item { WeekCard(ov.days) }
                    }

                    // ---- the slower pages
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            val growth = ov.latestGrowth
                            val growthLine = growth?.let { g ->
                                listOfNotNull(
                                    g.weightG?.let { LabFormat.number(it / 1000.0) + " " + b.kg },
                                    g.lengthCm?.let { LabFormat.number(it) + " " + b.cm },
                                ).joinToString(" · ").takeIf { it.isNotEmpty() }?.let(b::latestGrowth)
                            } ?: b.noGrowth
                            PageRow(Res.drawable.ic3d_scale, b.growth, growthLine) { onOpen(Route.BabyGrowth) }
                            val next = ov.nextVaccine
                            PageRow(
                                Res.drawable.ic3d_shield,
                                b.vaccines,
                                next?.let { b.nextVaccine(it.name, strings.dates.dayMonth(it.dueOn)) } ?: b.vaccinesDone,
                                warn = next?.status == VaccineStatus.OVERDUE,
                            ) { onOpen(Route.BabyVaccines) }
                            PageRow(Res.drawable.ic3d_star, b.milestones, b.milestonesCount(ov.milestonesReached, ov.milestonesTotal)) {
                                onOpen(Route.BabyMilestones)
                            }
                        }
                    }
                }
            }
        }
        SadoraBottomSheet(visible = diaperSheet, title = b.diaper, onDismiss = { diaperSheet = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DiaperKind.entries.forEach { kind ->
                    SadoraButton(
                        b.diaperKind(kind),
                        onClick = {
                            diaperSheet = false
                            scope.launch { if (babies.diaper(kind)) onToast(savedToast) }
                        },
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    art: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    SadoraCard(modifier, padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xxs) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            ArtTile(art, tint = if (highlighted) c.secondary else c.primary, size = 44.dp)
            Text(label, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Column(modifier) {
        Text(value, style = Sadora.type.h2, color = c.text)
        Text(label, style = noteStyle, color = c.muted)
    }
}

/** Sleep hours per day as bars, the day's feeds under each. */
@Composable
private fun WeekCard(days: List<BabyDay>) {
    val c = Sadora.colors
    val b = strings.babies
    val most = (days.maxOfOrNull { it.sleepMinutes } ?: 0).coerceAtLeast(60)
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        CardLabel(b.week + " · " + b.sleep.lowercase())
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { day ->
                val label = "${strings.dates.dayMonth(day.date)}: ${b.sleep} ${hoursMinutes(day.sleepMinutes.toLong())}, ${b.feeds} ${day.feeds}"
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = label },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .fillMaxHeight((day.sleepMinutes.toFloat() / most).coerceIn(0.04f, 1f) * 0.8f)
                            .clip(Radius.chip)
                            .background(c.primary.copy(alpha = 0.75f)),
                    )
                    Text("${day.date.day}", style = noteStyle, color = c.muted)
                }
            }
        }
    }
}

@Composable
private fun PageRow(
    art: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    detail: String,
    warn: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onClick, verticalGap = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(art, tint = c.primary, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = Sadora.type.h3, color = c.text)
                Text(detail, style = noteStyle, color = if (warn) c.warningText else c.muted)
            }
            androidx.compose.material3.Icon(SadoraIcons.ChevronRight, contentDescription = null, tint = c.muted2)
        }
    }
}

/** "hozirgina" under a minute, "1 soat 5 daq oldin" after. */
@Composable
private fun sinceLabel(then: kotlin.time.Instant, now: kotlin.time.Instant): String =
    if ((now - then).inWholeMinutes < 1) strings.babies.justNow else strings.babies.ago(agoLabel(then, now))

@Composable
internal fun hoursMinutes(minutes: Long): String =
    if (minutes <= 0) "0" else strings.common.hoursMinutes((minutes / 60).toInt(), (minutes % 60).toInt())

// ---------------------------------------------------------------- the profile

/** A baby's profile: new, or one of hers to correct or delete. */
@Composable
fun BabyEditScreen(
    id: String?,
    babies: BabyController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val b = strings.babies
    val scope = rememberCoroutineScope()
    val existing: Baby? = id?.let { wanted -> babies.babies.firstOrNull { it.id == wanted } }
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var sex by remember(existing) { mutableStateOf(existing?.sex) }
    var born by remember(existing) {
        mutableStateOf(existing?.birthDate?.let { "${it.day}.${it.month.ordinal + 1}.${it.year}" }.orEmpty())
    }
    var weight by remember(existing) { mutableStateOf(existing?.birthWeightG?.toString().orEmpty()) }
    var length by remember(existing) { mutableStateOf(existing?.birthLengthCm?.let(LabFormat::number).orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val savedToast = b.saved

    val date = parseTypedDate(born)
    val dateError = typedDateError(born, allowFuture = false)
    val ready = name.isNotBlank() && sex != null && date != null && dateError == null && !saving

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(if (id == null) b.newBaby else b.editBaby, onBack = onClose)
            ScreenContent {
                item {
                    SadoraCard(padding = Spacing.sm) {
                        SadoraTextField(name, { name = acceptText(it, Limits.BABY_NAME_MAX) }, label = b.name)
                        CardLabel(b.sex)
                        ChipFlowRow {
                            SelectChip(b.girl, selected = sex == BabySex.GIRL, onClick = { sex = BabySex.GIRL })
                            SelectChip(b.boy, selected = sex == BabySex.BOY, onClick = { sex = BabySex.BOY })
                        }
                        Text(b.sexNote, style = noteStyle, color = c.muted2)
                        SadoraTextField(born, { born = acceptText(it, 10) }, label = b.birthDate, placeholder = "12.08.2026", error = dateError)
                        // One per row: the labels are long, and two side by side broke in different places.
                        SadoraTextField(
                            weight,
                            { weight = acceptText(it.filter(Char::isDigit), 5) },
                            label = b.birthWeight,
                            placeholder = "3200",
                            keyboardType = KeyboardType.Number,
                        )
                        SadoraTextField(
                            length,
                            { length = acceptText(it, 5) },
                            label = b.birthLength,
                            placeholder = "50",
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                }
                babies.error?.takeIf { !saving }?.let { item { ErrorStrip(it.readable(strings.errors)) } }
                item {
                    SadoraButton(
                        strings.common.save,
                        enabled = ready,
                        onClick = {
                            val birth = date ?: return@SadoraButton
                            val chosen = sex ?: return@SadoraButton
                            saving = true
                            babies.clearError()
                            scope.launch {
                                val saved = babies.save(
                                    id,
                                    SaveBabyRequest(
                                        name = name.trim(),
                                        sex = chosen,
                                        birthDate = birth,
                                        birthWeightG = weight.toIntOrNull(),
                                        birthLengthCm = LabFormat.parse(length),
                                    ),
                                )
                                saving = false
                                if (saved != null) {
                                    onToast(savedToast)
                                    onClose()
                                }
                            }
                        },
                    )
                }
                if (id != null) {
                    item { SadoraButton(b.delete, onClick = { confirmDelete = true }, tone = ButtonTone.Destructive) }
                }
            }
        }
        SadoraDialog(
            visible = confirmDelete,
            title = b.deleteTitle,
            body = b.deleteBody,
            confirmText = b.delete,
            onConfirm = {
                confirmDelete = false
                scope.launch { if (id != null && babies.delete(id)) onClose() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The card on the postpartum journey that opens the baby's page. */
@Composable
fun BabyEntryCard(babies: BabyController, onOpen: (Route) -> Unit) {
    val c = Sadora.colors
    val b = strings.babies
    LaunchedEffect(Unit) { if (!babies.loaded) babies.refresh() }
    val baby = babies.active
    SadoraCard(onClick = { onOpen(Route.Baby) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.ic3d_baby, tint = c.primary, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(baby?.name ?: b.entryTitle, style = Sadora.type.h3, color = c.text)
                Text(baby?.let { b.age(it.birthDate.daysUntil(deviceToday())) } ?: b.entryBody, style = Sadora.type.body, color = c.muted)
            }
        }
    }
}
