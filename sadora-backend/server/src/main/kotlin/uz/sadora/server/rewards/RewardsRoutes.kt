package uz.sadora.server.rewards

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.uuid.Uuid
import uz.sadora.contract.Ack
import uz.sadora.contract.AdjustCoinsRequest
import uz.sadora.contract.ClaimReferralRequest
import uz.sadora.contract.CreateShopProductRequest
import uz.sadora.contract.RedeemRequest
import uz.sadora.contract.SaveCoinRuleRequest
import uz.sadora.contract.SaveHomeLayoutRequest
import uz.sadora.contract.SaveShopProductRequest
import uz.sadora.contract.UpdateRedemptionRequest
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.api.requireUserId
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

/**
 * The reward endpoints the app calls.
 *
 * `POST /rewards/check-in` runs on every launch and is deliberately cheap and
 * idempotent: it is the app saying "I opened", and the server deciding whether that
 * means anything today.
 */
fun Route.rewardsRoutes(
    rewards: RewardsService,
    shop: ShopService,
    home: HomeLayoutRepository,
) {
    authenticate(USER_AUTH) {
        route("/rewards") {
            post("/check-in") {
                call.respond(rewards.checkIn(call.requireUserId()))
            }

            get {
                call.respond(rewards.summary(call.requireUserId(), call.intParameter("limit", 30, 100)))
            }

            get("/referral") {
                call.respond(rewards.referral(call.requireUserId()))
            }

            post("/referral/claim") {
                val request = call.receive<ClaimReferralRequest>()
                call.respond(rewards.claimReferral(call.requireUserId(), request.code))
            }
        }

        route("/shop") {
            get {
                call.respond(shop.catalogue(call.requireUserId()))
            }

            get("/redemptions") {
                call.respond(shop.redemptions(call.requireUserId()))
            }

            post("/redeem") {
                val request = call.receive<RedeemRequest>()
                call.respond(shop.redeem(call.requireUserId(), parseUuid(request.productId, "productId")))
            }
        }

        route("/me/home-layout") {
            get {
                call.respond(home.layoutOf(call.requireUserId()))
            }

            put {
                val request = call.receive<SaveHomeLayoutRequest>()
                call.respond(home.save(call.requireUserId(), request.widgets))
            }

            delete {
                call.respond(home.reset(call.requireUserId()))
            }
        }
    }
}

/**
 * The operator's side of the scheme: what each action pays, what the shop sells, and at
 * what discount.
 *
 * Analysts read; only Owner and Admin write. A rate is a lever on the whole economy and
 * a discount is a commitment to a partner — neither belongs to a support account.
 */
fun Route.adminRewardsRoutes(
    rewards: RewardsService,
    shop: ShopService,
    audit: AuditService,
) {
    authenticate(ADMIN_AUTH) {
        route("/admin/rewards") {
            get("/overview") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(shop.overview())
            }

            get("/rules") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(rewards.rules())
            }

            put("/rules/{reason}") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val reason = call.parameters["reason"].orEmpty()
                val request = call.receive<SaveCoinRuleRequest>()
                val rule = rewards.saveRule(reason, request.amount, request.dailyCap, request.enabled)
                audit.record(
                    AuditEntry(
                        actorType = ActorType.ADMIN,
                        actorId = principal.adminId,
                        action = AuditActions.REWARDS_RULE_UPDATED,
                        entityType = "coin_rule",
                        entityId = reason,
                        metadata = mapOf(
                            "amount" to request.amount.toString(),
                            "dailyCap" to (request.dailyCap?.toString() ?: "none"),
                            "enabled" to request.enabled.toString(),
                        ),
                        ip = call.requestContext().ip,
                        userAgent = call.requestContext().userAgent,
                    ),
                )
                call.respond(rule)
            }

            get("/users/{id}") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(rewards.adminCard(call.userIdParameter()))
            }

            post("/users/{id}/adjust") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val userId = call.userIdParameter()
                val request = call.receive<AdjustCoinsRequest>()
                val balance = rewards.adjust(userId, request.amount, request.note)
                audit.record(
                    AuditEntry(
                        actorType = ActorType.ADMIN,
                        actorId = principal.adminId,
                        action = AuditActions.REWARDS_COINS_ADJUSTED,
                        entityType = "user",
                        entityId = userId.toString(),
                        metadata = mapOf(
                            "amount" to request.amount.toString(),
                            "note" to request.note,
                            "balance" to balance.balance.toString(),
                        ),
                        ip = call.requestContext().ip,
                        userAgent = call.requestContext().userAgent,
                    ),
                )
                call.respond(balance)
            }
        }

        route("/admin/shop") {
            get("/products") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(shop.adminCatalogue())
            }

            post("/products") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<CreateShopProductRequest>()
                val product = shop.create(request)
                audit.record(shopEntry(principal.adminId, AuditActions.SHOP_PRODUCT_CREATED, product.id, call))
                call.respond(HttpStatusCode.Created, product)
            }

            put("/products/{id}") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<SaveShopProductRequest>()
                val product = shop.update(id, request)
                audit.record(shopEntry(principal.adminId, AuditActions.SHOP_PRODUCT_UPDATED, product.id, call))
                call.respond(product)
            }

            delete("/products/{id}") {
                val principal = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val removed = shop.delete(id)
                audit.record(
                    shopEntry(
                        principal.adminId,
                        if (removed) AuditActions.SHOP_PRODUCT_DELETED else AuditActions.SHOP_PRODUCT_DEACTIVATED,
                        id.toString(),
                        call,
                    ),
                )
                call.respond(Ack(removed))
            }

            get("/redemptions") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(shop.recentRedemptions(call.intParameter("limit", 50, 200)))
            }

            put("/redemptions/{id}") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = call.receive<UpdateRedemptionRequest>()
                shop.setRedemptionStatus(id, request.status)
                call.respond(Ack())
            }
        }
    }
}

private fun shopEntry(
    adminId: Uuid,
    action: String,
    entityId: String,
    call: io.ktor.server.application.ApplicationCall,
) = AuditEntry(
    actorType = ActorType.ADMIN,
    actorId = adminId,
    action = action,
    entityType = "shop_product",
    entityId = entityId,
    ip = call.requestContext().ip,
    userAgent = call.requestContext().userAgent,
)

private fun io.ktor.server.application.ApplicationCall.userIdParameter(): Uuid =
    parseUuid(parameters["id"].orEmpty(), "id")
