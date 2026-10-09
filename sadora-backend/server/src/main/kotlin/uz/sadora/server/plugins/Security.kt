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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.dbQuery

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

/**
 * The signed-in admin operator. [role] gates which admin routes are reachable.
 * [totpSetupRequired] is an operator who has not enrolled 2FA where it is mandatory:
 * she reaches her own account page and nothing else until she does.
 */
data class AdminPrincipal(val adminId: Uuid, val role: AdminRole, val totpSetupRequired: Boolean = false)

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

fun Application.configureSecurity(jwtService: JwtService, accountGate: AccountGate, adminRequireTotp: Boolean) {
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
                val adminId = credential.principalOf(TokenSubjectType.ADMIN) ?: return@validate null
                // Read fresh on every call rather than trusted from the token: a disabled
                // or demoted operator loses access now, not when her token runs out.
                // There are a handful of operators, so the lookup costs nothing.
                val row = dbQuery {
                    AdminUsers.selectAll().where { AdminUsers.id eq adminId }.singleOrNull()
                } ?: return@validate null
                if (row[AdminUsers.status] != "active") return@validate null
                val role = AdminRole.entries.firstOrNull { it.name.equals(row[AdminUsers.role], true) }
                    ?: return@validate null
                AdminPrincipal(
                    adminId = adminId,
                    role = role,
                    totpSetupRequired = adminRequireTotp && !row[AdminUsers.totpEnabled],
                )
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
