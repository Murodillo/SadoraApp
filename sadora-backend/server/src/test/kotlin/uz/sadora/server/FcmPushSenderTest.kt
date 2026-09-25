package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.notify.FcmPushSender
import uz.sadora.server.notify.GoogleAccessTokens
import uz.sadora.server.notify.OutboxRecord
import uz.sadora.server.notify.PushConfigurationException
import uz.sadora.server.notify.ServiceAccountKey

/**
 * The sender's job is what happens per device, which is the part a real FCM project
 * cannot be relied on to demonstrate: a token that no longer exists has to be forgotten,
 * and one dead phone must not mark a reminder failed for a tablet that received it.
 */
class FcmPushSenderTest {

    private val record = OutboxRecord(
        id = Uuid.random(),
        userId = Uuid.random(),
        category = NotificationCategory.MED_REMINDER,
        title = "Dori vaqti",
        body = "Yod, 1 tabletka",
        scheduledFor = Instant.fromEpochSeconds(1_800_000_000),
        status = NotificationStatus.QUEUED,
        sentAt = null,
        suppressedReason = null,
    )

    @Test
    fun `a delivered message reports success and says who it is for`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val sender = sender(seen) { HttpStatusCode.OK to """{"name":"projects/p/messages/1"}""" }

        assertTrue(sender.send(record, listOf("device-a")))

        // The token request, then the send.
        assertEquals(2, seen.size)
        val push = seen.last()
        assertTrue(push.url.toString().endsWith("/v1/projects/test-project/messages:send"), push.url.toString())
        assertEquals("Bearer test-access-token", push.headers["Authorization"])
    }

    @Test
    fun `a token FCM says is gone is forgotten rather than retried forever`() = runTest {
        val forgotten = mutableListOf<String>()
        val sender = sender(mutableListOf(), onRejected = { forgotten += it }) {
            HttpStatusCode.NotFound to """{"error":{"status":"UNREGISTERED"}}"""
        }

        assertFalse(sender.send(record, listOf("gone")))
        assertEquals(listOf("gone"), forgotten)
    }

    @Test
    fun `one dead device does not fail a notification another device received`() = runTest {
        val forgotten = mutableListOf<String>()
        var call = 0
        val sender = sender(mutableListOf(), onRejected = { forgotten += it }) {
            call++
            // First push fails, second succeeds. (Call 1 is the token request.)
            if (call == 2) HttpStatusCode.NotFound to """{"error":{"status":"UNREGISTERED"}}"""
            else HttpStatusCode.OK to """{"name":"projects/p/messages/1"}"""
        }

        assertTrue(sender.send(record, listOf("gone", "alive")), "the tablet got it")
        assertEquals(listOf("gone"), forgotten)
    }

    @Test
    fun `no tokens is not a delivery`() = runTest {
        assertFalse(sender(mutableListOf()) { HttpStatusCode.OK to "{}" }.send(record, emptyList()))
    }

    /** Broken credentials are an operator problem; the row stays failed and retryable. */
    @Test
    fun `credentials that cannot produce a token fail the send rather than throwing`() = runTest {
        val sender = sender(mutableListOf()) { HttpStatusCode.Unauthorized to """{"error":"invalid_grant"}""" }
        assertFalse(sender.send(record, listOf("device-a")))
    }

    @Test
    fun `a private key that is not a key says so instead of failing at the first push`() {
        val broken = ServiceAccountKey("a@b.iam.gserviceaccount.com", "-----BEGIN PRIVATE KEY-----\nnope\n-----END PRIVATE KEY-----")
        val failure = runCatching { broken.privateKey() }.exceptionOrNull()
        assertTrue(failure is PushConfigurationException, "was $failure")
    }

    // ---------------------------------------------------------------- harness

    private fun sender(
        seen: MutableList<HttpRequestData>,
        onRejected: suspend (String) -> Unit = {},
        respondWith: () -> Pair<HttpStatusCode, String>,
    ): FcmPushSender {
        val engine = MockEngine { request ->
            seen += request
            if (request.url.toString().contains("oauth2")) {
                // The token exchange, unless the test is failing it.
                val (status, _) = respondWith()
                if (status == HttpStatusCode.Unauthorized && seen.size == 1) {
                    respond(
                        ByteReadChannel("""{"error":"invalid_grant"}"""),
                        HttpStatusCode.Unauthorized,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                } else {
                    respond(
                        ByteReadChannel("""{"access_token":"test-access-token","expires_in":3600}"""),
                        HttpStatusCode.OK,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            } else {
                val (status, body) = respondWith()
                respond(
                    ByteReadChannel(body),
                    status,
                    headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return FcmPushSender(
            client = client,
            projectId = "test-project",
            tokens = GoogleAccessTokens(client, TEST_KEY, FcmPushSender.SCOPE, "https://oauth2.googleapis.com/token"),
            onTokenRejected = onRejected,
        )
    }

    private companion object {
        /** A throwaway 2048-bit key, generated for this test and used nowhere else. */
        val TEST_KEY = ServiceAccountKey(
            clientEmail = "push@sadora-test.iam.gserviceaccount.com",
            privateKeyPem = generateTestKeyPem(),
        )

        fun generateTestKeyPem(): String {
            val pair = java.security.KeyPairGenerator.getInstance("RSA")
                .apply { initialize(2048) }
                .generateKeyPair()
            val encoded = java.util.Base64.getMimeEncoder(64, "\n".toByteArray())
                .encodeToString(pair.private.encoded)
            return "-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----\n"
        }
    }
}
