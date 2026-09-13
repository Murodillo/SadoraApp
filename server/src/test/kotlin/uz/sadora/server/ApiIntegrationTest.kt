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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
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
import uz.sadora.contract.NutritionGoals
import uz.sadora.contract.RedeemRequest
import uz.sadora.contract.RedeemResult
import uz.sadora.contract.Redemption
import uz.sadora.contract.ReferralStatus
import uz.sadora.contract.RewardsSummary
import uz.sadora.contract.SaveHomeLayoutRequest
import uz.sadora.contract.ShopCatalog
import uz.sadora.contract.ShopKind
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.contract.StageBaseline
import uz.sadora.contract.AuthSession
import uz.sadora.contract.BillingCatalogue
import uz.sadora.contract.CheckoutRequest
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityIdentity
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
import uz.sadora.contract.UzbekPhone
import uz.sadora.server.admin.AdminSession
import uz.sadora.server.admin.AdminMe
import uz.sadora.server.admin.AdminSignInRequest
import uz.sadora.server.admin.Totp
import uz.sadora.server.admin.TotpConfirmRequest
import uz.sadora.server.admin.TotpDisableRequest
import uz.sadora.server.admin.TotpEnrolment
import uz.sadora.server.admin.AdminStats
import uz.sadora.server.auth.PasswordHasher
import uz.sadora.server.billing.BillingService
import uz.sadora.server.community.HideRequest
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
        val wrong = client.post("/v1/admin/me/totp/confirm") {
            auth(admin.token)
            json(TotpConfirmRequest("000000"))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
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
        assertEquals(HttpStatusCode.Unauthorized, sessionOnly.status)
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
    private class Api(val client: HttpClient)

    private fun api(block: suspend Api.() -> Unit) = testApplication {
        application { apiModule(component) }
        val client = createClient {
            install(ContentNegotiation) { json(wireJson) }
        }
        Api(client).block()
    }

    /** Rows a table holds for one account, by its user_id column. */
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

    private suspend fun Api.adminAccount(): TestAdmin {
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
        referralLinkBase = "https://sadora.uz/r",
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
