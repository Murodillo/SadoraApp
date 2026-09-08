package uz.sadora.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two halves of a phone number rule, which are deliberately not the same.
 *
 * [UzbekPhone.accept] runs on every keystroke and truncates, so a pasted number works.
 * [UzbekPhone.parse] decides whether a request may be sent, and refuses rather than
 * truncating — nine plausible digits at the front of sixteen is somebody else's number.
 */
class UzbekPhoneTest {

    @Test
    fun `the field never holds more than nine digits`() {
        assertEquals("901234567", UzbekPhone.accept("9012345678901234"))
        assertEquals("901234567", UzbekPhone.accept("90 123 45 67 89"))
        assertEquals(
            UzbekPhone.NATIONAL_LENGTH,
            UzbekPhone.accept("1234567890123456789").length,
        )
    }

    @Test
    fun `a pasted number loses its country code but a real 99 code does not`() {
        assertEquals("901234567", UzbekPhone.accept("+998 90 123 45 67"))
        assertEquals("901234567", UzbekPhone.accept("998901234567"))
        // "99" is an operator code as well as the start of the country code. Someone
        // part-way through typing 99 8… must not have it eaten.
        assertEquals("99", UzbekPhone.accept("99"))
        assertEquals("998", UzbekPhone.accept("998"))
        assertEquals("998123456", UzbekPhone.accept("998123456"))
    }

    @Test
    fun `the mask groups the digits as they arrive`() {
        assertEquals("", UzbekPhone.format(""))
        assertEquals("9", UzbekPhone.format("9"))
        assertEquals("90", UzbekPhone.format("90"))
        assertEquals("90 1", UzbekPhone.format("901"))
        assertEquals("90 123", UzbekPhone.format("90123"))
        assertEquals("90 123 4", UzbekPhone.format("901234"))
        assertEquals("90 123 45", UzbekPhone.format("9012345"))
        assertEquals("90 123 45 67", UzbekPhone.format("901234567"))
        // Re-formatting what is already formatted must not move anything.
        assertEquals("90 123 45 67", UzbekPhone.format("90 123 45 67"))
    }

    @Test
    fun `a complete number behind a real operator code is valid`() {
        UzbekPhone.OPERATOR_CODES.forEach { code ->
            assertTrue(UzbekPhone.isValid("${code}1234567"), "rejected: $code")
            assertEquals("+998${code}1234567", UzbekPhone.toE164("${code}1234567"))
        }
    }

    @Test
    fun `anything else is not a number`() {
        listOf(
            "",                  // nothing
            "90123456",          // eight digits
            "9012345678",        // ten
            "9012345678901234",  // a paste that went wrong
            "70 123 45 67",      // no operator uses 70
            "71 123 45 67",      // a Tashkent landline; the app signs in by SMS
            "10 123 45 67",
        ).forEach { input ->
            assertFalse(UzbekPhone.isValid(input), "accepted: $input")
            assertNull(UzbekPhone.toE164(input))
        }
    }

    /** The error is held back while an answer could still become right. */
    @Test
    fun `a half-typed number is incomplete rather than wrong`() {
        assertTrue(UzbekPhone.isIncomplete("90"))
        assertTrue(UzbekPhone.isIncomplete("90 123 45 6"))
        assertFalse(UzbekPhone.isIncomplete("90 123 45 67"))
        // Complete but wrong: nine digits, no such operator. That one is worth saying.
        assertFalse(UzbekPhone.isIncomplete("70 123 45 67"))
        assertFalse(UzbekPhone.isValid("70 123 45 67"))
    }
}
