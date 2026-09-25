package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings

/**
 * The check mark beside a verified doctor's name. Filled, in the brand colour, and
 * drawn nowhere else, so it cannot be confused with a badge an alias earned.
 */
@Composable
internal fun VerifiedMark(size: Dp = 16.dp) {
    val c = Sadora.colors
    val label = strings.doctors.verified
    Box(
        Modifier
            .size(size)
            .clip(Radius.chip)
            .background(c.primary)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(size * 0.66f), tint = c.onPrimary)
    }
}

/**
 * A doctor's avatar: her initials on a solid ring, with the check mark on its shoulder.
 * Solid where an alias's is a soft tint, so the two never read as the same kind of name.
 */
@Composable
internal fun DoctorAvatar(name: String, size: Dp = 36.dp) {
    val c = Sadora.colors
    Box(Modifier.size(size + 4.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(Radius.chip)
                .background(c.primary.copy(alpha = if (c.isDark) 0.28f else 0.14f))
                .border(1.5.dp, c.primary, Radius.chip),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(name), style = Sadora.type.h3, color = c.primary)
        }
        Box(Modifier.align(Alignment.BottomEnd)) { VerifiedMark(size = (size.value * 0.42f).dp) }
    }
}

/** "Shifokor javob berdi" under a question a doctor has answered. */
@Composable
internal fun DoctorAnsweredChip(count: Int, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Row(
        modifier
            .clip(Radius.chip)
            .background(c.primary.copy(alpha = if (c.isDark) 0.22f else 0.10f))
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VerifiedMark(size = 14.dp)
        Text(
            strings.doctors.answeredBy(count),
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Medium),
            color = c.textAccent,
        )
    }
}

/** First letters of the first two words that are not a title. */
internal fun initials(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() && !it.trimEnd('.').equals("dr", ignoreCase = true) }
    return words.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { name.take(1).uppercase() }
}
