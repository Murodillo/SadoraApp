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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCheckbox
import org.example.project.ui.components.noRippleClickable

// ---------------------------------------------------------------- illustration

/** One cog in the field behind the shield. */
private data class Cog(
    val x: Float,
    val y: Float,
    /** Radius as a fraction of the canvas width. */
    val radius: Float,
    val teeth: Int,
    val tone: Int,
    /** Degrees per full drift cycle; negative turns the other way. */
    val speed: Float,
)

/**
 * Cogs sit behind the shield in two loose arcs, largest at the outside, so the
 * shield reads as the thing they are all turning for.
 */
private val cogs = listOf(
    Cog(0.20f, 0.24f, 0.135f, 12, 1, 360f),
    Cog(0.35f, 0.10f, 0.085f, 10, 2, -300f),
    Cog(0.14f, 0.55f, 0.100f, 11, 2, -260f),
    Cog(0.26f, 0.78f, 0.075f, 9, 1, 320f),
    Cog(0.79f, 0.20f, 0.115f, 11, 0, -280f),
    Cog(0.90f, 0.44f, 0.080f, 10, 1, 340f),
    Cog(0.76f, 0.72f, 0.098f, 11, 2, -220f),
    Cog(0.58f, 0.09f, 0.062f, 8, 0, 380f),
)

private const val CogCycleMillis = 26_000

/**
 * The consent screen's illustration: a shield over slowly turning cogs.
 *
 * The cogs share one linear phase and multiply it by their own [Cog.speed], which is
 * what lets neighbouring cogs turn at different rates and in opposite directions
 * while every one of them still completes a whole number of turns per cycle — so the
 * loop restarts on an identical frame and never visibly snaps back.
 *
 * [entry] drives the one-shot open: 0 is off-screen, 1 is settled.
 */
@Composable
private fun ShieldIllustration(entry: Float, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val cogTones = remember(c) {
        listOf(
            c.primary.copy(alpha = 0.35f),
            c.secondary.copy(alpha = 0.30f),
            c.accent.copy(alpha = 0.28f),
        )
    }

    val turn = rememberInfiniteTransition(label = "cog-turn")
    val phase by turn.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CogCycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cog-phase",
    )

    Canvas(modifier) {
        val w = size.width

        cogs.forEachIndexed { index, cog ->
            // Cogs arrive before the shield and stagger among themselves, so the
            // machinery is already turning by the time the shield lands on it.
            val start = index * 0.05f
            val local = ((entry - start) / (0.75f - start).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed

            drawCog(
                center = Offset(w * cog.x, size.height * cog.y),
                radius = w * cog.radius * (0.75f + 0.25f * local),
                teeth = cog.teeth,
                rotation = phase * cog.speed,
                color = cogTones[cog.tone].copy(alpha = cogTones[cog.tone].alpha * local),
                holeColor = c.bg,
            )
        }

        // The shield comes in last and slightly overshoots, which is what makes the
        // whole illustration feel like it lands rather than fades.
        val shieldIn = ((entry - 0.25f) / 0.75f).coerceIn(0f, 1f)
        if (shieldIn <= 0f) return@Canvas
        val overshoot = 1f + 0.06f * (1f - shieldIn) * shieldIn * 4f

        drawShield(
            center = Offset(w * 0.5f, size.height * 0.5f),
            width = w * 0.44f * (0.86f + 0.14f * shieldIn) * overshoot,
            fill = c.primary,
            highlight = c.onPrimary.copy(alpha = 0.16f),
            outline = c.text.copy(alpha = if (c.isDark) 0.35f else 0.9f),
            emblem = c.onPrimary,
            alpha = shieldIn,
        )
    }
}

/** A cog: [teeth] rounded stubs around a disc, with the hub knocked back out. */
private fun DrawScope.drawCog(
    center: Offset,
    radius: Float,
    teeth: Int,
    rotation: Float,
    color: Color,
    holeColor: Color,
) {
    val toothWidth = radius * 0.32f
    val toothLength = radius * 0.26f
    val step = 360f / teeth

    repeat(teeth) { i ->
        withTransform({
            translate(center.x, center.y)
            rotate(rotation + step * i, Offset.Zero)
        }) {
            drawRoundRect(
                color = color,
                topLeft = Offset(-toothWidth / 2f, -radius - toothLength * 0.5f),
                size = Size(toothWidth, toothLength + radius * 0.2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(toothWidth * 0.35f),
            )
        }
    }
    drawCircle(color, radius = radius, center = center)
    drawCircle(holeColor, radius = radius * 0.36f, center = center)
}

/** The shield, with the welcome screen's bloom standing in it. */
private fun DrawScope.drawShield(
    center: Offset,
    width: Float,
    fill: Color,
    highlight: Color,
    outline: Color,
    emblem: Color,
    alpha: Float,
) {
    val hw = width / 2f
    val h = width * 1.24f
    val top = center.y - h / 2f
    val bottom = center.y + h / 2f
    val corner = width * 0.18f

    val shield = Path().apply {
        moveTo(center.x - hw, top + corner)
        quadraticTo(center.x - hw, top, center.x - hw + corner, top)
        lineTo(center.x + hw - corner, top)
        quadraticTo(center.x + hw, top, center.x + hw, top + corner)
        lineTo(center.x + hw, top + h * 0.44f)
        cubicTo(
            center.x + hw, top + h * 0.80f,
            center.x + hw * 0.52f, bottom - h * 0.06f,
            center.x, bottom,
        )
        cubicTo(
            center.x - hw * 0.52f, bottom - h * 0.06f,
            center.x - hw, top + h * 0.80f,
            center.x - hw, top + h * 0.44f,
        )
        close()
    }

    drawPath(shield, fill.copy(alpha = fill.alpha * alpha))
    // A soft crescent along the left edge keeps the flat fill from looking like a sticker.
    drawPath(
        Path().apply {
            moveTo(center.x - hw * 0.72f, top + h * 0.12f)
            quadraticTo(
                center.x - hw * 0.95f, center.y,
                center.x - hw * 0.18f, bottom - h * 0.10f,
            )
            quadraticTo(
                center.x - hw * 0.62f, center.y,
                center.x - hw * 0.42f, top + h * 0.12f,
            )
            close()
        },
        highlight.copy(alpha = highlight.alpha * alpha),
    )
    drawPath(
        shield,
        outline.copy(alpha = outline.alpha * alpha),
        style = Stroke(width = width * 0.035f),
    )

    // The emblem: the same bloom that drifts across the welcome screen, so the two
    // screens read as one piece of art rather than two unrelated illustrations.
    drawBloom(
        center = Offset(center.x, center.y - h * 0.04f),
        radius = width * 0.30f,
        petals = 6,
        rotation = 0f,
        color = emblem,
        coreColor = fill,
        stem = 0f,
        stemColor = emblem,
        alpha = alpha,
        scale = 1f,
    )
}

// ---------------------------------------------------------------- screen

/** The three things a new account has to answer before onboarding starts. */
private const val ConsentCount = 3

/**
 * "Tanangiz. Ma'lumotingiz." — the consent gate shown once, before onboarding.
 *
 * The first two consents are required: the app cannot hold health data without the
 * first, and cannot be used at all without the second, so [onContinue] stays disabled
 * until both are given. Tracking is genuinely optional and starts off.
 */
@Composable
fun ConsentGateScreen(
    state: AppState,
    onContinue: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entry.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }

    val required = state.consentStoreHealth && state.consentTerms

    // Status-bar inset comes from the onboarding host; only the bottom is ours.
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.md))
            ShieldIllustration(
                entry = entry.value,
                modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1.15f),
            )
            Spacer(Modifier.height(Spacing.lg))

            GateReveal(entry.value, from = 0.45f) {
                Text(
                    "Tanangiz. Ma'lumotingiz.",
                    style = Sadora.type.h1,
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            GateReveal(entry.value, from = 0.55f) {
                Text(
                    "Salomatlik ma'lumotlaringiz SADORA'dan tashqarida hech kimga " +
                        "berilmaydi va uni istalgan vaqtda o'chira olasiz.",
                    style = Sadora.type.body,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            GateReveal(entry.value, from = 0.65f) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ConsentLine(
                        checked = state.consentStoreHealth,
                        onCheckedChange = { state.consentStoreHealth = it },
                        text = buildAnnotatedString {
                            append("Salomatlik ma'lumotlarimni ilova funksiyalari uchun ")
                            append("qayta ishlashga roziman. Batafsil — ")
                            withLink("Maxfiylik siyosati", c.textAccent)
                            append(".")
                        },
                        onLinkClick = { onOpenLegal(LegalDocument.Privacy) },
                    )
                    ConsentLine(
                        checked = state.consentTerms,
                        onCheckedChange = { state.consentTerms = it },
                        text = buildAnnotatedString {
                            append("Men ")
                            withLink("Foydalanish shartlari", c.textAccent)
                            append(" va ")
                            withLink("Maxfiylik siyosati", c.textAccent)
                            append("ga roziman.")
                        },
                        onLinkClick = { onOpenLegal(LegalDocument.Terms) },
                    )
                    ConsentLine(
                        checked = state.consentAnalytics,
                        onCheckedChange = { state.consentAnalytics = it },
                        text = buildAnnotatedString {
                            append(
                                "Ilovadagi harakatlarim anonim tahlil qilinishiga roziman. " +
                                    "Bu ixtiyoriy va SADORA'ni yaxshilash uchun ishlatiladi.",
                            )
                        },
                        onLinkClick = null,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        GateReveal(entry.value, from = 0.75f) {
            Column(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    "Hammasiga rozilik",
                    style = Sadora.type.h3,
                    color = c.textAccent,
                    modifier = Modifier
                        .clip(Radius.chip)
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .noRippleClickable {
                            state.consentStoreHealth = true
                            state.consentTerms = true
                            state.consentAnalytics = true
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
                SadoraButton("Davom etish", onContinue, enabled = required)
            }
        }
    }
}

/** A consent row whose label carries tappable links. */
@Composable
private fun ConsentLine(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: AnnotatedString,
    onLinkClick: (() -> Unit)?,
) {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouchTarget)
            .padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SadoraCheckbox(checked, onCheckedChange)
        Text(
            text,
            style = Sadora.type.body,
            color = c.text,
            modifier = Modifier
                .weight(1f)
                // The links share the row's tap target: tapping the wording opens the
                // document, and the checkbox alone toggles consent. Tapping text to
                // agree would make it far too easy to consent while reaching for a link.
                .noRippleClickable(enabled = onLinkClick != null) { onLinkClick?.invoke() },
        )
    }
}

/** Appends [label] in the accent colour used for the legal links. */
private fun AnnotatedString.Builder.withLink(
    label: String,
    color: Color,
) {
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(label) }
}

/**
 * Fades a block in once the shared [entry] ramp passes [from].
 *
 * The whole screen runs off one `Animatable` rather than a timer per block, so the
 * sequence stays in step and a slow first frame delays everything together instead
 * of tearing the reveal apart.
 */
@Composable
private fun GateReveal(entry: Float, from: Float, content: @Composable () -> Unit) {
    val progress = ((entry - from) / (1f - from)).coerceIn(0f, 1f)
    val lift = with(LocalDensity.current) { 12.dp.toPx() }
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * lift
        },
    ) {
        content()
    }
}