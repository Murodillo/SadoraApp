package org.example.project.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.ui.components.SadoraButton
import kotlin.math.roundToInt

// ---------------------------------------------------------------- social proof

/** One line of the reassurance panel. */
private data class Proof(val headline: String, val body: String)

private val proofs = listOf(
    Proof("Ayollar tanlagan", "O'zbekistonda minglab ayol siklini SADORA bilan kuzatadi."),
    Proof("Shifokorlar bilan", "Savollar va maqolalar ginekologlar bilan birga tayyorlanadi."),
    Proof("Ma'lumot sizniki", "Istalgan vaqtda eksport qiling yoki butunlay o'chiring."),
)

/**
 * The pause between two stretches of questions.
 *
 * It answers the question a long form provokes — why am I typing all this — before
 * it gets asked, and gives the sequence somewhere to breathe. The bloom field from
 * the welcome screen comes back behind it, so the pause reads as the same app
 * rather than an ad break.
 */
@Composable
fun ReassuranceScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val entry = rememberPageEntry(1300)

    Box(modifier.fillMaxSize()) {
        // Dimmed: here the field is a backdrop for copy, not the subject.
        BloomField(Modifier.fillMaxSize(), fieldAlpha = 0.3f)

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Reveal(entry.value, from = 0.05f) {
                Text(
                    "Siz yolg'iz emassiz",
                    style = Sadora.type.h1,
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            proofs.forEachIndexed { index, proof ->
                Reveal(entry.value, from = optionStart(index, base = 0.22f, step = 0.14f)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(Radius.chip)
                                .background(c.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                SadoraIcons.Check,
                                contentDescription = null,
                                Modifier.size(IconSize.md),
                                tint = c.primary,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(proof.headline, style = Sadora.type.h3, color = c.text)
                            Text(proof.body, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Reveal(entry.value, from = 0.72f) {
                SadoraButton("Davom etish", onContinue)
            }
        }
    }
}

// ---------------------------------------------------------------- analysing

/** The lines the analysing screen steps through, and where each one lands. */
private val analysisSteps = listOf(
    0.30f to "Javoblaringiz o'qilmoqda…",
    0.58f to "Siklingiz hisoblanmoqda…",
    0.82f to "Bugun ekrani sozlanmoqda…",
    1.00f to "Deyarli tayyor…",
)

/**
 * The wait at the end of the flow, with a ring that fills to a hundred.
 *
 * The ring is driven by one long animation to 1 rather than by real progress: there
 * is no measurable work behind it, and a bar that stalls at a real percentage reads
 * as a hang. What it does honestly is take a fixed, short time and then hand over.
 */
@Composable
fun AnalysingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(3400, easing = FastOutSlowInEasing))
        onDone()
    }

    val percent = (progress.value * 100).roundToInt()
    val caption = (analysisSteps.firstOrNull { progress.value <= it.first }
        ?: analysisSteps.last()).second

    Column(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Sizga moslashtirilmoqda",
            style = Sadora.type.h1,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))

        Box(
            Modifier.fillMaxWidth(0.52f).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(progress.value)
            Text("$percent%", style = Sadora.type.data, color = c.textAccent)
        }

        Spacer(Modifier.height(Spacing.xl))
        Text(caption, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
    }
}

/** The ring itself: a soft track, a gradient sweep, and a slow shimmer behind it. */
@Composable
private fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val shimmer = rememberInfiniteTransition(label = "ring-shimmer")
    val spin by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-spin",
    )

    Canvas(modifier.fillMaxSize()) {
        val stroke = size.minDimension * 0.09f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = c.line,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            // The sweep gradient turns with the shimmer, so the filled arc keeps
            // moving even while the percentage is between two of its steps.
            brush = Brush.sweepGradient(
                listOf(c.secondary, c.primary, c.secondary),
                center = center,
            ),
            startAngle = -90f + spin * 0.1f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}