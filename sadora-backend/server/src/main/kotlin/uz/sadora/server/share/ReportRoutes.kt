package uz.sadora.server.share

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
import io.ktor.server.routing.route
import uz.sadora.contract.PdfReportRequest
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.USER_AUTH

/** Her PDF report: what is left this month, and the file itself. */
fun Route.reportRoutes(reports: ReportService) {
    authenticate(USER_AUTH) {
        route("/reports") {
            get("/options") { call.respond(reports.options(call.requireUserId())) }

            post("/pdf") {
                val request = call.receive<PdfReportRequest>()
                val file = reports.pdf(call.requireUserId(), request)
                call.response.header(HttpHeaders.CacheControl, "private, no-store")
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
                call.respondBytes(file.bytes, ContentType.Application.Pdf)
            }
        }
    }
}
