package uz.sadora.doctor.data

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import uz.sadora.contract.Ack
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Page
import uz.sadora.contract.PhotoView

/**
 * Profile photos: which URLs her token may be sent to, the cache and its single fetch
 * per URL, when she is asked for a photo, and her new photo reaching every place she is
 * already drawn.
 */
class PhotosTest {

    // ---------------------------------------------------------------- the URL

    @Test
    fun `a path is put under the api base url`() {
        val base = "http://test.local"
        assertEquals("http://test.local/v1/doctors/doc-1/photo?v=17", photoRequestUrl(base, "/v1/doctors/doc-1/photo?v=17"))
        assertEquals("http://test.local/v1/me/photo", photoRequestUrl("$base/", "v1/me/photo"))
        assertEquals("https://api.sadora.app/v1/x", photoRequestUrl("https://api.sadora.app", " /v1/x "))
    }

    @Test
    fun `no photo is no request`() {
        assertNull(photoRequestUrl("http://test.local", null))
        assertNull(photoRequestUrl("http://test.local", ""))
        assertNull(photoRequestUrl("http://test.local", "   "))
    }

    @Test
    fun `her token only goes to her own server`() {
        val base = "https://api.sadora.app"
        assertEquals(
            "https://api.sadora.app/v1/doctors/doc-1/photo?v=2",
            photoRequestUrl(base, "https://api.sadora.app/v1/doctors/doc-1/photo?v=2"),
        )
        assertEquals("https://API.sadora.app/v1/p", photoRequestUrl(base, "https://API.sadora.app/v1/p"))
        assertNull(photoRequestUrl(base, "https://evil.example/v1/p"))
        assertNull(photoRequestUrl(base, "http://api.sadora.app/v1/p"), "another scheme is another origin")
        assertNull(photoRequestUrl(base, "https://api.sadora.app:8443/v1/p"), "another port is another origin")
        assertNull(photoRequestUrl(base, "https://api.sadora.app@evil.example/v1/p"))
        assertNull(photoRequestUrl(base, "//evil.example/v1/p"), "a scheme-relative URL names a host")
    }

    // ---------------------------------------------------------------- the cache

    @Test
    fun `the cache drops the photo used longest ago`() {
        val cache = PhotoCache<String>(maxEntries = 2)
        cache["a"] = "A"
        cache["b"] = "B"
        assertEquals("A", cache["a"]) // "a" is now the most recent
        cache["c"] = "C"
        assertEquals(2, cache.size)
        assertTrue("a" in cache)
        assertFalse("b" in cache)
        assertTrue("c" in cache)
        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun `a url is fetched once however many avatars ask for it at once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val images = RemoteImages(
            fetch = { url ->
                fetches++
                gate.await()
                ApiResult.Success(url.encodeToByteArray())
            },
            decode = { it.decodeToString() },
            decodeOn = Dispatchers.Unconfined,
        )
        val rows = (1..20).map { async { images.load("/v1/doctors/doc-1/photo?v=1") } }
        yield()
        gate.complete(Unit)
        rows.forEach { assertEquals("/v1/doctors/doc-1/photo?v=1", it.await()) }
        assertEquals(1, fetches)

        assertEquals("/v1/doctors/doc-1/photo?v=1", images.cached("/v1/doctors/doc-1/photo?v=1"))
        images.load("/v1/doctors/doc-1/photo?v=1")
        assertEquals(1, fetches, "a cached photo is not fetched again")
    }

    @Test
    fun `a failed fetch is tried again and an undecodable one is not kept`() = runTest {
        var fetches = 0
        var online = false
        val images = RemoteImages(
            fetch = { _ ->
                fetches++
                if (online) ApiResult.Success(byteArrayOf(1)) else ApiResult.Failure(ApiFailure.Network("offline"))
            },
            decode = { bytes -> bytes.takeIf { it.isNotEmpty() && it[0] == 1.toByte() }?.size },
            decodeOn = Dispatchers.Unconfined,
        )
        assertNull(images.load("/p"))
        online = true
        assertEquals(1, images.load("/p"))
        assertEquals(2, fetches)

        val broken = RemoteImages(
            fetch = { _ -> ApiResult.Success(byteArrayOf(9)) },
            decode = { _ -> error("not an image") },
            decodeOn = Dispatchers.Unconfined,
        )
        assertNull(broken.load("/q"))
        assertNull(broken.cached("/q"))
    }

    // ---------------------------------------------------------------- asking for one

    @Test
    fun `the sheet asks an approved doctor without a photo once`() {
        val approved = testAccount()
        assertTrue(photoNudgeDue(approved, alreadyAsked = false))
        assertFalse(photoNudgeDue(approved, alreadyAsked = true))
        assertFalse(photoNudgeDue(approved.copy(photoUrl = "/v1/doctors/doc-1/photo?v=1"), alreadyAsked = false))
        assertFalse(photoNudgeDue(testAccount(DoctorStatus.PENDING), alreadyAsked = false))
        assertFalse(photoNudgeDue(null, alreadyAsked = false))
    }

    @Test
    fun `the form asks for a photo once it is sent unless she has one`() {
        assertTrue(photoStepAfterApply(testAccount(DoctorStatus.PENDING)))
        assertFalse(photoStepAfterApply(testAccount(DoctorStatus.PENDING).copy(photoUrl = "/p?v=1")))
        assertFalse(photoStepAfterApply(testAccount(DoctorStatus.NONE)))
        assertFalse(photoStepAfterApply(null))
    }

    @Test
    fun `only her own bylines take the new photo`() {
        val mine = testQuestion("p1").copy(doctor = TestDoctor, alias = TestDoctor.fullName)
        val other = testQuestion("p2").copy(doctor = TestDoctor.copy(id = "doc-2", photoUrl = "/d2?v=1"))
        val alias = testQuestion("p3")
        val updated = listOf(mine, other, alias).withDoctorPhoto("doc-1", "/me?v=2")
        assertEquals("/me?v=2", updated[0].doctor?.photoUrl)
        assertEquals("/d2?v=1", updated[1].doctor?.photoUrl)
        assertEquals(alias, updated[2])

        val comments = listOf(testComment("c1", "p1", TestDoctor), testComment("c2", "p1"))
            .withDoctorCommentPhoto("doc-1", "/me?v=2")
        assertEquals("/me?v=2", comments[0].doctor?.photoUrl)
        assertNull(comments[1].doctor)
    }

    // ---------------------------------------------------------------- against the server

    @Test
    fun `a new photo goes up and shows on her account page and posts at once`() = runTest {
        val myPost = CommunityPost(
            id = "p1", topic = CommunityTopic.PREGNANCY, alias = TestDoctor.fullName, tint = 0,
            body = "Maslahat", createdAt = TestNow, doctor = TestDoctor,
        )
        val page = DoctorProfile(
            id = "doc-1", fullName = TestDoctor.fullName, specialty = TestDoctor.specialty,
            workplace = "Klinika", experienceYears = 12, verifiedSince = TestNow, isMe = true, posts = listOf(myPost),
        )
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctor/me" -> json(encode(testAccount()))
                "/v1/doctors/doc-1" -> json(encode(page))
                "/v1/community/posts" -> json(encode(Page(items = listOf(myPost), total = 1, limit = 50, offset = 0)))
                "/v1/doctor/photo" -> if (request.method == HttpMethod.Put) {
                    json(encode(PhotoView("/v1/doctors/doc-1/photo?v=5")))
                } else {
                    json(encode(Ack()))
                }
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadAccount()
        doctors.loadProfile("doc-1")
        doctors.loadFeed()

        assertTrue(doctors.setPhoto(CapturedPhotoData("aGVsbG8=", "image/jpeg")))
        val sent = recording.bodyOf(HttpMethod.Put, "/v1/doctor/photo").orEmpty()
        assertTrue("\"imageBase64\":\"aGVsbG8=\"" in sent, sent)
        assertTrue("\"mimeType\":\"image/jpeg\"" in sent, sent)
        assertEquals("/v1/doctors/doc-1/photo?v=5", doctors.account?.photoUrl)
        assertEquals("/v1/doctors/doc-1/photo?v=5", doctors.profile?.photoUrl)
        assertEquals("/v1/doctors/doc-1/photo?v=5", doctors.profilePosts.single().doctor?.photoUrl)
        assertEquals("/v1/doctors/doc-1/photo?v=5", doctors.feed.single().doctor?.photoUrl)

        assertTrue(doctors.removePhoto())
        assertEquals(1, recording.seen.count { it.method == HttpMethod.Delete && it.path == "/v1/doctor/photo" })
        assertNull(doctors.account?.photoUrl)
        assertNull(doctors.profile?.photoUrl)
        assertNull(doctors.feed.single().doctor?.photoUrl)
    }

    @Test
    fun `a refused photo leaves her account as it was and says why`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctor/me" -> json(encode(testAccount()))
                else -> json(
                    errorBody(ErrorCodes.VALIDATION_FAILED, details = mapOf("imageBase64" to "Rasm juda kichik")),
                    HttpStatusCode.BadRequest,
                )
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadAccount()

        assertFalse(doctors.setPhoto(CapturedPhotoData("aGVsbG8=", "image/jpeg")))
        assertNull(doctors.account?.photoUrl)
        val failure = assertIs<ApiFailure.Validation>(doctors.photoCalls.error)
        assertEquals("Rasm juda kichik", failure.fields["imageBase64"])
        assertNull(doctors.error, "the panel's banner is not the photo's failure")
    }

    @Test
    fun `a photo is fetched with her token from her own server`() = runTest {
        val jpeg = byteArrayOf(-1, -40, -1, -32)
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctors/doc-1/photo" -> respond(jpeg, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val graph = testGraph(recording)
        graph.session.saveTokens(testAuthSession(access = "access-7").tokens)

        val result = graph.photo("/v1/doctors/doc-1/photo?v=5")
        assertTrue(jpeg.contentEquals(assertIs<ApiResult.Success<ByteArray>>(result).value))
        val seen = recording.seen.single()
        assertEquals("Bearer access-7", seen.authorization)

        assertIs<ApiResult.Failure>(graph.photo("https://evil.example/v1/doctors/doc-1/photo"))
        assertEquals(1, recording.seen.size, "a foreign URL is refused before any request")
    }
}
