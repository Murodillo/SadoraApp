package uz.sadora.app.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.forWeek
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.PregnancyWeeks
import uz.sadora.app.ui.components.ArtIcon
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.CircleIconButton
import uz.sadora.app.ui.components.SadoraCard

/**
 * "Bolaning rivojlanishi" — the week-by-week card on the pregnancy screen.
 *
 * It opens on her current week and she can step through the others with the arrows;
 * stepping back to her own week brings the "this week" badge back, so it is always
 * clear whether she is reading about now or browsing.
 *
 * The picture is the size comparison: the clay fruit the baby matches this week
 * ([weekArt]), drawn large on the stage's gradient.
 */
@Composable
internal fun BabyWeekCard(currentWeek: Int, palette: List<androidx.compose.ui.graphics.Color>) {
    val t = strings.pregnancyWeeks
    val j = strings.journey
    val c = Sadora.colors
    val own = currentWeek.coerceIn(PregnancyWeeks.FIRST, PregnancyWeeks.LAST)
    var shown by remember(own) { mutableIntStateOf(own) }
    val week = PregnancyWeeks.of(shown)

    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(j.babyDevelopment, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
            val canBack = shown > PregnancyWeeks.FIRST
            val canForward = shown < PregnancyWeeks.LAST
            CircleIconButton(
                SadoraIcons.ChevronLeft,
                contentDescription = t.previousWeek,
                enabled = canBack,
            ) { if (canBack) shown-- }
            CircleIconButton(
                SadoraIcons.ChevronRight,
                contentDescription = t.nextWeek,
                enabled = canForward,
            ) { if (canForward) shown++ }
        }

        WeekPicture(
            art = weekArt(shown),
            label = j.weekOnly(shown),
            badge = if (shown == own) t.thisWeekCaps else null,
            colors = palette,
        )

        Text(
            t.sizeOf(t.fruits.forWeek(shown)),
            style = Sadora.type.h3,
            color = c.text,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Measure(
                label = t.lengthLabel,
                value = PregnancyWeeks.lengthValue(week.lengthMm, t.mm, t.cm),
                note = if (week.crownToHeel) t.crownToHeel else t.crownToRump,
                modifier = Modifier.weight(1f),
            )
            Measure(
                label = t.weightLabel,
                value = week.weightG?.let { PregnancyWeeks.weightValue(it, t.g, t.kg) } ?: t.weightTooSmall,
                note = null,
                modifier = Modifier.weight(1f),
            )
        }

        CardLabel(t.babyHeading)
        Text(t.baby.forWeek(shown), style = Sadora.type.body, color = c.text)

        CardLabel(t.motherHeading)
        Text(t.mother.forWeek(shown), style = Sadora.type.body, color = c.text)

        Text(t.averagesNote, style = Sadora.type.body, color = c.muted)
    }
}

@Composable
private fun WeekPicture(
    art: DrawableResource,
    label: String,
    badge: String?,
    colors: List<androidx.compose.ui.graphics.Color>,
) {
    val c = Sadora.colors
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .clip(Radius.cardSmall)
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.35f) })),
    ) {
        ArtIcon(art, 132.dp, Modifier.align(Alignment.Center))
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = Sadora.type.h3, color = c.text)
            if (badge != null) {
                Box(
                    Modifier
                        .clip(Radius.chip)
                        .background(c.surface.copy(alpha = 0.8f))
                        .padding(horizontal = Spacing.xs, vertical = 3.dp),
                ) {
                    Text(badge, style = Sadora.type.caption, color = c.textAccent)
                }
            }
        }
    }
}

@Composable
private fun Measure(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Column(
        modifier
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = Sadora.type.body, color = c.muted)
        Text(value, style = Sadora.type.h2, color = c.text)
        if (note != null) Text(note, style = Sadora.type.body, color = c.muted)
    }
}
