package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.PremiumCtaButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraDivider
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.noRippleClickable

private data class PlanFeature(val name: String, val free: String, val premium: String)

private val features = listOf(
    PlanFeature("Sikl va kayfiyat", "✓", "✓"),
    PlanFeature("Ovqat kundaligi", "✓", "✓"),
    PlanFeature("AI suhbat", "—", "20/kun"),
    PlanFeature("Ovqat skaneri", "—", "30/oy"),
    PlanFeature("30/90 kunlik tahlil", "—", "✓"),
)

/**
 * "SADORA Premium".
 *
 * The comparison table is honest about limits (20 chats a day, 30 scans a month)
 * and states plainly that nothing on the free plan is taken away.
 */
@Composable
fun PaywallScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var annual by remember { mutableStateOf(true) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.surface2)
                    .noRippleClickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = Sadora.type.h3, color = c.text)
            }
        }

        ScreenContent {
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(Brush.linearGradient(listOf(c.secondary, c.primary))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "✦",
                            style = Sadora.type.h1,
                            color = if (c.isDark) c.bg else Color.White,
                        )
                    }
                    Text("SADORA Premium", style = Sadora.type.h1, color = c.text)
                    Text(
                        "AI suhbat, ovqat skaneri va kengaytirilgan tahlillar. " +
                            "Bepul rejadagi hamma narsa saqlanadi.",
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "IMKONIYAT",
                            style = Sadora.type.caption,
                            color = c.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "BEPUL",
                            style = Sadora.type.caption,
                            color = c.muted,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "PREMIUM",
                            style = Sadora.type.caption,
                            color = c.textAccent,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    SadoraDivider()
                    features.forEach { feature ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                feature.name,
                                style = Sadora.type.body,
                                color = c.text,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                feature.free,
                                style = Sadora.type.body,
                                color = c.muted,
                                modifier = Modifier.width(64.dp),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                feature.premium,
                                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                                color = c.text,
                                modifier = Modifier.width(64.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            item {
                PlanOption(
                    title = "Yillik",
                    price = "299 000 so'm / yil",
                    note = "24 900 so'm/oy",
                    discount = "−38%",
                    selected = annual,
                    onClick = { annual = true },
                )
            }

            item {
                PlanOption(
                    title = "Oylik",
                    price = "39 900 so'm / oy",
                    note = null,
                    discount = null,
                    selected = !annual,
                    onClick = { annual = false },
                )
            }

            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PremiumCtaButton("Premium'ni boshlash", onClick = {
                        state.isPremium = true
                        onClose()
                    })
                    Text(
                        "Istalgan vaqtda bekor qilish mumkin",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                    Text(
                        "Xaridni tiklash",
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textAccent,
                        modifier = Modifier.noRippleClickable {},
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanOption(
    title: String,
    price: String,
    note: String?,
    discount: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(if (selected) c.primary.copy(alpha = if (c.isDark) 0.14f else 0.07f) else c.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.line, Radius.card)
            .noRippleClickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(title, style = Sadora.type.h3, color = c.text)
                if (discount != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(c.success.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(discount, style = Sadora.type.caption, color = c.success)
                    }
                }
            }
            Text(price, style = Sadora.type.h3, color = c.text)
            if (note != null) Text(note, style = Sadora.type.body, color = c.muted)
        }
        if (selected) Text("✓", style = Sadora.type.h2, color = c.textAccent)
    }
}
