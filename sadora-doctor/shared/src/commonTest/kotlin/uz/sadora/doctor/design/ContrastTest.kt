package uz.sadora.doctor.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG AA for the token pairs the doctor app actually draws with, in both themes. A
 * palette tweak that pushes one of them under its line fails here instead of on a
 * phone in the sun. Text needs 4.5:1; a control's border needs 3:1 (1.4.11).
 */
class ContrastTest {

    private val themes = listOf("light" to SadoraLightColors, "dark" to SadoraDarkColors)

    private fun SadoraColors.surfaces() = listOf("bg" to bg, "surface" to surface, "surface2" to surface2)

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertRatio(fg: Color, bg: Color, needed: Double, what: String) {
        val r = ratio(fg, bg)
        assertTrue(r >= needed, "$what is ${(r * 100).toInt() / 100.0}:1, needs $needed")
    }

    private fun assertAa(fg: Color, bg: Color, what: String) = assertRatio(fg, bg, 4.5, what)

    @Test
    fun textTokensClearAaOnEverySurface() {
        themes.forEach { (name, c) ->
            c.surfaces().forEach { (bgName, bg) ->
                assertAa(c.text, bg, "$name text on $bgName")
                assertAa(c.muted, bg, "$name muted on $bgName")
                assertAa(c.muted2, bg, "$name muted2 on $bgName")
                assertAa(c.textAccent, bg, "$name textAccent on $bgName")
                assertAa(c.dangerText, bg, "$name dangerText on $bgName")
                assertAa(c.successText, bg, "$name successText on $bgName")
                assertAa(c.warningText, bg, "$name warningText on $bgName")
            }
        }
    }

    @Test
    fun whiteReadsOnThePrimaryFill() {
        themes.forEach { (name, c) ->
            assertAa(c.onPrimary, c.primaryFill, "$name white on primaryFill")
        }
    }

    @Test
    fun whiteHoldsAcrossTheHeroGradient() {
        themes.forEach { (name, c) ->
            val (start, end) = c.heroColors
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { t ->
                assertAa(c.onPrimary, lerp(start, end, t), "$name white at $t of the hero gradient")
            }
        }
    }

    /** TintChip, ErrorStrip and the selected chip: text on a 12–22% wash of a colour. */
    @Test
    fun tintedChipsReadOnTheirOwnWash() {
        themes.forEach { (name, c) ->
            val selectedWash = if (c.isDark) 0.22f else 0.12f
            c.surfaces().forEach { (bgName, bg) ->
                listOf(
                    "dangerText" to c.dangerText,
                    "warningText" to c.warningText,
                    "successText" to c.successText,
                    "textAccent" to c.textAccent,
                ).forEach { (tintName, tint) ->
                    // TintChip: the text colour washed at 12% behind itself.
                    assertAa(tint, tint.copy(alpha = 0.12f).compositeOver(bg), "$name $tintName chip on $bgName")
                }
                // ErrorStrip: dangerText on the danger fill at 12%.
                assertAa(c.dangerText, c.danger.copy(alpha = 0.12f).compositeOver(bg), "$name error strip on $bgName")
                // An error toast: body text on the danger fill at 15%.
                assertAa(c.text, c.danger.copy(alpha = 0.15f).compositeOver(bg), "$name error toast on $bgName")
                // A selected chip: textAccent on its primary wash.
                assertAa(c.textAccent, c.primary.copy(alpha = selectedWash).compositeOver(bg), "$name selected chip on $bgName")
            }
        }
    }

    @Test
    fun controlBordersClearThreeToOne() {
        themes.forEach { (name, c) ->
            c.surfaces().forEach { (bgName, bg) ->
                assertRatio(c.lineStrong, bg, 3.0, "$name lineStrong on $bgName")
                assertRatio(c.textAccent, bg, 3.0, "$name outline button border on $bgName")
                assertRatio(c.dangerText, bg, 3.0, "$name destructive button border on $bgName")
            }
        }
    }
}
