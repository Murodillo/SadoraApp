package uz.sadora.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import uz.sadora.server.config.AiConfig
import uz.sadora.server.config.AppConfig
import uz.sadora.server.config.BillingConfig
import uz.sadora.server.config.ClickConfig
import uz.sadora.server.config.DatabaseConfig
import uz.sadora.server.config.Environment
import uz.sadora.server.config.HttpConfig
import uz.sadora.server.config.JwtConfig
import uz.sadora.server.config.OtpConfig
import uz.sadora.server.config.PaymeConfig
import uz.sadora.server.config.PushConfig
import uz.sadora.server.config.RedisConfig
import uz.sadora.server.config.SocialConfig
import uz.sadora.server.config.WearableConfig
import uz.sadora.server.config.WhoopConfig

/** The configuration every integration suite boots with: a development server that reaches no network. */
internal fun integrationTestConfig(databaseUrl: String) = AppConfig(
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
        demoPhones = setOf(DEMO_PHONE),
        demoCode = "271828",
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
        // The suite pays consultations, pets and frames through the instant page.
        devPay = true,
        allowTestPurchases = true,
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
    mediaDir = java.nio.file.Files.createTempDirectory("sadora-test-media").toString(),
)

/** A demo account's number, as OTP_DEMO_PHONES would name it. */
internal const val DEMO_PHONE = "+998900000099"
