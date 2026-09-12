package uz.sadora.app.ui.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.sadora.app.data.SadoraController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.GulMark
import uz.sadora.app.ui.components.IconTile
import uz.sadora.app.ui.components.PremiumCtaButton
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDivider
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.appearFromBelow
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.modules.PremiumComparison

/**
 * The Premium tab: what Premium is, what it opens, and the two ways in.
 *
 * It replaced Profile on the bar. A tab is the one place the product gets to make its
 * case every day, and Premium is the one thing worth making a case for — so the tab is
 * the case, calmly: the status at the top, the five things it adds, the comparison the
 * paywall also shows, the questions people ask before paying, and the buttons last.
 * The paywall itself stays a separate full-screen flow; this is the reading room.
 */
@Composable
fun PremiumScreen(
    state: AppState,
    controller: SadoraController,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.premium
    val c = Sadora.colors

    // The tier can change outside the app — a purchase on another device, a lapsed
    // subscription — so it is re-read whenever the tab is opened.
    LaunchedEffect(Unit) { controller.refreshEntitlements() }

    Column(modifier) {
        SadoraTopBar(t.title)

        ScreenContent {
            item {
                Box(Modifier.appearFromBelow(0)) {
                    StatusHero(state, onOpen)
                }
            }

            item {
                Box(Modifier.appearFromBelow(1)) {
                    SadoraCard {
                        Text(t.benefitsTitle, style = Sadora.type.h3, color = c.text)
                        Benefit(SadoraIcons.Sparkle, t.benefitAiTitle, t.benefitAiBody)
                        Benefit(SadoraIcons.Camera, t.benefitScannerTitle, t.benefitScannerBody)
                        Benefit(SadoraIcons.Chart, t.benefitInsightsTitle, t.benefitInsightsBody)
                        Benefit(SadoraIcons.Book, t.benefitLibraryTitle, t.benefitLibraryBody)
                        Benefit(SadoraIcons.Watch, t.benefitDevicesTitle, t.benefitDevicesBody)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(t.compareTitle, style = Sadora.type.h3, color = c.text, modifier = Modifier.padding(start = Spacing.xxs))
                    PremiumComparison()
                    Text(t.freeStays, style = Sadora.type.caption, color = c.muted, modifier = Modifier.padding(start = Spacing.xxs))
                }
            }

            if (!state.isPremium) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PremiumCtaButton(t.seePlans, onClick = { onOpen(Route.Paywall) })
                        SadoraButton(
                            t.buyWithCoins(strings.rewards.coinName),
                            { onOpen(Route.Shop) },
                            tone = ButtonTone.Outline,
                            icon = SadoraIcons.Bloom,
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    Text(t.faqTitle, style = Sadora.type.h3, color = c.text)
                    t.faq.forEachIndexed { index, (question, answer) ->
                        if (index > 0) SadoraDivider()
                        FaqRow(question, answer)
                    }
                }
            }
        }
    }
}

/** Active: the plan and its renewal. Not yet: the invitation, worded without pressure. */
@Composable
private fun StatusHero(state: AppState, onOpen: (Route) -> Unit) {
    val t = strings.premium
    val p = strings.profile
    val c = Sadora.colors
    val dates = strings.dates
    val onGradient = c.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(c.heroGradient)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(p.premiumBadge, style = Sadora.type.caption, color = onGradient)
            if (state.isPremium) {
                Box(
                    Modifier
                        .clip(Radius.chip)
                        .background(onGradient.copy(alpha = 0.22f))
                        .padding(horizontal = Spacing.xs, vertical = 3.dp),
                ) {
                    Text(p.premiumActive, style = Sadora.type.caption, color = onGradient)
                }
            }
        }
        Text(if (state.isPremium) t.activeTitle else t.inactiveTitle, style = Sadora.type.h2, color = onGradient)
        if (state.isPremium) {
            val until = state.premiumExpiresAt
            val renewal = when {
                until == null -> p.premiumNoExpiry
                state.premiumAutoRenewing -> p.premiumRenewsOn(dates.dayMonth(until) + " " + until.year)
                else -> p.premiumUntil(dates.dayMonth(until) + " " + until.year)
            }
            Text(renewal, style = Sadora.type.body, color = onGradient.copy(alpha = 0.85f))
            ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xs) {
                listOf(p.premiumFeatureAi, p.premiumFeatureScanner, p.premiumFeatureInsights).forEach { feature ->
                    Box(
                        Modifier
                            .clip(Radius.chip)
                            .background(onGradient.copy(alpha = 0.18f))
                            .padding(horizontal = Spacing.xs, vertical = 4.dp),
                    ) {
                        Text(feature, style = Sadora.type.caption, color = onGradient, maxLines = 1, softWrap = false)
                    }
                }
            }
            Text(
                t.manage,
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = onGradient,
                modifier = Modifier.noRippleClickable { onOpen(Route.Paywall) },
            )
        } else {
            Text(t.inactiveBody, style = Sadora.type.body, color = onGradient.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, body: String) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconTile(icon, tint = c.primary, size = 40.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Sadora.type.h3, color = c.text)
            Text(body, style = Sadora.type.body, color = c.muted)
        }
    }
}

/** A question that opens on tap. Closed by default so the list scans as questions. */
@Composable
private fun FaqRow(question: String, answer: String) {
    val c = Sadora.colors
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .noRippleClickable { open = !open }
            .padding(vertical = Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(question, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
            Text(if (open) "–" else "+", style = Sadora.type.h3, color = c.muted)
        }
        AnimatedVisibility(open) {
            Text(answer, style = Sadora.type.body, color = c.muted)
        }
    }
}
