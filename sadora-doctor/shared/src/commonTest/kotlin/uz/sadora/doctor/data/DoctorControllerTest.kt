package uz.sadora.doctor.data

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorDocumentUpload
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.ErrorCodes

/**
 * The doctor's controller against a mock server. What is pinned: an application moves
 * her account to what the server says, the work list is the server's, an answer goes
 * out, joins the thread and takes the question off the list, and a stale token is
 * refreshed once without the screen noticing.
 */
class DoctorControllerTest {

    private val application = DoctorApplicationRequest(
        fullName = "Dr. Nodira Karimova",
        specialty = DoctorSpecialty.GYNECOLOGIST,
        workplace = "Toshkent, 1-shahar klinikasi",
        experienceYears = 12,
        licenseNumber = "LIC-2041",
        documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, "aGVsbG8=")),
    )

    @Test
    fun `a successful application updates her account to pending`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctor/me" -> json(encode(testAccount(DoctorStatus.NONE)))
                "/v1/doctor/application" -> json(encode(testAccount(DoctorStatus.PENDING)))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()

        doctors.loadAccount(silent = false)
        assertEquals(PanelState.Intro, doctors.panelState)

        assertTrue(doctors.apply(application))
        assertIs<PanelState.Pending>(doctors.panelState)
        assertNull(doctors.applyCalls.error)
        val sent = recording.bodyOf(HttpMethod.Post, "/v1/doctor/application").orEmpty()
        assertTrue("\"specialty\":\"gynecologist\"" in sent, sent)
        assertTrue("\"kind\":\"diploma\"" in sent, sent)
    }

    @Test
    fun `a refused application keeps the account and says why on the form`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctor/me" -> json(encode(testAccount(DoctorStatus.REJECTED, note = "Litsenziya o'qilmaydi")))
                else -> json(
                    errorBody(ErrorCodes.VALIDATION_FAILED, details = mapOf("documents" to "Kamida bitta hujjat")),
                    HttpStatusCode.BadRequest,
                )
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadAccount(silent = false)

        assertFalse(doctors.apply(application))
        assertEquals(PanelState.Rejected("Litsenziya o'qilmaydi"), doctors.panelState)
        val failure = assertIs<ApiFailure.Validation>(doctors.applyCalls.error)
        assertEquals("Kamida bitta hujjat", failure.fields["documents"])
        // The panel's own banner is not the form's failure.
        assertNull(doctors.error)
    }

    @Test
    fun `the waiting questions are the server's`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctor/questions" -> json(encode(listOf(testQuestion("q1"), testQuestion("q2"))))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()
        assertFalse(doctors.questionsLoaded)

        doctors.loadQuestions()

        assertTrue(doctors.questionsLoaded)
        assertEquals(listOf("q1", "q2"), doctors.questions.map { it.id })
        assertEquals(1, recording.countOf("/v1/doctor/questions"))
    }

    @Test
    fun `answering sends the answer — joins the thread and takes the question off the list`() = runTest {
        val answer = testComment("c-mine", "q1", doctor = TestDoctor).copy(body = "Bu normal holat, lekin shifokoringizga ko'rsating.")
        var answered = false
        val recording = RecordingEngine { request ->
            val path = request.url.encodedPath
            when {
                // Once answered, the server no longer lists it as waiting.
                path == "/v1/doctor/questions" ->
                    json(encode(if (answered) listOf(testQuestion("q2")) else listOf(testQuestion("q1"), testQuestion("q2"))))
                path == "/v1/community/posts/q1" -> json(encode(testQuestion("q1")))
                path == "/v1/community/posts/q1/comments" && request.method == HttpMethod.Get ->
                    json(encode(listOf(testComment("c1", "q1"))))
                path == "/v1/community/posts/q1/comments" && request.method == HttpMethod.Post -> {
                    answered = true
                    json(encode(answer), HttpStatusCode.Created)
                }
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadQuestions()
        doctors.openThread("q1")
        assertEquals("q1", doctors.thread?.id)
        assertEquals(listOf("c1"), doctors.threadComments.map { it.id })

        assertTrue(doctors.answer("q1", answer.body))

        assertEquals(listOf("q2"), doctors.questions.map { it.id })
        // Doctors' answers lead the thread, as the server orders it.
        assertEquals(listOf("c-mine", "c1"), doctors.threadComments.map { it.id })
        assertEquals(2, doctors.thread?.commentCount)
        assertEquals(1, doctors.thread?.doctorAnswers)
        val sent = recording.bodyOf(HttpMethod.Post, "/v1/community/posts/q1/comments").orEmpty()
        assertTrue(answer.body in sent, sent)

        // The panel draws it once more, in its old place and marked, so it can leave on screen.
        assertEquals(listOf("q1" to true, "q2" to false), doctors.questionRows.map { it.post.id to it.answered })
        assertEquals(1, doctors.questionRows.first().post.doctorAnswers)
        // A reload that no longer has it keeps it in place until the panel lets it go.
        doctors.loadQuestions()
        assertEquals(listOf("q2"), doctors.questions.map { it.id })
        assertEquals(listOf("q1" to true, "q2" to false), doctors.questionRows.map { it.post.id to it.answered })
        doctors.settleAnswered()
        assertEquals(listOf("q2"), doctors.questionRows.map { it.post.id })
    }

    @Test
    fun `a failed answer leaves the question waiting`() = runTest {
        val recording = RecordingEngine { request ->
            when {
                request.url.encodedPath == "/v1/doctor/questions" -> json(encode(listOf(testQuestion("q1"))))
                request.method == HttpMethod.Post ->
                    json(errorBody(ErrorCodes.RATE_LIMITED, details = mapOf("retryAfterSeconds" to "60")), HttpStatusCode.TooManyRequests)
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadQuestions()

        assertFalse(doctors.answer("q1", "Javob"))

        assertEquals(listOf("q1"), doctors.questions.map { it.id })
        assertEquals(60, assertIs<ApiFailure.RateLimited>(doctors.threadCalls.error).retryAfterSeconds)
    }

    @Test
    fun `saving her details puts the server's account on the panel`() = runTest {
        val recording = RecordingEngine { request ->
            when {
                request.url.encodedPath == "/v1/doctor/me" && request.method == HttpMethod.Put ->
                    json(encode(testAccount().copy(workplace = "Samarqand, Viloyat perinatal markazi")))
                request.url.encodedPath == "/v1/doctor/me" -> json(encode(testAccount()))
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadAccount(silent = false)
        assertEquals("Dr. Nodira Karimova", doctors.doctorName)

        assertTrue(doctors.update(workplace = "Samarqand, Viloyat perinatal markazi", bio = ""))

        assertEquals("Samarqand, Viloyat perinatal markazi", doctors.account?.workplace)
        val sent = recording.bodyOf(HttpMethod.Put, "/v1/doctor/me").orEmpty()
        assertTrue("Samarqand" in sent, sent)
    }

    @Test
    fun `a new post goes out with its topic and heads her page`() = runTest {
        val published = CommunityPost(
            id = "p-new",
            topic = CommunityTopic.BODY,
            alias = TestDoctor.fullName,
            tint = 0,
            body = "Temir tanqisligi haqida",
            createdAt = TestNow,
            isMine = true,
            doctor = TestDoctor,
        )
        val page = DoctorProfile(
            id = "doc-1",
            fullName = TestDoctor.fullName,
            specialty = TestDoctor.specialty,
            workplace = "Toshkent",
            experienceYears = 12,
            verifiedSince = TestNow,
            postCount = 1,
            isMe = true,
            posts = listOf(testQuestion("old").copy(doctor = TestDoctor)),
        )
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/doctors/doc-1" -> json(encode(page))
                "/v1/community/posts" -> json(encode(published), HttpStatusCode.Created)
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val doctors = testGraph(recording).doctorController()
        doctors.loadProfile("doc-1")

        assertTrue(doctors.createPost(CommunityTopic.BODY, "Temir tanqisligi haqida"))

        assertEquals(listOf("p-new", "old"), doctors.profilePosts.map { it.id })
        assertEquals(2, doctors.profile?.postCount)
        val sent = recording.bodyOf(HttpMethod.Post, "/v1/community/posts").orEmpty()
        assertTrue("\"topic\":\"body\"" in sent, sent)
    }

    @Test
    fun `an expired access token is refreshed once and the call retried`() = runTest {
        val recording = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/refresh" -> json(encode(testAuthSession(access = "access-2", refresh = "refresh-2")))
                "/v1/doctor/me" ->
                    if (request.headers[HttpHeaders.Authorization] == "Bearer access-2") {
                        json(encode(testAccount()))
                    } else {
                        json(errorBody(ErrorCodes.TOKEN_EXPIRED), HttpStatusCode.Unauthorized)
                    }
                else -> json(errorBody(ErrorCodes.NOT_FOUND), HttpStatusCode.NotFound)
            }
        }
        val graph = testGraph(recording)
        val doctors = graph.doctorController()

        doctors.loadAccount(silent = false)

        assertIs<PanelState.Approved>(doctors.panelState)
        assertNull(doctors.error)
        assertEquals(1, recording.countOf("/v1/auth/refresh"))
        assertEquals("refresh-2", graph.tokenStorage.readRefreshToken())
    }
}
