package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.isValidTimeZone
import uz.sadora.server.core.resolveTimeZone

/**
 * Usage limits reset at the user's midnight, not the server's. Tashkent is UTC+5, so an
 * instant late in the UTC evening already belongs to the next Tashkent day — get this
 * wrong and a user's AI allowance resets five hours late every day.
 */
class UsageDayTest {

    @Test
    fun `the usage day follows the users timezone, not UTC`() {
        // 2026-08-26T20:30:00Z is 2026-08-27 01:30 in Tashkent.
        val instant = Instant.parse("2026-08-26T20:30:00Z")
        assertEquals("2026-08-26", instant.dayIn("UTC").toString())
        assertEquals("2026-08-27", instant.dayIn("Asia/Tashkent").toString())
        assertEquals("2026-08-26", instant.dayIn("Europe/Moscow").toString())
    }

    @Test
    fun `an unknown timezone falls back to Tashkent rather than failing`() {
        val instant = Instant.parse("2026-08-26T20:30:00Z")
        assertEquals(instant.dayIn(DEFAULT_TIMEZONE), instant.dayIn("Mars/Olympus_Mons"))
        assertEquals(DEFAULT_TIMEZONE, resolveTimeZone("nonsense").id)
    }

    @Test
    fun `timezone validation accepts real zones and rejects invented ones`() {
        assertTrue(isValidTimeZone("Asia/Tashkent"))
        assertTrue(isValidTimeZone("UTC"))
        assertTrue(!isValidTimeZone("Asia/Tashkentt"))
        assertTrue(!isValidTimeZone(""))
    }
}
