package uz.sadora.doctor.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import uz.sadora.contract.LabValue

class LabsTest {
    private fun row(
        value: Double? = null,
        valueText: String? = null,
        unit: String? = null,
        low: Double? = null,
        high: Double? = null,
        refText: String? = null,
    ) = LabValue(id = "v1", name = "Gemoglobin", value = value, valueText = valueText, unit = unit, refLow = low, refHigh = high, refText = refText)

    @Test
    fun `numbers lose a trailing zero and keep what was printed`() {
        assertEquals("104", labNumber(104.0))
        assertEquals("5.6", labNumber(5.6))
        assertEquals("0.025", labNumber(0.025))
        assertEquals("-1.5", labNumber(-1.5))
        assertEquals("0", labNumber(-0.0001))
        assertEquals("12000", labNumber(12_000.0))
    }

    @Test
    fun `a result is the number or the printed text, with its unit`() {
        assertEquals("104 g/L", labResult(row(value = 104.0, unit = "g/L")))
        assertEquals("manfiy", labResult(row(valueText = "manfiy")))
        assertEquals("1:160", labResult(row(valueText = " 1:160 ", unit = " ")))
        assertNull(labResult(row(unit = "g/L")))
    }

    @Test
    fun `a range reads as the laboratory printed it`() {
        assertEquals("120–150", labRange(row(low = 120.0, high = 150.0)))
        assertEquals("< 5", labRange(row(high = 5.0)))
        assertEquals("> 30", labRange(row(low = 30.0)))
        assertEquals("manfiy", labRange(row(refText = "manfiy")))
        assertNull(labRange(row()))
    }

    @Test
    fun `each route keeps its pages apart`() {
        val message = LabPhotos.InMessage("c1", "m1")
        val record = LabPhotos.InRecord("c1", "m1", "r1")
        val share = LabPhotos.InShare("tok", "r1")
        assertNotEquals(message.key(0), record.key(0))
        assertNotEquals(record.key(0), share.key(0))
        assertNotEquals(share.key(0), share.key(1))
    }
}
