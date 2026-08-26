package uz.sadora.server.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration is read straight from the environment rather than through Ktor's config
 * substitution: a JDBC URL contains colons, and `$VAR:default` splits on the first one.
 */
enum class Environment { DEV, STAGE, PROD;

    val isProduction: Boolean get() = this == PROD
}

data class AppConfig(
    val environment: Environment,
    val http: HttpConfig,
    val database: DatabaseConfig,
    val redis: RedisConfig,
    val jwt: JwtConfig,
    val otp: OtpConfig,
    val social: SocialConfig,
    val policyVersion: String,
    val minimumAppVersion: String?,
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            val environment = enumValueOf<Environment>(env("SADORA_ENV", "DEV").uppercase())
            val config = AppConfig(
                environment = environment,
                http = HttpConfig(
                    port = env("PORT", "8080").toInt(),
                    host = env("HOST", "0.0.0.0"),
                    allowedOrigins = env("CORS_ALLOWED_ORIGINS", "http://localhost:5173")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                ),
                database = DatabaseConfig(
                    jdbcUrl = env("DB_URL", "jdbc:postgresql://localhost:5432/sadora"),
                    user = env("DB_USER", "sadora"),
                    password = env("DB_PASSWORD", "sadora"),
                    maxPoolSize = env("DB_POOL_SIZE", "10").toInt(),
                    runMigrations = env("DB_MIGRATE", "true").toBoolean(),
                ),
                redis = RedisConfig(
                    url = envOrNull("REDIS_URL"),
                ),
                jwt = JwtConfig(
                    secret = env("JWT_SECRET", DEV_JWT_SECRET),
                    issuer = env("JWT_ISSUER", "sadora"),
                    audience = env("JWT_AUDIENCE", "sadora-app"),
                    accessTokenTtl = env("JWT_ACCESS_TTL_MINUTES", "15").toInt().minutes,
                    refreshTokenTtl = env("JWT_REFRESH_TTL_DAYS", "30").toInt().days,
                ),
                otp = OtpConfig(
                    codeLength = env("OTP_LENGTH", "6").toInt(),
                    ttl = env("OTP_TTL_SECONDS", "300").toInt().seconds,
                    maxAttempts = env("OTP_MAX_ATTEMPTS", "5").toInt(),
                    resendAfter = env("OTP_RESEND_SECONDS", "60").toInt().seconds,
                    maxPerPhonePerHour = env("OTP_MAX_PER_HOUR", "5").toInt(),
                    exposeCode = env("OTP_EXPOSE_CODE", "true").toBoolean(),
                ),
                social = SocialConfig(
                    appleBundleIds = env("APPLE_BUNDLE_IDS", "uz.sadora.app")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    googleClientIds = envOrNull("GOOGLE_CLIENT_IDS")
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                ),
                policyVersion = env("POLICY_VERSION", "2026-08-01"),
                minimumAppVersion = envOrNull("MINIMUM_APP_VERSION"),
            )
            config.verifyProductionSafety()
            return config
        }

        /**
         * Guards the settings that are convenient in dev and dangerous in production:
         * a shipped-by-default signing key, and OTP codes returned in the response body.
         */
        private fun AppConfig.verifyProductionSafety() {
            if (!environment.isProduction) return
            require(jwt.secret != DEV_JWT_SECRET) {
                "JWT_SECRET must be set in production — the development default is public."
            }
            require(jwt.secret.length >= 32) { "JWT_SECRET must be at least 32 characters." }
            require(!otp.exposeCode) { "OTP_EXPOSE_CODE must be false in production." }
        }

        private const val DEV_JWT_SECRET = "dev-only-secret-change-me-0123456789abcdef"

        private fun env(key: String, default: String): String =
            System.getenv(key)?.takeIf { it.isNotBlank() } ?: default

        private fun envOrNull(key: String): String? =
            System.getenv(key)?.takeIf { it.isNotBlank() }
    }
}

data class HttpConfig(val port: Int, val host: String, val allowedOrigins: List<String>)

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
    val runMigrations: Boolean,
)

/** Redis is optional: without a URL the server falls back to an in-process cache. */
data class RedisConfig(val url: String?)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)

data class OtpConfig(
    val codeLength: Int,
    val ttl: Duration,
    val maxAttempts: Int,
    val resendAfter: Duration,
    val maxPerPhonePerHour: Int,
    /** Returns the code in the API response. Refused in production by [AppConfig]. */
    val exposeCode: Boolean,
)

data class SocialConfig(
    val appleBundleIds: List<String>,
    val googleClientIds: List<String>,
)
