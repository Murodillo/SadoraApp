package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uz.sadora.server.auth.PhoneNumbers
import uz.sadora.server.core.ValidationException

class PhoneNumbersTest {

    @Test
    fun `every shape the app sends normalises to one number`() {
        val expected = "+998901234567"
        listOf(
            "901234567",
            "90 123 45 67",
            "90-123-45-67",
            "998901234567",
            "+998901234567",
            "+998 90 123 45 67",
            "(90) 123-45-67",
        ).forEach { input ->
            assertEquals(expected, PhoneNumbers.normalize(input), "failed for: $input")
        }
    }

    @Test
    fun `numbers of the wrong length are rejected`() {
        listOf("", "123", "9012345678901234", "12345678", "9012345678").forEach { input ->
            assertFailsWith<ValidationException>("accepted: $input") {
                PhoneNumbers.normalize(input)
            }
        }
    }

    /**
     * The first two digits are the one part a person cannot get nearly right. A number
     * behind an operator code that does not exist used to reach the SMS provider and
     * fail there, and all the user saw was a code that never arrived.
     */
    @Test
    fun `a code no operator uses is not a number`() {
        listOf("70 123 45 67", "10 123 45 67", "71 123 45 67").forEach { input ->
            assertFailsWith<ValidationException>("accepted: $input") {
                PhoneNumbers.normalize(input)
            }
        }
        // Every code the operators do use is accepted.
        uz.sadora.contract.UzbekPhone.OPERATOR_CODES.forEach { code ->
            assertEquals("+998${code}1234567", PhoneNumbers.normalize("${code}1234567"))
        }
    }

    /**
     * What the field does on each keystroke is not what the request check does: the
     * field truncates so a paste works, the check refuses, because sixteen digits with
     * nine plausible ones at the front would otherwise sign someone in as somebody else.
     */
    @Test
    fun `the field is forgiving where the request is strict`() {
        val phone = uz.sadora.contract.UzbekPhone
        assertEquals("901234567", phone.accept("+998 90 123 45 67"))
        assertEquals("901234567", phone.accept("9012345678901234"))
        assertEquals("90 123 45 67", phone.format("901234567"))
        assertEquals("90 123 4", phone.format("901234"))
        assertEquals("99", phone.accept("99"), "a real 99 code is not mistaken for the country")
        assertFailsWith<ValidationException> { PhoneNumbers.normalize("9012345678901234") }
    }

    @Test
    fun `masking keeps enough to recognise a number and not enough to dial it`() {
        val masked = PhoneNumbers.mask("+998901234567")
        assertEquals("+998 ** *** ** 67", masked)
        // Only the country code and the last two digits survive: the operator code and
        // the subscriber number would narrow a line down to a hundred candidates.
        assertEquals(listOf("998", "67"), Regex("\\d+").findAll(masked).map { it.value }.toList())
    }
}
