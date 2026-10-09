package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import uz.sadora.contract.AdminArticle
import uz.sadora.contract.AdjustCoinsRequest
import uz.sadora.contract.AdminRedemption
import uz.sadora.contract.AiGreeting
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.ClaimReferralRequest
import uz.sadora.contract.ClaimReferralResult
import uz.sadora.contract.CoinBalance
import uz.sadora.contract.CoinReasons
import uz.sadora.contract.DailyCheckInResult
import uz.sadora.contract.HomeLayout
import uz.sadora.contract.HomeWidget
import uz.sadora.contract.HomeWidgets
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.LogStageEventRequest
import uz.sadora.contract.NutritionGoals
import uz.sadora.contract.PeriodEntry
import uz.sadora.contract.RedeemRequest
import uz.sadora.contract.RedeemResult
import uz.sadora.contract.Redemption
import uz.sadora.contract.ReferralStatus
import uz.sadora.contract.RewardsSummary
import uz.sadora.contract.SaveHomeLayoutRequest
import uz.sadora.contract.ShopCatalog
import uz.sadora.contract.ShopKind
import uz.sadora.contract.StageEvent
import uz.sadora.contract.StageEventKind
import uz.sadora.contract.SymptomDefinition
import uz.sadora.contract.UpdatePeriodRequest
import uz.sadora.contract.WaterState
import uz.sadora.contract.AiChatQuota
import uz.sadora.contract.AiChatReply
import uz.sadora.contract.AiChatRequest
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.Article
import uz.sadora.contract.ArticleBlock
import uz.sadora.contract.ArticleFeed
import uz.sadora.contract.ArticleKind
import uz.sadora.contract.Appointment
import uz.sadora.contract.CompleteAppointmentRequest
import uz.sadora.contract.SaveAppointmentRequest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import uz.sadora.contract.StageBaseline
import uz.sadora.contract.AuthSession
import uz.sadora.contract.BillingCatalogue
import uz.sadora.contract.CheckoutRequest
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.BlockState
import uz.sadora.contract.CommunityBadge
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityIdentity
import uz.sadora.contract.CommunityProfile
import io.ktor.http.encodeURLPathPart
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.MessagePage
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.StartConversationRequest
import uz.sadora.contract.UpdateIdentityRequest
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.CreateArticleRequest
import uz.sadora.contract.CreateCommentRequest
import uz.sadora.contract.CreatePostRequest
import uz.sadora.contract.CycleBaseline
import uz.sadora.contract.CycleStatus
import uz.sadora.contract.FoodScanRequest
import uz.sadora.contract.Limits
import uz.sadora.contract.DailyLog
import uz.sadora.contract.DeleteAccountRequest
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.Entitlements
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.InsightsSummary
import uz.sadora.contract.LikeState
import uz.sadora.contract.MindCheckIn
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.OnboardingCheckIn
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.CreateShareRequest
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.ProfileShare
import uz.sadora.contract.ProviderInfo
import uz.sadora.contract.ProviderUnavailable
import uz.sadora.contract.WearableConnection
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.OtpRequest
import uz.sadora.contract.OtpVerifyRequest
import uz.sadora.contract.RefreshRequest
import uz.sadora.contract.Page
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.PaymentStatus
import uz.sadora.contract.Platform
import uz.sadora.contract.PublishArticleRequest
import uz.sadora.contract.ReportReason
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.StorePurchaseRequest
import uz.sadora.contract.SubscriptionSource
import uz.sadora.contract.SubscriptionTier
import uz.sadora.contract.SaveArticleRequest
import uz.sadora.contract.TrendMetric
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.contract.UserProfile
import uz.sadora.contract.AcceptPartnerInviteRequest
import uz.sadora.contract.Ack
import uz.sadora.contract.AccountKind
import uz.sadora.contract.CreatePartnerInviteRequest
import uz.sadora.contract.FollowedPerson
import uz.sadora.contract.PartnerInvite
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerRelation
import uz.sadora.contract.PartnerState
import uz.sadora.contract.PartnerView
import uz.sadora.contract.PausePartnerRequest
import uz.sadora.contract.CreatePartnerWebLinkRequest
import uz.sadora.contract.PartnerMessage
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerMessages
import uz.sadora.contract.PartnerWebLink
import uz.sadora.contract.SendPartnerMessageRequest
import uz.sadora.contract.UzbekPhone
import uz.sadora.server.admin.AdminSession
import uz.sadora.server.admin.AdminMe
import uz.sadora.server.admin.AdminSignInRequest
import uz.sadora.server.admin.Totp
import uz.sadora.server.admin.TotpConfirmRequest
import uz.sadora.server.admin.TotpDisableRequest
import uz.sadora.server.admin.TotpEnrolment
import uz.sadora.server.admin.AdminAnalytics
import uz.sadora.server.admin.AdminStats
import uz.sadora.server.auth.PasswordHasher
import uz.sadora.server.billing.AdminPaymentView
import uz.sadora.server.billing.BillingService
import uz.sadora.server.community.CommunityBadges
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorDocumentUpload
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.UpdateDoctorProfileRequest
import uz.sadora.server.community.HideRequest
import uz.sadora.server.community.ModerationCommentView
import uz.sadora.server.community.ModerationPostView
import uz.sadora.server.community.ModerationReportView
import uz.sadora.server.community.ResolveReportRequest
import uz.sadora.server.community.RestrictAuthorRequest
import uz.sadora.server.config.AiConfig
import uz.sadora.server.config.BillingConfig
import uz.sadora.server.config.ClickConfig
import uz.sadora.server.config.PaymeConfig
import uz.sadora.server.config.AppConfig
import uz.sadora.server.config.DatabaseConfig
import uz.sadora.server.config.Environment
import uz.sadora.server.config.HttpConfig
import uz.sadora.server.config.JwtConfig
import uz.sadora.server.config.OtpConfig
import uz.sadora.server.config.PushConfig
import uz.sadora.server.config.RedisConfig
import uz.sadora.server.config.SocialConfig
import uz.sadora.server.config.WearableConfig
import uz.sadora.server.config.WhoopConfig
import uz.sadora.server.core.now
import uz.sadora.server.user.AccountErasureJob
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.AuditLog
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

    @Test
    fun `the stage anchor comes back with the profile, so the app can count the week`() = api {
        val her = signUp()
        val due = LocalDate(2027, 3, 14)
        val response = client.post("/v1/me/onboarding") {
            auth(her.token)
            json(
                OnboardingRequest(
                    name = "Dilnoza",
                    language = Language.UZ,
                    timezone = "Asia/Tashkent",
                    lifeStage = LifeStage.PREGNANCY,
                    stage = StageBaseline(dueDate = due),
                    consents = ConsentGrants(storeHealth = true, policyVersion = "2026-08-01"),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsTextSafe())

        // Without this the app has no anchor and can only show a week it made up.
        val profile = get<UserProfile>("/v1/me", her.token)
        assertEquals(due, profile.stage?.dueDate)
    }

    // ---------------------------------------------------------------- profile share

    /**
     * The QR code end to end: she makes a link, a stranger opens it without a token and
     * sees her record as HTML, she revokes it, and the same link is then a 404 that says
     * nothing about whether it ever existed.
     */
    @Test
    fun `a share link opens the doctor page once made and stops once revoked`() = api {
        val user = signUp()
        onboard(user, mood = MoodLevel.GOOD, symptoms = listOf("headache"))

        val created = post<ProfileShare>("/v1/me/shares", user.token, CreateShareRequest(ttlHours = 2))
        val url = assertNotNull(created.url, "the creating response carries the link")
        assertTrue(url.startsWith("http://localhost:8080/share/"), url)
        val token = url.substringAfterLast('/')

        val page = client.get("/share/$token")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("<title>SADORA"), html.take(200))
        assertTrue(html.contains("Test"), "her name is on the page")
        assertEquals("no-store", page.headers["Cache-Control"])

        val json = client.get("/share/$token/json?lang=en")
        assertEquals(HttpStatusCode.OK, json.status)
        val summary = json.body<DoctorSummary>()
        assertEquals("Test", summary.person.name)
        assertEquals(Language.EN, summary.language)
        assertTrue(summary.symptomCounts.any { it.key == "headache" })

        val listed = get<List<ProfileShare>>("/v1/me/shares", user.token)
        assertEquals(2, listed.first().viewCount, "both opens were counted")

        val revoked = client.delete("/v1/me/shares/${created.id}") { auth(user.token) }
        assertEquals(HttpStatusCode.OK, revoked.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/share/$token").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/share/not-a-token-at-all").status)
    }

    @Test
    fun `a new share retires the previous one and a too-long life is refused`() = api {
        val user = signUp()
        onboard(user)
        val first = post<ProfileShare>("/v1/me/shares", user.token, CreateShareRequest(ttlHours = 24))
        val second = post<ProfileShare>("/v1/me/shares", user.token, CreateShareRequest(ttlHours = 24))
        assertNotEquals(first.id, second.id)
        val firstToken = first.url!!.substringAfterLast('/')
        assertEquals(HttpStatusCode.NotFound, client.get("/share/$firstToken").status, "the older link stopped")

        val tooLong = client.post("/v1/me/shares") { auth(user.token); json(CreateShareRequest(ttlHours = 24 * 30)) }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)

        val export = client.get("/v1/me/export") { auth(user.token) }
        assertEquals(HttpStatusCode.OK, export.status)
        assertTrue(export.headers["Content-Disposition"].orEmpty().contains("sadora-export.json"))
    }

    // ---------------------------------------------------------------- Yaqinim

    /**
     * The whole of Yaqinim: she makes a code, a new account signs up with it and becomes a
     * follower-only account, sees nothing until she says yes, then sees only what she
     * ticked — her mood and her fertile window stay out of the answer until she turns them
     * on — and nothing at all once she pauses or ends it.
     */
    @Test
    fun `a Yaqinim link shows only what she ticked, and only while she lets it`() = api {
        val her = signUp()
        onboard(her, mood = MoodLevel.LOW, symptoms = listOf("headache"))
        val today = get<CycleStatus>("/v1/cycle/status", her.token).today
        // Day 13 of a 28-day cycle: inside the fertile window by any count.
        post<PeriodEntry>("/v1/cycle/periods", her.token, LogPeriodRequest(today.minus(12, DateTimeUnit.DAY), today.minus(8, DateTimeUnit.DAY)))
        assertEquals(uz.sadora.contract.CyclePhase.FERTILE, get<CycleStatus>("/v1/cycle/status", her.token).phase, "the test needs a fertile day")

        val invite = post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest(PartnerRelation.HUSBAND))
        val code = assertNotNull(invite.code, "the creating response carries the code")
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code), code)
        assertTrue(invite.url.orEmpty().endsWith("/y/" + code.replace("-", "")), invite.url.orEmpty())
        assertNull(get<PartnerState>("/v1/partner", her.token).invite?.code, "a later read does not show the code")

        // He signs up with it: no onboarding, a follower-only account.
        val him = signUp()
        val followed = post<FollowedPerson>(
            "/v1/partner/accept",
            him.token,
            AcceptPartnerInviteRequest(code.lowercase().replace("-", " "), name = "Aziz", asPartnerAccount = true),
        )
        assertEquals(PartnerLinkStatus.PENDING, followed.status)
        assertEquals("Test", followed.name)
        val profile = get<UserProfile>("/v1/me", him.token)
        assertEquals(AccountKind.PARTNER, profile.accountKind)
        assertTrue(profile.onboardingCompleted)
        assertEquals("Aziz", profile.name)

        // Waiting for her: the status and nothing else.
        val waiting = get<PartnerView>("/v1/partner/following/${followed.linkId}", him.token)
        assertEquals(PartnerLinkStatus.PENDING, waiting.status)
        assertTrue(waiting.isEmpty)
        val herState = get<PartnerState>("/v1/partner", her.token)
        assertEquals("Aziz", herState.link?.partnerName)
        assertNull(herState.invite, "the code is used up")

        post<PartnerState>("/v1/partner/approve", her.token, Ack())
        val view = get<PartnerView>("/v1/partner/following/${followed.linkId}", him.token)
        assertEquals(PartnerLinkStatus.ACTIVE, view.status)
        assertEquals(LifeStage.CYCLE, view.stage)
        val cycle = assertNotNull(view.cycle)
        assertEquals(13, cycle.cycleDay)
        assertEquals(uz.sadora.contract.CyclePhase.FOLLICULAR, cycle.phase, "a fertile day reads as follicular without the permission")
        assertNull(cycle.fertileFrom)
        assertNull(cycle.ovulationOn)
        assertNull(view.day, "mood and symptoms are off by default")
        // Not in the JSON at all, not just null in the DTO.
        val raw = rawGet("/v1/partner/following/${followed.linkId}", him.token)
        assertFalse(raw.contains("headache") || raw.contains("fertileFrom"), raw)
        assertEquals(1, countCoins(her, CoinReasons.PARTNER_LINKED))

        put<PartnerState>(
            "/v1/partner/permissions",
            her.token,
            PartnerPermissions(cycle = true, fertile = true, mood = true, symptoms = true),
        )
        val wider = get<PartnerView>("/v1/partner/following/${followed.linkId}", him.token)
        assertEquals(uz.sadora.contract.CyclePhase.FERTILE, wider.cycle?.phase)
        assertNotNull(wider.cycle?.fertileFrom)
        assertEquals(MoodLevel.LOW, wider.day?.mood)
        assertEquals(1, wider.day?.symptoms?.size)
        assertNotNull(get<PartnerState>("/v1/partner", her.token).link?.lastViewedAt, "she can see that he looked")

        post<PartnerState>("/v1/partner/pause", her.token, PausePartnerRequest(paused = true))
        val paused = get<PartnerView>("/v1/partner/following/${followed.linkId}", him.token)
        assertEquals(PartnerLinkStatus.PAUSED, paused.status)
        assertTrue(paused.isEmpty)
        post<PartnerState>("/v1/partner/pause", her.token, PausePartnerRequest(paused = false))

        // Ending it closes the door at once, and a second link pays nothing more.
        val ended = client.delete("/v1/partner") { auth(her.token) }
        assertEquals(HttpStatusCode.OK, ended.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/partner/following/${followed.linkId}") { auth(him.token) }.status)
        assertTrue(get<PartnerState>("/v1/partner", him.token).following.isEmpty())
        val again = post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest())
        val second = post<FollowedPerson>("/v1/partner/accept", him.token, AcceptPartnerInviteRequest(again.code!!))
        post<PartnerState>("/v1/partner/approve", her.token, Ack())
        assertEquals(PartnerLinkStatus.ACTIVE, get<PartnerView>("/v1/partner/following/${second.linkId}", him.token).status)
        assertEquals(1, countCoins(her, CoinReasons.PARTNER_LINKED))
    }

    @Test
    fun `a Yaqinim code opens once, never for its maker, and a new one retires the old`() = api {
        val her = signUp().also { onboard(it) }
        val first = post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest(PartnerRelation.MOTHER))
        val second = post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest(PartnerRelation.MOTHER))

        val mother = signUp().also { onboard(it) }
        val stale = client.post("/v1/partner/accept") { auth(mother.token); json(AcceptPartnerInviteRequest(first.code!!)) }
        assertEquals(HttpStatusCode.NotFound, stale.status, "the older code stopped")
        val own = client.post("/v1/partner/accept") { auth(her.token); json(AcceptPartnerInviteRequest(second.code!!)) }
        assertEquals(HttpStatusCode.Conflict, own.status)
        val malformed = client.post("/v1/partner/accept") { auth(mother.token); json(AcceptPartnerInviteRequest("ABC")) }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)

        // A woman who tracks herself can follow her daughter and stays who she is.
        val followed = post<FollowedPerson>("/v1/partner/accept", mother.token, AcceptPartnerInviteRequest(second.code!!, asPartnerAccount = true))
        assertEquals(PartnerRelation.MOTHER, followed.relation)
        assertEquals(AccountKind.SELF, get<UserProfile>("/v1/me", mother.token).accountKind)

        val sister = signUp().also { onboard(it) }
        val used = client.post("/v1/partner/accept") { auth(sister.token); json(AcceptPartnerInviteRequest(second.code!!)) }
        assertEquals(HttpStatusCode.NotFound, used.status, "a used code opens nothing")

        // One person at a time: a new code while one is linked is refused, not swapped.
        val busy = client.post("/v1/partner/invite") { auth(her.token); json(CreatePartnerInviteRequest()) }
        assertEquals(HttpStatusCode.Conflict, busy.status)

        // He may leave on his own; her settings then show nobody.
        val left = client.delete("/v1/partner/following/${followed.linkId}") { auth(mother.token) }
        assertEquals(HttpStatusCode.OK, left.status)
        assertNull(get<PartnerState>("/v1/partner", her.token).link)

        // The page behind the link echoes the code and looks nothing up.
        val page = client.get("/y/${second.code!!.replace("-", "")}?lang=ru")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains(second.code!!))
        assertFalse(client.get("/y/%3Cscript%3E").bodyAsText().contains("<script>"))
    }

    @Test
    fun `her person hears that the period started, of a visit tomorrow, and when labour starts`() = api {
        val her = signUp().also { onboard(it) }
        val him = signUp()
        val code = post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest()).code!!
        post<FollowedPerson>("/v1/partner/accept", him.token, AcceptPartnerInviteRequest(code, name = "Aziz", asPartnerAccount = true))
        post<PartnerState>("/v1/partner/approve", her.token, Ack())
        assertEquals(1, outboxCount(him, "partner_approved:"), "he is told she said yes")

        val today = get<CycleStatus>("/v1/cycle/status", her.token).today
        post<PeriodEntry>("/v1/cycle/periods", her.token, LogPeriodRequest(today))
        assertEquals(1, outboxCount(him, "partner_period:"))

        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        post<Appointment>("/v1/appointments", her.token, SaveAppointmentRequest("UZI", tomorrow, LocalTime(10, 30)))
        // Ten in the morning in Tashkent, so the pass is inside his daytime whenever this runs.
        val morning = kotlinx.datetime.LocalDateTime(today, LocalTime(10, 0)).toInstant(kotlinx.datetime.TimeZone.of("Asia/Tashkent"))
        component.partnerService.dailyAlerts(morning)
        component.partnerService.dailyAlerts(morning)
        assertEquals(1, outboxCount(him, "partner_appointment:"), "the repeat pass sends nothing new")

        val labour = client.post("/v1/partner/alert/labour") { auth(her.token) }
        assertEquals(HttpStatusCode.OK, labour.status)
        assertEquals(1, outboxCount(him, "partner_labour:"))

        // With the appointments part off, the next visit is not his news.
        put<PartnerState>("/v1/partner/permissions", her.token, PartnerPermissions(appointments = false))
        post<Appointment>("/v1/appointments", her.token, SaveAppointmentRequest("Qon tahlili", tomorrow))
        component.partnerService.dailyAlerts(morning)
        assertEquals(1, outboxCount(him, "partner_appointment:"))
    }

    /**
     * Stage three: a heart from him, a request from her and his answer, read marks and
     * the unread counts both lists carry; nothing on a paused link, nothing for a stranger.
     */
    @Test
    fun `her person and she send each other small messages only while the link is active`() = api {
        val her = signUp().also { onboard(it) }
        val him = signUp()
        val stranger = signUp().also { onboard(it) }
        val code = post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest()).code!!
        val link = post<FollowedPerson>("/v1/partner/accept", him.token, AcceptPartnerInviteRequest(code, name = "Aziz", asPartnerAccount = true)).linkId
        val path = "/v1/partner/links/$link/messages"

        // Waiting for her yes: nothing can be sent yet.
        val early = client.post(path) { auth(him.token); json(SendPartnerMessageRequest(PartnerMessageKind.HEART)) }
        assertEquals(HttpStatusCode.Conflict, early.status)
        post<PartnerState>("/v1/partner/approve", her.token, Ack())

        post<PartnerMessage>(path, him.token, SendPartnerMessageRequest(PartnerMessageKind.HEART))
        assertEquals(1, get<PartnerState>("/v1/partner", her.token).link?.unread)
        assertEquals(1, outboxCount(her, "partner_msg:"))
        // Delivered at once, not on the minute's tick: the row leaves the queue within
        // moments (a test account has no push token, so it ends as no_device).
        val deadline = now() + 5.seconds
        while (queuedCount(her, "partner_msg:") > 0 && now() < deadline) kotlinx.coroutines.delay(100)
        assertEquals(0, queuedCount(her, "partner_msg:"), "the message waited for the scheduler")

        val request = post<PartnerMessage>(path, her.token, SendPartnerMessageRequest(PartnerMessageKind.TEA, text = "ignored"))
        assertNull(request.text, "a preset carries no text")
        assertTrue(request.fromMe)
        assertEquals(1, get<PartnerState>("/v1/partner", him.token).following.single().unread)

        val read = post<PartnerMessages>("$path/read", him.token, Ack())
        assertEquals(0, read.unread)
        assertEquals(listOf(PartnerMessageKind.TEA, PartnerMessageKind.HEART), read.items.map { it.kind }, "newest first")
        assertFalse(read.items.first().fromMe)
        post<PartnerMessage>(path, him.token, SendPartnerMessageRequest(PartnerMessageKind.CUSTOM, text = "  Hozir  olib   kelaman "))
        assertEquals("Hozir olib kelaman", get<PartnerMessages>(path, her.token).items.first().text)

        val blank = client.post(path) { auth(him.token); json(SendPartnerMessageRequest(PartnerMessageKind.CUSTOM, text = "  ")) }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        val tooLong = client.post(path) { auth(him.token); json(SendPartnerMessageRequest(PartnerMessageKind.CUSTOM, text = "x".repeat(201))) }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertEquals(HttpStatusCode.NotFound, client.get(path) { auth(stranger.token) }.status)

        post<PartnerState>("/v1/partner/pause", her.token, PausePartnerRequest(paused = true))
        val paused = client.post(path) { auth(him.token); json(SendPartnerMessageRequest(PartnerMessageKind.HUG)) }
        assertEquals(HttpStatusCode.Conflict, paused.status)
        client.delete("/v1/partner") { auth(her.token) }
        assertEquals(HttpStatusCode.NotFound, client.get(path) { auth(him.token) }.status, "an ended link's messages are gone")
    }

    @Test
    fun `a web link shows her chosen parts in a browser until it expires or she takes it back`() = api {
        val her = signUp()
        onboard(her, mood = MoodLevel.LOW, symptoms = listOf("headache"))
        val today = get<CycleStatus>("/v1/cycle/status", her.token).today
        post<PeriodEntry>("/v1/cycle/periods", her.token, LogPeriodRequest(today.minus(3, DateTimeUnit.DAY)))

        val tooLong = client.post("/v1/partner/web") { auth(her.token); json(CreatePartnerWebLinkRequest(ttlHours = 24 * 30)) }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        val first = post<PartnerWebLink>("/v1/partner/web", her.token, CreatePartnerWebLinkRequest(ttlHours = 24, permissions = PartnerPermissions(mood = false)))
        val url = assertNotNull(first.url)
        assertTrue(url.contains("/yv/"), url)
        assertNull(get<PartnerState>("/v1/partner", her.token).webLink?.url, "a later read does not show the link")

        val page = client.get("/yv/${url.substringAfterLast('/')}?lang=en")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("Period day 4"), html)
        assertFalse(html.contains("headache") || html.contains("Mood"), "mood was not ticked")
        assertEquals("no-store", page.headers["Cache-Control"])
        assertEquals(1, get<PartnerState>("/v1/partner", her.token).webLink?.viewCount)

        // A new link retires the old one; taking it back closes the new one too.
        val second = post<PartnerWebLink>("/v1/partner/web", her.token, CreatePartnerWebLinkRequest(ttlHours = 72))
        assertEquals(HttpStatusCode.NotFound, client.get("/yv/${url.substringAfterLast('/')}").status)
        client.delete("/v1/partner/web") { auth(her.token) }
        assertEquals(HttpStatusCode.NotFound, client.get("/yv/${second.url!!.substringAfterLast('/')}").status)
        assertNull(get<PartnerState>("/v1/partner", her.token).webLink)
        assertEquals(HttpStatusCode.NotFound, client.get("/yv/not-a-token").status)
    }

    /** A chat line rings the other phone at once; nothing waits for the minute's tick. */
    @Test
    fun `a direct message is delivered the moment it is written`() = api {
        val a = signUp().also { onboard(it) }
        val b = signUp().also { onboard(it) }
        val alias = get<CommunityIdentity>("/v1/community/me", b.token).alias
        val thread = post<ConversationThread>("/v1/community/conversations", a.token, StartConversationRequest(alias, "Salom"))
        assertNotNull(thread.conversation.id)
        assertEquals(1, outboxCount(b, "dm:"))
        val deadline = now() + 5.seconds
        while (queuedCount(b, "dm:") > 0 && now() < deadline) kotlinx.coroutines.delay(100)
        assertEquals(0, queuedCount(b, "dm:"), "the message waited for the scheduler")
    }

    // ---------------------------------------------------------------- wearable connections

    /**
     * WHOOP is unconfigured in the suite, and that has to be a first-class answer: the
     * provider is listed with a reason, and a connect attempt is a 503 rather than an
     * OAuth flow with nowhere to return to.
     */
    @Test
    fun `an unconfigured cloud wearable is listed as such and refuses to connect`() = api {
        val user = signUp()
        onboard(user)

        val providers = get<List<ProviderInfo>>("/v1/wearables/providers", user.token)
        val whoop = assertNotNull(providers.firstOrNull { it.provider == HealthProvider.WHOOP })
        assertFalse(whoop.available)
        assertEquals(ProviderUnavailable.NOT_CONFIGURED, whoop.unavailableReason)
        assertTrue(whoop.metrics.contains(HealthMetric.RECOVERY))
        assertEquals(HealthProvider.entries.size - 1, providers.size, "every provider but manual is listed")

        // The phone's stores need nothing configured on the server: the phone reads them.
        listOf(HealthProvider.APPLE_HEALTH, HealthProvider.HEALTH_CONNECT).forEach { provider ->
            val store = assertNotNull(providers.firstOrNull { it.provider == provider })
            assertTrue(store.available, "$provider is offered")
            assertEquals(uz.sadora.contract.ProviderKind.ON_DEVICE, store.kind)
            assertTrue(store.metrics.contains(HealthMetric.SKIN_TEMPERATURE))
        }

        val connect = client.post("/v1/wearables/whoop/connect") { auth(user.token) }
        assertEquals(HttpStatusCode.ServiceUnavailable, connect.status)

        // The provider's doors answer without a token, and refuse what is not theirs.
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/wearables/whoop/callback?error=access_denied").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/v1/wearables/whoop/webhook") { setBody("{}") }.status)

        assertTrue(get<List<WearableConnection>>("/v1/wearables/connections", user.token).isEmpty())
    }

    /**
     * The names the phone readers send beyond the first set — sleep stages, oxygen,
     * wrist and skin temperature — each have a mapping row, so none comes back unmapped.
     */
    @Test
    fun `the phone readers' sleep stages, oxygen and temperatures are all mapped`() = api {
        val user = signUp()
        onboard(user)
        val at = kotlin.time.Clock.System.now() - kotlin.time.Duration.parse("2h")
        fun sample(provider: HealthProvider, metric: String, value: Double) = uz.sadora.contract.HealthSampleInput(
            provider = provider,
            externalId = "test:$metric",
            metric = metric,
            value = value,
            startedAt = at,
        )

        val result = post<uz.sadora.contract.IngestResult>(
            "/v1/health-data/samples",
            user.token,
            uz.sadora.contract.IngestSamplesRequest(
                samples = listOf(
                    sample(HealthProvider.HEALTH_CONNECT, "SkinTemperature", 33.9),
                    sample(HealthProvider.HEALTH_CONNECT, "OxygenSaturation", 97.0),
                    sample(HealthProvider.HEALTH_CONNECT, "SleepDeep", 3_600.0),
                    sample(HealthProvider.HEALTH_CONNECT, "BasalBodyTemperature", 36.4),
                    sample(HealthProvider.APPLE_HEALTH, "HKQuantityTypeIdentifierAppleSleepingWristTemperature", 34.1),
                    sample(HealthProvider.APPLE_HEALTH, "HKQuantityTypeIdentifierOxygenSaturation", 98.0),
                    sample(HealthProvider.APPLE_HEALTH, "HKCategoryValueSleepAnalysisAsleepREM", 5_400.0),
                ),
            ),
        )

        assertTrue(result.unmapped.isEmpty(), "unmapped: ${result.unmapped}")
        assertEquals(7, result.accepted + result.updated + result.rejected)
    }

    // ---------------------------------------------------------------- Gul

    /**
     * The whole daily loop in one pass.
     *
     * What it pins is the idempotence: a launch is a `POST`, phones launch the app many
     * times a day, and only the first one of a day may pay or celebrate. Without that
     * the balance would grow with every return from the camera.
     */
    @Test
    fun `the first open of a day pays and celebrates, the second does neither`() = api {
        val user = signUp()
        onboard(user)

        val first = post<DailyCheckInResult>("/v1/rewards/check-in", user.token, Unit)
        assertTrue(first.celebrate, "the first open of a day is worth a celebration")
        assertEquals(1, first.streak.current)
        assertTrue(first.streak.openedToday)
        val earned = first.coins.balance
        assertTrue(earned > 0, "the daily rule pays something: $earned")
        assertTrue(first.awards.any { it.reason == CoinReasons.DAILY_OPEN })

        val second = post<DailyCheckInResult>("/v1/rewards/check-in", user.token, Unit)
        assertFalse(second.celebrate, "the same day again is not a new day")
        assertTrue(second.awards.isEmpty())
        assertEquals(earned, second.coins.balance, "nothing was paid twice")
    }

    /**
     * The ledger is the balance's explanation, so the wallet has to be able to show one
     * line per coin. A summary with a balance and an empty history would be a number the
     * app is asking her to take on trust.
     */
    @Test
    fun `the wallet explains its balance and lists what each action pays`() = api {
        val user = signUp()
        onboard(user)
        post<DailyCheckInResult>("/v1/rewards/check-in", user.token, Unit)

        val summary = get<RewardsSummary>("/v1/rewards", user.token)
        assertEquals(summary.coins.balance, summary.history.sumOf { it.amount })
        assertTrue(summary.history.any { it.reason == CoinReasons.DAILY_OPEN })
        assertTrue(summary.earnRates.any { it.reason == CoinReasons.DAILY_OPEN && it.amount > 0 })
        // Every row is worded by the server in her language; a blank title would leave
        // the wallet showing an internal key.
        assertTrue(summary.history.all { it.title.isNotBlank() })
    }

    /** Logging pays, and the cap in the rules is what stops it paying forever. */
    @Test
    fun `reaching the water goal pays once, and drinking more pays nothing further`() = api {
        val user = signUp()
        onboard(user)
        val goal = get<NutritionGoals>("/v1/nutrition/goals", user.token).waterGoalMl

        post<WaterState>("/v1/nutrition/water", user.token, AddWaterRequest(goal))
        val afterGoal = get<CoinBalance>("/v1/rewards", user.token).let {
            get<RewardsSummary>("/v1/rewards", user.token).coins
        }
        assertTrue(
            afterGoal.balance > 0,
            "the water goal pays: ${afterGoal.balance}",
        )

        post<WaterState>("/v1/nutrition/water", user.token, AddWaterRequest(250))
        val afterMore = get<RewardsSummary>("/v1/rewards", user.token).coins
        assertEquals(afterGoal.balance, afterMore.balance, "the goal is reached once a day, not per glass")
    }

    /**
     * A counted award stays counted when the caller names the thing being paid for. The
     * meal id kept a retry from paying twice; it used to lift the daily ceiling as well,
     * and every extra meal paid until the wallet bought Premium.
     */
    @Test
    fun `logging meals pays up to the daily cap and not a coin beyond it`() = api {
        val user = signUp()
        onboard(user)
        val rate = get<RewardsSummary>("/v1/rewards", user.token).earnRates
            .first { it.reason == CoinReasons.MEAL_LOGGED }
        val cap = assertNotNull(rate.dailyCap, "the seed caps meals per day")
        val today = LocalDate.parse(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tashkent")).toString())

        repeat(cap + 2) { index ->
            post<uz.sadora.contract.Meal>(
                "/v1/nutrition/meals",
                user.token,
                uz.sadora.contract.LogMealRequest(
                    date = today,
                    slot = uz.sadora.contract.MealSlot.SNACK,
                    description = "Meal $index",
                    kcal = 100,
                ),
            )
        }

        val paid = get<RewardsSummary>("/v1/rewards", user.token).history
            .count { it.reason == CoinReasons.MEAL_LOGGED }
        assertEquals(cap, paid, "the cap holds however many meals are logged")
    }

    /**
     * A badge is awarded by reading the board: the first day is a tier at once, a tier is
     * paid in Gul exactly once however often the board is read, and it stays "unseen"
     * until the app says the animation played.
     */
    @Test
    fun `the badge board awards a crossed tier once and holds it until it is seen`() = api {
        val user = signUp()
        onboard(user)
        post<DailyCheckInResult>("/v1/rewards/check-in", user.token, Unit)

        val first = get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", user.token)
        assertEquals(uz.sadora.contract.Badges.catalogue.size, first.badges.size)
        val step = first.badges.first { it.key == uz.sadora.contract.Badges.FIRST_STEP }
        assertEquals(1, step.tier, "the first day is the first step")
        assertTrue(first.unseen.any { it.key == uz.sadora.contract.Badges.FIRST_STEP && it.coins > 0 })
        assertTrue(first.badges.first { it.key == uz.sadora.contract.Badges.MEALS }.tier == 0)

        // Read again: nothing new is written or paid.
        val again = get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", user.token)
        assertEquals(first.unseen.size, again.unseen.size)
        assertEquals(1, countCoins(user, CoinReasons.BADGE_EARNED))

        // A list of unknown keys must not mark everything seen.
        post<uz.sadora.contract.Ack>("/v1/rewards/badges/seen", user.token, uz.sadora.contract.MarkBadgesSeenRequest(listOf("nope")))
        assertTrue(get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", user.token).unseen.isNotEmpty())

        post<uz.sadora.contract.Ack>("/v1/rewards/badges/seen", user.token, uz.sadora.contract.MarkBadgesSeenRequest())
        val seen = get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", user.token)
        assertTrue(seen.unseen.isEmpty(), "every unlock was played")
        assertEquals(1, seen.badges.first { it.key == uz.sadora.contract.Badges.FIRST_STEP }.tier)
    }

    /**
     * The badge she wears follows her alias: on her identity, her posts and comments as
     * others read them, and her profile — and only a badge she has actually earned.
     */
    @Test
    fun `a worn badge rides after her alias, and a locked one cannot be worn`() = api {
        val author = signUp().also { onboard(it) }
        val reader = signUp().also { onboard(it) }
        post<DailyCheckInResult>("/v1/rewards/check-in", author.token, Unit)
        val free = get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", author.token)
        assertFalse(free.canWear, "wearing is Premium")
        val refused = raw {
            client.put("/v1/rewards/badges/worn") {
                auth(author.token); json(uz.sadora.contract.WearBadgeRequest(uz.sadora.contract.Badges.FIRST_STEP))
            }
        }
        assertEquals(HttpStatusCode.PaymentRequired, refused.status, "a free account cannot wear one")

        postAck(
            "/v1/admin/users/${author.userId}/premium",
            adminToken(),
            uz.sadora.server.admin.GrantPremiumRequest(reason = "worn badge test"),
        )
        assertTrue(get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", author.token).canWear)

        val locked = raw {
            client.put("/v1/rewards/badges/worn") {
                auth(author.token); json(uz.sadora.contract.WearBadgeRequest(uz.sadora.contract.Badges.MEALS))
            }
        }
        assertEquals(HttpStatusCode.BadRequest, locked.status, "a locked badge is refused")

        val board = put<uz.sadora.contract.BadgeBoard>(
            "/v1/rewards/badges/worn", author.token, uz.sadora.contract.WearBadgeRequest(uz.sadora.contract.Badges.FIRST_STEP),
        )
        assertEquals(uz.sadora.contract.Badges.FIRST_STEP, board.worn)
        val expected = uz.sadora.contract.WornBadge(uz.sadora.contract.Badges.FIRST_STEP, 1, 1)
        assertEquals(expected, get<CommunityIdentity>("/v1/community/me", author.token).worn)

        val created = post<CommunityPost>("/v1/community/posts", author.token, CreatePostRequest(CommunityTopic.CYCLE, "Nishon test ${Uuid.random()}"))
        assertEquals(expected, created.worn)
        val seen = get<CommunityPost>("/v1/community/posts/${created.id}", reader.token)
        assertEquals(expected, seen.worn, "others see it on her post")
        val comment = post<CommunityComment>("/v1/community/posts/${created.id}/comments", author.token, CreateCommentRequest("izoh"))
        assertEquals(expected, comment.worn)
        assertEquals(expected, get<uz.sadora.contract.CommunityProfile>("/v1/community/profiles/${created.alias.encodeURLPathPart()}", reader.token).worn)

        put<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges/worn", author.token, uz.sadora.contract.WearBadgeRequest(null))
        assertNull(get<CommunityPost>("/v1/community/posts/${created.id}", reader.token).worn, "taken off, gone everywhere")
    }

    /**
     * The pet: anyone may pick one, only Premium hears it, and it keeps quiet inside its
     * cooldowns — except for a low mood, which is answered at once with the journal.
     */
    @Test
    fun `the pet speaks only for Premium and keeps quiet inside its cooldown`() = api {
        val user = signUp().also { onboard(it) }
        val initial = get<uz.sadora.contract.PetState>("/v1/pet", user.token)
        assertEquals(uz.sadora.contract.PetKind.NILUFAR, initial.pet, "the lotus is the default")
        assertFalse(initial.active)
        val chosen = put<uz.sadora.contract.PetState>(
            "/v1/pet", user.token, uz.sadora.contract.ChoosePetRequest(uz.sadora.contract.PetKind.LAYLO),
        )
        assertEquals(uz.sadora.contract.PetKind.LAYLO, chosen.pet, "picking is free")

        val water = uz.sadora.contract.PetNudgeRequest(uz.sadora.contract.PetTrigger.WATER_GOAL)
        val refused = raw { client.post("/v1/pet/nudge") { auth(user.token); json(water) } }
        assertEquals(HttpStatusCode.PaymentRequired, refused.status, "a free account gets no tips")

        postAck("/v1/admin/users/${user.userId}/premium", adminToken(), uz.sadora.server.admin.GrantPremiumRequest(reason = "pet test"))
        assertTrue(get<uz.sadora.contract.PetState>("/v1/pet", user.token).active)

        val first = assertNotNull(post<uz.sadora.contract.PetNudgeAnswer>("/v1/pet/nudge", user.token, water).nudge)
        assertEquals(uz.sadora.contract.PetKind.LAYLO, first.pet)
        assertEquals(uz.sadora.contract.PetPose.HAPPY, first.pose)
        assertTrue(first.text.isNotBlank())
        assertNull(post<uz.sadora.contract.PetNudgeAnswer>("/v1/pet/nudge", user.token, water).nudge, "once a day")
        val streak = uz.sadora.contract.PetNudgeRequest(uz.sadora.contract.PetTrigger.STREAK_KEPT)
        assertNull(post<uz.sadora.contract.PetNudgeAnswer>("/v1/pet/nudge", user.token, streak).nudge, "inside the gap")

        val low = uz.sadora.contract.PetNudgeRequest(uz.sadora.contract.PetTrigger.MOOD_LOW)
        val gentle = assertNotNull(post<uz.sadora.contract.PetNudgeAnswer>("/v1/pet/nudge", user.token, low).nudge, "a low mood never waits")
        assertEquals(uz.sadora.contract.PetAction.MIND_JOURNAL, gentle.action)
    }

    /**
     * Humo, the legendary pet: hidden until its flag is on, then on sale but never picked
     * for free. A store receipt buys it once, for the account it was bought for; the pet is
     * hers from then on, and speaks in her chosen voice when she has Premium.
     */
    @Test
    fun `Humo is bought once with a store receipt and only then can be chosen`() = api {
        val admin = adminToken()
        val buyer = signUp().also { onboard(it) }
        val other = signUp().also { onboard(it) }
        val humo = uz.sadora.contract.PetKind.HUMO

        setFlag(admin, uz.sadora.server.pet.PetShopService.SALE_FLAG, enabled = false)
        val hidden = get<uz.sadora.contract.PetState>("/v1/pet", buyer.token)
        assertFalse(humo in hidden.available, "not on sale: not shown")
        assertTrue(hidden.shop.isEmpty())
        assertFalse(hidden.offerDue)

        setFlag(admin, uz.sadora.server.pet.PetShopService.SALE_FLAG, enabled = true)
        val onSale = get<uz.sadora.contract.PetState>("/v1/pet", buyer.token)
        assertTrue(humo in onSale.available)
        val product = onSale.shop.single()
        assertEquals(humo, product.pet)
        assertEquals(49_900_000, product.priceMinor, "499 000 so'm")
        assertEquals("uz.sadora.pet.humo", product.googlePlayProductId)
        val refused = raw { client.put("/v1/pet") { auth(buyer.token); json(uz.sadora.contract.ChoosePetRequest(humo)) } }
        assertEquals(HttpStatusCode.Forbidden, refused.status, "a legendary pet is never picked for free")

        put<uz.sadora.contract.PetState>("/v1/pet", buyer.token, uz.sadora.contract.ChoosePetRequest(uz.sadora.contract.PetKind.LAYLO))
        val verifier = uz.sadora.server.billing.StoreVerifier { _, productId, _ ->
            uz.sadora.server.billing.VerifiedPurchase(
                productId = productId,
                transactionId = "GPA.humo-${buyer.userId}",
                expiresAt = null,
                autoRenewing = false,
                accountId = buyer.userId,
            )
        }
        val shop = component.petShopService.verifiedBy(verifier)
        val receipt = uz.sadora.contract.PetStorePurchase(humo, PaymentProvider.GOOGLE_PLAY, "uz.sadora.pet.humo", "token")
        kotlin.test.assertFailsWith<uz.sadora.server.core.ValidationException> {
            shop.buyInStore(Uuid.parse(other.userId), receipt)
        }
        assertTrue(get<uz.sadora.contract.PetState>("/v1/pet", other.token).owned.isEmpty())

        assertEquals(listOf(humo), shop.buyInStore(Uuid.parse(buyer.userId), receipt))
        val bought = get<uz.sadora.contract.PetState>("/v1/pet", buyer.token)
        assertEquals(listOf(humo), bought.owned)
        assertEquals(humo, bought.pet, "a pet she just bought comes to her at once")
        assertTrue(bought.shop.isEmpty(), "nothing left to sell her")
        shop.buyInStore(Uuid.parse(buyer.userId), receipt)
        assertEquals(1, countRowsFor(Uuid.parse(buyer.userId), "payment_transactions"), "a replayed receipt buys nothing twice")

        // Hers even with the sale switched off again; it speaks once she has Premium.
        setFlag(admin, uz.sadora.server.pet.PetShopService.SALE_FLAG, enabled = false)
        assertTrue(humo in get<uz.sadora.contract.PetState>("/v1/pet", buyer.token).available)
        postAck("/v1/admin/users/${buyer.userId}/premium", admin, uz.sadora.server.admin.GrantPremiumRequest(reason = "humo test"))
        val water = uz.sadora.contract.PetNudgeRequest(uz.sadora.contract.PetTrigger.WATER_GOAL)
        assertEquals(humo, assertNotNull(post<uz.sadora.contract.PetNudgeAnswer>("/v1/pet/nudge", buyer.token, water).nudge).pet)
    }

    /**
     * Humo by Payme: the payment row carries the pet, Payme's double delivery grants it
     * once, and the panel's refund takes it back — she is returned to the pet she had.
     */
    @Test
    fun `Humo paid with Payme is hers, and a refund takes it back`() = api {
        val admin = adminToken()
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)
        setFlag(admin, uz.sadora.server.pet.PetShopService.SALE_FLAG, enabled = true)
        val user = signUp().also { onboard(it) }
        put<uz.sadora.contract.PetState>("/v1/pet", user.token, uz.sadora.contract.ChoosePetRequest(uz.sadora.contract.PetKind.OHU))

        val checkout = post<CheckoutSession>(
            "/v1/pet/checkout",
            user.token,
            uz.sadora.contract.PetCheckoutRequest(uz.sadora.contract.PetKind.HUMO, PaymentProvider.PAYME),
        )
        assertEquals(49_900_000, checkout.amountMinor)
        val paymeId = "pm-humo-${Random.nextInt(1_000_000)}"
        payme(
            """{"id":2,"method":"CreateTransaction","params":{"id":"$paymeId","time":1788600000000,""" +
                """"amount":49900000,"account":{"order_id":"${checkout.transactionId}"}}}""",
        )
        repeat(2) { payme("""{"id":4,"method":"PerformTransaction","params":{"id":"$paymeId"}}""") }
        val owned = get<uz.sadora.contract.PetState>("/v1/pet", user.token)
        assertEquals(listOf(uz.sadora.contract.PetKind.HUMO), owned.owned)
        assertEquals(uz.sadora.contract.PetKind.HUMO, owned.pet)

        val row = get<Page<uz.sadora.server.billing.AdminPaymentView>>("/v1/admin/billing/payments?limit=200", admin)
            .items.single { it.id == checkout.transactionId }
        assertEquals("humo", row.pet)
        assertTrue(row.refundable)
        postAck("/v1/admin/billing/payments/${checkout.transactionId}/refund", admin, Ack())
        val refunded = get<uz.sadora.contract.PetState>("/v1/pet", user.token)
        assertTrue(refunded.owned.isEmpty())
        assertEquals(uz.sadora.contract.PetKind.OHU, refunded.pet, "back to the pet she had before")
    }

    /** Yaqinim can give her Humo: she asks, he pays by Payme, the pet is hers and not his. */
    @Test
    fun `Yaqinim can pay for Humo as a present`() = api {
        val admin = adminToken()
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)
        setFlag(admin, uz.sadora.server.pet.PetShopService.SALE_FLAG, enabled = true)
        val her = signUp().also { onboard(it) }
        val code = assertNotNull(post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest()).code)
        val him = signUp()
        post<FollowedPerson>("/v1/partner/accept", him.token, AcceptPartnerInviteRequest(code, name = "Aziz", asPartnerAccount = true))
        post<PartnerState>("/v1/partner/approve", her.token, Ack())

        val asked = post<uz.sadora.contract.PaymentRequest>(
            "/v1/payment-requests",
            her.token,
            uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PET, pet = uz.sadora.contract.PetKind.HUMO),
        )
        assertEquals(49_900_000, asked.amountMinor)
        assertEquals(uz.sadora.contract.PetKind.HUMO, asked.pet)
        val incoming = get<List<uz.sadora.contract.IncomingPaymentRequest>>("/v1/payment-requests/incoming", him.token).single()
        assertEquals(uz.sadora.contract.PetKind.HUMO, incoming.pet)

        val checkout = post<CheckoutSession>(
            "/v1/payment-requests/${incoming.id}/checkout",
            him.token,
            uz.sadora.contract.PayPaymentRequest(PaymentProvider.PAYME),
        )
        val paymeId = "pm-hg-${Random.nextInt(1_000_000)}"
        payme(
            """{"id":2,"method":"CreateTransaction","params":{"id":"$paymeId","time":1788600000000,""" +
                """"amount":49900000,"account":{"order_id":"${checkout.transactionId}"}}}""",
        )
        payme("""{"id":4,"method":"PerformTransaction","params":{"id":"$paymeId"}}""")
        assertEquals(listOf(uz.sadora.contract.PetKind.HUMO), get<uz.sadora.contract.PetState>("/v1/pet", her.token).owned)
        assertTrue(get<uz.sadora.contract.PetState>("/v1/pet", him.token).owned.isEmpty(), "the payer gets nothing")
        assertEquals(uz.sadora.contract.PaymentRequestStatus.PAID, get<uz.sadora.contract.PaymentRequestState>("/v1/payment-requests", her.token).current?.status)
    }

    /** She asked Yaqinim for Humo, then bought it herself: the request closes, so he cannot pay twice. */
    @Test
    fun `buying Humo herself closes the request she sent for it`() = api {
        val admin = adminToken()
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)
        setFlag(admin, uz.sadora.server.pet.PetShopService.SALE_FLAG, enabled = true)
        val her = signUp().also { onboard(it) }
        val code = assertNotNull(post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest()).code)
        val him = signUp()
        post<FollowedPerson>("/v1/partner/accept", him.token, AcceptPartnerInviteRequest(code, name = "Aziz", asPartnerAccount = true))
        post<PartnerState>("/v1/partner/approve", her.token, Ack())
        val asked = post<uz.sadora.contract.PaymentRequest>(
            "/v1/payment-requests",
            her.token,
            uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PET, pet = uz.sadora.contract.PetKind.HUMO),
        )

        val own = post<CheckoutSession>(
            "/v1/pet/checkout",
            her.token,
            uz.sadora.contract.PetCheckoutRequest(uz.sadora.contract.PetKind.HUMO, PaymentProvider.PAYME),
        )
        val paymeId = "pm-hs-${Random.nextInt(1_000_000)}"
        payme(
            """{"id":2,"method":"CreateTransaction","params":{"id":"$paymeId","time":1788600000000,""" +
                """"amount":49900000,"account":{"order_id":"${own.transactionId}"}}}""",
        )
        payme("""{"id":4,"method":"PerformTransaction","params":{"id":"$paymeId"}}""")

        assertEquals(uz.sadora.contract.PaymentRequestStatus.CANCELLED, get<uz.sadora.contract.PaymentRequestState>("/v1/payment-requests", her.token).current?.status)
        assertTrue(get<List<uz.sadora.contract.IncomingPaymentRequest>>("/v1/payment-requests/incoming", him.token).isEmpty())
        val late = raw {
            client.post("/v1/payment-requests/${asked.id}/checkout") { auth(him.token); json(uz.sadora.contract.PayPaymentRequest(PaymentProvider.PAYME)) }
        }
        assertEquals(HttpStatusCode.Conflict, late.status)
    }

    /** The one-off offer: due once she has Premium (or a 30-day streak), gone once seen or bought. */
    @Test
    fun `Humo is offered once a milestone is reached and never again after she has seen it`() = api {
        val admin = adminToken()
        setFlag(admin, uz.sadora.server.pet.PetShopService.SALE_FLAG, enabled = true)
        val user = signUp().also { onboard(it) }
        assertFalse(get<uz.sadora.contract.PetState>("/v1/pet", user.token).offerDue, "no milestone yet")

        postAck("/v1/admin/users/${user.userId}/premium", admin, uz.sadora.server.admin.GrantPremiumRequest(reason = "offer test"))
        assertTrue(get<uz.sadora.contract.PetState>("/v1/pet", user.token).offerDue, "the day Premium starts")
        val seen = post<uz.sadora.contract.PetState>("/v1/pet/offer/seen", user.token, Ack())
        assertFalse(seen.offerDue)
        assertFalse(get<uz.sadora.contract.PetState>("/v1/pet", user.token).offerDue, "once, ever")

        // The panel sets the price; the app reads it from the server.
        val row = get<List<uz.sadora.server.pet.AdminPetProduct>>("/v1/admin/billing/pet-products", admin).single()
        assertEquals(49_900_000, row.priceMinor)
        put<Ack>("/v1/admin/billing/pet-products/humo", admin, uz.sadora.server.pet.UpdatePetProductRequest(45_000_000))
        assertEquals(45_000_000, get<uz.sadora.contract.PetState>("/v1/pet", user.token).shop.single().priceMinor)
        put<Ack>("/v1/admin/billing/pet-products/humo", admin, uz.sadora.server.pet.UpdatePetProductRequest(49_900_000))
    }

    /** Ten meals is the bronze of "mindful eater", and the progress counts up to it. */
    @Test
    fun `logging meals moves the meals badge to its first tier`() = api {
        val user = signUp()
        onboard(user)
        val today = LocalDate.parse(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tashkent")).toString())
        val first = uz.sadora.contract.Badges.tiersOf(uz.sadora.contract.Badges.MEALS).first()
        repeat(first) { index ->
            post<uz.sadora.contract.Meal>(
                "/v1/nutrition/meals",
                user.token,
                uz.sadora.contract.LogMealRequest(date = today, slot = uz.sadora.contract.MealSlot.SNACK, description = "Meal $index", kcal = 50),
            )
        }
        val board = get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", user.token)
        val meals = board.badges.first { it.key == uz.sadora.contract.Badges.MEALS }
        assertEquals(first, meals.progress)
        assertEquals(1, meals.tier)
        assertTrue(board.unseen.any { it.key == uz.sadora.contract.Badges.MEALS && it.tier == 1 })
    }

    /**
     * Blocking revokes the refresh tokens, but the access token already on the phone
     * lives for fifteen minutes. The account is refused on its very next request, with
     * the same code sign-in gives, and let back in the moment the block is lifted.
     */
    @Test
    fun `a blocked account is refused on its next request, not when its token expires`() = api {
        val user = signUp()
        onboard(user)
        val admin = adminToken()

        val blocked = client.post("/v1/admin/users/${user.userId}/block") {
            auth(admin)
            json(uz.sadora.server.admin.BlockUserRequest(blocked = true, reason = "integration test"))
        }
        assertEquals(HttpStatusCode.OK, blocked.status, blocked.bodyAsTextSafe())

        val refused = client.get("/v1/me") { auth(user.token) }
        assertEquals(HttpStatusCode.Forbidden, refused.status, refused.bodyAsTextSafe())
        assertTrue(refused.bodyAsTextSafe().contains("account_blocked"))
        val today = LocalDate.parse(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tashkent")).toString())
        val refusedWrite = client.put("/v1/days/$today") {
            auth(user.token)
            json(uz.sadora.contract.SaveDailyLogRequest(mood = MoodLevel.OK))
        }
        assertEquals(HttpStatusCode.Forbidden, refusedWrite.status)

        client.post("/v1/admin/users/${user.userId}/block") {
            auth(admin)
            json(uz.sadora.server.admin.BlockUserRequest(blocked = false, reason = "test over"))
        }
        assertEquals(HttpStatusCode.OK, client.get("/v1/me") { auth(user.token) }.status)
    }

    /**
     * The invite pays both sides exactly once, and never for a code somebody typed at
     * their own account. A referral scheme that can be pointed at itself is a mint.
     */
    @Test
    fun `an invite code pays the inviter and the invited, and never the same person twice`() = api {
        val inviter = signUp()
        onboard(inviter)
        val referral = get<ReferralStatus>("/v1/rewards/referral", inviter.token)
        assertTrue(referral.code.isNotBlank())
        assertTrue(referral.link.endsWith(referral.code))

        val inviterBefore = get<RewardsSummary>("/v1/rewards", inviter.token).coins.balance

        val invited = signUp()
        val response = client.post("/v1/me/onboarding") {
            auth(invited.token)
            json(
                OnboardingRequest(
                    name = "Invited",
                    language = Language.UZ,
                    timezone = "Asia/Tashkent",
                    lifeStage = LifeStage.CYCLE,
                    consents = ConsentGrants(storeHealth = true),
                    inviteCode = referral.code,
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsTextSafe())

        val inviterAfter = get<RewardsSummary>("/v1/rewards", inviter.token).coins.balance
        assertTrue(inviterAfter > inviterBefore, "the inviter is paid: $inviterBefore -> $inviterAfter")

        val invitedWallet = get<RewardsSummary>("/v1/rewards", invited.token)
        assertTrue(invitedWallet.coins.balance > 0, "the invited account starts with a welcome")

        // Presenting the same code again changes nothing: the claim is keyed by account.
        val again = post<ClaimReferralResult>(
            "/v1/rewards/referral/claim",
            invited.token,
            ClaimReferralRequest(referral.code),
        )
        assertFalse(again.accepted)

        // And her own code pays her nothing.
        val own = get<ReferralStatus>("/v1/rewards/referral", invited.token)
        val selfClaim = post<ClaimReferralResult>(
            "/v1/rewards/referral/claim",
            invited.token,
            ClaimReferralRequest(own.code),
        )
        assertFalse(selfClaim.accepted)
    }

    /**
     * Spending. The important half is the refusal: a balance that could go negative
     * would be a shop giving product away.
     */
    @Test
    fun `Gul buys Premium, and a balance that does not cover it buys nothing`() = api {
        val user = signUp()
        onboard(user)

        val catalogue = get<ShopCatalog>("/v1/shop", user.token)
        val premium = assertNotNull(
            catalogue.products.firstOrNull { it.kind == ShopKind.PREMIUM },
            "the seeded shop sells Premium",
        )
        assertFalse(premium.affordable, "a new account cannot afford a month of Premium")

        val refused = client.post("/v1/shop/redeem") {
            auth(user.token)
            json(RedeemRequest(premium.id))
        }
        assertEquals(HttpStatusCode.BadRequest, refused.status, refused.bodyAsTextSafe())
        assertEquals(SubscriptionTier.FREE, get<Entitlements>("/v1/entitlements", user.token).tier)

        // Given the coins by an operator, the same purchase goes through and the
        // entitlement moves — the server owns both sides of that trade.
        val admin = adminToken()
        val credited = post<CoinBalance>(
            "/v1/admin/rewards/users/${user.userId}/adjust",
            admin,
            AdjustCoinsRequest(amount = premium.coinCost, note = "integration test"),
        )
        assertEquals(premium.coinCost, credited.balance)

        val result = post<RedeemResult>("/v1/shop/redeem", user.token, RedeemRequest(premium.id))
        assertTrue(result.premiumGranted)
        assertEquals(0, result.coins.balance, "the coins were spent, not copied")
        assertTrue(result.redemption.code.startsWith("SDR-"))
        assertEquals(SubscriptionTier.PREMIUM, get<Entitlements>("/v1/entitlements", user.token).tier)
    }

    /** A partner item hands over a code and a discount, and never claims to have shipped. */
    @Test
    fun `a vitamin redemption issues a code carrying the discount`() = api {
        val user = signUp()
        onboard(user)
        val admin = adminToken()

        val catalogue = get<ShopCatalog>("/v1/shop", user.token)
        val vitamin = assertNotNull(catalogue.products.firstOrNull { it.kind == ShopKind.VITAMIN })
        post<CoinBalance>(
            "/v1/admin/rewards/users/${user.userId}/adjust",
            admin,
            AdjustCoinsRequest(amount = vitamin.coinCost, note = "integration test"),
        )

        val result = post<RedeemResult>("/v1/shop/redeem", user.token, RedeemRequest(vitamin.id))
        assertFalse(result.premiumGranted, "a partner product grants no entitlement")
        assertEquals(vitamin.discountPercent, result.redemption.discountPercent)
        assertNotNull(result.redemption.expiresAt, "a partner code carries an expiry")

        val mine = get<List<Redemption>>("/v1/shop/redemptions", user.token)
        assertTrue(mine.any { it.code == result.redemption.code })
    }

    /**
     * The layout is a preference the account carries between phones, which is the whole
     * reason it is on the server rather than in local storage.
     */
    @Test
    fun `the home layout is saved whole and reconciled against the shipped catalogue`() = api {
        val user = signUp()
        onboard(user)

        val defaults = get<HomeLayout>("/v1/me/home-layout", user.token)
        assertEquals(HomeWidgets.keys.size, defaults.widgets.size)

        val rearranged = listOf(
            HomeWidget(HomeWidgets.STREAK, 0, true),
            HomeWidget(HomeWidgets.AI, 1, true),
            HomeWidget(HomeWidgets.SLEEP, 2, true),
            // A key from a client the server has never heard of: dropped, not refused.
            HomeWidget("something_new", 3, true),
        )
        val saved = put<HomeLayout>("/v1/me/home-layout", user.token, SaveHomeLayoutRequest(rearranged))
        assertEquals(HomeWidgets.STREAK, saved.visible().first())
        assertTrue(HomeWidgets.SLEEP in saved.visible(), "a widget she switched on stays on")
        assertFalse(saved.widgets.any { it.key == "something_new" })

        // It survives the round trip, which is the point of storing it at all.
        val reread = get<HomeLayout>("/v1/me/home-layout", user.token)
        assertEquals(HomeWidgets.STREAK, reread.visible().first())
    }

    /**
     * The greeting is free, unmetered, and different on every open — that last part is
     * the feature, and a cached line handed out twice would quietly undo it.
     */
    @Test
    fun `the home greeting answers for a free account and does not repeat itself`() = api {
        val user = signUp()
        onboard(user)

        val lines = (1..4).map { get<AiGreeting>("/v1/ai/greeting", user.token).line }
        assertTrue(lines.all { it.isNotBlank() })
        assertTrue(lines.toSet().size > 1, "four opens produced the same line every time: $lines")
    }

    // ---------------------------------------------------------------- admin 2FA

    /**
     * Sign-in has always demanded a TOTP code from an account with 2FA enabled, and
     * nothing could enable it — so every operator account was, in practice, a password.
     * This walks the enrolment the panel now offers, and then proves the code is really
     * required.
     */
    @Test
    fun `an operator can enrol in 2FA, and afterwards a password alone is not enough`() = api {
        val admin = adminAccount()

        val before = get<AdminMe>("/v1/admin/me", admin.token)
        assertTrue(!before.totpEnabled)

        val enrolment = client.post("/v1/admin/me/totp/start") { auth(admin.token) }.body<TotpEnrolment>()
        assertTrue(enrolment.otpauthUri.startsWith("otpauth://totp/SADORA:"), enrolment.otpauthUri)

        // Nothing is switched on until a code proves the authenticator holds the secret.
        assertTrue(!get<AdminMe>("/v1/admin/me", admin.token).totpEnabled)
        // A typo in the code is a field error, not the end of the session: a 401 here
        // used to sign the operator out of the panel in the middle of enrolling.
        val wrong = client.post("/v1/admin/me/totp/confirm") {
            auth(admin.token)
            json(TotpConfirmRequest("000000"))
        }
        assertEquals(HttpStatusCode.BadRequest, wrong.status)
        assertTrue(!get<AdminMe>("/v1/admin/me", admin.token).totpEnabled)

        val code = currentCodeFor(enrolment.secret)
        val confirmed = client.post("/v1/admin/me/totp/confirm") {
            auth(admin.token)
            json(TotpConfirmRequest(code))
        }
        assertEquals(HttpStatusCode.OK, confirmed.status, confirmed.bodyAsTextSafe())
        assertTrue(get<AdminMe>("/v1/admin/me", admin.token).totpEnabled)

        // The point of the whole exercise.
        val passwordOnly = client.post("/v1/admin/auth/login") {
            json(AdminSignInRequest(admin.email, admin.password))
        }
        assertEquals(HttpStatusCode.Unauthorized, passwordOnly.status)
        // Named as such, so the panel shows the code field instead of matching the
        // message text — and a missing code is not a failed attempt against the lockout.
        assertTrue(passwordOnly.bodyAsTextSafe().contains("totp_required"), passwordOnly.bodyAsTextSafe())
        repeat(5) {
            client.post("/v1/admin/auth/login") { json(AdminSignInRequest(admin.email, admin.password)) }
        }

        val withCode = client.post("/v1/admin/auth/login") {
            json(AdminSignInRequest(admin.email, admin.password, currentCodeFor(enrolment.secret)))
        }
        assertEquals(HttpStatusCode.OK, withCode.status, withCode.bodyAsTextSafe())
    }

    @Test
    fun `turning 2FA off needs the password as well, so a borrowed session cannot`() = api {
        val admin = adminAccount()
        val enrolment = client.post("/v1/admin/me/totp/start") { auth(admin.token) }.body<TotpEnrolment>()
        client.post("/v1/admin/me/totp/confirm") {
            auth(admin.token)
            json(TotpConfirmRequest(currentCodeFor(enrolment.secret)))
        }

        val sessionOnly = client.post("/v1/admin/me/totp/disable") {
            auth(admin.token)
            json(TotpDisableRequest(password = "not-the-password", code = currentCodeFor(enrolment.secret)))
        }
        assertEquals(HttpStatusCode.BadRequest, sessionOnly.status)
        assertTrue(get<AdminMe>("/v1/admin/me", admin.token).totpEnabled, "still protected")

        val proper = client.post("/v1/admin/me/totp/disable") {
            auth(admin.token)
            json(TotpDisableRequest(password = admin.password, code = currentCodeFor(enrolment.secret)))
        }
        assertEquals(HttpStatusCode.OK, proper.status, proper.bodyAsTextSafe())
        assertTrue(!get<AdminMe>("/v1/admin/me", admin.token).totpEnabled)
    }

    /** What the operator's authenticator would be showing right now. */
    private fun currentCodeFor(secret: String): String =
        Totp.generate(Totp.decodeBase32(secret), now().epochSeconds / 30)

    // ---------------------------------------------------------------- deletion

    /**
     * The half of "delete my account" that used to be missing: the row was marked and
     * then stayed forever. Health data is exactly the kind a person deletes an account
     * to be rid of, so this checks it is actually gone rather than hidden.
     */
    @Test
    fun `the erasure job removes the account and everything the schema hangs off it`() = api {
        val her = signUp()
        onboard(her, storeHealth = true, mood = MoodLevel.LOW, symptoms = listOf("fatigue"))
        val userId = Uuid.parse(her.userId)

        assertEquals(1, countRowsFor(userId, "daily_logs"), "she logged a check-in")

        val response = client.delete("/v1/me") {
            auth(her.token)
            json(DeleteAccountRequest(confirmation = "DELETE", reason = "no longer needed"))
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsTextSafe())

        // Every device is signed out the moment she asks: the refresh token is dead, so
        // no session can renew itself past the access token it is already holding.
        val renewed = client.post("/v1/auth/refresh") { json(RefreshRequest(her.refreshToken)) }
        assertEquals(HttpStatusCode.Unauthorized, renewed.status, renewed.bodyAsTextSafe())
        assertNotNull(component.userRepository.findById(userId), "still there during the grace period")

        // At least hers: the suite shares one database, so a previous test's pending
        // account may be due in the same tick.
        assertTrue(component.accountErasureJob.runOnce() >= 1)

        assertNull(component.userRepository.findById(userId), "the account is gone")
        assertEquals(0, countRowsFor(userId, "daily_logs"), "and so is her health data")
        assertEquals(0, countRowsFor(userId, "devices"))
        assertEquals(0, countRowsFor(userId, "user_consents"))

        // What is left is the record that it happened, with no one in it.
        val erasures = dbQuery {
            AuditLog.selectAll()
                .where { (AuditLog.action eq "user.erased") and (AuditLog.entityId eq userId.toString()) }
                .count()
        }
        assertEquals(1L, erasures, "an erased account still leaves a line saying so")
    }

    @Test
    fun `an account still inside its grace period is left alone`() = api {
        val her = signUp()
        val userId = Uuid.parse(her.userId)
        client.delete("/v1/me") {
            auth(her.token)
            json(DeleteAccountRequest(confirmation = "DELETE"))
        }

        // A day of grace is enough to make "asked just now" not yet due.
        val job = AccountErasureJob(
            users = component.userRepository,
            audit = component.auditService,
            gracePeriod = 1.days,
        )
        assertEquals(0, job.runOnce())
        assertNotNull(component.userRepository.findById(userId))
    }

    // ---------------------------------------------------------------- periods

    @Test
    fun `a period that overlaps or runs on from another is refused, a separate one is not`() = api {
        val her = signUp()
        onboard(her, referredByDoctor = null, storeHealth = true)
        val today = get<CycleStatus>("/v1/cycle/status", her.token).today
        fun ago(days: Int) = today.minus(days, DateTimeUnit.DAY)

        val first = post<PeriodEntry>("/v1/cycle/periods", her.token, LogPeriodRequest(ago(40), ago(36)))

        // Sharing a day, and starting the day after it ended: the same bleed twice.
        for (start in listOf(ago(36), ago(35))) {
            val refused = client.post("/v1/cycle/periods") { auth(her.token); json(LogPeriodRequest(start)) }
            assertEquals(HttpStatusCode.Conflict, refused.status, "start $start: ${refused.bodyAsTextSafe()}")
        }

        // A month on is a new cycle.
        val second = post<PeriodEntry>("/v1/cycle/periods", her.token, LogPeriodRequest(ago(12), ago(8)))

        // Moving one onto the other is refused too; editing a period within itself is not.
        val moved = client.patch("/v1/cycle/periods/${second.id}") {
            auth(her.token)
            json(UpdatePeriodRequest(startedOn = ago(38), endedOn = ago(34)))
        }
        assertEquals(HttpStatusCode.Conflict, moved.status, moved.bodyAsTextSafe())
        patch<PeriodEntry>("/v1/cycle/periods/${first.id}", her.token, UpdatePeriodRequest(endedOn = ago(35)))

        assertEquals(2, get<List<PeriodEntry>>("/v1/cycle/periods", her.token).size)
    }

    // ---------------------------------------------------------------- stage events

    @Test
    fun `stage events keep to their kind, score the questionnaire here and stay private`() = api {
        val her = signUp()
        onboard(her, referredByDoctor = null, storeHealth = true)
        val at = Clock.System.now() - 10.minutes

        val feed = post<StageEvent>(
            "/v1/stage-events",
            her.token,
            LogStageEventRequest(StageEventKind.FEEDING, at, durationSeconds = 600, detail = "left"),
        )
        assertEquals("left", feed.detail)

        // A breast feed without its length, a bottle without its millilitres, a hot flush
        // of intensity 7: each refused for what it means.
        for (bad in listOf(
            LogStageEventRequest(StageEventKind.FEEDING, at, detail = "right"),
            LogStageEventRequest(StageEventKind.FEEDING, at, detail = "bottle"),
            LogStageEventRequest(StageEventKind.HOT_FLUSH, at, value = 7),
            LogStageEventRequest(StageEventKind.KICK_COUNT, Clock.System.now() + 2.hours, value = 10, durationSeconds = 900),
        )) {
            val refused = client.post("/v1/stage-events") { auth(her.token); json(bad) }
            assertEquals(HttpStatusCode.BadRequest, refused.status, "$bad: ${refused.bodyAsTextSafe()}")
        }

        // The score is worked out from the answers; a value sent with them is ignored.
        val screen = post<StageEvent>(
            "/v1/stage-events",
            her.token,
            LogStageEventRequest(StageEventKind.MOOD_SCREEN, at, value = 0, answers = List(10) { 0 }),
        )
        assertEquals(21, screen.value)

        assertEquals(listOf(feed.id), get<List<StageEvent>>("/v1/stage-events?kind=feeding", her.token).map { it.id })
        assertEquals(2, get<List<StageEvent>>("/v1/stage-events", her.token).size)

        val other = signUp()
        onboard(other, referredByDoctor = null, storeHealth = true)
        assertTrue(get<List<StageEvent>>("/v1/stage-events", other.token).isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.delete("/v1/stage-events/${feed.id}") { auth(other.token) }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/v1/stage-events/${feed.id}") { auth(her.token) }.status)
    }

    @Test
    fun `the symptom catalogue speaks her language and offers each stage its own`() = api {
        val her = signUp()
        onboard(her, referredByDoctor = null, storeHealth = true)
        patch<UserProfile>("/v1/me", her.token, UpdateProfileRequest(language = Language.RU))

        val pregnancy = get<List<SymptomDefinition>>("/v1/symptoms?lifeStage=pregnancy", her.token).associateBy { it.key }
        assertEquals("Изжога", pregnancy["heartburn"]?.label)
        assertTrue("lochia" !in pregnancy)
        val postpartum = get<List<SymptomDefinition>>("/v1/symptoms?lifeStage=postpartum", her.token).map { it.key }
        assertTrue("lochia" in postpartum && "breast_tender" in postpartum, postpartum.toString())
    }

    @Test
    fun `the smart-device answer is saved after sign-up and a later save leaves it alone`() = api {
        val her = signUp()
        onboard(her, referredByDoctor = null, storeHealth = true)
        assertEquals(null, get<UserProfile>("/v1/me", her.token).hasWearable)

        assertEquals(true, patch<UserProfile>("/v1/me", her.token, UpdateProfileRequest(hasWearable = true)).hasWearable)
        assertEquals(true, patch<UserProfile>("/v1/me", her.token, UpdateProfileRequest(language = Language.RU)).hasWearable)
        assertEquals(false, patch<UserProfile>("/v1/me", her.token, UpdateProfileRequest(hasWearable = false)).hasWearable)
    }

    // ---------------------------------------------------------------- appointments

    @Test
    fun `appointments belong to their owner and need the storage consent to write`() = api {
        val her = signUp()
        onboard(her, referredByDoctor = null, storeHealth = true)

        val today = get<CycleStatus>("/v1/cycle/status", her.token).today
        val created = post<Appointment>(
            "/v1/appointments",
            her.token,
            SaveAppointmentRequest(
                title = "Skrining UTT",
                scheduledOn = today,
                scheduledAt = LocalTime(10, 30),
                place = "Respublika markazi",
                remindHoursBefore = 24,
            ),
        )
        assertEquals("Skrining UTT", created.title)
        assertFalse(created.isDone)

        // Marking it done is a timestamp, and it survives a re-read.
        put<Appointment>("/v1/appointments/${created.id}/completed", her.token, CompleteAppointmentRequest(true))
        val mine = get<List<Appointment>>("/v1/appointments", her.token)
        assertEquals(1, mine.size)
        assertTrue(mine.single().isDone)

        // Another account sees none of it, and cannot reach this one by id.
        val other = signUp()
        onboard(other, referredByDoctor = null, storeHealth = true)
        assertTrue(get<List<Appointment>>("/v1/appointments", other.token).isEmpty())
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/v1/appointments/${created.id}") { auth(other.token) }.status,
        )

        // Without the health-storage consent there is nothing to write into.
        val withoutConsent = signUp()
        onboard(withoutConsent, referredByDoctor = null, storeHealth = false)
        val refused = client.post("/v1/appointments") {
            auth(withoutConsent.token)
            json(SaveAppointmentRequest(title = "Qon tahlili", scheduledOn = today))
        }
        assertEquals(HttpStatusCode.Forbidden, refused.status, refused.bodyAsTextSafe())
    }

    // ---------------------------------------------------------------- validation

    /**
     * The four checks the profile screens rely on being there.
     *
     * They used to disagree with each other: onboarding refused a blank name, the
     * profile update accepted one of any length, and neither looked at the birth date —
     * so a saved profile could carry a name longer than its column and a birthday in
     * 1815. All four now come from one place, and so does the number the app caps at.
     */
    @Test
    fun `a profile refuses what the fields refuse`() = api {
        val her = signUp()
        onboard(her)

        val tooLong = client.patch("/v1/me") {
            auth(her.token)
            json(UpdateProfileRequest(name = "M".repeat(Limits.NAME_MAX + 1)))
        }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status, tooLong.bodyAsTextSafe())

        val blank = client.patch("/v1/me") {
            auth(her.token)
            json(UpdateProfileRequest(name = "   "))
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status, blank.bodyAsTextSafe())

        val tall = client.patch("/v1/me") {
            auth(her.token)
            json(UpdateProfileRequest(heightCm = 300))
        }
        assertEquals(HttpStatusCode.BadRequest, tall.status, tall.bodyAsTextSafe())

        val born = client.patch("/v1/me") {
            auth(her.token)
            json(UpdateProfileRequest(birthDate = LocalDate.parse("1815-06-18")))
        }
        assertEquals(HttpStatusCode.BadRequest, born.status, born.bodyAsTextSafe())

        // And what is inside the limits still saves.
        val ok = patch<UserProfile>(
            "/v1/me",
            her.token,
            UpdateProfileRequest(name = "Malika", heightCm = 164, birthDate = LocalDate.parse("1994-03-14")),
        )
        assertEquals("Malika", ok.name)
    }

    /**
     * Moving to pregnancy or postpartum in Settings carries the date the weeks are
     * counted from. Without it the app could only show a week it made up.
     */
    @Test
    fun `a stage changed in Settings brings its date and keeps the others`() = api {
        val her = signUp()
        onboard(her)
        val today = kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.of("Asia/Tashkent"))
        val due = today.plus(100, kotlinx.datetime.DateTimeUnit.DAY)

        val pregnant = patch<UserProfile>(
            "/v1/me",
            her.token,
            UpdateProfileRequest(lifeStage = LifeStage.PREGNANCY, stage = StageBaseline(dueDate = due)),
        )
        assertEquals(LifeStage.PREGNANCY, pregnant.lifeStage)
        assertEquals(due, pregnant.stage?.dueDate)

        val tooFar = client.patch("/v1/me") {
            auth(her.token)
            json(UpdateProfileRequest(stage = StageBaseline(dueDate = today.plus(400, kotlinx.datetime.DateTimeUnit.DAY))))
        }
        assertEquals(HttpStatusCode.BadRequest, tooFar.status, tooFar.bodyAsTextSafe())
        val unborn = client.patch("/v1/me") {
            auth(her.token)
            json(UpdateProfileRequest(stage = StageBaseline(birthDate = today.plus(1, kotlinx.datetime.DateTimeUnit.DAY))))
        }
        assertEquals(HttpStatusCode.BadRequest, unborn.status, unborn.bodyAsTextSafe())

        val born = today.minus(10, kotlinx.datetime.DateTimeUnit.DAY)
        val after = patch<UserProfile>(
            "/v1/me",
            her.token,
            UpdateProfileRequest(lifeStage = LifeStage.POSTPARTUM, stage = StageBaseline(birthDate = born)),
        )
        assertEquals(LifeStage.POSTPARTUM, after.lifeStage)
        assertEquals(born, after.stage?.birthDate)
        assertEquals(due, after.stage?.dueDate, "the due date was not hers to lose")
    }

    /**
     * A code is six digits. Anything else is refused before a challenge is looked up,
     * so a paste into the code box cannot burn one of her five attempts.
     */
    @Test
    fun `a code that is not six digits is not a wrong code`() = api {
        val phone = randomPhone()
        val challenge = client.post("/v1/auth/otp/request") { json(OtpRequest(phone)) }.body<OtpChallenge>()

        listOf("", "12345", "1234567", "12345a").forEach { code ->
            val response = client.post("/v1/auth/otp/verify") {
                json(OtpVerifyRequest(challenge.challengeId, code, DeviceInfo("test-device", Platform.ANDROID)))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "accepted: $code")
        }

        // The real code still works, so none of those consumed an attempt.
        val session = client.post("/v1/auth/otp/verify") {
            json(
                OtpVerifyRequest(
                    challenge.challengeId,
                    assertNotNull(challenge.devCode),
                    DeviceInfo("test-device", Platform.ANDROID, timezone = "Asia/Tashkent"),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, session.status, session.bodyAsTextSafe())
    }

    /** An appointment's place had no limit, which is a free text column open to anything. */
    @Test
    fun `an appointment refuses a title or a place longer than its column`() = api {
        val her = signUp()
        onboard(her, referredByDoctor = null, storeHealth = true)
        val today = get<CycleStatus>("/v1/cycle/status", her.token).today

        val longTitle = client.post("/v1/appointments") {
            auth(her.token)
            json(SaveAppointmentRequest(title = "x".repeat(Limits.APPOINTMENT_TITLE_MAX + 1), scheduledOn = today))
        }
        assertEquals(HttpStatusCode.BadRequest, longTitle.status, longTitle.bodyAsTextSafe())

        val longPlace = client.post("/v1/appointments") {
            auth(her.token)
            json(
                SaveAppointmentRequest(
                    title = "Skrining",
                    scheduledOn = today,
                    place = "x".repeat(Limits.APPOINTMENT_PLACE_MAX + 1),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, longPlace.status, longPlace.bodyAsTextSafe())
    }

    // ---------------------------------------------------------------- food scanner

    /**
     * The scanner is a paid model call, so it has the same three gates the chat has, in
     * the same order: consent, then the subscription, then the limit. This pins the two
     * refusals a free account should see before any image is ever sent anywhere.
     */
    @Test
    fun `the food scanner refuses a free account and a malformed image`() = api {
        val her = signUp()
        onboard(her, referredByDoctor = null, storeHealth = true)

        // Free tier: the feature is not in the plan, so it is 402 rather than a bad request.
        val refused = client.post("/v1/nutrition/scan") {
            auth(her.token)
            json(FoodScanRequest(imageBase64 = "aGVsbG8=", mimeType = "image/jpeg"))
        }
        assertEquals(HttpStatusCode.PaymentRequired, refused.status, refused.bodyAsTextSafe())

        postAck(
            "/v1/admin/users/${her.userId}/premium",
            adminToken(),
            uz.sadora.server.admin.GrantPremiumRequest(reason = "integration test"),
        )

        // With the plan, the request itself is validated before anything leaves the box.
        val empty = client.post("/v1/nutrition/scan") {
            auth(her.token)
            json(FoodScanRequest(imageBase64 = "", mimeType = "image/jpeg"))
        }
        assertEquals(HttpStatusCode.BadRequest, empty.status, empty.bodyAsTextSafe())

        val wrongType = client.post("/v1/nutrition/scan") {
            auth(her.token)
            json(FoodScanRequest(imageBase64 = "aGVsbG8=", mimeType = "application/pdf"))
        }
        assertEquals(HttpStatusCode.BadRequest, wrongType.status, wrongType.bodyAsTextSafe())

        // No model is configured in a test run, so a well-formed request says the
        // scanner could not answer rather than inventing a dish.
        val noModel = client.post("/v1/nutrition/scan") {
            auth(her.token)
            json(FoodScanRequest(imageBase64 = "aGVsbG8=", mimeType = "image/jpeg"))
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, noModel.status, noModel.bodyAsTextSafe())
        assertEquals(
            ErrorCodes.UPSTREAM_UNAVAILABLE,
            noModel.body<ApiErrorResponse>().error.code,
        )
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

    /**
     * The chat's long lists page. A thread gives its newest lines and says older ones
     * exist; `/messages?before=` reads them upward and never past another thread's line;
     * the threads list reads on by `before`; comments page with doctors' answers first.
     */
    @Test
    fun `threads, the threads list and comments are read a page at a time`() = api {
        val her = signUp().also { onboard(it) }
        val him = signUp().also { onboard(it) }
        val third = signUp().also { onboard(it) }
        val herAlias = get<CommunityIdentity>("/v1/community/me", her.token).alias
        val thirdAlias = get<CommunityIdentity>("/v1/community/me", third.token).alias

        val started = post<ConversationThread>("/v1/community/conversations", him.token, StartConversationRequest(herAlias, "line 0"))
        val id = started.conversation.id
        (1..5).forEach { post<DirectMessage>("/v1/community/conversations/$id/messages", him.token, SendMessageRequest("line $it")) }

        val newest = get<ConversationThread>("/v1/community/conversations/$id?limit=3", her.token)
        assertEquals(listOf("line 3", "line 4", "line 5"), newest.messages.map { it.body })
        assertTrue(newest.hasMore)
        val older = get<MessagePage>("/v1/community/conversations/$id/messages?before=${newest.messages.first().id}&limit=2", her.token)
        assertEquals(listOf("line 1", "line 2"), older.messages.map { it.body })
        assertTrue(older.hasMore)
        val oldest = get<MessagePage>("/v1/community/conversations/$id/messages?before=${older.messages.first().id}&limit=2", her.token)
        assertEquals(listOf("line 0"), oldest.messages.map { it.body })
        assertFalse(oldest.hasMore)
        assertFalse(get<ConversationThread>("/v1/community/conversations/$id", her.token).hasMore, "no limit: the whole short thread")
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/conversations/$id/messages?before=${newest.messages.first().id}") { auth(third.token) } }.status)

        // A line of another thread is not a cursor into this one.
        val other = post<ConversationThread>("/v1/community/conversations", him.token, StartConversationRequest(thirdAlias, "boshqa"))
        val foreign = other.messages.single().id
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/conversations/$id/messages?before=$foreign") { auth(him.token) } }.status)

        // His two threads, one per page, newest first.
        val first = get<List<Conversation>>("/v1/community/conversations?limit=1", him.token)
        assertEquals(listOf(other.conversation.id), first.map { it.id })
        val before = first.single().lastMessageAt.toString().replace("+", "%2B")
        val second = get<List<Conversation>>("/v1/community/conversations?limit=1&before=$before", him.token)
        assertEquals(listOf(id), second.map { it.id })
        assertTrue(get<List<Conversation>>("/v1/community/conversations?limit=1&before=${second.single().lastMessageAt}", him.token).isEmpty())
        assertEquals(HttpStatusCode.BadRequest, raw { client.get("/v1/community/conversations?before=kecha") { auth(him.token) } }.status)

        // Comments: three, read two and then one, in the order they were written.
        val postId = post<CommunityPost>("/v1/community/posts", her.token, CreatePostRequest(CommunityTopic.WELLBEING, "Sahifa test ${Uuid.random()}")).id
        (1..3).forEach { post<CommunityComment>("/v1/community/posts/$postId/comments", him.token, CreateCommentRequest("izoh $it")) }
        val top = get<List<CommunityComment>>("/v1/community/posts/$postId/comments?limit=2", her.token)
        assertEquals(listOf("izoh 1", "izoh 2"), top.map { it.body })
        val rest = get<List<CommunityComment>>("/v1/community/posts/$postId/comments?limit=2&offset=2", her.token)
        assertEquals(listOf("izoh 3"), rest.map { it.body })
        assertEquals(3, get<List<CommunityComment>>("/v1/community/posts/$postId/comments", her.token).size)
    }

    /**
     * Lists that used to stop at a cap now read on: the doctor directory and her reviews
     * by offset, an alias's and a doctor's posts past the few their page carries, and the
     * notification history below a cursor. A build that asks for no page gets each one
     * exactly as before.
     */
    @Test
    fun `the directory, reviews, profile posts and notification history read on past their first page`() = api {
        val doctor = signUp().also { onboard(it) }
        val patient = signUp().also { onboard(it) }
        val writer = signUp().also { onboard(it) }
        val admin = adminToken()
        val profileId = approvedDoctor(doctor, admin, "Dr Page ${Uuid.random().toString().take(6)}")

        // The directory: pages cut from the one recommended order; no limit is the whole list.
        val whole = get<List<DoctorListItem>>("/v1/doctors", patient.token)
        assertTrue(whole.any { it.id == profileId })
        val paged = (0 until 3).flatMap { get<List<DoctorListItem>>("/v1/doctors?limit=2&offset=${it * 2}", patient.token) }
        assertEquals(whole.take(6).map { it.id }, paged.map { it.id })
        assertTrue(get<List<DoctorListItem>>("/v1/doctors?limit=2&offset=${whole.size}", patient.token).isEmpty())

        // Reviews: newest first, two and then one; no paging asked is the latest, as before.
        val opened = post<ConversationThread>("/v1/doctors/$profileId/consultations", patient.token, uz.sadora.contract.StartConsultationRequest("Salom"))
        dbQuery {
            (1..3).forEach { n ->
                exec(
                    "INSERT INTO consultation_sessions (conversation_id, doctor_id, patient_id, rating, review, rated_at, opened_at, closed_at) " +
                        "VALUES ('${opened.conversation.id}', '$profileId', '${patient.userId}', $n, 'sharh $n', now() + interval '$n minutes', now(), now())",
                )
            }
        }
        val reviews = "/v1/doctors/$profileId/reviews"
        assertEquals(listOf("sharh 3", "sharh 2"), get<List<uz.sadora.contract.DoctorReview>>("$reviews?limit=2", patient.token).map { it.review })
        assertEquals(listOf("sharh 1"), get<List<uz.sadora.contract.DoctorReview>>("$reviews?limit=2&offset=2", patient.token).map { it.review })
        assertEquals(listOf("sharh 3", "sharh 2", "sharh 1"), get<List<uz.sadora.contract.DoctorReview>>(reviews, patient.token).map { it.review })

        // A doctor's posts: her page still carries the latest; `/posts` reads on with a total.
        val doctorBodies = (1..3).map {
            post<CommunityPost>("/v1/community/posts", doctor.token, CreatePostRequest(CommunityTopic.WELLBEING, "Shifokor posti $it ${Uuid.random()}")).body
        }.reversed()
        assertEquals(doctorBodies, get<DoctorProfile>("/v1/doctors/$profileId", patient.token).posts.map { it.body })
        val doctorFirst = get<Page<CommunityPost>>("/v1/doctors/$profileId/posts?limit=2", patient.token)
        assertEquals(doctorBodies.take(2), doctorFirst.items.map { it.body })
        assertEquals(3L, doctorFirst.total)
        assertTrue(doctorFirst.hasMore)
        val doctorRest = get<Page<CommunityPost>>("/v1/doctors/$profileId/posts?limit=2&offset=2", patient.token)
        assertEquals(doctorBodies.drop(2), doctorRest.items.map { it.body })
        assertFalse(doctorRest.hasMore)
        assertEquals(doctorBodies, get<Page<CommunityPost>>("/v1/doctors/$profileId/posts", patient.token).items.map { it.body })
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/doctors/${Uuid.random()}/posts") { auth(patient.token) } }.status)

        // An alias's posts, the same way.
        val alias = get<CommunityIdentity>("/v1/community/me", writer.token).alias
        val aliasBodies = (1..3).map {
            post<CommunityPost>("/v1/community/posts", writer.token, CreatePostRequest(CommunityTopic.WELLBEING, "Taxallus posti $it ${Uuid.random()}")).body
        }.reversed()
        val profilePath = "/v1/community/profiles/${alias.encodeURLPathPart()}"
        assertEquals(aliasBodies, get<CommunityProfile>(profilePath, patient.token).posts.map { it.body })
        val aliasFirst = get<Page<CommunityPost>>("$profilePath/posts?limit=2", patient.token)
        assertEquals(aliasBodies.take(2), aliasFirst.items.map { it.body })
        assertEquals(3L, aliasFirst.total)
        assertTrue(aliasFirst.hasMore)
        val aliasRest = get<Page<CommunityPost>>("$profilePath/posts?limit=2&offset=2", patient.token)
        assertEquals(aliasBodies.drop(2), aliasRest.items.map { it.body })
        assertEquals(2, aliasRest.offset)
        assertFalse(aliasRest.hasMore)
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/profiles/yoq-${Uuid.random()}/posts") { auth(patient.token) } }.status)

        // Notification history: the newest, then below a cursor that must be one of hers.
        dbQuery {
            (1..3).forEach { n ->
                exec(
                    "INSERT INTO notification_outbox (user_id, category, title, body, scheduled_for, status, dedupe_key, created_at) " +
                        "VALUES ('${patient.userId}', 'system', 'xabar $n', 'matn', now(), 'sent', 'page-test-$n', now() + interval '$n minutes')",
                )
            }
        }
        val newest = get<List<uz.sadora.contract.NotificationMessage>>("/v1/notifications/history?limit=2", patient.token)
        assertEquals(listOf("xabar 3", "xabar 2"), newest.map { it.title })
        val below = get<List<uz.sadora.contract.NotificationMessage>>("/v1/notifications/history?limit=2&before=${newest.last().id}", patient.token)
        assertEquals("xabar 1", below.first().title)
        assertTrue(below.none { it.id in newest.map { line -> line.id } })
        val all = get<List<uz.sadora.contract.NotificationMessage>>("/v1/notifications/history", patient.token)
        assertEquals(listOf("xabar 3", "xabar 2", "xabar 1"), all.take(3).map { it.title }, "no cursor: the newest, as before")
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/notifications/history?before=${newest.first().id}") { auth(writer.token) } }.status)
        assertEquals(HttpStatusCode.BadRequest, raw { client.get("/v1/notifications/history?before=kecha") { auth(patient.token) } }.status)
    }

    /**
     * The panel's payments, issued codes and comment drawer used to stop at their first
     * page, so an older row could not be reached at all. Each now pages with a total.
     */
    @Test
    fun `the panel pages payments, issued codes and a post's comments with a total`() = api {
        val user = signUp().also { onboard(it) }
        val admin = adminToken()

        // Payments: three pending checkouts, newest first, two and then one.
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)
        val orders = (1..3).map {
            post<CheckoutSession>("/v1/billing/checkout", user.token, CheckoutRequest("premium_year", PaymentProvider.PAYME)).transactionId
        }
        val payments = get<Page<AdminPaymentView>>("/v1/admin/billing/payments?state=pending&limit=2", admin)
        assertEquals(listOf(orders[2], orders[1]), payments.items.map { it.id })
        assertTrue(payments.total >= 3 && payments.hasMore)
        assertTrue(payments.items.all { it.state == PaymentState.PENDING })
        val nextPayments = get<Page<AdminPaymentView>>("/v1/admin/billing/payments?state=pending&limit=1&offset=2", admin)
        assertEquals(listOf(orders[0]), nextPayments.items.map { it.id })
        assertEquals(2, nextPayments.offset)
        val pastPayments = get<Page<AdminPaymentView>>("/v1/admin/billing/payments?state=pending&offset=${payments.total}", admin)
        assertTrue(pastPayments.items.isEmpty())
        assertEquals(payments.total, pastPayments.total, "the total does not depend on the page asked for")

        // Issued codes: three vitamin redemptions, the same way.
        val vitamin = assertNotNull(get<ShopCatalog>("/v1/shop", user.token).products.firstOrNull { it.kind == ShopKind.VITAMIN })
        post<CoinBalance>(
            "/v1/admin/rewards/users/${user.userId}/adjust",
            admin,
            AdjustCoinsRequest(amount = vitamin.coinCost * 3, note = "integration test"),
        )
        val codes = (1..3).map { post<RedeemResult>("/v1/shop/redeem", user.token, RedeemRequest(vitamin.id)).redemption.code }
        val redemptions = get<Page<AdminRedemption>>("/v1/admin/shop/redemptions?limit=2", admin)
        assertEquals(listOf(codes[2], codes[1]), redemptions.items.map { it.code })
        assertTrue(redemptions.total >= 3 && redemptions.hasMore)
        val nextRedemptions = get<Page<AdminRedemption>>("/v1/admin/shop/redemptions?limit=1&offset=2", admin)
        assertEquals(listOf(codes[0]), nextRedemptions.items.map { it.code })
        assertEquals(redemptions.total, nextRedemptions.total)

        // Comments: the moderation drawer reads the thread oldest first, a page at a time.
        val postId = post<CommunityPost>("/v1/community/posts", user.token, CreatePostRequest(CommunityTopic.WELLBEING, "Panel sahifa ${Uuid.random()}")).id
        (1..3).forEach { post<CommunityComment>("/v1/community/posts/$postId/comments", user.token, CreateCommentRequest("izoh $it")) }
        val firstComments = get<Page<ModerationCommentView>>("/v1/admin/community/posts/$postId/comments?limit=2", admin)
        assertEquals(listOf("izoh 1", "izoh 2"), firstComments.items.map { it.body })
        assertEquals(3L, firstComments.total)
        assertTrue(firstComments.hasMore)
        val moreComments = get<Page<ModerationCommentView>>("/v1/admin/community/posts/$postId/comments?limit=2&offset=2", admin)
        assertEquals(listOf("izoh 3"), moreComments.items.map { it.body })
        assertFalse(moreComments.hasMore)
        assertEquals(3, get<Page<ModerationCommentView>>("/v1/admin/community/posts/$postId/comments", admin).items.size)
    }

    /**
     * Profiles and private messages. The alias is the only handle on the wire; a block
     * or a closed door reads as the same refusal; and moderation sees a reported
     * message as text with an alias, like everything else in the room.
     */
    @Test
    fun `an alias has a profile with a bio and badges, and two aliases can message each other`() = api {
        val her = signUp().also { onboard(it) }
        val him = signUp().also { onboard(it) }
        val third = signUp().also { onboard(it) }
        val admin = adminToken()

        val me = get<CommunityIdentity>("/v1/community/me", her.token)
        assertTrue(CommunityBadge.NEWCOMER in me.badges, "a fresh alias is a newcomer: ${me.badges}")
        assertEquals(0, me.unreadMessages)

        // Bio: trimmed, capped, cleared by blank.
        val withBio = put<CommunityIdentity>("/v1/community/me", her.token, UpdateIdentityRequest(bio = "  Ikki bola, perimenopauza  "))
        assertEquals("Ikki bola, perimenopauza", withBio.bio)
        val tooLong = raw { client.put("/v1/community/me") { auth(her.token); json(UpdateIdentityRequest(bio = "x".repeat(Limits.BIO_MAX + 1))) } }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)

        // Her profile as he sees it, and as she sees it.
        val post = post<CommunityPost>("/v1/community/posts", her.token, CreatePostRequest(CommunityTopic.WELLBEING, "Profil test ${Uuid.random()}"))
        val encoded = me.alias.replace(" ", "%20")
        val seen = get<CommunityProfile>("/v1/community/profiles/$encoded", him.token)
        assertEquals(me.alias, seen.alias)
        assertEquals("Ikki bola, perimenopauza", seen.bio)
        assertEquals(1, seen.postCount)
        assertEquals(listOf(post.id), seen.posts.map { it.id })
        assertFalse(seen.isMe)
        assertTrue(seen.canMessage)
        assertTrue(CommunityBadge.NEWCOMER in seen.badges)
        val own = get<CommunityProfile>("/v1/community/profiles/$encoded", her.token)
        assertTrue(own.isMe)
        assertFalse(own.canMessage, "nobody messages herself")
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/profiles/Yoq%20Taxallus") { auth(him.token) } }.status)
        assertFalse(her.userId in rawGet("/v1/community/profiles/$encoded", him.token), "a profile never carries the account id")

        // He writes to her; she has one unread; opening it reads it.
        val started = post<ConversationThread>("/v1/community/conversations", him.token, StartConversationRequest(me.alias, "Salom, savolim bor"))
        assertEquals(me.alias, started.conversation.alias)
        assertEquals(1, started.messages.size)
        assertTrue(started.messages.single().isMine)
        assertEquals(1, get<CommunityIdentity>("/v1/community/me", her.token).unreadMessages)
        val hers = get<List<Conversation>>("/v1/community/conversations", her.token)
        assertEquals(1, hers.single().unread)
        assertEquals("Salom, savolim bor", hers.single().lastMessage)
        val thread = get<ConversationThread>("/v1/community/conversations/${started.conversation.id}", her.token)
        assertFalse(thread.messages.single().isMine)
        assertEquals(0, get<CommunityIdentity>("/v1/community/me", her.token).unreadMessages, "opening the thread reads it")

        // A second start from either side lands in the same thread. From his thread the
        // alias on the conversation is hers; hers names him by his own identity.
        val hisAlias = get<CommunityIdentity>("/v1/community/me", him.token).alias
        val again = post<ConversationThread>("/v1/community/conversations", her.token, StartConversationRequest(hisAlias, "Marhamat"))
        assertEquals(hisAlias, again.conversation.alias)
        assertEquals(started.conversation.id, again.conversation.id)
        assertEquals(2, again.messages.size)
        val reply = post<DirectMessage>("/v1/community/conversations/${started.conversation.id}/messages", him.token, SendMessageRequest("Rahmat"))
        assertTrue(reply.isMine)
        assertEquals(1, get<List<Conversation>>("/v1/community/conversations", her.token).single().unread)

        // Refusals: yourself, a stranger's thread, an empty line, a closed door, a block.
        assertEquals(HttpStatusCode.BadRequest, raw { client.post("/v1/community/conversations") { auth(her.token); json(StartConversationRequest(me.alias, "o'zimga")) } }.status)
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/conversations/${started.conversation.id}") { auth(third.token) } }.status)
        assertEquals(HttpStatusCode.BadRequest, raw { client.post("/v1/community/conversations/${started.conversation.id}/messages") { auth(him.token); json(SendMessageRequest("   ")) } }.status)
        put<CommunityIdentity>("/v1/community/me", her.token, UpdateIdentityRequest(dmOpen = false))
        assertFalse(get<CommunityProfile>("/v1/community/profiles/$encoded", third.token).canMessage)
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/community/conversations") { auth(third.token); json(StartConversationRequest(me.alias, "salom")) } }.status)
        put<CommunityIdentity>("/v1/community/me", her.token, UpdateIdentityRequest(dmOpen = true))

        val hisEncoded = hisAlias.replace(" ", "%20")
        assertEquals(BlockState(true), put<BlockState>("/v1/community/profiles/$hisEncoded/block", her.token))
        assertTrue(get<CommunityProfile>("/v1/community/profiles/$hisEncoded", her.token).blocked)
        assertFalse(get<CommunityProfile>("/v1/community/profiles/$encoded", him.token).canMessage, "a block closes the door from his side too")
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/community/conversations/${started.conversation.id}/messages") { auth(him.token); json(SendMessageRequest("hali ham")) } }.status)
        assertTrue(get<List<Conversation>>("/v1/community/conversations", him.token).single().blocked)
        val unblocked = raw { client.delete("/v1/community/profiles/$hisEncoded/block") { auth(her.token) } }
        assertEquals(HttpStatusCode.OK, unblocked.status)
        assertTrue(get<CommunityProfile>("/v1/community/profiles/$encoded", him.token).canMessage)

        // Reporting a thread reports his latest line; the queue shows it as a message.
        postAck("/v1/community/conversations/${started.conversation.id}/report", her.token, ReportRequest(ReportReason.ABUSE, "test"))
        assertEquals(HttpStatusCode.Conflict, raw { client.post("/v1/community/conversations/${started.conversation.id}/report") { auth(her.token); json(ReportRequest(ReportReason.SPAM)) } }.status)
        val reports = get<Page<ModerationReportView>>("/v1/admin/community/reports?open=true&limit=200", admin)
        val open = assertNotNull(reports.items.firstOrNull { it.messageId == reply.id })
        assertEquals("Rahmat", open.excerpt)
        assertFalse(him.userId in rawGet("/v1/admin/community/reports?open=true&limit=200", admin))
        postAck("/v1/admin/community/reports/${open.id}/resolve", admin, ResolveReportRequest("hide", "integration test"))
        val afterHide = get<ConversationThread>("/v1/community/conversations/${started.conversation.id}", her.token)
        assertTrue(afterHide.messages.none { it.id == reply.id }, "a hidden message leaves the thread")
        val auditRow = rawGet("/v1/admin/audit?action=community.report_resolved&entityId=${reply.id}&limit=5", admin)
        assertTrue("\"entityType\":\"community_message\"" in auditRow, "the audit log names the message, not a null comment: $auditRow")

        // The badge rules, exercised at their thresholds rather than assumed.
        repeat(CommunityBadges.WRITER_POSTS - 1) { index ->
            post<CommunityPost>("/v1/community/posts", her.token, CreatePostRequest(CommunityTopic.BODY, "Yozuvchi $index ${Uuid.random()}"))
        }
        assertTrue(CommunityBadge.WRITER in get<CommunityIdentity>("/v1/community/me", her.token).badges)
        val feed = get<Page<CommunityPost>>("/v1/community/posts?limit=100", him.token)
        assertTrue(feed.items.first { it.alias == me.alias }.badges.contains(CommunityBadge.WRITER), "badges ride on the feed's cards")
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

    // ---------------------------------------------------------------- insights

    @Test
    fun `insights report what was logged, and nothing at all when nothing was`() = api {
        val user = signUp().also { onboard(it, storeHealth = true) }

        val empty = get<InsightsSummary>("/v1/insights?days=7", user.token)
        assertEquals(7, empty.days)
        assertEquals(0, empty.daysLogged)
        assertTrue(empty.isEmpty)
        assertTrue(
            empty.trends.all { it.average == null && it.daysWithData == 0 },
            "an average over nothing is null, never zero: ${empty.trends}",
        )
        assertTrue(empty.trends.all { it.points.size == 7 }, "every day is a point, gaps included")
        assertFalse(empty.findingsAvailable, "the narrative is Premium")
        assertTrue(empty.findings.isEmpty())

        // Log something and the same window reports it — and only it.
        postAck("/v1/nutrition/water", user.token, uz.sadora.contract.AddWaterRequest(700))
        put<uz.sadora.contract.MindCheckIn>("/v1/mind/check-in", user.token, MindCheckIn(MoodLevel.GOOD, energy = 4, stress = 2))

        val logged = get<InsightsSummary>("/v1/insights?days=7", user.token)
        assertEquals(1, logged.daysLogged)
        val water = assertNotNull(logged.trend(TrendMetric.WATER_ML))
        assertEquals(700.0, water.average)
        assertEquals(1, water.daysWithData)
        assertEquals(6, water.points.count { it.value == null }, "the other six days stay gaps")
        assertEquals(4.0, logged.trend(TrendMetric.MOOD)?.average, "GOOD is 4 on the five-step scale")
        assertNull(water.previousAverage, "nothing was logged in the window before, so there is nothing to compare")
        assertNull(water.change)
        assertTrue(logged.findings.isEmpty(), "one day is never a finding")
    }

    @Test
    fun `a longer window is refused without the subscription and served with it`() = api {
        val user = signUp().also { onboard(it) }
        val admin = adminToken()

        val refused = raw { client.get("/v1/insights?days=30") { auth(user.token) } }
        assertEquals(HttpStatusCode.PaymentRequired, refused.status)
        assertEquals(ErrorCodes.ENTITLEMENT_REQUIRED, refused.body<ApiErrorResponse>().error.code)

        val invalid = raw { client.get("/v1/insights?days=14") { auth(user.token) } }
        assertEquals(HttpStatusCode.BadRequest, invalid.status, "only 7, 30 and 90 are windows")

        postAck(
            "/v1/admin/users/${user.userId}/premium",
            admin,
            uz.sadora.server.admin.GrantPremiumRequest(reason = "integration test"),
        )

        val granted = get<InsightsSummary>("/v1/insights?days=30", user.token)
        assertEquals(30, granted.days)
        assertEquals(30, granted.trends.first().points.size)
        assertTrue(granted.findingsAvailable, "Premium carries the narrative")
    }

    // ---------------------------------------------------------------- billing

    @Test
    fun `a Payme payment grants exactly one subscription, however many times it is delivered`() = api {
        val user = signUp().also { onboard(it) }
        val admin = adminToken()
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)

        val catalogue = get<BillingCatalogue>("/v1/billing/plans", user.token)
        val plan = assertNotNull(catalogue.plans.firstOrNull { it.id == "premium_year" })

        val session = post<CheckoutSession>(
            "/v1/billing/checkout",
            user.token,
            CheckoutRequest(plan.id, PaymentProvider.PAYME),
        )
        assertTrue(session.url.startsWith("https://checkout.paycom.uz/"), session.url)
        assertEquals(plan.priceMinor, session.amountMinor)

        val order = session.transactionId
        val paymeId = "pm-${Random.nextInt(1_000_000)}"

        // Unauthorised callers get Payme's own envelope, not the app's.
        val refused = payme("""{"id":1,"method":"CheckPerformTransaction"}""", auth = false)
        assertEquals(-32504, refused.errorCode())

        // The amount is checked to the tiyin.
        assertEquals(
            -31001,
            payme(
                """{"id":1,"method":"CheckPerformTransaction","params":{"amount":1,"account":{"order_id":"$order"}}}""",
            ).errorCode(),
        )

        payme(
            """{"id":2,"method":"CreateTransaction","params":{"id":"$paymeId","time":1788600000000,""" +
                """"amount":${plan.priceMinor},"account":{"order_id":"$order"}}}""",
        )
        // Payme asks twice; the second must describe the same transaction, not make one.
        payme(
            """{"id":3,"method":"CreateTransaction","params":{"id":"$paymeId","time":1788600000000,""" +
                """"amount":${plan.priceMinor},"account":{"order_id":"$order"}}}""",
        )

        repeat(3) { payme("""{"id":4,"method":"PerformTransaction","params":{"id":"$paymeId"}}""") }

        val status = get<PaymentStatus>("/v1/billing/payments/$order", user.token)
        assertEquals(PaymentState.PAID, status.state)
        assertEquals(SubscriptionTier.PREMIUM, assertNotNull(status.subscription).tier)

        // Three deliveries of "performed", one subscription.
        val entitlements = get<Entitlements>("/v1/entitlements", user.token)
        assertEquals(SubscriptionTier.PREMIUM, entitlements.tier)
        assertEquals(SubscriptionSource.PAYME, entitlements.source)
    }

    @Test
    fun `a Click callback without the right signature changes nothing`() = api {
        val user = signUp().also { onboard(it) }
        val admin = adminToken()
        setFlag(admin, BillingService.CLICK_FLAG, enabled = true)

        val session = post<CheckoutSession>(
            "/v1/billing/checkout",
            user.token,
            CheckoutRequest("premium_month", PaymentProvider.CLICK),
        )
        val order = session.transactionId
        val clickId = Random.nextInt(1_000_000).toString()
        val signTime = "2026-09-05 10:00:00"

        val forged = click(
            "prepare",
            mapOf(
                "click_trans_id" to clickId,
                "service_id" to "12345",
                "merchant_trans_id" to order,
                "amount" to "39900.00",
                "action" to "0",
                "error" to "0",
                "sign_time" to signTime,
                "sign_string" to "0000000000000000000000000000dead",
            ),
        )
        assertEquals(-1, forged["error"]?.jsonPrimitive?.int)
        assertEquals(
            PaymentState.PENDING,
            get<PaymentStatus>("/v1/billing/payments/$order", user.token).state,
            "a bad signature must not move the order at all",
        )

        // The same call, signed, is accepted.
        val prepareSign = clickSignature(
            clickId + "12345" + "test_click_secret" + order + "39900.00" + "0" + signTime,
        )
        val prepared = click(
            "prepare",
            mapOf(
                "click_trans_id" to clickId,
                "service_id" to "12345",
                "merchant_trans_id" to order,
                "amount" to "39900.00",
                "action" to "0",
                "error" to "0",
                "sign_time" to signTime,
                "sign_string" to prepareSign,
            ),
        )
        assertEquals(0, prepared["error"]?.jsonPrimitive?.int)

        val completeSign = clickSignature(
            clickId + "12345" + "test_click_secret" + order + order + "39900.00" + "1" + signTime,
        )
        val completeForm = mapOf(
            "click_trans_id" to clickId,
            "service_id" to "12345",
            "merchant_trans_id" to order,
            "merchant_prepare_id" to order,
            "amount" to "39900.00",
            "action" to "1",
            "error" to "0",
            "sign_time" to signTime,
            "sign_string" to completeSign,
        )
        assertEquals(0, click("complete", completeForm)["error"]?.jsonPrimitive?.int)
        // Click retries; the retry must not buy a second month.
        assertEquals(0, click("complete", completeForm)["error"]?.jsonPrimitive?.int)

        val status = get<PaymentStatus>("/v1/billing/payments/$order", user.token)
        assertEquals(PaymentState.PAID, status.state)
        assertEquals(SubscriptionSource.CLICK, assertNotNull(status.subscription).source)
    }

    @Test
    fun `a store receipt is refused while there is nothing to verify it with`() = api {
        val user = signUp().also { onboard(it) }

        val response = raw {
            client.post("/v1/billing/store/verify") {
                auth(user.token)
                json(StorePurchaseRequest(PaymentProvider.GOOGLE_PLAY, "premium_year", "made-up-token"))
            }
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            SubscriptionTier.FREE,
            get<Entitlements>("/v1/entitlements", user.token).tier,
            "an unverifiable receipt must never grant anything",
        )
    }

    /**
     * A verified receipt still belongs to one account: the one the app bought it for. The
     * same receipt posted from a second account is refused, and posted again from its own
     * account it changes nothing.
     */
    @Test
    fun `a store purchase grants Premium only to the account it was bought for, once`() = api {
        val buyer = signUp().also { onboard(it) }
        val other = signUp().also { onboard(it) }
        val verifier = uz.sadora.server.billing.StoreVerifier { _, productId, _ ->
            uz.sadora.server.billing.VerifiedPurchase(
                productId = productId,
                transactionId = "GPA.test-${buyer.userId}",
                expiresAt = now() + 30.minutes,
                autoRenewing = true,
                accountId = buyer.userId,
            )
        }
        val store = uz.sadora.server.billing.StorePurchaseService(
            component.billingRepository,
            component.subscriptionRepository,
            component.entitlementService,
            verifier,
        )
        val request = StorePurchaseRequest(PaymentProvider.GOOGLE_PLAY, "premium_month", "token")

        kotlin.test.assertFailsWith<uz.sadora.server.core.ValidationException> {
            store.verifyAndGrant(Uuid.parse(other.userId), request)
        }
        assertEquals(SubscriptionTier.FREE, get<Entitlements>("/v1/entitlements", other.token).tier)

        val buyerId = Uuid.parse(buyer.userId)
        store.verifyAndGrant(buyerId, request)
        assertEquals(SubscriptionTier.PREMIUM, get<Entitlements>("/v1/entitlements", buyer.token).tier)
        val firstExpiry = component.entitlementService.subscriptionStatus(buyerId).expiresAt
        store.verifyAndGrant(buyerId, request)
        assertEquals(firstExpiry, component.entitlementService.subscriptionStatus(buyerId).expiresAt, "a replayed receipt adds nothing")
        assertEquals(1, countRowsFor(buyerId, "payment_transactions"))
    }

    @Test
    fun `another account cannot read a payment it did not make`() = api {
        val user = signUp().also { onboard(it) }
        val stranger = signUp().also { onboard(it) }
        val admin = adminToken()
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)

        val session = post<CheckoutSession>(
            "/v1/billing/checkout",
            user.token,
            CheckoutRequest("premium_year", PaymentProvider.PAYME),
        )

        val response = raw { client.get("/v1/billing/payments/${session.transactionId}") { auth(stranger.token) } }
        assertEquals(HttpStatusCode.NotFound, response.status, "not a 403: its existence is not their business")
    }

    // ---------------------------------------------------------------- Bilim

    @Test
    fun `a draft is invisible to the app until it is published`() = api {
        val user = signUp().also { onboard(it) }
        val admin = adminToken()
        val slug = "qoralama-${Random.nextInt(100_000)}"

        post<AdminArticle>(
            "/v1/admin/content/articles",
            admin,
            CreateArticleRequest(
                slug = slug,
                article = SaveArticleRequest(
                    kind = ArticleKind.ARTICLE,
                    categoryKey = "sleep",
                    title = "Qoralama sarlavha",
                    excerpt = "Hali chop etilmagan.",
                    blocks = listOf(ArticleBlock.Paragraph("Matn.")),
                ),
            ),
        )

        val hidden = get<ArticleFeed>("/v1/articles", user.token)
        assertTrue(hidden.articles.none { it.slug == slug }, "a draft is not in the library")
        assertEquals(
            HttpStatusCode.NotFound,
            raw { client.get("/v1/articles/$slug") { auth(user.token) } }.status,
            "asking for a draft by name must not reveal that it exists as anything else",
        )

        put<AdminArticle>("/v1/admin/content/articles/$slug/published", admin, PublishArticleRequest(true))

        val published = get<ArticleFeed>("/v1/articles", user.token)
        val card = assertNotNull(published.articles.firstOrNull { it.slug == slug })
        assertFalse(card.locked, "a free article is never locked")
        assertEquals(1, card.readMinutes, "a short body still reads as a minute")
    }

    @Test
    fun `a premium article is listed for everyone and opens only with the subscription`() = api {
        val user = signUp().also { onboard(it) }
        val admin = adminToken()
        val slug = "premium-${Random.nextInt(100_000)}"

        post<AdminArticle>(
            "/v1/admin/content/articles",
            admin,
            CreateArticleRequest(
                slug = slug,
                article = SaveArticleRequest(
                    kind = ArticleKind.COURSE,
                    categoryKey = "cycle",
                    title = "Premium kurs",
                    excerpt = "Faqat obunachilarga.",
                    blocks = listOf(
                        ArticleBlock.Paragraph("Ochiq xatboshi."),
                        ArticleBlock.Heading("Ichkarida"),
                        ArticleBlock.Paragraph("Yopiq xatboshi."),
                    ),
                    premium = true,
                ),
            ),
        )
        put<AdminArticle>("/v1/admin/content/articles/$slug/published", admin, PublishArticleRequest(true))

        // Listed, so she can see what Premium holds — but the body stops after the opening.
        val feed = get<ArticleFeed>("/v1/articles", user.token)
        val card = assertNotNull(feed.articles.firstOrNull { it.slug == slug })
        assertTrue(card.premium && card.locked)

        val locked = get<Article>("/v1/articles/$slug", user.token)
        assertTrue(locked.truncated)
        assertEquals(listOf(ArticleBlock.Paragraph("Ochiq xatboshi.")), locked.blocks)

        postAck(
            "/v1/admin/users/${user.userId}/premium",
            admin,
            uz.sadora.server.admin.GrantPremiumRequest(reason = "integration test"),
        )

        val opened = get<Article>("/v1/articles/$slug", user.token)
        assertFalse(opened.truncated)
        assertEquals(3, opened.blocks.size)
        assertFalse(opened.summary.locked)
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
    fun `the analytics page counts today's sign-up and opener in the current window`() = api {
        val user = signUp().also { onboard(it) }
        // Opening the app is what the streak's check-in records; that is the DAU the page draws.
        post<DailyCheckInResult>("/v1/rewards/check-in", user.token, Unit)

        val report = get<AdminAnalytics>("/v1/admin/stats/analytics?days=7", adminToken())

        assertEquals(7, report.perDay.size, "one entry per day, dense")
        assertEquals(7, report.days)
        val today = report.perDay.last()
        assertTrue(today.signUps >= 1, "$today")
        assertTrue(today.activeUsers >= 1, "$today")
        assertTrue(report.current.signUps >= 1)
        assertEquals(listOf(1, 7, 30), report.retention.map { it.horizonDays })
        assertTrue(report.retention.all { it.returned <= it.cohort })
        val funnel = report.funnel.associate { it.key to it.count }
        assertTrue((funnel["registered"] ?: 0) >= (funnel["onboarded"] ?: 0), "$funnel")
        assertTrue((funnel["premium_now"] ?: 0) >= (funnel["paying"] ?: 0), "$funnel")
        assertTrue(report.platforms.any { it.key == "android" && it.count >= 1 }, "${report.platforms}")
        assertEquals(listOf("1-2", "3-6", "7-13", "14-29", "30+", "lapsed"), report.streaks.map { it.key })
        assertEquals(4, report.consents.size)

        // Support's job is one account at a time; the cohort page is not hers.
        val support = adminAccount(role = "SUPPORT")
        val refused = client.get("/v1/admin/stats/analytics") { auth(support.token) }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
    }

    @Test
    fun `without AI consent the answer is general and says so`() = api {
        val user = signUp().also { onboard(it, storeHealth = true, aiInsights = false) }
        val reply = post<AiChatReply>("/v1/ai/chat", user.token, AiChatRequest("Nega charchayapman?"))
        assertEquals("", reply.basedOn)
        assertTrue("roziligisiz" in reply.answer, reply.answer)
    }

    // ---------------------------------------------------------------- helpers

    private class TestUser(val token: String, val userId: String, val refreshToken: String)

    private val wireJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    /**
     * One JSON-speaking client per test. Named rather than exposed as `client`, because
     * [ApplicationTestBuilder] has a `client` of its own that knows no JSON, and a member
     * always wins over an extension.
     */
    /**
     * A refusal reaches her in the language her app is in. The code and the field names
     * are what the app branches on, so they stay exactly the same in every language.
     */
    @Test
    fun `a refusal is worded in the language the app asked for`() = api {
        suspend fun refusal(language: String?) = client.post("/v1/auth/otp/request") {
            language?.let { header(HttpHeaders.AcceptLanguage, it) }
            json(OtpRequest("12345"))
        }.body<ApiErrorResponse>().error

        val uzbek = refusal(null)
        val ru = refusal("ru")
        val en = refusal("en-US,en;q=0.9")

        assertEquals(ErrorCodes.VALIDATION_FAILED, ru.code)
        assertEquals(uzbek.code, en.code)
        assertEquals(uzbek.details.keys, ru.details.keys)
        assertEquals("So'rov ma'lumotlari noto'g'ri", uzbek.message)
        assertEquals("Неверные данные запроса", ru.message)
        assertEquals("The request data is invalid", en.message)
        val reason = uzbek.details.values.single()
        assertEquals(uz.sadora.server.i18n.ErrorText.translate(reason, uz.sadora.contract.Language.RU), ru.details.values.single())
        assertTrue(ru.details.values.single() != reason, "the reason stayed in Uzbek: $reason")
    }

    private class Api(val client: HttpClient)

    /**
     * A consent link is a URL, and URLs get forwarded. One started by one account and
     * approved in someone else's browser must not land the second person's WHOOP data in
     * the first account — nor in the second, which never asked. The state is spent, too.
     */
    @Test
    fun `a WHOOP consent started by one account cannot be completed by another`() = api {
        val starter = Uuid.parse(signUp().userId)
        val other = Uuid.parse(signUp().userId)
        // Configured, but pointed at a closed port: the check must refuse before any call.
        val whoop = uz.sadora.server.config.WhoopConfig(
            clientId = "id",
            clientSecret = "secret",
            redirectUri = "http://localhost:8080/v1/wearables/whoop/callback",
            apiBaseUrl = "http://127.0.0.1:1",
        )
        val service = uz.sadora.server.wearable.WearableConnectService(
            connections = component.connectionRepository,
            wearables = component.wearableService,
            access = component.healthAccess,
            audit = component.auditService,
            cipher = uz.sadora.server.core.TokenCipher.from(null, fallbackSecret = "x".repeat(40)),
            whoopConfig = whoop,
            whoop = uz.sadora.server.wearable.whoop.WhoopClient(io.ktor.client.HttpClient(), whoop),
        )
        component.connectionRepository.saveState("forwarded-state", starter, HealthProvider.WHOOP, now() + 10.minutes)

        kotlin.test.assertFailsWith<uz.sadora.server.core.ValidationException> {
            service.completeConnect(HealthProvider.WHOOP, "forwarded-state", "code", caller = other)
        }
        kotlin.test.assertFailsWith<uz.sadora.server.core.ValidationException> {
            service.completeConnect(HealthProvider.WHOOP, "forwarded-state", "code", caller = starter)
        }
        assertEquals(null, component.connectionRepository.find(starter, HealthProvider.WHOOP))
        assertEquals(null, component.connectionRepository.find(other, HealthProvider.WHOOP))
    }

    // ---------------------------------------------------------------- doctors

    @Test
    fun `a doctor applies, is approved, writes with a check mark, and a suspension takes it down`() = api {
        val doctor = signUp().also { onboard(it) }
        val asker = signUp().also { onboard(it) }
        val admin = adminToken()
        val jpeg = kotlin.io.encoding.Base64.encode(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
        val name = "Dr Test ${Uuid.random().toString().take(6)}"

        // Her alias post, written before she became a doctor, stays an alias post.
        val aliasBody = "Alias post ${Uuid.random()}"
        val aliasPost = post<CommunityPost>("/v1/community/posts", doctor.token, CreatePostRequest(CommunityTopic.CYCLE, aliasBody))
        val alias = aliasPost.alias

        assertEquals(DoctorStatus.NONE, get<DoctorAccount>("/v1/doctor/me", doctor.token).status)
        val noDocuments = raw {
            client.post("/v1/doctor/application") {
                auth(doctor.token)
                json(DoctorApplicationRequest(name, DoctorSpecialty.GYNECOLOGIST, "Klinika", 8, "LIC-1", documents = emptyList()))
            }
        }
        assertEquals(HttpStatusCode.BadRequest, noDocuments.status)

        val application = DoctorApplicationRequest(
            fullName = name,
            specialty = DoctorSpecialty.GYNECOLOGIST,
            workplace = "Toshkent, 1-klinika",
            experienceYears = 8,
            licenseNumber = "LIC-12345",
            bio = "Ginekolog",
            documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, jpeg)),
        )
        val pending = post<DoctorAccount>("/v1/doctor/application", doctor.token, application)
        assertEquals(DoctorStatus.PENDING, pending.status)
        assertEquals(1, pending.documentCount)
        val again = raw { client.post("/v1/doctor/application") { auth(doctor.token); json(application) } }
        assertEquals(HttpStatusCode.Conflict, again.status, "a pending application waits for its answer")

        // Before approval she still writes as her alias, and the questions list is closed.
        assertEquals(HttpStatusCode.Forbidden, raw { client.get("/v1/doctor/questions") { auth(doctor.token) } }.status)

        val queue = get<Page<uz.sadora.server.doctor.AdminDoctorRow>>("/v1/admin/doctors?status=pending", admin)
        val row = assertNotNull(queue.items.firstOrNull { it.fullName == name })
        val detail = get<uz.sadora.server.doctor.AdminDoctorDetail>("/v1/admin/doctors/${row.id}", admin)
        assertEquals("LIC-12345", detail.licenseNumber)
        val document = raw { client.get("/v1/admin/doctors/${row.id}/documents/${detail.documents.single().id}") { auth(admin) } }
        assertEquals(HttpStatusCode.OK, document.status)
        assertEquals("image/jpeg", document.headers["Content-Type"])
        assertEquals("no-store", document.headers["Cache-Control"])

        val noNote = raw {
            client.post("/v1/admin/doctors/${row.id}/review") { auth(admin); json(uz.sadora.server.doctor.DoctorReviewRequest("reject")) }
        }
        assertEquals(HttpStatusCode.BadRequest, noNote.status, "a rejection needs a reason")
        postAck("/v1/admin/doctors/${row.id}/review", admin, uz.sadora.server.doctor.DoctorReviewRequest("approve"))
        val approved = get<DoctorAccount>("/v1/doctor/me", doctor.token)
        assertEquals(DoctorStatus.APPROVED, approved.status)
        assertEquals(row.id, approved.profileId)

        // A question from someone else, then the doctor's own post and her answer.
        val question = post<CommunityPost>(
            "/v1/community/posts",
            asker.token,
            CreatePostRequest(CommunityTopic.CYCLE, "Savol ${Uuid.random()}"),
        )
        val waiting = get<List<CommunityPost>>("/v1/doctor/questions", doctor.token)
        assertTrue(waiting.any { it.id == question.id }, "an unanswered question is on her list")

        val doctorPost = post<CommunityPost>(
            "/v1/community/posts",
            doctor.token,
            CreatePostRequest(CommunityTopic.WELLBEING, "Shifokor maslahati ${Uuid.random()}"),
        )
        val byline = assertNotNull(doctorPost.doctor, "an approved doctor writes as herself")
        assertEquals(name, byline.fullName)
        assertEquals(name, doctorPost.alias)
        assertTrue(doctorPost.badges.isEmpty(), "no alias badges on a doctor post")

        post<CommunityComment>("/v1/community/posts/${question.id}/comments", asker.token, CreateCommentRequest("Men ham bilmoqchiman"))
        val answer = post<CommunityComment>("/v1/community/posts/${question.id}/comments", doctor.token, CreateCommentRequest("Shifokor javobi"))
        assertEquals(name, answer.doctor?.fullName)
        val thread = get<List<CommunityComment>>("/v1/community/posts/${question.id}/comments", asker.token)
        assertEquals(answer.id, thread.first().id, "the doctor's answer leads the thread")
        assertEquals(1, get<CommunityPost>("/v1/community/posts/${question.id}", asker.token).doctorAnswers)
        assertTrue(get<List<CommunityPost>>("/v1/doctor/questions", doctor.token).none { it.id == question.id })

        val doctorsOnly = get<Page<CommunityPost>>("/v1/community/posts?doctors=true&limit=100", asker.token)
        assertTrue(doctorsOnly.items.isNotEmpty() && doctorsOnly.items.all { it.doctor != null })
        assertTrue(doctorsOnly.items.any { it.id == doctorPost.id })

        val page = get<DoctorProfile>("/v1/doctors/${row.id}", asker.token)
        assertEquals(1, page.postCount)
        assertEquals(1, page.answerCount)
        assertTrue(page.posts.none { it.id == aliasPost.id }, "her alias posts never reach her doctor page")
        val aliasPage = get<CommunityProfile>("/v1/community/profiles/${alias.encodeURLPathPart()}", asker.token)
        assertTrue(aliasPage.posts.none { it.id == doctorPost.id }, "her doctor posts never reach her alias page")
        assertTrue(get<List<DoctorListItem>>("/v1/doctors", asker.token).any { it.id == row.id })

        val edited = put<DoctorAccount>("/v1/doctor/me", doctor.token, UpdateDoctorProfileRequest(bio = "Yangi bio"))
        assertEquals("Yangi bio", edited.bio)

        // The moderation queue shows her name on a doctor post, never her alias.
        val moderation = get<Page<ModerationPostView>>("/v1/admin/community/posts?limit=200", admin)
        val moderated = assertNotNull(moderation.items.firstOrNull { it.id == doctorPost.id })
        assertTrue(moderated.byDoctor)
        assertEquals(name, moderated.alias)

        // A suspension takes her doctor posts and answers off the feed with her.
        postAck("/v1/admin/doctors/${row.id}/review", admin, uz.sadora.server.doctor.DoctorReviewRequest("suspend", "Tekshiruv"))
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/posts/${doctorPost.id}") { auth(asker.token) } }.status)
        assertTrue(get<List<CommunityComment>>("/v1/community/posts/${question.id}/comments", asker.token).none { it.id == answer.id })
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/doctors/${row.id}") { auth(asker.token) } }.status)
        val suspended = get<DoctorAccount>("/v1/doctor/me", doctor.token)
        assertEquals(DoctorStatus.SUSPENDED, suspended.status)
        assertEquals("Tekshiruv", suspended.reviewNote)
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/doctor/application") { auth(doctor.token); json(application) } }.status)

        postAck("/v1/admin/doctors/${row.id}/review", admin, uz.sadora.server.doctor.DoctorReviewRequest("reinstate"))
        assertEquals(doctorPost.id, get<CommunityPost>("/v1/community/posts/${doctorPost.id}", asker.token).id)
    }

    @Test
    fun `a rejected doctor may apply again and the new documents replace the old`() = api {
        val doctor = signUp().also { onboard(it) }
        val admin = adminToken()
        val jpeg = kotlin.io.encoding.Base64.encode(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9, 9))
        val first = DoctorApplicationRequest(
            fullName = "Dr Rejected ${Uuid.random().toString().take(6)}",
            specialty = DoctorSpecialty.ENDOCRINOLOGIST,
            workplace = "Klinika",
            experienceYears = 3,
            licenseNumber = "L-1",
            documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, jpeg), DoctorDocumentUpload(DoctorDocumentKind.LICENSE, jpeg)),
        )
        val id = post<DoctorAccount>("/v1/doctor/application", doctor.token, first).let {
            get<Page<uz.sadora.server.doctor.AdminDoctorRow>>("/v1/admin/doctors?status=pending&limit=200", admin)
                .items.single { row -> row.fullName == first.fullName }.id
        }
        postAck("/v1/admin/doctors/$id/review", admin, uz.sadora.server.doctor.DoctorReviewRequest("reject", "Diplom o'qilmaydi"))
        val rejected = get<DoctorAccount>("/v1/doctor/me", doctor.token)
        assertEquals(DoctorStatus.REJECTED, rejected.status)
        assertEquals("Diplom o'qilmaydi", rejected.reviewNote)
        val approveRejected = raw {
            client.post("/v1/admin/doctors/$id/review") { auth(admin); json(uz.sadora.server.doctor.DoctorReviewRequest("approve")) }
        }
        assertEquals(HttpStatusCode.Conflict, approveRejected.status, "only a pending application is approved")

        val resubmitted = post<DoctorAccount>(
            "/v1/doctor/application",
            doctor.token,
            first.copy(documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, jpeg))),
        )
        assertEquals(DoctorStatus.PENDING, resubmitted.status)
        assertEquals(null, resubmitted.reviewNote)
        assertEquals(1, resubmitted.documentCount)
        assertEquals(1, get<uz.sadora.server.doctor.AdminDoctorDetail>("/v1/admin/doctors/$id", admin).documents.size)
    }

    /**
     * "Ask Yaqinim to pay": she asks, he hears of it, pays by Payme from his own app — and
     * may switch the year she asked for to a month — and the days are hers. A refund takes
     * them back. Behind a store subscription of hers the days are banked, and spent the
     * day it is gone. A second open request is refused; his switch keeps him out of it.
     */
    @Test
    fun `a request to pay reaches Yaqinim and his payment becomes her Premium`() = api {
        val admin = adminToken()
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)
        val her = signUp().also { onboard(it) }
        val code = assertNotNull(post<PartnerInvite>("/v1/partner/invite", her.token, CreatePartnerInviteRequest()).code)
        val him = signUp()
        val followed = post<FollowedPerson>("/v1/partner/accept", him.token, AcceptPartnerInviteRequest(code, name = "Aziz", asPartnerAccount = true))
        post<PartnerState>("/v1/partner/approve", her.token, Ack())

        val asked = post<uz.sadora.contract.PaymentRequest>(
            "/v1/payment-requests",
            her.token,
            uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PREMIUM, uz.sadora.contract.BillingPeriod.YEAR, note = "  Iltimos  "),
        )
        assertTrue(asked.sentToPartner)
        assertEquals("Iltimos", asked.note)
        assertEquals(29900000, asked.amountMinor)
        assertTrue(asked.shareUrl.orEmpty().contains("/pr/"), asked.shareUrl.orEmpty())
        assertEquals(1, outboxCount(him, "payreq:"))
        val twice = raw {
            client.post("/v1/payment-requests") {
                auth(her.token)
                json(uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PREMIUM, uz.sadora.contract.BillingPeriod.MONTH))
            }
        }
        assertEquals(HttpStatusCode.Conflict, twice.status, "one open request at a time")

        // His side: her name, both gift plans, Payme.
        val incoming = get<List<uz.sadora.contract.IncomingPaymentRequest>>("/v1/payment-requests/incoming", him.token).single()
        assertEquals("Test", incoming.fromName)
        assertEquals(setOf("gift_month", "gift_year"), incoming.plans.map { it.id }.toSet())
        assertTrue(PaymentProvider.PAYME in incoming.providers)
        assertTrue(get<List<uz.sadora.contract.IncomingPaymentRequest>>("/v1/payment-requests/incoming", her.token).isEmpty())
        assertTrue(get<BillingCatalogue>("/v1/billing/plans", her.token).plans.none { it.id.startsWith("gift_") }, "the paywall sells no gift")

        // He switches to a month and pays it; Payme delivers twice.
        val checkout = post<CheckoutSession>(
            "/v1/payment-requests/${incoming.id}/checkout",
            him.token,
            uz.sadora.contract.PayPaymentRequest(PaymentProvider.PAYME, planId = "gift_month"),
        )
        assertEquals(3990000, checkout.amountMinor)
        val paymeId = "pm-g-${Random.nextInt(1_000_000)}"
        payme(
            """{"id":2,"method":"CreateTransaction","params":{"id":"$paymeId","time":1788600000000,""" +
                """"amount":3990000,"account":{"order_id":"${checkout.transactionId}"}}}""",
        )
        repeat(2) { payme("""{"id":4,"method":"PerformTransaction","params":{"id":"$paymeId"}}""") }
        assertEquals(PaymentState.PAID, get<PaymentStatus>("/v1/billing/payments/${checkout.transactionId}", him.token).state)

        val herState = get<uz.sadora.contract.PaymentRequestState>("/v1/payment-requests", her.token)
        assertEquals(uz.sadora.contract.PaymentRequestStatus.PAID, herState.current?.status)
        val premium = get<Entitlements>("/v1/entitlements", her.token)
        assertEquals(SubscriptionTier.PREMIUM, premium.tier)
        val days = (assertNotNull(premium.expiresAt) - now()).inWholeDays
        assertTrue(days in 29..30, "a month, not the year she asked for: $days")
        assertEquals(SubscriptionTier.FREE, get<Entitlements>("/v1/entitlements", him.token).tier, "the payer gets nothing")
        assertEquals(1, outboxCount(her, "payreq_paid:"))
        assertTrue(get<List<uz.sadora.contract.IncomingPaymentRequest>>("/v1/payment-requests/incoming", him.token).isEmpty())

        // The panel names who paid, and its refund takes the days back.
        val row = get<Page<uz.sadora.server.billing.AdminPaymentView>>("/v1/admin/billing/payments?limit=200", admin)
            .items.single { it.id == checkout.transactionId }
        assertTrue(row.gift && row.refundable)
        assertEquals(him.userId, row.payerId)
        assertEquals(her.userId, row.userId)
        postAck("/v1/admin/billing/payments/${checkout.transactionId}/refund", admin, Ack())
        assertEquals(SubscriptionTier.FREE, get<Entitlements>("/v1/entitlements", her.token).tier)
        assertEquals(1, outboxCount(her, "gift_refunded:"))

        // The browser link: the page, Payme's door, and closed once she takes it back.
        val second = post<uz.sadora.contract.PaymentRequest>(
            "/v1/payment-requests",
            her.token,
            uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PREMIUM, uz.sadora.contract.BillingPeriod.YEAR),
        )
        val path = "/pr/" + assertNotNull(second.shareUrl).substringAfter("/pr/")
        val page = client.get(path)
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("Test sizdan yordam"), "her name on the page")
        val noRedirects = client.config { followRedirects = false }
        val door = noRedirects.get("$path/pay?provider=payme&plan=gift_year")
        assertEquals(HttpStatusCode.Found, door.status)
        assertTrue(door.headers[HttpHeaders.Location].orEmpty().startsWith("https://checkout.paycom.uz/"))
        val rotated = post<uz.sadora.contract.PaymentRequest>("/v1/payment-requests/${second.id}/share", her.token, Ack())
        assertEquals(HttpStatusCode.NotFound, client.get(path).status, "a fresh link retires the old one")
        val freshPath = "/pr/" + assertNotNull(rotated.shareUrl).substringAfter("/pr/")
        assertEquals(uz.sadora.contract.PaymentRequestStatus.CANCELLED, raw { client.delete("/v1/payment-requests/${second.id}") { auth(her.token) } }.body<uz.sadora.contract.PaymentRequest>().status)
        assertTrue(client.get(freshPath).bodyAsText().contains("yopilgan"))

        // "Not now" closes it; she reads it as closed.
        val third = post<uz.sadora.contract.PaymentRequest>(
            "/v1/payment-requests",
            her.token,
            uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PREMIUM, uz.sadora.contract.BillingPeriod.MONTH),
        )
        postAck("/v1/payment-requests/${third.id}/decline", him.token, Ack())
        assertEquals(uz.sadora.contract.PaymentRequestStatus.DECLINED, get<uz.sadora.contract.PaymentRequestState>("/v1/payment-requests", her.token).current?.status)

        // Behind her own store subscription, a gift is banked and spent when it is gone.
        component.subscriptionRepository.grant(Uuid.parse(her.userId), SubscriptionSource.GOOGLE_PLAY, now() + 5.days, productId = "premium_month")
        val fourth = post<uz.sadora.contract.PaymentRequest>(
            "/v1/payment-requests",
            her.token,
            uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PREMIUM, uz.sadora.contract.BillingPeriod.MONTH),
        )
        val banked = post<CheckoutSession>("/v1/payment-requests/${fourth.id}/checkout", him.token, uz.sadora.contract.PayPaymentRequest(PaymentProvider.PAYME))
        assertEquals(HttpStatusCode.OK, raw { client.post("/v1/billing/dev-pay/${banked.transactionId}") }.status)
        assertEquals(SubscriptionSource.GOOGLE_PLAY, get<Entitlements>("/v1/entitlements", her.token).source, "her store subscription stands")
        component.subscriptionRepository.revoke(Uuid.parse(her.userId), "test: store lapsed")
        val spent = get<Entitlements>("/v1/entitlements", her.token)
        assertEquals(SubscriptionTier.PREMIUM, spent.tier)
        assertEquals(SubscriptionSource.MANUAL, spent.source)

        // His switch: off, and she is not offered him; a new request goes by link only.
        put<FollowedPerson>("/v1/partner/following/${followed.linkId}/payment-requests", him.token, uz.sadora.contract.PaymentRequestSwitch(false))
        assertEquals(false, get<PartnerState>("/v1/partner", her.token).link?.acceptsPaymentRequests)
        val fifth = post<uz.sadora.contract.PaymentRequest>(
            "/v1/payment-requests",
            her.token,
            uz.sadora.contract.CreatePaymentRequest(uz.sadora.contract.PaymentRequestKind.PREMIUM, uz.sadora.contract.BillingPeriod.MONTH),
        )
        assertFalse(fifth.sentToPartner)
        assertTrue(get<List<uz.sadora.contract.IncomingPaymentRequest>>("/v1/payment-requests/incoming", him.token).isEmpty())
    }

    private fun api(block: suspend Api.() -> Unit) = testApplication {
        application { apiModule(component) }
        val client = createClient {
            install(ContentNegotiation) { json(wireJson) }
        }
        Api(client).block()
    }

    @Test
    fun `a patient consults a doctor with photos and her record and a report opens the lines around it`() = api {
        val doctor = signUp().also { onboard(it) }
        val patient = signUp().also { onboard(it) }
        val stranger = signUp().also { onboard(it) }
        val admin = adminToken()
        val name = "Dr Chat ${Uuid.random().toString().take(6)}"
        val jpeg = kotlin.io.encoding.Base64.encode(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
        post<DoctorAccount>(
            "/v1/doctor/application",
            doctor.token,
            DoctorApplicationRequest(name, DoctorSpecialty.GYNECOLOGIST, "Klinika", 9, "LIC-9", documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, jpeg))),
        )
        val profileId = get<Page<uz.sadora.server.doctor.AdminDoctorRow>>("/v1/admin/doctors?status=pending&limit=200", admin)
            .items.first { it.fullName == name }.id
        postAck("/v1/admin/doctors/$profileId/review", admin, uz.sadora.server.doctor.DoctorReviewRequest("approve"))

        // Her page offers the consultation; her own page does not.
        val page = get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", patient.token)
        assertTrue(page.canMessage)
        assertNull(page.conversationId)
        assertFalse(get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", doctor.token).canMessage)

        // Opening it: a window, the doctor by name, the first line.
        val opened = post<ConversationThread>("/v1/doctors/$profileId/consultations", patient.token, uz.sadora.contract.StartConsultationRequest("Salom, doktor"))
        val id = opened.conversation.id
        assertEquals(name, opened.conversation.alias)
        assertEquals(profileId, opened.conversation.doctor?.id)
        val window = assertNotNull(opened.conversation.consultation)
        assertTrue(window.open)
        assertEquals(1, opened.messages.size)
        val again = post<ConversationThread>("/v1/doctors/$profileId/consultations", patient.token, uz.sadora.contract.StartConsultationRequest())
        assertEquals(id, again.conversation.id)
        assertEquals(window.expiresAt, again.conversation.consultation?.expiresAt, "a second tap does not extend an open window")
        assertEquals(id, get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", patient.token).conversationId)

        // The doctor's list names the patient; her own chat header does not count it.
        val patients = get<List<Conversation>>("/v1/community/conversations?scope=patients", doctor.token)
        val held = patients.single { it.id == id }
        assertEquals("Test", held.alias)
        assertEquals(LifeStage.CYCLE, held.patient?.lifeStage)
        assertNull(held.doctor)
        assertEquals(1, held.unread)
        assertTrue(get<List<Conversation>>("/v1/community/conversations?scope=personal", doctor.token).none { it.id == id })
        assertEquals(0, get<CommunityIdentity>("/v1/community/me", doctor.token).unreadMessages)
        assertTrue(get<List<Conversation>>("/v1/community/conversations?scope=personal", patient.token).any { it.id == id })
        assertTrue(get<List<Conversation>>("/v1/community/conversations?scope=patients", patient.token).isEmpty())

        // A photo: its size is read from the picture, and only the two of them may open it.
        val picture = java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(picture, "png", it) }.toByteArray()
        val photo = post<DirectMessage>(
            "/v1/community/conversations/$id/messages",
            patient.token,
            SendMessageRequest("Tahlil natijasi", image = uz.sadora.contract.MessageImageUpload(kotlin.io.encoding.Base64.encode(png), "image/png")),
        )
        assertEquals(uz.sadora.contract.MessageKind.IMAGE, photo.kind)
        assertEquals(40, photo.image?.width)
        assertEquals(30, photo.image?.height)
        val fetched = raw { client.get("/v1/community/conversations/$id/messages/${photo.id}/image") { auth(doctor.token) } }
        assertEquals(HttpStatusCode.OK, fetched.status)
        assertEquals("image/png", fetched.headers["Content-Type"])
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/conversations/$id/messages/${photo.id}/image") { auth(stranger.token) } }.status)
        val notAPicture = raw {
            client.post("/v1/community/conversations/$id/messages") {
                auth(patient.token)
                json(SendMessageRequest(image = uz.sadora.contract.MessageImageUpload(kotlin.io.encoding.Base64.encode(byteArrayOf(1, 2, 3)), "image/png")))
            }
        }
        assertEquals(HttpStatusCode.BadRequest, notAPicture.status)

        // Her record, attached by her, read live by her doctor; a doctor cannot attach one.
        val record = post<DirectMessage>("/v1/community/conversations/$id/messages", patient.token, SendMessageRequest(attachRecord = true))
        assertEquals(uz.sadora.contract.MessageKind.RECORD, record.kind)
        val summary = get<uz.sadora.contract.DoctorSummary>("/v1/community/conversations/$id/messages/${record.id}/record?lang=ru", doctor.token)
        assertEquals("Test", summary.person.name)
        assertEquals(Language.RU, summary.language)
        assertEquals(HttpStatusCode.BadRequest, raw { client.post("/v1/community/conversations/$id/messages") { auth(doctor.token); json(SendMessageRequest(attachRecord = true)) } }.status)
        assertTrue("consultation.record_viewed" in rawGet("/v1/admin/audit?action=consultation.record_viewed&entityId=${record.id}&limit=5", admin))

        // Ticks and typing: she types, he sees it; he opens the thread, her lines turn read.
        postAck("/v1/community/conversations/$id/typing", patient.token, uz.sadora.contract.Ack())
        val doctorView = get<ConversationThread>("/v1/community/conversations/$id", doctor.token)
        assertTrue(doctorView.otherTyping)
        assertTrue(doctorView.messages.none { it.read }, "nothing of his has been sent yet")
        val patientView = get<ConversationThread>("/v1/community/conversations/$id", patient.token)
        assertTrue(patientView.messages.filter { it.isMine }.all { it.read }, "the doctor opened the thread after every line")
        assertNotNull(patientView.otherReadAt)
        assertFalse(patientView.otherTyping, "the doctor is not typing")

        // The doctor closes it: no more lines, no more record; she opens it again.
        val closed = post<ConversationThread>("/v1/community/conversations/$id/close", doctor.token, uz.sadora.contract.Ack())
        assertEquals(false, closed.conversation.consultation?.open)
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/community/conversations/$id/close") { auth(patient.token) } }.status)
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/community/conversations/$id/messages") { auth(patient.token); json(SendMessageRequest("yana")) } }.status)
        assertEquals(HttpStatusCode.Forbidden, raw { client.get("/v1/community/conversations/$id/messages/${record.id}/record") { auth(doctor.token) } }.status)
        val reopened = post<ConversationThread>("/v1/doctors/$profileId/consultations", patient.token, uz.sadora.contract.StartConsultationRequest("Yana savol"))
        assertEquals(id, reopened.conversation.id)
        assertEquals(true, reopened.conversation.consultation?.open)
        post<DirectMessage>("/v1/community/conversations/$id/messages", doctor.token, SendMessageRequest("Marhamat, eshitaman"))

        // Her switch: off, and the button and the door close together.
        put<DoctorAccount>("/v1/doctor/me", doctor.token, uz.sadora.contract.UpdateDoctorProfileRequest(acceptsConsultations = false))
        assertFalse(get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", stranger.token).canMessage)
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/doctors/$profileId/consultations") { auth(stranger.token); json(uz.sadora.contract.StartConsultationRequest()) } }.status)
        put<DoctorAccount>("/v1/doctor/me", doctor.token, uz.sadora.contract.UpdateDoctorProfileRequest(acceptsConsultations = true))

        // The doctor reports the thread: the staff panel reads the lines around the line,
        // names the doctor by name and the patient by alias, and never shows the record.
        post<DirectMessage>("/v1/community/conversations/$id/messages", patient.token, SendMessageRequest("Qo'pol xabar"))
        postAck("/v1/community/conversations/$id/report", doctor.token, ReportRequest(ReportReason.ABUSE))
        val report = get<Page<ModerationReportView>>("/v1/admin/community/reports?open=true&limit=200", admin)
            .items.first { it.excerpt == "Qo'pol xabar" }
        assertTrue(report.consultation, "the queue says the line is from a consultation")
        assertEquals(uz.sadora.contract.MessageKind.TEXT, report.messageKind)
        val context = get<uz.sadora.server.community.ReportContextView>("/v1/admin/community/reports/${report.id}/context", admin)
        assertTrue(context.consultation)
        assertEquals("$name ✓", context.reporter)
        assertFalse(context.reportedIsDoctor)
        assertEquals("Qo'pol xabar", context.messages.single { it.reported }.body)
        assertTrue(context.messages.filter { it.kind == uz.sadora.contract.MessageKind.RECORD }.all { it.body.isEmpty() })
        assertFalse(patient.userId in rawGet("/v1/admin/community/reports/${report.id}/context", admin), "the context never carries an account id")
        assertTrue("community.report_context_viewed" in rawGet("/v1/admin/audit?action=community.report_context_viewed&limit=5", admin))
        val stats = get<uz.sadora.server.community.CommunityStatsView>("/v1/admin/community/stats", admin)
        assertTrue(stats.consultations >= 1 && stats.openMessageReports >= 1, "$stats")

        // Silencing the sender by the report stops her writing anywhere in the room.
        postAck("/v1/admin/community/reports/${report.id}/restrict-sender", admin, uz.sadora.server.community.RestrictAuthorRequest("haqorat", days = 1))
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/community/conversations/$id/messages") { auth(patient.token); json(SendMessageRequest("yana")) } }.status)

        // Her card in the doctors page counts, and says nothing more.
        val card = get<uz.sadora.server.doctor.AdminDoctorDetail>("/v1/admin/doctors/$profileId", admin)
        val counts = assertNotNull(card.consultations)
        assertEquals(1, counts.total)
        assertEquals(1, counts.messagesFromDoctor)
    }

    @Test
    fun `a paid consultation opens on payment and ends answered, rated, paid out, or refunded`() = api {
        val doctor = signUp().also { onboard(it) }
        val patient = signUp().also { onboard(it) }
        val admin = adminToken()
        setFlag(admin, BillingService.PAYME_FLAG, enabled = true)
        put<uz.sadora.server.consultation.CommissionView>("/v1/admin/settings/commission", admin, uz.sadora.server.consultation.SetCommissionRequest(10))
        val name = "Dr Pay ${Uuid.random().toString().take(6)}"
        val jpeg = kotlin.io.encoding.Base64.encode(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
        post<DoctorAccount>(
            "/v1/doctor/application",
            doctor.token,
            DoctorApplicationRequest(name, DoctorSpecialty.GYNECOLOGIST, "Klinika", 9, "LIC-P", documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, jpeg))),
        )
        val profileId = get<Page<uz.sadora.server.doctor.AdminDoctorRow>>("/v1/admin/doctors?status=pending&limit=200", admin)
            .items.first { it.fullName == name }.id
        postAck("/v1/admin/doctors/$profileId/review", admin, uz.sadora.server.doctor.DoctorReviewRequest("approve"))

        // Her settings: a price under Payme's minimum is refused; every day, all day.
        val price = 5_000_000L
        assertEquals(HttpStatusCode.BadRequest, raw { client.put("/v1/doctor/settings") { auth(doctor.token); json(uz.sadora.contract.UpdateDoctorSettingsRequest(priceMinor = 500)) } }.status)
        val allWeek = (1..7).map { uz.sadora.contract.DoctorHours(it, 0, 1440) }
        val settings = put<uz.sadora.contract.DoctorSettings>("/v1/doctor/settings", doctor.token, uz.sadora.contract.UpdateDoctorSettingsRequest(priceMinor = price, hours = allWeek))
        assertEquals(price, settings.priceMinor)
        assertEquals(10, settings.commissionPercent)
        assertEquals(7, settings.hours.size)

        paidPage(profileId, patient, price)
        val id = payAndOpen(profileId, doctor, patient, price)
        answerAndNote(id, doctor, patient)
        closeRateAndEarn(id, profileId, doctor, patient, admin, price)
        refundUnanswered(id, profileId, patient, admin)
        doctorBadges(doctor, patient)
        // The staff panel's quality table has her, with one unanswered window.
        val quality = qualityRow(profileId, admin)
        assertEquals(2, quality.consultationsTotal)
        assertEquals(1, quality.unansweredTotal)

        // Busy: still reachable, but not online.
        put<uz.sadora.contract.DoctorSettings>("/v1/doctor/settings", doctor.token, uz.sadora.contract.UpdateDoctorSettingsRequest(busy = true))
        assertEquals(false, get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", patient.token).availability?.onlineNow)
        put<uz.sadora.server.consultation.CommissionView>("/v1/admin/settings/commission", admin, uz.sadora.server.consultation.SetCommissionRequest(20))
    }

    private suspend fun Api.doctorBadges(doctor: TestUser, patient: TestUser) {
        // What she did is on her board: verified, a consultation, a note, a quick reply.
        val board = get<uz.sadora.contract.BadgeBoard>("/v1/doctor/badges", doctor.token)
        assertEquals(uz.sadora.contract.DoctorBadges.catalogue.size, board.badges.size)
        fun tier(key: String) = board.badges.first { it.key == key }.tier
        assertEquals(1, tier(uz.sadora.contract.DoctorBadges.VERIFIED))
        assertEquals(1, tier(uz.sadora.contract.DoctorBadges.CONSULTS))
        assertEquals(1, tier(uz.sadora.contract.DoctorBadges.QUICK_REPLIES))
        assertEquals(0, tier(uz.sadora.contract.DoctorBadges.RATED), "one review is short of five")
        assertEquals(1, board.badges.first { it.key == uz.sadora.contract.DoctorBadges.NOTES }.progress)
        assertFalse(board.canWear)
        assertTrue(board.unseen.any { it.key == uz.sadora.contract.DoctorBadges.VERIFIED && it.coins == 0 })
        // Seen once, and only hers: the patient is no doctor, and her own board is untouched.
        postAck("/v1/doctor/badges/seen", doctor.token, uz.sadora.contract.MarkBadgesSeenRequest())
        assertTrue(get<uz.sadora.contract.BadgeBoard>("/v1/doctor/badges", doctor.token).unseen.isEmpty())
        assertEquals(HttpStatusCode.Forbidden, raw { client.get("/v1/doctor/badges") { auth(patient.token) } }.status)
        assertTrue(get<uz.sadora.contract.BadgeBoard>("/v1/rewards/badges", doctor.token).badges.none { it.key == uz.sadora.contract.DoctorBadges.CONSULTS })
    }

    private suspend fun Api.paidPage(profileId: String, patient: TestUser, price: Long) {
        // Her page: the price, online now, and the ways to pay; the list says the same.
        val page = get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", patient.token)
        assertEquals(price, page.priceMinor)
        assertEquals(true, page.availability?.onlineNow)
        assertTrue(PaymentProvider.PAYME in page.paymentProviders)
        assertEquals(price, get<List<uz.sadora.contract.DoctorListItem>>("/v1/doctors", patient.token).single { it.id == profileId }.priceMinor)

    }

    private suspend fun Api.payAndOpen(profileId: String, doctor: TestUser, patient: TestUser, price: Long): String {
        // Asking without paying is answered with the price, and nothing opens.
        val refused = raw { client.post("/v1/doctors/$profileId/consultations") { auth(patient.token); json(uz.sadora.contract.StartConsultationRequest("Salom")) } }
        assertEquals(HttpStatusCode.PaymentRequired, refused.status)
        assertTrue(ErrorCodes.CONSULTATION_PAYMENT_REQUIRED in refused.bodyAsText())

        // Checkout, then Payme's own protocol: the window opens on "performed", once.
        val checkout = post<CheckoutSession>("/v1/doctors/$profileId/consultations/checkout", patient.token, uz.sadora.contract.ConsultationCheckoutRequest(PaymentProvider.PAYME))
        assertEquals(price, checkout.amountMinor)
        val again = post<CheckoutSession>("/v1/doctors/$profileId/consultations/checkout", patient.token, uz.sadora.contract.ConsultationCheckoutRequest(PaymentProvider.PAYME))
        assertEquals(price, again.amountMinor, "a second tap reuses the pending session")
        assertTrue(get<List<Conversation>>("/v1/community/conversations?scope=patients", doctor.token).isEmpty(), "unpaid, nothing to see")
        val paymeId = "pm-c-${Random.nextInt(1_000_000)}"
        payme(
            """{"id":2,"method":"CreateTransaction","params":{"id":"$paymeId","time":1788600000000,""" +
                """"amount":$price,"account":{"order_id":"${checkout.transactionId}"}}}""",
        )
        repeat(2) { payme("""{"id":4,"method":"PerformTransaction","params":{"id":"$paymeId"}}""") }
        val status = get<PaymentStatus>("/v1/billing/payments/${checkout.transactionId}", patient.token)
        assertEquals(PaymentState.PAID, status.state)
        assertNotNull(status.consultationSessionId)
        assertNull(status.subscription)

        val id = assertNotNull(get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", patient.token).conversationId)
        val opened = get<ConversationThread>("/v1/community/conversations/$id", patient.token).conversation.consultation
        assertEquals(true, opened?.open)
        assertEquals(uz.sadora.contract.ConsultationPayment.PAID, opened?.payment)
        assertEquals(false, opened?.canRate, "nothing to rate before she answers")
        assertTrue(dbQuery { exec("SELECT count(*) FROM notification_outbox WHERE dedupe_key = 'consultation_paid:${status.consultationSessionId}' AND target_app = 'doctor'") { it.next(); it.getInt(1) } } == 1)

        // A patient's line rings the doctor app, with a link to the thread.
        val line = post<DirectMessage>("/v1/community/conversations/$id/messages", patient.token, SendMessageRequest("Savolim bor"))
        val routed = dbQuery {
            exec("SELECT target_app, link FROM notification_outbox WHERE dedupe_key = 'dm:${line.id}'") { rows -> rows.next(); rows.getString(1) to rows.getString(2) }
        }
        assertEquals("doctor" to "sadora://conversation/$id", routed)

        return id
    }

    private suspend fun Api.answerAndNote(id: String, doctor: TestUser, patient: TestUser) {
        // She answers with a quick reply and keeps a note of her own.
        val reply = post<uz.sadora.contract.QuickReply>("/v1/doctor/quick-replies", doctor.token, uz.sadora.contract.SaveQuickReplyRequest("Salom", "Assalomu alaykum, eshitaman"))
        put<uz.sadora.contract.QuickReply>("/v1/doctor/quick-replies/${reply.id}", doctor.token, uz.sadora.contract.SaveQuickReplyRequest("Salom", "Assalomu alaykum!"))
        assertEquals("Assalomu alaykum!", get<List<uz.sadora.contract.QuickReply>>("/v1/doctor/quick-replies", doctor.token).single().body)
        post<DirectMessage>("/v1/community/conversations/$id/messages", doctor.token, SendMessageRequest("Assalomu alaykum!"))
        put<uz.sadora.contract.PatientNote>("/v1/doctor/patients/$id/note", doctor.token, uz.sadora.contract.SavePatientNoteRequest("Qon tahlili kerak"))
        assertEquals("Qon tahlili kerak", get<uz.sadora.contract.PatientNote>("/v1/doctor/patients/$id/note", doctor.token).body)
        assertEquals(HttpStatusCode.Forbidden, raw { client.get("/v1/doctor/patients/$id/note") { auth(patient.token) } }.status, "a patient is not a doctor")
        assertEquals(true, get<ConversationThread>("/v1/community/conversations/$id", patient.token).conversation.consultation?.canRate)

    }

    private suspend fun Api.closeRateAndEarn(id: String, profileId: String, doctor: TestUser, patient: TestUser, admin: String, price: Long) {
        // She closes it with her advice; the patient keeps it and rates, once.
        post<ConversationThread>("/v1/community/conversations/$id/close", doctor.token, uz.sadora.contract.CloseConsultationRequest("Ko'proq suv iching"))
        val closed = get<ConversationThread>("/v1/community/conversations/$id", patient.token).conversation.consultation
        assertEquals(false, closed?.open)
        assertEquals("Ko'proq suv iching", closed?.summary)
        assertEquals(HttpStatusCode.Forbidden, raw { client.post("/v1/community/conversations/$id/rating") { auth(doctor.token); json(uz.sadora.contract.RateConsultationRequest(5)) } }.status)
        postAck("/v1/community/conversations/$id/rating", patient.token, uz.sadora.contract.RateConsultationRequest(5, "Rahmat!"))
        assertEquals(HttpStatusCode.Conflict, raw { client.post("/v1/community/conversations/$id/rating") { auth(patient.token); json(uz.sadora.contract.RateConsultationRequest(4)) } }.status)
        val rated = get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", patient.token)
        assertEquals(5.0, rated.rating)
        assertEquals(1, rated.ratingCount)
        assertEquals("Rahmat!", get<List<uz.sadora.contract.DoctorReview>>("/v1/doctors/$profileId/reviews", patient.token).single().review)
        // The directory carries her reply habit; one answered window is too few for the badge.
        val listed = get<List<uz.sadora.contract.DoctorListItem>>("/v1/doctors", patient.token).single { it.id == profileId }
        assertEquals(1, listed.consultationsTotal)
        assertEquals(0, listed.avgFirstReplyMinutes)
        assertFalse(listed.fastReply)

        // Her numbers, her history of this patient, her money.
        val stats = get<uz.sadora.contract.DoctorStats>("/v1/doctor/stats", doctor.token)
        assertEquals(1, stats.consultationsTotal)
        assertEquals(0, stats.avgFirstReplyMinutes)
        assertEquals(5.0, stats.rating)
        val history = get<uz.sadora.contract.PatientHistory>("/v1/doctor/patients/$id/history", doctor.token)
        assertEquals("Ko'proq suv iching", history.sessions.single().summary)
        val earnings = get<uz.sadora.contract.DoctorEarnings>("/v1/doctor/earnings", doctor.token)
        assertEquals(price, earnings.grossMinor)
        assertEquals(price / 10, earnings.commissionMinor)
        assertEquals(price - price / 10, earnings.balanceMinor)
        val afterPayout = post<uz.sadora.contract.DoctorEarnings>("/v1/admin/doctors/$profileId/payouts", admin, uz.sadora.server.consultation.CreatePayoutRequest(1_000_000, "Oktyabr"))
        assertEquals(price - price / 10 - 1_000_000, afterPayout.balanceMinor)
        assertTrue("doctor.payout_recorded" in rawGet("/v1/admin/audit?action=doctor.payout_recorded&entityId=$profileId&limit=5", admin))

    }

    private suspend fun Api.refundUnanswered(id: String, profileId: String, patient: TestUser, admin: String) {
        // A second window, paid on the development page and never answered: when its
        // time is up it is owed back, and an operator marks it returned.
        val second = post<CheckoutSession>("/v1/doctors/$profileId/consultations/checkout", patient.token, uz.sadora.contract.ConsultationCheckoutRequest(PaymentProvider.PAYME))
        assertEquals(HttpStatusCode.OK, raw { client.post("/v1/billing/dev-pay/${second.transactionId}") }.status)
        assertEquals(true, get<ConversationThread>("/v1/community/conversations/$id", patient.token).conversation.consultation?.open)
        dbQuery {
            exec("UPDATE consultation_sessions SET expires_at = now() - interval '1 minute' WHERE conversation_id = '$id' AND closed_at IS NULL")
            exec("UPDATE community_conversations SET expires_at = now() - interval '1 minute' WHERE id = '$id'")
        }
        assertTrue(component.consultationService.expireDue() >= 1)
        val owed = get<uz.sadora.server.consultation.AdminConsultationPage>("/v1/admin/consultations?payment=refund_due&limit=200", admin)
            .page.items.single { it.doctorId == profileId }
        assertEquals("refund", owed.closedReason)
        assertEquals(PaymentProvider.PAYME, owed.provider)
        postAck("/v1/admin/consultations/${owed.id}/refunded", admin, uz.sadora.contract.Ack())
        assertEquals(HttpStatusCode.Conflict, raw { client.post("/v1/admin/consultations/${owed.id}/refunded") { auth(admin) } }.status)
        assertTrue("consultation.refunded" in rawGet("/v1/admin/audit?action=consultation.refunded&entityId=${owed.id}&limit=5", admin))

    }

    /**
     * A busy doctor's money and work list come a page at a time: the totals are summed
     * over everything while the lines and payouts page by offset; the staff's quality
     * table pages with its total; the questions read on by offset. A call with no
     * parameters still answers as the installed apps read it.
     */
    @Test
    fun `earnings, the quality table and the questions list come a page at a time`() = api {
        val doctor = signUp().also { onboard(it) }
        val patient = signUp().also { onboard(it) }
        val admin = adminToken()
        val profileId = approvedDoctor(doctor, admin, "Dr Pages ${Uuid.random().toString().take(6)}")
        // A free consultation: the conversation the money sessions below belong to.
        val conversationId = post<ConversationThread>(
            "/v1/doctors/$profileId/consultations",
            patient.token,
            uz.sadora.contract.StartConsultationRequest("Salom"),
        ).conversation.id
        // 60 paid windows answered in five minutes, at 15 % rounded down per window; three
        // owed back and two returned, never answered; 55 payouts.
        val paidCount = 60
        dbQuery {
            val columns = "conversation_id, doctor_id, patient_id, opened_at, expires_at, closed_at, closed_reason, " +
                "price_minor, commission_percent, payment_state, first_reply_at, created_at"
            val at = "now() - (i || ' hours')::interval"
            exec(
                "INSERT INTO consultation_sessions ($columns) " +
                    "SELECT '$conversationId', '$profileId', '${patient.userId}', $at, $at + interval '1 day', $at + interval '1 hour', " +
                    "'doctor', 100000 + i, 15, 'paid', $at + interval '5 minutes', $at FROM generate_series(1, $paidCount) AS i",
            )
            exec(
                "INSERT INTO consultation_sessions ($columns) " +
                    "SELECT '$conversationId', '$profileId', '${patient.userId}', $at, $at + interval '1 day', $at + interval '1 day', " +
                    "'refund', 200000, 15, CASE WHEN i <= 103 THEN 'refund_due' ELSE 'refunded' END, NULL, $at " +
                    "FROM generate_series(101, 105) AS i",
            )
            exec(
                "INSERT INTO doctor_payouts (doctor_id, amount_minor, note, paid_at, created_by) " +
                    "SELECT '$profileId', 1000 * i, 'p' || i, now() - (i || ' days')::interval, gen_random_uuid() FROM generate_series(1, 55) AS i",
            )
        }
        val gross = (1..paidCount).sumOf { 100_000L + it }
        val commission = (1..paidCount).sumOf { (100_000L + it) * 15 / 100 }
        val paidOut = (1..55).sumOf { 1000L * it }

        // The call the installed apps make: totals over everything, the first page of each list.
        val shape = Json.parseToJsonElement(rawGet("/v1/doctor/earnings", doctor.token)).jsonObject
        assertEquals(50, shape["lines"]!!.jsonArray.size)
        assertEquals(50, shape["payouts"]!!.jsonArray.size)
        val earnings = get<uz.sadora.contract.DoctorEarnings>("/v1/doctor/earnings", doctor.token)
        assertEquals(gross, earnings.grossMinor)
        assertEquals(commission, earnings.commissionMinor)
        assertEquals(gross - commission, earnings.netMinor)
        assertEquals(paidOut, earnings.paidOutMinor)
        assertEquals(gross - commission - paidOut, earnings.balanceMinor)
        assertEquals(3 * 200_000L, earnings.refundDueMinor)
        assertEquals(paidCount + 5L, earnings.linesTotal, "the free window is no line")
        assertEquals(55L, earnings.payoutsTotal)
        assertTrue(earnings.linesHaveMore && earnings.payoutsHaveMore)
        assertEquals(100_001L, earnings.lines.first().priceMinor, "the latest opened first")

        // The rest by offset; together the pages are every line once, and they add up to the totals.
        val restLines = get<Page<uz.sadora.contract.EarningLine>>("/v1/doctor/earnings/lines?offset=50", doctor.token)
        assertEquals(15, restLines.items.size)
        assertFalse(restLines.hasMore)
        val allLines = earnings.lines + restLines.items
        assertEquals(paidCount + 5, allLines.map { it.sessionId }.toSet().size)
        assertEquals(earnings.netMinor, allLines.sumOf { it.netMinor })
        assertEquals(earnings.commissionMinor, allLines.sumOf { it.commissionMinor })
        val restPayouts = get<Page<uz.sadora.contract.DoctorPayoutView>>("/v1/doctor/earnings/payouts?limit=10&offset=50", doctor.token)
        assertEquals(5, restPayouts.items.size)
        assertEquals(paidOut, (earnings.payouts + restPayouts.items).sumOf { it.amountMinor })

        // Staff read the same, and their own pages.
        val staff = get<uz.sadora.contract.DoctorEarnings>("/v1/admin/doctors/$profileId/earnings", admin)
        assertEquals(earnings.balanceMinor, staff.balanceMinor)
        assertEquals(5, get<Page<uz.sadora.contract.EarningLine>>("/v1/admin/doctors/$profileId/earnings/lines?limit=20&offset=60", admin).items.size)
        assertEquals(55L, get<Page<uz.sadora.contract.DoctorPayoutView>>("/v1/admin/doctors/$profileId/earnings/payouts?limit=1", admin).total)

        // The quality table: pages that meet without overlap, and her numbers summed in SQL.
        val firstTwo = get<Page<uz.sadora.server.consultation.AdminDoctorQuality>>("/v1/admin/doctors/quality?limit=2", admin)
        val one = get<Page<uz.sadora.server.consultation.AdminDoctorQuality>>("/v1/admin/doctors/quality?limit=1", admin)
        val two = get<Page<uz.sadora.server.consultation.AdminDoctorQuality>>("/v1/admin/doctors/quality?limit=1&offset=1", admin)
        assertEquals(firstTwo.items.map { it.doctorId }, (one.items + two.items).map { it.doctorId })
        assertEquals(firstTwo.total, one.total)
        assertTrue(firstTwo.items.first().consultationsMonth >= firstTwo.items.last().consultationsMonth)
        val row = qualityRow(profileId, admin)
        assertEquals(paidCount + 5 + 1, row.consultationsTotal)
        assertEquals(paidCount + 5 + 1, row.consultationsMonth)
        assertEquals(5, row.unansweredTotal)
        assertEquals(5, row.avgFirstReplyMinutes)
        assertEquals(gross, row.grossMinor)
        assertEquals(gross - commission, row.netMinor)
        assertEquals(paidOut, row.paidOutMinor)
        assertEquals(3 * 200_000L, row.refundDueMinor)
        // Her Home and the patients' directory count from the same grouped read.
        val stats = get<uz.sadora.contract.DoctorStats>("/v1/doctor/stats", doctor.token)
        assertEquals(row.consultationsTotal, stats.consultationsTotal)
        assertEquals(row.consultationsMonth, stats.consultationsMonth)
        assertEquals(row.unansweredTotal, stats.unansweredTotal)
        assertEquals(row.avgFirstReplyMinutes, stats.avgFirstReplyMinutes)
        val listed = get<List<uz.sadora.contract.DoctorListItem>>("/v1/doctors", doctor.token).single { it.id == profileId }
        assertEquals(row.consultationsTotal, listed.consultationsTotal)
        assertEquals(row.avgFirstReplyMinutes, listed.avgFirstReplyMinutes)

        // The questions read on by offset, in one stable order; no offset is the old first page.
        repeat(4) { post<CommunityPost>("/v1/community/posts", patient.token, CreatePostRequest(CommunityTopic.CYCLE, "Sahifa savoli $it ${Uuid.random()}")) }
        val firstFour = get<List<CommunityPost>>("/v1/doctor/questions?limit=4", doctor.token)
        val pageOne = get<List<CommunityPost>>("/v1/doctor/questions?limit=2", doctor.token)
        val pageTwo = get<List<CommunityPost>>("/v1/doctor/questions?limit=2&offset=2", doctor.token)
        assertEquals(4, firstFour.size)
        assertEquals(firstFour.map { it.id }, (pageOne + pageTwo).map { it.id })
        assertEquals(firstFour.map { it.id }, get<List<CommunityPost>>("/v1/doctor/questions", doctor.token).take(4).map { it.id })
    }

    /** Her row of the quality table, read page by page as the staff panel would. */
    private suspend fun Api.qualityRow(profileId: String, admin: String): uz.sadora.server.consultation.AdminDoctorQuality {
        var offset = 0
        while (true) {
            val page = get<Page<uz.sadora.server.consultation.AdminDoctorQuality>>("/v1/admin/doctors/quality?limit=200&offset=$offset", admin)
            page.items.firstOrNull { it.doctorId == profileId }?.let { return it }
            if (!page.hasMore) error("doctor $profileId is not in the quality table")
            offset += page.items.size
        }
    }

    @Test
    fun `photos reach who they are for - hers her doctors and a doctors everyone`() = api {
        val doctor = signUp().also { onboard(it) }
        val patient = signUp().also { onboard(it) }
        val stranger = signUp().also { onboard(it) }
        val admin = adminToken()
        val profileId = approvedDoctor(doctor, admin, "Dr Photo ${Uuid.random().toString().take(6)}")

        // Hers: any shape in, a square JPEG out, on her own profile.
        val wide = java.awt.image.BufferedImage(300, 200, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val png = java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(wide, "png", it) }.toByteArray()
        val upload = uz.sadora.contract.PhotoUpload(kotlin.io.encoding.Base64.encode(png), "image/png")
        val mine = put<uz.sadora.contract.PhotoView>("/v1/me/photo", patient.token, upload)
        assertEquals(mine.photoUrl, get<uz.sadora.contract.UserProfile>("/v1/me", patient.token).avatarUrl)
        val served = raw { client.get(assertNotNull(mine.photoUrl)) { auth(patient.token) } }
        assertEquals("image/jpeg", served.headers["Content-Type"])
        val square = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(served.readRawBytes()))
        assertEquals(200, square.width)
        assertEquals(200, square.height)
        assertEquals(HttpStatusCode.BadRequest, raw { client.put("/v1/me/photo") { auth(patient.token); json(uz.sadora.contract.PhotoUpload("bm90IGFuIGltYWdl", "image/png")) } }.status)

        // A doctor's: on her page, her own account, and to any reader.
        val hers = put<uz.sadora.contract.PhotoView>("/v1/doctor/photo", doctor.token, upload)
        assertEquals(hers.photoUrl, get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", stranger.token).photoUrl)
        assertEquals(hers.photoUrl, get<DoctorAccount>("/v1/doctor/me", doctor.token).photoUrl)
        assertEquals(HttpStatusCode.OK, raw { client.get(assertNotNull(hers.photoUrl)) { auth(stranger.token) } }.status)
        assertEquals(HttpStatusCode.Forbidden, raw { client.put("/v1/doctor/photo") { auth(stranger.token); json(upload) } }.status)

        // In a consultation each side sees the other; a stranger, and an alias thread, see nothing.
        val thread = post<ConversationThread>("/v1/doctors/$profileId/consultations", patient.token, uz.sadora.contract.StartConsultationRequest("Salom"))
        val id = thread.conversation.id
        assertEquals(hers.photoUrl, thread.conversation.doctor?.photoUrl)
        val held = get<List<Conversation>>("/v1/community/conversations?scope=patients", doctor.token).single { it.id == id }
        val patientPhoto = assertNotNull(held.patient?.photoUrl)
        assertTrue(patientPhoto.startsWith("/v1/community/conversations/$id/photo?v="))
        assertEquals(HttpStatusCode.OK, raw { client.get(patientPhoto) { auth(doctor.token) } }.status)
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/community/conversations/$id/photo") { auth(stranger.token) } }.status)
        assertFalse(rawGet("/v1/community/me", patient.token).contains("/photo"), "the room never carries her photo")

        // Staff take a doctor's photo down, and it is gone everywhere.
        assertEquals(HttpStatusCode.OK, raw { client.get("/v1/admin/doctors/$profileId/photo") { auth(admin) } }.status)
        assertEquals(HttpStatusCode.OK, raw { client.delete("/v1/admin/doctors/$profileId/photo") { auth(admin); json(uz.sadora.server.photo.RemovePhotoRequest("Yuz ko'rinmaydi")) } }.status)
        assertNull(get<uz.sadora.contract.DoctorProfile>("/v1/doctors/$profileId", patient.token).photoUrl)
        assertTrue("doctor.photo_removed" in rawGet("/v1/admin/audit?action=doctor.photo_removed&entityId=$profileId&limit=5", admin))

        // She removes hers.
        assertEquals(HttpStatusCode.OK, raw { client.delete("/v1/me/photo") { auth(patient.token) } }.status)
        assertNull(get<uz.sadora.contract.UserProfile>("/v1/me", patient.token).avatarUrl)
    }

    @Test
    fun `a doctor prescribes in a consultation and the patient adds it to her medications`() = api {
        val doctor = signUp().also { onboard(it) }
        val patient = signUp().also { onboard(it) }
        val stranger = signUp().also { onboard(it) }
        val admin = adminToken()
        val name = "Dr Rx ${Uuid.random().toString().take(6)}"
        val profileId = approvedDoctor(doctor, admin, name)
        val opened = post<ConversationThread>("/v1/doctors/$profileId/consultations", patient.token, uz.sadora.contract.StartConsultationRequest("Salom"))
        val id = opened.conversation.id
        val nine = kotlinx.datetime.LocalTime(9, 0)
        val twentyOne = kotlinx.datetime.LocalTime(21, 0)
        val rx = uz.sadora.contract.SendPrescriptionRequest(
            items = listOf(
                uz.sadora.contract.PrescriptionItem(
                    name = " Amoksitsillin ",
                    dose = "500",
                    unit = "mg",
                    schedule = uz.sadora.contract.MedicationSchedule(times = listOf(twentyOne, nine)),
                    foodRelation = uz.sadora.contract.FoodRelation.AFTER,
                    days = 5,
                ),
                uz.sadora.contract.PrescriptionItem(
                    name = "Vitamin D",
                    form = uz.sadora.contract.PrescriptionForm.DROPS,
                    dose = "2",
                    unit = "tomchi",
                    schedule = uz.sadora.contract.MedicationSchedule(times = listOf(nine)),
                    foodRelation = uz.sadora.contract.FoodRelation.WITH,
                    startDay = 6,
                ),
            ),
            note = "Ko'p suv iching",
        )
        val path = "/v1/community/conversations/$id/prescriptions"

        // Only the doctor writes one, and only something a pharmacy could read.
        assertEquals(HttpStatusCode.Forbidden, raw { client.post(path) { auth(patient.token); json(rx) } }.status)
        assertEquals(HttpStatusCode.NotFound, raw { client.post(path) { auth(stranger.token); json(rx) } }.status)
        val noDose = rx.copy(items = listOf(rx.items.first().copy(dose = " ")))
        assertEquals(HttpStatusCode.BadRequest, raw { client.post(path) { auth(doctor.token); json(noDose) } }.status)
        val tooMany = rx.copy(items = List(uz.sadora.contract.Limits.PRESCRIPTION_ITEMS_MAX + 1) { rx.items.first() })
        assertEquals(HttpStatusCode.BadRequest, raw { client.post(path) { auth(doctor.token); json(tooMany) } }.status)

        val sent = post<DirectMessage>(path, doctor.token, rx)
        assertEquals(uz.sadora.contract.MessageKind.PRESCRIPTION, sent.kind)
        assertTrue(sent.body.contains("Amoksitsillin"), "the body is the prescription as text")
        val written = assertNotNull(sent.prescription)
        assertEquals("Amoksitsillin", written.items.first().name)
        assertEquals(listOf(nine, twentyOne), written.items.first().schedule.times, "times are stored in order")

        // She reads it in the thread, with the doctor named on it.
        val line = get<ConversationThread>("/v1/community/conversations/$id", patient.token).messages.single { it.id == sent.id }
        val hers = assertNotNull(line.prescription)
        assertEquals(name, hers.doctor.fullName)
        assertNull(hers.patientName, "her copy does not name her")
        assertEquals("Test", get<List<uz.sadora.contract.Prescription>>(path, doctor.token).single().patientName)
        assertEquals(hers.id, get<List<uz.sadora.contract.Prescription>>("/v1/prescriptions", patient.token).single().id)
        assertEquals(HttpStatusCode.NotFound, raw { client.get("/v1/prescriptions/${hers.id}") { auth(stranger.token) } }.status)

        // She adds it: the start date is hers, day 6 counts from it, and the times shift.
        val today = kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.of("Asia/Tashkent"))
        val wrongCount = uz.sadora.contract.AddPrescriptionRequest(today, listOf(uz.sadora.contract.AddPrescriptionItem(0, listOf(nine))))
        assertEquals(HttpStatusCode.BadRequest, raw { client.post("/v1/prescriptions/${hers.id}/add") { auth(patient.token); json(wrongCount) } }.status)
        assertEquals(HttpStatusCode.NotFound, raw { client.post("/v1/prescriptions/${hers.id}/add") { auth(doctor.token); json(wrongCount) } }.status)
        val eight = kotlinx.datetime.LocalTime(8, 0)
        val added = post<uz.sadora.contract.AddPrescriptionResult>(
            "/v1/prescriptions/${hers.id}/add",
            patient.token,
            uz.sadora.contract.AddPrescriptionRequest(
                today,
                listOf(
                    uz.sadora.contract.AddPrescriptionItem(0, listOf(eight, twentyOne)),
                    uz.sadora.contract.AddPrescriptionItem(1, listOf(nine)),
                ),
            ),
        )
        assertNotNull(added.prescription.addedAt)
        val course = added.medications.first { it.name == "Amoksitsillin" }
        assertEquals(listOf(eight, twentyOne), course.schedule.times)
        assertEquals(today, course.startedOn)
        assertEquals(today.plus(4, kotlinx.datetime.DateTimeUnit.DAY), course.endedOn, "five days, the first one included")
        assertEquals(hers.id, course.prescriptionId)
        assertEquals(name, course.prescribedBy)
        val drops = added.medications.first { it.name == "Vitamin D" }
        assertEquals(today.plus(5, kotlinx.datetime.DateTimeUnit.DAY), drops.startedOn)
        assertNull(drops.endedOn)
        assertEquals(HttpStatusCode.Conflict, raw { client.post("/v1/prescriptions/${hers.id}/add") { auth(patient.token); json(wrongCount) } }.status)
        assertNotNull(get<List<uz.sadora.contract.Prescription>>(path, doctor.token).single().addedAt, "the doctor sees that she added it")

        // Her edit keeps what the doctor decided and takes her times.
        val edited = put<uz.sadora.contract.Medication>(
            "/v1/meds/${course.id}",
            patient.token,
            uz.sadora.contract.SaveMedicationRequest(
                name = "Boshqa nom",
                dosage = "1000",
                schedule = uz.sadora.contract.MedicationSchedule(times = listOf(kotlinx.datetime.LocalTime(7, 0), twentyOne)),
                remindersEnabled = false,
            ),
        )
        assertEquals("Amoksitsillin", edited.name)
        assertEquals("500", edited.dosage)
        assertEquals(uz.sadora.contract.FoodRelation.AFTER, edited.foodRelation)
        assertEquals(course.endedOn, edited.endedOn)
        assertEquals(kotlinx.datetime.LocalTime(7, 0), edited.schedule.times.first())
        assertFalse(edited.remindersEnabled)

        // The window closes; the doctor can still cancel, with a reason, once.
        post<ConversationThread>("/v1/community/conversations/$id/close", doctor.token, uz.sadora.contract.Ack())
        assertEquals(HttpStatusCode.Forbidden, raw { client.post(path) { auth(doctor.token); json(rx) } }.status)
        val cancelPath = "/v1/prescriptions/${hers.id}/cancel"
        assertEquals(HttpStatusCode.Forbidden, raw { client.post(cancelPath) { auth(patient.token); json(uz.sadora.contract.CancelPrescriptionRequest("x")) } }.status)
        assertEquals(HttpStatusCode.BadRequest, raw { client.post(cancelPath) { auth(doctor.token); json(uz.sadora.contract.CancelPrescriptionRequest("  ")) } }.status)
        val cancelled = post<uz.sadora.contract.Prescription>(cancelPath, doctor.token, uz.sadora.contract.CancelPrescriptionRequest("Doza xato yozildi"))
        assertEquals("Doza xato yozildi", cancelled.cancelReason)
        assertEquals(HttpStatusCode.Conflict, raw { client.post(cancelPath) { auth(doctor.token); json(uz.sadora.contract.CancelPrescriptionRequest("yana")) } }.status)
        val courses = get<List<uz.sadora.contract.Medication>>("/v1/meds?includeArchived=true", patient.token).filter { it.prescriptionId == hers.id }
        assertEquals(listOf("Amoksitsillin"), courses.map { it.name }, "the course from day 6 had not begun: it is gone, not archived")
        assertTrue(courses.none { it.active }, "a cancelled prescription's courses stop")
        assertEquals(today, courses.single().endedOn)
        assertTrue(get<uz.sadora.contract.MedicationDay>("/v1/meds/days/${today.plus(5, kotlinx.datetime.DateTimeUnit.DAY)}", patient.token).doses.isEmpty())
        assertEquals(1, outboxCount(patient, "rx-cancel:"))
        assertNotNull(get<ConversationThread>("/v1/community/conversations/$id", patient.token).messages.single { it.id == sent.id }.prescription?.cancelledAt)
    }

    private suspend fun Api.approvedDoctor(doctor: TestUser, admin: String, name: String): String {
        val jpeg = kotlin.io.encoding.Base64.encode(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
        post<DoctorAccount>(
            "/v1/doctor/application",
            doctor.token,
            DoctorApplicationRequest(name, DoctorSpecialty.GYNECOLOGIST, "Klinika", 9, "LIC-F", documents = listOf(DoctorDocumentUpload(DoctorDocumentKind.DIPLOMA, jpeg))),
        )
        val profileId = get<Page<uz.sadora.server.doctor.AdminDoctorRow>>("/v1/admin/doctors?status=pending&limit=200", admin)
            .items.first { it.fullName == name }.id
        postAck("/v1/admin/doctors/$profileId/review", admin, uz.sadora.server.doctor.DoctorReviewRequest("approve"))
        return profileId
    }

    /** Rows a table holds for one account, by its user_id column. */
    private suspend fun outboxCount(user: TestUser, keyPrefix: String): Int = dbQuery {
        exec("SELECT count(*) FROM notification_outbox WHERE user_id = '${user.userId}' AND dedupe_key LIKE '$keyPrefix%'") { rows ->
            rows.next()
            rows.getInt(1)
        } ?: 0
    }

    private suspend fun queuedCount(user: TestUser, keyPrefix: String): Int = dbQuery {
        exec("SELECT count(*) FROM notification_outbox WHERE user_id = '${user.userId}' AND status = 'queued' AND dedupe_key LIKE '$keyPrefix%'") { rows ->
            rows.next()
            rows.getInt(1)
        } ?: 0
    }

    private suspend fun countCoins(user: TestUser, reason: String): Int = dbQuery {
        exec("SELECT count(*) FROM coin_ledger WHERE user_id = '${user.userId}' AND reason = '$reason'") { rows ->
            rows.next()
            rows.getInt(1)
        } ?: 0
    }

    private suspend fun countRowsFor(userId: Uuid, table: String): Int = dbQuery {
        exec("SELECT count(*) FROM $table WHERE user_id = '$userId'") { rows ->
            rows.next()
            rows.getInt(1)
        } ?: 0
    }

    private suspend fun Api.signUp(): TestUser {
        val phone = randomPhone()
        val challenge = client.post("/v1/auth/otp/request") { json(OtpRequest(phone)) }.body<OtpChallenge>()
        val code = assertNotNull(challenge.devCode, "the test config exposes the code")
        val session = client.post("/v1/auth/otp/verify") {
            json(OtpVerifyRequest(challenge.challengeId, code, DeviceInfo("test-device", Platform.ANDROID, timezone = "Asia/Tashkent")))
        }.body<AuthSession>()
        assertTrue(session.isNewUser)
        return TestUser(session.tokens.accessToken, session.user.id, session.tokens.refreshToken)
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

    private suspend fun Api.adminToken(): String = adminAccount().token

    private class TestAdmin(val token: String, val email: String, val password: String)

    private suspend fun Api.adminAccount(role: String = "owner"): TestAdmin {
        val email = "test-${Uuid.random()}@sadora.test"
        val password = "Test12345"
        dbQuery {
            AdminUsers.insert {
                it[id] = Uuid.random()
                it[AdminUsers.email] = email
                it[passwordHash] = PasswordHasher.hash(password)
                it[name] = "Integration"
                it[AdminUsers.role] = role.lowercase()
                it[totpEnabled] = false
                it[status] = "active"
                it[failedAttempts] = 0
                it[createdAt] = now().toOffsetDateTime()
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
        val token = client.post("/v1/admin/auth/login") { json(AdminSignInRequest(email, password)) }
            .body<AdminSession>().accessToken
        return TestAdmin(token, email, password)
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

    private suspend inline fun <reified T> Api.put(path: String, token: String, body: Any? = null): T {
        val response = client.put(path) {
            auth(token)
            body?.let { json(it) }
        }
        assertEquals(HttpStatusCode.OK, response.status, "PUT $path: ${response.bodyAsTextSafe()}")
        return response.body()
    }

    /**
     * A number an operator would actually issue.
     *
     * The previous `+9989` + eight random digits produced `+99892…` and `+99896…` about
     * a fifth of the time, and no Uzbek operator uses 92 or 96 — so the tests were
     * signing up with numbers the app itself now refuses to type.
     */
    private fun randomPhone(): String {
        val code = UzbekPhone.OPERATOR_CODES.random()
        return "+998" + code + (1..7).joinToString("") { Random.nextInt(10).toString() }
    }

    private suspend inline fun <reified T> Api.patch(path: String, token: String, body: Any): T {
        val response = client.patch(path) { auth(token); json(body) }
        assertEquals(HttpStatusCode.OK, response.status, "PATCH $path: ${response.bodyAsTextSafe()}")
        return response.body()
    }

    private suspend fun Api.postAck(path: String, token: String, body: Any) {
        val response = client.post(path) { auth(token); json(body) }
        assertEquals(HttpStatusCode.OK, response.status, "POST $path: ${response.bodyAsTextSafe()}")
    }


    /** Posts to Payme's endpoint in their envelope, with or without their Basic auth. */
    private suspend fun Api.payme(body: String, auth: Boolean = true): JsonObject {
        val response = client.post("/v1/payments/payme") {
            if (auth) {
                val encoded = java.util.Base64.getEncoder()
                    .encodeToString("Paycom:test_payme_key".toByteArray())
                header(HttpHeaders.Authorization, "Basic $encoded")
            }
            setBody(TextContent(body, ContentType.Application.Json))
        }
        assertEquals(HttpStatusCode.OK, response.status, "Payme reads the envelope, not the status")
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun JsonObject.errorCode(): Int? =
        this["error"]?.jsonObject?.get("code")?.jsonPrimitive?.int

    private suspend fun Api.click(step: String, form: Map<String, String>): JsonObject {
        val response = client.submitForm(
            url = "/v1/payments/click/$step",
            formParameters = parameters { form.forEach { (key, value) -> append(key, value) } },
        )
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun clickSignature(source: String): String =
        java.security.MessageDigest.getInstance("MD5")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
        // No API key, so the test never reaches a network: the gateway answers from the
        // rules, which is also what a deployment without a key does.
        ai = AiConfig(
            apiKey = null,
            model = "test-model",
            endpoint = "http://localhost",
            timeout = 5.seconds,
            maxOutputTokens = 800,
            inputCostPerMillionMicros = 100_000,
            outputCostPerMillionMicros = 400_000,
        ),
        // Unconfigured, so notifications are logged rather than sent: the suite must not
        // reach Firebase, and the scheduler's own behaviour is what it checks.
        push = PushConfig(projectId = null, serviceAccountPath = null),
        // Provider credentials the tests sign with; a real deployment reads them from
        // the environment and refuses checkout when they are absent.
        billing = BillingConfig(
            payme = PaymeConfig(
                merchantId = "test_merchant",
                key = "test_payme_key",
                login = "Paycom",
                accountField = "order_id",
                checkoutUrl = "https://checkout.paycom.uz",
            ),
            click = ClickConfig(
                serviceId = "12345",
                merchantId = "54321",
                secretKey = "test_click_secret",
                checkoutUrl = "https://my.click.uz/services/pay",
            ),
        ),
        policyVersion = "2026-08-01",
        minimumAppVersion = null,
        referralLinkBase = "https://sadora.app/r",
        publicBaseUrl = "http://localhost:8080",
        // WHOOP pointed at nothing: the connect endpoint must say "not configured" and
        // never reach a network from the suite.
        wearables = WearableConfig(
            tokenKey = null,
            whoop = WhoopConfig(clientId = null, clientSecret = null, redirectUri = "http://localhost:8080/v1/wearables/whoop/callback", apiBaseUrl = "http://localhost"),
        ),
        // Zero, so the erasure test can tick the job instead of waiting thirty days.
        accountErasureGracePeriod = Duration.ZERO,
    )
}
