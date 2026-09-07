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
import uz.sadora.server.community.CommunityModerationService
import uz.sadora.server.community.CommunityRepository
import uz.sadora.server.community.CommunityService
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
import uz.sadora.server.notify.LoggingPushSender
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.notify.NotificationScheduler
import uz.sadora.server.notify.NotificationService
import uz.sadora.server.user.UserRepository
import uz.sadora.server.wearable.WearableRepository
import uz.sadora.server.wearable.WearableService
import uz.sadora.server.user.UserService

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
    val otpService = OtpService(config.otp, cache, LoggingOtpSender())
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

    val userService = UserService(
        users = userRepository,
        entitlements = entitlementService,
        flags = flagService,
        refreshTokens = refreshTokenService,
        audit = auditService,
        config = config,
    )

    val healthAccess = HealthAccess(userRepository, entitlementService)
    val healthService = HealthService(healthRepository, healthAccess)
    val mindService = MindService(mindRepository, healthRepository, healthAccess)
    val appointmentService = AppointmentService(appointmentRepository, healthAccess)
    val nutritionService = NutritionService(nutritionRepository, healthAccess)
    val medicationService = MedicationService(medicationRepository, healthAccess)
    val wearableService = WearableService(wearableRepository, healthAccess)

    val notificationRepository = NotificationRepository()
    val notificationService = NotificationService(notificationRepository)
    val notificationScheduler = NotificationScheduler(
        notifications = notificationRepository,
        medications = medicationRepository,
        users = userRepository,
        sender = LoggingPushSender(),
    )

    val communityRepository = CommunityRepository()
    val communityService = CommunityService(
        repository = communityRepository,
        users = userRepository,
        flags = flagService,
        environment = config.environment,
    )
    val communityModerationService = CommunityModerationService(communityRepository, auditService)

    val contentRepository = ContentRepository()
    val contentService = ContentService(
        repository = contentRepository,
        users = userRepository,
        entitlements = entitlementService,
    )

    val insightsService = InsightsService(
        access = healthAccess,
        health = healthRepository,
        nutrition = nutritionRepository,
        wearables = wearableRepository,
        entitlements = entitlementService,
    )

    val aiUsageRepository = AiUsageRepository()
    val aiGateway = AiGateway(
        config = config.ai,
        usage = aiUsageRepository,
        // No key means no model object at all, so the gateway cannot try and fail on
        // every question — it answers from the rules and says so in the log once.
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
        outboundHttpClient.close()
        cache.close()
        databaseFactory.close()
    }
}
