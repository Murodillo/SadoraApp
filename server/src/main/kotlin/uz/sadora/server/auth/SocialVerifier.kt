package uz.sadora.server.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.ErrorCodes
import uz.sadora.server.config.SocialConfig
import uz.sadora.server.core.ApiException
import uz.sadora.server.core.UnauthorizedException

/** What we trust about a user after the provider's token checks out. */
data class SocialIdentity(
    val provider: AuthProvider,
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
)

/**
 * Verifies Apple and Google identity tokens against the provider's published keys.
 *
 * The signature is checked locally rather than by calling a `tokeninfo` endpoint — no
 * outbound request on the sign-in path beyond the cached key fetch, and no dependence on
 * a third party being up while a user is trying to sign in.
 */
class SocialVerifier(private val config: SocialConfig) {

    private val logger = LoggerFactory.getLogger(SocialVerifier::class.java)

    private val appleKeys: JwkProvider = provider("https://appleid.apple.com/auth/keys")
    private val googleKeys: JwkProvider = provider("https://www.googleapis.com/oauth2/v3/certs")

    private fun provider(url: String): JwkProvider =
        JwkProviderBuilder(URI(url).toURL())
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    suspend fun verify(provider: AuthProvider, idToken: String): SocialIdentity = when (provider) {
        AuthProvider.APPLE -> verifyToken(
            provider = AuthProvider.APPLE,
            idToken = idToken,
            keys = appleKeys,
            issuers = APPLE_ISSUERS,
            audiences = config.appleBundleIds,
            configHint = "APPLE_BUNDLE_IDS",
        )

        AuthProvider.GOOGLE -> verifyToken(
            provider = AuthProvider.GOOGLE,
            idToken = idToken,
            keys = googleKeys,
            issuers = GOOGLE_ISSUERS,
            audiences = config.googleClientIds,
            configHint = "GOOGLE_CLIENT_IDS",
        )

        else -> throw uz.sadora.server.core.ValidationException(
            "provider",
            "Faqat apple yoki google qo'llab-quvvatlanadi",
        )
    }

    private suspend fun verifyToken(
        provider: AuthProvider,
        idToken: String,
        keys: JwkProvider,
        issuers: List<String>,
        audiences: List<String>,
        configHint: String,
    ): SocialIdentity {
        if (audiences.isEmpty()) {
            // A misconfigured server must not silently accept any audience.
            throw ApiException(
                io.ktor.http.HttpStatusCode.ServiceUnavailable,
                ErrorCodes.INTERNAL_ERROR,
                "$provider kirish sozlanmagan ($configHint)",
            )
        }

        val decoded = runCatching { JWT.decode(idToken) }.getOrElse {
            throw UnauthorizedException(ErrorCodes.SOCIAL_TOKEN_INVALID, "Token o'qib bo'lmadi")
        }

        val verified = withContext(Dispatchers.IO) {
            runCatching {
                val jwk = keys.get(decoded.keyId)
                val algorithm = Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null)
                JWT.require(algorithm)
                    .withAnyOfAudience(*audiences.toTypedArray())
                    .acceptLeeway(LEEWAY_SECONDS)
                    .build()
                    .verify(idToken)
            }.getOrElse { failure ->
                logger.warn("{} id_token rejected: {}", provider, failure.message)
                throw UnauthorizedException(
                    ErrorCodes.SOCIAL_TOKEN_INVALID,
                    "Token tekshiruvdan o'tmadi",
                )
            }
        }

        if (verified.issuer !in issuers) {
            throw UnauthorizedException(ErrorCodes.SOCIAL_TOKEN_INVALID, "Token manbasi noto'g'ri")
        }

        val emailVerified = verified.getClaim("email_verified").let { claim ->
            // Apple sends this as a string in some flows and a boolean in others.
            claim.asBoolean() ?: claim.asString()?.toBooleanStrictOrNull() ?: false
        }

        return SocialIdentity(
            provider = provider,
            subject = verified.subject,
            email = verified.getClaim("email").asString(),
            emailVerified = emailVerified,
            name = verified.getClaim("name").asString(),
        )
    }

    private companion object {
        const val LEEWAY_SECONDS = 30L
        val APPLE_ISSUERS = listOf("https://appleid.apple.com")
        val GOOGLE_ISSUERS = listOf("https://accounts.google.com", "accounts.google.com")
    }
}
