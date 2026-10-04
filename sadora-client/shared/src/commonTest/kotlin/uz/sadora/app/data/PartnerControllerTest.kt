package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.app.nav.AppLink
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerRelation

class PartnerControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    @Test
    fun `a Yaqinim link is read from the push from the app scheme and from the web page`() {
        assertEquals(AppLink.Partner(null), AppLink.parse("sadora://yaqinim"))
        assertEquals(AppLink.Partner("K7M2QP4X"), AppLink.parse("sadora://yaqinim/k7m2-qp4x"))
        assertEquals(AppLink.Partner("K7M2QP4X"), AppLink.parse("https://api.sadora.app/y/K7M2QP4X"))
        // Not eight letters of the code alphabet: not a code at all.
        assertEquals(AppLink.Partner(null), AppLink.parse("sadora://yaqinim/ABC"))
        assertNull(AppLink.parse("https://api.sadora.app/y/ABC"))
        // The referral link still reads as one.
        assertIs<AppLink.Invite>(AppLink.parse("https://sadora.app/r/K7M2QP"))
    }

    @Test
    fun `a new invite keeps its code on this phone only`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/partner/invite" -> json(
                    """{"code":"K7M2-QP4X","url":"http://x/y/K7M2QP4X","relation":"mother",""" +
                        """"createdAt":"2026-10-05T08:00:00Z","expiresAt":"2026-10-07T08:00:00Z"}""",
                    HttpStatusCode.Created,
                )
                else -> json("{}")
            }
        }
        val partner = graph(recording).partnerController()

        val created = assertNotNull(partner.invite(PartnerRelation.MOTHER))

        assertEquals("K7M2-QP4X", created.code)
        assertEquals("K7M2-QP4X", partner.freshInvite?.code)
        assertNull(partner.state?.invite?.code, "the state keeps only that an invite is out")
        assertTrue(recording.bodies.any { it.contains("\"relation\":\"mother\"") })
    }

    @Test
    fun `a typed code puts the person at the top of the list waiting for her`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/partner/accept" ->
                    json("""{"linkId":"l-1","name":"Malika","relation":"husband","status":"pending"}""")
                else -> json("{}")
            }
        }
        val partner = graph(recording).partnerController()

        val followed = assertNotNull(partner.accept("K7M2QP4X", name = "Aziz", asPartnerAccount = true))

        assertEquals(PartnerLinkStatus.PENDING, followed.status)
        assertEquals(listOf("l-1"), partner.following.map { it.linkId })
        assertTrue(recording.bodies.any { it.contains("\"asPartnerAccount\":true") && it.contains("\"name\":\"Aziz\"") })
    }

    @Test
    fun `a refused code leaves the server sentence for the sheet`() = runTest {
        val recording = RecordingEngine {
            json(errorBody(ErrorCodes.NOT_FOUND, "Kod topilmadi yoki muddati o'tgan"), HttpStatusCode.NotFound)
        }
        val partner = graph(recording).partnerController()

        assertNull(partner.accept("K7M2QP4X"))

        val failure = assertIs<ApiFailure.Unexpected>(partner.error)
        assertEquals("Kod topilmadi yoki muddati o'tgan", failure.message)
        assertNotNull(failure.requestId, "an answer from the API, not from a proxy")
        assertTrue(partner.following.isEmpty())
    }

    @Test
    fun `a permission the server refused is switched back`() = runTest {
        val linked = """{"link":{"id":"l-1","status":"active","partnerName":"Aziz","relation":"husband",""" +
            """"permissions":{"cycle":true,"fertile":false},"createdAt":"2026-10-05T08:00:00Z"}}"""
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/partner/permissions" -> json(errorBody(ErrorCodes.INTERNAL_ERROR, "x"), HttpStatusCode.InternalServerError)
                else -> json(linked)
            }
        }
        val partner = graph(recording).partnerController()
        partner.refresh()
        assertTrue(partner.hasActiveLink)

        val saved = partner.savePermissions(PartnerPermissions(cycle = true, fertile = true))

        assertFalse(saved)
        assertEquals(false, partner.state?.link?.permissions?.fertile)
    }
}
