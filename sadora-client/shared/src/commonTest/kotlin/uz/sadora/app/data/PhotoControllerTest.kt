package uz.sadora.app.data

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.app.data.api.photoRequestPath
import uz.sadora.app.i18n.StringsUz
import uz.sadora.app.model.AppState
import uz.sadora.contract.Ack
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.PhotoView

/**
 * Profile photos as the app drives them. What is pinned: a server `photoUrl` is fetched
 * under the API base, with its version, and only once; no URL means no request; a
 * failure is not remembered; her photo goes up base64 by PUT and becomes hers at once,
 * drawn from what she picked; a refused picture keeps the server's reason and leaves
 * her old photo; removing it brings her initial back; and the profile carries it in.
 */
class PhotoControllerTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

    private fun graph(recording: RecordingEngine, baseUrl: String = "http://test.local") = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment(baseUrl),
        engine = recording.build(),
    )

    private fun image(): RecordingEngine = RecordingEngine { respond(jpeg, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg")) }

    @Test
    fun `a server path loses its leading slash so the base URL prefixes it`() {
        assertEquals("v1/doctors/d1/photo?v=17", photoRequestPath("/v1/doctors/d1/photo?v=17"))
        assertEquals("v1/me/photo?v=1", photoRequestPath("v1/me/photo?v=1"))
        assertEquals("https://cdn.example/p.jpg", photoRequestPath("https://cdn.example/p.jpg"))
        assertNull(photoRequestPath(null))
        assertNull(photoRequestPath(""))
        assertNull(photoRequestPath("  "))
        assertNull(photoRequestPath("/"))
    }

    @Test
    fun `a doctor's photo is fetched once with its version`() = runTest {
        val versions = mutableListOf<String>()
        val recording = RecordingEngine { request ->
            versions.add(request.url.parameters["v"].orEmpty())
            respond(jpeg, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
        }
        val photos = graph(recording).photoController(AppState())

        assertContentEquals(jpeg, photos.bytes("/v1/doctors/d1/photo?v=17"))
        assertContentEquals(jpeg, photos.bytes("/v1/doctors/d1/photo?v=17"))
        assertEquals(1, recording.countOf("/v1/doctors/d1/photo"))
        assertEquals(listOf("17"), versions)

        // A new photo is a new URL, so it is fetched again.
        photos.bytes("/v1/doctors/d1/photo?v=18")
        assertEquals(2, recording.countOf("/v1/doctors/d1/photo"))
        assertFalse(photos.saving, "loading a face is not saving")
    }

    @Test
    fun `the photo is asked for under a base URL with a path`() = runTest {
        val recording = image()
        val photos = graph(recording, baseUrl = "http://test.local/api").photoController(AppState())
        assertContentEquals(jpeg, photos.bytes("/v1/doctors/d1/photo?v=1"))
        assertEquals(listOf("/api/v1/doctors/d1/photo"), recording.paths)
    }

    @Test
    fun `no URL is no request and initials`() = runTest {
        val recording = image()
        val photos = graph(recording).photoController(AppState())
        assertNull(photos.bytes(null))
        assertNull(photos.bytes(""))
        assertTrue(recording.paths.isEmpty())
    }

    @Test
    fun `a photo that would not load is asked for again next time`() = runTest {
        var fail = true
        val recording = RecordingEngine {
            if (fail) {
                json(errorBody(ErrorCodes.NOT_FOUND, "no photo"), HttpStatusCode.NotFound)
            } else {
                respond(jpeg, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
        }
        val photos = graph(recording).photoController(AppState())
        assertNull(photos.bytes("/v1/doctors/d1/photo?v=1"))
        fail = false
        assertContentEquals(jpeg, photos.bytes("/v1/doctors/d1/photo?v=1"))
        assertEquals(2, recording.countOf("/v1/doctors/d1/photo"))
        assertNull(photos.error, "a face that did not load is not a banner")
    }

    @Test
    fun `her photo goes up and is hers at once`() = runTest {
        val methods = mutableListOf<HttpMethod>()
        val recording = RecordingEngine { request ->
            methods.add(request.method)
            json(encode(PhotoView("/v1/me/photo?v=42")))
        }
        val state = AppState()
        val photos = graph(recording).photoController(state)

        assertTrue(photos.setMine("/9gBAgM=", "image/jpeg"))
        assertEquals(listOf(HttpMethod.Put), methods)
        assertEquals("/v1/me/photo", recording.paths.single())
        assertTrue(recording.bodies.single().contains("\"imageBase64\":\"/9gBAgM=\""))
        assertEquals("/v1/me/photo?v=42", state.avatarUrl)

        // What she picked is what is drawn: no download of what she just sent.
        assertContentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3), photos.bytes(state.avatarUrl))
        assertEquals(1, recording.paths.size)
    }

    @Test
    fun `a refused picture keeps the server's reason and her old photo`() = runTest {
        val recording = RecordingEngine {
            json(
                errorBody(ErrorCodes.VALIDATION_FAILED, "Validation failed", mapOf("image" to "Rasm juda kichik")),
                HttpStatusCode.BadRequest,
            )
        }
        val state = AppState().apply { avatarUrl = "/v1/me/photo?v=1" }
        val photos = graph(recording).photoController(state)

        assertFalse(photos.setMine("AAAA", "image/jpeg"))
        assertEquals("Rasm juda kichik", photos.error?.readable(StringsUz.errors))
        assertEquals("/v1/me/photo?v=1", state.avatarUrl)
        assertFalse(photos.saving)
    }

    @Test
    fun `removing her photo brings her initial back`() = runTest {
        val methods = mutableListOf<HttpMethod>()
        val recording = RecordingEngine { request ->
            methods.add(request.method)
            json(encode(Ack()))
        }
        val state = AppState().apply { avatarUrl = "/v1/me/photo?v=1" }
        val photos = graph(recording).photoController(state)

        assertTrue(photos.removeMine())
        assertEquals(listOf(HttpMethod.Delete), methods)
        assertEquals("/v1/me/photo", recording.paths.single())
        assertNull(state.avatarUrl)
    }

    @Test
    fun `the profile carries her photo in and out`() {
        val state = AppState()
        state.applyServerProfile(testProfile().copy(avatarUrl = "/v1/me/photo?v=7"), testEntitlements())
        assertEquals("/v1/me/photo?v=7", state.avatarUrl)
        state.applyServerProfile(testProfile(), testEntitlements())
        assertNull(state.avatarUrl, "removed elsewhere is removed here")
    }

    @Test
    fun `with no backend nothing is sent and nothing changes`() = runTest {
        val state = AppState().apply { avatarUrl = "/v1/me/photo?v=1" }
        val photos = PhotoController(null, state)
        assertFalse(photos.setMine("AAAA", "image/jpeg"))
        assertFalse(photos.removeMine())
        assertNull(photos.bytes("/v1/me/photo?v=1"))
        assertEquals("/v1/me/photo?v=1", state.avatarUrl)
    }

    @Test
    fun `the image cache drops the least recently used first`() = runTest {
        val cache = ImageBytesCache(capacity = 2)
        cache.put("a", byteArrayOf(1))
        cache.put("b", byteArrayOf(2))
        cache.get("a")
        cache.put("c", byteArrayOf(3))
        assertNull(cache.get("b"))
        assertContentEquals(byteArrayOf(1), cache.get("a"))
        assertContentEquals(byteArrayOf(3), cache.get("c"))

        var fetched = 0
        assertNull(cache.getOrFetch("d") { fetched++; null })
        assertContentEquals(byteArrayOf(4), cache.getOrFetch("d") { fetched++; byteArrayOf(4) })
        assertContentEquals(byteArrayOf(4), cache.getOrFetch("d") { fetched++; byteArrayOf(5) })
        assertEquals(2, fetched, "a miss that fetched nothing is asked again; a hit is not")
    }
}
