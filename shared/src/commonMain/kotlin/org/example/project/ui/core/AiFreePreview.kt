package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent

private val premiumFeatures = listOf(
    "Kuniga 20 savol, ma'lumotlar kontekstida",
    "Har kunlik shaxsiy AI xulosa",
    "Ovqat skaneri — oyda 30 marta",
    "30 va 90 kunlik tahlillar",
)

/**
 * The free-plan AI screen.
 *
 * Shows half of a real sample answer so the value is concrete rather than described,
 * then gives "Premium'ni ko'rish" and "Hozir emas" the *same* visual weight — the
 * design is explicit that declining must not be a second-class action.
 */
@Composable
fun AiFreePreviewScreen(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    Column(modifier) {
        SadoraTopBar(
            "SADORA AI",
            trailing = { SadoraBadge("BEPUL REJA", BadgeTone.Neutral) },
        )

        ScreenContent {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(listOf(c.secondary, c.primary))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "✦",
                            style = Sadora.type.display,
                            color = if (c.isDark) c.bg else Color.White,
                        )
                    }
                    Text(
                        "Sizga qanday yordam bera olaman?",
                        style = Sadora.type.h2,
                        color = c.text,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Ma'lumotlaringizni o'qib shaxsiy javob beradi",
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                SadoraCard {
                    CardLabel("Namuna javob")
                    Text(
                        "Oxirgi uch kunda uyqu odatdagidan qisqa bo'lgan va suv iste'moli " +
                            "pasaygan.",
                        style = Sadora.type.body,
                        color = c.text,
                    )
                    // The second half fades out — the preview stops mid-answer.
                    Box {
                        Text(
                            "Shu kunlarda energiya ham past qayd etilgan. Bugun ikki qadam: " +
                                "tushga qadar 700 ml suv va 23:00 gacha yotish.",
                            style = Sadora.type.body,
                            color = c.text.copy(alpha = 0.25f),
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    premiumFeatures.forEach { feature ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text("✓", style = Sadora.type.h3, color = c.success)
                            Text(feature, style = Sadora.type.body, color = c.text)
                        }
                    }
                }
            }

            item {
                DisclaimerNote(
                    "Bepul rejadagi hamma narsa qoladi: sikl, kayfiyat, suv, ovqat " +
                        "kundaligi, dorilar, 7 kunlik tahlil.",
                )
            }

            item {
                // Equal weight, by design — declining is not a lesser choice.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraButton(
                        "Premium'ni ko'rish",
                        onUpgrade,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraButton(
                        "Hozir emas",
                        onDismiss,
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
