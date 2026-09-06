package org.example.project.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraLoader
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.noRippleClickable

/**
 * "Ovqat skaneri · kamera".
 *
 * Gallery, shutter and manual entry sit in one row — the design is clear that
 * scanning is never the only way in. The monthly quota is stated up front rather
 * than surfacing as a surprise at the limit.
 */
@Composable
fun FoodScanCameraScreen(
    state: AppState,
    onCapture: () -> Unit,
    onManualEntry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors

    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar("Ovqat skaneri", onBack = onClose, centered = true)

        Column(
            Modifier.weight(1f).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Taomni ramka ichiga joylashtiring",
                style = Sadora.type.h2,
                color = c.text,
                textAlign = TextAlign.Center,
            )

            // Viewfinder stand-in with the framing guide the design shows.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .clip(Radius.card)
                    .background(c.text.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.78f)
                        .aspectRatio(1f)
                        .border(2.dp, c.primary, Radius.card),
                )
                Text("🍽️", style = Sadora.type.display)
            }

            Text(
                "Yaxshi yorug'lik natijani aniqroq qiladi",
                style = Sadora.type.body,
                color = c.muted,
            )
        }

        Column(
            Modifier.padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CaptureSideAction(SadoraIcons.Document, "Galereya", onClick = onCapture)

                Box(
                    Modifier
                        .size(76.dp)
                        .clip(Radius.chip)
                        .background(c.heroGradient)
                        .noRippleClickable(onClick = onCapture),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SadoraIcons.Camera, contentDescription = "Suratga olish", Modifier.size(28.dp), tint = c.onPrimary)
                }

                CaptureSideAction(SadoraIcons.Pencil, "Qo'lda", onClick = onManualEntry)
            }

            Text(
                "Bu oyda 6 / 30 skan ishlatildi",
                style = Sadora.type.body,
                color = c.muted,
            )
        }
    }
}

@Composable
private fun CaptureSideAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = Sadora.colors
    Column(
        Modifier.noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(Radius.chip)
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, Modifier.size(IconSize.md), tint = c.text)
        }
        Text(label, style = Sadora.type.body, color = c.muted)
    }
}

/**
 * "Ovqat skaneri · tahlil" — the analysis step.
 *
 * Shows what the model is doing and states the expected wait before it starts, so
 * the delay never reads as a hang.
 */
@Composable
fun FoodScanAnalyzingScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var step by remember { mutableStateOf(0) }

    val steps = listOf(
        "Rasm sifati tekshirildi",
        "Taom aniqlandi",
        "Porsiya va makrolar hisoblanmoqda",
    )

    LaunchedEffect(Unit) {
        repeat(steps.size) {
            delay(900)
            step++
        }
        delay(300)
        onDone()
    }

    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar("", onBack = onCancel)

        Column(
            Modifier.weight(1f).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ImagePlaceholder(Modifier.fillMaxWidth().aspectRatio(1.4f), emoji = sampleScan.emoji, shape = Radius.card)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SadoraLoader(size = 34.dp)
                Column {
                    Text("Tahlil qilinmoqda…", style = Sadora.type.h3, color = c.text)
                    Text("Bu odatda 3–5 soniya oladi", style = Sadora.type.body, color = c.muted)
                }
            }

            steps.forEachIndexed { index, label ->
                val done = index < step
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        if (done) SadoraIcons.Check else SadoraIcons.Clock,
                        contentDescription = null,
                        Modifier.size(IconSize.md),
                        tint = if (done) c.success else c.muted2,
                    )
                    Text(label, style = Sadora.type.body, color = if (done) c.text else c.muted)
                }
            }
        }

        Box(Modifier.padding(Spacing.screen)) {
            SadoraButton("Bekor qilish", onCancel, tone = ButtonTone.Ghost)
        }
    }
}
