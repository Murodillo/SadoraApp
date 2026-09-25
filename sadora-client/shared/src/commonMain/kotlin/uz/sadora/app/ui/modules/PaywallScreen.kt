package uz.sadora.app.ui.modules

import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.app.data.BillingController
import uz.sadora.app.data.SadoraController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.ModuleStrings
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.PremiumCtaButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.PaymentProvider
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.data.readable

/**
 * t.premiumTitle.
 *
 * The comparison table is honest about limits (20 chats a day, 30 scans a month)
 * and states plainly that nothing on the free plan is taken away.
 */
@Composable
fun PaywallScreen(
    state: AppState,
    controller: SadoraController,
    billing: BillingController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val catalogue = billing.catalogue
    // A store build sells only what the store lists, at the store's price.
    val inStore = billing.store != null
    val plans = catalogue?.plans.orEmpty().filter { !inStore || billing.storePrices.containsKey(billing.storeProductId(it)) }
    var selectedPlan by remember { mutableStateOf<String?>(null) }
    var restoreNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { billing.loadCatalogue() }
    LaunchedEffect(catalogue) { if (catalogue != null) billing.loadStorePrices() }

    // The highlighted plan is the server's recommendation; until the catalogue arrives
    // there is nothing selected, because there is nothing to select.
    LaunchedEffect(plans) {
        if (selectedPlan == null) {
            selectedPlan = plans.firstOrNull { it.highlighted }?.id ?: plans.firstOrNull()?.id
        }
    }

    // The wait below runs in this screen's scope, so leaving cancels it — and used to
    // leave `pending` set on a controller that lives all session: every later visit found
    // both pay buttons reading "kutilmoqda" and disabled until the app was restarted.
    DisposableEffect(Unit) { onDispose { billing.cancelPending() } }

    /** Opens the provider's page, then waits for its callback to reach the server. */
    fun pay(provider: PaymentProvider) {
        val planId = selectedPlan ?: return
        scope.launch {
            val session = billing.startCheckout(planId, provider) ?: return@launch
            uriHandler.openUri(session.url)
            billing.awaitPayment { controller.refreshEntitlements() }
        }
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(Radius.chip)
                    .background(c.surface2)
                    .noRippleClickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = Sadora.type.h3, color = c.text)
            }
        }

        ScreenContent {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(c.heroGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            SadoraIcons.Sparkle,
                            contentDescription = null,
                            Modifier.size(28.dp),
                            tint = c.onPrimary,
                        )
                    }
                    Text(t.premiumTitle, style = Sadora.type.h1, color = c.text)
                    Text(
                        t.premiumBody,
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item { PremiumComparison() }

            if (catalogue == null) {
                item {
                    if (billing.busy) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Skeleton(Modifier.fillMaxWidth().height(72.dp))
                            Skeleton(Modifier.fillMaxWidth().height(72.dp))
                        }
                    } else {
                        // No prices means no offer: quoting a figure the server has not
                        // confirmed is how two app versions end up selling at two prices.
                        SadoraCard {
                            Text(t.plansFailed, style = Sadora.type.h3, color = c.text)
                            Text(
                                billing.error?.readable() ?: t.plansFailedBody,
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                    }
                }
            }

            // The store answered with nothing to sell — the products are not live in the
            // console yet, or Play does not know this build. Say so instead of a blank.
            if (inStore && catalogue != null && plans.isEmpty() && !billing.busy) {
                item {
                    SadoraCard {
                        Text(t.plansFailed, style = Sadora.type.h3, color = c.text)
                        Text(
                            t.storePlansUnavailable(if (billing.store?.provider == PaymentProvider.APP_STORE) "App Store" else "Google Play"),
                            style = Sadora.type.body,
                            color = c.muted,
                        )
                    }
                }
            }

            items(plans.size) { index ->
                val plan = plans[index]
                PlanOption(
                    title = plan.title,
                    // The store's own localized price; the server's so'm figure and its
                    // savings maths describe a different price list.
                    price = if (inStore) billing.storePrices[billing.storeProductId(plan)].orEmpty() else plan.priceLabel(t),
                    note = if (inStore) null else plan.monthlyNote(t),
                    discount = if (inStore) null else plan.savingLabel(plans, t),
                    selected = plan.id == selectedPlan,
                    onClick = { selectedPlan = plan.id },
                )
            }

            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    val waiting = billing.pending != null
                    val payable = if (inStore) {
                        emptyList()
                    } else {
                        catalogue?.providers.orEmpty().filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }
                    }
                    val storeName = if (billing.store?.provider == PaymentProvider.APP_STORE) "App Store" else "Google Play"

                    if (billing.paid) {
                        Text(
                            t.paymentAccepted,
                            style = Sadora.type.h3,
                            color = c.successText,
                        )
                    }

                    // Paid is the end of this screen. The buttons used to come back under
                    // "To'lov qabul qilindi", one tap from a second checkout.
                    if (!billing.paid && !state.isPremium) payable.forEach { provider ->
                        PremiumCtaButton(
                            when {
                                waiting -> t.paymentPending
                                else -> provider.buttonLabel(t)
                            },
                            enabled = !waiting && !billing.busy && selectedPlan != null,
                            onClick = { pay(provider) },
                        )
                    }

                    if (inStore && !billing.paid && !state.isPremium) {
                        val plan = plans.firstOrNull { it.id == selectedPlan }
                        PremiumCtaButton(
                            if (billing.storePending) t.paymentPending else t.subscribe,
                            enabled = plan != null && !billing.busy && !billing.storePending,
                            onClick = {
                                val userId = controller.currentUserId ?: return@PremiumCtaButton
                                plan ?: return@PremiumCtaButton
                                scope.launch { billing.buyInStore(plan, userId) { controller.refreshEntitlements() } }
                            },
                        )
                        if (billing.storePending) {
                            Text(t.storePending, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
                        }
                        Text(t.storeRenewalTerms(storeName), style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
                    }

                    if (!inStore && payable.isEmpty() && catalogue != null) {
                        // The store flow needs the platform billing SDK, which the app
                        // does not carry yet; saying so is better than a button that
                        // cannot do anything.
                        Text(
                            t.noPaymentMethod,
                            style = Sadora.type.body,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    billing.error?.readable()?.let {
                        Text(it, style = Sadora.type.body, color = c.warning, textAlign = TextAlign.Center)
                    }

                    Text(
                        t.cancelAnytime,
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                    // Restoring is asking the server what this account is entitled to —
                    // the client never decides that for itself.
                    Text(
                        t.restorePurchase,
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textAccent,
                        modifier = Modifier.noRippleClickable(enabled = !controller.busy && !billing.busy) {
                            scope.launch {
                                restoreNote = null
                                // In a store build, the store is asked first: purchases it
                                // holds for this phone's account go to the server to verify.
                                val granted = billing.reconcileStore { controller.refreshEntitlements() }
                                if (!granted) controller.refreshEntitlements()
                                if (inStore && !granted && !state.isPremium) restoreNote = t.nothingToRestore
                            }
                        },
                    )
                    restoreNote?.let { Text(it, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
private fun PlanOption(
    title: String,
    price: String,
    note: String?,
    discount: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = strings.modules
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(if (selected) c.primary.copy(alpha = if (c.isDark) 0.14f else 0.07f) else c.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.line, Radius.card)
            .noRippleClickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(title, style = Sadora.type.h3, color = c.text)
                if (discount != null) {
                    Box(
                        Modifier
                            .clip(Radius.chip)
                            .background(c.success.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(discount, style = Sadora.type.caption, color = c.successText)
                    }
                }
            }
            Text(price, style = Sadora.type.h3, color = c.text)
            if (note != null) Text(note, style = Sadora.type.body, color = c.muted)
        }
        if (selected) Text("✓", style = Sadora.type.h2, color = c.textAccent)
    }
}

/** "299 000 so'm / yil". The server sends tiyin; the screen is the only place that formats it. */
private fun BillingPlan.priceLabel(t: ModuleStrings): String =
    t.priceFor(sumLabel(priceMinor), monthly = period != BillingPeriod.YEAR)

/** What a year plan works out to per month, so the comparison is hers to make. */
private fun BillingPlan.monthlyNote(t: ModuleStrings): String? =
    monthlyEquivalentMinor?.let { t.perMonth(sumLabel(it)) }

/**
 * "−38%" against the cheapest monthly plan.
 *
 * Computed from the two prices rather than stored, so it cannot contradict them — a
 * discount badge that disagrees with the numbers beside it is worse than no badge.
 */
private fun BillingPlan.savingLabel(all: List<BillingPlan>, t: ModuleStrings): String? {
    if (period != BillingPeriod.YEAR) return null
    val monthly = all.filter { it.period == BillingPeriod.MONTH }.minByOrNull { it.priceMinor } ?: return null
    val fullYear = monthly.priceMinor * 12
    if (fullYear <= priceMinor) return null
    val saved = (fullYear - priceMinor) * 100 / fullYear
    return t.saving(saved.toInt())
}

/** Thousands separated with a space, the way prices are written in Uzbek. */
internal fun sumLabel(minor: Long): String =
    (minor / 100).toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()

private fun PaymentProvider.buttonLabel(t: ModuleStrings): String = when (this) {
    PaymentProvider.PAYME -> t.payWithPayme
    PaymentProvider.CLICK -> t.payWithClick
    PaymentProvider.APP_STORE -> t.payWithAppStore
    PaymentProvider.GOOGLE_PLAY -> t.payWithGooglePlay
}
