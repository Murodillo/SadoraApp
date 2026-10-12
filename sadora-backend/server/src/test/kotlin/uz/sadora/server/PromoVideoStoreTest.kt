package uz.sadora.server

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.promo.PromoVideoStore

class PromoVideoStoreTest {

    private val media: File = Files.createTempDirectory("sadora-media").toFile()
    private val videoDir = File(media, "video")
    private var at = Instant.parse("2026-10-13T08:00:00Z")
    private val store = PromoVideoStore(media) { at }

    @AfterTest
    fun cleanUp() {
        media.deleteRecursively()
    }

    /** An MP4's first box, then filler up to [size]. */
    private fun mp4(size: Int, fill: Byte = 7): ByteArray =
        ByteArray(size) { fill }.also { "\u0000\u0000\u0000\u0018ftypmp42".toByteArray(Charsets.ISO_8859_1).copyInto(it) }

    private suspend fun upload(bytes: ByteArray, name: String = "reklama.MP4", piece: Int = 1000): String {
        val id = store.start(name, bytes.size.toLong())
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + piece, bytes.size)
            assertEquals(end.toLong(), store.append(id, offset.toLong(), bytes.copyOfRange(offset, end)))
            offset = end
        }
        return id
    }

    @Test
    fun `pieces become the video and the manifest names it`() = runTest {
        assertNull(store.current())
        val bytes = mp4(2500)
        val manifest = store.complete(upload(bytes))

        assertEquals("video/mp4", manifest.mimeType)
        assertEquals(2500, manifest.sizeBytes)
        assertEquals(manifest, store.current())
        assertTrue(File(videoDir, manifest.file).readBytes().contentEquals(bytes))
        assertEquals(setOf(manifest.file, "current.json"), videoDir.list()!!.toSet())
        assertTrue(File(videoDir, "current.json").readText().contains(manifest.file))
    }

    @Test
    fun `a new video replaces the old one under a new name`() = runTest {
        val first = store.complete(upload(mp4(1200)))
        at += 5.seconds
        val second = store.complete(upload(mp4(1800, fill = 9)))

        assertNotEquals(first.file, second.file)
        assertEquals(second, store.current())
        assertEquals(setOf(second.file, "current.json"), videoDir.list()!!.toSet())
    }

    @Test
    fun `a piece sent twice lands once and a piece from further on is refused`() = runTest {
        val bytes = mp4(3000)
        val id = store.start("a.mp4", 3000)
        store.append(id, 0, bytes.copyOfRange(0, 1000))
        store.append(id, 1000, bytes.copyOfRange(1000, 2000))
        assertEquals(2000, store.append(id, 1000, bytes.copyOfRange(1000, 2000)))
        assertFailsWith<ConflictException> { store.append(id, 2500, bytes.copyOfRange(2500, 3000)) }
        assertFailsWith<ConflictException> { store.complete(id) }
        store.append(id, 2000, bytes.copyOfRange(2000, 3000))
        assertFailsWith<ConflictException> { store.append(id, 3000, byteArrayOf(1)) }

        val manifest = store.complete(id)
        assertTrue(File(videoDir, manifest.file).readBytes().contentEquals(bytes))
    }

    @Test
    fun `what is not a video is refused and the video in place stays`() = runTest {
        val kept = store.complete(upload(mp4(1500)))

        assertFailsWith<ValidationException> { store.start("clip.mov", 10) }
        assertFailsWith<ValidationException> { store.start("clip.mp4", 0) }
        assertFailsWith<ValidationException> { store.start("clip.mp4", PromoVideoStore.MAX_BYTES + 1) }
        // A text file with a video's name.
        val fake = upload(ByteArray(2000) { 'a'.code.toByte() }, name = "virus.mp4")
        assertFailsWith<ValidationException> { store.complete(fake) }

        assertEquals(kept, store.current())
        assertEquals(setOf(kept.file, "current.json"), videoDir.list()!!.toSet())
    }

    @Test
    fun `starting again drops the unfinished upload`() = runTest {
        val abandoned = store.start("a.mp4", 2000)
        store.append(abandoned, 0, mp4(1000))
        store.start("b.webm", 1000)

        assertFailsWith<NotFoundException> { store.append(abandoned, 1000, ByteArray(1000)) }
        assertTrue(videoDir.list()!!.isEmpty())
    }

    @Test
    fun `removing leaves nothing, and a manifest pointing nowhere is no video`() = runTest {
        store.complete(upload(mp4(1500)))
        assertTrue(store.remove())
        assertNull(store.current())
        assertTrue(videoDir.list()!!.isEmpty())
        assertTrue(!store.remove())

        File(videoDir, "current.json").writeText("""{"file":"../../etc/passwd","mimeType":"video/mp4","sizeBytes":1,"updatedAt":"2026-10-13T08:00:00Z"}""")
        assertNull(store.current())
    }
}
