package uz.sadora.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.PartnerController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.QrCode
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDivider
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.rememberShareAction
import uz.sadora.contract.Limits
import uz.sadora.contract.PartnerMessage
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerWebLink

/** What the person who follows her can send in one tap. */
internal val FollowerKinds = listOf(
    PartnerMessageKind.HEART,
    PartnerMessageKind.HUG,
    PartnerMessageKind.THINKING,
    PartnerMessageKind.CUSTOM,
)

/** What she can ask for in one tap. */
internal val HerKinds = listOf(
    PartnerMessageKind.TEA,
    PartnerMessageKind.SWEETS,
    PartnerMessageKind.REST,
    PartnerMessageKind.CALL,
    PartnerMessageKind.QUIET,
    PartnerMessageKind.HUG,
    PartnerMessageKind.CUSTOM,
)

/**
 * The small messages between the two of them: a row of one-tap chips, the last few
 * messages, and — on the follower's side — her latest request pinned on top with the
 * two answers it wants.
 */
@Composable
fun PartnerMessagesCard(
    linkId: String,
    partner: PartnerController,
    title: String,
    otherName: String,
    kinds: List<PartnerMessageKind>,
    /** True on the follower's side: her open request gets "on my way" and "done". */
    answersRequests: Boolean,
    onSent: (String) -> Unit,
) {
    val t = strings.partner
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var writing by remember { mutableStateOf(false) }
    // "Malika so'radi", not "Malika Karimova so'radi": these are messages between two
    // people who know each other.
    val name = firstName(otherName)
    val items = partner.messages[linkId].orEmpty()

    fun send(kind: PartnerMessageKind, text: String? = null) {
        scope.launch { if (partner.send(linkId, kind, text)) onSent(t.sent) }
    }

    // Her latest message, when it asks for something and nothing has come back since.
    val openRequest = items.firstOrNull()?.takeIf { answersRequests && !it.fromMe && (it.kind.isRequest || it.kind == PartnerMessageKind.CUSTOM) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
    openRequest?.let { request ->
        SadoraCard {
            CardLabel(t.asked(name), color = c.textAccent)
            Text(request.label(), style = Sadora.type.h2, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SadoraButton(t.kind(PartnerMessageKind.ON_IT), { send(PartnerMessageKind.ON_IT) }, enabled = !partner.busy, modifier = Modifier.weight(1f))
                SadoraButton(t.kind(PartnerMessageKind.DONE), { send(PartnerMessageKind.DONE) }, tone = ButtonTone.Outline, enabled = !partner.busy, modifier = Modifier.weight(1f))
            }
        }
    }

    SadoraCard {
        CardLabel(title)
        ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xs) {
            kinds.forEach { kind ->
                SelectChip(t.kind(kind), selected = false, onClick = {
                    if (kind == PartnerMessageKind.CUSTOM) writing = true else send(kind)
                })
            }
        }
        SadoraDivider()
        CardLabel(t.messagesTitle)
        if (items.isEmpty()) {
            Text(t.noMessages, style = Sadora.type.body, color = c.muted)
        } else {
            items.take(ShownInCard).forEach { MessageLine(it, name) }
        }
    }
    }

    CustomMessageSheet(
        visible = writing,
        busy = partner.busy,
        onDismiss = { writing = false },
        onSend = { text ->
            writing = false
            send(PartnerMessageKind.CUSTOM, text)
        },
    )
}

@Composable
private fun MessageLine(message: PartnerMessage, otherName: String) {
    val t = strings.partner
    val c = Sadora.colors
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Column(Modifier.weight(1f)) {
            // Body type, smaller: the caption style's tracking spread a name into letters.
            Text(
                (if (message.fromMe) t.youPrefix else otherName),
                style = Sadora.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = if (message.fromMe) c.muted else c.textAccent,
            )
            Text(message.label(), style = Sadora.type.body.copy(fontWeight = FontWeight.Medium), color = c.text)
        }
        Text(
            strings.dates.ago(message.createdAt, Clock.System.now()),
            style = Sadora.type.body.copy(fontSize = 13.sp),
            color = c.muted2,
        )
    }
}

@Composable
private fun PartnerMessage.label(): String =
    if (kind == PartnerMessageKind.CUSTOM) "✍️ " + text.orEmpty() else strings.partner.kind(kind)

@Composable
private fun CustomMessageSheet(visible: Boolean, busy: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val t = strings.partner
    val focus = LocalFocusManager.current
    var text by remember(visible) { mutableStateOf("") }
    SadoraBottomSheet(visible = visible, title = t.customTitle, onDismiss = onDismiss) {
        SadoraTextField(
            value = text,
            onValueChange = { text = it.take(Limits.PARTNER_MESSAGE_MAX) },
            placeholder = t.customHint,
            singleLine = false,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            trailing = "${text.length}/${Limits.PARTNER_MESSAGE_MAX}",
        )
        SadoraButton(t.send, { onSend(text.trim()) }, enabled = !busy && text.isNotBlank(), icon = SadoraIcons.Send)
    }
}

internal fun firstName(full: String): String = full.trim().substringBefore(' ').ifBlank { full }

/** Fewer than the server keeps: the card is a glance, not a history. */
private const val ShownInCard = 8

// ---------------------------------------------------------------- the web link

private val WebTtlDays = listOf(1, 3, 7)

/**
 * The browser link for someone without the app: what it shows, how long it lives, how
 * often it was opened. Like the doctor's QR code, the link exists on this phone only on
 * the answer that made it.
 */
@Composable
fun PartnerWebLinkCard(
    partner: PartnerController,
    /** What the link will show: what her person sees, or the defaults when there is none. */
    permissions: PartnerPermissions,
    stage: uz.sadora.app.model.LifeStage,
    onToast: (String) -> Unit,
) {
    // Only the parts her stage has: a cycle link that names "pregnancy" would read as news.
    val shown = permissions.forStage(stage)
    val t = strings.partner
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val share = rememberShareAction()
    var days by remember { mutableStateOf(3) }
    val live: PartnerWebLink? = partner.state?.webLink
    val fresh = partner.freshWebLink?.takeIf { it.id == live?.id }

    SadoraCard {
        CardLabel(t.webTitle)
        Text(t.webBody, style = Sadora.type.body, color = c.muted)
        Text(t.webShows + " " + shownNames(shown), style = Sadora.type.body, color = c.text)

        if (live != null) {
            val url = fresh?.url
            if (url != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode(url, contentDescription = t.webTitle) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                SadoraBadge(t.webExpires(expiry(live)), BadgeTone.Success)
                Text(if (live.viewCount == 0) t.webNever else t.webViews(live.viewCount), style = Sadora.type.body.copy(fontSize = 13.sp), color = c.muted)
            }
            if (url != null) {
                SadoraButton(t.webShare, { share(t.webShareMessage(url)) }, icon = SadoraIcons.Share)
            } else {
                Text(t.webOutBody, style = Sadora.type.body.copy(fontSize = 13.sp), color = c.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SadoraButton(t.webNew, { scope.launch { partner.createWebLink(days * 24, shown) } }, tone = ButtonTone.Outline, enabled = !partner.busy, modifier = Modifier.weight(1f))
                SadoraButton(t.webRevoke, { scope.launch { if (partner.revokeWebLink()) onToast(t.webRevoked) } }, tone = ButtonTone.Ghost, modifier = Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                WebTtlDays.forEach { option ->
                    SelectChip(t.webDays(option), selected = days == option, onClick = { days = option }, modifier = Modifier.weight(1f))
                }
            }
            SadoraButton(t.webCreate, { scope.launch { partner.createWebLink(days * 24, shown) } }, enabled = !partner.busy && !partner.isOffline)
        }
    }
}

/** The parts that mean something in [stage]; the rest are sent as off. Mirrors the ticks offered. */
internal fun PartnerPermissions.forStage(stage: uz.sadora.app.model.LifeStage): PartnerPermissions {
    val cycleStage = stage.predictsCycle
    val pregnant = stage == uz.sadora.app.model.LifeStage.Pregnancy || stage == uz.sadora.app.model.LifeStage.Postpartum
    val careStage = stage == uz.sadora.app.model.LifeStage.Postpartum || stage == uz.sadora.app.model.LifeStage.Perimenopause ||
        stage == uz.sadora.app.model.LifeStage.Menopause
    return copy(
        cycle = cycle && cycleStage,
        fertile = fertile && cycleStage,
        pregnancy = pregnancy && pregnant,
        care = care && careStage,
    )
}

@Composable
private fun shownNames(p: PartnerPermissions): String {
    val t = strings.partner
    return buildList {
        if (p.cycle) add(t.permCycle)
        if (p.fertile) add(t.permFertile)
        if (p.mood) add(t.permMood)
        if (p.symptoms) add(t.permSymptoms)
        if (p.pregnancy) add(t.permPregnancy)
        if (p.appointments) add(t.permAppointments)
        if (p.care) add(t.permCare)
    }.joinToString(", ").lowercase()
}

private fun expiry(link: PartnerWebLink): String {
    val at = link.expiresAt.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${at.day}.${(at.month.ordinal + 1).toString().padStart(2, '0')} ${Fmt.time(at)}"
}
