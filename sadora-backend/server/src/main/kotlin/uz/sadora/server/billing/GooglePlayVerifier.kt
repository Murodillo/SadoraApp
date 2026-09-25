package uz.sadora.server.billing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.contract.PaymentProvider
import uz.sadora.server.core.now

/**
 * Google Play subscriptions, checked with the Play Developer API.
 *
 * The app sends the `purchaseToken` Play gave it; this asks Play what that token is —
 * `purchases.subscriptionsv2.get` — as the service account that Play Console granted
 * "View financial data". Nothing the phone says about the purchase is believed: the
 * product, the expiry, the order id and the account it was bought for all come from
 * Play's answer.
 *
 * Only an active subscription (or one in its grace period, which Play still counts as
 * paid) is verified. Anything else — pending, on hold, expired, revoked — is a refusal.
 */
class GooglePlayVerifier(
    private val http: HttpClient,
    private val packageName: String,
    private val serviceAccount: ServiceAccount,
    private val apiBase: String = "https://androidpublisher.googleapis.com",
    /**
     * Whether a licence tester's purchase — one Play never charged for — counts. True
     * on a laptop and on staging, where those are the only purchases there are; false
     * in production, where a tester's free receipt must not become a free subscription.
     */
    private val allowTestPurchases: Boolean = true,
) {
    private val tokenLock = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiresAt: Instant = Instant.DISTANT_PAST

    suspend fun verify(productId: String, purchaseToken: String): VerifiedPurchase {
        val url = "$apiBase/androidpublisher/v3/applications/${packageName.encodeURLPathPart()}" +
            "/purchases/subscriptionsv2/tokens/${purchaseToken.encodeURLPathPart()}"
        val response: HttpResponse = http.get(url) {
            header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // 400/404 is Play saying the token is not one of ours; the body says why, and
            // is not echoed back to the client.
            throw ReceiptRejectedException("Google Play rejected the purchase token (${response.status.value})")
        }
        return interpret(json.decodeFromString<SubscriptionPurchaseV2>(body), productId)
    }

    /** Play's answer, turned into what we grant — or a refusal. */
    internal fun interpret(purchase: SubscriptionPurchaseV2, productId: String): VerifiedPurchase {
        if (purchase.subscriptionState !in PAID_STATES) {
            throw ReceiptRejectedException("Subscription is ${purchase.subscriptionState}")
        }
        // Play marks a licence tester's purchase with a `testPurchase` object; no money
        // moved, so outside development nothing is granted for it.
        if (purchase.testPurchase != null && !allowTestPurchases) {
            throw ReceiptRejectedException("Test purchases are not accepted here")
        }
        val line = purchase.lineItems.firstOrNull { it.productId == productId }
            ?: throw ReceiptRejectedException("The token is for a different product")
        val orderId = purchase.latestOrderId
            ?: throw ReceiptRejectedException("Play returned no order id")
        return VerifiedPurchase(
            productId = line.productId,
            // A renewal is a new order on the same token, so the order id — not the token —
            // is what makes each paid period its own transaction.
            transactionId = orderId,
            expiresAt = line.expiryTime?.let(Instant::parse),
            autoRenewing = line.autoRenewingPlan?.autoRenewEnabled ?: false,
            accountId = purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId,
        )
    }

    /** A service-account access token, reused until a few minutes before it expires. */
    private suspend fun accessToken(): String = tokenLock.withLock {
        accessToken?.takeIf { now() < accessTokenExpiresAt - 5.minutes }?.let { return@withLock it }
        val issuedAt = now()
        val assertion = JWT.create()
            .withIssuer(serviceAccount.clientEmail)
            .withAudience(serviceAccount.tokenUri)
            .withClaim("scope", SCOPE)
            .withIssuedAt(Date(issuedAt.toEpochMilliseconds()))
            .withExpiresAt(Date((issuedAt + 60.minutes).toEpochMilliseconds()))
            .sign(Algorithm.RSA256(null, serviceAccount.privateKey))
        val response = http.submitForm(
            url = serviceAccount.tokenUri,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", assertion)
            },
        )
        if (!response.status.isSuccess()) {
            throw ReceiptRejectedException("Google OAuth refused the service account (${response.status.value})")
        }
        val token = json.decodeFromString<OAuthToken>(response.bodyAsText())
        accessToken = token.accessToken
        accessTokenExpiresAt = issuedAt + token.expiresIn.seconds
        token.accessToken
    }

    /** The parts of a Google service-account key file this needs. */
    class ServiceAccount(
        val clientEmail: String,
        val privateKey: RSAPrivateKey,
        val tokenUri: String,
    ) {
        companion object {
            fun fromFile(path: String): ServiceAccount {
                val key = json.decodeFromString<ServiceAccountFile>(File(path).readText())
                val der = Base64.getDecoder().decode(
                    key.privateKey
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\s".toRegex(), ""),
                )
                val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
                return ServiceAccount(key.clientEmail, privateKey, key.tokenUri)
            }
        }
    }

    companion object {
        private const val SCOPE = "https://www.googleapis.com/auth/androidpublisher"
        private val PAID_STATES = setOf("SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD")
        private val json = Json { ignoreUnknownKeys = true }
    }
}

// ---------------------------------------------------------------- Play's JSON

@Serializable
internal data class ServiceAccountFile(
    @kotlinx.serialization.SerialName("client_email") val clientEmail: String,
    @kotlinx.serialization.SerialName("private_key") val privateKey: String,
    @kotlinx.serialization.SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
)

@Serializable
internal data class OAuthToken(
    @kotlinx.serialization.SerialName("access_token") val accessToken: String,
    @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long = 3600,
)

@Serializable
internal data class SubscriptionPurchaseV2(
    val subscriptionState: String = "",
    val latestOrderId: String? = null,
    val lineItems: List<LineItem> = emptyList(),
    val externalAccountIdentifiers: ExternalAccountIdentifiers? = null,
    /** Present, as an empty object, only on a licence tester's purchase. */
    val testPurchase: TestPurchase? = null,
) {
    @Serializable
    data class LineItem(
        val productId: String,
        val expiryTime: String? = null,
        val autoRenewingPlan: AutoRenewingPlan? = null,
    )

    @Serializable
    data class AutoRenewingPlan(val autoRenewEnabled: Boolean = false)

    @Serializable
    data class ExternalAccountIdentifiers(val obfuscatedExternalAccountId: String? = null)

    /** Play sends `"testPurchase": {}` — no fields, only presence. */
    @Serializable
    class TestPurchase
}

/** Routes each store to its verifier; a store with no credentials refuses, as before. */
class StoreVerifiers(
    private val googlePlay: GooglePlayVerifier?,
    private val appStore: AppStoreVerifier?,
) : StoreVerifier {
    override suspend fun verify(provider: PaymentProvider, productId: String, token: String): VerifiedPurchase =
        when (provider) {
            PaymentProvider.GOOGLE_PLAY -> googlePlay?.verify(productId, token)
            PaymentProvider.APP_STORE -> appStore?.verify(productId, token)
            else -> null
        } ?: UnconfiguredStoreVerifier.verify(provider, productId, token)
}
