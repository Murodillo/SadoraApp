package uz.sadora.server.admin.pm

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.contains
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.parseUuidOrNull
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.escapeLike
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.plugins.AdminRole

/** Which tasks a list asks for. Empty means "any". */
data class PmTaskFilter(
    val statuses: Set<PmStatus> = emptySet(),
    val assigneeId: Uuid? = null,
    val epicId: Uuid? = null,
    val releaseId: Uuid? = null,
    val component: String? = null,
    val label: String? = null,
    val type: PmTaskType? = null,
    val priority: PmPriority? = null,
    val text: String? = null,
    val archived: Boolean = false,
    /** True: subtasks too, not only the cards a board shows. */
    val withSubtasks: Boolean = false,
)

/**
 * The team's tasks, epics and releases.
 *
 * A task is known outside by its key, `SAD-<number>`, which commits and docs mention. Its
 * card sits in one board column at a [PmTasks.position]; moving it between columns is a
 * change of status, written to its history with who did it. Nothing is deleted on the way:
 * archiving hides a task and its subtasks, and only the Owner may then delete it for good.
 *
 * Done is the team's to give. A tools/pm token — a Claude session — may take a task as far
 * as review; the last step is taken in the panel by a person.
 */
class PmTaskService(private val audit: AuditService) {

    // ------------------------------------------------------------ tasks

    suspend fun list(filter: PmTaskFilter): List<PmTaskSummary> = dbQuery {
        var condition: Op<Boolean> = if (filter.archived) PmTasks.archivedAt.isNotNull() else PmTasks.archivedAt.isNull()
        // The archive lists subtasks too: one archived on its own has no live parent to be found under.
        if (!filter.withSubtasks && !filter.archived) condition = condition and PmTasks.parentId.isNull()
        if (filter.statuses.isNotEmpty()) condition = condition and (PmTasks.status inList filter.statuses.map { it.key })
        filter.assigneeId?.let { condition = condition and (PmTasks.assigneeId eq it) }
        filter.epicId?.let { condition = condition and (PmTasks.epicId eq it) }
        filter.releaseId?.let { condition = condition and (PmTasks.releaseId eq it) }
        filter.type?.let { condition = condition and (PmTasks.type eq it.key) }
        filter.priority?.let { condition = condition and (PmTasks.priority eq it.key) }
        filter.text?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            val number = keyNumber(text)
            val pattern = "%${text.lowercase().take(80).escapeLike()}%"
            val byText = (PmTasks.title.lowerCase() like pattern) or (PmTasks.description.lowerCase() like pattern)
            condition = condition and (if (number != null) byText or (PmTasks.number eq number) else byText)
        }
        filter.component?.let { condition = condition and PmTasks.components.contains(listOf(it)) }
        filter.label?.let { condition = condition and PmTasks.labels.contains(listOf(it)) }
        val rows = PmTasks.selectAll().where { condition }
            .orderBy(PmTasks.position to SortOrder.ASC, PmTasks.number to SortOrder.ASC)
            .limit(LIST_CAP)
            .toList()
        summaries(rows)
    }

    suspend fun get(key: String): PmTaskDetail = dbQuery { detail(requireRow(key)) }

    suspend fun create(request: CreatePmTaskRequest, actor: AdminPrincipal): PmTaskDetail {
        val title = cleanTitle(request.title)
        val description = cleanDescription(request.description)
        refuseDoneByToken(request.status, actor)
        return dbQuery {
            val parent = request.parentKey?.let { parentFor(it, null) }
            // A subtask belongs where its parent does unless told otherwise.
            val parentRow = parent?.let { PmTasks.selectAll().where { PmTasks.id eq it }.single() }
            val id = Uuid.random()
            val number = nextNumber()
            val at = now().toOffsetDateTime()
            PmTasks.insert {
                it[PmTasks.id] = id
                it[PmTasks.number] = number
                it[PmTasks.title] = title
                it[PmTasks.description] = description
                it[type] = request.type.key
                it[status] = request.status.key
                it[priority] = request.priority.key
                it[components] = cleanComponents(request.components)
                it[labels] = cleanLabels(request.labels)
                it[assigneeId] = request.assigneeId?.let { raw -> requirePerson(raw) }
                it[reporterId] = actor.adminId
                it[epicId] = request.epicId?.let { raw -> requireEpic(raw) } ?: parentRow?.get(PmTasks.epicId)
                it[releaseId] = request.releaseId?.let { raw -> requireRelease(raw) } ?: parentRow?.get(PmTasks.releaseId)
                it[parentId] = parent
                it[dueDate] = request.dueDate
                it[position] = bottomOf(request.status)
                it[doneAt] = if (request.status == PmStatus.DONE) at else null
                it[createdAt] = at
                it[updatedAt] = at
            }
            event(id, actor, KIND_CREATED, null, request.status.key)
            detail(requireRow(keyOf(number)))
        }
    }

    suspend fun update(key: String, request: UpdatePmTaskRequest, actor: AdminPrincipal): PmTaskDetail {
        val unknown = request.clear - CLEARABLE
        if (unknown.isNotEmpty()) throw ValidationException("clear", "Bo'shatib bo'lmaydigan maydon: ${unknown.joinToString()}")
        request.status?.let { refuseDoneByToken(it, actor) }
        val title = request.title?.let(::cleanTitle)
        val description = request.description?.let(::cleanDescription)
        return dbQuery {
            val row = requireRow(key)
            val id = row[PmTasks.id]
            val before = PmStatus.of(row[PmTasks.status])
            val beforeAssignee = row[PmTasks.assigneeId]
            val status = request.status
            val assignee = when {
                "assigneeId" in request.clear -> null
                request.assigneeId != null -> requirePerson(request.assigneeId)
                else -> beforeAssignee
            }
            val parent = when {
                "parentKey" in request.clear -> null
                request.parentKey != null -> parentFor(request.parentKey, id)
                else -> row[PmTasks.parentId]
            }
            val epic = request.epicId?.let(::requireEpic)
            val release = request.releaseId?.let(::requireRelease)
            val moved = status != null && status != before
            val at = now().toOffsetDateTime()
            PmTasks.update({ PmTasks.id eq id }) {
                title?.let { value -> it[PmTasks.title] = value }
                description?.let { value -> it[PmTasks.description] = value }
                request.type?.let { value -> it[type] = value.key }
                request.priority?.let { value -> it[priority] = value.key }
                request.components?.let { value -> it[components] = cleanComponents(value) }
                request.labels?.let { value -> it[labels] = cleanLabels(value) }
                it[assigneeId] = assignee
                it[parentId] = parent
                if ("epicId" in request.clear) it[epicId] = null else epic?.let { value -> it[epicId] = value }
                if ("releaseId" in request.clear) it[releaseId] = null else release?.let { value -> it[releaseId] = value }
                if ("dueDate" in request.clear) it[dueDate] = null else request.dueDate?.let { value -> it[dueDate] = value }
                if (status != null) {
                    it[PmTasks.status] = status.key
                    if (moved) it[doneAt] = if (status == PmStatus.DONE) at else null
                }
                when {
                    request.position != null -> it[position] = request.position
                    moved -> it[position] = bottomOf(status!!)
                }
                it[updatedAt] = at
            }
            if (moved) event(id, actor, KIND_STATUS, before.key, status!!.key)
            if (assignee != beforeAssignee) event(id, actor, KIND_ASSIGNEE, beforeAssignee?.toString(), assignee?.toString())
            detail(requireRow(key))
        }
    }

    /** Hides the task and its subtasks from the board; [restore] brings back the ones archived with it. */
    suspend fun archive(key: String, actor: AdminPrincipal): PmTaskDetail = dbQuery {
        val row = requireRow(key)
        if (row[PmTasks.archivedAt] == null) {
            val at = now().toOffsetDateTime()
            val id = row[PmTasks.id]
            PmTasks.update({ ((PmTasks.id eq id) or (PmTasks.parentId eq id)) and PmTasks.archivedAt.isNull() }) {
                it[archivedAt] = at
                it[updatedAt] = at
            }
            event(id, actor, KIND_ARCHIVED, null, null)
        }
        detail(requireRow(key))
    }

    suspend fun restore(key: String, actor: AdminPrincipal): PmTaskDetail = dbQuery {
        val row = requireRow(key)
        val archivedAt = row[PmTasks.archivedAt]
        if (archivedAt != null) {
            val id = row[PmTasks.id]
            PmTasks.update({ (PmTasks.id eq id) or ((PmTasks.parentId eq id) and (PmTasks.archivedAt eq archivedAt)) }) {
                it[PmTasks.archivedAt] = null
                it[updatedAt] = now().toOffsetDateTime()
            }
            event(id, actor, KIND_RESTORED, null, null)
        }
        detail(requireRow(key))
    }

    /** For good, with its subtasks, comments and history. Owner only, and only once archived. */
    suspend fun delete(key: String, actor: AdminPrincipal, context: RequestContext) {
        refuseForGood(actor)
        val title = dbQuery {
            val row = requireRow(key)
            if (row[PmTasks.archivedAt] == null) throw ConflictException("Avval taskni arxivlang")
            PmTasks.deleteWhere { PmTasks.id eq row[PmTasks.id] }
            row[PmTasks.title]
        }
        audit.recordPm(actor, AuditActions.PM_TASK_DELETED, "pm_task", key, context, mapOf("title" to title.take(120)))
    }

    suspend fun comment(key: String, request: PmCommentRequest, actor: AdminPrincipal): PmComment {
        val body = request.body.trim()
        if (body.isEmpty() || body.length > COMMENT_MAX) throw ValidationException("body", "Izoh 1–$COMMENT_MAX belgi bo'lsin")
        return dbQuery {
            val taskId = requireRow(key)[PmTasks.id]
            val id = Uuid.random()
            val at = now().toOffsetDateTime()
            PmComments.insert {
                it[PmComments.id] = id
                it[PmComments.taskId] = taskId
                it[authorId] = actor.adminId
                it[PmComments.body] = body
                it[createdAt] = at
            }
            PmTasks.update({ PmTasks.id eq taskId }) { it[updatedAt] = at }
            PmComment(id.toString(), actor.adminId.toString(), body, at.toKotlinInstant())
        }
    }

    /** Her own comment, or anyone's for the Owner and Admin. */
    suspend fun deleteComment(key: String, commentId: Uuid, actor: AdminPrincipal) {
        dbQuery {
            val taskId = requireRow(key)[PmTasks.id]
            val comment = PmComments.selectAll().where { (PmComments.id eq commentId) and (PmComments.taskId eq taskId) }.singleOrNull()
                ?: throw NotFoundException("Izoh topilmadi")
            val lead = actor.role in PM_LEAD_ROLES
            if (!lead && comment[PmComments.authorId] != actor.adminId) throw ForbiddenException(message = "Faqat o'z izohingizni o'chira olasiz")
            PmComments.deleteWhere { PmComments.id eq commentId }
        }
    }

    // ------------------------------------------------------------ epics and releases

    suspend fun epics(includeArchived: Boolean = false): List<PmEpic> = dbQuery { epicRows(includeArchived) }

    suspend fun createEpic(request: SavePmEpicRequest, actor: AdminPrincipal): PmEpic {
        val (title, description, status) = cleanEpic(request)
        return dbQuery {
            val id = Uuid.random()
            val at = now().toOffsetDateTime()
            PmEpics.insert {
                it[PmEpics.id] = id
                it[PmEpics.title] = title
                it[PmEpics.description] = description
                it[PmEpics.status] = status
                it[createdBy] = actor.adminId
                it[createdAt] = at
                it[updatedAt] = at
            }
            epicRows(true).first { it.id == id.toString() }
        }
    }

    suspend fun updateEpic(id: Uuid, request: SavePmEpicRequest): PmEpic {
        val (title, description, status) = cleanEpic(request)
        return dbQuery {
            val changed = PmEpics.update({ PmEpics.id eq id }) {
                it[PmEpics.title] = title
                it[PmEpics.description] = description
                it[PmEpics.status] = status
                it[updatedAt] = now().toOffsetDateTime()
            }
            if (changed == 0) throw NotFoundException("Epic topilmadi")
            epicRows(true).first { it.id == id.toString() }
        }
    }

    suspend fun setEpicArchived(id: Uuid, archived: Boolean): PmEpic = dbQuery {
        val changed = PmEpics.update({ PmEpics.id eq id }) {
            it[archivedAt] = if (archived) now().toOffsetDateTime() else null
            it[updatedAt] = now().toOffsetDateTime()
        }
        if (changed == 0) throw NotFoundException("Epic topilmadi")
        epicRows(true).first { it.id == id.toString() }
    }

    suspend fun releases(includeArchived: Boolean = false): List<PmRelease> = dbQuery { releaseRows(includeArchived) }

    suspend fun createRelease(request: SavePmReleaseRequest, actor: AdminPrincipal): PmRelease {
        val clean = cleanRelease(request)
        return dbQuery {
            val id = Uuid.random()
            val at = now().toOffsetDateTime()
            PmReleases.insert {
                it[PmReleases.id] = id
                it[name] = clean.name
                it[description] = clean.description
                it[targetDate] = clean.targetDate
                it[status] = clean.status
                it[releasedAt] = if (clean.status == RELEASED) at else null
                it[createdBy] = actor.adminId
                it[createdAt] = at
                it[updatedAt] = at
            }
            releaseRows(true).first { it.id == id.toString() }
        }
    }

    suspend fun updateRelease(id: Uuid, request: SavePmReleaseRequest): PmRelease {
        val clean = cleanRelease(request)
        return dbQuery {
            val before = PmReleases.selectAll().where { PmReleases.id eq id }.singleOrNull() ?: throw NotFoundException("Reliz topilmadi")
            val at = now().toOffsetDateTime()
            PmReleases.update({ PmReleases.id eq id }) {
                it[name] = clean.name
                it[description] = clean.description
                it[targetDate] = clean.targetDate
                it[status] = clean.status
                it[releasedAt] = when {
                    clean.status != RELEASED -> null
                    before[PmReleases.releasedAt] != null -> before[PmReleases.releasedAt]
                    else -> at
                }
                it[updatedAt] = at
            }
            releaseRows(true).first { it.id == id.toString() }
        }
    }

    suspend fun setReleaseArchived(id: Uuid, archived: Boolean): PmRelease = dbQuery {
        val changed = PmReleases.update({ PmReleases.id eq id }) {
            it[archivedAt] = if (archived) now().toOffsetDateTime() else null
            it[updatedAt] = now().toOffsetDateTime()
        }
        if (changed == 0) throw NotFoundException("Reliz topilmadi")
        releaseRows(true).first { it.id == id.toString() }
    }

    /** Operators who can be given a task: the roles that reach the section, and active. */
    suspend fun people(): List<PmPerson> = dbQuery {
        AdminUsers.selectAll()
            .where { (AdminUsers.status eq "active") and (AdminUsers.role inList PM_ROLES.map { it.name.lowercase() }) }
            .orderBy(AdminUsers.name to SortOrder.ASC)
            .map { it.toPerson() }
    }

    suspend fun person(id: Uuid): PmPerson = dbQuery {
        AdminUsers.selectAll().where { AdminUsers.id eq id }.single().toPerson()
    }

    // ------------------------------------------------------------ inside a transaction

    private fun JdbcTransaction.requireRow(key: String): ResultRow {
        val number = keyNumber(key) ?: throw NotFoundException("Task topilmadi")
        return PmTasks.selectAll().where { PmTasks.number eq number }.singleOrNull() ?: throw NotFoundException("Task topilmadi")
    }

    private fun JdbcTransaction.nextNumber(): Int {
        var number = 0
        exec("SELECT nextval('pm_task_number_seq')") { rows -> if (rows.next()) number = rows.getInt(1) }
        return number
    }

    /** One past the column's last card. */
    private fun bottomOf(status: PmStatus): Double {
        val top = PmTasks.position.max()
        val last = PmTasks.select(top).where { (PmTasks.status eq status.key) and PmTasks.archivedAt.isNull() }.single()[top]
        return (last ?: 0.0) + 1.0
    }

    /** The parent a task may hang under: an existing top-level task that is not itself. */
    private fun parentFor(parentKey: String, self: Uuid?): Uuid {
        val parent = PmTasks.selectAll().where { PmTasks.number eq (keyNumber(parentKey) ?: -1) }.singleOrNull()
            ?: throw ValidationException("parentKey", "Asosiy task topilmadi")
        val id = parent[PmTasks.id]
        if (id == self) throw ValidationException("parentKey", "Task o'ziga subtask bo'la olmaydi")
        if (parent[PmTasks.parentId] != null) throw ValidationException("parentKey", "Subtaskning subtaski bo'lmaydi")
        if (self != null && PmTasks.selectAll().where { PmTasks.parentId eq self }.count() > 0) {
            throw ValidationException("parentKey", "Subtasklari bor task boshqasiga subtask bo'la olmaydi")
        }
        return id
    }

    private fun requirePerson(raw: String): Uuid {
        val id = parseUuidOrNull(raw) ?: throw ValidationException("assigneeId", "Ijrochi topilmadi")
        val row = AdminUsers.selectAll().where { AdminUsers.id eq id }.singleOrNull()
        val role = row?.let { r -> AdminRole.entries.firstOrNull { it.name.equals(r[AdminUsers.role], true) } }
        if (row == null || role !in PM_ROLES || row[AdminUsers.status] != "active") {
            throw ValidationException("assigneeId", "Ijrochi topilmadi")
        }
        return id
    }

    private fun requireEpic(raw: String): Uuid {
        val id = parseUuidOrNull(raw) ?: throw ValidationException("epicId", "Epic topilmadi")
        if (PmEpics.selectAll().where { PmEpics.id eq id }.empty()) throw ValidationException("epicId", "Epic topilmadi")
        return id
    }

    private fun requireRelease(raw: String): Uuid {
        val id = parseUuidOrNull(raw) ?: throw ValidationException("releaseId", "Reliz topilmadi")
        if (PmReleases.selectAll().where { PmReleases.id eq id }.empty()) throw ValidationException("releaseId", "Reliz topilmadi")
        return id
    }

    private fun event(taskId: Uuid, actor: AdminPrincipal, kind: String, from: String?, to: String?) {
        PmTaskEvents.insert {
            it[id] = Uuid.random()
            it[PmTaskEvents.taskId] = taskId
            it[actorId] = actor.adminId
            it[PmTaskEvents.kind] = kind
            it[fromValue] = from
            it[toValue] = to
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    private fun summaries(rows: List<ResultRow>): List<PmTaskSummary> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[PmTasks.id] }
        val children = PmTasks.select(PmTasks.parentId, PmTasks.status)
            .where { (PmTasks.parentId inList ids) and PmTasks.archivedAt.isNull() }
            .groupBy({ it[PmTasks.parentId]!! }, { it[PmTasks.status] })
        val commentCount = PmComments.taskId.count()
        val comments = PmComments.select(PmComments.taskId, commentCount)
            .where { PmComments.taskId inList ids }
            .groupBy(PmComments.taskId)
            .associate { it[PmComments.taskId] to it[commentCount].toInt() }
        val parentIds = rows.mapNotNull { it[PmTasks.parentId] }.distinct()
        val parentKeys = if (parentIds.isEmpty()) emptyMap() else PmTasks.select(PmTasks.id, PmTasks.number)
            .where { PmTasks.id inList parentIds }
            .associate { it[PmTasks.id] to keyOf(it[PmTasks.number]) }
        return rows.map { row ->
            val id = row[PmTasks.id]
            val kids = children[id].orEmpty()
            PmTaskSummary(
                id = id.toString(),
                key = keyOf(row[PmTasks.number]),
                title = row[PmTasks.title],
                type = PmTaskType.of(row[PmTasks.type]),
                status = PmStatus.of(row[PmTasks.status]),
                priority = PmPriority.of(row[PmTasks.priority]),
                components = row[PmTasks.components],
                labels = row[PmTasks.labels],
                assigneeId = row[PmTasks.assigneeId]?.toString(),
                epicId = row[PmTasks.epicId]?.toString(),
                releaseId = row[PmTasks.releaseId]?.toString(),
                parentKey = row[PmTasks.parentId]?.let(parentKeys::get),
                dueDate = row[PmTasks.dueDate],
                position = row[PmTasks.position],
                archived = row[PmTasks.archivedAt] != null,
                subtasks = kids.size,
                subtasksDone = kids.count { it == PmStatus.DONE.key },
                comments = comments[id] ?: 0,
                createdAt = row[PmTasks.createdAt].toKotlinInstant(),
                updatedAt = row[PmTasks.updatedAt].toKotlinInstant(),
            )
        }
    }

    private fun detail(row: ResultRow): PmTaskDetail {
        val id = row[PmTasks.id]
        val summary = summaries(listOf(row)).single()
        val subtasks = summaries(
            PmTasks.selectAll().where { (PmTasks.parentId eq id) and PmTasks.archivedAt.isNull() }
                .orderBy(PmTasks.number to SortOrder.ASC).toList(),
        )
        val comments = PmComments.selectAll().where { PmComments.taskId eq id }
            .orderBy(PmComments.createdAt to SortOrder.ASC)
            .map { PmComment(it[PmComments.id].toString(), it[PmComments.authorId]?.toString(), it[PmComments.body], it[PmComments.createdAt].toKotlinInstant()) }
        val events = PmTaskEvents.selectAll().where { PmTaskEvents.taskId eq id }
            .orderBy(PmTaskEvents.createdAt to SortOrder.ASC)
            .map {
                PmTaskEvent(it[PmTaskEvents.kind], it[PmTaskEvents.actorId]?.toString(), it[PmTaskEvents.fromValue], it[PmTaskEvents.toValue], it[PmTaskEvents.createdAt].toKotlinInstant())
            }
        val commits = PmTaskCommits.selectAll().where { PmTaskCommits.taskId eq id }
            .orderBy(PmTaskCommits.committedAt to SortOrder.DESC)
            .map {
                PmCommit(it[PmTaskCommits.sha], it[PmTaskCommits.branch], it[PmTaskCommits.message], it[PmTaskCommits.author], it[PmTaskCommits.url], it[PmTaskCommits.committedAt].toKotlinInstant())
            }
        return PmTaskDetail(
            id = summary.id,
            key = summary.key,
            title = summary.title,
            description = row[PmTasks.description],
            type = summary.type,
            status = summary.status,
            priority = summary.priority,
            components = summary.components,
            labels = summary.labels,
            assigneeId = summary.assigneeId,
            reporterId = row[PmTasks.reporterId]?.toString(),
            epicId = summary.epicId,
            releaseId = summary.releaseId,
            parentKey = summary.parentKey,
            dueDate = summary.dueDate,
            position = summary.position,
            archived = summary.archived,
            doneAt = row[PmTasks.doneAt]?.toKotlinInstant(),
            createdAt = summary.createdAt,
            updatedAt = summary.updatedAt,
            subtasks = subtasks,
            comments = comments,
            events = events,
            commits = commits,
        )
    }

    private fun epicRows(includeArchived: Boolean): List<PmEpic> {
        val counts = progress(PmTasks.epicId)
        return PmEpics.selectAll()
            .apply { if (!includeArchived) where { PmEpics.archivedAt.isNull() } }
            .orderBy(PmEpics.createdAt to SortOrder.ASC)
            .map { row ->
                val id = row[PmEpics.id]
                val (tasks, done) = counts[id] ?: (0 to 0)
                PmEpic(
                    id = id.toString(),
                    title = row[PmEpics.title],
                    description = row[PmEpics.description],
                    status = row[PmEpics.status],
                    archived = row[PmEpics.archivedAt] != null,
                    tasks = tasks,
                    done = done,
                    createdAt = row[PmEpics.createdAt].toKotlinInstant(),
                    updatedAt = row[PmEpics.updatedAt].toKotlinInstant(),
                )
            }
    }

    private fun releaseRows(includeArchived: Boolean): List<PmRelease> {
        val counts = progress(PmTasks.releaseId)
        return PmReleases.selectAll()
            .apply { if (!includeArchived) where { PmReleases.archivedAt.isNull() } }
            .orderBy(PmReleases.targetDate to SortOrder.ASC_NULLS_LAST, PmReleases.createdAt to SortOrder.ASC)
            .map { row ->
                val id = row[PmReleases.id]
                val (tasks, done) = counts[id] ?: (0 to 0)
                PmRelease(
                    id = id.toString(),
                    name = row[PmReleases.name],
                    description = row[PmReleases.description],
                    targetDate = row[PmReleases.targetDate],
                    status = row[PmReleases.status],
                    releasedAt = row[PmReleases.releasedAt]?.toKotlinInstant(),
                    archived = row[PmReleases.archivedAt] != null,
                    tasks = tasks,
                    done = done,
                    createdAt = row[PmReleases.createdAt].toKotlinInstant(),
                    updatedAt = row[PmReleases.updatedAt].toKotlinInstant(),
                )
            }
    }

    /** Live tasks per epic or release, and how many of them are done. */
    private fun progress(column: org.jetbrains.exposed.v1.core.Column<Uuid?>): Map<Uuid, Pair<Int, Int>> =
        PmTasks.select(column, PmTasks.status)
            .where { column.isNotNull() and PmTasks.archivedAt.isNull() }
            .groupBy({ it[column]!! }, { it[PmTasks.status] })
            .mapValues { (_, statuses) -> statuses.size to statuses.count { it == PmStatus.DONE.key } }

    private fun ResultRow.toPerson() = PmPerson(
        id = this[AdminUsers.id].toString(),
        name = this[AdminUsers.name],
        email = this[AdminUsers.email],
        role = AdminRole.entries.firstOrNull { it.name.equals(this[AdminUsers.role], true) } ?: AdminRole.ANALYST,
    )

    internal companion object {
        const val LIST_CAP = 2000
        const val TITLE_MAX = 200
        const val DESCRIPTION_MAX = 100_000
        const val COMMENT_MAX = 20_000
        const val LABEL_MAX = 40
        const val RELEASED = "released"
        const val KIND_CREATED = "created"
        const val KIND_STATUS = "status"
        const val KIND_ASSIGNEE = "assignee"
        const val KIND_ARCHIVED = "archived"
        const val KIND_RESTORED = "restored"
        private val CLEARABLE = setOf("assigneeId", "epicId", "releaseId", "parentKey", "dueDate")
        private val KEY = Regex("^(?:SAD-)?(\\d{1,9})$", RegexOption.IGNORE_CASE)

        fun keyOf(number: Int): String = "SAD-$number"

        /** "SAD-12", "sad-12" or "12" → 12; anything else is not a key. */
        fun keyNumber(text: String): Int? = KEY.matchEntire(text.trim())?.groupValues?.get(1)?.toIntOrNull()

        fun cleanTitle(raw: String): String {
            val title = raw.trim()
            if (title.isEmpty() || title.length > TITLE_MAX) throw ValidationException("title", "Sarlavha 1–$TITLE_MAX belgi bo'lsin")
            return title
        }

        fun cleanDescription(raw: String): String {
            if (raw.length > DESCRIPTION_MAX) throw ValidationException("description", "Tavsif juda uzun")
            return raw.trimEnd()
        }

        fun cleanComponents(raw: List<String>): List<String> {
            val unknown = raw.filter { it !in PM_COMPONENTS }
            if (unknown.isNotEmpty()) throw ValidationException("components", "Noma'lum komponent: ${unknown.joinToString()}")
            return PM_COMPONENTS.filter { it in raw }
        }

        /** Lower-case, no spaces inside, each once, in the order given. */
        fun cleanLabels(raw: List<String>): List<String> {
            val labels = raw.map { it.trim().lowercase().replace(Regex("\\s+"), "-") }.filter { it.isNotEmpty() }.distinct()
            if (labels.size > 12) throw ValidationException("labels", "Eng ko'pi 12 ta label")
            if (labels.any { it.length > LABEL_MAX }) throw ValidationException("labels", "Label eng ko'pi $LABEL_MAX belgi")
            return labels
        }

        fun refuseDoneByToken(status: PmStatus, actor: AdminPrincipal) {
            if (status == PmStatus.DONE && actor.viaToken) {
                throw ForbiddenException(message = "«Done» holatiga faqat panelda o'tkaziladi. Tokendan «review» gacha.")
            }
        }

        fun cleanEpic(request: SavePmEpicRequest): Triple<String, String, String> {
            val title = request.title.trim()
            if (title.isEmpty() || title.length > 160) throw ValidationException("title", "Nomi 1–160 belgi bo'lsin")
            if (request.status !in setOf("open", "done")) throw ValidationException("status", "Holat noto'g'ri")
            if (request.description.length > DESCRIPTION_MAX) throw ValidationException("description", "Tavsif juda uzun")
            return Triple(title, request.description.trimEnd(), request.status)
        }

        fun cleanRelease(request: SavePmReleaseRequest): SavePmReleaseRequest {
            val name = request.name.trim()
            if (name.isEmpty() || name.length > 120) throw ValidationException("name", "Nomi 1–120 belgi bo'lsin")
            if (request.status !in setOf("planned", RELEASED)) throw ValidationException("status", "Holat noto'g'ri")
            if (request.description.length > DESCRIPTION_MAX) throw ValidationException("description", "Tavsif juda uzun")
            return request.copy(name = name, description = request.description.trimEnd())
        }
    }
}
