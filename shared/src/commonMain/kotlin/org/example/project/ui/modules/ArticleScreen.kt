package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.noRippleClickable

/**
 * "Maqola" — the reader.
 *
 * Author and reviewing clinician are shown side by side at the top, and every
 * article closes with a boundary note about what the content is not.
 */
@Composable
fun ArticleScreen(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    Column(modifier) {
        SadoraTopBar(
            "",
            onBack = onClose,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    RoundAction("♡")
                    RoundAction("↗")
                }
            },
        )

        ScreenContent {
            item {
                ImagePlaceholder(
                    Modifier.fillMaxWidth().aspectRatio(1.9f),
                    shape = Radius.card,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    SadoraBadge("OVQATLANISH", BadgeTone.Neutral)
                    SadoraBadge("6 DAQIQA", BadgeTone.Neutral)
                }
            }

            item {
                Text(
                    "Temirga boy taomlar: nima yeyish va nima bilan qo'shish",
                    style = Sadora.type.h1,
                    color = c.text,
                )
            }

            item {
                // Author and reviewer carry equal visual weight.
                SadoraCard(padding = Spacing.sm) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Byline(
                            initials = "NK",
                            name = "Nilufar Karimova",
                            role = "Muallif · nutritsiolog",
                            modifier = Modifier.weight(1f),
                        )
                        Byline(
                            initials = "SA",
                            name = "Dr. S. Aliyeva",
                            role = "✓ Ko'rib chiqqan",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                Text(
                    "Temir qonda kislorod tashuvchi gemoglobin uchun kerak. Hayz davrida " +
                        "yo'qotilgan temirni ovqat bilan qoplash odatiy amaliyot.",
                    style = Sadora.type.body,
                    color = c.text,
                )
            }

            item {
                Text("Nimalarda ko'p", style = Sadora.type.h2, color = c.text)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(
                        "Qora jigar, mol go'shti, tovuq jigari",
                        "Yasmiq, no'xat, loviya",
                        "Ismaloq, ko'kat, quruq o'rik",
                    ).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("•", style = Sadora.type.body, color = c.textAccent)
                            Text(line, style = Sadora.type.body, color = c.text)
                        }
                    }
                }
            }

            item {
                DisclaimerNote(
                    "Ushbu material umumiy salomatlik ma'lumoti. Temir preparatlarini " +
                        "shifokor tavsiyasisiz boshlash tavsiya etilmaydi.",
                )
            }
        }
    }
}

@Composable
private fun Byline(
    initials: String,
    name: String,
    role: String,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = Sadora.type.caption, color = c.secondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = Sadora.type.body, color = c.text, maxLines = 1)
            Text(role, style = Sadora.type.caption, color = c.muted, maxLines = 1)
        }
    }
}

@Composable
private fun RoundAction(glyph: String) {
    val c = Sadora.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .noRippleClickable {},
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Sadora.type.h3, color = c.text)
    }
}
