package uz.sadora.doctor.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SADORA colour tokens — the "SADORA PRODUCTS" deck palette, the same as the client app's.
 *
 * Lavender ground, white cards, a purple primary and the purple→pink brand gradient.
 * Every token exists in both themes and keeps the same semantic role, so a screen
 * built against these names renders correctly in light and dark without branching.
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
    /**
     * Border of a control — a field, an idle chip, an outline button. [line] is a
     * hairline between surfaces (1.2:1); a control's edge is what tells her where to
     * tap, so it needs 3:1 against the surfaces it sits on (WCAG 1.4.11).
     */
    val lineStrong: Color,
    val success: Color,
    /** [success] as text — the fill colour is 3.1:1 on white, too light to read. */
    val successText: Color,
    val warning: Color,
    /** [warning] as text, including on its own 12–15% wash (a TintChip). */
    val warningText: Color,
    /** Fills only — a dot, a border, a wash. Text in this colour uses [dangerText]. */
    val danger: Color,
    /** Errors and destructive labels: 4.5:1 on every surface and on a danger wash. */
    val dangerText: Color,
    /**
     * A solid purple that white text sits on — my bubbles, count pills, the filled
     * pill button. [primary] is too light for that (white was 4.2:1 / 3.3:1); this is
     * the hero gradient's start, 5.26:1 under white, the same in both themes.
     */
    val primaryFill: Color,
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
}

/** The deepened brand pair behind [SadoraColors.heroColors]; the same in both themes.
 * Declared above the palettes: top-level vals initialise in file order. */
private val HeroStart = Color(0xFF6A4FF0)
private val HeroEnd = Color(0xFFC93C88)

val SadoraLightColors = SadoraColors(
    bg = Color(0xFFF7F5FF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1EDFF),
    primary = Color(0xFF7B61FF),
    textAccent = Color(0xFF5E43DC),
    secondary = Color(0xFFFF6FB8),
    accent = Color(0xFF4FC3FF),
    accentText = Color(0xFF1B7FB0),
    text = Color(0xFF1A1630),
    muted = Color(0xFF66617F),
    muted2 = Color(0xFF6C6787),
    line = Color(0xFFEAE6FA),
    lineStrong = Color(0xFF847E9F),
    success = Color(0xFF2BA57A),
    successText = Color(0xFF1A6E50),
    warning = Color(0xFF9A6200),
    warningText = Color(0xFF855300),
    danger = Color(0xFFD8404A),
    dangerText = Color(0xFFB32A34),
    primaryFill = HeroStart,
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
    lineStrong = Color(0xFF78729F),
    success = Color(0xFF3FCF98),
    successText = Color(0xFF3FCF98),
    warning = Color(0xFFFFB020),
    warningText = Color(0xFFFFB020),
    danger = Color(0xFFFF5C64),
    dangerText = Color(0xFFFF878E),
    primaryFill = HeroStart,
    onPrimary = Color(0xFFFFFFFF),
    shadow = Color(0xFF000000),
    isDark = true,
)

val LocalSadoraColors = staticCompositionLocalOf { SadoraLightColors }
