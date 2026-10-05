package uz.sadora.app.ui.modules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import uz.sadora.app.data.RewardsController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.BadgeGridCard
import uz.sadora.app.ui.components.BadgeGridSkeleton
import uz.sadora.app.ui.components.BadgesHeroCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.contract.BadgeState

/**
 * Her badges, on a page of their own: the count and the one she wears, then what she
 * has earned, then what is still ahead.
 *
 * Apart from the wallet on purpose. Gul is a currency she spends; badges are a record of
 * what she did, and putting them under a balance made them read as a way to earn coins.
 */
@Composable
fun BadgesScreen(
    rewards: RewardsController,
    onClose: () -> Unit,
    /** Opens a badge's sheet, which the shell hosts so that it covers the tab bar. */
    onOpenBadge: (BadgeState) -> Unit,
    /** Wearing a badge is Premium; the header's line opens the paywall for a free account. */
    onUpgrade: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = strings.badges
    val c = Sadora.colors

    LaunchedEffect(Unit) { rewards.loadBadges(force = true) }
    val board = rewards.badges

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)
        ScreenContent {
            item { BadgesHeroCard(board, onUpgrade = onUpgrade) }

            if (board == null) {
                item { BadgeGridSkeleton() }
                return@ScreenContent
            }

            // The most advanced first among the earned; the locked keep the catalogue's order.
            val earned = board.badges.filter { it.tier > 0 }
                .sortedWith(compareByDescending<BadgeState> { it.tier.toFloat() / it.maxTier }.thenByDescending { it.earnedAt })
            val locked = board.badges.filter { it.tier == 0 }
            item {
                BadgeGridCard(t.earnedSection, earned, board.worn, onOpenBadge)
            }
            item {
                BadgeGridCard(t.lockedSection, locked, board.worn, onOpenBadge, indexOffset = earned.size)
            }
            item {
                Text(
                    t.principle,
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted2,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}
