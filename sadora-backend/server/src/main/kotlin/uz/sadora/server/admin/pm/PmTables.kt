package uz.sadora.server.admin.pm

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

private val pmJson = Json { ignoreUnknownKeys = true }
private val stringList = ListSerializer(String.serializer())

object PmEpics : Table("pm_epics") {
    val id = uuid("id")
    val title = text("title")
    val description = text("description")
    val status = text("status")
    val archivedAt = timestampWithTimeZone("archived_at").nullable()
    val createdBy = uuid("created_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PmReleases : Table("pm_releases") {
    val id = uuid("id")
    val name = text("name")
    val description = text("description")
    val targetDate = date("target_date").nullable()
    val status = text("status")
    val releasedAt = timestampWithTimeZone("released_at").nullable()
    val archivedAt = timestampWithTimeZone("archived_at").nullable()
    val createdBy = uuid("created_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PmTasks : Table("pm_tasks") {
    val id = uuid("id")
    val number = integer("number")
    val title = text("title")
    val description = text("description")
    val type = text("type")
    val status = text("status")
    val priority = text("priority")
    val components = jsonb("components", pmJson, stringList)
    val labels = jsonb("labels", pmJson, stringList)
    val assigneeId = uuid("assignee_id").nullable()
    val reporterId = uuid("reporter_id").nullable()
    val epicId = uuid("epic_id").nullable()
    val releaseId = uuid("release_id").nullable()
    val parentId = uuid("parent_id").nullable()
    val dueDate = date("due_date").nullable()
    val position = double("position")
    val archivedAt = timestampWithTimeZone("archived_at").nullable()
    val doneAt = timestampWithTimeZone("done_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PmComments : Table("pm_comments") {
    val id = uuid("id")
    val taskId = uuid("task_id")
    val authorId = uuid("author_id").nullable()
    val body = text("body")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PmTaskEvents : Table("pm_task_events") {
    val id = uuid("id")
    val taskId = uuid("task_id")
    val actorId = uuid("actor_id").nullable()
    val kind = text("kind")
    val fromValue = text("from_value").nullable()
    val toValue = text("to_value").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PmTaskCommits : Table("pm_task_commits") {
    val taskId = uuid("task_id")
    val sha = text("sha")
    val branch = text("branch")
    val message = text("message")
    val author = text("author")
    val url = text("url")
    val committedAt = timestampWithTimeZone("committed_at")

    override val primaryKey = PrimaryKey(taskId, sha)
}

object PmDocs : Table("pm_docs") {
    val id = uuid("id")
    val parentId = uuid("parent_id").nullable()
    val title = text("title")
    val body = text("body")
    val position = double("position")
    val version = integer("version")
    val archivedAt = timestampWithTimeZone("archived_at").nullable()
    val createdBy = uuid("created_by").nullable()
    val updatedBy = uuid("updated_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PmDocVersions : Table("pm_doc_versions") {
    val docId = uuid("doc_id")
    val version = integer("version")
    val title = text("title")
    val body = text("body")
    val editedBy = uuid("edited_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(docId, version)
}

object PmImages : Table("pm_images") {
    val id = uuid("id")
    val contentType = text("content_type")
    val sizeBytes = integer("size_bytes")
    val uploadedBy = uuid("uploaded_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object PmTokens : Table("pm_tokens") {
    val id = uuid("id")
    val adminId = uuid("admin_id")
    val name = text("name")
    val tokenHash = text("token_hash")
    val createdAt = timestampWithTimeZone("created_at")
    val lastUsedAt = timestampWithTimeZone("last_used_at").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
