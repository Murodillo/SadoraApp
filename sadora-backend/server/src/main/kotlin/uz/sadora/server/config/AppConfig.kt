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
    /**
     * Whether an admin must enrol an authenticator before the panel opens. Until she
     * does, her token reaches only her own account page, where enrolment lives. Off on a
     * laptop only; staging and production are both on the internet.
     */
    val adminRequireTotp: Boolean = false,
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
                    eskiz = EskizConfig(
                        email = envOrNull("ESKIZ_EMAIL"),
                        password = envOrNull("ESKIZ_PASSWORD"),
                        from = env("ESKIZ_FROM", "4546"),
                        baseUrl = env("ESKIZ_BASE_URL", "https://notify.eskiz.uz"),
                    ),
                    smsText = env("OTP_SMS_TEXT", DEFAULT_SMS_TEXT),
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
                    googlePlay = GooglePlayConfig(
                        packageName = env("GOOGLE_PLAY_PACKAGE_NAME", "uz.sadora.app"),
                        serviceAccountFile = envOrNull("GOOGLE_PLAY_SERVICE_ACCOUNT_FILE"),
                    ),
                    // Each switch is named rather than read off SADORA_ENV: production runs
                    // as STAGE until the release, and "not PROD" there meant a public page
                    // that marked any pending payment paid.
                    devPay = env("BILLING_DEV_PAY", (environment == Environment.DEV).toString()).toBoolean(),
                    allowTestPurchases = env(
                        "STORE_ALLOW_TEST_PURCHASES",
                        (environment == Environment.DEV).toString(),
                    ).toBoolean(),
                    // App Review buys in the sandbox against the production server, so
                    // refusing sandbox receipts there gets the build rejected. Only the
                    // people invited to TestFlight can make one.
                    appStoreAllowSandbox = env("APPSTORE_ALLOW_SANDBOX", "true").toBoolean(),
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
                    oura = OuraConfig(
                        clientId = envOrNull("OURA_CLIENT_ID"),
                        clientSecret = envOrNull("OURA_CLIENT_SECRET"),
                        redirectUri = env("OURA_REDIRECT_URI", "$publicBaseUrl/v1/wearables/oura/callback"),
                        authorizeUrl = env("OURA_AUTHORIZE_URL", "https://cloud.ouraring.com/oauth/authorize"),
                        apiBaseUrl = env("OURA_API_BASE_URL", "https://api.ouraring.com"),
                    ),
                ),
                accountErasureGracePeriod = env("ACCOUNT_ERASURE_GRACE_DAYS", "30").toInt().days,
                adminRequireTotp = env("ADMIN_REQUIRE_TOTP", (environment != Environment.DEV).toString()).toBoolean(),
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
            if (environment == Environment.DEV) return
            // Staging is reachable from the internet too. A token signed with the key that
            // sits in the public repository is a token anybody can mint, so the signing key
            // is checked on every environment but a laptop; the OTP conveniences below stay
            // allowed on stage, where testers read the code from the response.
            require(jwt.secret != DEV_JWT_SECRET) {
                "JWT_SECRET must be set outside development — the development default is public."
            }
            require(jwt.secret.length >= 32) { "JWT_SECRET must be at least 32 characters." }
            if (!environment.isProduction) return
            require(!otp.exposeCode) { "OTP_EXPOSE_CODE must be false in production." }
            require(adminRequireTotp) { "ADMIN_REQUIRE_TOTP must be true in production." }
            require(!billing.devPay) { "BILLING_DEV_PAY must be false in production." }
            require(!billing.allowTestPurchases) { "STORE_ALLOW_TEST_PURCHASES must be false in production." }
            require(otp.fixedCode == null) { "OTP_FIXED_CODE must not be set in production." }
            // Without a sender every code goes nowhere and nobody can sign in — better a
            // server that refuses to start than one that looks up and locks everyone out.
            require(otp.eskiz.isConfigured) { "ESKIZ_EMAIL and ESKIZ_PASSWORD must be set in production." }
            require("{code}" in otp.smsText) { "OTP_SMS_TEXT must contain {code}." }
            require(publicBaseUrl.startsWith("https://")) { "PUBLIC_BASE_URL must be an https URL in production." }
            if (wearables.whoop.isConfigured || wearables.oura.isConfigured) {
                require(wearables.tokenKey != null) { "WEARABLE_TOKEN_KEY must be set when a cloud wearable is configured." }
            }
        }

        /**
         * Eskiz sends only texts its moderators approved, character for character, so
         * this is the wording to submit with the sender application — or OTP_SMS_TEXT
         * is set to whatever they approved instead.
         */
        private const val DEFAULT_SMS_TEXT = "Sadora: kirish kodingiz {code}. Uni hech kimga aytmang."

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
    val eskiz: EskizConfig = EskizConfig(null, null, from = "4546", baseUrl = "https://notify.eskiz.uz"),
    /** The SMS, with `{code}` where the digits go. */
    val smsText: String = "{code}",
)

/**
 * The SMS gateway's account: notify.eskiz.uz, the one Uzbek operators route through
 * without a contract per operator. The cabinet's login is the API's login.
 */
data class EskizConfig(
    val email: String?,
    val password: String?,
    /** The sender name: "4546" is Eskiz's shared one until a brand name is registered. */
    val from: String,
    val baseUrl: String,
) {
    val isConfigured: Boolean get() = email != null && password != null

    override fun toString(): String =
        "EskizConfig(email=$email, password=${if (password == null) "null" else "***"}, from=$from, baseUrl=$baseUrl)"
}

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

data class BillingConfig(
    val payme: PaymeConfig,
    val click: ClickConfig,
    val googlePlay: GooglePlayConfig = GooglePlayConfig(packageName = "uz.sadora.app", serviceAccountFile = null),
    /**
     * The page at /v1/billing/dev-pay that pays a pending payment at once, with no login.
     * On only where nobody can buy anything real: a laptop and staging.
     */
    val devPay: Boolean = false,
    /** Whether a Play licence tester's free purchase grants anything. */
    val allowTestPurchases: Boolean = false,
    /** Whether an App Store sandbox (TestFlight, App Review) receipt grants anything. */
    val appStoreAllowSandbox: Boolean = true,
)

/** Play Developer API access: the app's package and a service account Play Console trusts. */
data class GooglePlayConfig(
    val packageName: String,
    /** Path to the service-account JSON; blank means Play receipts are refused. */
    val serviceAccountFile: String?,
) {
    val isConfigured: Boolean get() = serviceAccountFile != null
}

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
    val oura: OuraConfig = OuraConfig(null, null, "", "", ""),
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

/** Oura's API application, on the same terms as [WhoopConfig]. */
data class OuraConfig(
    val clientId: String?,
    val clientSecret: String?,
    /** Must match a redirect URI registered on the Oura application, character for character. */
    val redirectUri: String,
    val authorizeUrl: String,
    val apiBaseUrl: String,
) {
    val isConfigured: Boolean get() = clientId != null && clientSecret != null
}
