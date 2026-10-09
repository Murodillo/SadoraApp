package uz.sadora.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.PartnerController
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.LifeStage
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.QrCode
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraDivider
import uz.sadora.app.ui.components.SadoraSwitch
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.SettingsRow
import uz.sadora.app.ui.components.rememberShareAction
import uz.sadora.contract.FollowedPerson
import uz.sadora.contract.PartnerInvite
import uz.sadora.contract.PartnerLink
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerRelation
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile

/**
 * "Yaqinim" — her side: make a code, say yes to the person who typed it, choose what
 * they see, pause or end it. Below it, the people she follows herself, if any.
 *
 * Like the doctor's QR code, everything about what leaves the app is said on this screen
 * rather than behind a link: what the person will see, and the list of what they never
 * will.
 */
@Composable
fun YaqinimScreen(
    state: AppState,
    partner: PartnerController,
    onOpenPerson: (String) -> Unit,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.partner
    val scope = rememberCoroutineScope()
    var relation by remember { mutableStateOf(PartnerRelation.HUSBAND) }
    var confirmEnd by remember { mutableStateOf(false) }
    var codeSheet by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        partner.refresh(silent = partner.state != null)
        partner.loadMyRequest()
        // A request to pay someone she follows: the push about it lands on this screen.
        partner.loadIncoming()
    }
    // A code from a shared link opens the sheet prefilled — also when it arrives while
    // this screen is already open — and is consumed by opening it.
    LaunchedEffect(state.pendingPartnerCode) {
        val code = state.pendingPartnerCode ?: return@LaunchedEffect
        state.pendingPartnerCode = null
        prefill = code
        codeSheet = true
    }

    val current = partner.state
    val link = current?.link
    // Her person's messages: on open, and whenever the count says something new arrived.
    LaunchedEffect(link?.id, link?.unread) { link?.id?.let { partner.loadMessages(it) } }
    val invite = current?.invite
    val fresh = partner.freshInvite

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.title, onBack = onClose)
            ScreenContent {
                partner.error?.let { failure ->
                    item { ErrorStrip(failure.partnerReadable(), onRetry = partner::clearError) }
                }
                if (partner.myRequest != null) item { MyRequestCard(partner) }
                partner.incoming.forEach { request ->
                    item(key = "payreq-${request.id}") { IncomingRequestCard(request, partner, onToast) }
                }

                when {
                    link?.status == PartnerLinkStatus.PENDING -> item {
                        RequestCard(link, busy = partner.busy, onApprove = {
                            scope.launch { if (partner.approve()) onToast(t.approved) }
                        }, onDecline = { scope.launch { partner.end() } })
                    }
                    link != null -> {
                        item { LinkedCard(link, onPause = { paused -> scope.launch { partner.pause(paused) } }) }
                        if (link.status == PartnerLinkStatus.ACTIVE) item {
                            PartnerMessagesCard(
                                linkId = link.id,
                                partner = partner,
                                title = t.askFrom(firstName(link.partnerName)),
                                otherName = link.partnerName,
                                kinds = HerKinds,
                                answersRequests = false,
                                onSent = onToast,
                            )
                        }
                        item {
                            PermissionsCard(state.lifeStage, link.permissions) { next ->
                                scope.launch { partner.savePermissions(next) }
                            }
                        }
                        item { SadoraButton(t.disconnect, { confirmEnd = true }, tone = ButtonTone.Destructive) }
                    }
                    fresh?.code != null -> item {
                        CodeCard(fresh, onNew = { scope.launch { partner.invite(fresh.relation) } }, onCancel = {
                            scope.launch { partner.end() }
                        })
                    }
                    invite != null -> item {
                        InviteOutCard(invite, busy = partner.busy, onNew = { scope.launch { partner.invite(invite.relation) } }, onCancel = {
                            scope.launch { partner.end() }
                        })
                    }
                    else -> item {
                        IntroCard(relation, onRelation = { relation = it }, busy = partner.busy, offline = partner.isOffline) {
                            scope.launch { partner.invite(relation) }
                        }
                    }
                }

                if (link == null) {
                    item { NeverCard() }
                }

                item {
                    PartnerWebLinkCard(partner, permissions = link?.permissions ?: PartnerPermissions(), stage = state.lifeStage, onToast = onToast)
                }

                item {
                    FollowingCard(
                        following = partner.following,
                        onOpen = onOpenPerson,
                        onHaveCode = { codeSheet = true },
                    )
                }
            }
        }

        PartnerCodeSheet(
            visible = codeSheet,
            partner = partner,
            initialCode = prefill,
            onDismiss = { codeSheet = false },
            onAccepted = { followed ->
                codeSheet = false
                onToast(t.requestSent(followed.name))
            },
        )
    }

    SadoraDialog(
        visible = confirmEnd,
        title = t.disconnectConfirmTitle,
        body = t.disconnectConfirmBody(link?.partnerName.orEmpty()),
        confirmText = t.disconnect,
        onConfirm = {
            confirmEnd = false
            scope.launch { if (partner.end()) onToast(t.disconnected) }
        },
        onDismiss = { confirmEnd = false },
    )
}

@Composable
private fun IntroCard(
    relation: PartnerRelation,
    onRelation: (PartnerRelation) -> Unit,
    busy: Boolean,
    offline: Boolean,
    onCreate: () -> Unit,
) {
    val t = strings.partner
    val c = Sadora.colors
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.ic3d_partner, tint = c.primary, size = 46.dp)
            Text(t.introTitle, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
        }
        Text(t.introBody, style = Sadora.type.body, color = c.muted)
        CardLabel(t.whoLabel)
        ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xs) {
            PartnerRelation.entries.forEach { option ->
                SelectChip(t.relation(option), selected = option == relation, onClick = { onRelation(option) })
            }
        }
        SadoraButton(if (busy) t.creating else t.createInvite, onCreate, enabled = !busy && !offline)
    }
}

/** The code, big enough to read out over the phone, and the share sheet beside it. */
@Composable
private fun CodeCard(invite: PartnerInvite, onNew: () -> Unit, onCancel: () -> Unit) {
    val t = strings.partner
    val c = Sadora.colors
    val share = rememberShareAction()
    val code = invite.code.orEmpty()
    SadoraCard {
        Text(t.codeTitle, style = Sadora.type.h3, color = c.text)
        Text(
            code,
            style = Sadora.type.display.copy(fontWeight = FontWeight.Bold),
            color = c.textAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        )
        invite.url?.let { url ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrCode(url, contentDescription = t.codeTitle)
            }
        }
        Text(t.codeSteps, style = Sadora.type.body, color = c.muted)
        SadoraBadge(t.codeExpires(whenText(invite)), BadgeTone.Neutral)
        SadoraButton(t.shareCode, { share(t.shareMessage(code, invite.url)) }, icon = SadoraIcons.Share)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(t.newCode, onNew, tone = ButtonTone.Outline, modifier = Modifier.weight(1f))
            SadoraButton(t.cancelInvite, onCancel, tone = ButtonTone.Ghost, modifier = Modifier.weight(1f))
        }
    }
}

/** An invite made earlier: the code is gone from this phone, only its expiry is known. */
@Composable
private fun InviteOutCard(invite: PartnerInvite, busy: Boolean, onNew: () -> Unit, onCancel: () -> Unit) {
    val t = strings.partner
    val c = Sadora.colors
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.ic3d_message, tint = c.primary, size = 46.dp)
            Text(t.inviteOut, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
        }
        Text(t.inviteOutBody(whenText(invite)), style = Sadora.type.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(t.newCode, onNew, enabled = !busy, modifier = Modifier.weight(1f))
            SadoraButton(t.cancelInvite, onCancel, tone = ButtonTone.Ghost, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RequestCard(link: PartnerLink, busy: Boolean, onApprove: () -> Unit, onDecline: () -> Unit) {
    val t = strings.partner
    val c = Sadora.colors
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.ic3d_partner, tint = c.primary, size = 46.dp)
            Column(Modifier.weight(1f)) {
                Text(t.requestTitle(link.partnerName), style = Sadora.type.h3, color = c.text)
                Text(t.relation(link.relation), style = Sadora.type.body, color = c.muted)
            }
        }
        Text(t.requestBody, style = Sadora.type.body, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(t.decline, onDecline, tone = ButtonTone.Secondary, modifier = Modifier.weight(1f))
            SadoraButton(t.approve, onApprove, enabled = !busy, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LinkedCard(link: PartnerLink, onPause: (Boolean) -> Unit) {
    val t = strings.partner
    val c = Sadora.colors
    val paused = link.status == PartnerLinkStatus.PAUSED
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.ic3d_partner, tint = if (paused) c.muted2 else c.primary, size = 46.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (paused) t.pausedTitle(link.partnerName) else t.linkedTitle(link.partnerName),
                    style = Sadora.type.h3,
                    color = c.text,
                )
                Text(
                    t.relation(link.relation) + " · " +
                        (link.lastViewedAt?.let { t.lastViewed(strings.dates.ago(it, Clock.System.now())) } ?: t.neverViewed),
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
        }
        SadoraDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text(t.pause, style = Sadora.type.h3, color = c.text)
                Text(t.pauseNote, style = Sadora.type.body, color = c.muted)
            }
            SadoraSwitch(checked = paused, onCheckedChange = onPause)
        }
    }
}

/**
 * The ticks, each with what it shows in one line. Only the parts her stage has are
 * offered: a pregnancy tick means nothing to a woman tracking her cycle, and an unused
 * switch is one more thing to wonder about.
 */
@Composable
private fun PermissionsCard(stage: LifeStage, shown: PartnerPermissions, onChange: (PartnerPermissions) -> Unit) {
    val t = strings.partner
    SadoraCard {
        CardLabel(t.showsTitle)
        if (stage.predictsCycle) {
            PermissionRow(t.permCycle, t.permCycleNote, shown.cycle) { onChange(shown.copy(cycle = it)) }
            PermissionRow(t.permFertile, t.permFertileNote, shown.fertile) { onChange(shown.copy(fertile = it)) }
        }
        if (stage == LifeStage.Pregnancy || stage == LifeStage.Postpartum) {
            PermissionRow(t.permPregnancy, t.permPregnancyNote, shown.pregnancy) { onChange(shown.copy(pregnancy = it)) }
        }
        PermissionRow(t.permMood, t.permMoodNote, shown.mood) { onChange(shown.copy(mood = it)) }
        PermissionRow(t.permSymptoms, t.permSymptomsNote, shown.symptoms) { onChange(shown.copy(symptoms = it)) }
        PermissionRow(t.permAppointments, t.permAppointmentsNote, shown.appointments) { onChange(shown.copy(appointments = it)) }
        if (stage == LifeStage.Postpartum || stage == LifeStage.Perimenopause || stage == LifeStage.Menopause) {
            PermissionRow(t.permCare, t.permCareNote, shown.care) { onChange(shown.copy(care = it)) }
        }
        SadoraDivider()
        CardLabel(t.neverTitle)
        t.neverList.forEach { NeverLine(it) }
    }
}

@Composable
private fun PermissionRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Sadora.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Sadora.type.h3, color = c.text)
            Text(note, style = Sadora.type.body, color = c.muted)
        }
        SadoraSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NeverCard() {
    val t = strings.partner
    SadoraCard {
        CardLabel(t.neverTitle)
        t.neverList.forEach { NeverLine(it) }
    }
}

@Composable
private fun NeverLine(text: String) {
    val c = Sadora.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(SadoraIcons.Lock, contentDescription = null, Modifier.size(IconSize.sm), tint = c.muted2)
        Text(text, style = Sadora.type.body, color = c.text)
    }
}

@Composable
private fun FollowingCard(following: List<FollowedPerson>, onOpen: (String) -> Unit, onHaveCode: () -> Unit) {
    val t = strings.partner
    SadoraCard(padding = Spacing.xs) {
        if (following.isNotEmpty()) {
            CardLabel(t.followingTitle, modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs))
            following.forEach { person ->
                SettingsRow(Res.drawable.ic3d_heart, person.name, value = statusLabel(person.status)) { onOpen(person.linkId) }
            }
        }
        SettingsRow(SadoraIcons.Plus, t.haveCode, onClick = onHaveCode)
    }
}

@Composable
internal fun statusLabel(status: PartnerLinkStatus): String? {
    val t = strings.partner
    return when (status) {
        PartnerLinkStatus.PENDING -> t.statusPending
        PartnerLinkStatus.PAUSED -> t.statusPaused
        else -> null
    }
}

/**
 * Typing a code someone sent. Used by her screen, by the follower-only app, and by a
 * shared link that arrives while the app is open.
 */
@Composable
fun PartnerCodeSheet(
    visible: Boolean,
    partner: PartnerController,
    initialCode: String,
    onDismiss: () -> Unit,
    onAccepted: (FollowedPerson) -> Unit,
) {
    val t = strings.partner
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var code by remember(visible, initialCode) { mutableStateOf(formatCode(initialCode)) }
    SadoraBottomSheet(visible = visible, title = t.enterCodeTitle, onDismiss = {
        partner.clearError()
        onDismiss()
    }) {
        SadoraTextField(
            value = code,
            onValueChange = {
                code = formatCode(it)
                partner.clearError()
            },
            label = t.codeLabel,
            placeholder = t.codeHint,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            error = partner.error?.partnerReadable(),
        )
        SadoraButton(
            t.follow,
            onClick = {
                scope.launch { partner.accept(code)?.let(onAccepted) }
            },
            enabled = !partner.busy && normaliseCode(code).length == CodeLength,
        )
    }
}

private fun whenText(invite: PartnerInvite): String {
    val at = invite.expiresAt.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${at.day}.${(at.month.ordinal + 1).toString().padStart(2, '0')} ${Fmt.time(at)}"
}

/** The server's alphabet: no 0/O or 1/I/L, so a code read aloud cannot be misheard. */
private const val CodeAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
internal const val CodeLength = 8

internal fun normaliseCode(raw: String): String = raw.uppercase().filter { it in CodeAlphabet }.take(CodeLength)

/** `k7m2qp4x` as typed becomes `K7M2-QP4X`, the way it was handed over. */
internal fun formatCode(raw: String): String = normaliseCode(raw).chunked(4).joinToString("-")
