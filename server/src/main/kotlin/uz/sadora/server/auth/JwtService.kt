package uz.sadora.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import kotlin.time.Instant
import kotlin.uuid.Uuid
import uz.sadora.server.config.JwtConfig
import uz.sadora.server.core.now

/** Who the caller is. Admin tokens are a separate realm from user tokens. */
enum class TokenSubjectType { USER, ADMIN }

class JwtService(private val config: JwtConfig) {

    private val algorithm: Algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    val realm: String = "sadora"

    /**
     * Access tokens are short-lived and carry only identity — no entitlements, no
     * profile. Anything that can change during the token's life is read fresh, so an
     * account blocked at 12:00 cannot keep working until 12:15.
     */
    fun issueAccessToken(subjectId: Uuid, type: TokenSubjectType): AccessToken {
        val issuedAt = now()
        val expiresAt = issuedAt + config.accessTokenTtl
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(subjectId.toString())
            .withClaim(CLAIM_TYPE, type.name.lowercase())
            .withJWTId(Uuid.random().toString())
            .withIssuedAt(issuedAt.toJavaDate())
            .withExpiresAt(expiresAt.toJavaDate())
            .sign(algorithm)
        return AccessToken(token, expiresAt)
    }

    /**
     * Admin tokens carry the role so route guards do not need a database read on every
     * request. A role change therefore takes effect on the operator's next sign-in,
     * which is acceptable for a team of five to ten; revoking access is done by
     * deactivating the account, and that is checked at sign-in.
     */
    fun issueAdminToken(adminId: Uuid, role: uz.sadora.server.plugins.AdminRole): AccessToken {
        val issuedAt = now()
        val expiresAt = issuedAt + config.accessTokenTtl
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(adminId.toString())
            .withClaim(CLAIM_TYPE, TokenSubjectType.ADMIN.name.lowercase())
            .withClaim(uz.sadora.server.plugins.CLAIM_ROLE, role.name.lowercase())
            .withJWTId(Uuid.random().toString())
            .withIssuedAt(issuedAt.toJavaDate())
            .withExpiresAt(expiresAt.toJavaDate())
            .sign(algorithm)
        return AccessToken(token, expiresAt)
    }

    companion object {
        const val CLAIM_TYPE = "typ"
    }
}

data class AccessToken(val value: String, val expiresAt: Instant)

private fun Instant.toJavaDate(): Date = Date(toEpochMilliseconds())
