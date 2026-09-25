package uz.sadora.doctor.data

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.Ack
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorDocumentUpload
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Language
import uz.sadora.contract.OtpChallenge

/**
 * The app's whole journey, screen by screen, against one fake server that remembers what
 * was done to it: sign in with the dev code, apply, wait, get approved, answer a
 * question, write a post, sign out. Each step asserts what the screen it stands for
 * would draw.
 */
class DoctorFlowTest {

    /** The backend, as far as this journey touches it. */
    private class FakeServer {
        var status = DoctorStatus.NONE
        val answered = mutableSetOf<String>()
        val posts = mutableListOf<CommunityPost>()

        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            val post = request.method == HttpMethod.Post
            when {
                path == "/v1/auth/otp/request" ->
                    json(encode(OtpChallenge("ch-1", TestNow, resendAfterSeconds = 60, attemptsLeft = 5, devCode = "246810")))
                path == "/v1/auth/otp/verify" -> json(encode(testAuthSession()))
                path == "/v1/auth/logout" -> json(encode(Ack()))
                path == "/v1/doctor/me" -> json(encode(testAccount(status)))
                path == "/v1/doctor/application" -> {
                    status = DoctorStatus.PENDING
                    json(encode(testAccount(status)))
                }
                path == "/v1/doctor/questions" ->
                    json(encode(listOf(testQuestion("q1"), testQuestion("q2")).filterNot { it.id in answered }))
                path == "/v1/community/posts/q1" -> json(encode(testQuestion("q1")))
                path == "/v1/community/posts/q1/comments" && !post -> json(encode(listOf(testComment("c1", "q1"))))
                path == "/v1/community/posts/q1/comments" && post -> {
                    answered += "q1"
                    json(encode(testComment("c-mine", "q1", doctor = TestDoctor)), HttpStatusCode.Created)
                }
                path == "/v1/community/posts" && post -> {
                    val created = CommunityPost(
                        id = "p${posts.size + 1}",
                        topic = CommunityTopic.WELLBEING,
                        alias = TestDoctor.fullName,
                        tint = 0,
                        body = "Uyqu va gormonlar",
                        createdAt = TestNow,
                        isMine = true,
                        doctor = TestDoctor,
                    )
                    posts.add(0, created)
                    json(encode(created), HttpStatusCode.Created)
                }
                path == "/v1/doctors/doc-1" -> json(
                    encode(
                        DoctorProfile(
                            id = "doc-1",
                            fullName = TestDoctor.fullName,
                            specialty = TestDoctor.specialty,
                            workplace = "Toshkent",
                            experienceYears = 12,
                            verifiedSince = TestNow,
                            postCount = posts.size,
                            answerCount = answered.size,
                            isMe = true,
                            posts = posts.toList(),
                        ),
                    ),
                )
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
    }

    @Test
    fun `sign in — apply — get approved — answer — post — sign out`() = runTest {
        val server = FakeServer()
        val graph = testGraph(server.recording, storedToken = null)
        val auth = graph.authController()
        val doctors = graph.doctorController()

        // Splash: nothing stored, so the sign-in page.
        assertEquals(SessionState.SignedOut, graph.repository.resume())

        // Sign-in: the number, then the code the dev server sent back, already filled in.
        auth.updatePhone("90 123 45 67")
        assertTrue(auth.requestCode(Language.UZ))
        assertEquals("246810", auth.code)
        assertTrue(auth.verify())
        assertIs<SessionState.SignedIn>(graph.session.state.value)

        // Panel, never applied: the intro card.
        doctors.loadAccount(silent = false)
        assertEquals(PanelState.Intro, doctors.panelState)
        assertEquals("Bearer access-1", server.recording.seen.last().authorization)

        // The form goes in; the panel waits.
        assertTrue(
            doctors.apply(
                DoctorApplicationRequest(
                    fullName = "Dr. Nodira Karimova",
                    specialty = DoctorSpecialty.GYNECOLOGIST,
                    workplace = "Toshkent",
                    experienceYears = 12,
                    licenseNumber = "LIC-2041",
                    documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, "aGVsbG8=")),
                ),
            ),
        )
        assertIs<PanelState.Pending>(doctors.panelState)
        assertNull(doctors.doctorName)

        // An admin approves; the next look at the panel shows her verified, with work waiting.
        server.status = DoctorStatus.APPROVED
        doctors.loadAccount(silent = false)
        assertIs<PanelState.Approved>(doctors.panelState)
        assertEquals("Dr. Nodira Karimova", doctors.doctorName)
        doctors.loadQuestions()
        assertEquals(listOf("q1", "q2"), doctors.questionRows.map { it.post.id })

        // A question's page, and her answer.
        doctors.openThread("q1")
        assertTrue(doctors.answer("q1", "Bu 12-haftada normal holat."))
        assertEquals("c-mine", doctors.threadComments.first().id)

        // Back on the panel: the answered question is shown leaving, then it is gone.
        doctors.loadQuestions()
        assertEquals(listOf(true, false), doctors.questionRows.map { it.answered })
        doctors.settleAnswered()
        assertEquals(listOf("q2"), doctors.questionRows.map { it.post.id })

        // Her page, and a post of her own heading it.
        doctors.loadProfile("doc-1")
        assertEquals(1, doctors.profile?.answerCount)
        assertTrue(doctors.createPost(CommunityTopic.WELLBEING, "Uyqu va gormonlar"))
        assertEquals("p1", doctors.profilePosts.first().id)
        assertTrue(doctors.profilePosts.first().doctor != null)

        // Sign out: the session and everything the panel held are gone.
        auth.signOut()
        doctors.reset()
        assertEquals(SessionState.SignedOut, graph.session.state.value)
        assertNull(graph.tokenStorage.readRefreshToken())
        assertEquals(PanelState.Loading, doctors.panelState)
        assertTrue(doctors.questionRows.isEmpty())
        assertEquals(1, server.recording.countOf("/v1/auth/logout"))
    }
}
