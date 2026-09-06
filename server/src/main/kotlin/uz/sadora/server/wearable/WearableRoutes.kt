package uz.sadora.server.wearable

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import uz.sadora.contract.Ack
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.IngestSamplesRequest
import uz.sadora.contract.MetricMapping
import uz.sadora.server.api.requestContext
import uz.sadora.server.api.requireAdminRole
import uz.sadora.server.api.requireUserId
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.core.ValidationException
import uz.sadora.server.plugins.ADMIN_AUTH
import uz.sadora.server.plugins.AdminRole
import uz.sadora.server.plugins.USER_AUTH

@Serializable
data class ProviderHealthView(
    val provider: HealthProvider,
    val sampleCount: Long,
    val lastSampleAt: String? = null,
)

fun Route.wearableRoutes(wearables: WearableService) {
    authenticate(USER_AUTH) {
        route("/health-data") {
            /**
             * The device reads HealthKit or Health Connect natively and posts batches
             * here. Re-sending a sample updates it rather than adding a second one, so a
             * client that re-syncs on every launch is harmless.
             */
            post("/samples") {
                val request = call.receive<IngestSamplesRequest>()
                call.respond(wearables.ingest(call.requireUserId(), request))
            }

            get("/today") {
                call.respond(wearables.today(call.requireUserId()))
            }

            get("/daily") {
                val userId = call.requireUserId()
                val to = call.dateParameter("to") ?: wearables.today(userId).date
                val from = call.dateParameter("from") ?: to.minus(13, DateTimeUnit.DAY)
                call.respond(wearables.daily(userId, from, to))
            }

            /** The Data Sources screen. */
            get("/sources") {
                call.respond(wearables.sources(call.requireUserId()))
            }
        }
    }
}

/** Pages 11 and 12 of the admin panel: provider health, and the mapping table. */
fun Route.adminWearableRoutes(
    wearables: WearableService,
    repository: WearableRepository,
    audit: AuditService,
) {
    authenticate(ADMIN_AUTH) {
        route("/admin/wearables") {
            get("/providers") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(
                    repository.providerTotals().map { (provider, stats) ->
                        ProviderHealthView(provider, stats.first, stats.second?.toString())
                    }.sortedByDescending { it.sampleCount },
                )
            }

            get("/mappings") {
                call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.ANALYST)
                call.respond(wearables.mappings())
            }

            /**
             * Adding a provider, or following one that renamed a field, is a row here —
             * no migration, no release.
             */
            put("/mappings") {
                val admin = call.requireAdminRole(AdminRole.OWNER, AdminRole.ADMIN)
                val request = call.receive<MetricMapping>()
                wearables.saveMapping(request)
                audit.record(
                    AuditEntry(
                        actorType = ActorType.ADMIN,
                        actorId = admin.adminId,
                        actorLabel = admin.role.name.lowercase(),
                        action = "wearable.mapping_updated",
                        entityType = "metric_mapping",
                        entityId = "${request.provider.name.lowercase()}:${request.providerMetric}",
                        metadata = mapOf(
                            "metric" to request.metric.name.lowercase(),
                            "scale" to request.scale.toString(),
                            "active" to request.active.toString(),
                        ),
                        ip = call.requestContext().ip,
                    ),
                )
                call.respond(Ack())
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.dateParameter(name: String): LocalDate? {
    val raw = request.queryParameters[name] ?: return null
    return runCatching { LocalDate.parse(raw) }.getOrElse {
        throw ValidationException(name, "YYYY-MM-DD formatida bo'lishi kerak")
    }
}
