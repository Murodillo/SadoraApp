package uz.sadora.doctor.ui.doctor

import kotlin.test.Test
import kotlin.test.assertEquals
import uz.sadora.contract.MediaKind
import uz.sadora.contract.MessageMedia
import uz.sadora.doctor.i18n.MediaStringsEn
import uz.sadora.doctor.i18n.MediaStringsRu
import uz.sadora.doctor.i18n.MediaStringsUz

/** The album's grid, and the words the list and the tiles say about photos and videos. */
class MessageMediaTest {

    @Test
    fun `an album lays out like a messenger's`() {
        assertEquals(emptyList(), albumRows(0))
        assertEquals(listOf(listOf(0)), albumRows(1))
        assertEquals(listOf(listOf(0, 1)), albumRows(2))
        assertEquals(listOf(listOf(0), listOf(1, 2)), albumRows(3))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), albumRows(4))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), albumRows(10), "the fourth tile carries +6")
    }

    @Test
    fun `lengths and sizes read the way a phone shows them`() {
        assertEquals("0:00", formatDuration(null))
        assertEquals("0:42", formatDuration(41_600))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("3:00", formatDuration(180_000))
        // The report card's sizes: the doctor app writes them in each language's own way.
        assertEquals("850 KB", MediaStringsUz.size(850L * 1024))
        assertEquals("18 MB", MediaStringsEn.size(18L * 1024 * 1024))
        assertEquals("18,3 МБ", MediaStringsRu.size(18L * 1024 * 1024 + 300L * 1024))
        assertEquals("100 MB", MediaStringsEn.size(100L * 1024 * 1024))
    }

    @Test
    fun `the consultation list says what the last line carried`() {
        val photo = MessageMedia("p", MediaKind.PHOTO, 1, 1, sizeBytes = 1)
        val video = MessageMedia("v", MediaKind.VIDEO, 1, 1, durationMs = 42_000, sizeBytes = 1)
        val t = MediaStringsUz
        assertEquals("Rasm", mediaLastLine(t, photo, 1, 0, ""))
        assertEquals("Video · 0:42", mediaLastLine(t, video, 1, 1, ""))
        assertEquals("3 ta rasm · natija", mediaLastLine(t, photo, 3, 0, "natija"))
        assertEquals("2 ta video", mediaLastLine(t, video, 2, 2, ""))
        assertEquals("2 ta rasm, 1 ta video", mediaLastLine(t, photo, 3, 1, ""))
        assertEquals("1 photo, 1 video", mediaLastLine(MediaStringsEn, photo, 2, 1, " "))
    }
}
