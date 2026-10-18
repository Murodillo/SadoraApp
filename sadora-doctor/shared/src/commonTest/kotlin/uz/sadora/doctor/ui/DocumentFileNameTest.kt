package uz.sadora.doctor.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import uz.sadora.doctor.ui.components.cacheFileName

class DocumentFileNameTest {
    @Test
    fun `a sent file name is written as one plain file in the cache`() {
        assertEquals("sadora-hisobot-6oy.pdf", cacheFileName("sadora-hisobot-6oy.pdf"))
        assertEquals("passwd", cacheFileName("../../etc/passwd"))
        assertEquals("a_b.pdf", cacheFileName("a:b.pdf"))
        assertEquals("Отчёт 2026.pdf", cacheFileName("Отчёт 2026.pdf"))
        assertEquals("report.pdf", cacheFileName("..."))
        assertEquals("report.pdf", cacheFileName(""))
    }
}
