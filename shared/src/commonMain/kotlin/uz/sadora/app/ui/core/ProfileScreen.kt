package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.SadoraController
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.Avatar
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.IconTile
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SettingsRow
import uz.sadora.app.ui.components.noRippleClickable

/**
 * "Profil" — account, subscription status, and the settings that change how the
 * rest of the app behaves.
 *
 * Opened from the avatar in the home header rather than from the tab bar: it is a
 * settings screen, visited a few times a month, and the bar slot went to Premium. The
 * subscription block states what the plan includes and when it renews; the design
 * deliberately avoids aggressive re-selling here.
 */
@Composable
fun ProfileScreen(
    state: AppState,
    controller: SadoraController,
    health: HealthController,
    onOpen: (Route) -> Unit,
    onSignedOut: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.profile
    val scope = rememberCoroutineScope()

    // The tier can change outside the app — a purchase on another device, a lapsed
    // subscription — so re-check it whenever Profile is opened.
    LaunchedEffect(Unit) {
        controller.refreshEntitlements()
        health.loadSources()
    }

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)

        ScreenContent {
            item {
                SadoraCard(onClick = { onOpen(Route.PersonalDetails) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Avatar(state.name, size = 52.dp)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                state.name.ifBlank { t.unnamed },
                                style = Sadora.type.h3,
                                color = c.text,
                            )
                            Text(state.email, style = Sadora.type.body, color = c.muted)
                        }
                        Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
                    }
                }
            }

            item {
                if (state.isPremium) PremiumStatusCard(state) else UpgradeCard { onOpen(Route.Paywall) }
            }

            // The QR code for a doctor, first among the actions: it is the one thing on
            // this screen she opens while someone is waiting.
            item {
                SadoraCard(onClick = { onOpen(Route.ShareProfile) }) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        IconTile(SadoraIcons.Shield, tint = c.primary, size = 44.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t.shareProfile, style = Sadora.type.h3, color = c.text)
                            Text(t.shareProfileNote, style = Sadora.type.body, color = c.muted)
                        }
                        Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
                    }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    // Gul sits above the modules rather than among the settings: it is
                    // something she uses, not something she configures.
                    SettingsRow(
                        SadoraIcons.Bloom,
                        t.rewards,
                        value = if (state.coins > 0) Fmt.int(state.coins) else null,
                        iconTint = c.secondary,
                    ) { onOpen(Route.Rewards) }
                    SettingsRow(SadoraIcons.Bookmark, t.shop) { onOpen(Route.Shop) }
                    SettingsRow(SadoraIcons.Share, t.referral) { onOpen(Route.Referral) }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(SadoraIcons.Moon, t.sleep) { onOpen(Route.Sleep) }
                    SettingsRow(SadoraIcons.Pill, t.medications) { onOpen(Route.Medications) }
                    if (state.communityEnabled) {
                        SettingsRow(SadoraIcons.Chats, t.secretChat, iconTint = c.secondary) { onOpen(Route.SecretChat) }
                    }
                    SettingsRow(SadoraIcons.Chart, t.insights) { onOpen(Route.Insights) }
                    SettingsRow(SadoraIcons.Book, t.knowledge) { onOpen(Route.Knowledge) }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(SadoraIcons.Profile, t.personalDetails) { onOpen(Route.PersonalDetails) }
                    SettingsRow(SadoraIcons.Target, t.goals) { onOpen(Route.GoalsSettings) }
                    SettingsRow(
                        SadoraIcons.Journey,
                        t.lifeStage,
                        value = strings.stages.title(state.lifeStage),
                    ) { onOpen(Route.LifeStageSettings) }
                    // Blank until the providers have loaded: a "2" that was never true
                    // is worse than nothing next to a row you are about to open.
                    val connected = health.sources.count { it.connected }
                    SettingsRow(
                        SadoraIcons.Watch,
                        t.devices,
                        value = if (health.sources.isEmpty()) null else "$connected",
                    ) {
                        onOpen(Route.DataSources)
                    }
                    SettingsRow(SadoraIcons.Home, t.homeLayout) { onOpen(Route.HomeLayout) }
                    SettingsRow(SadoraIcons.Bell, t.notifications) { onOpen(Route.Notifications) }
                    SettingsRow(SadoraIcons.Lock, t.privacyAndSecurity) { onOpen(Route.PrivacySecurity) }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(
                        SadoraIcons.Globe,
                        t.language,
                        value = state.language.native,
                    ) { onOpen(Route.LanguageSettings) }
                    SettingsRow(
                        if (state.darkTheme) SadoraIcons.Moon else SadoraIcons.Today,
                        t.theme,
                        value = if (state.darkTheme) t.themeDark else t.themeLight,
                    ) { state.darkTheme = !state.darkTheme }
                    SettingsRow(SadoraIcons.Info, t.about) { onOpen(Route.About) }
                }
            }

            item {
                SadoraButton(
                    if (controller.busy) t.signingOut else t.signOut,
                    tone = ButtonTone.Secondary,
                    enabled = !controller.busy,
                    onClick = {
                        scope.launch {
                            controller.signOut()
                            onSignedOut()
                        }
                    },
                )
            }
        }
    }
}

/** Active subscription: plan, renewal date, and what it unlocks. */
@Composable
private fun PremiumStatusCard(state: AppState) {
    val c = Sadora.colors
    val t = strings.profile
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
            Text(t.premiumBadge, style = Sadora.type.caption, color = onGradient)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .clip(Radius.chip)
                    .background(onGradient.copy(alpha = 0.22f))
                    .padding(horizontal = Spacing.xs, vertical = 3.dp),
            ) {
                Text(t.premiumActive, style = Sadora.type.caption, color = onGradient)
            }
        }
        Text(t.premiumYearly, style = Sadora.type.h2, color = onGradient)
        val until = state.premiumExpiresAt
        val renewal = when {
            until == null -> t.premiumNoExpiry
            state.premiumAutoRenewing -> t.premiumRenewsOn(dates.dayMonth(until) + " " + until.year)
            else -> t.premiumUntil(dates.dayMonth(until) + " " + until.year)
        }
        Text(renewal, style = Sadora.type.body, color = onGradient)
        // Flow, not a fixed row — the longest feature name would otherwise wrap mid-chip.
        ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xs) {
            listOf(t.premiumFeatureAi, t.premiumFeatureScanner, t.premiumFeatureInsights).forEach { feature ->
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .clip(Radius.chip)
                        .background(onGradient.copy(alpha = 0.18f))
                        .padding(horizontal = Spacing.xs, vertical = 4.dp),
                ) {
                    Text(
                        feature,
                        style = Sadora.type.caption,
                        color = onGradient,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpgradeCard(onUpgrade: () -> Unit) {
    val c = Sadora.colors
    val t = strings.profile
    SadoraCard(onClick = onUpgrade) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                SadoraIcons.Sparkle,
                contentDescription = null,
                Modifier.size(28.dp),
                tint = c.secondary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.upgradeTitle, style = Sadora.type.h3, color = c.text)
                Text(
                    t.upgradeSubtitle,
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}
