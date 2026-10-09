package uz.sadora.server.frame

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.BuyFrameRequest
import uz.sadora.contract.FrameCheckoutRequest
import uz.sadora.contract.FrameStorePurchase
import uz.sadora.contract.WearFrameRequest
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.consultation.origin
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

fun Route.frameRoutes(frames: FrameService) {
    authenticate(USER_AUTH) {
        route("/frames") {
            get {
                call.respond(frames.board(call.requireUserId()))
            }

            /** Wear one she owns; `{"key": null}` takes it off. */
            put("/worn") {
                call.respond(frames.wear(call.requireUserId(), call.receive<WearFrameRequest>().key))
            }

            /** A Gul frame: the coins and the frame together. */
            post("/buy") {
                call.respond(frames.buyWithCoins(call.requireUserId(), call.receive<BuyFrameRequest>().key))
            }

            /** A paid frame by Payme or Click: the link to pay. */
            post("/checkout") {
                call.respond(frames.checkout(call.requireUserId(), call.receive<FrameCheckoutRequest>(), call.origin()))
            }

            /** A paid frame by a store receipt; the answer is her frames as they now are. */
            post("/store") {
                call.respond(frames.buyInStore(call.requireUserId(), call.receive<FrameStorePurchase>()))
            }
        }
    }
}

/** The panel: frame prices, and one account's frames on the user card. */
fun Route.adminFrameRoutes(frames: FrameService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/frames/products") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(frames.adminProducts())
            }
            put("/{key}") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                frames.updateProduct(call.parameters["key"].orEmpty(), call.receive<UpdateFrameProductRequest>(), admin.adminId)
                call.respond(frames.adminProducts())
            }
        }
        route("/admin/users/{id}/frames") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(frames.adminUserFrames(parseUuid(call.parameters["id"].orEmpty(), "id")))
            }
            post {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val userId = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(frames.adminGrant(userId, call.receive<GrantFrameRequest>().key, admin.adminId))
            }
        }
    }
}
