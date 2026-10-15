package uz.sadora.server.widget

import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.application.hooks.ResponseSent
import uz.sadora.server.plugins.UserPrincipal

/**
 * After a successful change to anything a widget draws, her widgets and watch are told to
 * redraw. Hooked on the paths rather than in each service, so a new write under them is
 * covered without anyone remembering to call it.
 *
 * Health-data ingest is left out on purpose: the phone posts it on every open, and the
 * widget's own periodic refresh is soon enough for a step count.
 */
fun widgetRefreshPlugin(refresher: WidgetRefresher) = createApplicationPlugin("WidgetRefresh") {
    on(ResponseSent) { call ->
        if (call.request.httpMethod == HttpMethod.Get) return@on
        if (call.response.status()?.isSuccess() != true) return@on
        val path = call.request.path()
        // The profile itself (stage, language, discreet mode), not everything under it.
        if (path != "/v1/me" && WATCHED.none { path.startsWith(it) }) return@on
        val userId = call.principal<UserPrincipal>()?.userId ?: return@on
        refresher.changed(userId)
    }
}

private val WATCHED = listOf(
    "/v1/nutrition/water",
    "/v1/meds",
    "/v1/cycle",
    "/v1/prescriptions",
)
