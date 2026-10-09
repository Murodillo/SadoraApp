package uz.sadora.app.ui.modules

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.data.PromptPrefs
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.resources.Res
import uz.sadora.app.resources.ic3d_watch
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.claySurface
import uz.sadora.app.ui.components.animateFloatUnlessReduced

/** Days after sign-up before the question is first asked: long enough to have used the app. */
const val WearableAskAfterDays = 3

/** How long a "later" holds the question back. */
const val WearableSnoozeDays = 7

/**
 * Whether the smart-device question is due for this account today.
 *
 * Never for someone who has answered, or who already has a device connected — the
 * question would only be asking what the devices screen already knows.
 */
suspend fun wearableQuestionDue(
    prompts: PromptPrefs,
    userId: String,
    hasWearable: Boolean?,
    memberSince: LocalDate?,
    deviceConnected: Boolean,
    today: LocalDate,
): Boolean {
    if (hasWearable != null || deviceConnected || memberSince == null) return false
    if (today < memberSince.plus(DatePeriod(days = WearableAskAfterDays))) return false
    val snoozedUntil = prompts.wearableAskAfter(userId)
    return snoozedUntil == null || today >= snoozedUntil
}

/** The date a "later" moves the question to. */
fun wearableSnoozedUntil(today: LocalDate): LocalDate = today.plus(DatePeriod(days = WearableSnoozeDays))

/**
 * "Do you wear a smart watch or band?" — asked a few days in, not at sign-up.
 *
 * Sign-up is the wrong moment for it: she has not seen the sleep tab yet, so "connect a
 * device" means nothing. The picture says what the yes is for — the watch hands its
 * readings to the phone — before she is asked to answer.
 */
@Composable
fun WearablePromptSheet(
    visible: Boolean,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onLater: () -> Unit,
) {
    val t = strings.devices
    val c = Sadora.colors
    SadoraBottomSheet(visible = visible, title = t.askTitle, onDismiss = onLater) {
        Text(t.askBody, style = Sadora.type.bodyPretty, color = c.muted)
        WatchToPhoneArt(Modifier.fillMaxWidth().height(176.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        ) {
            t.askReadings.forEach { reading ->
                Text(
                    reading,
                    style = Sadora.type.caption.copy(letterSpacing = 0.sp),
                    color = c.textAccent,
                    modifier = Modifier
                        .clip(Radius.chip)
                        .background(c.primary.copy(alpha = 0.12f))
                        .padding(horizontal = Spacing.sm, vertical = 6.dp),
                )
            }
        }
        Text(
            t.askBrands,
            style = Sadora.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xxs))
        SadoraButton(t.askYes, onYes)
        SadoraButton(t.askNo, onNo, tone = ButtonTone.Outline)
        SadoraButton(t.askLater, onLater, tone = ButtonTone.Ghost)
    }
}

/**
 * The watch on the left, the phone on the right, and the readings travelling between
 * them: sleep bars and a pulse fill in on the phone as the dots arrive.
 */
@Composable
private fun WatchToPhoneArt(modifier: Modifier) {
    val c = Sadora.colors
    val motion = rememberInfiniteTransition(label = "wearable-art")
    val travel by motion.animateFloatUnlessReduced(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "dots",
        still = 0.5f,
    )
    val bob by motion.animateFloatUnlessReduced(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
        still = 0f,
    )
    val fill by motion.animateFloatUnlessReduced(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "fill",
        still = 1f,
    )

    Box(
        modifier.claySurface(c, Radius.card, c.surface2, elevation = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(Res.drawable.ic3d_watch),
                contentDescription = null,
                modifier = Modifier.size(92.dp).graphicsLayer { translationY = bob.dp.toPx() },
            )

            // The readings on their way: three dots, each a third of a lap behind the last.
            Canvas(Modifier.weight(1f).height(24.dp)) {
                val y = size.height / 2
                val r = 4.dp.toPx()
                drawLine(
                    c.line,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                )
                repeat(3) { i ->
                    val p = (travel + i / 3f) % 1f
                    val alpha = when {
                        p < 0.15f -> p / 0.15f
                        p > 0.85f -> (1f - p) / 0.15f
                        else -> 1f
                    }
                    drawCircle(c.primary.copy(alpha = alpha), r, Offset(size.width * p, y))
                }
            }

            PhoneSketch(fill)
        }
    }
}

/** A phone with the sleep tab on it, its bars growing as the readings come in. */
@Composable
private fun PhoneSketch(fill: Float) {
    val c = Sadora.colors
    Column(
        Modifier
            .size(width = 76.dp, height = 132.dp)
            .claySurface(c, RoundedCornerShape(18.dp), c.surface, elevation = 8.dp)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 22.dp, height = 4.dp).clip(Radius.chip).background(c.line))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(SadoraIcons.Moon, null, Modifier.size(12.dp), tint = c.primary)
            Box(Modifier.size(width = 30.dp, height = 5.dp).clip(Radius.chip).background(c.line))
        }
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(0.55f, 0.9f, 0.7f, 1f, 0.6f).forEach { h ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(h * fill)
                        .clip(RoundedCornerShape(3.dp))
                        .background(c.primary.copy(alpha = 0.75f)),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(SadoraIcons.Heart, null, Modifier.size(12.dp), tint = c.danger)
            Box(Modifier.width(30.dp * fill).height(5.dp).clip(Radius.chip).background(c.danger.copy(alpha = 0.6f)))
        }
    }
}
