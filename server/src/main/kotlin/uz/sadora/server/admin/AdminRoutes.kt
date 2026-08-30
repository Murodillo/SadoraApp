package uz.sadora.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import uz.sadora.contract.Ack
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.audit.AuditRepository
import uz.sadora.server.auth.RefreshTokenService
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.user.UserRepository

@Serializable
private data class CreatedId(val id: String)

/**
 * Admin panel API. Every route names the roles that may reach it — the proposal fixes a
 * strict page list per role, and repeating it at each route keeps that decision visible
 * where it is enforced rather than in a table somewhere else.
 */
fun Route.adminRoutes(
    adminAuth: AdminAuthService,
    adminService: AdminService,
    auditRepository: AuditRepository,
    statsRepository: AdminStatsRepository,
    refreshTokens: RefreshTokenService,
) {
    route("/admin") {

        /**
         * The panel itself, served from the jar.
         *
         * Static and public on purpose: the sign-in screen has to load before there is
         * a token to authorise it with. Everything the panel can actually *do* sits
         * behind [ADMIN_AUTH] below.
         */
        staticResources("/ui", "admin") {
            default("index.html")
        }

        rateLimit(RateLimits.AUTH) {
            post("/auth/login") {
                val request = call.receive<AdminSignInRequest>()
                call.respond(adminAuth.signIn(request, call.requestContext()))
            }
        }

        authenticate(ADMIN_AUTH) {

            // --- dashboard ------------------------------------------------------
            get("/stats") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(statsRepository.stats())
            }

            get("/stats/signups") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(statsRepository.signUpsPerDay(call.intParameter("days", 14, 90)))
            }

            get("/stats/events") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(statsRepository.recentEvents(call.intParameter("limit", 12, 50)))
            }

            // --- users: list and card -----------------------------------------
            get("/users") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                val filter = UserRepository.UserFilter(
                    query = call.request.queryParameters["q"],
                    status = call.enumParameter<AccountStatus>("status"),
                    language = call.enumParameter<Language>("language"),
                    lifeStage = call.enumParameter<LifeStage>("lifeStage"),
                )
                call.respond(
                    adminService.listUsers(
                        filter = filter,
                        limit = call.intParameter("limit", default = 50, max = 200),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }

            get("/users/{id}") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(adminService.userCard(userId))
            }

            post("/users/{id}/block") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<BlockUserRequest>()
                adminService.setBlocked(userId, request, admin, refreshTokens, call.requestContext())
                call.respond(Ack())
            }

            post("/users/{id}/premium") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<GrantPremiumRequest>()
                adminService.grantPremium(userId, request, admin, call.requestContext())
                call.respond(Ack())
            }

            // --- entitlements and limits --------------------------------------
            get("/features") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(adminService.featureDefinitions())
            }

            put("/features/{key}") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val key = call.parameters["key"]
                    ?: throw ValidationException("key", "Ko'rsatilmagan")
                val request = call.receive<UpdateFeatureDefinitionRequest>()
                adminService.updateFeatureDefinition(key, request, admin, call.requestContext())
                call.respond(Ack())
            }

            put("/users/{id}/features/{key}") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                val key = call.parameters["key"] ?: throw ValidationException("key", "Ko'rsatilmagan")
                val request = call.receive<SetOverrideRequest>()
                adminService.setOverride(userId, key, request, admin, call.requestContext())
                call.respond(Ack())
            }

            delete("/users/{id}/features/{key}") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                val key = call.parameters["key"] ?: throw ValidationException("key", "Ko'rsatilmagan")
                adminService.clearOverride(userId, key, admin, call.requestContext())
                call.respond(Ack())
            }

            // --- feature flags -------------------------------------------------
            get("/flags") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(adminService.flags())
            }

            put("/flags/{key}") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val key = call.parameters["key"] ?: throw ValidationException("key", "Ko'rsatilmagan")
                val request = call.receive<UpdateFlagRequest>()
                adminService.updateFlag(key, request, admin, call.requestContext())
                call.respond(Ack())
            }

            post("/flags/{key}/rules") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val key = call.parameters["key"] ?: throw ValidationException("key", "Ko'rsatilmagan")
                val request = call.receive<CreateFlagRuleRequest>()
                val id = adminService.addFlagRule(key, request, admin, call.requestContext())
                call.respond(HttpStatusCode.Created, CreatedId(id))
            }

            delete("/flags/{key}/rules/{ruleId}") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val key = call.parameters["key"] ?: throw ValidationException("key", "Ko'rsatilmagan")
                val ruleId = parseUuid(call.parameters["ruleId"].orEmpty(), "ruleId")
                adminService.removeFlagRule(key, ruleId, admin, call.requestContext())
                call.respond(Ack())
            }

            // --- audit log: owner only, as specified in the proposal ------------
            get("/audit") {
                call.requireAdminRole(AdminRole.OWNER)
                val (items, total) = auditRepository.list(
                    action = call.request.queryParameters["action"],
                    entityType = call.request.queryParameters["entityType"],
                    entityId = call.request.queryParameters["entityId"],
                    limit = call.intParameter("limit", default = 50, max = 200),
                    offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                )
                call.respond(
                    uz.sadora.contract.Page(
                        items = items,
                        total = total,
                        limit = call.intParameter("limit", default = 50, max = 200),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE),
                    ),
                )
            }
        }
    }
}
