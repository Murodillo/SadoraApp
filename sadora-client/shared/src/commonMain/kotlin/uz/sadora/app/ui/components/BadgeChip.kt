package uz.sadora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.CommunityBadge

/** The glyph and tint each badge wears, the same on a card and on a profile. */
@Composable
fun CommunityBadge.icon(): ImageVector = when (this) {
    CommunityBadge.Newcomer -> SadoraIcons.Bloom
    CommunityBadge.Early -> SadoraIcons.Sparkle
    CommunityBadge.Writer -> SadoraIcons.Pencil
    CommunityBadge.Helper -> SadoraIcons.Message
    CommunityBadge.Loved -> SadoraIcons.Heart
    CommunityBadge.Veteran -> SadoraIcons.Shield
}

@Composable
fun CommunityBadge.tint(): Color {
    val c = Sadora.colors
    return when (this) {
        CommunityBadge.Newcomer -> c.success
        CommunityBadge.Early -> c.warning
        CommunityBadge.Writer -> c.primary
        CommunityBadge.Helper -> c.accentText
        CommunityBadge.Loved -> c.secondary
        CommunityBadge.Veteran -> c.textAccent
    }
}

/**
 * One badge as a small pill: glyph and word. [compact] drops the word for the feed,
 * where two of these sit beside an alias and a word each would push the age off the line.
 */
@Composable
fun BadgeChip(badge: CommunityBadge, compact: Boolean = false, modifier: Modifier = Modifier) {
    val tint = badge.tint()
    Row(
        modifier
            .clip(Radius.chip)
            .background(tint.copy(alpha = if (Sadora.colors.isDark) 0.22f else 0.12f))
            .padding(horizontal = if (compact) 5.dp else 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(badge.icon(), contentDescription = strings.community.badge(badge), Modifier.size(12.dp), tint = tint)
        if (!compact) {
            Text(
                strings.community.badge(badge),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
                color = tint,
                maxLines = 1,
            )
        }
    }
}

/** Up to [max] badges in a row; the feed shows two, the profile all of them. */
@Composable
fun BadgeRow(badges: List<CommunityBadge>, max: Int = badges.size, compact: Boolean = false) {
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        badges.take(max).forEach { BadgeChip(it, compact = compact) }
    }
}
