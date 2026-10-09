package uz.sadora.app.ui.core

import uz.sadora.app.ui.components.art
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.PhotoController
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
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SettingsRow
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.ArtIcon
import uz.sadora.app.ui.onboarding.art

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
    photos: PhotoController,
    /** Opens the photo sheet, which the shell hosts so that it covers the tab bar. */
    onEditPhoto: () -> Unit,
    onOpen: (Route) -> Unit,
    /** Yaqinim, for the unread count on its row. */
    partner: uz.sadora.app.data.PartnerController? = null,
    /** Her badges, for the strip under the premium card; nothing is drawn until one is earned. */
    badges: uz.sadora.contract.BadgeBoard? = null,
    /** Her AI companion, for its row; null hides the row (no backend, no pet). */
    pet: uz.sadora.contract.PetKind? = null,
    /** A legendary pet she owns, marked in gold after her name. */
    legendaryPet: uz.sadora.contract.PetKind? = null,
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

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.title, onBack = onClose)

            ScreenContent {
                item {
                    SadoraCard(onClick = { onOpen(Route.PersonalDetails) }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            // The avatar opens her frames, its camera badge changes the photo;
                            // the rest of the card still opens her details.
                            EditableAvatar(state, photos, size = 52.dp, onClick = { onOpen(Route.Frames) }, onCamera = onEditPhoto)
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                // Her name, and right after it the badge she chose to wear.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                                ) {
                                    Text(
                                        state.name.ifBlank { t.unnamed },
                                        style = Sadora.type.h3,
                                        color = c.text,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    uz.sadora.app.ui.components.WornBadgeMark(state.wornBadge, size = 26.dp)
                                    legendaryPet?.let { uz.sadora.app.ui.components.LegendaryMark(it, size = 26.dp) }
                                }
                                // A phone-only account has no email; the number is what she signed in with.
                                Text(
                                    state.email.ifBlank { "+${uz.sadora.contract.UzbekPhone.COUNTRY_CODE} ${uz.sadora.contract.UzbekPhone.format(state.phone)}" },
                                    style = Sadora.type.body,
                                    color = c.muted,
                                )
                            }
                            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
                        }
                    }
                }

                item {
                    if (state.isPremium) PremiumStatusCard(state) else UpgradeCard { onOpen(Route.Paywall) }
                }

                if (badges?.badges?.any { it.tier > 0 } == true) {
                    item { uz.sadora.app.ui.components.BadgeStrip(badges, onOpen = { onOpen(Route.Badges) }) }
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
                            ArtTile(Res.drawable.ic3d_record, tint = c.primary, size = 44.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(t.shareProfile, style = Sadora.type.h3, color = c.text)
                                Text(t.shareProfileNote, style = Sadora.type.body, color = c.muted)
                            }
                            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
                        }
                    }
                }

                // Yaqinim beside the doctor's code: both are her record leaving the app, to
                // one person, on her terms.
                item {
                    SadoraCard(onClick = { onOpen(Route.Yaqinim) }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            ArtTile(Res.drawable.ic3d_partner, tint = c.secondary, size = 44.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(strings.partner.title, style = Sadora.type.h3, color = c.text)
                                // Unread first: a heart waiting is the reason to open this row.
                                val unread = partner?.state?.link?.unread ?: 0
                                Text(
                                    if (unread > 0) strings.partner.unreadCount(unread) else strings.partner.profileRowNote,
                                    style = Sadora.type.body,
                                    color = if (unread > 0) c.textAccent else c.muted,
                                )
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
                            Res.drawable.ic3d_flower_coin,
                            t.rewards,
                            value = if (state.coins > 0) Fmt.int(state.coins) else null,
                            iconTint = c.secondary,
                        ) { onOpen(Route.Rewards) }
                        SettingsRow(Res.drawable.ic3d_shop, t.shop) { onOpen(Route.Shop) }
                        SettingsRow(Res.drawable.ic3d_gift, t.referral) { onOpen(Route.Referral) }
                    }
                }

                item {
                    SadoraCard(padding = Spacing.xs) {
                        SettingsRow(Res.drawable.ic3d_sleep, t.sleep) { onOpen(Route.Sleep) }
                        SettingsRow(Res.drawable.ic3d_meds, t.medications) { onOpen(Route.Medications) }
                        if (state.communityEnabled) {
                            SettingsRow(Res.drawable.ic3d_chats, t.secretChat, iconTint = c.secondary) { onOpen(Route.SecretChat) }
                        }
                        SettingsRow(Res.drawable.ic3d_insights, t.insights) { onOpen(Route.Insights) }
                        SettingsRow(Res.drawable.ic3d_book, t.knowledge) { onOpen(Route.Knowledge) }
                    }
                }

                item {
                    SadoraCard(padding = Spacing.xs) {
                        SettingsRow(Res.drawable.ic3d_profile, t.personalDetails) { onOpen(Route.PersonalDetails) }
                        SettingsRow(Res.drawable.ic3d_target, t.goals) { onOpen(Route.GoalsSettings) }
                        SettingsRow(
                            state.lifeStage.art(),
                            t.lifeStage,
                            value = strings.stages.title(state.lifeStage),
                        ) { onOpen(Route.LifeStageSettings) }
                        // Blank until the providers have loaded: a "2" that was never true
                        // is worse than nothing next to a row you are about to open.
                        val connected = health.sources.count { it.connected }
                        SettingsRow(
                            Res.drawable.ic3d_watch,
                            t.devices,
                            value = if (health.sources.isEmpty()) null else "$connected",
                        ) {
                            onOpen(Route.DataSources)
                        }
                        if (pet != null) {
                            SettingsRow(
                                pet.art(uz.sadora.contract.PetPose.IDLE),
                                strings.pet.title,
                                value = strings.pet.name(pet),
                            ) { onOpen(Route.PetPicker) }
                        }
                        SettingsRow(Res.drawable.ic3d_home, t.homeLayout) { onOpen(Route.HomeLayout) }
                        SettingsRow(Res.drawable.ic3d_bell, t.notifications) { onOpen(Route.Notifications) }
                        SettingsRow(Res.drawable.ic3d_lock, t.privacyAndSecurity) { onOpen(Route.PrivacySecurity) }
                    }
                }

                item {
                    SadoraCard(padding = Spacing.xs) {
                        SettingsRow(
                            Res.drawable.ic3d_globe,
                            t.language,
                            value = state.language.native,
                        ) { onOpen(Route.LanguageSettings) }
                        SettingsRow(
                            if (state.darkTheme) Res.drawable.ic3d_sleep else Res.drawable.ic3d_sun,
                            t.theme,
                            value = if (state.darkTheme) t.themeDark else t.themeLight,
                        ) { state.darkTheme = !state.darkTheme }
                        SettingsRow(Res.drawable.ic3d_bulb, t.about) { onOpen(Route.About) }
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
            ArtIcon(Res.drawable.ic3d_crown, 34.dp)
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
