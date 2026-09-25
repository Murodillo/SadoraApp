package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import io.ktor.http.HttpStatusCode
import uz.sadora.app.nav.AppLink
import uz.sadora.contract.ErrorCodes

class WearableReturnTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    @Test
    fun `the return link carries the code and state back decoded`() {
        val link = assertIs<AppLink.WearableReturn>(
            AppLink.parse("sadora://wearables/whoop?status=ok&code=a%2Fb%3D&state=xYz_1"),
        )
        assertEquals("whoop", link.provider)
        assertEquals(true, link.ok)
        assertEquals("a/b=", link.code)
        assertEquals("xYz_1", link.state)
    }

    @Test
    fun `a failed return has no code to send`() {
        val link = assertIs<AppLink.WearableReturn>(AppLink.parse("sadora://wearables/whoop?status=error"))
        assertEquals(false, link.ok)
        assertNull(link.code)
    }

    /** The connection exists only once the server takes the code from her own session. */
    @Test
    fun `returning with a code completes the connection through the server`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/wearables/whoop/complete" -> json("""{"ok":true}""")
                else -> json("[]")
            }
        }
        val controller = graph(recording).wearableController()
        controller.connectStarted = uz.sadora.contract.HealthProvider.WHOOP

        controller.onReturned("whoop", ok = true, code = "c0de", state = "st4te")

        assertEquals(true, controller.returned)
        assertEquals(1, recording.countOf("/v1/wearables/whoop/complete"))
        assertEquals(true, recording.bodies.any { it.contains("\"state\":\"st4te\"") && it.contains("\"code\":\"c0de\"") })
    }

    /** A crafted link must not make the signed-in app post a code for a flow it never began. */
    @Test
    fun `a return for a flow this app did not start is ignored`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/wearables/whoop/complete" -> json("""{"ok":true}""")
                else -> json("[]")
            }
        }
        val controller = graph(recording).wearableController()

        controller.onReturned("whoop", ok = true, code = "c0de", state = "st4te")

        assertEquals(false, controller.returned)
        assertEquals(0, recording.countOf("/v1/wearables/whoop/complete"))
    }

    @Test
    fun `a state issued to someone else is reported as not connected`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/wearables/whoop/complete" ->
                    json(errorBody(ErrorCodes.VALIDATION_FAILED, "Bu ulanish boshqa hisob uchun boshlangan"), HttpStatusCode.BadRequest)
                else -> json("[]")
            }
        }
        val controller = graph(recording).wearableController()
        controller.connectStarted = uz.sadora.contract.HealthProvider.WHOOP

        controller.onReturned("whoop", ok = true, code = "c0de", state = "someone-elses")

        assertEquals(false, controller.returned)
    }
}
