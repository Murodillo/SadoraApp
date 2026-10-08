package uz.sadora.app.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.sadora.app.ui.components.chatMarkdown

class ChatMarkdownTest {
    @Test
    fun boldAndListLoseTheirMarks() {
        val out = chatMarkdown("* **Suv balansi:** ko'proq iching\n- yengil sayr")
        assertEquals("• Suv balansi: ko'proq iching\n• yengil sayr", out.text)
        val bold = out.spanStyles.single { it.item.fontWeight == FontWeight.SemiBold }
        assertEquals("Suv balansi:", out.text.substring(bold.start, bold.end))
    }

    @Test
    fun italicHeadingAndCode() {
        val out = chatMarkdown("## Uyqu\n*sekin* nafas `4-7-8`")
        assertEquals("Uyqu\nsekin nafas 4-7-8", out.text)
        assertTrue(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun aLoneStarStaysAStar() {
        assertEquals("5 * 3 = 15, *ochiq qoldi", chatMarkdown("5 * 3 = 15, *ochiq qoldi").text)
    }

    @Test
    fun blankRunsCollapse() {
        assertEquals("bir\n\nikki", chatMarkdown("bir\n\n\n\nikki\n").text)
    }
}
