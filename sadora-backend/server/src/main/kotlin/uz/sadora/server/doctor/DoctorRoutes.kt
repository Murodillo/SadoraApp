package uz.sadora.server.doctor

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.UpdateDoctorProfileRequest
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.community.CommunityService
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

/**
 * The doctor role in the app: her own application under `/doctor`, and the verified
 * doctors as readers see them under `/doctors`.
 */
fun Route.doctorRoutes(doctors: DoctorService, community: CommunityService) {
    authenticate(USER_AUTH) {
        route("/doctor") {
            get("/me") {
                call.respond(doctors.account(call.requireUserId()))
            }
            put("/me") {
                val request = call.receive<UpdateDoctorProfileRequest>()
                call.respond(doctors.updateProfile(call.requireUserId(), request))
            }
            post("/application") {
                val request = call.receive<DoctorApplicationRequest>()
                call.respond(doctors.apply(call.requireUserId(), request))
            }
            /** Questions no doctor has answered yet — the panel's work list. */
            get("/questions") {
                call.respond(
                    community.doctorQuestions(
                        viewer = call.requireUserId(),
                        topic = call.enumParameter<CommunityTopic>("topic"),
                        limit = call.intParameter("limit", default = 50, max = 100),
                    ),
                )
            }
        }

        route("/doctors") {
            get {
                call.respond(community.doctors(call.requireUserId()))
            }
            get("/{id}") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(community.doctorProfile(call.requireUserId(), id))
            }
        }
    }
}

/** The review queue. Support may read and look at documents; Owner and Admin decide. */
fun Route.adminDoctorRoutes(doctors: DoctorService) {
    authenticate(ADMIN_AUTH) {
        route("/admin/doctors") {
            get {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                call.respond(
                    doctors.list(
                        status = call.enumParameter<DoctorStatus>("status"),
                        limit = call.intParameter("limit", default = 50, max = 200),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }
            get("/counts") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                call.respond(doctors.counts())
            }
            route("/{id}") {
                get {
                    call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                    call.respond(doctors.detail(call.doctorId()))
                }
                get("/documents/{documentId}") {
                    call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.SUPPORT)
                    val document = doctors.document(
                        call.doctorId(),
                        parseUuid(call.parameters["documentId"].orEmpty(), "documentId"),
                    )
                    // A licence photo is personal data: never cached anywhere on the way.
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondBytes(document.bytes, ContentType.parse(document.mimeType))
                }
                post("/review") {
                    val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                    val request = call.receive<DoctorReviewRequest>()
                    doctors.review(call.doctorId(), request, admin, call.requestContext())
                    call.respond(Ack())
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.doctorId() = parseUuid(parameters["id"].orEmpty(), "id")
