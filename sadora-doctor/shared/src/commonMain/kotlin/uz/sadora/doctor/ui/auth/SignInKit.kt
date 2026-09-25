package uz.sadora.doctor.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.MinTouchTarget
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.ui.components.SadoraMark
import uz.sadora.doctor.ui.components.drawBloom
import uz.sadora.doctor.ui.components.noRippleClickable
import uz.sadora.doctor.i18n.strings

// The client app's onboarding kit (QuestionKit.kt), trimmed to what signing in uses: the
// page's entry ramp, the reveal, the brand block, the scaffold with its progress line,
// and the footer button that slides up. A doctor signs in on the same pages a woman does.

// ---------------------------------------------------------------- reveal

/**
 * The shared entry ramp for a question page.
 *
 * Every page builds one of these and hands it to [Reveal] blocks, so a whole screen
 * assembles from a single animation clock. That matters more than it sounds: with a
 * timer per block, a slow first frame after the page transition would scatter the
 * reveal, and the staggering would drift against the incoming slide.
 */
@Composable
fun rememberPageEntry(durationMillis: Int = 900): Animatable<Float, *> {
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entry.animateTo(1f, tween(durationMillis, easing = LinearOutSlowInEasing))
    }
    return entry
}

/**
 * Fades and lifts [content] once the page's [entry] ramp passes [from].
 *
 * The lift is a layer translation, never padding, so nothing below reflows while the
 * reveal plays — each block holds its final position from the first frame.
 */
@Composable
fun Reveal(
    entry: Float,
    from: Float,
    modifier: Modifier = Modifier,
    lift: Int = 16,
    content: @Composable () -> Unit,
) {
    val span = (1f - from).coerceAtLeast(0.15f)
    val progress = ((entry - from) / span).coerceIn(0f, 1f)
    val liftPx = with(LocalDensity.current) { lift.dp.toPx() }
    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * liftPx
        },
    ) {
        content()
    }
}

// ---------------------------------------------------------------- scaffold

/**
 * The brand block every registration page opens with: the mark, the wordmark, and a
 * blossom drifting in the top-right corner.
 *
 * It is the deck's signature on these screens — the same three elements on every
 * question, so a run of twenty pages reads as one flow rather than as a form.
 */
@Composable
private fun QuestionBrand(modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Box(modifier.fillMaxWidth().height(96.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            // Two soft blooms in the corner, drawn from the same flower as the splash.
            drawBloom(
                center = Offset(size.width * 0.88f, size.height * 0.30f),
                radius = 26.dp.toPx(),
                petals = 6,
                rotation = 12f,
                color = c.secondary.copy(alpha = 0.30f),
                coreColor = c.secondary.copy(alpha = 0.45f),
                stem = 0f,
                stemColor = Color.Transparent,
                alpha = 1f,
                scale = 1f,
            )
            drawBloom(
                center = Offset(size.width * 0.97f, size.height * 0.62f),
                radius = 16.dp.toPx(),
                petals = 5,
                rotation = -20f,
                color = c.primary.copy(alpha = 0.22f),
                coreColor = c.primary.copy(alpha = 0.35f),
                stem = 0f,
                stemColor = Color.Transparent,
                alpha = 1f,
                scale = 1f,
            )
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SadoraMark(size = 56.dp)
            Text(
                "SADORA DOCTOR",
                style = Sadora.type.caption.copy(
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                    fontWeight = FontWeight.Medium,
                ),
                color = c.muted,
            )
        }
    }
}

/**
 * The frame every onboarding question shares: back, progress, the brand block, a
 * staged headline, a scrolling body, and a footer that carries the primary button.
 *
 * [progress] is the position in the whole question sequence, 0..1. It animates
 * rather than jumping, which is the one piece of continuity across a page
 * transition — everything else on screen is replaced.
 */
@Composable
fun QuestionScaffold(
    title: String,
    progress: Float,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    entry: Animatable<Float, *> = rememberPageEntry(),
    /** Set false on the pages that need every pixel for their own content. */
    brand: Boolean = true,
    /** Sits in the top-right corner, level with the progress bar. */
    topEnd: @Composable (() -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    val e = entry.value
    val focus = LocalFocusManager.current

    Column(
        modifier
            .fillMaxSize()
            // Whichever is taller. Padding for both would double-count, because the
            // keyboard's inset already covers the navigation bar it sits on top of.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            // A tap on the empty space around the fields puts the keyboard away.
            // Fields and buttons consume the press themselves, so this only ever fires
            // where there is nothing to press — which is exactly where someone taps
            // when they want the keyboard gone.
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // The chevron holds its slot even on the first page, so the progress bar
            // does not shift sideways between questions.
            Box(Modifier.size(MinTouchTarget), contentAlignment = Alignment.Center) {
                if (onBack != null) {
                    Icon(
                        SadoraIcons.ChevronLeft,
                        contentDescription = strings.common.back,
                        Modifier
                            .size(IconSize.lg)
                            .noRippleClickable(onClick = onBack),
                        tint = c.text,
                    )
                }
            }
            ProgressLine(progress, Modifier.weight(1f))
            if (topEnd != null) topEnd() else Spacer(Modifier.size(Spacing.xs))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (brand) {
                Reveal(e, from = 0.0f) { QuestionBrand() }
            } else {
                Spacer(Modifier.height(Spacing.md))
            }
            Reveal(e, from = 0.05f) {
                Text(
                    title,
                    style = Sadora.type.h1,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (subtitle != null) {
                Reveal(e, from = 0.16f) {
                    Text(
                        subtitle,
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            content()
            Spacer(Modifier.height(Spacing.lg))
        }

        // fillMaxWidth matters: on the pages whose primary button is still hidden the
        // column would otherwise shrink to its only child and strand the secondary
        // link against the left edge.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            footer()
        }
    }
}

/** The hairline progress bar across the top of every question. */
@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val width by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "progress",
    )
    Box(
        modifier
            .height(6.dp)
            .clip(Radius.chip)
            .background(c.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(width)
                .height(6.dp)
                .clip(Radius.chip)
                .background(c.heroGradient),
        )
    }
}

/**
 * The footer button, which slides up the first time there is something to confirm.
 *
 * Questions that answer themselves on tap have no footer at all; this is for the
 * ones that need a deliberate second action.
 */
@Composable
fun ColumnScope.AnswerFooter(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 2 } +
            fadeIn(tween(220)),
        exit = fadeOut(tween(140)),
    ) {
        content()
    }
}
