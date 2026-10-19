package uz.sadora.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
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
import uz.sadora.server.api.requireAdmin
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
    analyticsRepository: AdminAnalyticsRepository,
    refreshTokens: RefreshTokenService,
    operators: AdminOperatorService,
    support: AdminSupportService,
    search: AdminSearchService,
    system: SystemHealthService,
    campaigns: CampaignService,
    release: AppReleaseService,
) {
    route("/admin") {

        rateLimit(RateLimits.AUTH) {
            post("/auth/login") {
                val request = call.receive<AdminSignInRequest>()
                call.respond(adminAuth.signIn(request, call.requestContext()))
            }
        }

        authenticate(ADMIN_AUTH) {

            // --- the operator's own account -------------------------------------
            // Every role reaches these: 2FA is each operator's own to switch on, and an
            // analyst who cannot enrol is an analyst whose account stays a password.
            get("/me") {
                call.respond(adminAuth.me(call.requireAdmin().adminId))
            }

            post("/me/totp/start") {
                call.respond(adminAuth.startTotpEnrolment(call.requireAdmin().adminId))
            }

            post("/me/totp/confirm") {
                val request = call.receive<TotpConfirmRequest>()
                adminAuth.confirmTotp(call.requireAdmin().adminId, request, call.requestContext())
                call.respond(Ack())
            }

            post("/me/password") {
                val request = call.receive<AdminPasswordChangeRequest>()
                adminAuth.changePassword(call.requireAdmin().adminId, request, call.requestContext())
                call.respond(Ack())
            }

            post("/me/totp/disable") {
                val request = call.receive<TotpDisableRequest>()
                adminAuth.disableTotp(call.requireAdmin().adminId, request, call.requestContext())
                call.respond(Ack())
            }

            // --- dashboard ------------------------------------------------------
            get("/stats") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(statsRepository.stats())
            }

            // The dashboard's "Bugun" queues; each role gets the ones its pages can work.
            get("/stats/todo") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(statsRepository.todo(principal.role))
            }

            get("/stats/signups") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(statsRepository.signUpsPerDay(call.intParameter("days", 14, 90)))
            }

            // The analytics page. Analyst reads it; Support's job is one account at a time,
            // and the cohort numbers here are not that.
            get("/stats/analytics") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(analyticsRepository.analytics(call.intParameter("days", 30, 180)))
            }

            get("/stats/events") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                val events = statsRepository.recentEvents(call.intParameter("limit", 12, 50))
                // The audit log is the Owner's. The dashboard feed shows everybody what
                // happened, but who, from where and why stay with the log itself — the
                // same rows were readable here in full by every role.
                call.respond(if (principal.role == AdminRole.OWNER) events else events.map { it.redacted() })
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
                call.respond(adminService.userCard(userId).copy(connections = support.connections(userId)))
            }

            // --- the card's support side: what happened, and the operators' notes ---
            get("/users/{id}/timeline") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(support.timeline(userId, principal, call.intParameter("limit", 40, 100)))
            }

            get("/users/{id}/notes") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(support.notes(userId, principal))
            }

            post("/users/{id}/notes") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<AddSupportNoteRequest>()
                call.respond(HttpStatusCode.Created, support.addNote(userId, request, principal, call.requestContext()))
            }

            delete("/users/{id}/notes/{noteId}") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                val noteId = parseUuid(call.parameters["noteId"].orEmpty(), "noteId")
                support.deleteNote(userId, noteId, principal, call.requestContext())
                call.respond(Ack())
            }

            // --- one search box for the panel (Ctrl+K) ---------------------------
            // The roles that can open what it finds: a card, a doctor, a payment's owner.
            get("/search") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                call.respond(search.search(call.request.queryParameters["q"].orEmpty()))
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

            get("/features/usage") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(statsRepository.featureUsage(call.intParameter("days", 7, 90)))
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

            // --- system health: what is broken outside the product --------------
            get("/system/health") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                call.respond(system.health())
            }

            // --- push campaigns: written by Owner and Admin, read by Analyst ---------
            get("/campaigns") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(campaigns.list())
            }

            post("/campaigns/audience") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(campaigns.audience(call.receive<CampaignSegment>()))
            }

            post("/campaigns") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<CreateCampaignRequest>()
                call.respond(HttpStatusCode.Created, campaigns.create(request, admin, call.requestContext()))
            }

            post("/campaigns/test") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                call.respond(campaigns.test(call.receive<CampaignTestRequest>(), admin, call.requestContext()))
            }

            post("/campaigns/{id}/cancel") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(campaigns.cancel(id, admin, call.requestContext()))
            }

            // --- app versions and maintenance ----------------------------------
            get("/app-release") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(release.policy())
            }

            put("/app-release") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                call.respond(release.save(call.receive<AppReleasePolicy>(), admin, call.requestContext()))
            }

            // --- operators: the Owner's alone -----------------------------------
            get("/operators") {
                call.requireAdminRole(AdminRole.OWNER)
                call.respond(operators.list())
            }

            post("/operators") {
                val owner = call.requireAdminRole(AdminRole.OWNER)
                val request = call.receive<CreateOperatorRequest>()
                call.respond(HttpStatusCode.Created, operators.create(request, owner, call.requestContext()))
            }

            put("/operators/{id}/role") {
                val owner = call.requireAdminRole(AdminRole.OWNER)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<UpdateOperatorRequest>()
                call.respond(operators.changeRole(id, request, owner, call.requestContext()))
            }

            put("/operators/{id}/status") {
                val owner = call.requireAdminRole(AdminRole.OWNER)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<OperatorStatusRequest>()
                call.respond(operators.setActive(id, request.active, owner, call.requestContext()))
            }

            post("/operators/{id}/reset-password") {
                val owner = call.requireAdminRole(AdminRole.OWNER)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(operators.resetPassword(id, owner, call.requestContext()))
            }

            post("/operators/{id}/reset-totp") {
                val owner = call.requireAdminRole(AdminRole.OWNER)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(operators.resetTotp(id, owner, call.requestContext()))
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
