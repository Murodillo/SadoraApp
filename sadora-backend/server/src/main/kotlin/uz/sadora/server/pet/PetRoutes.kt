package uz.sadora.server.pet

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.ChoosePetRequest
import uz.sadora.contract.PetCheckoutRequest
import uz.sadora.contract.PetNudgeRequest
import uz.sadora.contract.PetStorePurchase
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.consultation.origin
import uz.sadora.contract.PetKind
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

fun Route.petRoutes(pets: PetService, shop: PetShopService) {
    authenticate(USER_AUTH) {
        route("/pet") {
            get {
                call.respond(pets.state(call.requireUserId()))
            }

            put {
                val request = call.receive<ChoosePetRequest>()
                call.respond(pets.choose(call.requireUserId(), request.pet))
            }

            /** The legendary pet by Payme or Click: the link to pay. */
            post("/checkout") {
                val request = call.receive<PetCheckoutRequest>()
                call.respond(shop.checkout(call.requireUserId(), request, call.origin()))
            }

            /** The legendary pet by a store receipt; the answer is her pets as they now are. */
            post("/store") {
                val userId = call.requireUserId()
                shop.buyInStore(userId, call.receive<PetStorePurchase>())
                call.respond(pets.state(userId))
            }

            post("/offer/seen") {
                call.receive<Ack>()
                call.respond(pets.offerSeen(call.requireUserId()))
            }

            /** 402 for a free account; an empty answer when the pet stays quiet. */
            post("/nudge") {
                val request = call.receive<PetNudgeRequest>()
                call.respond(pets.nudge(call.requireUserId(), request))
            }
        }
    }
}

/** The panel's price for a legendary pet. The store prices are set in their consoles. */
fun Route.adminPetRoutes(shop: PetShopService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/billing/pet-products") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT, AdminRole.ANALYST)
                call.respond(shop.adminProducts())
            }
            put("/{pet}") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val key = call.parameters["pet"].orEmpty()
                val pet = PetKind.entries.firstOrNull { it.wireKey == key && it.legendary }
                    ?: throw NotFoundException("Bunday mahsulot yo'q")
                shop.updateProduct(pet, call.receive<UpdatePetProductRequest>(), admin.adminId)
                call.respond(Ack())
            }
        }
    }
}
