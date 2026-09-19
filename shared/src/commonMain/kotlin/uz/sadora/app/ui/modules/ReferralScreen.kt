package uz.sadora.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uz.sadora.app.data.RewardsController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.IconTile
import uz.sadora.app.ui.components.GulMark
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.appearFromBelow
import uz.sadora.app.ui.components.rememberShareAction

/**
 * "Invite your friends."
 *
 * The screen states the whole deal in one card — what she gets, what her friend gets —
 * and then gets out of the way with one button. The fair-use line at the bottom is not
 * fine print hidden under a fold: a scheme that pays per sign-up will be tested, and it
 * is fairer to say up front that a code counts once than to silently refuse one later.
 */
@Composable
fun ReferralScreen(
    rewards: RewardsController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.rewards
    val c = Sadora.colors
    val share = rememberShareAction()
    var shared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { rewards.loadReferral() }

    val referral = rewards.referral

    Column(modifier) {
        SadoraTopBar(t.referralTitle, onBack = onClose)

        if (referral == null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Skeleton(Modifier.fillMaxWidth().height(180.dp))
                Skeleton(Modifier.fillMaxWidth().height(120.dp))
            }
            return@Column
        }

        ScreenContent {
            item {
                Box(Modifier.appearFromBelow(0)) {
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            IconTile(SadoraIcons.Share, tint = c.primary, size = 46.dp)
                            Text(
                                t.referralSubtitle,
                                style = Sadora.type.body,
                                color = c.muted,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Both halves of the deal, one line each — hers and her
                        // friend's. Showing only her own reward is what makes a referral
                        // screen read as a scheme rather than as an invitation.
                        RewardLine(t.rewardPerJoin(Fmt.int(referral.rewardPerJoin)))
                        RewardLine(t.welcomeReward(Fmt.int(referral.welcomeReward)))
                    }
                }
            }

            item {
                Box(Modifier.appearFromBelow(1)) {
                    SadoraCard {
                        CardLabel(t.yourCode)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(Radius.cardSmall)
                                .background(c.surface2)
                                .padding(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                referral.code,
                                style = Sadora.type.h1,
                                color = c.text,
                                modifier = Modifier.weight(1f),
                            )
                            GulMark(size = 26.dp)
                        }
                        Text(referral.link, style = Sadora.type.body, color = c.muted, maxLines = 1)
                        SadoraButton(
                            t.shareLink,
                            {
                                share(t.shareMessage(referral.link))
                                shared = true
                            },
                            icon = SadoraIcons.Share,
                        )
                        if (shared) {
                            Text(t.codeCopied, style = Sadora.type.body, color = c.successText)
                        }
                    }
                }
            }

            item {
                Box(Modifier.appearFromBelow(2)) {
                    SadoraCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            IconTile(SadoraIcons.Profile, tint = c.primary, size = 38.dp)
                            Text(
                                t.invitedCount(referral.invited),
                                style = Sadora.type.h3,
                                color = c.text,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            IconTile(SadoraIcons.Bloom, tint = c.secondary, size = 38.dp)
                            Text(
                                t.referralEarned(Fmt.int(referral.coinsEarned)),
                                style = Sadora.type.h3,
                                color = c.text,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Box(Modifier.appearFromBelow(3)) {
                    SadoraCard {
                        Text(t.referralHowTitle, style = Sadora.type.h3, color = c.text)
                        t.referralSteps.forEachIndexed { index, step ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(Radius.chip)
                                        .background(c.surface2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                                        color = c.textAccent,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Text(step, style = Sadora.type.body, color = c.muted, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item { DisclaimerNote(t.referralFairUse) }
        }
    }
}

/** One half of the deal: the Gul mark and the sentence that says who gets what. */
@Composable
private fun RewardLine(text: String) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        GulMark(size = 18.dp)
        Text(text, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
    }
}
