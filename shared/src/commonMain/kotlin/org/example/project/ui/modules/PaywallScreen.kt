package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import org.example.project.data.BillingController
import org.example.project.data.SadoraController
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.PremiumCtaButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraDivider
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.Skeleton
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.PaymentProvider
import org.example.project.ui.components.noRippleClickable

private data class PlanFeature(val name: String, val free: String, val premium: String)

private val features = listOf(
    PlanFeature("Sikl va kayfiyat", "✓", "✓"),
    PlanFeature("Ovqat kundaligi", "✓", "✓"),
    PlanFeature("AI suhbat", "—", "20/kun"),
    PlanFeature("Ovqat skaneri", "—", "30/oy"),
    PlanFeature("30/90 kunlik tahlil", "—", "✓"),
)

/**
 * "SADORA Premium".
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
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val catalogue = billing.catalogue
    val plans = catalogue?.plans.orEmpty()
    var selectedPlan by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { billing.loadCatalogue() }

    // The highlighted plan is the server's recommendation; until the catalogue arrives
    // there is nothing selected, because there is nothing to select.
    LaunchedEffect(plans) {
        if (selectedPlan == null) {
            selectedPlan = plans.firstOrNull { it.highlighted }?.id ?: plans.firstOrNull()?.id
        }
    }

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
                    Text("SADORA Premium", style = Sadora.type.h1, color = c.text)
                    Text(
                        "AI suhbat, ovqat skaneri va kengaytirilgan tahlillar. " +
                            "Bepul rejadagi hamma narsa saqlanadi.",
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "IMKONIYAT",
                            style = Sadora.type.caption,
                            color = c.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "BEPUL",
                            style = Sadora.type.caption,
                            color = c.muted,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "PREMIUM",
                            style = Sadora.type.caption,
                            color = c.textAccent,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    SadoraDivider()
                    features.forEach { feature ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                feature.name,
                                style = Sadora.type.body,
                                color = c.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                feature.free,
                                style = Sadora.type.body,
                                color = c.muted,
                                modifier = Modifier.width(64.dp),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                feature.premium,
                                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                                color = c.text,
                                modifier = Modifier.width(64.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

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
                            Text("Tariflar yuklanmadi", style = Sadora.type.h3, color = c.text)
                            Text(
                                billing.error ?: "Internetni tekshirib, qayta urinib ko'ring.",
                                style = Sadora.type.body,
                                color = c.muted,
                            )
                        }
                    }
                }
            }

            items(plans.size) { index ->
                val plan = plans[index]
                PlanOption(
                    title = plan.title,
                    price = plan.priceLabel(),
                    note = plan.monthlyNote(),
                    discount = plan.savingLabel(plans),
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
                    val payable = catalogue?.providers.orEmpty()
                        .filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }

                    if (billing.paid) {
                        Text(
                            "To'lov qabul qilindi. Premium ochildi.",
                            style = Sadora.type.h3,
                            color = c.success,
                        )
                    }

                    payable.forEach { provider ->
                        PremiumCtaButton(
                            when {
                                waiting -> "To'lov kutilmoqda…"
                                else -> provider.buttonLabel()
                            },
                            enabled = !waiting && !billing.busy && selectedPlan != null,
                            onClick = { pay(provider) },
                        )
                    }

                    if (payable.isEmpty() && catalogue != null) {
                        // The store flow needs the platform billing SDK, which the app
                        // does not carry yet; saying so is better than a button that
                        // cannot do anything.
                        Text(
                            "Hozircha to'lov usuli mavjud emas.",
                            style = Sadora.type.body,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                        )
                    }

                    billing.error?.let {
                        Text(it, style = Sadora.type.body, color = c.warning, textAlign = TextAlign.Center)
                    }

                    Text(
                        "Istalgan vaqtda bekor qilish mumkin",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                    // Restoring is asking the server what this account is entitled to —
                    // the client never decides that for itself.
                    Text(
                        "Xaridni tiklash",
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textAccent,
                        modifier = Modifier.noRippleClickable(enabled = !controller.busy) {
                            scope.launch { controller.refreshEntitlements() }
                        },
                    )
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
                        Text(discount, style = Sadora.type.caption, color = c.success)
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
private fun BillingPlan.priceLabel(): String =
    "${sumLabel(priceMinor)} so'm / ${if (period == BillingPeriod.YEAR) "yil" else "oy"}"

/** What a year plan works out to per month, so the comparison is hers to make. */
private fun BillingPlan.monthlyNote(): String? =
    monthlyEquivalentMinor?.let { "${sumLabel(it)} so'm/oy" }

/**
 * "−38%" against the cheapest monthly plan.
 *
 * Computed from the two prices rather than stored, so it cannot contradict them — a
 * discount badge that disagrees with the numbers beside it is worse than no badge.
 */
private fun BillingPlan.savingLabel(all: List<BillingPlan>): String? {
    if (period != BillingPeriod.YEAR) return null
    val monthly = all.filter { it.period == BillingPeriod.MONTH }.minByOrNull { it.priceMinor } ?: return null
    val fullYear = monthly.priceMinor * 12
    if (fullYear <= priceMinor) return null
    val saved = (fullYear - priceMinor) * 100 / fullYear
    return "−$saved%"
}

/** Thousands separated with a space, the way prices are written in Uzbek. */
private fun sumLabel(minor: Long): String =
    (minor / 100).toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()

private fun PaymentProvider.buttonLabel(): String = when (this) {
    PaymentProvider.PAYME -> "Payme orqali to'lash"
    PaymentProvider.CLICK -> "Click orqali to'lash"
    PaymentProvider.APP_STORE -> "App Store orqali"
    PaymentProvider.GOOGLE_PLAY -> "Google Play orqali"
}
