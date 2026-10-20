package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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

    private class FakePick(val bytes: ByteArray, override val kind: MediaKind = MediaKind.PHOTO) : PickedMedia {
        var released = false
        var prepared = 0
        override val durationMs: Long? = null
        override suspend fun preview(): ByteArray? = null
        override suspend fun prepare(onProgress: (Float) -> Unit): PreparedMedia {
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

    private class Sent(val conversationId: String, val caption: String, val mediaIds: List<String>)

    private fun TestScope.uploader(server: FakeServer, sent: MutableList<Sent>, failSends: Int = 0): MediaUploader {
        var failures = failSends
        // The test scope itself, not backgroundScope: advanceUntilIdle runs only foreground work.
        return MediaUploader(server, this) { album, ids ->
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
