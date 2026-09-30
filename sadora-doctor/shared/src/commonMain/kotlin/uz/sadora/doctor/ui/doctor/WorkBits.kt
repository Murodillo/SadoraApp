package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.doctor.data.clockOf
import uz.sadora.doctor.data.groupedSom
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.IconTile
import uz.sadora.doctor.ui.components.Motion
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.noRippleToggleable

// The small pieces her working-day screens share: a switch on a card, a row that opens
// a page, a chip, a line of money.

/** The pill switch the doctor app draws for an on/off setting. */
@Composable
internal fun SwitchThumb(on: Boolean, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val thumbX by animateDpAsState(if (on) 20.dp else 0.dp, tween(Motion.Quick), label = "switch")
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(Radius.chip)
            .background(if (on) c.primary else c.surface2)
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = thumbX).size(20.dp).clip(Radius.chip).background(if (on) c.onPrimary else c.muted2))
    }
}

/** A card that is one switch: the whole card toggles, as a screen reader hears it too. */
@Composable
internal fun SwitchCard(
    title: String,
    body: String?,
    on: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier.noRippleToggleable(value = on, role = Role.Switch, enabled = enabled, onValueChange = onToggle)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = Sadora.type.h3, color = if (on && tint != null) tint else c.text)
                body?.let { Text(it, style = Sadora.type.body, color = c.muted) }
            }
            SwitchThumb(on)
        }
    }
}

/** A card row that opens a page of its own: icon, title, what is behind it, a chevron. */
@Composable
internal fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, onClick = onClick, padding = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconTile(icon, tint = tint ?: c.primary, size = 40.dp, iconSize = IconSize.md)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Sadora.type.h3, color = c.text)
                subtitle?.let { Text(it, style = Sadora.type.body, color = c.muted) }
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}

/** A small tinted label: "To'langan", "Javob kutilmoqda". */
@Composable
internal fun TintChip(text: String, tint: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
        color = tint,
        modifier = modifier
            .clip(Radius.chip)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    )
}

/** "Jami tushum ........ 150 000 so'm". [strong] is the line the card is about. */
@Composable
internal fun MoneyLine(label: String, minor: Long, strong: Boolean = false, tint: Color? = null) {
    val c = Sadora.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Sadora.type.body, color = if (strong) c.text else c.muted, modifier = Modifier.weight(1f))
        Text(
            somText(minor),
            style = if (strong) Sadora.type.h3 else Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = tint ?: c.text,
        )
    }
}

/** Tiyin as the app writes money: "50 000 so'm". */
@Composable
internal fun somText(minor: Long): String = strings.work.som(groupedSom(minor))

/** A price, or "Bepul" for zero. */
@Composable
internal fun priceText(minor: Long): String = if (minor == 0L) strings.work.free else somText(minor)

/** "4-sentabr, 09:30" on her phone's clock. */
@Composable
internal fun dayMonthTime(at: Instant): String {
    val local = at.toLocalDateTime(TimeZone.currentSystemDefault())
    return strings.dates.dayMonth(local.date) + ", " + clockOf(local.hour * 60 + local.minute)
}

/** "4-sentabr" on her phone's clock. */
@Composable
internal fun dayMonthOf(at: Instant): String =
    strings.dates.dayMonth(at.toLocalDateTime(TimeZone.currentSystemDefault()).date)
