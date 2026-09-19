package uz.sadora.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SADORA colour tokens — the "SADORA PRODUCTS" deck palette.
 *
 * Lavender ground, white cards, a purple primary and the purple→pink brand gradient.
 * Every token exists in both themes and keeps the same semantic role, so a screen
 * built against these names renders correctly in light and dark without branching.
 * The dark set is also what the AI assistant screen is painted in regardless of the
 * app theme — the deck draws that one screen on navy.
 */
@Immutable
data class SadoraColors(
    /** Screen background. */
    val bg: Color,
    /** Cards, inputs, modals. */
    val surface: Color,
    /** Icon backgrounds, progress tracks. */
    val surface2: Color,
    /** Primary button, active tab, progress. Purple. */
    val primary: Color,
    /** Primary as *text* — darkened on light so it clears AA. */
    val textAccent: Color,
    /** The gradient's other end, and the period colour. Pink. */
    val secondary: Color,
    /** Water, sleep, fertile window. */
    val accent: Color,
    /** Accent as text (AA on light surfaces). */
    val accentText: Color,
    val text: Color,
    val muted: Color,
    /**
     * Dimmer than [muted] — timestamps, footnotes, tab labels. Still real text, so it
     * clears 4.5:1 on every surface; the two differ by a step, not by legibility.
     */
    val muted2: Color,
    val line: Color,
    val success: Color,
    /** [success] as text — the fill colour is 3.1:1 on white, too light to read. */
    val successText: Color,
    val warning: Color,
    val danger: Color,
    /** Content colour for filled primary buttons and gradient surfaces. */
    val onPrimary: Color,
    /** Card shadow tint. Lavender on light so the lift reads soft, not grey. */
    val shadow: Color,
    val isDark: Boolean,
) {
    /**
     * The brand gradient in order, purple first, pink second — a step deeper than
     * [primary] and [secondary], because nearly everything drawn on it is white text:
     * buttons, the Premium and AI cards, the avatar. On the lighter pair white fell to
     * 2.6:1 at the pink end; on these it holds 4.7:1 or better across the whole sweep.
     */
    val heroColors: List<Color>
        get() = listOf(HeroStart, HeroEnd)

    /**
     * The hero gradient. The design restricts it to a handful of places: hero
     * surfaces, the AI entry points, the primary CTA and the active tab.
     */
    val heroGradient: Brush
        get() = Brush.linearGradient(heroColors)

    /** The macro legend: protein, fat, carbohydrate. Same order everywhere. */
    val protein: Color get() = accent
    val fat: Color get() = secondary
    val carbs: Color get() = warningSoft

    /** A warm amber for carbohydrate and the calorie ring; [warning] is too dark for it. */
    val warningSoft: Color
        get() = if (isDark) Color(0xFFFFC46B) else Color(0xFFFFB13D)
}

val SadoraLightColors = SadoraColors(
    bg = Color(0xFFF7F5FF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1EDFF),
    primary = Color(0xFF7B61FF),
    textAccent = Color(0xFF6247E0),
    secondary = Color(0xFFFF6FB8),
    accent = Color(0xFF4FC3FF),
    accentText = Color(0xFF1B7FB0),
    text = Color(0xFF1A1630),
    muted = Color(0xFF66617F),
    muted2 = Color(0xFF6C6787),
    line = Color(0xFFEAE6FA),
    success = Color(0xFF2BA57A),
    successText = Color(0xFF1E7A5A),
    warning = Color(0xFF9A6200),
    danger = Color(0xFFD8404A),
    onPrimary = Color(0xFFFFFFFF),
    shadow = Color(0xFF7B61FF),
    isDark = false,
)

val SadoraDarkColors = SadoraColors(
    bg = Color(0xFF0F0D24),
    surface = Color(0xFF1B1838),
    surface2 = Color(0xFF272348),
    primary = Color(0xFF8E7BFF),
    textAccent = Color(0xFFB2A6FF),
    secondary = Color(0xFFFF7EC4),
    accent = Color(0xFF63D8FF),
    accentText = Color(0xFF63D8FF),
    text = Color(0xFFF3F0FA),
    muted = Color(0xFFA39DBF),
    muted2 = Color(0xFF948EB0),
    line = Color(0xFF2E2A52),
    success = Color(0xFF3FCF98),
    successText = Color(0xFF3FCF98),
    warning = Color(0xFFFFB020),
    danger = Color(0xFFFF5C64),
    onPrimary = Color(0xFFFFFFFF),
    shadow = Color(0xFF000000),
    isDark = true,
)

/** The deepened brand pair behind [SadoraColors.heroColors]; the same in both themes. */
private val HeroStart = Color(0xFF6A4FF0)
private val HeroEnd = Color(0xFFC93C88)

val LocalSadoraColors = staticCompositionLocalOf { SadoraLightColors }

/**
 * Per-life-stage accents. Pregnancy and postpartum shift to a warm palette so the
 * stage reads as its own experience rather than "Cycle with things switched off".
 */
@Immutable
data class StagePalette(val start: Color, val end: Color, val tint: Color)

object StagePalettes {
    /**
     * Text on the warm pregnancy/postpartum gradients. Those are too light for
     * [SadoraColors.onPrimary] in either theme, so they take a fixed dark ink.
     */
    val warmInk = Color(0xFF2A1145)

    val cycle = StagePalette(Color(0xFF7B61FF), Color(0xFFFF6FB8), Color(0xFF7B61FF))
    val pregnancy = StagePalette(Color(0xFFFFB020), Color(0xFFFF8E92), Color(0xFFFF8E92))
    val postpartum = StagePalette(Color(0xFFFF8AA3), Color(0xFFFFB020), Color(0xFFFF8AA3))
    val perimenopause = StagePalette(Color(0xFF7B61FF), Color(0xFF63D8FF), Color(0xFF9B85FF))
    val menopause = StagePalette(Color(0xFF63D8FF), Color(0xFF2FBF8F), Color(0xFF63D8FF))
}

/**
 * The four cycle phases as the deck draws them on the dial: period pink, follicular
 * blue, ovulation magenta, luteal purple. Fixed rather than themed — the legend under
 * the dial names them, and the same four colours have to mean the same thing on every
 * screen that shows the cycle.
 */
object PhaseColors {
    val period = Color(0xFFFF4F9A)
    val follicular = Color(0xFF4FB8FF)
    val fertile = Color(0xFFFF63C8)
    val luteal = Color(0xFF8E7BFF)
}
