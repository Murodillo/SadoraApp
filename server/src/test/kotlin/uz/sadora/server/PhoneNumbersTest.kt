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
        listOf("", "123", "9012345678901234", "12345678").forEach { input ->
            assertFailsWith<ValidationException>("accepted: $input") {
                PhoneNumbers.normalize(input)
            }
        }
    }

    @Test
    fun `masking keeps enough to recognise a number and not enough to dial it`() {
        val masked = PhoneNumbers.mask("+998901234567")
        assertEquals("+99890123**67", masked)
    }
}
