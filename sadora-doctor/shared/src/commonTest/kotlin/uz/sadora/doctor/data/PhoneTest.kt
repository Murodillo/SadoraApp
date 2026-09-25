package uz.sadora.doctor.data

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uz.sadora.doctor.i18n.StringsUz
import uz.sadora.doctor.ui.components.PhoneMask
import uz.sadora.doctor.ui.components.acceptPhone
import uz.sadora.doctor.ui.components.phoneError
import uz.sadora.doctor.ui.components.phoneIsComplete

/**
 * The phone field: what it keeps, what it draws, and what goes over the wire. The rules
 * are the contract's `UzbekPhone`, which the server checks with too.
 */
class PhoneTest {

    @Test
    fun `the field keeps nine national digits whatever is pasted`() {
        assertEquals("901234567", acceptPhone("+998 90 123-45-67"))
        assertEquals("901234567", acceptPhone("998901234567"))
        assertEquals("901234567", acceptPhone("90 123 45 67 89"))
        // The start of a real 99 operator code is not mistaken for the country code.
        assertEquals("99", acceptPhone("99"))
    }

    @Test
    fun `a number is complete only with a known operator code`() {
        assertTrue(phoneIsComplete("901234567"))
        assertTrue(phoneIsComplete("331234567"))
        assertFalse(phoneIsComplete("701234567"))
        assertFalse(phoneIsComplete("90123"))
    }

    @Test
    fun `the wire gets E164 and a broken number goes as its digits`() {
        assertEquals("+998901234567", normalizePhone("90 123 45 67"))
        assertEquals("+998901234567", normalizePhone("+998 (90) 123-45-67"))
        assertEquals("90123", normalizePhone("90 123"))
    }

    @Test
    fun `the mask draws the groups and keeps the caret on the digit`() {
        val drawn = PhoneMask.filter(AnnotatedString("901234567"))
        assertEquals("90 123 45 67", drawn.text.text)
        // After the fifth digit the caret sits after "90 123", not inside it.
        assertEquals(6, drawn.offsetMapping.originalToTransformed(5))
        assertEquals(12, drawn.offsetMapping.originalToTransformed(9))
        assertEquals(2, drawn.offsetMapping.transformedToOriginal(3))
        assertEquals(9, drawn.offsetMapping.transformedToOriginal(12))
        assertEquals("90 12", PhoneMask.filter(AnnotatedString("9012")).text.text)
    }

    @Test
    fun `the sign-in controller holds the digits and says when it may send`() {
        val auth = AuthController(repository = null)
        auth.updatePhone("+998 90 123 45 67")
        assertEquals("901234567", auth.phone)
        assertTrue(auth.phoneReady)
        auth.updatePhone("70 123 45 67")
        assertFalse(auth.phoneReady)
    }

    @Test
    fun `the error waits until the number could no longer become right`() {
        val t = StringsUz.errors
        assertNull(phoneError("", t))
        assertNull(phoneError("70", t))
        assertNull(phoneError("901234567", t))
        assertEquals(t.phoneInvalid, phoneError("701234567", t))
    }
}
