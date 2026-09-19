package uz.sadora.server.billing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.Date
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.server.core.now

/**
 * App Store subscriptions, checked from the transaction Apple signed.
 *
 * StoreKit 2 hands the app a `signedTransactionInfo`: a JWS whose header carries the
 * certificate chain that signed it. The chain must end in Apple Root CA - G3, which is
 * pinned here from the file Apple publishes (SHA-256 checked below), not taken from the
 * JWS itself — the root in the header is only compared against it. Each link is checked
 * with its parent's key, the leaf and intermediate must carry Apple's App Store receipt
 * marker extensions, and the payload must be signed by the leaf.
 *
 * No App Store Connect key is needed for this. What it cannot tell is a later refund or
 * a renewal; those come from App Store Server Notifications, which are the next step.
 */
class AppStoreVerifier(
    private val bundleIds: Set<String>,
    private val root: X509Certificate = pinnedRoot(),
    private val clock: () -> Instant = { now() },
) {

    fun verify(productId: String, signedTransaction: String): VerifiedPurchase {
        val payload = verifiedPayload(signedTransaction)
        if (payload.bundleId !in bundleIds) throw ReceiptRejectedException("The transaction is for another app")
        if (payload.productId != productId) throw ReceiptRejectedException("The transaction is for a different product")
        if (payload.revocationDate != null) throw ReceiptRejectedException("The transaction was refunded or revoked")
        val expiresAt = payload.expiresDate?.let(Instant::fromEpochMilliseconds)
        if (expiresAt != null && expiresAt <= clock()) throw ReceiptRejectedException("The subscription has expired")
        return VerifiedPurchase(
            productId = payload.productId,
            transactionId = payload.transactionId,
            expiresAt = expiresAt,
            autoRenewing = payload.type == "Auto-Renewable Subscription",
            // A UUID: Swift writes it upper-case, Kotlin lower-case. Same id either way.
            accountId = payload.appAccountToken?.lowercase(),
        )
    }

    internal fun verifiedPayload(jws: String): TransactionPayload {
        val decoded = runCatching { JWT.decode(jws) }.getOrElse { throw ReceiptRejectedException("Not a signed transaction") }
        if (decoded.algorithm != "ES256") throw ReceiptRejectedException("Unexpected signature algorithm")
        val chain = decoded.getHeaderClaim("x5c").asList(String::class.java)
            ?.map(::certificate)
            ?: throw ReceiptRejectedException("No certificate chain")
        if (chain.size != 3) throw ReceiptRejectedException("Unexpected certificate chain")
        val (leaf, intermediate, headerRoot) = chain

        if (!headerRoot.encoded.contentEquals(root.encoded)) throw ReceiptRejectedException("Not signed by Apple")
        val signedAt = decoded.getClaim("signedDate").asLong()?.let(::Date) ?: Date(clock().toEpochMilliseconds())
        runCatching {
            intermediate.verify(root.publicKey)
            leaf.verify(intermediate.publicKey)
            listOf(leaf, intermediate, root).forEach { it.checkValidity(signedAt) }
        }.getOrElse { throw ReceiptRejectedException("The certificate chain does not verify") }
        if (leaf.getExtensionValue(LEAF_MARKER) == null || intermediate.getExtensionValue(INTERMEDIATE_MARKER) == null) {
            throw ReceiptRejectedException("The certificates are not App Store receipt certificates")
        }

        runCatching {
            JWT.require(Algorithm.ECDSA256(leaf.publicKey as ECPublicKey, null)).build().verify(jws)
        }.getOrElse { throw ReceiptRejectedException("The signature does not verify") }

        val payloadJson = String(Base64.getUrlDecoder().decode(decoded.payload))
        return json.decodeFromString<TransactionPayload>(payloadJson)
    }

    @Serializable
    internal data class TransactionPayload(
        val transactionId: String,
        val originalTransactionId: String? = null,
        val bundleId: String,
        val productId: String,
        val type: String? = null,
        val expiresDate: Long? = null,
        val revocationDate: Long? = null,
        val appAccountToken: String? = null,
        val environment: String? = null,
    )

    companion object {
        /** Apple Root CA - G3, as published at apple.com/certificateauthority. */
        private const val ROOT_SHA256 = "63343ABFB89A6A03EBB57E9B3F5FA7BE7C4F5C756F3017B3A8C488C3653E9179"
        private const val LEAF_MARKER = "1.2.840.113635.100.6.11.1"
        private const val INTERMEDIATE_MARKER = "1.2.840.113635.100.6.2.1"
        private val json = Json { ignoreUnknownKeys = true }

        fun pinnedRoot(): X509Certificate {
            val bytes = AppStoreVerifier::class.java.getResourceAsStream("/certs/AppleRootCA-G3.cer")?.readBytes()
                ?: error("certs/AppleRootCA-G3.cer is missing from the server resources")
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
            check(digest == ROOT_SHA256) { "The bundled Apple root certificate is not the one Apple publishes" }
            return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        }

        private fun certificate(base64: String): X509Certificate =
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(Base64.getDecoder().decode(base64))) as X509Certificate
    }
}
