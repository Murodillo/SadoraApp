package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import uz.sadora.contract.BadgeState
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.BadgeGridCard
import uz.sadora.doctor.ui.components.BadgeGridSkeleton
import uz.sadora.doctor.ui.components.BadgesHeroCard
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent

/**
 * Her badges on a page of their own, as the client app has them: the count and the bar,
 * what she has earned (furthest along first), and what is still ahead in the catalogue's
 * order. Opening it reads the board afresh, which is also what awards.
 */
@Composable
fun BadgesScreen(
    work: WorkController,
    onClose: () -> Unit,
    /** Opens a badge's sheet, which the shell hosts so that it covers everything. */
    onOpenBadge: (BadgeState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.badges
    val c = Sadora.colors

    LaunchedEffect(Unit) { work.loadBadges(force = true) }
    val board = work.badges

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)
        ScreenContent {
            item { BadgesHeroCard(board) }

            if (board == null) {
                item { BadgeGridSkeleton() }
                return@ScreenContent
            }

            val earned = board.badges.filter { it.tier > 0 }
                .sortedWith(compareByDescending<BadgeState> { it.tier.toFloat() / it.maxTier }.thenByDescending { it.earnedAt })
            val locked = board.badges.filter { it.tier == 0 }
            item { BadgeGridCard(t.earnedSection, earned, onOpenBadge) }
            item { BadgeGridCard(t.lockedSection, locked, onOpenBadge, indexOffset = earned.size) }
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
