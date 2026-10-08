package uz.sadora.server.doctor

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import uz.sadora.contract.MarkBadgesSeenRequest
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.StartConsultationRequest
import uz.sadora.contract.UpdateDoctorProfileRequest
import uz.sadora.server.api.enumParameter
import uz.sadora.server.api.intParameter
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.community.CommunityService
import uz.sadora.server.community.MessagingService
import uz.sadora.server.consultation.ConsultationService
import uz.sadora.server.core.parseUuid
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

/**
 * The doctor role in the app: her own application under `/doctor`, and the verified
 * doctors as readers see them under `/doctors`.
 */
fun Route.doctorRoutes(
    doctors: DoctorService,
    community: CommunityService,
    messaging: MessagingService,
    consultations: ConsultationService,
) {
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
            /**
             * Questions no doctor has answered yet — the panel's work list, newest first.
             * A list, not a page, as the first builds read it: a page shorter than `limit`
             * is the last, and `offset` reads on from where the panel is.
             */
            get("/questions") {
                call.respond(
                    community.doctorQuestions(
                        viewer = call.requireUserId(),
                        topic = call.enumParameter<CommunityTopic>("topic"),
                        limit = call.intParameter("limit", default = 50, max = 100),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }
        }

        route("/doctors") {
            /**
             * The directory, `limit` doctors from `offset`. The recommended order weighs
             * ratings and who is online now, so it is decided over every doctor and the
             * page is cut from it afterwards; no limit is the whole list, as old builds read it.
             */
            get {
                val limit = call.intParameter("limit", default = CommunityService.DIRECTORY_MAX, max = CommunityService.DIRECTORY_MAX)
                val offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE)
                val ordered = consultations.decorate(community.doctors(call.requireUserId()))
                call.respond(ordered.drop(offset).take(limit))
            }
            get("/{id}") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val viewer = call.requireUserId()
                call.respond(consultations.decorate(viewer, community.doctorProfile(viewer, id)))
            }
            /** Her posts a page at a time; her page carries only the first few. */
            get("/{id}/posts") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                call.respond(
                    community.doctorPosts(
                        viewer = call.requireUserId(),
                        doctorId = id,
                        limit = call.intParameter("limit", default = CommunityService.PROFILE_POSTS, max = CommunityService.MAX_PROFILE_POSTS),
                        offset = call.intParameter("offset", default = 0, max = Int.MAX_VALUE).toLong(),
                    ),
                )
            }
            /** A patient opens a consultation with her, or opens it again. */
            post("/{id}/consultations") {
                val id = parseUuid(call.parameters["id"].orEmpty(), "id")
                val request = runCatching { call.receive<StartConsultationRequest>() }.getOrDefault(StartConsultationRequest())
                call.respond(HttpStatusCode.Created, messaging.startConsultation(call.requireUserId(), id, request))
            }
        }
    }
}

/**
 * Her badge board: reading it awards any tier newly crossed, and `seen` says the unlock
 * has been played — by the app or the web panel, whichever she opened first.
 */
fun Route.doctorBadgeRoutes(badges: DoctorBadgeService) {
    authenticate(USER_AUTH) {
        route("/doctor/badges") {
            get {
                call.respond(badges.board(call.requireUserId()))
            }
            post("/seen") {
                val request = call.receive<MarkBadgesSeenRequest>()
                badges.markSeen(call.requireUserId(), request.keys)
                call.respond(Ack())
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
