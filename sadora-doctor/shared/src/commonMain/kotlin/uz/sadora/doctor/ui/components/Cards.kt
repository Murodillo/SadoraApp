package uz.sadora.doctor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing

/**
 * Every card in the clay finish the icons have: lit along the top, a bright rim where
 * the light catches it, a soft lavender lift under it (light theme only — a shadow on
 * navy is invisible and only costs a layer; the rim separates it there).
 */
fun Modifier.cardSurface(
    colors: uz.sadora.doctor.design.SadoraColors,
    shape: Shape = Radius.card,
    elevation: Dp = 10.dp,
): Modifier = claySurface(colors, shape, colors.surface, elevation = elevation)

/** The standard surface: 24dp radius, soft shadow, hairline border. */
@Composable
fun SadoraCard(
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.md,
    onClick: (() -> Unit)? = null,
    verticalGap: Dp = Spacing.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            // A tappable card dips under the finger; a static one must not, or every
            // surface on the screen would look interactive.
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .cardSurface(c)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(verticalGap),
        content = content,
    )
}

/** Section heading used between card groups on a screen. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = Sadora.type.h2, color = c.text)
        when {
            trailing != null -> trailing()
            action != null -> Text(
                action,
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.noRippleClickable { onAction?.invoke() },
            )
        }
    }
}

/**
 * A round pastel tile with an icon in it — the deck's way of leading a card row.
 * [tint] colours the icon; the disc is the same colour washed out.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 44.dp,
    iconSize: Dp = IconSize.md,
    shape: Shape = Radius.chip,
) {
    val c = Sadora.colors
    val colour = tint ?: c.primary
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colour.copy(alpha = if (c.isDark) 0.22f else 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(iconSize), tint = colour)
    }
}

/**
 * One of the colour clay icons (`composeResources/drawable/ic3d_*`) at [size] — the
 * client app's set, so the two apps read as one family.
 *
 * They carry their own colour, so unlike [Icon] nothing tints them.
 */
@Composable
fun ArtIcon(art: DrawableResource, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(art), contentDescription = null, modifier.size(size))
}

/**
 * [IconTile] for a colour icon: the same washed-out disc, with [art] in it untinted.
 * [tint] only colours the disc.
 */
@Composable
fun ArtTile(
    art: DrawableResource,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 44.dp,
    artSize: Dp = size * 0.74f,
    shape: Shape = Radius.chip,
) {
    val c = Sadora.colors
    val colour = tint ?: c.primary
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colour.copy(alpha = if (c.isDark) 0.22f else 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        ArtIcon(art, artSize)
    }
}

/**
 * A note the app shows wherever it must not be read as medical guidance.
 */
@Composable
fun DisclaimerNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Text(icon, style = Sadora.type.body, color = c.muted)
        Text(text, style = Sadora.type.body, color = c.muted)
    }
}
