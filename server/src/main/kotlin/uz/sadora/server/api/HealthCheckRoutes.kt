package uz.sadora.server.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.server.db.FeatureFlagsTable
import uz.sadora.server.db.dbQuery

@Serializable
data class HealthStatus(val status: String, val environment: String, val version: String)

/**
 * `/health/live` answers "is the process up" and never touches the database, so a
 * database blip does not get the container killed. `/health/ready` answers "can it serve
 * traffic" and does.
 */
fun Route.healthCheckRoutes(environment: String, version: String) {
    route("/health") {
        get("/live") {
            call.respond(HealthStatus("ok", environment, version))
        }

        get("/ready") {
            val reachable = runCatching {
                dbQuery { FeatureFlagsTable.selectAll().limit(1).count() }
            }.isSuccess
            if (reachable) {
                call.respond(HealthStatus("ready", environment, version))
            } else {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthStatus("database_unavailable", environment, version),
                )
            }
        }
    }
}
