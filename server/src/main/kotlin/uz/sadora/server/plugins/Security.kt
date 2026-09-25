package uz.sadora.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import kotlin.uuid.Uuid
import uz.sadora.contract.ApiError
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.ErrorCodes
import uz.sadora.server.auth.JwtService
import uz.sadora.server.auth.TokenSubjectType

const val USER_AUTH: String = "user-jwt"
const val ADMIN_AUTH: String = "admin-jwt"

/** The signed-in mobile user. Nothing but identity — everything else is read fresh. */
data class UserPrincipal(val userId: Uuid)

/**
 * A valid token for an account that may no longer act — blocked, or waiting to be
 * erased. Authentication succeeds, so the call reaches the route, and the route's
 * `requireUserId()` turns this into the same `account_blocked` refusal sign-in gives.
 * Refusing inside `validate` would only produce a bare 401, which the app reads as an
 * expired session and answers by trying to refresh.
 */
data class BlockedPrincipal(val userId: Uuid, val message: String)

/** The signed-in admin operator. [role] gates which admin routes are reachable. */
data class AdminPrincipal(val adminId: Uuid, val role: AdminRole)

/**
 * Admin roles, narrowest last. The proposal fixes these four and the page list each one
 * may reach; [AdminRole.SUPPORT] in particular can open a user card but never her health
 * data, which is enforced by the user card containing none.
 */
enum class AdminRole {
    OWNER, ADMIN, SUPPORT, ANALYST;

    fun canManageContent(): Boolean = this == OWNER || this == ADMIN
    fun canManageUsers(): Boolean = this == OWNER || this == ADMIN || this == SUPPORT
    fun canReadAudit(): Boolean = this == OWNER
}

fun Application.configureSecurity(jwtService: JwtService, accountGate: AccountGate) {
    install(Authentication) {
        jwt(USER_AUTH) {
            realm = jwtService.realm
            verifier(jwtService.verifier)
            validate { credential ->
                val userId = credential.principalOf(TokenSubjectType.USER) ?: return@validate null
                // The token is good; the account behind it may not be. Blocking and a
                // deletion request revoke the refresh tokens, but an access token already
                // on the phone stays valid for its whole life — this is where it stops.
                accountGate.blockedMessage(userId)
                    ?.let { BlockedPrincipal(userId, it) }
                    ?: UserPrincipal(userId)
            }
            challenge { _, _ -> call.respondUnauthorized() }
        }

        jwt(ADMIN_AUTH) {
            realm = jwtService.realm
            verifier(jwtService.verifier)
            validate { credential ->
                credential.principalOf(TokenSubjectType.ADMIN)?.let { adminId ->
                    val role = credential.payload.getClaim(CLAIM_ROLE).asString()
                        ?.let { stored -> AdminRole.entries.firstOrNull { it.name.equals(stored, true) } }
                        ?: return@validate null
                    AdminPrincipal(adminId, role)
                }
            }
            challenge { _, _ -> call.respondUnauthorized() }
        }
    }
}

const val CLAIM_ROLE: String = "role"

/**
 * A user token must not open an admin route and vice versa, so the subject type is
 * checked here rather than left to each route to remember.
 */
private fun io.ktor.server.auth.jwt.JWTCredential.principalOf(expected: TokenSubjectType): Uuid? {
    val type = payload.getClaim(JwtService.CLAIM_TYPE).asString() ?: return null
    if (!type.equals(expected.name, ignoreCase = true)) return null
    return payload.subject?.let { runCatching { Uuid.parse(it) }.getOrNull() }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondUnauthorized() {
    respond(
        HttpStatusCode.Unauthorized,
        ApiErrorResponse(
            ApiError(
                code = ErrorCodes.UNAUTHORIZED,
                message = "Avtorizatsiya talab qilinadi",
                requestId = callId,
            ),
        ),
    )
}
