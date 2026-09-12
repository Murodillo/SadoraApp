package uz.sadora.app.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.RewardsController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CoinPill
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.EmojiTile
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.GulMark
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDivider
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SegmentedControl
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.appearFromBelow
import uz.sadora.contract.Redemption
import uz.sadora.contract.RedemptionStatus
import uz.sadora.contract.ShopKind
import uz.sadora.contract.ShopProduct

/**
 * The Gul shop.
 *
 * Three sections and one honest boundary. Premium is delivered by the app the moment
 * the coins are spent; a vitamin or a device is a partner's product and what Gul buys
 * is a discount on it. The card therefore shows the price twice — before and after —
 * because a "15%" with nothing to apply it to would be a number pretending to be an
 * offer.
 *
 * Nothing is hidden behind a lock: a card she cannot afford yet stays visible with the
 * shortfall written on it, which is what makes the balance mean something.
 */
@Composable
fun ShopScreen(
    state: AppState,
    rewards: RewardsController,
    onClose: () -> Unit,
    onPremiumGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.shop
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rewards.loadCatalog()
        rewards.loadRedemptions()
    }

    val kinds = listOf(ShopKind.PREMIUM, ShopKind.VITAMIN, ShopKind.DEVICE)
    var selected by remember { mutableStateOf(0) }
    var confirming by remember { mutableStateOf<ShopProduct?>(null) }
    var issued by remember { mutableStateOf<Redemption?>(null) }
    var premiumIssued by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    Column(modifier) {
        SadoraTopBar(
            t.title,
            onBack = onClose,
            trailing = { CoinPill(state.coins) },
        )

        if (rewards.catalog == null && rewards.busy) {
            ShopSkeleton()
            return@Column
        }

        val kind = kinds[selected]
        val products = rewards.productsOf(kind)

        ScreenContent {
            item {
                SegmentedControl(
                    options = kinds.map { t.tab(it) },
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }

            rewards.error?.let { failure ->
                item { ErrorStrip(failure.readable()) }
            }

            if (products.isEmpty()) {
                item {
                    SadoraCard {
                        Text(t.empty, style = Sadora.type.body, color = c.muted)
                    }
                }
            }

            itemsIndexed(products) { index, product ->
                Box(Modifier.appearFromBelow(index.coerceAtMost(5))) {
                    ProductCard(
                        product = product,
                        balance = state.coins,
                        onRedeem = { confirming = product },
                    )
                }
            }

            if (kind != ShopKind.PREMIUM) {
                item { DisclaimerNote(t.partnerNote) }
            }

            if (rewards.redemptions.isNotEmpty()) {
                item {
                    SadoraCard {
                        Text(t.myCodes, style = Sadora.type.h3, color = c.text)
                        rewards.redemptions.forEachIndexed { index, redemption ->
                            if (index > 0) SadoraDivider()
                            RedemptionRow(redemption)
                        }
                    }
                }
            }
        }
    }

    // The confirmation. Spending is irreversible, so it is a deliberate second tap —
    // and it says exactly what will be taken and what will arrive.
    val pending = confirming
    SadoraBottomSheet(
        visible = pending != null,
        title = pending?.let { t.confirmTitle(it.title) }.orEmpty(),
        onDismiss = { confirming = null },
    ) {
        pending?.let { product ->
            Text(
                if (product.kind == ShopKind.PREMIUM) {
                    t.confirmPremiumBody
                } else {
                    t.confirmBody(Fmt.int(product.coinCost))
                },
                style = Sadora.type.body,
                color = c.muted,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SadoraButton(
                    t.cancel,
                    { confirming = null },
                    tone = ButtonTone.Outline,
                    modifier = Modifier.weight(1f),
                )
                SadoraButton(
                    if (rewards.busy) t.redeeming else t.redeem,
                    {
                        scope.launch {
                            val result = rewards.redeem(product.id)
                            confirming = null
                            if (result != null) {
                                issued = result.redemption
                                premiumIssued = result.premiumGranted
                                copied = false
                                if (result.premiumGranted) onPremiumGranted()
                            }
                        }
                    },
                    enabled = !rewards.busy && product.affordable,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // What she got. Kept as a sheet rather than a toast: a partner code is the whole
    // purchase, and it must not disappear after three seconds.
    val code = issued
    val share = uz.sadora.app.ui.components.rememberShareAction()
    SadoraBottomSheet(
        visible = code != null,
        title = if (premiumIssued) t.issuedPremiumTitle else t.issuedTitle,
        onDismiss = { issued = null },
    ) {
        code?.let { redemption ->
            Text(
                if (premiumIssued) t.issuedPremiumBody else t.issuedBody,
                style = Sadora.type.body,
                color = c.muted,
            )
            if (!premiumIssued) {
                SadoraCard(padding = Spacing.sm) {
                    Text(
                        t.yourCode,
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted,
                    )
                    Text(
                        redemption.code,
                        style = Sadora.type.h2,
                        color = c.text,
                    )
                    redemption.expiresAt?.let { expiry ->
                        Text(
                            t.validUntil(
                                expiry.toLocalDateTime(TimeZone.currentSystemDefault()).date
                                    .let { strings.dates.dayMonth(it) },
                            ),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
                // There is no clipboard in commonMain and one platform helper for it
                // would be a third expect/actual pair; the share sheet already exists
                // and puts the code where she actually needs it — in a message to
                // herself, or straight to the shop.
                SadoraButton(
                    t.copyCode,
                    { share(redemption.code); copied = true },
                    tone = ButtonTone.Outline,
                    icon = SadoraIcons.Share,
                )
                if (copied) {
                    Text(t.codeCopied, style = Sadora.type.body, color = c.success)
                }
            }
        }
    }
}

/**
 * One card in the shop.
 *
 * A Premium row says what it grants; a partner row says the retail price, strikes it
 * through, and puts the discounted one beside it. Both end in the same place: what it
 * costs in Gul, and whether she has that.
 */
@Composable
private fun ProductCard(
    product: ShopProduct,
    balance: Int,
    onRedeem: () -> Unit,
) {
    val t = strings.shop
    val c = Sadora.colors
    val short = (product.coinCost - balance).coerceAtLeast(0)

    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            EmojiTile(
                product.emoji ?: "🎁",
                tint = if (product.kind == ShopKind.PREMIUM) c.primary else c.accent,
                size = 46.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                product.brand?.let {
                    Text(
                        it,
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                        maxLines = 1,
                    )
                }
                Text(product.title, style = Sadora.type.h3, color = c.text)
                product.description?.let {
                    Text(it, style = Sadora.type.body, color = c.muted, maxLines = 3)
                }
            }
            if (product.discountPercent > 0) {
                SadoraBadge(t.discount(product.discountPercent), tone = BadgeTone.Success)
            }
        }

        // What she pays the partner, before and after. Premium has neither: the coins
        // are the whole payment, and a struck-through price would be inventing one.
        val price = product.priceUzs
        val discounted = product.discountedPriceUzs
        if (price != null && discounted != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (product.discountPercent > 0) {
                    Text(
                        t.priceWas(soum(price)),
                        style = Sadora.type.body.copy(textDecoration = TextDecoration.LineThrough),
                        color = c.muted2,
                    )
                }
                Text(
                    t.priceNow(soum(discounted)),
                    style = Sadora.type.h3,
                    color = c.text,
                )
                product.savingUzs?.takeIf { it > 0 }?.let {
                    Text(t.saving(soum(it)), style = Sadora.type.body, color = c.success)
                }
            }
        }
        product.premiumDays?.let {
            Text(t.premiumDays(it), style = Sadora.type.h3, color = c.textAccent)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    GulMark(size = 18.dp)
                    Text(
                        Fmt.int(product.coinCost),
                        style = Sadora.type.h3,
                        color = c.text,
                    )
                }
                when {
                    product.outOfStock -> Text(t.outOfStock, style = Sadora.type.body, color = c.warningSoft)
                    short > 0 -> Text(
                        t.shortBy(Fmt.int(short)),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted,
                    )
                    product.stock != null -> Text(
                        t.stockLeft(product.stock!!),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                    )
                }
            }
            PillButton(
                text = when {
                    product.outOfStock -> t.outOfStock
                    short > 0 -> t.notEnough
                    else -> t.redeem
                },
                onClick = onRedeem,
                tone = if (product.affordable) ButtonTone.Primary else ButtonTone.Ghost,
            )
        }
    }
}

@Composable
private fun RedemptionRow(redemption: Redemption) {
    val t = strings.shop
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(redemption.productTitle, style = Sadora.type.body, color = c.text, maxLines = 1)
            Text(redemption.code, style = Sadora.type.h3, color = c.textAccent, maxLines = 1)
        }
        SadoraBadge(
            when (redemption.status) {
                RedemptionStatus.ISSUED -> t.statusIssued
                RedemptionStatus.USED -> t.statusUsed
                RedemptionStatus.EXPIRED -> t.statusExpired
                RedemptionStatus.CANCELLED -> t.statusCancelled
            },
            tone = when (redemption.status) {
                RedemptionStatus.ISSUED -> BadgeTone.Success
                RedemptionStatus.USED -> BadgeTone.Neutral
                else -> BadgeTone.Warning
            },
        )
    }
}

/** "145 000 so'm" — the app's one way of writing a price. */
private fun soum(amount: Long): String = "${Fmt.int(amount.toInt())} so'm"

@Composable
private fun ShopSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Skeleton(Modifier.fillMaxWidth().height(44.dp))
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(148.dp)) }
    }
}
