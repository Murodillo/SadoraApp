package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.ui.components.AiOrb
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.i18n.strings

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
    val t = strings.ai

    Column(modifier) {
        SadoraTopBar(
            t.title,
            trailing = { SadoraBadge(t.freeBadge, BadgeTone.Neutral) },
        )

        ScreenContent {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AiOrb(size = 132.dp)
                    Text(
                        t.howCanIHelp,
                        style = Sadora.type.h2,
                        color = c.text,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        t.readsYourData,
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.sampleAnswer)
                    Text(
                        t.sampleAnswerBody,
                        style = Sadora.type.body,
                        color = c.text,
                    )
                    // The second half fades out — the preview stops mid-answer.
                    Box {
                        Text(
                            t.sampleAnswerAdvice,
                            style = Sadora.type.body,
                            color = c.text.copy(alpha = 0.25f),
                        )
                    }
                }
            }

            item {
                SadoraCard {
                    t.freeFeatures.forEach { feature ->
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
                    t.freeKeeps,
                )
            }

            item {
                // Equal weight, by design — declining is not a lesser choice.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraButton(
                        t.seePremium,
                        onUpgrade,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraButton(
                        t.notNow,
                        onDismiss,
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
