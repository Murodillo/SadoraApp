package org.example.project.ui.core

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
import org.example.project.data.SadoraController
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.nav.Route
import org.example.project.ui.components.Avatar
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SettingsRow
import org.example.project.ui.components.noRippleClickable

/**
 * "Profil" — account, subscription status, and the settings that change how the
 * rest of the app behaves.
 *
 * The subscription block states what the plan includes and when it renews; the
 * design deliberately avoids aggressive re-selling here.
 */
@Composable
fun ProfileScreen(
    state: AppState,
    controller: SadoraController,
    onOpen: (Route) -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    // The tier can change outside the app — a purchase on another device, a lapsed
    // subscription — so re-check it whenever Profile is opened.
    LaunchedEffect(Unit) { controller.refreshEntitlements() }

    Column(modifier) {
        SadoraTopBar("Profil")

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
                                "${state.name} Yusupova",
                                style = Sadora.type.h3,
                                color = c.text,
                            )
                            Text(state.email, style = Sadora.type.body, color = c.muted)
                        }
                        Icon(
    SadoraIcons.ChevronRight,
    contentDescription = null,
    Modifier.size(IconSize.md),
    tint = c.muted2,
)
                    }
                }
            }

            item {
                if (state.isPremium) PremiumStatusCard(state) else UpgradeCard { onOpen(Route.Paywall) }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(SadoraIcons.Moon, "Uyqu") { onOpen(Route.Sleep) }
                    SettingsRow(SadoraIcons.Bloom, "Ong") { onOpen(Route.Mind) }
                    SettingsRow(SadoraIcons.Pill, "Dorilar") { onOpen(Route.Medications) }
                    SettingsRow(SadoraIcons.Chart, "Tahlillar") { onOpen(Route.Insights) }
                    SettingsRow(SadoraIcons.Book, "Bilim") { onOpen(Route.Knowledge) }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(SadoraIcons.Profile, "Shaxsiy ma'lumotlar") { onOpen(Route.PersonalDetails) }
                    SettingsRow(SadoraIcons.Target, "Maqsadlar") { onOpen(Route.GoalsSettings) }
                    SettingsRow(
                        SadoraIcons.Journey,
                        "Hayot bosqichi",
                        value = state.lifeStage.title,
                    ) { onOpen(Route.LifeStageSettings) }
                    SettingsRow(SadoraIcons.Watch, "Ulangan qurilmalar", value = "2") {
                        onOpen(Route.DataSources)
                    }
                    SettingsRow(SadoraIcons.Bell, "Bildirishnomalar") { onOpen(Route.Notifications) }
                    SettingsRow(SadoraIcons.Lock, "Maxfiylik va xavfsizlik") { onOpen(Route.PrivacySecurity) }
                }
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    // Only Uzbek is written; a chevron would promise a screen that does not exist.
                    SettingsRow(
                        SadoraIcons.Globe,
                        "Til",
                        value = state.language.native,
                        showChevron = false,
                    )
                    SettingsRow(
                        if (state.darkTheme) SadoraIcons.Moon else SadoraIcons.Today,
                        "Mavzu",
                        value = if (state.darkTheme) "Qorong'i" else "Yorug'",
                    ) { state.darkTheme = !state.darkTheme }
                    SettingsRow(SadoraIcons.Info, "SADORA haqida") { onOpen(Route.About) }
                }
            }

            item {
                SadoraButton(
                    if (controller.busy) "Chiqilmoqda…" else "Chiqish",
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
    val onGradient = c.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(Brush.linearGradient(listOf(c.secondary, c.primary)))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("SADORA PREMIUM", style = Sadora.type.caption, color = onGradient)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .clip(Radius.chip)
                    .background(onGradient.copy(alpha = 0.22f))
                    .padding(horizontal = Spacing.xs, vertical = 3.dp),
            ) {
                Text("Faol", style = Sadora.type.caption, color = onGradient)
            }
        }
        Text("Yillik obuna", style = Sadora.type.h2, color = onGradient)
        Text(state.premiumRenewal, style = Sadora.type.body, color = onGradient.copy(alpha = 0.85f))
        // Flow, not a fixed row — the longest feature name would otherwise wrap mid-chip.
        ChipFlowRow(horizontalGap = Spacing.xs, verticalGap = Spacing.xs) {
            listOf("AI chat", "Ovqat skaneri", "Kengaytirilgan tahlil").forEach { feature ->
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
                Text("SADORA Premium", style = Sadora.type.h3, color = c.text)
                Text(
                    "AI suhbat, ovqat skaneri va kengaytirilgan tahlillar",
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            Icon(
    SadoraIcons.ChevronRight,
    contentDescription = null,
    Modifier.size(IconSize.md),
    tint = c.muted2,
)
        }
    }
}
