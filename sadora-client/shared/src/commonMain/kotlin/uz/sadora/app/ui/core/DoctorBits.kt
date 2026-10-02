package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.AvailabilityLine
import uz.sadora.app.i18n.AvailabilityTone
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.RemoteAvatar
import uz.sadora.app.ui.components.noRippleClickable

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
 * A doctor's avatar: her photo, or her initials, on a solid ring, with the check mark on
 * its shoulder. Solid where an alias's is a soft tint, so the two never read as the same
 * kind of name. Her photo is public like her name; initials stand in until it loads, and
 * for good when she has none or it will not load.
 */
@Composable
internal fun DoctorAvatar(name: String, size: Dp = 36.dp, photoUrl: String? = null) {
    val c = Sadora.colors
    Box(Modifier.size(size + 4.dp)) {
        RemoteAvatar(
            url = photoUrl,
            initials = initials(name),
            size = size,
            // The ring is drawn over the photo as well: it is what says "doctor".
            modifier = Modifier.border(1.5.dp, c.primary, Radius.chip),
        ) {
            Box(
                Modifier.fillMaxSize().background(c.primary.copy(alpha = if (c.isDark) 0.28f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(name), style = Sadora.type.h3, color = c.primary)
            }
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

/** "Hozir onlayn" with a green dot, "Band" with an amber one, the next hour with a grey one. */
@Composable
internal fun AvailabilityRow(line: AvailabilityLine, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val dot = when (line.tone) {
        AvailabilityTone.Online -> c.success
        AvailabilityTone.Busy -> c.warning
        AvailabilityTone.Away -> c.muted2
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(Radius.chip).background(dot))
        Text(
            line.text,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Medium),
            color = if (line.tone == AvailabilityTone.Online) c.successText else c.muted,
        )
    }
}

/** The green dot on a doctor's avatar or name while she is answering. */
@Composable
internal fun OnlineDot(size: Dp = 10.dp) {
    val c = Sadora.colors
    Box(Modifier.size(size).clip(Radius.chip).background(c.surface).padding(2.dp)) {
        Box(Modifier.size(size - 4.dp).clip(Radius.chip).background(c.success))
    }
}

/** "To'langan" in the consultation's banner. */
@Composable
internal fun PaidChip(modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Row(
        modifier
            .clip(Radius.chip)
            .background(c.success.copy(alpha = if (c.isDark) 0.22f else 0.14f))
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(12.dp), tint = c.successText)
        Text(
            strings.doctors.paidChip,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
            color = c.successText,
        )
    }
}

/**
 * Five stars. Read-only on a review; with [onSelect], each is a button of its own, big
 * enough for a thumb, and read out as "4 yulduz".
 */
@Composable
internal fun StarRow(
    rating: Int,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    onSelect: ((Int) -> Unit)? = null,
) {
    val c = Sadora.colors
    val d = strings.doctors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (onSelect != null) 4.dp else 1.dp)) {
        for (star in 1..5) {
            val lit = star <= rating
            val glyph = @Composable {
                Text(
                    "★",
                    style = Sadora.type.h3.copy(fontSize = size.value.sp, lineHeight = size.value.sp),
                    color = if (lit) c.warning else c.line,
                )
            }
            if (onSelect == null) {
                glyph()
            } else {
                val label = d.stars(star)
                Box(
                    Modifier
                        .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                        .clip(Radius.chip)
                        .noRippleClickable(role = Role.Button) { onSelect(star) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { glyph() }
            }
        }
    }
}
