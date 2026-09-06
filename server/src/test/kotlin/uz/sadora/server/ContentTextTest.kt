package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.sadora.contract.ArticleBlock
import uz.sadora.server.content.estimateReadMinutes
import uz.sadora.server.content.preview

/**
 * The two decisions the library makes about a body: how long it says the piece takes to
 * read, and how much of it a locked article gives away.
 */
class ContentTextTest {

    private fun words(count: Int) = ArticleBlock.Paragraph(List(count) { "so'z" }.joinToString(" "))

    @Test
    fun `an empty article still takes a minute, because zero is not a length`() {
        assertEquals(1, estimateReadMinutes(emptyList()))
        assertEquals(1, estimateReadMinutes(listOf(ArticleBlock.Paragraph("Bir jumla."))))
    }

    @Test
    fun `every block type counts towards the estimate, bullets included`() {
        val blocks = listOf(
            ArticleBlock.Heading(List(20) { "sarlavha" }.joinToString(" ")),
            words(100),
            ArticleBlock.Bullets(List(4) { List(25) { "band" }.joinToString(" ") }),
            ArticleBlock.Note(List(20) { "eslatma" }.joinToString(" ")),
        )
        // 20 + 100 + 100 + 20 = 240 words, which rounds up to two minutes at 140/min.
        assertEquals(2, estimateReadMinutes(blocks))
    }

    @Test
    fun `a part-minute rounds up, so a card never promises less than the read`() {
        assertEquals(2, estimateReadMinutes(listOf(words(141))))
        assertEquals(1, estimateReadMinutes(listOf(words(140))))
    }

    /** The paywall shows what she would be buying, not the whole thing and not nothing. */
    @Test
    fun `a locked article gives away its first paragraph and nothing after it`() {
        val blocks = listOf(
            ArticleBlock.Paragraph("Birinchi xatboshi."),
            ArticleBlock.Heading("Ichkarida"),
            ArticleBlock.Paragraph("Ikkinchi xatboshi."),
        )
        val preview = blocks.preview()
        assertEquals(listOf(ArticleBlock.Paragraph("Birinchi xatboshi.")), preview)
    }

    @Test
    fun `a body that opens with a heading still previews a paragraph rather than the heading`() {
        val blocks = listOf(
            ArticleBlock.Heading("Sarlavha"),
            ArticleBlock.Paragraph("Matn."),
        )
        assertEquals(listOf(ArticleBlock.Paragraph("Matn.")), blocks.preview())
    }

    @Test
    fun `a body with no paragraph at all previews nothing rather than leaking a list`() {
        val blocks = listOf(ArticleBlock.Bullets(listOf("bir", "ikki")))
        assertTrue(blocks.preview().isEmpty())
    }
}
