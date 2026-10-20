package uz.sadora.server

import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uz.sadora.server.media.SealedFile
import uz.sadora.server.media.SegmentedCipher

class SegmentedCipherTest {

    private val dir: File = Files.createTempDirectory("sadora-sealed").toFile()
    private val key = ByteArray(32) { (it * 7).toByte() }
    private val cipher = SegmentedCipher(key)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** Three whole segments and a short fourth: every boundary case in one file. */
    private val plain = Random(42).nextBytes(3 * SegmentedCipher.SEGMENT_BYTES + 1234)

    private fun sealed(): File = File(dir, "a.sealed").also { file ->
        file.outputStream().use { out -> SealedFile.seal(cipher, plain.inputStream(), out) }
    }

    @Test
    fun `a sealed file reads back whole`() {
        val file = sealed()
        val read = SealedFile(file, cipher, plain.size.toLong()).open(0L until plain.size).use { it.readBytes() }
        assertContentEquals(plain, read)
        assertEquals(SegmentedCipher.sealedSize(plain.size.toLong()), file.length())
    }

    @Test
    fun `a range across segment boundaries reads exactly those bytes`() {
        val file = SealedFile(sealed(), cipher, plain.size.toLong())
        val segment = SegmentedCipher.SEGMENT_BYTES
        listOf(
            0L..0L,
            (segment - 10L)..(segment + 9L),
            (segment - 1L)..(2L * segment + 5),
            (3L * segment)..(plain.size - 1L),
            (plain.size - 1L)..(plain.size - 1L),
        ).forEach { range ->
            val read = file.open(range).use { it.readBytes() }
            assertContentEquals(plain.copyOfRange(range.first.toInt(), range.last.toInt() + 1), read, "range $range")
        }
    }

    @Test
    fun `another key or a changed byte refuses instead of returning garbage`() {
        val file = sealed()
        val other = SegmentedCipher(ByteArray(32) { 1 })
        assertFailsWith<Exception> { SealedFile(file, other, plain.size.toLong()).open(0L..10L).use { it.readBytes() } }

        val bytes = file.readBytes()
        bytes[SegmentedCipher.sealedOffset(1).toInt() + 40] = (bytes[SegmentedCipher.sealedOffset(1).toInt() + 40] + 1).toByte()
        file.writeBytes(bytes)
        val tampered = SealedFile(file, cipher, plain.size.toLong())
        // The first segment is untouched and still reads; the changed one does not.
        assertContentEquals(plain.copyOfRange(0, 100), tampered.open(0L..99L).use { it.readBytes() })
        assertFailsWith<Exception> { tampered.open(SegmentedCipher.SEGMENT_BYTES.toLong()..SegmentedCipher.SEGMENT_BYTES + 10L).use { it.readBytes() } }
    }

    @Test
    fun `a file cut short fails at the missing segment`() {
        val file = sealed()
        file.writeBytes(file.readBytes().copyOf(SegmentedCipher.sealedOffset(2).toInt()))
        assertFailsWith<Exception> { SealedFile(file, cipher, plain.size.toLong()).open(0L until plain.size).use { it.readBytes() } }
    }

    @Test
    fun `the plaintext is nowhere in the file on disk`() {
        val marker = "ftypmp42-SECRET-MARKER".toByteArray()
        val content = ByteArray(5000).also { marker.copyInto(it, 100) }
        val file = File(dir, "b.sealed")
        file.outputStream().use { SealedFile.seal(cipher, content.inputStream(), it) }
        val onDisk = String(file.readBytes(), Charsets.ISO_8859_1)
        assertEquals(-1, onDisk.indexOf(String(marker, Charsets.ISO_8859_1)))
    }
}
