package uz.sadora.app.ui.partner

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class PartnerCodeMaskTest {

    /** What a field does: each keystroke appended at the caret, then stored normalised. */
    private fun typeByHand(input: String): String =
        input.fold("") { stored, ch -> normaliseCode(stored + ch) }

    @Test
    fun `a code typed one character at a time keeps its order`() {
        assertEquals("6KGPX2EV", typeByHand("6kgp-x2ev"))
        assertEquals("6KGPX2EV", typeByHand("6KGPX2EV"))
    }

    @Test
    fun `the dash is only drawn — the caret maps across it`() {
        val shown = PartnerCodeMask.filter(AnnotatedString("6KGPX2EV"))
        assertEquals("6KGP-X2EV", shown.text.text)
        val map = shown.offsetMapping
        assertEquals(4, map.originalToTransformed(4))
        assertEquals(6, map.originalToTransformed(5))
        assertEquals(9, map.originalToTransformed(8))
        assertEquals(5, map.transformedToOriginal(6))
        assertEquals(8, map.transformedToOriginal(9))
        assertEquals("6KGP", PartnerCodeMask.filter(AnnotatedString("6KGP")).text.text)
        assertEquals(4, PartnerCodeMask.filter(AnnotatedString("6KGP")).offsetMapping.transformedToOriginal(5))
    }

    @Test
    fun `a pasted or linked code with its dash is read the same`() {
        assertEquals("6KGPX2EV", normaliseCode("6KGP-X2EV"))
        assertEquals("6KGPX2EV", normaliseCode(" 6kgp x2ev "))
    }
}
