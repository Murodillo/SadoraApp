package uz.sadora.server.notify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.server.core.now

/**
 * An OAuth access token for a Google service account, from its private key.
 *
 * Google's own client library would bring in a large dependency tree for one HTTP call
 * and one signature, and the flow is short enough to read: sign a JWT that says who you
 * are and what you want, hand it to the token endpoint, get a bearer back. The token
 * lives an hour, so it is cached until shortly before it expires — a fetch per push
 * would be an extra round trip on every notification.
 */
class GoogleAccessTokens(
    private val client: HttpClient,
    private val credentials: ServiceAccountKey,
    private val scope: String,
    private val tokenEndpoint: String = "https://oauth2.googleapis.com/token",
) {
    private val mutex = Mutex()
    private var cached: String? = null
    private var expiresAt: Instant = Instant.DISTANT_PAST

    suspend fun token(): String = mutex.withLock {
        val current = cached
        // A minute of margin: a token that expires in transit is a 401 for something
        // that was valid when it was picked up.
        if (current != null && now() < expiresAt - 60.seconds) return current

        val assertion = signedAssertion()
        val response = client.submitForm(
            url = tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", assertion)
            },
        )
        if (!response.status.isSuccess()) {
            throw PushConfigurationException(
                "Google token endpoint said ${response.status.value}: ${response.bodyAsText().take(200)}",
            )
        }
        val body = response.body<TokenResponse>()
        cached = body.accessToken
        expiresAt = now() + body.expiresIn.seconds
        body.accessToken
    }

    private fun signedAssertion(): String {
        val issued = now().epochSeconds
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = """
            {"iss":"${credentials.clientEmail}","scope":"$scope","aud":"$tokenEndpoint",
            "iat":$issued,"exp":${issued + 3600}}
        """.trimIndent().replace("\n", "")
        val signingInput = "${header.base64Url()}.${claims.base64Url()}"
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(credentials.privateKey())
            update(signingInput.toByteArray())
        }.sign()
        return "$signingInput.${signature.base64Url()}"
    }

    @Serializable
    private data class TokenResponse(
        @kotlinx.serialization.SerialName("access_token") val accessToken: String,
        @kotlinx.serialization.SerialName("expires_in") val expiresIn: Long,
    )
}

/** Raised when the credentials are present but unusable. Never when they are absent. */
class PushConfigurationException(message: String) : Exception(message)

/**
 * The two fields of a service-account JSON this needs.
 *
 * Read from the file Google Cloud hands you, so the operator copies one file rather
 * than transcribing a PEM into an environment variable and losing the newlines.
 */
@Serializable
data class ServiceAccountKey(
    @kotlinx.serialization.SerialName("client_email") val clientEmail: String,
    @kotlinx.serialization.SerialName("private_key") val privateKeyPem: String,
    @kotlinx.serialization.SerialName("project_id") val projectId: String? = null,
) {
    fun privateKey(): java.security.PrivateKey {
        val body = privateKeyPem
            .replace("\\n", "\n")
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .filterNot { it.isWhitespace() }
        val decoded = runCatching { Base64.getDecoder().decode(body) }
            .getOrElse { throw PushConfigurationException("private_key is not valid PEM") }
        return runCatching {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(decoded))
        }.getOrElse { throw PushConfigurationException("private_key is not a PKCS#8 RSA key") }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses the service-account JSON, or explains what is wrong with it. */
        fun parse(raw: String): ServiceAccountKey =
            runCatching { json.decodeFromString<ServiceAccountKey>(raw) }
                .getOrElse { throw PushConfigurationException("service account JSON is not readable: ${it.message}") }
    }
}

private fun String.base64Url(): String = toByteArray().base64Url()

private fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
