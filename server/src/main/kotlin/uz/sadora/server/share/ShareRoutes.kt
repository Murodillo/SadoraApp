package uz.sadora.server.share

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import uz.sadora.contract.Ack
import uz.sadora.contract.CreateShareRequest
import uz.sadora.contract.Language
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireUserId
import uz.sadora.server.plugins.RateLimits
import uz.sadora.server.plugins.USER_AUTH

/** Hers: make a link, see the ones she made, take one back. Behind the user's token. */
fun Route.shareRoutes(shares: ShareService) {
    authenticate(USER_AUTH) {
        route("/me/shares") {
            post {
                val request = runCatching { call.receive<CreateShareRequest>() }.getOrDefault(CreateShareRequest())
                call.respond(HttpStatusCode.Created, shares.create(call.requireUserId(), request, call.requestContext().ip))
            }

            get {
                call.respond(shares.list(call.requireUserId()))
            }

            delete("/{id}") {
                shares.revoke(call.requireUserId(), call.parameters["id"].orEmpty(), call.requestContext().ip)
                call.respond(Ack())
            }
        }

        /** The same document as JSON, for her own export. */
        get("/me/export") {
            val userId = call.requireUserId()
            val language = call.request.queryParameters["lang"]?.let(::languageOf)
            call.response.header("Content-Disposition", "attachment; filename=\"sadora-export.json\"")
            call.respond(shares.summaryFor(userId, language ?: Language.UZ))
        }
    }
}

/**
 * The public page — outside the API prefix, outside auth, and rate limited per address:
 * a token is unguessable, but the door it opens still should not be tried ten thousand
 * times a minute. An unknown token gets the same answer as an expired one.
 */
fun Route.publicShareRoutes(shares: ShareService) {
    rateLimit(RateLimits.SHARE) {
        route("/share/{token}") {
            get {
                val token = call.parameters["token"].orEmpty()
                val language = call.request.queryParameters["lang"]?.let(::languageOf)
                val context = call.requestContext()
                val summary = shares.open(token, language, context.ip, context.userAgent)
                if (summary == null) {
                    call.respondText(DoctorPage.gone(language ?: Language.UZ), ContentType.Text.Html, HttpStatusCode.NotFound)
                } else {
                    // Never cached, never framed: the page is health data, shown once.
                    call.response.header("Cache-Control", "no-store")
                    call.respondText(DoctorPage.render(summary, summary.language), ContentType.Text.Html)
                }
            }

            get("/json") {
                val token = call.parameters["token"].orEmpty()
                val language = call.request.queryParameters["lang"]?.let(::languageOf)
                val context = call.requestContext()
                val summary = shares.open(token, language, context.ip, context.userAgent)
                    ?: return@get call.respond(HttpStatusCode.NotFound, Ack(false))
                call.response.header("Cache-Control", "no-store")
                call.respond(summary)
            }
        }
    }
}

private fun languageOf(raw: String): Language? =
    Language.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
