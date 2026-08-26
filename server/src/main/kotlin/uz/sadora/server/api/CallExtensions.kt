package uz.sadora.server.api

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.plugins.origin
import io.ktor.server.request.userAgent
import kotlin.uuid.Uuid
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.UnauthorizedException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.UserPrincipal

fun ApplicationCall.requireUserId(): Uuid =
    principal<UserPrincipal>()?.userId ?: throw UnauthorizedException()

fun ApplicationCall.requireAdmin(): AdminPrincipal =
    principal<AdminPrincipal>() ?: throw UnauthorizedException()

/**
 * Role check for admin routes. Phrased as "these roles may" rather than "this role may
 * not", so adding a role defaults to no access instead of accidental access.
 */
fun ApplicationCall.requireAdminRole(vararg allowed: AdminRole): AdminPrincipal {
    val principal = requireAdmin()
    if (principal.role !in allowed) {
        throw ForbiddenException(message = "Bu amal uchun ruxsat yo'q")
    }
    return principal
}

/**
 * Client IP as seen by the load balancer. `origin.remoteHost` already honours the
 * forwarded headers Ktor is configured to trust.
 */
fun ApplicationCall.requestContext(): RequestContext = RequestContext(
    ip = request.origin.remoteHost,
    userAgent = request.userAgent(),
)

fun ApplicationCall.deviceIdHeader(): String? = request.header("X-Device-Id")

fun ApplicationCall.intParameter(name: String, default: Int, max: Int): Int {
    val raw = request.queryParameters[name] ?: return default
    val value = raw.toIntOrNull() ?: throw ValidationException(name, "Butun son bo'lishi kerak")
    if (value < 0) throw ValidationException(name, "Manfiy bo'lishi mumkin emas")
    return value.coerceAtMost(max)
}

inline fun <reified E : Enum<E>> ApplicationCall.enumParameter(name: String): E? {
    val raw = request.queryParameters[name] ?: return null
    return enumValues<E>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw ValidationException(name, "Noma'lum qiymat: $raw")
}
