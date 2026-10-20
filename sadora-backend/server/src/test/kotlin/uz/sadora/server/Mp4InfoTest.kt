package uz.sadora.server

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uz.sadora.server.media.ByteSource
import uz.sadora.server.media.Mp4Info

/**
 * The fixtures are real files made with ffmpeg (see the commit that added them): a
 * faststart MP4 with sound, a MOV whose picture is stored sideways with a rotation
 * matrix — what a phone held upright records — and an MP4 with its index at the end.
 */
class Mp4InfoTest {

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/media/$name")) { "missing fixture $name" }.use { it.readBytes() }

    private fun read(bytes: ByteArray): Mp4Info? = Mp4Info.read(ByteSource.of(bytes))

    @Test
    fun `a landscape mp4 reads its length and its size`() {
        val info = assertNotNull(read(fixture("landscape.mp4")))
        assertTrue(info.durationMs in 2450..2550, "duration ${info.durationMs}")
        assertEquals(320, info.width)
        assertEquals(240, info.height)
    }

    @Test
    fun `a video stored sideways is as tall as it is shown`() {
        val info = assertNotNull(read(fixture("rotated.mov")))
        assertTrue(info.durationMs in 1150..1250, "duration ${info.durationMs}")
        assertEquals(240, info.width)
        assertEquals(320, info.height)
    }

    @Test
    fun `the index after the media is found too`() {
        val info = assertNotNull(read(fixture("tail_moov.mp4")))
        assertTrue(info.durationMs in 950..1050, "duration ${info.durationMs}")
        assertEquals(160, info.width)
        assertEquals(120, info.height)
    }

    @Test
    fun `the boxes that say where and what are found, and the video plays without them`() {
        val bytes = fixture("geotagged.mov")
        val info = assertNotNull(read(bytes))
        assertTrue(info.metadataBoxes.isNotEmpty(), "the phone's notes are in udta/meta")
        info.metadataBoxes.forEach { box ->
            assertTrue(String(bytes, box.start.toInt() + 4, 4, Charsets.ISO_8859_1) in setOf("udta", "meta"), "box at ${box.start}")
        }
        val blanked = bytes.copyOf()
        info.metadataBoxes.forEach { box -> Mp4Info.blank(box) { offset, value -> blanked[offset.toInt()] = value } }
        val text = String(blanked, Charsets.ISO_8859_1)
        assertEquals(-1, text.indexOf("+41.3111"), "where it was filmed is gone")
        assertEquals(-1, text.indexOf("SECRET-TITLE"))
        val after = assertNotNull(read(blanked), "still a video")
        assertEquals(info.durationMs to (info.width to info.height), after.durationMs to (after.width to after.height))
        assertTrue(after.metadataBoxes.isEmpty())
    }

    @Test
    fun `anything else is not a video`() {
        assertNull(read(Random(1).nextBytes(4096)))
        assertNull(read(ByteArray(0)))
        // A JPEG's first bytes.
        assertNull(read(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(200)))
        // A video cut before its index: the container starts right but says nothing.
        val whole = fixture("tail_moov.mp4")
        assertNull(read(whole.copyOf(whole.size / 2)))
    }
}
