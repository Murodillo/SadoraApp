package uz.sadora.server

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import uz.sadora.server.billing.AppStoreVerifier
import uz.sadora.server.billing.GooglePlayVerifier
import uz.sadora.server.billing.ReceiptRejectedException
import uz.sadora.server.billing.SubscriptionPurchaseV2

/**
 * The two store verifiers, without a network. Play's answer is interpreted from JSON
 * shaped like the API's; Apple's chain check is exercised with Apple's own root posing
 * as every link, which must still be refused because it is not a receipt certificate.
 */
class StoreVerifierTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun play(body: String) = json.decodeFromString<SubscriptionPurchaseV2>(body)

    private val playVerifier = GooglePlayVerifier(
        http = io.ktor.client.HttpClient(),
        packageName = "uz.sadora.app",
        serviceAccount = GooglePlayVerifier.ServiceAccount(
            clientEmail = "test@example.iam.gserviceaccount.com",
            privateKey = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
                .generateKeyPair().private as java.security.interfaces.RSAPrivateKey,
            tokenUri = "http://127.0.0.1:1/token",
        ),
    )

    @Test
    fun `an active Play subscription is verified with its order id and account`() {
        val purchase = play(
            """{"subscriptionState":"SUBSCRIPTION_STATE_ACTIVE","latestOrderId":"GPA.1234-5678-9012-34567..0",
               "lineItems":[{"productId":"premium_month","expiryTime":"2026-10-20T10:00:00Z","autoRenewingPlan":{"autoRenewEnabled":true}}],
               "externalAccountIdentifiers":{"obfuscatedExternalAccountId":"user-1"}}""",
        )
        val verified = playVerifier.interpret(purchase, "premium_month")
        assertEquals("GPA.1234-5678-9012-34567..0", verified.transactionId)
        assertEquals("user-1", verified.accountId)
        assertTrue(verified.autoRenewing)
    }

    @Test
    fun `a Play subscription that is not paid for is refused`() {
        listOf("SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING", "SUBSCRIPTION_STATE_ON_HOLD").forEach { state ->
            assertFailsWith<ReceiptRejectedException>(state) {
                playVerifier.interpret(
                    play("""{"subscriptionState":"$state","latestOrderId":"GPA.1","lineItems":[{"productId":"premium_month"}]}"""),
                    "premium_month",
                )
            }
        }
    }

    /** Play marks a licence tester's purchase; nobody paid for it, so production refuses it. */
    @Test
    fun `a licence tester's purchase is accepted in development and refused in production`() {
        val body = """{"subscriptionState":"SUBSCRIPTION_STATE_ACTIVE","latestOrderId":"GPA.9",
               "lineItems":[{"productId":"premium_month"}],"testPurchase":{}}"""
        assertEquals("GPA.9", playVerifier.interpret(play(body), "premium_month").transactionId)

        val production = GooglePlayVerifier(
            http = io.ktor.client.HttpClient(),
            packageName = "uz.sadora.app",
            serviceAccount = GooglePlayVerifier.ServiceAccount(
                clientEmail = "test@example.iam.gserviceaccount.com",
                privateKey = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
                    .generateKeyPair().private as java.security.interfaces.RSAPrivateKey,
                tokenUri = "http://127.0.0.1:1/token",
            ),
            allowTestPurchases = false,
        )
        assertFailsWith<ReceiptRejectedException> { production.interpret(play(body), "premium_month") }
    }

    @Test
    fun `a Play token for another product is refused`() {
        assertFailsWith<ReceiptRejectedException> {
            playVerifier.interpret(
                play("""{"subscriptionState":"SUBSCRIPTION_STATE_ACTIVE","latestOrderId":"GPA.1","lineItems":[{"productId":"premium_month"}]}"""),
                "premium_year",
            )
        }
    }

    @Test
    fun `the bundled Apple root is the one Apple publishes`() {
        assertTrue(AppStoreVerifier.pinnedRoot().subjectX500Principal.name.contains("Apple Root CA - G3"))
    }

    @Test
    fun `anything that is not an Apple-signed transaction is refused`() {
        val verifier = AppStoreVerifier(bundleIds = setOf("uz.sadora.app"))
        assertFailsWith<ReceiptRejectedException> { verifier.verify("uz.sadora.premium.month", "not-a-jws") }

        // Apple's own root as leaf, intermediate and root: every link "verifies", but none
        // is a receipt certificate, and a forged payload must go nowhere.
        val root = Base64.getEncoder().encodeToString(AppStoreVerifier.pinnedRoot().encoded)
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val header = b64.encodeToString("""{"alg":"ES256","x5c":["$root","$root","$root"]}""".toByteArray())
        val payload = b64.encodeToString(
            """{"transactionId":"1","bundleId":"uz.sadora.app","productId":"uz.sadora.premium.month","appAccountToken":"u"}""".toByteArray(),
        )
        assertFailsWith<ReceiptRejectedException> {
            verifier.verify("uz.sadora.premium.month", "$header.$payload.${b64.encodeToString(ByteArray(64))}")
        }
    }
}
