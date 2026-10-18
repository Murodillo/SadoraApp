package uz.sadora.doctor.ui.doctor

import kotlin.test.Test
import kotlin.test.assertEquals
import uz.sadora.contract.PainArea
import uz.sadora.contract.PainKind

/** The pain diary's "when" and "where" lists on her record: most first, zeros dropped, ties stable. */
class RankedCountsTest {

    @Test
    fun `the most frequent comes first and a zero is left out`() {
        val ranked = rankedCounts(mapOf(PainKind.BOWEL to 3, PainKind.PERIOD to 11, PainKind.CONSTANT to 0, PainKind.OVULATION to 5))
        assertEquals(listOf(PainKind.PERIOD to 11, PainKind.OVULATION to 5, PainKind.BOWEL to 3), ranked)
    }

    @Test
    fun `a tie keeps the body map's order`() {
        val ranked = rankedCounts(mapOf(PainArea.LOWER_BACK to 4, PainArea.LOWER_ABDOMEN to 4, PainArea.RECTUM to 1))
        assertEquals(listOf(PainArea.LOWER_ABDOMEN, PainArea.LOWER_BACK, PainArea.RECTUM), ranked.map { it.first })
    }

    @Test
    fun `nothing logged is an empty list`() {
        assertEquals(emptyList(), rankedCounts(emptyMap<PainArea, Int>()))
    }
}
