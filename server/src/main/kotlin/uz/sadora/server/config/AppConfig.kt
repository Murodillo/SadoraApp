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
    val ai: AiConfig,
    val push: PushConfig,
    val billing: BillingConfig,
    val policyVersion: String,
    val minimumAppVersion: String?,
    /**
     * Where an invite link points.
     *
     * Built here rather than in the app so the domain lives in one place: the code is
     * appended to it, the landing page behind it sends the visitor to the right store,
     * and a change of domain is a deploy rather than a release.
     */
    val referralLinkBase: String,
    /**
     * Where this server is reachable from outside — the base of every link it hands out:
     * the doctor page behind a QR code, and the OAuth redirect a wearable provider sends
     * a browser back to. The API's own address, not the landing page's, because both of
     * those are served by this process.
     */
    val publicBaseUrl: String,
    val wearables: WearableConfig,
    /**
     * How long a deletion request waits before the account is erased for real.
     *
     * A window for a person who changes her mind, or asks support to — not a soft delete
     * kept "just in case". Thirty days is the ordinary support window; shorten it and a
     * misplaced tap is unrecoverable, lengthen it and the promise stops being true.
     */
    val accountErasureGracePeriod: Duration,
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            val environment = enumValueOf<Environment>(env("SADORA_ENV", "DEV").uppercase())
            val publicBaseUrl = env("PUBLIC_BASE_URL", "http://localhost:8080").trimEnd('/')
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
                    fixedCode = envOrNull("OTP_FIXED_CODE"),
                ),
                social = SocialConfig(
                    appleBundleIds = env("APPLE_BUNDLE_IDS", "uz.sadora.app")
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    googleClientIds = envOrNull("GOOGLE_CLIENT_IDS")
                        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                ),
                ai = AiConfig(
                    apiKey = envOrNull("GEMINI_API_KEY"),
                    model = env("AI_MODEL", "gemini-3.6-flash"),
                    endpoint = env("AI_ENDPOINT", "https://generativelanguage.googleapis.com"),
                    timeout = env("AI_TIMEOUT_SECONDS", "20").toInt().seconds,
                    maxOutputTokens = env("AI_MAX_OUTPUT_TOKENS", "800").toInt(),
                    inputCostPerMillionMicros = env("AI_INPUT_COST_MICROS", "100000").toLong(),
                    outputCostPerMillionMicros = env("AI_OUTPUT_COST_MICROS", "400000").toLong(),
                ),
                push = PushConfig(
                    projectId = envOrNull("FCM_PROJECT_ID"),
                    serviceAccountPath = envOrNull("FCM_SERVICE_ACCOUNT_FILE"),
                ),
                billing = BillingConfig(
                    payme = PaymeConfig(
                        merchantId = envOrNull("PAYME_MERCHANT_ID"),
                        key = envOrNull("PAYME_KEY"),
                        login = env("PAYME_LOGIN", "Paycom"),
                        accountField = env("PAYME_ACCOUNT_FIELD", "order_id"),
                        checkoutUrl = env("PAYME_CHECKOUT_URL", "https://checkout.paycom.uz"),
                    ),
                    click = ClickConfig(
                        serviceId = envOrNull("CLICK_SERVICE_ID"),
                        merchantId = envOrNull("CLICK_MERCHANT_ID"),
                        secretKey = envOrNull("CLICK_SECRET_KEY"),
                        checkoutUrl = env("CLICK_CHECKOUT_URL", "https://my.click.uz/services/pay"),
                    ),
                ),
                // The day the copy in the app's LegalScreen took effect. The consent row records
                // this string, so a screen dated later than the version stored against it
                // would make the record say she agreed to something she never saw.
                policyVersion = env("POLICY_VERSION", "2026-09-03"),
                minimumAppVersion = envOrNull("MINIMUM_APP_VERSION"),
                referralLinkBase = env("REFERRAL_LINK_BASE", "https://sadora.app/r").trimEnd('/'),
                publicBaseUrl = publicBaseUrl,
                wearables = WearableConfig(
                    tokenKey = envOrNull("WEARABLE_TOKEN_KEY"),
                    whoop = WhoopConfig(
                        clientId = envOrNull("WHOOP_CLIENT_ID"),
                        clientSecret = envOrNull("WHOOP_CLIENT_SECRET"),
                        redirectUri = env("WHOOP_REDIRECT_URI", "$publicBaseUrl/v1/wearables/whoop/callback"),
                        apiBaseUrl = env("WHOOP_API_BASE_URL", "https://api.prod.whoop.com"),
                    ),
                ),
                accountErasureGracePeriod = env("ACCOUNT_ERASURE_GRACE_DAYS", "30").toInt().days,
            )
            config.verifyProductionSafety()
            return config
        }

        /**
         * Guards the settings that are convenient in dev and dangerous in production:
         * a shipped-by-default signing key, OTP codes returned in the response body, and
         * a fixed OTP code.
         */
        private fun AppConfig.verifyProductionSafety() {
            if (!environment.isProduction) return
            require(jwt.secret != DEV_JWT_SECRET) {
                "JWT_SECRET must be set in production — the development default is public."
            }
            require(jwt.secret.length >= 32) { "JWT_SECRET must be at least 32 characters." }
            require(!otp.exposeCode) { "OTP_EXPOSE_CODE must be false in production." }
            require(otp.fixedCode == null) { "OTP_FIXED_CODE must not be set in production." }
            require(publicBaseUrl.startsWith("https://")) { "PUBLIC_BASE_URL must be an https URL in production." }
            if (wearables.whoop.isConfigured) {
                require(wearables.tokenKey != null) { "WEARABLE_TOKEN_KEY must be set when a cloud wearable is configured." }
            }
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
    /**
     * Issues this code instead of a random one, so a tester on a real phone does not
     * have to read the response body or the server log. Refused in production by
     * [AppConfig]; [codeLength] is ignored while it is set.
     */
    val fixedCode: String? = null,
)

data class SocialConfig(
    val appleBundleIds: List<String>,
    val googleClientIds: List<String>,
)

/**
 * The AI gateway's settings.
 *
 * Costs are in USD micros per million tokens, so a price change is an environment
 * variable rather than a deploy, and the arithmetic stays in integers — money in a
 * double is a bug waiting for a big enough number.
 */
data class AiConfig(
    val apiKey: String?,
    val model: String,
    val endpoint: String,
    val timeout: kotlin.time.Duration,
    val maxOutputTokens: Int,
    val inputCostPerMillionMicros: Long,
    val outputCostPerMillionMicros: Long,
) {
    /** Null token counts cost nothing rather than guessing — an unknown is not an estimate. */
    fun costMicros(promptTokens: Int?, completionTokens: Int?): Long =
        (promptTokens ?: 0).toLong() * inputCostPerMillionMicros / 1_000_000 +
            (completionTokens ?: 0).toLong() * outputCostPerMillionMicros / 1_000_000
}

/**
 * The payment providers' credentials.
 *
 * Every one is nullable, and an unconfigured provider is not offered at checkout rather
 * than failing at it: the catalogue asks [PaymeConfig.isConfigured] before listing a
 * button that would produce a link nobody can pay.
 */
/**
 * Firebase Cloud Messaging. Both fields or neither: with anything missing the server
 * logs notifications instead of delivering them, which is what a laptop should do and
 * what production must be noticed not doing.
 */
data class PushConfig(
    val projectId: String?,
    val serviceAccountPath: String?,
) {
    val isConfigured: Boolean get() = projectId != null && serviceAccountPath != null
}

data class BillingConfig(val payme: PaymeConfig, val click: ClickConfig)

data class PaymeConfig(
    val merchantId: String?,
    val key: String?,
    val login: String,
    /** The field name Payme sends the order id in; agreed with them per merchant. */
    val accountField: String,
    val checkoutUrl: String,
) {
    val isConfigured: Boolean get() = merchantId != null && key != null
}

data class ClickConfig(
    val serviceId: String?,
    val merchantId: String?,
    val secretKey: String?,
    val checkoutUrl: String,
) {
    val isConfigured: Boolean get() = serviceId != null && merchantId != null && secretKey != null
}

/**
 * The cloud wearables the server pulls from, and the key their tokens rest under.
 *
 * [tokenKey] is 32 random bytes, base64: the refresh tokens a provider issues are
 * long-lived credentials to someone's health data and are encrypted at rest with it.
 * Without a key the server derives one from the JWT secret, which is fine on a laptop
 * and refused in production by [AppConfig].
 */
data class WearableConfig(
    val tokenKey: String?,
    val whoop: WhoopConfig,
)

/**
 * WHOOP's developer app. Both credentials or nothing: with either missing the provider
 * is listed as "not configured" and the connect button is not drawn, rather than starting
 * an OAuth flow that has nowhere to come back to.
 */
data class WhoopConfig(
    val clientId: String?,
    val clientSecret: String?,
    /** Must match a redirect URI registered in the WHOOP dashboard, character for character. */
    val redirectUri: String,
    val apiBaseUrl: String,
) {
    val isConfigured: Boolean get() = clientId != null && clientSecret != null
}
