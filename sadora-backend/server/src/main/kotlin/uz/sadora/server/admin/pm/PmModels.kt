package uz.sadora.server.admin.pm

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.sadora.server.plugins.AdminRole

/*
 * The shapes of the "Loyiha" section: tasks on a board, epics and releases they belong to,
 * and the team's docs. The panel and tools/pm both speak these.
 */

/** Who reaches the section at all, and who may also archive epics and releases. */
val PM_ROLES = arrayOf(AdminRole.OWNER, AdminRole.ADMIN, AdminRole.DEVELOPER)
val PM_LEAD_ROLES = arrayOf(AdminRole.OWNER, AdminRole.ADMIN)

/** The board's columns, left to right. */
@Serializable
enum class PmStatus {
    @SerialName("backlog") BACKLOG,
    @SerialName("todo") TODO,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("review") REVIEW,
    @SerialName("done") DONE,
    ;

    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String): PmStatus = entries.first { it.key == key }
    }
}

@Serializable
enum class PmTaskType {
    @SerialName("bug") BUG,
    @SerialName("feature") FEATURE,
    @SerialName("task") TASK,
    ;

    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String): PmTaskType = entries.first { it.key == key }
    }
}

@Serializable
enum class PmPriority {
    @SerialName("critical") CRITICAL,
    @SerialName("high") HIGH,
    @SerialName("medium") MEDIUM,
    @SerialName("low") LOW,
    ;

    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String): PmPriority = entries.first { it.key == key }
    }
}

/** The parts of Sadora a task can touch: the four frontends, the server, and the rest. */
val PM_COMPONENTS: List<String> = listOf(
    "android", "ios", "doctor_app", "backend", "admin", "doctor_admin", "landing", "infra", "design",
)

@Serializable
data class PmPerson(val id: String, val name: String, val email: String, val role: AdminRole)

@Serializable
data class PmTaskSummary(
    val id: String,
    val key: String,
    val title: String,
    val type: PmTaskType,
    val status: PmStatus,
    val priority: PmPriority,
    val components: List<String>,
    val labels: List<String>,
    val assigneeId: String? = null,
    val epicId: String? = null,
    val releaseId: String? = null,
    val parentKey: String? = null,
    val dueDate: LocalDate? = null,
    val position: Double,
    val archived: Boolean,
    val subtasks: Int,
    val subtasksDone: Int,
    val comments: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class PmComment(val id: String, val authorId: String? = null, val body: String, val createdAt: Instant)

@Serializable
data class PmTaskEvent(
    val kind: String,
    val actorId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val at: Instant,
)

@Serializable
data class PmCommit(
    val sha: String,
    val branch: String,
    val message: String,
    val author: String,
    val url: String,
    val committedAt: Instant,
)

@Serializable
data class PmTaskDetail(
    val id: String,
    val key: String,
    val title: String,
    val description: String,
    val type: PmTaskType,
    val status: PmStatus,
    val priority: PmPriority,
    val components: List<String>,
    val labels: List<String>,
    val assigneeId: String? = null,
    val reporterId: String? = null,
    val epicId: String? = null,
    val releaseId: String? = null,
    val parentKey: String? = null,
    val dueDate: LocalDate? = null,
    val position: Double,
    val archived: Boolean,
    val doneAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val subtasks: List<PmTaskSummary> = emptyList(),
    val comments: List<PmComment> = emptyList(),
    val events: List<PmTaskEvent> = emptyList(),
    val commits: List<PmCommit> = emptyList(),
    /** Docs whose text names this task's key. */
    val mentionedIn: List<PmDocHit> = emptyList(),
)

@Serializable
data class CreatePmTaskRequest(
    val title: String,
    val description: String = "",
    val type: PmTaskType = PmTaskType.TASK,
    val status: PmStatus = PmStatus.BACKLOG,
    val priority: PmPriority = PmPriority.MEDIUM,
    val components: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val assigneeId: String? = null,
    val epicId: String? = null,
    val releaseId: String? = null,
    /** "SAD-12": the new task becomes its subtask. */
    val parentKey: String? = null,
    val dueDate: LocalDate? = null,
)

/**
 * A partial change: a field left out stays as it is. [clear] names the optional fields to
 * empty — `assigneeId`, `epicId`, `releaseId`, `parentKey`, `dueDate` — since a missing
 * field and a null one read the same on the wire.
 */
@Serializable
data class UpdatePmTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val type: PmTaskType? = null,
    val status: PmStatus? = null,
    val priority: PmPriority? = null,
    val components: List<String>? = null,
    val labels: List<String>? = null,
    val assigneeId: String? = null,
    val epicId: String? = null,
    val releaseId: String? = null,
    val parentKey: String? = null,
    val dueDate: LocalDate? = null,
    /** Where the card sits in its column; left out, a card moved to another column goes to its bottom. */
    val position: Double? = null,
    val clear: List<String> = emptyList(),
)

@Serializable
data class PmCommentRequest(val body: String)

@Serializable
data class PmEpic(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val archived: Boolean,
    val tasks: Int,
    val done: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class SavePmEpicRequest(val title: String, val description: String = "", val status: String = "open")

@Serializable
data class PmRelease(
    val id: String,
    val name: String,
    val description: String,
    val targetDate: LocalDate? = null,
    val status: String,
    val releasedAt: Instant? = null,
    val archived: Boolean,
    val tasks: Int,
    val done: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class SavePmReleaseRequest(
    val name: String,
    val description: String = "",
    val targetDate: LocalDate? = null,
    val status: String = "planned",
)

/** Everything a form needs to fill its pickers, in one call. */
@Serializable
data class PmMeta(
    val me: PmPerson,
    val people: List<PmPerson>,
    val epics: List<PmEpic>,
    val releases: List<PmRelease>,
    val components: List<String> = PM_COMPONENTS,
    val commits: PmCommitSyncState,
)

@Serializable
data class PmCommitSyncState(
    val enabled: Boolean,
    val repo: String,
    val lastSuccessAt: Instant? = null,
    val lastError: String? = null,
)

// ---------------------------------------------------------------- docs

@Serializable
data class PmDocNode(
    val id: String,
    val parentId: String? = null,
    val title: String,
    val position: Double,
    val version: Int,
    val archived: Boolean,
    val updatedBy: String? = null,
    val updatedAt: Instant,
)

@Serializable
data class PmDoc(
    val id: String,
    val parentId: String? = null,
    val title: String,
    val body: String,
    val position: Double,
    val version: Int,
    val archived: Boolean,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class CreatePmDocRequest(
    val title: String,
    val body: String = "",
    val parentId: String? = null,
)

/**
 * A save. [baseVersion] is the version the editor opened; a newer one on the server is a
 * conflict. [parentId] is where the page sits after the save — left out, at the top level.
 */
@Serializable
data class UpdatePmDocRequest(
    val title: String,
    val body: String,
    val baseVersion: Int,
    val parentId: String? = null,
    val position: Double? = null,
)

@Serializable
data class PmDocVersionInfo(val version: Int, val title: String, val editedBy: String? = null, val createdAt: Instant, val size: Int)

@Serializable
data class PmDocVersion(val version: Int, val title: String, val body: String, val editedBy: String? = null, val createdAt: Instant)

@Serializable
data class PmImageRef(val id: String, val contentType: String, val size: Int)

// ---------------------------------------------------------------- search

@Serializable
data class PmSearchResults(val tasks: List<PmTaskSummary>, val docs: List<PmDocHit>)

@Serializable
data class PmDocHit(val id: String, val title: String, val snippet: String)

// ---------------------------------------------------------------- tokens

@Serializable
data class PmTokenView(
    val id: String,
    val name: String,
    val adminId: String,
    val createdAt: Instant,
    val lastUsedAt: Instant? = null,
    val revoked: Boolean,
)

@Serializable
data class CreatePmTokenRequest(val name: String)

/** The token itself, shown once; only its hash is kept. */
@Serializable
data class PmTokenCreated(val token: String, val view: PmTokenView)
