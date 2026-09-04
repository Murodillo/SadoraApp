package uz.sadora.server

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uz.sadora.server.community.AliasGenerator

/**
 * The alias is the whole of a poster's identity in the room, so its shape is worth
 * pinning: two words, never a name, a suffix only once the plain forms run out.
 */
class AliasGeneratorTest {

    @Test
    fun `a plain alias is two capitalised words`() {
        repeat(200) { seed ->
            val alias = AliasGenerator.candidate(Random(seed), attempt = 0)
            val words = alias.split(' ')
            assertEquals(2, words.size, alias)
            assertTrue(words.all { it.first().isUpperCase() }, alias)
            assertTrue(words.none { it.any(Char::isDigit) }, alias)
        }
    }

    @Test
    fun `late attempts carry a numeric suffix so the search cannot circle forever`() {
        val alias = AliasGenerator.candidate(Random(7), attempt = 5)
        val parts = alias.split(' ')
        assertEquals(3, parts.size, alias)
        assertTrue(parts.last().toIntOrNull() in 10..99, alias)
    }

    @Test
    fun `the same seed gives the same alias, so a retry is reproducible`() {
        assertEquals(
            AliasGenerator.candidate(Random(42), attempt = 0),
            AliasGenerator.candidate(Random(42), attempt = 0),
        )
    }

    @Test
    fun `tints stay inside the palette the app draws`() {
        repeat(100) { seed ->
            assertTrue(AliasGenerator.tint(Random(seed)) in 0 until AliasGenerator.TINT_COUNT)
        }
    }

    @Test
    fun `there are enough plain combinations for a first cohort`() {
        assertTrue(AliasGenerator.plainCombinations >= 200)
    }
}
