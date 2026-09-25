package uz.sadora.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

    @Test
    fun calendarDayNumbersReadOnTheirFills() {
        assertAa(StagePalettes.warmInk, PhaseColors.period, "ink on period")
        assertAa(StagePalettes.warmInk, PhaseColors.fertile, "ink on fertile")
    }
}
