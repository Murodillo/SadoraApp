package org.example.project.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SADORA colour tokens — section "01 · POYDEVOR" of the design.
 *
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
    /** Primary button, active tab. */
    val primary: Color,
    /** Primary as *text* — darkened on light so it clears AA. */
    val textAccent: Color,
    /** Icons, second data series, Learn. */
    val secondary: Color,
    /** Water, sleep, fertile window. */
    val accent: Color,
    /** Accent as text (AA on light surfaces). */
    val accentText: Color,
    val text: Color,
    val muted: Color,
    /** Dimmer than [muted] — timestamps, footnotes. */
    val muted2: Color,
    val line: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Content colour for filled primary buttons. */
    val onPrimary: Color,
    val isDark: Boolean,
) {
    /**
     * The hero gradient. The design restricts it to exactly four places:
     * hero, AI, Premium CTA and the FAB.
     */
    val heroGradient: Brush
        get() = Brush.linearGradient(listOf(secondary, primary))
}

val SadoraLightColors = SadoraColors(
    bg = Color(0xFFFBF8FF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF2ECFF),
    primary = Color(0xFFFF5A7D),
    textAccent = Color(0xFFD62F52),
    secondary = Color(0xFF7B61FF),
    accent = Color(0xFF4FD1FF),
    accentText = Color(0xFF1D7FA6),
    text = Color(0xFF1E1A2E),
    muted = Color(0xFF736C8C),
    muted2 = Color(0xFF8E86A8),
    line = Color(0xFFE9E2F8),
    success = Color(0xFF1B8A66),
    warning = Color(0xFF8A5A00),
    danger = Color(0xFFC42B30),
    onPrimary = Color(0xFFFFFFFF),
    isDark = false,
)

val SadoraDarkColors = SadoraColors(
    bg = Color(0xFF131020),
    surface = Color(0xFF1C1730),
    surface2 = Color(0xFF272040),
    primary = Color(0xFFFF6E8C),
    textAccent = Color(0xFFFF6E8C),
    secondary = Color(0xFF9B85FF),
    accent = Color(0xFF63D8FF),
    accentText = Color(0xFF63D8FF),
    text = Color(0xFFF3F0FA),
    muted = Color(0xFF9A93B4),
    muted2 = Color(0xFFA9A2BE),
    line = Color(0xFF322B4D),
    success = Color(0xFF2FBF8F),
    warning = Color(0xFFFFB020),
    danger = Color(0xFFE5484D),
    onPrimary = Color(0xFF131020),
    isDark = true,
)

val LocalSadoraColors = staticCompositionLocalOf { SadoraDarkColors }

/**
 * Per-life-stage accents. Pregnancy and postpartum shift to a warm palette so the
 * stage reads as its own experience rather than "Cycle with things switched off".
 */
@Immutable
data class StagePalette(val start: Color, val end: Color, val tint: Color)

object StagePalettes {
    val cycle = StagePalette(Color(0xFF9B85FF), Color(0xFFFF6E8C), Color(0xFFFF6E8C))
    val pregnancy = StagePalette(Color(0xFFFFB020), Color(0xFFFF8E92), Color(0xFFFF8E92))
    val postpartum = StagePalette(Color(0xFFFF8AA3), Color(0xFFFFB020), Color(0xFFFF8AA3))
    val perimenopause = StagePalette(Color(0xFF7B61FF), Color(0xFF63D8FF), Color(0xFF9B85FF))
    val menopause = StagePalette(Color(0xFF63D8FF), Color(0xFF2FBF8F), Color(0xFF63D8FF))
}
