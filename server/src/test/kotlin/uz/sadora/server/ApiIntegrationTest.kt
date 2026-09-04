package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import uz.sadora.contract.AiChatQuota
import uz.sadora.contract.AiChatReply
import uz.sadora.contract.AiChatRequest
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.AuthSession
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.CycleBaseline
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.DailyLog
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.LikeState
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.OnboardingCheckIn
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.OtpRequest
import uz.sadora.contract.OtpVerifyRequest
import uz.sadora.contract.Page
import uz.sadora.contract.Platform
import uz.sadora.contract.ReportReason
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.UserProfile
import uz.sadora.server.admin.AdminSession
import uz.sadora.server.admin.AdminSignInRequest
import uz.sadora.server.admin.AdminStats
import uz.sadora.server.auth.PasswordHasher
import uz.sadora.server.community.HideRequest
import uz.sadora.server.community.ModerationPostView
import uz.sadora.server.community.ModerationReportView
import uz.sadora.server.community.ResolveReportRequest
import uz.sadora.server.community.RestrictAuthorRequest
import uz.sadora.server.config.AppConfig
import uz.sadora.server.config.DatabaseConfig
import uz.sadora.server.config.Environment
import uz.sadora.server.config.HttpConfig
import uz.sadora.server.config.JwtConfig
import uz.sadora.server.config.OtpConfig
import uz.sadora.server.config.RedisConfig
import uz.sadora.server.config.SocialConfig
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.dbQuery

/**
 * The whole API against a real Postgres, the way the phone and the admin panel use it.
 *
 * Runs only when `TEST_DB_URL` is set — locally against a throwaway `sadora_test`
 * database, in CI against the job's Postgres service — and is skipped otherwise, so a
 * unit-test run on a laptop without Docker stays green. Every account it creates has a
 * random phone number, so the same database can host the suite again and again.
 *
 * What it pins is the seams a unit test cannot reach: the migration on top of the
 * previous ones, the consent gate in front of the onboarding check-in, the allowance the
 * entitlement service spends per AI question, and the alias-only surface of moderation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {

    private val databaseUrl: String? = System.getenv("TEST_DB_URL")?.takeIf { it.isNotBlank() }
    private lateinit var component: AppComponent

    @BeforeAll
    fun boot() {
        assumeTrue(databaseUrl != null, "TEST_DB_URL not set — integration test skipped")
        component = AppComponent(testConfig(databaseUrl!!))
    }

    @AfterAll
    fun shutDown() {
        if (::component.isInitialized) component.close()
    }

    // ---------------------------------------------------------------- onboarding

    @Test
    fun `onboarding stores the referral and lands the first check-in behind the consent gate`() = api {
        val user = signUp()
        onboard(user, referredByDoctor = true, storeHealth = true, mood = MoodLevel.LOW, symptoms = listOf("fatigue", "not_a_symptom"))

        val status = get<CycleStatus>("/v1/cycle/status", user.token)
        val day = get<DailyLog>("/v1/days/${status.today}", user.token)
        assertEquals(MoodLevel.LOW, day.mood)
        assertEquals(listOf("fatigue"), day.symptoms.map { it.key }, "unknown keys are dropped, known ones kept")

        val profile = get<UserProfile>("/v1/me", user.token)
        assertTrue(profile.onboardingCompleted)
    }

    @Test
    fun `without storage consent the first check-in is dropped, not rejected`() = api {
        val user = signUp()
        onboard(user, referredByDoctor = null, storeHealth = false, mood = MoodLevel.GOOD, symptoms = listOf("fatigue"))

        val status = get<CycleStatus>("/v1/cycle/status", user.token)
        val day = get<DailyLog>("/v1/days/${status.today}", user.token)
        assertTrue(day.isEmpty, "nothing was stored: $day")
    }

    // ---------------------------------------------------------------- community

    @Test
    fun `the secret chat works end to end and stays alias-only for moderation`() = api {
        val author = signUp().also { onboard(it) }
        val reader = signUp().also { onboard(it) }
        val admin = adminToken()

        val identity = get<CommunityIdentity>("/v1/community/me", author.token)
        assertTrue(identity.alias.split(' ').size >= 2, identity.alias)
        assertEquals(identity, get<CommunityIdentity>("/v1/community/me", author.token), "the alias is stable")

        val body = "Integration test post ${Uuid.random()}"
        val created = post<CommunityPost>("/v1/community/posts", author.token, CreatePostRequest(CommunityTopic.CYCLE, body))
        assertEquals(identity.alias, created.alias)
        assertTrue(created.isMine)

        // The reader sees it, without ownership, and can react to it.
        val feed = get<Page<CommunityPost>>("/v1/community/posts?topic=cycle&limit=50", reader.token)
        val seen = assertNotNull(feed.items.firstOrNull { it.id == created.id })
        assertFalse(seen.isMine)
        assertEquals(0, seen.likeCount)

        val liked = put<LikeState>("/v1/community/posts/${created.id}/like", reader.token)
        assertEquals(LikeState(liked = true, likeCount = 1), liked)
        put<LikeState>("/v1/community/posts/${created.id}/like", reader.token).also {
            assertEquals(1, it.likeCount, "liking twice is one like")
        }
        put<uz.sadora.contract.SaveState>("/v1/community/posts/${created.id}/save", reader.token)

        val comment = post<CommunityComment>(
            "/v1/community/posts/${created.id}/comments",
            reader.token,
            CreateCommentRequest("Menda ham shunday bo'lgan"),
        )
        assertTrue(comment.isMine)
        val comments = get<List<CommunityComment>>("/v1/community/posts/${created.id}/comments", author.token)
        assertEquals(listOf(comment.id), comments.map { it.id })
        assertFalse(comments.single().isMine, "the author does not own the reader's comment")

        val saved = get<Page<CommunityPost>>("/v1/community/posts?saved=true", reader.token)
        assertEquals(listOf(created.id), saved.items.map { it.id })
        val refreshed = saved.items.single()
        assertTrue(refreshed.liked && refreshed.saved)
        assertEquals(1, refreshed.commentCount)

        // Reporting: once per reader, never one's own.
        val ownReport = raw { client.post("/v1/community/posts/${created.id}/report") { auth(author.token); json(ReportRequest(ReportReason.SPAM)) } }
        assertEquals(HttpStatusCode.BadRequest, ownReport.status)
        val report = raw { client.post("/v1/community/posts/${created.id}/report") { auth(reader.token); json(ReportRequest(ReportReason.MISINFORMATION, "test")) } }
        assertEquals(HttpStatusCode.OK, report.status)
        val duplicate = raw { client.post("/v1/community/posts/${created.id}/report") { auth(reader.token); json(ReportRequest(ReportReason.SPAM)) } }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        // Moderation sees the alias and the counts, and nothing that names the account.
        val queue = get<Page<ModerationPostView>>("/v1/admin/community/posts?reported=true&limit=200", admin)
        val moderated = assertNotNull(queue.items.firstOrNull { it.id == created.id })
        assertEquals(identity.alias, moderated.alias)
        assertEquals(1, moderated.openReports)
        assertEquals(1, moderated.likeCount)
        val rawRow = rawGet("/v1/admin/community/posts?reported=true&limit=200", admin)
        assertFalse(author.userId in rawRow, "the moderation payload must not carry the author's id")

        val reports = get<Page<ModerationReportView>>("/v1/admin/community/reports?open=true&limit=200", admin)
        val open = assertNotNull(reports.items.firstOrNull { it.postId == created.id })
        assertEquals(ReportReason.MISINFORMATION, open.reason)

        // Hiding closes the reports and takes the post out of every feed.
        postAck("/v1/admin/community/posts/${created.id}/hide", admin, HideRequest(hidden = true, reason = "integration test"))
        val afterHide = get<Page<CommunityPost>>("/v1/community/posts?limit=100", reader.token)
        assertTrue(afterHide.items.none { it.id == created.id })
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/posts/${created.id}") { auth(reader.token) } }.status)
        val stillOpen = get<Page<ModerationReportView>>("/v1/admin/community/reports?open=true&limit=200", admin)
        assertTrue(stillOpen.items.none { it.postId == created.id }, "hiding resolves the reports on the post")

        // Restoring brings it back; a second resolve on a closed report is refused.
        postAck("/v1/admin/community/posts/${created.id}/hide", admin, HideRequest(hidden = false))
        assertEquals(HttpStatusCode.OK, raw { client.get("/v1/community/posts/${created.id}") { auth(reader.token) } }.status)
        val resolveAgain = raw { client.post("/v1/admin/community/reports/${open.id}/resolve") { auth(admin); json(ResolveReportRequest("dismiss")) } }
        assertEquals(HttpStatusCode.BadRequest, resolveAgain.status)

        // Restricting the author reaches her through the post; she can read but not write.
        postAck("/v1/admin/community/posts/${created.id}/restrict-author", admin, RestrictAuthorRequest(reason = "integration test", days = 1))
        val refused = raw { client.post("/v1/community/posts") { auth(author.token); json(CreatePostRequest(CommunityTopic.BODY, "another one")) } }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(HttpStatusCode.OK, raw { client.get("/v1/community/posts") { auth(author.token) } }.status)

        // The author can still take her own post down; the reader cannot.
        assertEquals(HttpStatusCode.NotFound, raw { client.delete("/v1/community/posts/${created.id}") { auth(reader.token) } }.status)
        assertEquals(HttpStatusCode.OK, raw { client.delete("/v1/community/posts/${created.id}") { auth(author.token) } }.status)
    }

    @Test
    fun `an operator can close the room with the flag`() = api {
        val user = signUp().also { onboard(it) }
        val admin = adminToken()
        setFlag(admin, "community", enabled = false)
        try {
            val response = raw { client.get("/v1/community/posts") { auth(user.token) } }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(ErrorCodes.FEATURE_DISABLED, response.body<ApiErrorResponse>().error.code)
        } finally {
            setFlag(admin, "community", enabled = true)
        }
    }

    // ---------------------------------------------------------------- AI

    @Test
    fun `AI chat spends the free allowance and reads her data only with consent`() = api {
        val user = signUp().also { onboard(it, storeHealth = true, aiInsights = true) }

        val quota = get<AiChatQuota>("/v1/ai/chat/quota", user.token)
        assertTrue(quota.enabled)
        val limit = assertNotNull(quota.dailyLimit, "the free tier is metered")
        assertEquals(0, quota.usedToday)

        val first = post<AiChatReply>("/v1/ai/chat", user.token, AiChatRequest("Nega charchayapman?"))
        assertTrue(first.basedOn.isNotEmpty(), "with consent the reply names its basis: $first")
        assertEquals(limit - 1, first.remainingToday)

        repeat(limit - 1) { post<AiChatReply>("/v1/ai/chat", user.token, AiChatRequest("Suv ichishim kerakmi?")) }

        val refused = raw { client.post("/v1/ai/chat") { auth(user.token); json(AiChatRequest("Yana bir savol")) } }
        assertEquals(HttpStatusCode.TooManyRequests, refused.status)
        assertEquals(ErrorCodes.LIMIT_REACHED, refused.body<ApiErrorResponse>().error.code)

        val spent = get<AiChatQuota>("/v1/ai/chat/quota", user.token)
        assertEquals(0, spent.remainingToday)

        // The dashboard counts what was spent.
        val stats = get<AdminStats>("/v1/admin/stats", adminToken())
        assertTrue((stats.aiUsageToday["ai_chat"] ?: 0) >= limit, "$stats")
    }

    @Test
    fun `without AI consent the answer is general and says so`() = api {
        val user = signUp().also { onboard(it, storeHealth = true, aiInsights = false) }
        val reply = post<AiChatReply>("/v1/ai/chat", user.token, AiChatRequest("Nega charchayapman?"))
        assertEquals("", reply.basedOn)
        assertTrue("roziligisiz" in reply.answer, reply.answer)
    }

    // ---------------------------------------------------------------- helpers

    private class TestUser(val token: String, val userId: String)

    private val wireJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    /**
     * One JSON-speaking client per test. Named rather than exposed as `client`, because
     * [ApplicationTestBuilder] has a `client` of its own that knows no JSON, and a member
     * always wins over an extension.
     */
    private class Api(val client: HttpClient)

    private fun api(block: suspend Api.() -> Unit) = testApplication {
        application { apiModule(component) }
        val client = createClient {
            install(ContentNegotiation) { json(wireJson) }
        }
        Api(client).block()
    }

    private suspend fun Api.signUp(): TestUser {
        val phone = "+9989" + (1..8).joinToString("") { Random.nextInt(10).toString() }
        val challenge = client.post("/v1/auth/otp/request") { json(OtpRequest(phone)) }.body<OtpChallenge>()
        val code = assertNotNull(challenge.devCode, "the test config exposes the code")
        val session = client.post("/v1/auth/otp/verify") {
            json(OtpVerifyRequest(challenge.challengeId, code, DeviceInfo("test-device", Platform.ANDROID, timezone = "Asia/Tashkent")))
        }.body<AuthSession>()
        assertTrue(session.isNewUser)
        return TestUser(session.tokens.accessToken, session.user.id)
    }

    private suspend fun Api.onboard(
        user: TestUser,
        referredByDoctor: Boolean? = false,
        storeHealth: Boolean = true,
        aiInsights: Boolean = true,
        mood: MoodLevel? = null,
        symptoms: List<String> = emptyList(),
    ) {
        val request = OnboardingRequest(
            name = "Test",
            language = Language.UZ,
            timezone = "Asia/Tashkent",
            lifeStage = LifeStage.CYCLE,
            cycle = CycleBaseline(averageCycleLength = 28, averagePeriodLength = 5),
            consents = ConsentGrants(storeHealth = storeHealth, aiInsights = aiInsights),
            referredByDoctor = referredByDoctor,
            firstCheckIn = if (mood == null && symptoms.isEmpty()) null else OnboardingCheckIn(mood, symptoms),
        )
        val response = client.post("/v1/me/onboarding") { auth(user.token); json(request) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsTextSafe())
    }

    private suspend fun Api.adminToken(): String {
        val email = "test-${Uuid.random()}@sadora.test"
        val password = "Test12345"
        dbQuery {
            AdminUsers.insert {
                it[id] = Uuid.random()
                it[AdminUsers.email] = email
                it[passwordHash] = PasswordHasher.hash(password)
                it[name] = "Integration"
                it[role] = "owner"
                it[totpEnabled] = false
                it[status] = "active"
                it[failedAttempts] = 0
                it[createdAt] = now().toOffsetDateTime()
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
        return client.post("/v1/admin/auth/login") { json(AdminSignInRequest(email, password)) }.body<AdminSession>().accessToken
    }

    private suspend fun Api.setFlag(admin: String, key: String, enabled: Boolean) {
        val response = client.put("/v1/admin/flags/$key") {
            auth(admin)
            json(uz.sadora.server.admin.UpdateFlagRequest(enabled = enabled, defaultValue = true))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend inline fun <reified T> Api.get(path: String, token: String): T {
        val response = client.get(path) { auth(token) }
        assertEquals(HttpStatusCode.OK, response.status, "GET $path: ${response.bodyAsTextSafe()}")
        return response.body()
    }

    private suspend fun Api.rawGet(path: String, token: String): String =
        client.get(path) { auth(token) }.bodyAsTextSafe()

    private suspend inline fun <reified T> Api.post(path: String, token: String, body: Any): T {
        val response = client.post(path) { auth(token); json(body) }
        assertTrue(response.status.value in 200..299, "POST $path: ${response.status} ${response.bodyAsTextSafe()}")
        return response.body()
    }

    private suspend inline fun <reified T> Api.put(path: String, token: String): T {
        val response = client.put(path) { auth(token) }
        assertEquals(HttpStatusCode.OK, response.status, "PUT $path: ${response.bodyAsTextSafe()}")
        return response.body()
    }

    private suspend fun Api.postAck(path: String, token: String, body: Any) {
        val response = client.post(path) { auth(token); json(body) }
        assertEquals(HttpStatusCode.OK, response.status, "POST $path: ${response.bodyAsTextSafe()}")
    }

    private suspend fun Api.raw(block: suspend () -> HttpResponse): HttpResponse = block()

    private fun io.ktor.client.request.HttpRequestBuilder.auth(token: String) = bearerAuth(token)

    /**
     * Encodes the body from its runtime class, so helpers can take `Any` without every
     * call site spelling out the type twice.
     */
    private fun io.ktor.client.request.HttpRequestBuilder.json(body: Any) {
        val encoded = wireJson.encodeToString(serializer(body.javaClass as java.lang.reflect.Type), body)
        setBody(TextContent(encoded, ContentType.Application.Json))
    }

    private suspend fun HttpResponse.bodyAsTextSafe(): String =
        runCatching { bodyAsText() }.getOrDefault("")

    private fun testConfig(databaseUrl: String) = AppConfig(
        environment = Environment.DEV,
        http = HttpConfig(port = 0, host = "127.0.0.1", allowedOrigins = listOf("http://localhost:5173")),
        database = DatabaseConfig(
            jdbcUrl = databaseUrl,
            user = System.getenv("TEST_DB_USER")?.takeIf { it.isNotBlank() } ?: "sadora",
            password = System.getenv("TEST_DB_PASSWORD")?.takeIf { it.isNotBlank() } ?: "sadora",
            maxPoolSize = 4,
            runMigrations = true,
        ),
        redis = RedisConfig(url = null),
        jwt = JwtConfig(
            secret = "integration-test-secret-0123456789abcdef",
            issuer = "sadora",
            audience = "sadora-app",
            accessTokenTtl = 15.minutes,
            refreshTokenTtl = 30.days,
        ),
        otp = OtpConfig(
            codeLength = 6,
            ttl = 300.seconds,
            maxAttempts = 5,
            resendAfter = 60.seconds,
            maxPerPhonePerHour = 100,
            exposeCode = true,
        ),
        social = SocialConfig(appleBundleIds = listOf("uz.sadora.app"), googleClientIds = emptyList()),
        policyVersion = "2026-08-01",
        minimumAppVersion = null,
    )
}
