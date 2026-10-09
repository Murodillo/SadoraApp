package uz.sadora.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.PartnerController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraSwitch
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.rememberShareAction
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.IncomingPaymentRequest
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequest
import uz.sadora.contract.PaymentRequestKind
import uz.sadora.contract.PaymentRequestLimits
import uz.sadora.contract.PaymentRequestStatus

/**
 * "Ask Yaqinim to pay", her half: the form in a sheet, then what became of it.
 *
 * Opened from the paywall (Premium) and from a doctor's pay sheet (a consultation). With
 * a request already open the sheet shows that one instead of a second form — the server
 * keeps one at a time.
 */
@Composable
fun AskPartnerContent(
    partner: PartnerController,
    kind: PaymentRequestKind,
    doctorId: String? = null,
    initialYear: Boolean = true,
    onClose: () -> Unit,
    /** For a PET request: which legendary pet. */
    pet: uz.sadora.contract.PetKind? = null,
) {
    val t = strings.partner
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var year by remember { mutableStateOf(initialYear) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { partner.loadMyRequest() }

    val current = partner.myRequest?.takeIf { it.status == PaymentRequestStatus.OPEN }
    if (current != null) {
        MyRequestBody(partner, current)
        SadoraButton(strings.common.close, onClose, tone = ButtonTone.Secondary)
        return
    }

    Text(
        when (kind) {
            PaymentRequestKind.PREMIUM -> t.askBodyPremium
            PaymentRequestKind.CONSULTATION -> t.askBodyConsultation
            PaymentRequestKind.PET -> strings.pet.askBody
        },
        style = Sadora.type.body,
        color = c.muted,
    )
    if (kind == PaymentRequestKind.PREMIUM) {
        CardLabel(t.askPeriod)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SelectChip(t.period(year = false), selected = !year, onClick = { year = false }, modifier = Modifier.weight(1f))
            SelectChip(t.period(year = true), selected = year, onClick = { year = true }, modifier = Modifier.weight(1f))
        }
    }
    SadoraTextField(
        value = note,
        onValueChange = { note = it.take(PaymentRequestLimits.NOTE_MAX) },
        placeholder = t.askNoteHint,
        singleLine = false,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailing = "${note.length}/${PaymentRequestLimits.NOTE_MAX}",
    )
    partner.error?.let { Text(it.partnerReadable(), style = Sadora.type.body, color = c.warning) }
    SadoraButton(
        t.askSend,
        enabled = !partner.busy,
        icon = SadoraIcons.Send,
        onClick = {
            scope.launch {
                partner.askToPay(
                    kind = kind,
                    period = if (kind == PaymentRequestKind.PREMIUM) (if (year) BillingPeriod.YEAR else BillingPeriod.MONTH) else null,
                    doctorId = doctorId,
                    note = note,
                    pet = pet,
                )
            }
        },
    )
    SadoraButton(strings.common.cancel, onClose, tone = ButtonTone.Ghost)
}

/** Her request on the Yaqinim screen, while it is open or just closed. */
@Composable
fun MyRequestCard(partner: PartnerController) {
    val request = partner.myRequest ?: return
    SadoraCard { MyRequestBody(partner, request) }
}

@Composable
private fun MyRequestBody(partner: PartnerController, request: PaymentRequest) {
    val t = strings.partner
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val share = rememberShareAction()
    val what = request.what()

    CardLabel(t.askTitle)
    Text(what, style = Sadora.type.h3, color = c.text)
    Text(
        t.requestStatus(request.status),
        style = Sadora.type.body,
        color = if (request.status == PaymentRequestStatus.PAID) c.successText else c.muted,
    )
    request.note?.let { Text("“$it”", style = Sadora.type.body, color = c.muted) }
    if (request.status != PaymentRequestStatus.OPEN) return

    if (request.sentToPartner) Text(t.sentToPartner, style = Sadora.type.body, color = c.textAccent)
    Text(t.requestExpires(dateText(request)), style = Sadora.type.caption, color = c.muted)
    Text(t.shareHint, style = Sadora.type.body, color = c.muted)
    SadoraButton(
        t.shareLink,
        enabled = !partner.busy,
        icon = SadoraIcons.Share,
        tone = ButtonTone.Secondary,
        onClick = {
            scope.launch { partner.requestShareUrl()?.let { share(t.shareRequestMessage(what, it)) } }
        },
    )
    SadoraButton(t.cancelRequest, { scope.launch { partner.cancelMyRequest() } }, tone = ButtonTone.Ghost, enabled = !partner.busy)
}

/**
 * A request as the person she asked sees it, above her view: what, how much, and the
 * way to pay. In a store build a Premium gift goes through the store's own sheet; a
 * consultation, a real-world service, is paid by Payme or Click everywhere.
 */
@Composable
fun IncomingRequestCard(
    request: IncomingPaymentRequest,
    partner: PartnerController,
    onToast: (String) -> Unit,
) {
    val t = strings.partner
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val premium = request.kind == PaymentRequestKind.PREMIUM
    val petProduct = request.petProduct?.takeIf { request.kind == PaymentRequestKind.PET }
    var planId by remember(request.id) {
        mutableStateOf(request.plans.firstOrNull { it.period == request.period }?.id ?: request.plans.firstOrNull()?.id)
    }
    val plan = request.plans.firstOrNull { it.id == planId }
    val inStore = (premium || petProduct?.let(partner::storeProductId) != null) && partner.store != null
    val direct = request.providers.filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }
    val waiting = partner.paying

    LifecycleResumeEffect(waiting?.transactionId) {
        val job = if (waiting == null) {
            null
        } else {
            scope.launch { if (partner.awaitRequestPayment()) onToast(t.giftThanks) }
        }
        onPauseOrDispose { job?.cancel() }
    }

    SadoraCard {
        Text(t.incomingTitle(request.fromName), style = Sadora.type.h3, color = c.text)
        Text(
            when (request.kind) {
                PaymentRequestKind.PREMIUM -> "Sadora " + t.premiumWhat(request.period == BillingPeriod.YEAR)
                PaymentRequestKind.CONSULTATION -> t.consultationWhat(request.doctorName)
                PaymentRequestKind.PET -> strings.pet.requestWhat
            },
            style = Sadora.type.body,
            color = c.textAccent,
        )
        request.note?.let { Text("“$it”", style = Sadora.type.body, color = c.muted) }
        Text(t.incomingBody, style = Sadora.type.body, color = c.muted)

        if (premium && request.plans.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                request.plans.sortedBy { it.priceMinor }.forEach { option ->
                    val price = if (inStore) partner.storeProductId(option)?.let { partner.giftPrices[it] }.orEmpty() else strings.doctors.price(Fmt.sum(option.priceMinor))
                    SelectChip(
                        t.period(option.period == BillingPeriod.YEAR) + if (price.isBlank()) "" else " · $price",
                        selected = option.id == planId,
                        onClick = { planId = option.id },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (!premium) {
            Text(strings.doctors.price(Fmt.sum(request.amountMinor)), style = Sadora.type.h2, color = c.text)
        }

        when {
            waiting != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.5.dp)
                    Text(t.payWaiting, style = Sadora.type.h3, color = c.text)
                }
                SadoraButton(t.payReopen, { uriHandler.openUri(waiting.url) }, tone = ButtonTone.Secondary)
                SadoraButton(strings.common.cancel, { partner.cancelRequestCheckout() }, tone = ButtonTone.Ghost)
            }
            inStore && petProduct != null -> {
                val price = partner.storeProductId(petProduct)?.let { partner.giftPrices[it] }.orEmpty()
                SadoraButton(
                    t.giveGift(price),
                    enabled = !partner.busy && !partner.storePending,
                    onClick = { scope.launch { if (partner.payPetInStore(request.id, petProduct)) onToast(t.giftThanks) } },
                )
                if (partner.storePending) Text(t.giftStorePending, style = Sadora.type.body, color = c.muted)
            }
            inStore -> {
                val price = plan?.let(partner::storeProductId)?.let { partner.giftPrices[it] }.orEmpty()
                SadoraButton(
                    t.giveGift(price),
                    enabled = plan != null && !partner.busy && !partner.storePending,
                    onClick = {
                        val chosen = plan ?: return@SadoraButton
                        scope.launch { if (partner.payInStore(request.id, chosen)) onToast(t.giftThanks) }
                    },
                )
                if (partner.storePending) Text(t.giftStorePending, style = Sadora.type.body, color = c.muted)
            }
            direct.isEmpty() -> Text(t.giftNoProvider, style = Sadora.type.body, color = c.muted)
            else -> direct.forEach { provider ->
                SadoraButton(
                    t.payWith(if (provider == PaymentProvider.PAYME) "Payme" else "Click"),
                    enabled = !partner.busy && (!premium || plan != null),
                    onClick = {
                        scope.launch {
                            val session = partner.startRequestCheckout(request.id, provider, if (premium) planId else null) ?: return@launch
                            uriHandler.openUri(session.url)
                        }
                    },
                )
            }
        }
        if (waiting == null) {
            SadoraButton(t.notNow, { scope.launch { partner.declineRequest(request.id) } }, tone = ButtonTone.Ghost, enabled = !partner.busy)
        }
    }
}

/** The follower's own switch for her requests to pay. */
@Composable
fun AcceptRequestsRow(linkId: String, partner: PartnerController) {
    val t = strings.partner
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val accepts = partner.following.firstOrNull { it.linkId == linkId }?.acceptsPaymentRequests ?: return
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text(t.acceptRequests, style = Sadora.type.h3, color = c.text)
                Text(t.acceptRequestsNote, style = Sadora.type.body, color = c.muted)
            }
            SadoraSwitch(checked = accepts, onCheckedChange = { scope.launch { partner.setAcceptsPaymentRequests(linkId, it) } })
        }
    }
}

@Composable
private fun PaymentRequest.what(): String {
    val t = strings.partner
    return when (kind) {
        PaymentRequestKind.PREMIUM -> t.premiumWhat(period == BillingPeriod.YEAR)
        PaymentRequestKind.CONSULTATION -> t.consultationWhat(doctorName)
        PaymentRequestKind.PET -> strings.pet.requestWhat
    }
}

@Composable
private fun dateText(request: PaymentRequest): String =
    strings.dates.dayMonth(request.expiresAt.toLocalDateTime(TimeZone.currentSystemDefault()).date)
