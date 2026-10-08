package uz.sadora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.resources.*
import uz.sadora.contract.BadgeBoard
import uz.sadora.contract.BadgeState
import uz.sadora.contract.BadgeUnlock
import uz.sadora.contract.WornBadge
import uz.sadora.app.data.wornBadge
import uz.sadora.contract.PetPose

// ------------------------------------------------------------------ the unlock moment

/**
 * A tier just reached, celebrated.
 *
 * The medal is struck rather than shown: it spins in edge-first like a coin tossed onto
 * a table, overshoots, and lands with a tap of haptics and a ring of light; rays turn
 * slowly behind it and petals burst from where it landed. Gold and the single moments
 * get the petal shower over the whole screen as well, so the top tier still feels like
 * more the fifth time she sees one.
 *
 * One at a time: several arriving together (a first read after an update can carry a
 * handful) queue up behind "Ajoyib!", with a count of what is still to come and a way
 * to close them all. Unlike the streak card it waits for her — a badge is rarer than a
 * day, and the words on it are worth reading.
 */
@Composable
fun BadgeUnlockOverlay(
    unlock: BadgeUnlock?,
    remaining: Int,
    onNext: (BadgeUnlock) -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = remember { mutableStateOf<BadgeUnlock?>(null) }
    unlock?.let { last.value = it }
    SystemBackHandler(enabled = unlock != null) { unlock?.let(onNext) }

    AnimatedVisibility(
        visible = unlock != null,
        enter = fadeIn(tween(Motion.Standard)),
        exit = fadeOut(tween(Motion.Standard)),
        modifier = modifier,
    ) {
        val shown = last.value ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .noRippleClickable { },
            contentAlignment = Alignment.Center,
        ) {
            // Keyed on the tier, so the next badge in the queue is struck from the start.
            key(shown.key, shown.tier) {
                val metal = BadgeMetal.of(shown.tier, shown.maxTier)
                PetalShower(visible = metal == BadgeMetal.Gold || metal == BadgeMetal.Rose)
                UnlockCard(shown, remaining, onNext = { onNext(shown) }, onSkipAll = onSkipAll)
            }
        }
    }
}

@Composable
private fun UnlockCard(
    unlock: BadgeUnlock,
    remaining: Int,
    onNext: () -> Unit,
    onSkipAll: () -> Unit,
) {
    val c = Sadora.colors
    val t = strings.badges
    val metal = BadgeMetal.of(unlock.tier, unlock.maxTier)
    val palette = metal.palette(c.isDark)
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current

    // The strike: scale on a loose spring, the spin on a decelerating tween, and one
    // clock for everything that follows the landing (ring, burst, rays).
    val scale = remember { Animatable(if (reduce) 1f else 0.15f) }
    val spin = remember { Animatable(if (reduce) 0f else 720f) }
    val landing = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow)) }
        spin.animateTo(0f, tween(1100, easing = Motion.Emphasized))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        landing.animateTo(1f, tween(1300, easing = LinearEasing))
    }

    val rays = rememberInfiniteTransition(label = "badge-rays")
    val turn by rays.animateFloatUnlessReduced(
        0f, 360f,
        infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "badge-rays-turn",
    )

    Column(
        Modifier
            .padding(horizontal = Spacing.xl)
            .width(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            // Sunburst: twelve soft rays turning slowly, growing in with the medal.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val grow = scale.value.coerceIn(0f, 1f)
                        scaleX = 0.4f + 0.6f * grow
                        scaleY = 0.4f + 0.6f * grow
                        alpha = grow
                    },
            ) {
                val r = size.minDimension / 2f
                rotate(turn) {
                    for (i in 0 until RAYS) {
                        val a = i * (2f * PI.toFloat() / RAYS)
                        val w = 0.11f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(center.x, center.y)
                            lineTo(center.x + cos(a - w) * r, center.y + sin(a - w) * r)
                            lineTo(center.x + cos(a + w) * r, center.y + sin(a + w) * r)
                            close()
                        }
                        drawPath(
                            path,
                            Brush.radialGradient(
                                listOf(palette.glow.copy(alpha = 0.55f), Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                        )
                    }
                }
                drawCircle(
                    Brush.radialGradient(
                        listOf(palette.glow.copy(alpha = 0.5f), Color.Transparent),
                        center = center,
                        radius = r * 0.7f,
                    ),
                    radius = r * 0.7f,
                )
            }

            // The flash ring the landing sends out.
            Canvas(Modifier.fillMaxSize()) {
                val p = landing.value
                if (p <= 0f || p >= 0.6f) return@Canvas
                val local = p / 0.6f
                val r = size.minDimension * (0.26f + 0.26f * local)
                drawCircle(
                    Color.White.copy(alpha = (1f - local) * 0.85f),
                    radius = r,
                    style = Stroke(width = size.minDimension * 0.025f * (1f - local) + 1f),
                )
            }

            PetalBurst(landing.value.coerceIn(0f, 1f), Modifier.size(260.dp), count = 16)

            // Her companion, delighted, at the medal's feet.
            CompanionOr(PetPose.HAPPY, 84.dp, Modifier.align(Alignment.BottomEnd))

            // Sparkles thrown outward from the medal as it lands.
            Canvas(Modifier.fillMaxSize()) {
                val p = landing.value
                if (p <= 0f || p >= 1f) return@Canvas
                val eased = 1f - (1f - p) * (1f - p)
                val r = size.minDimension / 2f
                for (i in 0 until FLYING_SPARKLES) {
                    val a = i * (2f * PI.toFloat() / FLYING_SPARKLES) + 0.3f
                    val d = r * (0.38f + 0.5f * eased)
                    drawSparkle(
                        Offset(center.x + cos(a) * d, center.y + sin(a) * d),
                        r * 0.07f * (1f - p),
                        if (i % 2 == 0) Color.White else palette.light,
                    )
                }
            }

            BadgeMedal(
                key = unlock.key,
                tier = unlock.tier,
                maxTier = unlock.maxTier,
                size = 150.dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationY = spin.value
                    cameraDistance = 14f * density
                },
            )
        }

        val name = t.name(unlock.key).orEmpty()
        val tierName = t.tierName(unlock.tier, unlock.maxTier)
        Text(
            t.newBadge.uppercase(),
            style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = TextUnit.Unspecified),
            color = palette.light,
            modifier = Modifier.appearFromBelow(delayMillis = 450),
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            name,
            style = Sadora.type.h1,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.appearFromBelow(delayMillis = 540),
        )
        Spacer(Modifier.height(Spacing.xs))
        TierPill(tierName = t.tierReached(tierName), metal = metal, modifier = Modifier.appearFromBelow(delayMillis = 630))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            t.goal(unlock.key, uz.sadora.contract.Badges.tiersOf(unlock.key).getOrNull(unlock.tier - 1) ?: 0),
            style = Sadora.type.body,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
            modifier = Modifier.appearFromBelow(delayMillis = 720),
        )
        if (unlock.coins > 0) {
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier
                    .appearFromBelow(delayMillis = 820)
                    .clip(Radius.chip)
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = Spacing.sm, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GulMark(size = 18.dp, bloom = true, bloomKey = unlock.tier)
                Text(
                    strings.rewards.coinsGained(Fmt.int(unlock.coins)),
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        SadoraButton(t.continueLabel, onNext, modifier = Modifier.appearFromBelow(delayMillis = 900))
        if (remaining > 0) {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                Modifier.appearFromBelow(delayMillis = 960),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(t.more(remaining), style = Sadora.type.body, color = Color.White.copy(alpha = 0.75f))
                Text(
                    t.skipAll,
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.clip(Radius.chip).pressable(onClick = onSkipAll).padding(horizontal = Spacing.xs, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * The badge she wears, inline after a name: on a post, a comment, a profile. Just the
 * medal — the name beside it is the point, the medal is the ornament — at a size that
 * sits on the text's line, with its gesture still running.
 */
@Composable
fun WornBadgeMark(worn: WornBadge?, size: androidx.compose.ui.unit.Dp = 22.dp, modifier: Modifier = Modifier) {
    if (worn == null || badgeArt(worn.key) == null) return
    val t = strings.badges
    val name = t.name(worn.key) ?: return
    val label = "$name, ${t.tierName(worn.tier, worn.maxTier)}"
    BadgeMedal(
        key = worn.key,
        tier = worn.tier,
        maxTier = worn.maxTier,
        size = size,
        halo = false,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/** The tier's name on a strip of its own metal. */
@Composable
fun TierPill(tierName: String, metal: BadgeMetal, modifier: Modifier = Modifier) {
    val palette = metal.palette(false)
    Text(
        tierName,
        style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = TextUnit.Unspecified),
        color = if (metal == BadgeMetal.Locked) Sadora.colors.muted else palette.dark.darken(),
        modifier = modifier
            .clip(Radius.chip)
            .background(Brush.horizontalGradient(listOf(palette.light, palette.mid, palette.light)))
            .padding(horizontal = Spacing.sm, vertical = 4.dp),
    )
}

/** A metal's darkest stop, darker still, so text on its own strip clears contrast. */
private fun Color.darken(): Color = Color(red * 0.62f, green * 0.62f, blue * 0.62f, alpha)

private const val RAYS = 12
private const val FLYING_SPARKLES = 10

// ------------------------------------------------------------------ the board

/**
 * The badges page's header: the laurel, how many tiers she has of how many, a bar for
 * it, and the badge she wears beside her name — or how to choose one.
 */
@Composable
fun BadgesHeroCard(board: BadgeBoard?, modifier: Modifier = Modifier, onUpgrade: () -> Unit = {}) {
    val c = Sadora.colors
    val t = strings.badges
    SadoraCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ArtIcon(Res.drawable.badge_laurel, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.subtitle, style = Sadora.type.body, color = c.muted)
                if (board != null) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AnimatedNumber(board.earnedCount, Sadora.type.h1, c.text)
                        Text(
                            "/ ${board.totalCount}",
                            style = Sadora.type.h3,
                            color = c.muted2,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
        if (board != null) {
            SadoraProgressBar(
                if (board.totalCount == 0) 0f else board.earnedCount.toFloat() / board.totalCount,
                gradient = true,
            )
        }
        val worn = board?.wornBadge()
        val locked = board != null && !board.canWear
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Radius.card)
                .background(c.surface2)
                .then(if (locked) Modifier.pressable(onClick = onUpgrade) else Modifier)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (worn != null) {
                WornBadgeMark(worn, size = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(t.wornLabel, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                    Text(
                        t.name(worn.key).orEmpty(),
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.text,
                    )
                }
            } else if (locked) {
                ArtIcon(Res.drawable.ic3d_crown, 32.dp)
                Text(t.wornPremiumOnly, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.text, modifier = Modifier.weight(1f))
                Icon(SadoraIcons.ChevronRight, contentDescription = null, tint = c.muted2, modifier = Modifier.size(18.dp))
            } else {
                Text(t.wornNone, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * A titled three-column grid of medals, each with its name and a pip per tier. Tapping
 * one opens [BadgeDetailSheet]. [indexOffset] keeps the stagger and the gesture phases
 * running on from the grid above it.
 */
@Composable
fun BadgeGridCard(
    title: String,
    badges: List<BadgeState>,
    worn: String?,
    onOpen: (BadgeState) -> Unit,
    modifier: Modifier = Modifier,
    indexOffset: Int = 0,
) {
    val c = Sadora.colors
    val t = strings.badges
    val shown = badges.filter { badgeArt(it.key) != null && t.name(it.key) != null }
    if (shown.isEmpty()) return
    SadoraCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
            Text(shown.size.toString(), style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.muted2)
        }
        shown.chunked(3).forEachIndexed { row, three ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                three.forEachIndexed { col, badge ->
                    val index = indexOffset + row * 3 + col
                    BadgeCell(
                        badge,
                        index,
                        worn = worn == badge.key,
                        Modifier.weight(1f).appearFromBelow(index = index),
                        onClick = { onOpen(badge) },
                    )
                }
                repeat(3 - three.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The board while it loads: the shape of the grid, so the page does not jump. */
@Composable
fun BadgeGridSkeleton() {
    SadoraCard {
        repeat(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) { Skeleton(Modifier.size(78.dp).clip(Radius.chip)) }
            }
        }
    }
}

@Composable
private fun BadgeCell(badge: BadgeState, index: Int, worn: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Sadora.colors
    val t = strings.badges
    val name = t.name(badge.key).orEmpty()
    val metal = BadgeMetal.of(badge.tier, badge.maxTier)
    Column(
        modifier
            .clip(Radius.card)
            .pressable(onClick = onClick)
            .padding(vertical = Spacing.xs)
            .semantics { contentDescription = "$name, ${if (badge.tier > 0) t.tierName(badge.tier, badge.maxTier) else t.locked}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BadgeMedal(
            key = badge.key,
            tier = badge.tier,
            maxTier = badge.maxTier,
            size = 78.dp,
            progress = if (badge.tier == 0) badge.nextProgress else 0f,
            phase = index,
        )
        Text(
            name,
            style = Sadora.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = TextUnit.Unspecified),
            color = if (badge.tier > 0) c.text else c.muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
        )
        if (worn) {
            Text(
                t.wearing,
                style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = TextUnit.Unspecified),
                color = c.onPrimary,
                modifier = Modifier
                    .clip(Radius.chip)
                    .background(c.heroGradient)
                    .padding(horizontal = Spacing.xs, vertical = 1.dp),
            )
        } else {
            TierPips(badge.tier, badge.maxTier, metal)
        }
    }
}

/** One dot per tier, filled in the metal of the tier reached. */
@Composable
fun TierPips(tier: Int, maxTier: Int, metal: BadgeMetal) {
    val c = Sadora.colors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(maxTier) { i ->
            val on = i < tier
            Box(
                Modifier
                    .size(if (on) 7.dp else 6.dp)
                    .clip(Radius.chip)
                    .background(if (on) metal.palette(c.isDark).mid else c.line),
            )
        }
    }
}

/**
 * One badge up close: the medal large, every tier with what it asks, and how far she is
 * toward the next one.
 */
@Composable
fun BadgeDetailSheet(
    badge: BadgeState?,
    onDismiss: () -> Unit,
    /** The key she wears now, so the button can say "take off" on it. */
    worn: String? = null,
    /** Wear this badge, or take hers off with null. */
    onWear: (String?) -> Unit = {},
    /** Premium: false turns the button into the way to the paywall. */
    canWear: Boolean = true,
    onUpgrade: () -> Unit = {},
) {
    val c = Sadora.colors
    val t = strings.badges
    val last = remember { mutableStateOf<BadgeState?>(null) }
    badge?.let { last.value = it }
    val shown = last.value
    SadoraBottomSheet(
        visible = badge != null,
        title = shown?.let { t.name(it.key) }.orEmpty(),
        onDismiss = onDismiss,
    ) {
        if (shown == null) return@SadoraBottomSheet
        val metal = BadgeMetal.of(shown.tier, shown.maxTier)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeMedal(
                key = shown.key,
                tier = shown.tier,
                maxTier = shown.maxTier,
                size = 150.dp,
                progress = if (shown.tier == 0) shown.nextProgress else 0f,
            )
            Spacer(Modifier.height(Spacing.xs))
            TierPill(
                if (shown.tier > 0) t.tierName(shown.tier, shown.maxTier) else t.locked,
                metal,
            )
        }

        // The ladder: each tier, its goal, and whether it is done.
        shown.thresholds.forEachIndexed { i, target ->
            val tier = i + 1
            val done = shown.tier >= tier
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BadgeMedal(
                    key = shown.key,
                    tier = if (done) tier else 0,
                    maxTier = shown.maxTier,
                    size = 40.dp,
                    animate = false,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        t.tierName(tier, shown.maxTier),
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = if (done) c.text else c.muted,
                    )
                    Text(
                        t.goal(shown.key, target),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted,
                    )
                }
                if (done) {
                    Icon(SadoraIcons.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        "${minOf(shown.progress, target)} / $target",
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                    )
                }
            }
        }

        val next = shown.nextThreshold
        if (next != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    t.next(t.tierName(shown.tier + 1, shown.maxTier), shown.progress, next),
                    style = Sadora.type.body,
                    color = c.text,
                )
                SadoraProgressBar(shown.nextProgress, gradient = true)
            }
        } else {
            Text(t.allDone, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.successText)
        }

        // Only an earned badge can be worn: the medal after her name says she did it.
        if (shown.tier > 0) {
            val wearing = worn == shown.key
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                if (canWear) {
                    SadoraButton(
                        if (wearing) t.takeOff else t.wear,
                        { onWear(if (wearing) null else shown.key) },
                        tone = if (wearing) ButtonTone.Outline else ButtonTone.Primary,
                    )
                    Text(t.wearHint, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                } else {
                    // Free: the badge is hers to keep; only wearing it is Premium.
                    SadoraButton(t.wearPremium, onUpgrade, icon = SadoraIcons.Lock)
                    Text(t.wearPremiumHint, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                }
            }
        }

        Text(t.principle, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    }
}

/**
 * A short row of her latest medals, for the profile: the three most recent earned and
 * the count, opening the board on a tap. Draws nothing until she has one.
 */
@Composable
fun BadgeStrip(
    board: BadgeBoard?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** True on the wallet, where it is the door to the page even before the first badge. */
    alwaysShow: Boolean = false,
) {
    val c = Sadora.colors
    val t = strings.badges
    val earned = board?.badges.orEmpty()
        .filter { it.tier > 0 && badgeArt(it.key) != null }
        .sortedByDescending { it.earnedAt }
    if (!alwaysShow && (board == null || earned.isEmpty())) return
    SadoraCard(modifier, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (alwaysShow) ArtIcon(Res.drawable.badge_laurel, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(t.title, style = Sadora.type.h3, color = c.text)
                Text(
                    if (board == null) t.subtitle else t.earnedOf(board.earnedCount, board.totalCount),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                earned.take(3).forEachIndexed { i, badge ->
                    BadgeMedal(badge.key, badge.tier, badge.maxTier, 48.dp, phase = i)
                }
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, tint = c.muted2, modifier = Modifier.size(18.dp))
        }
    }
}
