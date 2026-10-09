package uz.sadora.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import uz.sadora.app.ui.components.ClayLitAlphaLight
import uz.sadora.app.ui.components.HeroLabelGloss
import uz.sadora.app.ui.components.LegendaryGoldTextDark
import uz.sadora.app.ui.components.LegendaryGoldTextLight
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG AA for the token pairs the app actually draws text with. A palette tweak that
 * pushes one of them under 4.5:1 fails here instead of on a phone in the sun.
 */
class ContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAa(fg: Color, bg: Color, what: String) {
        val r = ratio(fg, bg)
        assertTrue(r >= 4.5, "$what is ${(r * 100).toInt() / 100.0}:1, needs 4.5")
    }

    @Test
    fun textTokensClearAaOnEverySurface() {
        listOf("light" to SadoraLightColors, "dark" to SadoraDarkColors).forEach { (name, c) ->
            listOf("bg" to c.bg, "surface" to c.surface, "surface2" to c.surface2).forEach { (bgName, bg) ->
                assertAa(c.text, bg, "$name text on $bgName")
                assertAa(c.muted, bg, "$name muted on $bgName")
                assertAa(c.muted2, bg, "$name muted2 on $bgName")
            }
            assertAa(c.textAccent, c.surface, "$name textAccent on surface")
            assertAa(c.successText, c.surface, "$name successText on surface")
        }
    }

    @Test
    fun whiteHoldsAcrossTheHeroGradient() {
        listOf(SadoraLightColors, SadoraDarkColors).forEach { c ->
            val (start, end) = c.heroColors
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { t ->
                val stop = androidx.compose.ui.graphics.lerp(start, end, t)
                assertAa(c.onPrimary, stop, "white at $t of the hero gradient")
            }
        }
    }

    /** WCAG 1.4.11: a control's edge, a chart's bar, a phase's dot — 3:1 on what is behind it. */
    private fun assertNonText(fg: Color, bg: Color, what: String) {
        val r = ratio(fg, bg)
        assertTrue(r >= 3.0, "$what is ${(r * 100).toInt() / 100.0}:1, needs 3")
    }

    private val themes = listOf("light" to SadoraLightColors, "dark" to SadoraDarkColors)

    private fun SadoraColors.grounds() = listOf("bg" to bg, "surface" to surface, "surface2" to surface2)

    /** [tint] at [alpha] laid over [ground] — the pale wash a badge or a strip sits on. */
    private fun wash(tint: Color, alpha: Float, ground: Color) = tint.copy(alpha = alpha).compositeOver(ground)

    @Test
    fun semanticTextTokensClearAaOnEverySurface() {
        themes.forEach { (name, c) ->
            c.grounds().forEach { (bgName, bg) ->
                assertAa(c.dangerText, bg, "$name dangerText on $bgName")
                assertAa(c.accentText, bg, "$name accentText on $bgName")
                assertAa(c.warningText, bg, "$name warningText on $bgName")
                assertAa(c.secondaryText, bg, "$name secondaryText on $bgName")
                assertAa(c.successText, bg, "$name successText on $bgName")
                assertAa(c.textAccent, bg, "$name textAccent on $bgName")
            }
            // Destructive buttons, the out-of-stock line: on a card.
            assertAa(c.warning, c.surface, "$name warning on surface")
            val gold = if (c.isDark) LegendaryGoldTextDark else LegendaryGoldTextLight
            assertAa(gold, c.surface, "$name legendary gold text on surface")
        }
    }

    @Test
    fun badgeWordsReadOnTheirOwnWash() {
        themes.forEach { (name, c) ->
            // SadoraBadge: wash colour, wash alpha, word colour.
            val badges = listOf(
                Triple("premium", c.secondary to (if (c.isDark) 0.24f else 0.14f), c.secondaryText),
                Triple("success", c.success to 0.16f, c.successText),
                Triple("warning", c.warning to 0.16f, c.warningText),
                Triple("danger", c.danger to 0.14f, c.dangerText),
            )
            // The community BadgeChip: tint() for the wash, textTint() for the word.
            val chipAlpha = if (c.isDark) 0.22f else 0.12f
            val chips = listOf(
                Triple("newcomer", c.success to chipAlpha, c.successText),
                Triple("early", c.warning to chipAlpha, c.warningText),
                Triple("writer", c.primary to chipAlpha, c.textAccent),
                Triple("helper", c.accentText to chipAlpha, c.accentText),
                Triple("loved", c.secondary to chipAlpha, c.secondaryText),
                Triple("veteran", c.textAccent to chipAlpha, c.textAccent),
            )
            // The error strip and the escalation box.
            val strips = listOf(Triple("error strip", c.danger to 0.12f, c.dangerText))
            (badges + chips + strips).forEach { (what, washSpec, word) ->
                val (tint, alpha) = washSpec
                listOf("bg" to c.bg, "surface" to c.surface).forEach { (bgName, bg) ->
                    assertAa(word, wash(tint, alpha, bg), "$name $what word on its wash over $bgName")
                }
            }
        }
    }

    @Test
    fun whiteHoldsUnderTheGlossOfAHeroButton() {
        // The label's first line starts halfway down the lit band, where the clay's white
        // has faded to half its top value; the label spans the middle three quarters of
        // the hero sweep.
        val behindLabel = ClayLitAlphaLight * HeroLabelGloss * 0.5f
        themes.forEach { (name, c) ->
            val (start, end) = c.heroColors
            listOf(0f, 0.25f, 0.5f, 0.75f).forEach { t ->
                val stop = lerp(start, end, t)
                assertAa(c.onPrimary, wash(Color.White, behindLabel, stop), "$name white on glossed hero at $t")
                // The Premium screen's feature chips: a dark wash on the hero.
                assertAa(c.onPrimary, wash(Color.Black, 0.15f, stop), "$name white on a Premium chip at $t")
            }
            // PillButton Primary and the filled RoundIconButton.
            val pill = c.heroColors.first()
            assertAa(c.onPrimary, pill, "$name white on the filled pill")
            assertAa(c.onPrimary, wash(Color.White, behindLabel, pill), "$name white on the glossed pill")
        }
    }

    @Test
    fun controlEdgesAndDataMarksClearThreeToOne() {
        themes.forEach { (name, c) ->
            c.grounds().forEach { (bgName, bg) ->
                assertNonText(c.lineStrong, bg, "$name lineStrong on $bgName")
                assertNonText(c.chartAccent, bg, "$name chartAccent on $bgName")
                assertNonText(c.chartSecondary, bg, "$name chartSecondary on $bgName")
                assertNonText(c.chartWarm, bg, "$name chartWarm on $bgName")
                listOf(
                    "period" to PhaseColors.periodMark(c.isDark),
                    "follicular" to PhaseColors.follicularMark(c.isDark),
                    "fertile" to PhaseColors.fertileMark(c.isDark),
                    "luteal" to PhaseColors.lutealMark(c.isDark),
                ).forEach { (phase, mark) -> assertNonText(mark, bg, "$name $phase mark on $bgName") }
            }
            // The outline button's border, now at full strength.
            assertNonText(c.primary, c.surface, "$name outline border on surface")
            // A progress bar or ring sits on a surface2 track.
            assertNonText(c.chartAccent, c.surface2, "$name chartAccent on its track")
        }
    }

    @Test
    fun calendarDayNumbersReadOnTheirFills() {
        assertAa(StagePalettes.warmInk, PhaseColors.period, "ink on period")
        assertAa(StagePalettes.warmInk, PhaseColors.fertile, "ink on fertile")
    }
}
