package uz.sadora.server.admin

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.AppStatus
import uz.sadora.contract.AppStatusRequest
import uz.sadora.contract.AppUpdate
import uz.sadora.contract.Language
import uz.sadora.contract.Platform
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AppSettings
import uz.sadora.server.db.dbQuery
import uz.sadora.server.i18n.errorLanguageOf
import uz.sadora.server.plugins.AdminPrincipal

/** One platform's builds: below [minBuild] must update, below [latestBuild] is told it may. */
@Serializable
data class PlatformRelease(
    val minBuild: Int? = null,
    val latestBuild: Int? = null,
    val storeUrl: String? = null,
)

@Serializable
data class Maintenance(
    val active: Boolean = false,
    /** Keyed `uz`, `ru`, `en`; Uzbek is required while it is on. */
    val message: Map<String, String> = emptyMap(),
)

/** The admin panel's "Ilova versiyasi" page, stored whole in app_settings. */
@Serializable
data class AppReleasePolicy(
    val android: PlatformRelease = PlatformRelease(storeUrl = PLAY_URL),
    val ios: PlatformRelease = PlatformRelease(),
    val maintenance: Maintenance = Maintenance(),
    val updatedAt: Instant? = null,
)

const val PLAY_URL = "https://play.google.com/store/apps/details?id=uz.sadora.app"
private const val POLICY_KEY = "app_release_policy"
private val policyJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/**
 * Forced and suggested updates, and the maintenance screen.
 *
 * The answer is read on every app start, before sign-in, so the policy is held in memory
 * for a few seconds rather than read per request. Nothing here blocks the API itself: an
 * app too old to know this endpoint keeps working, and maintenance never stops webhooks or
 * the panel — it only tells the app to show its notice.
 */
class AppReleaseService(private val audit: AuditService) {
    @Volatile private var cached: Pair<AppReleasePolicy, Instant>? = null

    suspend fun policy(): AppReleasePolicy {
        cached?.let { (policy, at) -> if (now() - at < CACHE_FOR) return policy }
        val stored = dbQuery {
            AppSettings.selectAll().where { AppSettings.key eq POLICY_KEY }.singleOrNull()?.get(AppSettings.value)
        }
        val policy = stored?.let { runCatching { policyJson.decodeFromString<AppReleasePolicy>(it) }.getOrNull() } ?: AppReleasePolicy()
        cached = policy to now()
        return policy
    }

    suspend fun status(request: AppStatusRequest, language: Language): AppStatus = statusFor(policy(), request, language)

    suspend fun save(policy: AppReleasePolicy, admin: AdminPrincipal, context: RequestContext): AppReleasePolicy {
        val cleaned = validate(policy).copy(updatedAt = now())
        dbQuery {
            AppSettings.upsert(AppSettings.key) {
                it[key] = POLICY_KEY
                it[value] = policyJson.encodeToString(AppReleasePolicy.serializer(), cleaned)
                it[updatedAt] = now().toOffsetDateTime()
                it[updatedBy] = admin.adminId
            }
        }
        cached = cleaned to now()
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = AuditActions.APP_RELEASE_POLICY_UPDATED,
                entityType = "app_setting",
                entityId = POLICY_KEY,
                metadata = mapOf(
                    "android_min" to cleaned.android.minBuild.toString(),
                    "ios_min" to cleaned.ios.minBuild.toString(),
                    "maintenance" to cleaned.maintenance.active.toString(),
                ),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        return cleaned
    }

    internal companion object {
        val CACHE_FOR = 15.seconds
        const val MESSAGE_MAX = 300

        /** Pure, so every combination of builds can be checked in a test. */
        fun statusFor(policy: AppReleasePolicy, request: AppStatusRequest, language: Language): AppStatus {
            val platform = when (request.platform) {
                Platform.ANDROID -> policy.android
                Platform.IOS -> policy.ios
                Platform.WEB -> null
            }
            val build = request.build
            val update = when {
                platform == null || build == null -> AppUpdate.NONE
                platform.minBuild != null && build < platform.minBuild -> AppUpdate.REQUIRED
                platform.latestBuild != null && build < platform.latestBuild -> AppUpdate.SUGGESTED
                else -> AppUpdate.NONE
            }
            val maintenance = policy.maintenance
            return AppStatus(
                update = update,
                storeUrl = platform?.storeUrl,
                maintenance = maintenance.active,
                message = if (maintenance.active) {
                    maintenance.message[language.name.lowercase()]?.takeIf { it.isNotBlank() } ?: maintenance.message["uz"]
                } else {
                    null
                },
            )
        }

        fun validate(policy: AppReleasePolicy): AppReleasePolicy {
            fun platform(name: String, release: PlatformRelease): PlatformRelease {
                listOf("minBuild" to release.minBuild, "latestBuild" to release.latestBuild).forEach { (field, value) ->
                    if (value != null && value < 1) throw ValidationException("$name.$field", "Build raqami musbat bo'lsin")
                }
                if (release.minBuild != null && release.latestBuild != null && release.minBuild > release.latestBuild) {
                    throw ValidationException("$name.minBuild", "Minimal build eng yangisidan katta bo'lmasin")
                }
                val url = release.storeUrl?.trim()?.takeIf { it.isNotEmpty() }
                if (url != null && !url.startsWith("https://")) throw ValidationException("$name.storeUrl", "Havola https:// bilan boshlansin")
                return release.copy(storeUrl = url)
            }
            val message = policy.maintenance.message
                .filterKeys { it in setOf("uz", "ru", "en") }
                .mapValues { it.value.trim() }
                .filterValues { it.isNotEmpty() }
            message.forEach { (language, text) ->
                if (text.length > MESSAGE_MAX) throw ValidationException("maintenance.message.$language", "Xabar eng ko'pi $MESSAGE_MAX belgi")
            }
            if (policy.maintenance.active && message["uz"] == null) {
                throw ValidationException("maintenance.message.uz", "Texnik ishlar xabarini o'zbekcha yozing")
            }
            return policy.copy(
                android = platform("android", policy.android),
                ios = platform("ios", policy.ios),
                maintenance = policy.maintenance.copy(message = message),
            )
        }
    }
}

/**
 * The app's first question, before sign-in: may this build run, and is the service open.
 * Unauthenticated, answered from memory, and in the language the request asked for.
 */
fun Route.appStatusRoutes(release: AppReleaseService) {
    post("/app/status") {
        val request = call.receive<AppStatusRequest>()
        call.respond(release.status(request, errorLanguageOf(call.request.headers["Accept-Language"])))
    }
}
