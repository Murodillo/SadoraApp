package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import uz.sadora.server.admin.Totp

/**
 * Checked against the RFC 6238 appendix B vectors (HMAC-SHA1, secret
 * `12345678901234567890`). The published values are eight digits; a six-digit code is
 * their last six.
 */
class TotpTest {

    private val secret = Totp.decodeBase32(RFC_SECRET_BASE32)

    @Test
    fun `matches the RFC 6238 test vectors`() {
        val vectors = mapOf(
            59L to "287082",
            1111111109L to "081804",
            1111111111L to "050471",
            1234567890L to "005924",
            2000000000L to "279037",
            20000000000L to "353130",
        )
        vectors.forEach { (epochSeconds, expected) ->
            assertEquals(expected, Totp.generate(secret, epochSeconds / 30), "at t=$epochSeconds")
        }
    }

    @Test
    fun `base32 decoding round-trips the RFC secret`() {
        assertEquals("12345678901234567890", String(secret, Charsets.US_ASCII))
    }

    @Test
    fun `malformed codes are rejected without throwing`() {
        listOf("", "12345", "1234567", "abcdef", "12 34 56 78").forEach { code ->
            assertFalse(Totp.verify(RFC_SECRET_BASE32, code), "accepted: $code")
        }
    }

    @Test
    fun `a malformed secret fails closed`() {
        assertFalse(Totp.verify("not base32 at all!!", "123456"))
    }

    private companion object {
        const val RFC_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    }
}
