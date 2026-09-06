package org.example.project.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.project.design.IconSize
import org.example.project.design.PhaseColors
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.ui.components.AiOrb
import org.example.project.ui.components.IconTile
import org.example.project.ui.components.LogoRevealMillis
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraLoader
import org.example.project.ui.components.SadoraLogoReveal
import org.example.project.ui.components.cardSurface
import org.example.project.ui.components.noRippleClickable
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------- bloom field

/**
 * One drawn flower.
 *
 * Positions are fractions of the canvas so the field keeps its composition on a
 * small phone and a tablet alike; everything else is in dp and converted at draw
 * time.
 */
private data class Bloom(
    val x: Float,
    val y: Float,
    /** Petal length, dp. */
    val radius: Float,
    val petals: Int,
    /** Resting rotation, degrees. */
    val tilt: Float,
    /** Index into the palette built by [BloomField]. */
    val tone: Int,
    /** 0..1 offset into the shared drift cycle, so no two blooms move together. */
    val phase: Float,
    /** Vertical travel, dp. */
    val bob: Float,
    /** Rotation travel, degrees. */
    val sway: Float,
    /** Stem length in dp; 0 draws no stem. */
    val stem: Float = 0f,
)

/**
 * The composition from the deck's splash: blooms gather in the bottom-left corner
 * and thin out toward the top right, leaving the middle clear for the mark.
 */
private val blooms = listOf(
    Bloom(0.10f, 0.07f, 18f, 5, -12f, 3, 0.00f, 6f, 5f),
    Bloom(0.88f, 0.10f, 16f, 8, 8f, 3, 0.28f, 7f, 4f),
    Bloom(0.92f, 0.30f, 14f, 4, -20f, 2, 0.15f, 6f, 6f),
    Bloom(0.08f, 0.66f, 26f, 6, 18f, 0, 0.55f, 8f, 7f, stem = 30f),
    Bloom(0.20f, 0.78f, 34f, 6, -10f, 0, 0.36f, 9f, 5f),
    Bloom(0.34f, 0.90f, 24f, 5, 22f, 1, 0.71f, 7f, 6f, stem = 28f),
    Bloom(0.12f, 0.92f, 30f, 7, -16f, 1, 0.22f, 10f, 4f),
    Bloom(0.52f, 0.95f, 18f, 5, 12f, 3, 0.50f, 6f, 6f),
    Bloom(0.84f, 0.88f, 22f, 6, -24f, 2, 0.86f, 8f, 5f, stem = 26f),
    Bloom(0.70f, 0.82f, 14f, 5, 30f, 3, 0.93f, 5f, 8f),
)

/** How long one full drift cycle takes. Long and prime-ish so repeats stay unnoticed. */
private const val DriftMillis = 11_000

/**
 * The drifting flower field behind the splash and the onboarding pauses.
 *
 * Two animations run at once and deliberately stay separate: a one-shot entry that
 * fades and scales each bloom in with a stagger, and a single shared infinite phase
 * that every bloom reads at its own offset. Sharing one phase — rather than giving
 * each bloom its own `infiniteRepeatable` — is what keeps the flowers on one frame
 * clock, so nothing stutters against anything else, and driving the motion through
 * `sin` means the loop closes on itself with no seam at the wrap.
 */
@Composable
fun BloomField(
    modifier: Modifier = Modifier,
    entryDelayMillis: Int = 0,
    /** Overall opacity. Below 1 the field reads as a wash behind copy. */
    fieldAlpha: Float = 1f,
) {
    val c = Sadora.colors
    val palette = remember(c) {
        listOf(
            c.secondary.copy(alpha = 0.75f),
            c.primary.copy(alpha = 0.6f),
            c.accent.copy(alpha = 0.5f),
            c.secondary.copy(alpha = 0.35f),
        )
    }
    val core = c.onPrimary.copy(alpha = 0.7f)
    val stemColor = c.primary.copy(alpha = 0.3f)

    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(entryDelayMillis.toLong())
        entry.animateTo(1f, tween(durationMillis = 1500, easing = FastOutSlowInEasing))
    }

    val drift = rememberInfiniteTransition(label = "bloom-drift")
    val phase by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(DriftMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bloom-phase",
    )

    Canvas(modifier) {
        blooms.forEachIndexed { index, bloom ->
            // Stagger: each bloom consumes the same 0..1 entry ramp, offset so the
            // field opens outward instead of appearing all at once.
            val start = index * 0.045f
            val local = ((entry.value - start) / (1f - start)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed

            val angle = ((phase + bloom.phase) * 2f * PI).toFloat()
            val bobbing = sin(angle) * bloom.bob.dp.toPx()
            val swaying = sin(angle * 0.7f) * bloom.sway

            drawBloom(
                center = Offset(
                    x = size.width * bloom.x,
                    y = size.height * bloom.y + bobbing,
                ),
                radius = bloom.radius.dp.toPx(),
                petals = bloom.petals,
                rotation = bloom.tilt + swaying,
                color = palette[bloom.tone],
                coreColor = core,
                stem = bloom.stem.dp.toPx(),
                stemColor = stemColor,
                // The last stretch of the ramp is pure fade, so a bloom settles at
                // full size a beat before it reaches full opacity.
                alpha = local * fieldAlpha,
                scale = 0.6f + 0.4f * local,
            )
        }
    }
}

/**
 * One flower: an optional curved stem, [petals] teardrops around a centre disc.
 *
 * Shared with the consent illustration, so the bloom standing in the shield is
 * literally the same flower that drifts across the splash.
 */
internal fun DrawScope.drawBloom(
    center: Offset,
    radius: Float,
    petals: Int,
    rotation: Float,
    color: Color,
    coreColor: Color,
    stem: Float,
    stemColor: Color,
    alpha: Float,
    scale: Float,
) {
    val r = radius * scale

    if (stem > 0f) {
        val path = Path().apply {
            moveTo(center.x, center.y)
            quadraticTo(
                center.x - r * 0.55f,
                center.y + stem * 0.55f,
                center.x - r * 0.2f,
                center.y + stem * scale,
            )
        }
        drawPath(
            path,
            stemColor.copy(alpha = stemColor.alpha * alpha),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    // Fewer petals read best wide, many petals best narrow, so the width follows
    // the count rather than being a fixed fraction of the radius.
    val width = r * (0.62f - petals * 0.028f).coerceAtLeast(0.24f)
    val petal = Path().apply {
        moveTo(0f, 0f)
        cubicTo(width, -r * 0.22f, width * 0.8f, -r * 0.86f, 0f, -r)
        cubicTo(-width * 0.8f, -r * 0.86f, -width, -r * 0.22f, 0f, 0f)
        close()
    }

    val step = 360f / petals
    repeat(petals) { i ->
        withTransform({
            translate(center.x, center.y)
            rotate(rotation + step * i, Offset.Zero)
        }) {
            drawPath(petal, color.copy(alpha = color.alpha * alpha))
        }
    }

    drawCircle(
        coreColor.copy(alpha = coreColor.alpha * alpha),
        radius = r * 0.19f,
        center = center,
    )
}

// ---------------------------------------------------------------- splash

/** The deck's splash ground: a lavender wash, #F7E7FF at the top to #EDE9FF below. */
private val SplashWash = listOf(Color(0xFFF7E7FF), Color(0xFFEDE9FF))

/**
 * "01. Splash" — the mark, the wordmark and the tagline on the lavender wash, with
 * the bloom field gathering at the foot of the screen.
 *
 * [onReady] fires once the reveal has played out; the caller holds here until the
 * stored session has resolved too.
 */
@Composable
fun SplashScreen(onReady: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors

    // Hold until the reveal has finished rather than for a round number: the app should
    // never cut its own logo off mid-word.
    LaunchedEffect(Unit) {
        delay(LogoRevealMillis + 350L)
        onReady()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(if (c.isDark) Brush.verticalGradient(listOf(c.bg, c.surface)) else Brush.verticalGradient(SplashWash)),
    ) {
        BloomField(Modifier.fillMaxSize(), fieldAlpha = 0.85f)

        Column(
            Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The brand's own reveal, to the deck's timing: the S is written in one
            // stroke, the dot pops before the line has settled, the letters rise, and
            // the promise arrives last.
            SadoraLogoReveal(size = 148.dp)

            // A slow session would otherwise leave the finished logo sitting still. The
            // loader arrives a beat after the hand-off would normally have happened, so
            // it is seen only when the app really is waiting — never as a flash on a
            // launch that went well.
            var waiting by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(LogoRevealMillis + 700L)
                waiting = true
            }
            Spacer(Modifier.height(Spacing.lg))
            AnimatedVisibility(waiting, enter = fadeIn(tween(400))) {
                SadoraLoader(size = 40.dp)
            }
        }
    }
}

/** Kept for callers that still use the old name. */
@Composable
fun WelcomeScreen(onReady: () -> Unit, modifier: Modifier = Modifier) = SplashScreen(onReady, modifier)

// ---------------------------------------------------------------- welcome

/** One of the six things the welcome screen says the app does. */
private data class Feature(val icon: ImageVector, val label: String, val tint: Color)

/**
 * The six, ordered so no two neighbours in the two-column grid carry adjacent hues —
 * the tiles are the only colour on the screen and they should read as six things,
 * not as one gradient.
 */
@Composable
private fun features(): List<Feature> {
    val c = Sadora.colors
    val t = strings.welcome
    return listOf(
        Feature(SadoraIcons.Bloom, t.featureCycle, PhaseColors.period),
        Feature(SadoraIcons.Smile, t.featureMood, c.warningSoft),
        Feature(SadoraIcons.Nutrition, t.featureNutrition, c.success),
        Feature(SadoraIcons.Pill, t.featureMeds, c.accent),
        Feature(SadoraIcons.Sparkle, t.featureAi, c.primary),
        Feature(SadoraIcons.Chart, t.featureInsights, PhaseColors.fertile),
    )
}

/**
 * "02. Welcome" — the first interactive screen: what the app is for, in six tiles,
 * with the language switch in the corner because language is the one answer she
 * needs before she can read anything else.
 *
 * It is built from the same pieces as the rest of the app rather than from its own
 * shapes: the AI orb it will meet again on the assistant screen, cards with the
 * standard surface, and the splash's flower field carried through behind them so the
 * two screens read as one opening.
 */
@Composable
fun IntroScreen(
    state: AppState,
    onStart: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.welcome
    val entry = rememberPageEntry(1300)
    val tiles = features()

    Box(modifier.fillMaxSize()) {
        // The splash's blooms keep drifting under this screen, faint enough to stay
        // behind the copy.
        BloomField(Modifier.fillMaxSize(), entryDelayMillis = 150, fieldAlpha = 0.22f)

        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                LanguageSwitch(selected = state.language, onSelect = { state.language = it })
            }

            // The block is centred in whatever is left between the language switch
            // and the button, and scrolls only once it outgrows that: a short screen
            // scrolls, a tall one does not leave the copy stranded at the top.
            BoxWithConstraints(Modifier.weight(1f)) {
                val viewport = maxHeight
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = viewport)
                        .padding(horizontal = Spacing.screen),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                Reveal(entry.value, from = 0.02f) { AiOrb(size = 150.dp) }
                Spacer(Modifier.height(Spacing.sm))
                Reveal(entry.value, from = 0.14f) {
                    Text(
                        t.title,
                        style = Sadora.type.h1,
                        color = c.text,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Reveal(entry.value, from = 0.22f) {
                    Text(
                        t.subtitle,
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))

                tiles.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        row.forEachIndexed { columnIndex, feature ->
                            Reveal(
                                entry.value,
                                from = optionStart(rowIndex * 2 + columnIndex, base = 0.36f, step = 0.06f),
                                modifier = Modifier.weight(1f),
                            ) {
                                FeatureTile(feature)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xs))

                // The promise the consent gate will make in full, said once here so
                // the first screen is not only a list of features.
                Reveal(entry.value, from = 0.7f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            SadoraIcons.Lock,
                            contentDescription = null,
                            Modifier.size(IconSize.sm),
                            tint = c.muted2,
                        )
                        Text(
                            t.privacyPromise,
                            style = Sadora.type.body,
                            color = c.muted2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                }
            }

            Reveal(entry.value, from = 0.78f) {
                Column(
                    Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SadoraButton(t.start, onStart)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t.haveAccount, style = Sadora.type.body, color = c.muted)
                        Text(
                            t.signIn,
                            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                            color = c.textAccent,
                            modifier = Modifier.noRippleClickable(onClick = onSignIn),
                        )
                    }
                }
            }
        }
    }
}

/** One feature, on the app's own card surface with its domain's colour on the icon. */
@Composable
private fun FeatureTile(feature: Feature, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .cardSurface(c, shape = Radius.tile)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconTile(feature.icon, tint = feature.tint, size = 38.dp, iconSize = 20.dp)
        Text(
            feature.label,
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.text,
            maxLines = 2,
        )
    }
}

/**
 * Fades and lifts [content] into place after [delayMillis].
 *
 * The lift is applied as a layer translation rather than padding so the reveal
 * never reflows the column underneath it — every line holds its final position
 * from the first frame, and only the pixels move.
 */
@Composable
private fun RevealLine(
    delayMillis: Int,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    val lift = with(LocalDensity.current) { 14.dp.toPx() }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * lift
        },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
