package uz.sadora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.contract.CoinAward
import uz.sadora.contract.DailyCheckInResult

/**
 * The Gul balance, as the home header and the shop draw it.
 *
 * A pill rather than a plain number: the balance is spendable, so it should look like
 * something you can tap, and it always does open the wallet.
 */
@Composable
fun CoinPill(
    amount: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    val t = strings.rewards
    Row(
        modifier
            .clip(Radius.chip)
            .background(c.surface2)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.xs, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // The flower opens again whenever the balance rises — a coin arriving is a
        // bloom, and a spend is not.
        GulMark(size = 18.dp, bloom = true, bloomKey = rememberGainKey(amount))
        // Counts up rather than appearing: the balance changes while she is looking at
        // it — after a check-in, after a purchase — and the movement is what says so.
        AnimatedNumber(
            value = amount,
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.text,
            format = { Fmt.int(it) },
        )
        Text(
            t.coinName.lowercase(),
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = c.muted,
        )
    }
}

/**
 * The streak badge: a number with a flame that breathes.
 *
 * The flame is drawn from two arcs rather than an emoji so it can take the phase colour
 * and stay legible at 16dp, where "🔥" turns to mush.
 */
@Composable
fun StreakBadge(
    days: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    val breathing = rememberInfiniteTransition(label = "streak-breath")
    // Only a live streak breathes. A zero is a resting state, and a pulsing zero would
    // read as an alarm about something she has not done.
    val pulse by breathing.animateFloatUnlessReduced(
        initialValue = if (days > 0) 0.92f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "streak-pulse",
    )

    Row(
        modifier
            .clip(Radius.chip)
            .background(if (days > 0) c.secondary.copy(alpha = 0.14f) else c.surface2)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.xs, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }) {
            IconTile(
                SadoraIcons.Bloom,
                tint = if (days > 0) c.secondary else c.muted2,
                size = 18.dp,
                iconSize = 12.dp,
            )
        }
        Text(
            days.toString(),
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (days > 0) c.text else c.muted,
        )
    }
}

/**
 * The once-a-day celebration.
 *
 * Three rules shape it, and they are the reason it is a bespoke overlay rather than a
 * toast.
 *
 * It appears only when the server says a new day of the streak began — never on the
 * second launch of the same day. It leaves on its own after [HOLD_MILLIS]: a
 * celebration that waits for a tap is a dialog, and a dialog between her and the app on
 * every first open would be a tax, not a reward. And a tap anywhere dismisses it early,
 * because someone who opened the app to log a dose should not have to watch it.
 */
@Composable
fun StreakCelebration(
    result: DailyCheckInResult?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = result != null
    // Kept through the exit animation so the card does not blank as it scales away.
    val last = remember { androidx.compose.runtime.mutableStateOf<DailyCheckInResult?>(null) }
    result?.let { last.value = it }

    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        delay(HOLD_MILLIS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.Quick)),
        exit = fadeOut(tween(Motion.Standard)),
        modifier = modifier,
    ) {
        val shown = last.value ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                // The scrim is deliberately not opaque: the app stays visible behind it,
                // so the overlay reads as a moment rather than as a screen she must clear.
                .background(Color.Black.copy(alpha = 0.42f))
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // Petals drifting down the whole screen, only on a milestone: the days between
            // milestones are meant to feel ordinary, and a shower every morning would not.
            PetalShower(visible = shown.milestone != null)
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(Motion.Springy, initialScale = 0.72f) + fadeIn(tween(Motion.Quick)),
                exit = scaleOut(tween(Motion.Standard), targetScale = 0.9f) + fadeOut(tween(Motion.Quick)),
            ) {
                StreakCard(shown)
            }
        }
    }
}

@Composable
private fun StreakCard(result: DailyCheckInResult) {
    val c = Sadora.colors
    val t = strings.rewards
    val days = result.streak.current
    val milestone = result.milestone

    // One clock for the whole card, so the burst, the ring and the number are one
    // gesture rather than three animations that happen to start together.
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entry.animateTo(1f, tween(900, easing = Motion.Emphasized)) }

    Column(
        Modifier
            .padding(horizontal = Spacing.xl)
            .width(280.dp)
            .clip(Radius.card)
            .background(c.surface)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Petals bursting out behind the ring. An ordinary day gets a small burst,
            // a milestone a bigger one — so the milestone still feels like more.
            PetalBurst(entry.value, Modifier.size(if (milestone != null) 200.dp else 150.dp), count = if (milestone != null) 18 else 10)

            ProgressRing(
                progress = if (milestone != null) 1f else result.streak.milestoneProgress,
                size = 116.dp,
                strokeWidth = 9.dp,
                color = c.secondary,
                glow = true,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(
                        value = days,
                        style = Sadora.type.h1,
                        color = c.text,
                    )
                    Text(
                        strings.common.daysWord,
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted,
                    )
                }
            }
        }

        Text(
            when {
                milestone != null -> t.milestoneReached(milestone)
                days <= 1 -> t.streakStarted
                else -> t.streakDays(days)
            },
            style = Sadora.type.h2,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        Text(
            result.streak.nextMilestone
                ?.let { next -> t.daysToMilestone((next - days).coerceAtLeast(1), next) }
                ?: t.streakBeyondMilestones,
            style = Sadora.type.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )

        // What the open actually paid, one chip per award, arriving in sequence.
        if (result.awards.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                result.awards.forEachIndexed { index, award ->
                    AwardChip(award, Modifier.appearFromBelow(delayMillis = 320 + index * 140))
                }
            }
        }
    }
}

@Composable
private fun AwardChip(award: CoinAward, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val t = strings.rewards
    Row(
        modifier
            .clip(Radius.chip)
            .background(c.surface2)
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GulMark(size = 16.dp, bloom = true)
        Text(
            t.coinsGained(Fmt.int(award.amount)),
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.textAccent,
        )
        Text(award.title, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
    }
}

/** How long the celebration stays before it takes itself away. */
private const val HOLD_MILLIS = 2400L
