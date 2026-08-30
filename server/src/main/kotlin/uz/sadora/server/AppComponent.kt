package uz.sadora.server

import uz.sadora.server.admin.AdminAuthService
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
import uz.sadora.server.user.UserRepository
import uz.sadora.server.user.UserService

/**
 * Wiring, by hand.
 *
 * A DI container would earn its keep at a few hundred beans; at this size a constructor
 * graph you can read top to bottom is easier to follow and fails at compile time rather
 * than at startup.
 */
class AppComponent(val config: AppConfig) : AutoCloseable {

    val databaseFactory: DatabaseFactory = DatabaseFactory.connect(config.database)
    val cache: Cache = Caches.create(config.redis)

    val auditService = AuditService()
    val auditRepository = AuditRepository()
    val statsRepository = AdminStatsRepository()

    val userRepository = UserRepository()
    val entitlementRepository = EntitlementRepository()
    val subscriptionRepository = SubscriptionRepository()
    val flagRepository = FeatureFlagRepository()

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
        cache.close()
        databaseFactory.close()
    }
}
