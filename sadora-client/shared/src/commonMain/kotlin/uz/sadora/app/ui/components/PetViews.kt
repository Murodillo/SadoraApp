package uz.sadora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.data.PetBubble
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.resources.*
import uz.sadora.contract.PetAction
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetPose

fun PetKind.art(pose: PetPose): DrawableResource = when (this) {
    PetKind.NILUFAR -> when (pose) {
        PetPose.IDLE -> Res.drawable.pet_nilufar_idle
        PetPose.HAPPY -> Res.drawable.pet_nilufar_happy
        PetPose.THINK -> Res.drawable.pet_nilufar_think
        PetPose.SLEEP -> Res.drawable.pet_nilufar_sleep
    }
    PetKind.MOMIQ -> when (pose) {
        PetPose.IDLE -> Res.drawable.pet_momiq_idle
        PetPose.HAPPY -> Res.drawable.pet_momiq_happy
        PetPose.THINK -> Res.drawable.pet_momiq_think
        PetPose.SLEEP -> Res.drawable.pet_momiq_sleep
    }
    PetKind.LAYLO -> when (pose) {
        PetPose.IDLE -> Res.drawable.pet_laylo_idle
        PetPose.HAPPY -> Res.drawable.pet_laylo_happy
        PetPose.THINK -> Res.drawable.pet_laylo_think
        PetPose.SLEEP -> Res.drawable.pet_laylo_sleep
    }
    PetKind.ANORXON -> when (pose) {
        PetPose.IDLE -> Res.drawable.pet_anorxon_idle
        PetPose.HAPPY -> Res.drawable.pet_anorxon_happy
        PetPose.THINK -> Res.drawable.pet_anorxon_think
        PetPose.SLEEP -> Res.drawable.pet_anorxon_sleep
    }
    PetKind.OHU -> when (pose) {
        PetPose.IDLE -> Res.drawable.pet_ohu_idle
        PetPose.HAPPY -> Res.drawable.pet_ohu_happy
        PetPose.THINK -> Res.drawable.pet_ohu_think
        PetPose.SLEEP -> Res.drawable.pet_ohu_sleep
    }
}

/**
 * The video loops drawn so far, as frames in composeResources/files/pets/<name>/NN.webp,
 * cut from Gemini (Veo) clips by tools/cut_pet_video.py. A pose without one falls back to
 * the still with its bob, so the loops can arrive one at a time.
 */
private class PetLoop(val name: String, val frames: Int, val fps: Int = 12)

private fun loopFor(pet: PetKind, pose: PetPose): PetLoop? = when (pose) {
    PetPose.IDLE -> when (pet) {
        PetKind.NILUFAR -> PetLoop("nilufar_wave", 49)
        PetKind.MOMIQ -> PetLoop("momiq_wave", 109)
        PetKind.LAYLO -> PetLoop("laylo_wave", 115)
        PetKind.ANORXON -> PetLoop("anorxon_wave", 101)
        PetKind.OHU -> PetLoop("ohu_wave", 94)
    }
    PetPose.HAPPY -> when (pet) {
        PetKind.NILUFAR -> PetLoop("nilufar_happy", 43)
        PetKind.MOMIQ -> PetLoop("momiq_happy", 49)
        PetKind.LAYLO -> PetLoop("laylo_happy", 111)
        PetKind.ANORXON -> PetLoop("anorxon_happy", 44)
        PetKind.OHU -> PetLoop("ohu_happy", 109)
    }
    PetPose.THINK -> when (pet) {
        PetKind.NILUFAR -> PetLoop("nilufar_think", 109)
        PetKind.MOMIQ -> PetLoop("momiq_think", 94)
        PetKind.LAYLO -> PetLoop("laylo_think", 112)
        PetKind.ANORXON -> PetLoop("anorxon_think", 102)
        PetKind.OHU -> PetLoop("ohu_think", 94)
    }
    PetPose.SLEEP -> when (pet) {
        PetKind.NILUFAR -> PetLoop("nilufar_sleep", 41)
        PetKind.MOMIQ -> PetLoop("momiq_sleep", 34)
        PetKind.LAYLO -> PetLoop("laylo_sleep", 44)
        PetKind.ANORXON -> PetLoop("anorxon_sleep", 42)
        PetKind.OHU -> PetLoop("ohu_sleep", 97)
    }
}

/**
 * A pet, alive. Where a video loop exists it plays it; otherwise the still bobs when
 * awake and breathes when asleep. Both stop with the system's reduce-motion.
 */
@Composable
fun PetImage(
    pet: PetKind,
    pose: PetPose,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val loop = loopFor(pet, pose)
    if (loop != null && !LocalReduceMotion.current) {
        PetLoopImage(loop, pet, pose, size, modifier)
        return
    }
    PetStill(pet, pose, size, modifier)
}

/**
 * Plays a loop. Only the encoded frames are held (under a megabyte), and each is
 * decoded as it comes up, so a playing pet costs one small bitmap rather than fifty.
 * Until the bytes are read the still stands in, so nothing pops.
 */
@Composable
private fun PetLoopImage(loop: PetLoop, pet: PetKind, pose: PetPose, size: Dp, modifier: Modifier) {
    var frame by remember(loop.name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(loop.name) {
        val bytes = withContext(Dispatchers.Default) {
            runCatching {
                (0 until loop.frames).map { Res.readBytes("files/pets/${loop.name}/${it.toString().padStart(3, '0')}.webp") }
            }.getOrNull()
        } ?: return@LaunchedEffect
        val step = 1000L / loop.fps
        var n = 0
        while (true) {
            val started = TimeSource.Monotonic.markNow()
            frame = withContext(Dispatchers.Default) { runCatching { bytes[n].decodeToImageBitmap() }.getOrNull() } ?: frame
            n = (n + 1) % bytes.size
            delay((step - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(1))
        }
    }
    val shown = frame
    if (shown == null) {
        PetStill(pet, pose, size, modifier)
    } else {
        Image(shown, contentDescription = strings.pet.name(pet), modifier = modifier.size(size))
    }
}

@Composable
private fun PetStill(
    pet: PetKind,
    pose: PetPose,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val asleep = pose == PetPose.SLEEP
    val life = rememberInfiniteTransition(label = "pet-life")
    val t by life.animateFloatUnlessReduced(
        0f, 1f,
        infiniteRepeatable(tween(if (asleep) 2600 else 1400, easing = Motion.Gentle), RepeatMode.Reverse),
        label = "pet-life-t",
    )
    Image(
        painter = painterResource(pet.art(pose)),
        contentDescription = strings.pet.name(pet),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                if (asleep) {
                    scaleY = 1f + 0.035f * t
                    scaleX = 1f - 0.01f * t
                } else {
                    translationY = -size.toPx() * 0.04f * t
                    scaleY = 1f - 0.015f * (1f - t)
                }
            },
    )
}

/**
 * The pet in the corner with its bubble. One at a time, above the tab bar, and gone on
 * its own after a few seconds — it is a word in passing, not a dialog.
 *
 * While it speaks the page behind dims, so the bubble reads as the one thing on screen;
 * a tap on the dimmed page, or Back, sends the pet away. [modifier] places the pet and
 * its bubble inside the full-screen layer this draws.
 */
@Composable
fun PetBubbleOverlay(
    bubble: PetBubble?,
    onAction: (PetAction) -> Unit,
    onWake: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SystemBackHandler(enabled = bubble != null) { onDismiss() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = bubble != null,
            enter = fadeIn(tween(Motion.Standard)),
            exit = fadeOut(tween(Motion.Standard)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = ScrimAlpha))
                    .noRippleClickable(onClick = onDismiss),
            )
        }
        PetSpeech(bubble, onAction, onWake, onDismiss, Modifier.align(Alignment.BottomEnd).then(modifier))
    }
}

@Composable
private fun PetSpeech(
    bubble: PetBubble?,
    onAction: (PetAction) -> Unit,
    onWake: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val last = remember { mutableStateOf<PetBubble?>(null) }
    bubble?.let { last.value = it }

    LaunchedEffect(bubble) {
        val shown = bubble ?: return@LaunchedEffect
        delay(if (shown.teaser) TeaserMillis else BubbleMillis)
        onDismiss()
    }

    AnimatedVisibility(
        visible = bubble != null,
        enter = slideInVertically(tween(Motion.Slow, easing = Motion.Emphasized)) { it / 2 } +
            fadeIn(tween(Motion.Standard)) + scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 1f)),
        exit = slideOutVertically(tween(Motion.Standard)) { it / 3 } + fadeOut(tween(Motion.Standard)),
        modifier = modifier,
    ) {
        val shown = last.value ?: return@AnimatedVisibility
        val c = Sadora.colors
        val type = Sadora.type
        val t = strings.pet
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Column(
                Modifier
                    .padding(bottom = 56.dp)
                    .widthIn(max = 232.dp)
                    .cardSurface(c)
                    .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = t.name(shown.pet),
                        style = type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.textAccent,
                        modifier = Modifier.weight(1f).padding(top = Spacing.xxs),
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .noRippleClickable(onClick = onDismiss)
                            .semantics { contentDescription = t.close },
                        contentAlignment = Alignment.Center,
                    ) {
                        // The plus, turned: the icon set has no cross of its own.
                        Icon(SadoraIcons.Plus, null, tint = c.muted, modifier = Modifier.size(16.dp).rotate(45f))
                    }
                }
                Text(
                    text = if (shown.teaser) t.teaser else shown.text.orEmpty(),
                    style = type.body,
                    color = c.text,
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                when {
                    shown.teaser -> PillButton(t.wake, onClick = onWake, tone = ButtonTone.Primary)
                    shown.action != null -> PillButton(t.action(shown.action), onClick = { onAction(shown.action) })
                }
            }
            PetImage(
                shown.pet,
                shown.pose,
                size = PetSize,
                modifier = Modifier.noRippleClickable { if (shown.teaser) onWake() else onDismiss() },
            )
        }
    }
}

private val PetSize = 104.dp

/** How dark the page behind the pet goes: enough to step back, not enough to hide it. */
private const val ScrimAlpha = 0.4f
private const val BubbleMillis = 9_000L
private const val TeaserMillis = 7_000L
