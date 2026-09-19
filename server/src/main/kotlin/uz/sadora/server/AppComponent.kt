package uz.sadora.server

import uz.sadora.server.admin.AdminAuthService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import uz.sadora.server.ai.AiGateway
import uz.sadora.server.billing.BillingRepository
import uz.sadora.server.billing.BillingService
import uz.sadora.server.billing.ClickGateway
import uz.sadora.server.billing.PaymeGateway
import uz.sadora.server.billing.StorePurchaseService
import uz.sadora.server.billing.UnconfiguredStoreVerifier
import uz.sadora.server.ai.AiService
import uz.sadora.server.ai.AiUsageRepository
import uz.sadora.server.ai.GeminiAnswerer
import uz.sadora.server.ai.GeminiFoodVision
import uz.sadora.server.ai.GreetingService
import uz.sadora.server.rewards.HomeLayoutRepository
import uz.sadora.server.rewards.RewardsRepository
import uz.sadora.server.rewards.RewardsService
import uz.sadora.server.rewards.ShopRepository
import uz.sadora.server.rewards.ShopService
import uz.sadora.server.community.CommunityModerationService
import uz.sadora.server.community.CommunityRepository
import uz.sadora.server.community.CommunityService
import uz.sadora.server.community.MessagingRepository
import uz.sadora.server.community.MessagingService
import uz.sadora.server.admin.AdminService
import uz.sadora.server.admin.AdminStatsRepository
import uz.sadora.server.audit.AuditRepository
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.AuthService
import uz.sadora.server.auth.JwtService
import uz.sadora.server.auth.LoggingOtpSender
import uz.sadora.server.auth.OtpService
import uz.sadora.server.auth.RefreshTokenService
import uz.sadora.server.auth.SocialVerifier
import uz.sadora.server.cache.Cache
import uz.sadora.server.cache.Caches
import uz.sadora.server.config.AppConfig
import uz.sadora.server.config.Environment
import uz.sadora.server.db.DatabaseFactory
import uz.sadora.server.entitlement.EntitlementRepository
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.entitlement.SubscriptionRepository
import uz.sadora.server.flags.FeatureFlagRepository
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.health.HealthRepository
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.MedicationRepository
import uz.sadora.server.health.MedicationService
import uz.sadora.server.health.AppointmentRepository
import uz.sadora.server.health.AppointmentService
import uz.sadora.server.health.MindRepository
import uz.sadora.server.health.MindService
import uz.sadora.server.health.NutritionRepository
import uz.sadora.server.health.NutritionService
import uz.sadora.server.content.ContentRepository
import uz.sadora.server.content.ContentService
import uz.sadora.server.insights.InsightsService
import java.io.File
import uz.sadora.server.notify.FcmPushSender
import uz.sadora.server.notify.GoogleAccessTokens
import uz.sadora.server.notify.LoggingPushSender
import uz.sadora.server.notify.PushSender
import uz.sadora.server.notify.ServiceAccountKey
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.notify.NotificationScheduler
import uz.sadora.server.notify.NotificationService
import uz.sadora.server.user.AccountErasureJob
import uz.sadora.server.user.UserRepository
import uz.sadora.server.wearable.WearableRepository
import uz.sadora.server.wearable.WearableService
import uz.sadora.server.wearable.ConnectionRepository
import uz.sadora.server.wearable.WearableConnectService
import uz.sadora.server.wearable.WearableSyncJob
import uz.sadora.server.wearable.whoop.WhoopClient
import uz.sadora.server.core.TokenCipher
import uz.sadora.server.user.UserService
import uz.sadora.server.share.ShareRepository
import uz.sadora.server.share.ShareService

/**
 * Wiring, by hand.
 *
 * A DI container would earn its keep at a few hundred beans; at this size a constructor
 * graph you can read top to bottom is easier to follow and fails at compile time rather
 * than at startup.
 */
class AppComponent(val config: AppConfig) : AutoCloseable {

    /**
     * The one client for calls that leave the server. JSON is negotiated here so a
     * provider client is a request and a data class, not another client to configure.
     */
    val outboundHttpClient: HttpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val databaseFactory: DatabaseFactory = DatabaseFactory.connect(config.database)
    val cache: Cache = Caches.create(config.redis)

    val auditService = AuditService()
    val auditRepository = AuditRepository()
    val statsRepository = AdminStatsRepository()

    val userRepository = UserRepository()
    val entitlementRepository = EntitlementRepository()
    val subscriptionRepository = SubscriptionRepository()
    val flagRepository = FeatureFlagRepository()
    val healthRepository = HealthRepository()
    val mindRepository = MindRepository()
    val appointmentRepository = AppointmentRepository()
    val nutritionRepository = NutritionRepository()
    val medicationRepository = MedicationRepository()
    val wearableRepository = WearableRepository()

    val entitlementService = EntitlementService(entitlementRepository)
    val flagService = FeatureFlagService(flagRepository)

    val jwtService = JwtService(config.jwt)
    val refreshTokenService = RefreshTokenService(config.jwt, auditService)
    val otpService = OtpService(config.otp, cache, LoggingOtpSender(showCode = config.environment == Environment.DEV))
    val socialVerifier = SocialVerifier(config.social)

    val authService = AuthService(
        users = userRepository,
        otp = otpService,
        social = socialVerifier,
        refreshTokens = refreshTokenService,
        jwt = jwtService,
        entitlements = entitlementService,
        audit = auditService,
    )

    // ---- rewards -------------------------------------------------------------
    // Built before the AI services: the greeting reads the streak, so the reward layer
    // has to exist first. Nothing flows the other way — the scheme never reads health.
    val rewardsRepository = RewardsRepository()
    val homeLayoutRepository = HomeLayoutRepository()
    val shopRepository = ShopRepository()

    val rewardsService = RewardsService(
        repository = rewardsRepository,
        users = userRepository,
        referralLinkBase = config.referralLinkBase,
    )

    val shopService = ShopService(
        shop = shopRepository,
        rewards = rewardsRepository,
        subscriptions = subscriptionRepository,
        entitlements = entitlementService,
    )

    val userService = UserService(
        users = userRepository,
        entitlements = entitlementService,
        flags = flagService,
        refreshTokens = refreshTokenService,
        audit = auditService,
        config = config,
        rewards = rewardsService,
    )

    val aiUsageRepository = AiUsageRepository()

    val healthAccess = HealthAccess(userRepository, entitlementService)
    val healthService = HealthService(healthRepository, healthAccess)
    val mindService = MindService(mindRepository, healthRepository, healthAccess, rewardsService)
    val appointmentService = AppointmentService(appointmentRepository, healthAccess)
    val nutritionService = NutritionService(
        nutrition = nutritionRepository,
        access = healthAccess,
        // Same key and same model as the chat, and the same rule: no key, no model
        // object, so the scanner reports itself unavailable instead of failing per call.
        vision = config.ai.apiKey?.let { GeminiFoodVision(outboundHttpClient, config.ai) },
        visionConfig = config.ai,
        usage = aiUsageRepository,
        rewards = rewardsService,
    )
    val medicationService = MedicationService(medicationRepository, healthAccess, rewardsService)
    val wearableService = WearableService(wearableRepository, healthAccess)

    /**
     * Cloud wearables. WHOOP exists as a client only when its credentials do, the same
     * rule as the payment providers: an unconfigured provider is listed as unavailable,
     * never as a button that starts a flow with nowhere to return to.
     */
    val connectionRepository = ConnectionRepository()
    val wearableConnectService = WearableConnectService(
        connections = connectionRepository,
        wearables = wearableService,
        access = healthAccess,
        audit = auditService,
        cipher = TokenCipher.from(config.wearables.tokenKey, fallbackSecret = config.jwt.secret),
        whoopConfig = config.wearables.whoop,
        whoop = if (config.wearables.whoop.isConfigured) WhoopClient(outboundHttpClient, config.wearables.whoop) else null,
    )
    val wearableSyncJob = WearableSyncJob(wearableConnectService)

    val notificationRepository = NotificationRepository()
    val notificationService = NotificationService(notificationRepository)
    /**
     * FCM when it is configured, the log when it is not.
     *
     * Not a refusal like the store verifier: a notification that is only logged costs
     * nobody anything, while a checkout that is only logged gives the product away. A
     * laptop and a demo want the log; production wants the line below to say fcm, and
     * the boot log says which one it got.
     */
    val pushSender: PushSender = if (config.push.isConfigured) {
        FcmPushSender(
            client = outboundHttpClient,
            projectId = config.push.projectId!!,
            tokens = GoogleAccessTokens(
                client = outboundHttpClient,
                credentials = ServiceAccountKey.parse(File(config.push.serviceAccountPath!!).readText()),
                scope = FcmPushSender.SCOPE,
            ),
            onTokenRejected = { token -> userRepository.forgetPushToken(token) },
        )
    } else {
        LoggingPushSender()
    }

    val notificationScheduler = NotificationScheduler(
        notifications = notificationRepository,
        medications = medicationRepository,
        users = userRepository,
        sender = pushSender,
    )

    val accountErasureJob = AccountErasureJob(
        users = userRepository,
        audit = auditService,
        gracePeriod = config.accountErasureGracePeriod,
    )

    val communityRepository = CommunityRepository()
    val messagingRepository = MessagingRepository()
    val communityService = CommunityService(
        repository = communityRepository,
        users = userRepository,
        flags = flagService,
        environment = config.environment,
        messaging = messagingRepository,
    )
    val messagingService = MessagingService(
        messages = messagingRepository,
        community = communityService,
        identities = communityRepository,
        notifications = notificationRepository,
    )
    val communityModerationService = CommunityModerationService(communityRepository, auditService, messagingRepository)

    val contentRepository = ContentRepository()
    val contentService = ContentService(
        repository = contentRepository,
        users = userRepository,
        entitlements = entitlementService,
        rewards = rewardsService,
    )

    val insightsService = InsightsService(
        access = healthAccess,
        health = healthRepository,
        nutrition = nutritionRepository,
        wearables = wearableRepository,
        entitlements = entitlementService,
    )

    val aiGateway = AiGateway(
        config = config.ai,
        usage = aiUsageRepository,
        // No key means no model object at all, so the gateway cannot try and fail on
        // every question — it answers from the rules and says so in the log once.
        model = config.ai.apiKey?.let { GeminiAnswerer(outboundHttpClient, config.ai) },
    )

    /**
     * The home screen's greeting.
     *
     * Its own service rather than a method on [AiService]: it is not a question, it is
     * not metered against her chat allowance, and it must answer for a free account.
     * The only thing the two share is the model object and the cost log.
     */
    val greetingService = GreetingService(
        users = userRepository,
        flags = flagService,
        environment = config.environment,
        cache = cache,
        config = config.ai,
        health = healthService,
        nutrition = nutritionService,
        mind = mindService,
        wearables = wearableService,
        rewards = rewardsRepository,
        usage = aiUsageRepository,
        model = config.ai.apiKey?.let { GeminiAnswerer(outboundHttpClient, config.ai) },
    )

    val aiService = AiService(
        users = userRepository,
        entitlements = entitlementService,
        flags = flagService,
        environment = config.environment,
        health = healthService,
        nutrition = nutritionService,
        wearables = wearableService,
        gateway = aiGateway,
        usage = aiUsageRepository,
    )

    /**
     * The QR code she shows a doctor. Reads through the health services rather than the
     * repositories, so the page is subject to exactly the same access rules as the app.
     */
    val shareRepository = ShareRepository()
    val shareService = ShareService(
        shares = shareRepository,
        users = userRepository,
        health = healthService,
        mind = mindRepository,
        medications = medicationService,
        appointments = appointmentRepository,
        nutrition = nutritionRepository,
        wearables = wearableService,
        audit = auditService,
        publicBaseUrl = config.publicBaseUrl,
    )

    val billingRepository = BillingRepository()
    val billingService = BillingService(
        repository = billingRepository,
        subscriptions = subscriptionRepository,
        entitlements = entitlementService,
        users = userRepository,
        flags = flagService,
        config = config.billing,
        environment = config.environment,
    )
    val paymeGateway = PaymeGateway(billingRepository, billingService, config.billing.payme)
    val clickGateway = ClickGateway(billingRepository, billingService, config.billing.click)
    val storePurchaseService = StorePurchaseService(
        repository = billingRepository,
        subscriptions = subscriptionRepository,
        entitlements = entitlementService,
        // No App Store key and no Play service account yet, so receipts are refused
        // rather than believed.
        verifier = UnconfiguredStoreVerifier,
    )

    val adminAuthService = AdminAuthService(jwtService, auditService)

    val adminService = AdminService(
        users = userRepository,
        entitlementRepository = entitlementRepository,
        entitlementService = entitlementService,
        subscriptions = subscriptionRepository,
        flagRepository = flagRepository,
        flagService = flagService,
        audit = auditService,
    )

    override fun close() {
        notificationScheduler.stop()
        wearableSyncJob.stop()
        accountErasureJob.stop()
        outboundHttpClient.close()
        cache.close()
        databaseFactory.close()
    }
}
