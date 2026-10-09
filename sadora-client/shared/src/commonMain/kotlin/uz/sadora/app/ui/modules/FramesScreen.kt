package uz.sadora.app.ui.modules

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import uz.sadora.app.data.FrameController
import uz.sadora.app.data.PartnerController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.Avatar
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.FramedAvatar
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.WornBadgeMark
import uz.sadora.app.ui.partner.AskPartnerContent
import uz.sadora.contract.FrameProduct
import uz.sadora.contract.FrameState
import uz.sadora.contract.FrameUnlock
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequestKind

/**
 * Her avatar frames: a large preview of her photo in the frame she is looking at, then
 * the frames by how they are had — Gul, a badge, money. Tapping a tile previews it and
 * opens what can be done with it: wear it, buy it, or see which badge opens it.
 *
 * Opened by tapping her photo on Profile; the photo itself is changed from here too.
 */
@Composable
fun FramesScreen(
    state: AppState,
    frames: FrameController,
    onClose: () -> Unit,
    onEditPhoto: () -> Unit,
    onOpenBadges: () -> Unit,
    modifier: Modifier = Modifier,
    partner: PartnerController? = null,
) {
    val t = strings.frames
    val c = Sadora.colors
    val type = Sadora.type
    var previewKey by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<FrameState?>(null) }
    var asking by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        frames.load()
        frames.loadStorePrices()
    }

    val board = frames.board
    val shown = previewKey ?: state.wornFrame

    Box(modifier) {
        Column {
            SadoraTopBar(t.title, onBack = onClose)
            ScreenContent {
                item {
                    SadoraCard(verticalGap = Spacing.xs) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            FramedAvatar(shown, size = 136.dp) { inner -> Avatar(state.name, size = inner, photoUrl = state.avatarUrl) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                Text(shown?.let(t::name) ?: state.name, style = type.h2, color = c.text)
                                WornBadgeMark(state.wornBadge, size = 24.dp)
                            }
                            Text(t.subtitle, style = type.body, color = c.muted, textAlign = TextAlign.Center)
                            board?.let { Text(t.balance(it.coins), style = type.h3, color = c.textAccent) }
                            notice?.let { Text(it, style = type.body, color = c.primary, textAlign = TextAlign.Center) }
                            SadoraButton(t.changePhoto, onEditPhoto, tone = ButtonTone.Secondary)
                        }
                    }
                }
                if (board == null && frames.busy) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), color = c.primary)
                        }
                    }
                }
                val all = board?.frames.orEmpty()
                listOf(
                    FrameUnlock.COINS to t.coinsSection,
                    FrameUnlock.BADGE to t.badgeSection,
                    FrameUnlock.PAID to t.paidSection,
                ).forEach { (unlock, title) ->
                    val group = all.filter { it.unlock == unlock }
                    if (group.isEmpty()) return@forEach
                    item(key = "label-$unlock") { CardLabel(title) }
                    group.chunked(3).forEachIndexed { row, tiles ->
                        item(key = "row-$unlock-$row") {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                tiles.forEach { frame ->
                                    FrameTile(
                                        frame = frame,
                                        state = state,
                                        frames = frames,
                                        worn = board?.worn == frame.key,
                                        previewing = previewKey == frame.key,
                                        onClick = {
                                            previewKey = frame.key
                                            notice = null
                                            frames.clearError()
                                            open = frame
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - tiles.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        SadoraBottomSheet(
            visible = open != null && !asking,
            title = open?.key?.let(t::name).orEmpty(),
            onDismiss = {
                open = null
                previewKey = null
            },
        ) {
            open?.let { frame ->
                // The board may have moved on (bought, worn) while the sheet is up.
                val live = board?.frames?.firstOrNull { it.key == frame.key } ?: frame
                FrameSheet(
                    frame = live,
                    frames = frames,
                    worn = board?.worn == live.key,
                    coins = board?.coins ?: 0,
                    canAsk = partner != null,
                    onAsk = { asking = true },
                    onOpenBadges = {
                        open = null
                        onOpenBadges()
                    },
                    onDone = { message ->
                        open = null
                        previewKey = null
                        notice = message
                    },
                )
            }
        }
        if (partner != null) {
            SadoraBottomSheet(visible = asking, title = strings.partner.askTitle, onDismiss = { asking = false }) {
                AskPartnerContent(
                    partner = partner,
                    kind = PaymentRequestKind.FRAME,
                    frame = open?.key,
                    onClose = {
                        asking = false
                        open = null
                    },
                )
            }
        }
    }
}

@Composable
private fun FrameTile(
    frame: FrameState,
    state: AppState,
    frames: FrameController,
    worn: Boolean,
    previewing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.frames
    val c = Sadora.colors
    val type = Sadora.type
    SadoraCard(
        modifier = modifier.then(if (worn || previewing) Modifier.border(2.dp, c.primary, Radius.card) else Modifier),
        padding = Spacing.xs,
        verticalGap = Spacing.xxs,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            FramedAvatar(frame.key, size = 72.dp) { inner -> Avatar(state.name, size = inner, photoUrl = state.avatarUrl) }
            Text(
                t.name(frame.key).orEmpty(),
                style = type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    worn -> t.wearing
                    frame.owned -> t.owned
                    frame.unlock == FrameUnlock.COINS -> t.coins(frame.coinCost ?: 0)
                    frame.unlock == FrameUnlock.BADGE -> "🔒"
                    else -> frame.product?.let { priceText(frames, it) }.orEmpty()
                },
                style = type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = if (worn || frame.owned) c.primary else c.muted,
                maxLines = 1,
            )
        }
    }
}

/** What can be done with one frame: wear or take off what she owns, otherwise the way to get it. */
@Composable
private fun FrameSheet(
    frame: FrameState,
    frames: FrameController,
    worn: Boolean,
    coins: Int,
    canAsk: Boolean,
    onAsk: () -> Unit,
    onOpenBadges: () -> Unit,
    onDone: (String?) -> Unit,
) {
    val t = strings.frames
    val c = Sadora.colors
    val type = Sadora.type
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FramedAvatar(frame.key, size = 112.dp) { inner -> Box(Modifier.size(inner)) }
        if (frame.animated) Text("✦ " + t.animated, style = type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.textAccent)
    }
    frames.error?.let { Text(it.readable(), style = type.body, color = c.warning) }

    when {
        frame.owned && worn -> SadoraButton(
            t.takeOff,
            enabled = !frames.busy,
            tone = ButtonTone.Secondary,
            onClick = { scope.launch { if (frames.wear(null)) onDone(null) } },
        )
        frame.owned -> SadoraButton(
            t.wear,
            enabled = !frames.busy,
            onClick = { scope.launch { if (frames.wear(frame.key)) onDone(null) } },
        )
        frame.unlock == FrameUnlock.COINS -> {
            val cost = frame.coinCost ?: 0
            Text(t.buyTitle, style = type.h3, color = c.text)
            Text(t.buyCoinsBody(cost), style = type.body, color = c.muted)
            Text(t.balance(coins), style = type.body, color = c.textAccent)
            if (coins < cost) Text(t.short(cost - coins), style = type.body, color = c.warning)
            SadoraButton(
                t.buyCoins(cost),
                enabled = !frames.busy && coins >= cost,
                onClick = { scope.launch { if (frames.buyWithCoins(frame.key)) onDone(t.bought) } },
            )
        }
        frame.unlock == FrameUnlock.BADGE -> {
            val badge = frame.badge?.let { strings.badges.name(it) } ?: frame.badge.orEmpty()
            Text(t.badgeLock(badge), style = type.body, color = c.text)
            SadoraButton(strings.badges.viewAll, onOpenBadges, tone = ButtonTone.Secondary)
        }
        else -> frame.product?.let { product -> PaidContent(frames, product, canAsk, onAsk) { onDone(t.bought) } }
    }
}

/** A paid frame: the store's sheet in a store build, Payme or Click elsewhere, or Yaqinim. */
@Composable
private fun PaidContent(frames: FrameController, product: FrameProduct, canAsk: Boolean, onAsk: () -> Unit, onBought: () -> Unit) {
    val t = strings.frames
    val c = Sadora.colors
    val type = Sadora.type
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val waiting = frames.paying
    val inStore = frames.store != null && frames.storeProductId(product) != null
    // A store build sells a digital thing through the store only, never Payme or Click.
    val direct = if (frames.store != null) emptyList() else product.providers.filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }

    LifecycleResumeEffect(waiting?.transactionId) {
        val job = if (waiting == null) null else scope.launch { if (frames.awaitPayment()) onBought() }
        onPauseOrDispose { job?.cancel() }
    }

    Text(t.paidBody, style = type.body, color = c.text)
    when {
        waiting != null -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.5.dp)
                Text(t.paying, style = type.h3, color = c.text)
            }
            SadoraButton(t.payReopen, { uriHandler.openUri(waiting.url) }, tone = ButtonTone.Secondary)
            SadoraButton(strings.common.cancel, { frames.cancelCheckout() }, tone = ButtonTone.Ghost)
        }
        inStore -> {
            SadoraButton(
                t.buy(priceText(frames, product)),
                enabled = !frames.busy && !frames.storePending,
                onClick = { scope.launch { if (frames.buyInStore(product)) onBought() } },
            )
            if (frames.storePending) Text(t.storePending, style = type.body, color = c.muted)
        }
        direct.isEmpty() -> Text(t.noProvider, style = type.body, color = c.muted)
        else -> direct.forEach { provider ->
            SadoraButton(
                strings.partner.payWith(if (provider == PaymentProvider.PAYME) "Payme" else "Click") + " · " + priceText(frames, product),
                enabled = !frames.busy,
                onClick = {
                    scope.launch {
                        val session = frames.checkout(product.key, provider) ?: return@launch
                        uriHandler.openUri(session.url)
                    }
                },
            )
        }
    }
    if (canAsk && waiting == null) SadoraButton(t.askYaqinim, onAsk, tone = ButtonTone.Secondary)
}

/** The store's own price in a store build; ours everywhere else. */
@Composable
private fun priceText(frames: FrameController, product: FrameProduct): String =
    frames.storeProductId(product)?.takeIf { frames.store != null }?.let { frames.storePrices[it] }
        ?: strings.doctors.price(Fmt.sum(product.priceMinor))
