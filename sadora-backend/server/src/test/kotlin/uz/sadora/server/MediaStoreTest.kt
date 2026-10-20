package uz.sadora.server

import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid
import uz.sadora.server.core.ConflictException
import uz.sadora.server.media.MediaStore
import uz.sadora.server.media.MediaVariant
import uz.sadora.server.media.SegmentedCipher

class MediaStoreTest {

    private val root: File = Files.createTempDirectory("sadora-media").toFile()
    private var at = Instant.parse("2026-10-20T08:00:00Z")
    private val store = MediaStore(root) { at }
    private val cipher = SegmentedCipher(SegmentedCipher.newKey())
    private val chunk = MediaStore.CHUNK_BYTES

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private val video = Random(7).nextBytes(2 * chunk + 70_000)

    private fun send(id: Uuid, bytes: ByteArray, from: Int = 0, until: Int = bytes.size) {
        var offset = from
        while (offset < until) {
            val end = minOf(offset + chunk, bytes.size)
            assertEquals(end.toLong(), store.append(id, cipher, offset.toLong(), bytes.copyOfRange(offset, end), bytes.size.toLong()))
            offset = end
        }
    }

    private fun readAll(id: Uuid, variant: MediaVariant, size: Int): ByteArray =
        store.open(id, variant, cipher, size.toLong(), 0L until size).use { it.readBytes() }

    @Test
    fun `pieces become the file, sealed on disk and whole when read`() {
        val id = Uuid.random()
        send(id, video)
        assertEquals(video.size.toLong(), store.received(id))
        store.promotePart(id)
        assertContentEquals(video, readAll(id, MediaVariant.FULL, video.size))
        assertEquals(0L, store.received(id), "the part is gone once it is the file")
        // Nothing on disk is the plaintext.
        val onDisk = root.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, onDisk.size)
        assertFalse(onDisk.single().readBytes().copyOf(64).contentEquals(video.copyOf(64)))
    }

    @Test
    fun `a piece sent again lands where it did, and the upload goes on from there`() {
        val id = Uuid.random()
        send(id, video, until = chunk)
        // Its answer was lost on the way back: the phone sends the same piece again.
        assertEquals(chunk.toLong(), store.append(id, cipher, 0, video.copyOfRange(0, chunk), video.size.toLong()))
        send(id, video, from = chunk)
        store.promotePart(id)
        assertContentEquals(video, readAll(id, MediaVariant.FULL, video.size))
    }

    @Test
    fun `what has arrived survives a restart`() {
        val id = Uuid.random()
        send(id, video, until = 2 * chunk)
        val restarted = MediaStore(root) { at }
        assertEquals(2L * chunk, restarted.received(id))
    }

    @Test
    fun `a piece that would leave a hole, start mid-segment, or overrun the size is refused`() {
        val id = Uuid.random()
        send(id, video, until = chunk)
        val size = video.size.toLong()
        assertFailsWith<ConflictException> { store.append(id, cipher, 2L * chunk, video.copyOfRange(0, chunk), size) }
        assertFailsWith<ConflictException> { store.append(id, cipher, 100, video.copyOfRange(0, chunk), size) }
        assertFailsWith<ConflictException> { store.append(id, cipher, chunk.toLong(), ByteArray(chunk + 1), size) }
        // A short piece is the last one only.
        assertFailsWith<ConflictException> { store.append(id, cipher, chunk.toLong(), ByteArray(1000), size) }
        assertFailsWith<ConflictException> { store.append(id, cipher, chunk.toLong(), ByteArray(0), size) }
        assertEquals(chunk.toLong(), store.received(id))
    }

    @Test
    fun `a photo's variants are written whole and deleted together`() {
        val id = Uuid.random()
        val full = Random(1).nextBytes(300_000)
        val thumb = Random(2).nextBytes(20_000)
        store.write(id, MediaVariant.FULL, cipher, full)
        store.write(id, MediaVariant.THUMB, cipher, thumb)
        assertContentEquals(full, readAll(id, MediaVariant.FULL, full.size))
        assertContentEquals(thumb, readAll(id, MediaVariant.THUMB, thumb.size))
        store.delete(id)
        assertTrue(root.walkTopDown().none { it.isFile })
    }

    @Test
    fun `the sweeper removes files nobody knows once they are old enough`() {
        val kept = Uuid.random()
        val orphan = Uuid.random()
        val fresh = Uuid.random()
        store.write(kept, MediaVariant.FULL, cipher, ByteArray(10) { 1 })
        store.write(orphan, MediaVariant.FULL, cipher, ByteArray(10) { 2 })
        store.write(orphan, MediaVariant.THUMB, cipher, ByteArray(10) { 3 })
        send(orphan, video, until = chunk)
        at += 3.hours
        // Written after the cut-off: an upload that has not reached the table yet is not an orphan.
        store.write(fresh, MediaVariant.FULL, cipher, ByteArray(10) { 4 })

        val removed = kotlinx.coroutines.runBlocking { store.sweep(olderThan = at - 1.hours) { ids -> ids.filterTo(HashSet()) { it == kept } } }

        assertEquals(1, removed)
        val names = root.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        assertTrue(names.any { it.startsWith(kept.toString()) })
        assertTrue(names.any { it.startsWith(fresh.toString()) })
        assertTrue(names.none { it.startsWith(orphan.toString()) })
    }
}
