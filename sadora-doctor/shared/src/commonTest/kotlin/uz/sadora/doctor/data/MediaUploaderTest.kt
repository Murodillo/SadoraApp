package uz.sadora.doctor.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.Ack
import uz.sadora.contract.MediaKind
import uz.sadora.contract.MediaUpload
import uz.sadora.contract.MessageMedia
import uz.sadora.contract.StartMediaUploadRequest

/**
 * The album on its way up, against a fake server. What is pinned: each file goes up in
 * the server's pieces and is finished before the message names it; a dropped connection
 * leaves the album failed and a retry carries on from what the server already has; a
 * message that failed after its files went up is sent again without sending them again;
 * and a cancelled album takes its unsent uploads off the server and frees its files.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaUploaderTest {

    private class FakeServer : MediaTransport {
        val chunk = 4
        val uploads = linkedMapOf<String, ByteArray>()
        val received = mutableMapOf<String, Long>()
        val puts = mutableListOf<Pair<String, Long>>()
        val cancelled = mutableListOf<String>()
        /** Fails the n-th piece sent (1-based), once. */
        var failPut: Int? = null
        var failComplete = false

        override suspend fun start(request: StartMediaUploadRequest): ApiResult<MediaUpload> {
            val id = "u${uploads.size + 1}"
            uploads[id] = ByteArray(request.sizeBytes.toInt())
            received[id] = 0
            return ApiResult.Success(MediaUpload(id, request.kind, request.sizeBytes, 0, chunk))
        }

        override suspend fun progress(uploadId: String): ApiResult<MediaUpload> {
            val size = uploads[uploadId]?.size ?: return ApiResult.Failure(ApiFailure.Unexpected("gone"))
            return ApiResult.Success(MediaUpload(uploadId, MediaKind.PHOTO, size.toLong(), received.getValue(uploadId), chunk))
        }

        override suspend fun put(uploadId: String, offset: Long, bytes: ByteArray): ApiResult<MediaUpload> {
            puts += uploadId to offset
            if (failPut == puts.size) {
                failPut = null
                return ApiResult.Failure(ApiFailure.Network("dropped"))
            }
            val file = uploads.getValue(uploadId)
            bytes.copyInto(file, offset.toInt())
            received[uploadId] = offset + bytes.size
            return ApiResult.Success(MediaUpload(uploadId, MediaKind.PHOTO, file.size.toLong(), offset + bytes.size, chunk))
        }

        override suspend fun complete(uploadId: String, posterBase64: String?): ApiResult<MessageMedia> {
            if (failComplete) return ApiResult.Failure(ApiFailure.Validation("Bu fayl video emas yoki buzilgan", emptyMap()))
            val file = uploads.getValue(uploadId)
            return ApiResult.Success(MessageMedia("m-$uploadId", MediaKind.PHOTO, 10, 10, sizeBytes = file.size.toLong()))
        }

        override suspend fun cancel(uploadId: String): ApiResult<Ack> {
            cancelled += uploadId
            return ApiResult.Success(Ack())
        }
    }

    private class FakePick(
        val bytes: ByteArray,
        override val kind: MediaKind = MediaKind.PHOTO,
        val source: String = "pick",
        var failPrepare: Boolean = false,
    ) : PickedMedia {
        var released = false
        var prepared = 0
        override val durationMs: Long? = null
        override suspend fun preview(): ByteArray? = null
        override suspend fun keep(): PickRef = PickRef(kind, source, durationMs)
        override suspend fun prepare(onProgress: (Float) -> Unit): PreparedMedia {
            if (failPrepare) throw MediaPrepareException(PrepareProblem.Unreadable)
            prepared++
            onProgress(1f)
            return PreparedMedia(
                kind = kind,
                mimeType = "image/jpeg",
                bytes = MediaBytes.of(bytes),
                width = 10,
                height = 10,
                durationMs = null,
                poster = null,
            )
        }
        override fun discard() {
            released = true
        }
    }

    /** The app's own files, in memory: what survives a restart in these tests. */
    private class FakeFiles : OutgoingFiles {
        var manifest: String? = null
        val files = mutableMapOf<String, ByteArray>()
        /** What the platform finds again behind a pick's reference after a restart. */
        val picks = mutableMapOf<String, FakePick>()
        private var next = 0

        override suspend fun readManifest(): String? = manifest
        override suspend fun writeManifest(text: String?) {
            manifest = text
        }
        override suspend fun write(bytes: ByteArray): String = "file-${++next}".also { files[it] = bytes }
        override fun open(path: String): MediaBytes? = files[path]?.let { bytes ->
            object : MediaBytes {
                override val size = bytes.size.toLong()
                override suspend fun read(offset: Long, length: Int) =
                    bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + length).toInt())
                override fun release() {
                    files.remove(path)
                }
            }
        }
        override fun delete(path: String) {
            files.remove(path)
        }
        override fun restore(ref: PickRef): PickedMedia? = picks[ref.source]
        override suspend fun sweep(keep: Set<String>) {
            files.keys.retainAll(keep)
        }
    }

    private class Sent(val conversationId: String, val caption: String, val mediaIds: List<String>)

    private class FixedClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private fun TestScope.uploader(
        server: FakeServer,
        sent: MutableList<Sent>,
        failSends: Int = 0,
        files: OutgoingFiles? = null,
        clock: Clock = FixedClock(Instant.parse("2026-10-20T10:00:00Z")),
    ): MediaUploader {
        var failures = failSends
        // The test scope itself, not backgroundScope: advanceUntilIdle runs only foreground work.
        return MediaUploader(server, this, files, clock) { album, ids ->
            if (failures > 0) {
                failures--
                ApiResult.Failure(ApiFailure.Network("offline"))
            } else {
                sent += Sent(album.conversationId, album.caption, ids)
                ApiResult.Success(Unit)
            }
        }
    }

    @Test
    fun `an album goes up in pieces and the message names the files in order`() = runTest {
        val server = FakeServer()
        val sent = mutableListOf<Sent>()
        val first = FakePick(ByteArray(10) { it.toByte() })
        val second = FakePick(ByteArray(3) { 9 })
        val uploader = uploader(server, sent)

        uploader.send("c1", " Natija ", listOf(first, second))
        advanceUntilIdle()

        assertEquals(listOf("u1" to 0L, "u1" to 4L, "u1" to 8L, "u2" to 0L), server.puts)
        assertTrue(server.uploads.getValue("u1").contentEquals(first.bytes))
        val message = sent.single()
        assertEquals("c1", message.conversationId)
        assertEquals("Natija", message.caption)
        assertEquals(listOf("m-u1", "m-u2"), message.mediaIds)
        assertTrue(uploader.outgoing.isEmpty(), "a sent album leaves the queue")
        assertTrue(first.released && second.released)
    }

    @Test
    fun `a dropped connection fails the album and a retry carries on from what arrived`() = runTest {
        val server = FakeServer().apply { failPut = 2 }
        val sent = mutableListOf<Sent>()
        val pick = FakePick(ByteArray(10) { it.toByte() })
        val uploader = uploader(server, sent)

        uploader.send("c1", "", listOf(pick))
        advanceUntilIdle()
        val album = uploader.outgoing.single()
        assertIs<OutgoingState.Failed>(album.state)
        assertIs<ApiFailure.Network>((album.state as OutgoingState.Failed).failure)
        assertTrue(sent.isEmpty())

        uploader.retry(album.localId)
        advanceUntilIdle()

        // The first piece is not sent again: the retry asked where the upload stood.
        assertEquals(listOf("u1" to 0L, "u1" to 4L, "u1" to 4L, "u1" to 8L), server.puts)
        assertEquals(1, server.uploads.size, "the same upload, not a new one")
        assertEquals(1, pick.prepared, "the file is not prepared twice")
        assertEquals(listOf("m-u1"), sent.single().mediaIds)
        assertTrue(uploader.outgoing.isEmpty())
    }

    @Test
    fun `a message that failed after its files went up is sent again without them`() = runTest {
        val server = FakeServer()
        val sent = mutableListOf<Sent>()
        val uploader = uploader(server, sent, failSends = 1)

        uploader.send("c1", "", listOf(FakePick(ByteArray(5))))
        advanceUntilIdle()
        val album = uploader.outgoing.single()
        assertIs<OutgoingState.Failed>(album.state)
        val putsBefore = server.puts.size

        uploader.retry(album.localId)
        advanceUntilIdle()

        assertEquals(putsBefore, server.puts.size)
        assertEquals(listOf("m-u1"), sent.single().mediaIds)
    }

    @Test
    fun `a refusal is kept with the album until she retries or drops it`() = runTest {
        val server = FakeServer().apply { failComplete = true }
        val sent = mutableListOf<Sent>()
        val uploader = uploader(server, sent)

        uploader.send("c1", "", listOf(FakePick(ByteArray(5))))
        advanceUntilIdle()
        val failed = uploader.outgoing.single().state as OutgoingState.Failed
        assertIs<ApiFailure.Validation>(failed.failure)
    }

    @Test
    fun `a cancelled album takes its uploads off the server and frees its files`() = runTest {
        val server = FakeServer().apply { failPut = 3 }
        val sent = mutableListOf<Sent>()
        val first = FakePick(ByteArray(3))
        val second = FakePick(ByteArray(9))
        val uploader = uploader(server, sent)

        uploader.send("c1", "", listOf(first, second))
        advanceUntilIdle()
        val album = uploader.outgoing.single()

        uploader.cancel(album.localId)
        advanceUntilIdle()

        assertEquals(listOf("u1", "u2"), server.cancelled)
        assertTrue(uploader.outgoing.isEmpty())
        assertTrue(first.released && second.released)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `an album cut off by a restart comes back unsent — and a retry goes on from what the server has`() = runTest {
        val server = FakeServer().apply { failPut = 2 }
        val sent = mutableListOf<Sent>()
        val files = FakeFiles()
        val bytes = ByteArray(10) { it.toByte() }
        val pick = FakePick(bytes, source = "gallery-1")
        val before = uploader(server, sent, files = files).also { it.restore(owner = "her") }

        before.send("c1", "Natija", listOf(pick))
        advanceUntilIdle()
        assertIs<OutgoingState.Failed>(before.outgoing.single().state)

        // The app is closed and opened again: a new uploader finds the album on disk.
        files.picks["gallery-1"] = FakePick(bytes, source = "gallery-1")
        val after = uploader(server, sent, files = files)
        after.restore(owner = "her")
        val album = after.outgoing.single()
        assertEquals("c1" to "Natija", album.conversationId to album.caption)
        assertEquals(OutgoingState.Failed(), album.state, "unsent, waiting for her to send it again")

        after.retry(album.localId)
        advanceUntilIdle()

        assertEquals(listOf("u1" to 0L, "u1" to 4L, "u1" to 4L, "u1" to 8L), server.puts, "carried on, not started again")
        assertEquals(1, server.uploads.size)
        assertEquals(0, files.picks.getValue("gallery-1").prepared, "the prepared file was kept, so nothing is prepared twice")
        assertEquals(listOf("m-u1"), sent.single().mediaIds)
        assertTrue(after.outgoing.isEmpty())
        assertTrue(files.files.isEmpty(), "its files went once it was sent")
        assertTrue(files.manifest == null || !files.manifest!!.contains("Natija"))
    }

    @Test
    fun `what a closed app left half-written is swept when it opens again`() = runTest {
        val server = FakeServer().apply { failPut = 1 }
        val files = FakeFiles()
        val before = uploader(server, mutableListOf(), files = files).also { it.restore(owner = "her") }
        before.send("c1", "", listOf(FakePick(ByteArray(5), source = "a")))
        advanceUntilIdle()
        val kept = files.files.keys.toSet()
        // A video the app was compressing when it was closed: on disk, named nowhere.
        files.files["half-a-video.mp4"] = ByteArray(3)

        uploader(server, mutableListOf(), files = files).restore(owner = "her")

        assertEquals(kept, files.files.keys, "the album's own file stays, the half-written one goes")
    }

    @Test
    fun `an album that never got prepared is prepared again from what she picked`() = runTest {
        val server = FakeServer()
        val sent = mutableListOf<Sent>()
        val files = FakeFiles()
        val before = uploader(server, sent, files = files).also { it.restore(owner = "her") }
        before.send("c1", "", listOf(FakePick(ByteArray(6), source = "camera-1", failPrepare = true)))
        advanceUntilIdle()

        val again = FakePick(ByteArray(6) { 5 }, source = "camera-1")
        files.picks["camera-1"] = again
        val after = uploader(server, sent, files = files).also { it.restore(owner = "her") }
        after.retry(after.outgoing.single().localId)
        advanceUntilIdle()

        assertEquals(1, again.prepared)
        assertEquals(1, sent.size)
        assertTrue(again.released)
    }

    @Test
    fun `another account — a day gone by — or signing out leaves nothing behind`() = runTest {
        val server = FakeServer().apply { failPut = 1 }
        val sent = mutableListOf<Sent>()
        val files = FakeFiles()
        val clock = FixedClock(Instant.parse("2026-10-20T10:00:00Z"))
        val first = uploader(server, sent, files = files, clock = clock).also { it.restore(owner = "her") }
        first.send("c1", "", listOf(FakePick(ByteArray(5), source = "a")))
        advanceUntilIdle()
        assertTrue(files.files.isNotEmpty())

        // Someone else signs in on the same phone: her album is not theirs to see.
        val stranger = uploader(server, sent, files = files, clock = clock).also { it.restore(owner = "someone-else") }
        assertTrue(stranger.outgoing.isEmpty())
        assertTrue(files.files.isEmpty())
        assertEquals(null, files.manifest)

        // A day later the server has forgotten the uploads; so does the phone.
        val second = uploader(server, sent, files = files, clock = clock).also { it.restore(owner = "her") }
        server.failPut = server.puts.size + 1
        second.send("c1", "", listOf(FakePick(ByteArray(5), source = "b")))
        advanceUntilIdle()
        clock.now = clock.now + 25.hours
        val dayLater = uploader(server, sent, files = files, clock = clock).also { it.restore(owner = "her") }
        assertTrue(dayLater.outgoing.isEmpty())
        assertTrue(files.files.isEmpty())

        // Signing out drops what is waiting, files and all.
        clock.now = clock.now - 25.hours
        val third = uploader(server, sent, files = files, clock = clock).also { it.restore(owner = "her") }
        server.failPut = server.puts.size + 1
        third.send("c1", "", listOf(FakePick(ByteArray(5), source = "c")))
        advanceUntilIdle()
        third.discardAll()
        advanceUntilIdle()
        assertTrue(third.outgoing.isEmpty())
        assertTrue(files.files.isEmpty())
        assertEquals(null, files.manifest)
    }

    @Test
    fun `the album says how far it has got`() = runTest {
        val server = FakeServer()
        val uploader = uploader(server, mutableListOf())
        val album = uploader.send("c1", "", listOf(FakePick(ByteArray(8)), FakePick(ByteArray(8))))
        assertEquals(0f, album.progress)
        assertEquals(listOf("c1"), uploader.outgoingFor("c1").map { it.conversationId })
        assertTrue(uploader.outgoingFor("c2").isEmpty())
        advanceUntilIdle()
    }
}
