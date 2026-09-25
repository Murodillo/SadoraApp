package uz.sadora.doctor.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.ApiError
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.AuthSession
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorAuthor
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.Entitlements
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.SubscriptionTier
import uz.sadora.contract.TokenPair
import uz.sadora.contract.UserProfile

// Fixtures and a recording mock server, in the shape of the client app's FakeBackend.

/** A fixed instant so the fixtures do not depend on the clock. */
val TestNow: Instant = Instant.parse("2026-09-26T09:00:00Z")

fun testProfile(): UserProfile = UserProfile(
    id = "11111111-1111-1111-1111-111111111111",
    phone = "+998901234567",
    name = "Nodira",
    language = Language.UZ,
    timezone = "Asia/Tashkent",
    lifeStage = LifeStage.CYCLE,
    onboardingCompleted = true,
    status = AccountStatus.ACTIVE,
    createdAt = TestNow,
)

fun testAuthSession(access: String = "access-1", refresh: String = "refresh-1"): AuthSession = AuthSession(
    tokens = TokenPair(
        accessToken = access,
        refreshToken = refresh,
        accessExpiresAt = TestNow + 15.minutes,
        refreshExpiresAt = TestNow + 30.minutes,
    ),
    user = testProfile(),
    entitlements = Entitlements(tier = SubscriptionTier.FREE, features = emptyList(), evaluatedAt = TestNow),
    isNewUser = false,
)

fun testAccount(status: DoctorStatus = DoctorStatus.APPROVED, note: String? = null): DoctorAccount = DoctorAccount(
    status = status,
    profileId = "doc-1".takeIf { status == DoctorStatus.APPROVED },
    fullName = "Dr. Nodira Karimova".takeIf { status != DoctorStatus.NONE },
    specialty = DoctorSpecialty.GYNECOLOGIST.takeIf { status != DoctorStatus.NONE },
    workplace = "Toshkent, 1-shahar klinikasi".takeIf { status != DoctorStatus.NONE },
    experienceYears = 12.takeIf { status != DoctorStatus.NONE },
    licenseNumber = "LIC-2041".takeIf { status != DoctorStatus.NONE },
    documentCount = if (status == DoctorStatus.NONE) 0 else 2,
    reviewNote = note,
    submittedAt = TestNow.takeIf { status != DoctorStatus.NONE },
)

val TestDoctor = DoctorAuthor(id = "doc-1", fullName = "Dr. Nodira Karimova", specialty = DoctorSpecialty.GYNECOLOGIST)

fun testQuestion(id: String, comments: Int = 1): CommunityPost = CommunityPost(
    id = id,
    topic = CommunityTopic.PREGNANCY,
    alias = "Sokin Bulut",
    tint = 2,
    body = "Savol $id: 12-haftada qon tahlili normalmi?",
    createdAt = TestNow,
    commentCount = comments,
)

fun testComment(id: String, postId: String, doctor: DoctorAuthor? = null): CommunityComment = CommunityComment(
    id = id,
    postId = postId,
    alias = doctor?.fullName ?: "Yorug' Tong",
    tint = 1,
    body = "Izoh $id",
    createdAt = TestNow,
    isMine = doctor != null,
    doctor = doctor,
)

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

fun MockRequestHandleScope.json(value: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(ByteReadChannel(value), status, jsonHeaders)

inline fun <reified T> encode(value: T): String = SadoraJson.encodeToString(value)

fun errorBody(code: String, message: String = "no", details: Map<String, String> = emptyMap()): String =
    SadoraJson.encodeToString(ApiErrorResponse(ApiError(code, message, details, "req-test")))

/** One request as the server saw it. */
data class Seen(val method: HttpMethod, val path: String, val body: String, val authorization: String?)

/**
 * Records every request the client made — method, path, body, bearer — so a test can
 * assert on what actually went over the wire, not only on what came back.
 */
class RecordingEngine(
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val seen = mutableListOf<Seen>()

    fun build(): MockEngine = MockEngine { request ->
        seen.add(
            Seen(
                method = request.method,
                path = request.url.encodedPath,
                body = (request.body as? TextContent)?.text.orEmpty(),
                authorization = request.headers[HttpHeaders.Authorization],
            ),
        )
        handler(request)
    }

    fun bodyOf(method: HttpMethod, path: String): String? = seen.lastOrNull { it.method == method && it.path == path }?.body

    fun countOf(path: String): Int = seen.count { it.path == path }
}

/** A graph over [recording], signed in with a stored refresh token unless told otherwise. */
fun testGraph(recording: RecordingEngine, storedToken: String? = "refresh-0"): DoctorGraph = DoctorGraph(
    tokenStorage = InMemoryTokenStorage(token = storedToken),
    device = FixedDeviceIdentity(),
    environment = SadoraEnvironment("http://test.local"),
    engine = recording.build(),
)
