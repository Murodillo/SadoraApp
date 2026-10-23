package uz.sadora.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import uz.sadora.server.admin.AdminSession
import uz.sadora.server.admin.AdminSignInRequest
import uz.sadora.contract.Ack
import uz.sadora.server.admin.pm.CreatePmDocRequest
import uz.sadora.server.admin.pm.GitHubCommit
import uz.sadora.server.admin.pm.PmCommitSync
import uz.sadora.server.config.PmConfig
import io.ktor.client.engine.mock.respond
import uz.sadora.server.admin.pm.CreatePmTaskRequest
import uz.sadora.server.admin.pm.PmDoc
import uz.sadora.server.admin.pm.PmDocNode
import uz.sadora.server.admin.pm.PmDocVersion
import uz.sadora.server.admin.pm.PmDocVersionInfo
import uz.sadora.server.admin.pm.PmImageRef
import uz.sadora.server.admin.pm.PmSearchResults
import uz.sadora.server.admin.pm.UpdatePmDocRequest
import uz.sadora.server.admin.pm.PmComment
import uz.sadora.server.admin.pm.PmCommentRequest
import uz.sadora.server.admin.pm.PmEpic
import uz.sadora.server.admin.pm.PmTaskSummary
import uz.sadora.server.admin.pm.PmTokenCreated
import uz.sadora.server.admin.pm.PmTokenView
import uz.sadora.server.admin.pm.CreatePmTokenRequest
import uz.sadora.server.admin.pm.SavePmEpicRequest
import uz.sadora.server.admin.pm.UpdatePmTaskRequest
import uz.sadora.server.admin.pm.PmPriority
import uz.sadora.server.admin.pm.PmStatus
import uz.sadora.server.admin.pm.PmTaskDetail
import uz.sadora.server.admin.pm.PmTaskType
import uz.sadora.server.auth.PasswordHasher
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AdminUsers
import uz.sadora.server.db.dbQuery

/**
 * The team's "Loyiha" section through its HTTP API: who may reach it, what a task and a
 * doc keep, and the long-lived tokens tools/pm signs in with. Runs only with TEST_DB_URL,
 * like [ApiIntegrationTest].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PmIntegrationTest {

    private val databaseUrl: String? = System.getenv("TEST_DB_URL")?.takeIf { it.isNotBlank() }
    private lateinit var component: AppComponent

    @BeforeAll
    fun boot() {
        assumeTrue(databaseUrl != null, "TEST_DB_URL not set — integration test skipped")
        component = AppComponent(integrationTestConfig(databaseUrl!!))
    }

    @AfterAll
    fun shutDown() {
        if (::component.isInitialized) {
            component.close()
            java.io.File(component.config.mediaDir).deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- roles

    @Test
    fun `a developer works on tasks and reaches nothing about the app's users`() = api {
        val developer = staff("developer")
        val created = post<PmTaskDetail>(
            "/v1/admin/pm/tasks",
            developer,
            CreatePmTaskRequest(title = "Kanban doskasi", type = PmTaskType.FEATURE, priority = PmPriority.HIGH),
        )
        assertTrue(Regex("SAD-\\d+").matches(created.key), created.key)
        assertEquals(PmStatus.BACKLOG, created.status)
        assertEquals(created.key, get<PmTaskDetail>("/v1/admin/pm/tasks/${created.key}", developer).key)

        for (path in listOf("/v1/admin/stats", "/v1/admin/users", "/v1/admin/operators", "/v1/admin/audit")) {
            assertEquals(HttpStatusCode.Forbidden, client.get(path) { bearerAuth(developer) }.status, path)
        }
        // Support and Analyst never see the team's work.
        assertEquals(HttpStatusCode.Forbidden, client.get("/v1/admin/pm/tasks") { bearerAuth(staff("support")) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/v1/admin/pm/tasks") { bearerAuth(staff("analyst")) }.status)
    }

    // ---------------------------------------------------------------- tasks

    @Test
    fun `a card moves across the board, keeps its history, and its epic counts it done`() = api {
        val developer = staff("developer")
        val epic = post<PmEpic>("/v1/admin/pm/epics", developer, SavePmEpicRequest("Baby tracker ${Uuid.random()}"))
        val task = post<PmTaskDetail>(
            "/v1/admin/pm/tasks",
            developer,
            CreatePmTaskRequest(title = "Uyqu jurnali", components = listOf("backend", "android"), labels = listOf("Tez Yechim"), epicId = epic.id),
        )
        assertEquals(listOf("android", "backend"), task.components, "kept in the fixed order")
        assertEquals(listOf("tez-yechim"), task.labels)

        val moved = patch<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer, UpdatePmTaskRequest(status = PmStatus.IN_PROGRESS))
        assertEquals(PmStatus.IN_PROGRESS, moved.status)
        assertEquals(listOf("created", "status"), moved.events.map { it.kind })
        assertEquals("backlog" to "in_progress", moved.events.last().from to moved.events.last().to)

        val sub = post<PmTaskDetail>("/v1/admin/pm/tasks", developer, CreatePmTaskRequest(title = "Migratsiya", parentKey = task.key))
        assertEquals(task.key, sub.parentKey)
        val grandchild = client.post("/v1/admin/pm/tasks") { bearerAuth(developer); json(CreatePmTaskRequest(title = "Chuqur", parentKey = sub.key)) }
        assertEquals(HttpStatusCode.BadRequest, grandchild.status, "one level of subtasks only")

        patch<PmTaskDetail>("/v1/admin/pm/tasks/${sub.key}", developer, UpdatePmTaskRequest(status = PmStatus.DONE))
        val withSub = get<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer)
        assertEquals(listOf(sub.key), withSub.subtasks.map { it.key })
        // The board lists top-level cards; the subtask shows as progress on its parent.
        val board = get<List<PmTaskSummary>>("/v1/admin/pm/tasks?epic=${epic.id}", developer)
        assertEquals(listOf(task.key), board.map { it.key })
        assertEquals(1 to 1, board.single().subtasks to board.single().subtasksDone)

        patch<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer, UpdatePmTaskRequest(status = PmStatus.DONE))
        val counted = get<List<PmEpic>>("/v1/admin/pm/epics", developer).single { it.id == epic.id }
        assertEquals(2 to 2, counted.tasks to counted.done)

        val cleared = patch<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer, UpdatePmTaskRequest(clear = listOf("epicId")))
        assertEquals(null, cleared.epicId)
        assertEquals(listOf("android", "backend"), cleared.components, "a partial change leaves the rest alone")
    }

    @Test
    fun `archiving hides a task with its subtasks, and only the Owner deletes it for good`() = api {
        val developer = staff("developer")
        val owner = staff("owner")
        val label = "arx-${Uuid.random().toString().take(8)}"
        val task = post<PmTaskDetail>("/v1/admin/pm/tasks", developer, CreatePmTaskRequest(title = "Eski reja", labels = listOf(label)))
        val sub = post<PmTaskDetail>("/v1/admin/pm/tasks", developer, CreatePmTaskRequest(title = "Eski qism", parentKey = task.key))

        post<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}/archive", developer, Ack())
        assertTrue(get<List<PmTaskSummary>>("/v1/admin/pm/tasks?label=$label", developer).isEmpty())
        assertEquals(listOf(task.key), get<List<PmTaskSummary>>("/v1/admin/pm/tasks?label=$label&archived=true", developer).map { it.key })
        assertTrue(get<PmTaskDetail>("/v1/admin/pm/tasks/${sub.key}", developer).archived)

        assertEquals(HttpStatusCode.Forbidden, delete("/v1/admin/pm/tasks/${task.key}", developer).status)
        post<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}/restore", developer, Ack())
        assertTrue(!get<PmTaskDetail>("/v1/admin/pm/tasks/${sub.key}", developer).archived, "restored with its parent")
        assertEquals(HttpStatusCode.Conflict, delete("/v1/admin/pm/tasks/${task.key}", owner).status, "archive first")

        post<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}/archive", owner, Ack())
        assertEquals(HttpStatusCode.OK, delete("/v1/admin/pm/tasks/${task.key}", owner).status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/admin/pm/tasks/${sub.key}") { bearerAuth(owner) }.status)
    }

    @Test
    fun `comments belong to their author, and the lead may remove any`() = api {
        val first = staff("developer")
        val second = staff("developer")
        val admin = staff("admin")
        val task = post<PmTaskDetail>("/v1/admin/pm/tasks", first, CreatePmTaskRequest(title = "Izohlar"))
        val comment = post<PmComment>("/v1/admin/pm/tasks/${task.key}/comments", first, PmCommentRequest("Ko'rib chiqing"))
        assertEquals(HttpStatusCode.Forbidden, delete("/v1/admin/pm/tasks/${task.key}/comments/${comment.id}", second).status)
        assertEquals(HttpStatusCode.OK, delete("/v1/admin/pm/tasks/${task.key}/comments/${comment.id}", admin).status)
        assertTrue(get<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", first).comments.isEmpty())
    }

    @Test
    fun `a commit naming a task shows on it once, with the branch it was first seen on`() = api {
        val developer = staff("developer")
        val task = post<PmTaskDetail>("/v1/admin/pm/tasks", developer, CreatePmTaskRequest(title = "Commit bilan"))
        val commit = GitHubCommit(
            sha = "c${Uuid.random().toString().replace("-", "")}",
            url = "https://github.com/Murodillo/SadoraApp/commit/x",
            message = "Add the board ${task.key}",
            author = "Murodillo",
            committedAt = kotlin.time.Instant.parse("2026-10-23T10:00:00Z"),
        )
        component.pmCommitSync.ingest("dev", listOf(commit))
        component.pmCommitSync.ingest("main", listOf(commit))
        val shown = get<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer).commits
        assertEquals(listOf(commit.sha to "dev"), shown.map { it.sha to it.branch })
    }

    /**
     * GitHub dates a commit when it was made, not when it was pushed; dev often waits days
     * before a push. Such a commit still reaches its task, as does one that names a task
     * created after it.
     */
    @Test
    fun `a commit pushed days after it was made is still linked`() = api {
        val developer = staff("developer")
        val task = post<PmTaskDetail>("/v1/admin/pm/tasks", developer, CreatePmTaskRequest(title = "Kech push"))
        fun wire(sha: String, message: String, date: String) =
            """{"sha":"$sha","html_url":"https://github.com/x/commit/$sha","commit":{"message":"$message","author":{"name":"M","date":"$date"}}}"""
        val suffix = Uuid.random().toString().take(8)
        var listed = listOf(wire("a$suffix", "Old work ${task.key}", "2020-01-02T00:00:00Z"))
        // GitHub's own filter: `since` drops commits dated before it.
        val engine = io.ktor.client.engine.mock.MockEngine { request ->
            val since = request.url.parameters["since"]?.let(kotlin.time.Instant::parse)
            val visible = listed.filter { since == null || kotlin.time.Instant.parse(Regex("\"date\":\"([^\"]+)").find(it)!!.groupValues[1]) >= since }
            respond(
                content = visible.joinToString(",", "[", "]"),
                headers = io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"),
            )
        }
        val sync = PmCommitSync(PmConfig(branches = listOf("test-$suffix"), commitPoll = true), HttpClient(engine))
        sync.pass()
        listed = listOf(wire("b$suffix", "Older still ${task.key}", "2019-05-05T00:00:00Z")) + listed
        sync.pass()
        val shown = get<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer).commits.map { it.sha }.toSet()
        assertEquals(setOf("a$suffix", "b$suffix"), shown)
    }

    // ---------------------------------------------------------------- docs

    /**
     * A save names the version it started from. Two editors who opened the same version
     * cannot overwrite each other: the second is told, and every version stays readable.
     */
    @Test
    fun `a doc keeps every version and refuses a save made on top of an older one`() = api {
        val developer = staff("developer")
        val claude = staff("developer")
        val parent = post<PmDoc>("/v1/admin/pm/docs", developer, CreatePmDocRequest(title = "Speclar"))
        val doc = post<PmDoc>("/v1/admin/pm/docs", developer, CreatePmDocRequest(title = "Chat media", body = "# Birinchi", parentId = parent.id))
        assertEquals(1, doc.version)

        val second = put<PmDoc>("/v1/admin/pm/docs/${doc.id}", claude, UpdatePmDocRequest("Chat media", "# Ikkinchi", baseVersion = 1, parentId = parent.id))
        assertEquals(2, second.version)
        val stale = client.put("/v1/admin/pm/docs/${doc.id}") { bearerAuth(developer); json(UpdatePmDocRequest("Chat media", "# Eski", baseVersion = 1, parentId = parent.id)) }
        assertEquals(HttpStatusCode.Conflict, stale.status)

        val versions = get<List<PmDocVersionInfo>>("/v1/admin/pm/docs/${doc.id}/versions", developer)
        assertEquals(listOf(2, 1), versions.map { it.version })
        assertEquals("# Birinchi", get<PmDocVersion>("/v1/admin/pm/docs/${doc.id}/versions/1", developer).body)

        val tree = get<List<PmDocNode>>("/v1/admin/pm/docs", developer)
        assertEquals(parent.id, tree.single { it.id == doc.id }.parentId)
        // A doc cannot be moved under its own child.
        val loop = client.put("/v1/admin/pm/docs/${parent.id}") { bearerAuth(developer); json(UpdatePmDocRequest("Speclar", "", baseVersion = 1, parentId = doc.id)) }
        assertEquals(HttpStatusCode.BadRequest, loop.status)

        post<PmDoc>("/v1/admin/pm/docs/${parent.id}/archive", developer, Ack())
        assertTrue(get<List<PmDocNode>>("/v1/admin/pm/docs", developer).none { it.id == doc.id }, "archived with its parent")
        assertEquals(HttpStatusCode.Forbidden, delete("/v1/admin/pm/docs/${parent.id}", developer).status)
        assertEquals(HttpStatusCode.OK, delete("/v1/admin/pm/docs/${parent.id}", staff("owner")).status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/admin/pm/docs/${doc.id}") { bearerAuth(developer) }.status)
    }

    @Test
    fun `a pasted picture is kept as what its bytes say and only the section can open it`() = api {
        val developer = staff("developer")
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
        val uploaded = client.post("/v1/admin/pm/images") { bearerAuth(developer); setBody(png) }
        assertEquals(HttpStatusCode.OK, uploaded.status, uploaded.text())
        val image = uploaded.body<PmImageRef>()
        assertEquals("image/png", image.contentType)

        val fetched = client.get("/v1/admin/pm/images/${image.id}") { bearerAuth(developer) }
        assertEquals(HttpStatusCode.OK, fetched.status)
        assertTrue(png.contentEquals(fetched.body<ByteArray>()))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/admin/pm/images/${image.id}").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/v1/admin/pm/images/${image.id}") { bearerAuth(staff("support")) }.status)

        val script = client.post("/v1/admin/pm/images") { bearerAuth(developer); setBody("<svg onload=alert(1)>".toByteArray()) }
        assertEquals(HttpStatusCode.BadRequest, script.status, "only PNG, JPEG, WebP and GIF")
    }

    @Test
    fun `search finds a task by key or words and a doc by its text`() = api {
        val developer = staff("developer")
        val word = "qidiruv${Uuid.random().toString().take(6)}"
        val task = post<PmTaskDetail>("/v1/admin/pm/tasks", developer, CreatePmTaskRequest(title = "Topiladigan $word"))
        val doc = post<PmDoc>("/v1/admin/pm/docs", developer, CreatePmDocRequest(title = "Eslatma", body = "Ichida $word bor"))

        val byWord = get<PmSearchResults>("/v1/admin/pm/search?q=$word", developer)
        assertEquals(listOf(task.key), byWord.tasks.map { it.key })
        assertEquals(listOf(doc.id), byWord.docs.map { it.id })
        assertTrue(word in byWord.docs.single().snippet)
        assertEquals(listOf(task.key), get<PmSearchResults>("/v1/admin/pm/search?q=${task.key.lowercase()}", developer).tasks.map { it.key })

        // A doc that names the task shows on the task's page; a longer key that starts the same does not count.
        val naming = post<PmDoc>("/v1/admin/pm/docs", developer, CreatePmDocRequest(title = "Reja", body = "Bu ${task.key} uchun, ${task.key}0 emas"))
        val other = post<PmDoc>("/v1/admin/pm/docs", developer, CreatePmDocRequest(title = "Boshqa", body = "Faqat ${task.key}9 haqida"))
        val mentions = get<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer).mentionedIn.map { it.id }
        assertTrue(naming.id in mentions)
        assertTrue(other.id !in mentions)
    }

    // ---------------------------------------------------------------- tokens

    /**
     * tools/pm signs in with a long-lived token. It opens the section and nothing else, it
     * may take a task to review but not to done, and it dies the moment it is revoked.
     */
    @Test
    fun `a token opens only the section, stops short of done, and dies when revoked`() = api {
        val developer = staff("developer")
        val created = post<PmTokenCreated>("/v1/admin/pm/tokens", developer, CreatePmTokenRequest("MacBook Claude"))
        val token = created.token
        assertTrue(token.startsWith("sadpm_"), token)

        val task = post<PmTaskDetail>("/v1/admin/pm/tasks", token, CreatePmTaskRequest(title = "Tokendan"))
        patch<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", token, UpdatePmTaskRequest(status = PmStatus.REVIEW))
        val done = client.patch("/v1/admin/pm/tasks/${task.key}") { bearerAuth(token); json(UpdatePmTaskRequest(status = PmStatus.DONE)) }
        assertEquals(HttpStatusCode.Forbidden, done.status)
        patch<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", developer, UpdatePmTaskRequest(status = PmStatus.DONE))

        // Nothing for good from a token, even the Owner's: that step is a person's.
        val ownerToken = post<PmTokenCreated>("/v1/admin/pm/tokens", staff("owner"), CreatePmTokenRequest("Owner CLI")).token
        post<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}/archive", ownerToken, Ack())
        assertEquals(HttpStatusCode.Forbidden, delete("/v1/admin/pm/tasks/${task.key}", ownerToken).status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/admin/me") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/v1/admin/pm/tokens") { bearerAuth(token) }.status, "a token cannot mint tokens")
        assertTrue(get<List<PmTokenView>>("/v1/admin/pm/tokens", developer).single { it.id == created.view.id }.lastUsedAt != null)

        // Someone else's token is not hers to see; the Owner sees and revokes it.
        val other = staff("developer")
        assertTrue(get<List<PmTokenView>>("/v1/admin/pm/tokens", other).none { it.id == created.view.id })
        assertEquals(HttpStatusCode.NotFound, delete("/v1/admin/pm/tokens/${created.view.id}", other).status)
        assertEquals(HttpStatusCode.OK, delete("/v1/admin/pm/tokens/${created.view.id}", staff("owner")).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/admin/pm/tasks") { bearerAuth(token) }.status)
    }

    // ---------------------------------------------------------------- backup

    /**
     * The Owner takes the whole section away as one file and brings it back into an empty
     * database — a fresh one here — with keys, history, versions and pictures intact.
     */
    @Test
    fun `a backup comes back whole into an empty database`() = api {
        val owner = staff("owner")
        val developer = staff("developer")
        assertEquals(HttpStatusCode.Forbidden, client.get("/v1/admin/pm/backup") { bearerAuth(developer) }.status)

        val task = post<PmTaskDetail>("/v1/admin/pm/tasks", developer, CreatePmTaskRequest(title = "Zaxiradagi task"))
        post<PmComment>("/v1/admin/pm/tasks/${task.key}/comments", developer, PmCommentRequest("izoh"))
        val doc = post<PmDoc>("/v1/admin/pm/docs", developer, CreatePmDocRequest(title = "Zaxiradagi hujjat", body = "v1"))
        put<PmDoc>("/v1/admin/pm/docs/${doc.id}", developer, UpdatePmDocRequest("Zaxiradagi hujjat", "v2", baseVersion = 1))
        val gif = "GIF89a-zaxira".toByteArray()
        val image = client.post("/v1/admin/pm/images") { bearerAuth(developer); setBody(gif) }.body<PmImageRef>()

        val backup = client.get("/v1/admin/pm/backup") { bearerAuth(owner) }
        assertEquals(HttpStatusCode.OK, backup.status)
        val snapshot = backup.text()

        // This database already holds work, so nothing is restored over it.
        val refused = client.post("/v1/admin/pm/restore") { bearerAuth(owner); setBody(TextContent(snapshot, ContentType.Application.Json)) }
        assertEquals(HttpStatusCode.Conflict, refused.status)

        val fresh = freshDatabase()
        try {
            val other = AppComponent(integrationTestConfig(fresh.url))
            try {
                testApplication {
                    application { apiModule(other) }
                    val freshClient = createClient { install(ContentNegotiation) { json(wireJson) } }
                    val freshApi = Api(freshClient)
                    val freshOwner = freshApi.staff("owner")
                    val restored = freshClient.post("/v1/admin/pm/restore") { bearerAuth(freshOwner); setBody(TextContent(snapshot, ContentType.Application.Json)) }
                    assertEquals(HttpStatusCode.OK, restored.status, restored.text())

                    val back = freshApi.get<PmTaskDetail>("/v1/admin/pm/tasks/${task.key}", freshOwner)
                    assertEquals("Zaxiradagi task", back.title)
                    assertEquals(listOf("izoh"), back.comments.map { it.body })
                    assertEquals(null, back.reporterId, "the old database's operators are not here")
                    assertEquals(listOf(2, 1), freshApi.get<List<PmDocVersionInfo>>("/v1/admin/pm/docs/${doc.id}/versions", freshOwner).map { it.version })
                    // Pictures travel beside the rows and come back under their old ids, once.
                    assertEquals(HttpStatusCode.OK, freshClient.put("/v1/admin/pm/images/${image.id}") { bearerAuth(freshOwner); setBody(gif) }.status)
                    assertEquals(HttpStatusCode.Conflict, freshClient.put("/v1/admin/pm/images/${image.id}") { bearerAuth(freshOwner); setBody(gif) }.status)
                    val picture = freshClient.get("/v1/admin/pm/images/${image.id}") { bearerAuth(freshOwner) }
                    assertTrue(gif.contentEquals(picture.body<ByteArray>()))
                    val next = freshApi.post<PmTaskDetail>("/v1/admin/pm/tasks", freshOwner, CreatePmTaskRequest(title = "Keyingisi"))
                    assertTrue(next.key.removePrefix("SAD-").toInt() > task.key.removePrefix("SAD-").toInt(), "numbering carries on")
                }
            } finally {
                other.close()
                java.io.File(other.config.mediaDir).deleteRecursively()
                // Opening a component makes its database the default; the suite's own comes back.
                org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.defaultDatabase = component.databaseFactory.database
            }
        } finally {
            fresh.drop()
        }
    }

    private class FreshDatabase(val url: String, val drop: () -> Unit)

    /** An empty database beside the test one, migrated by the component that opens it. */
    private fun freshDatabase(): FreshDatabase {
        val name = "sadora_pm_restore_${Uuid.random().toString().take(8)}"
        val admin = databaseUrl!!.substringBeforeLast('/') + "/postgres"
        val user = System.getenv("TEST_DB_USER")?.takeIf { it.isNotBlank() } ?: "sadora"
        val password = System.getenv("TEST_DB_PASSWORD")?.takeIf { it.isNotBlank() } ?: "sadora"
        fun run(sql: String) = java.sql.DriverManager.getConnection(admin, user, password).use { it.createStatement().execute(sql) }
        run("CREATE DATABASE $name")
        return FreshDatabase(databaseUrl.substringBeforeLast('/') + "/" + name) { run("DROP DATABASE IF EXISTS $name WITH (FORCE)") }
    }

    // ---------------------------------------------------------------- harness

    private class Api(val client: HttpClient)

    private val wireJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private fun api(block: suspend Api.() -> Unit) = testApplication {
        application { apiModule(component) }
        val client = createClient { install(ContentNegotiation) { json(wireJson) } }
        Api(client).block()
    }

    /** A signed-in operator of [role]; returns her access token. */
    private suspend fun Api.staff(role: String): String {
        val email = "pm-${Uuid.random()}@sadora.test"
        val password = "Test12345"
        dbQuery {
            AdminUsers.insert {
                it[id] = Uuid.random()
                it[AdminUsers.email] = email
                it[passwordHash] = PasswordHasher.hash(password)
                it[name] = "PM $role"
                it[AdminUsers.role] = role
                it[totpEnabled] = false
                it[status] = "active"
                it[failedAttempts] = 0
                it[createdAt] = now().toOffsetDateTime()
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
        return client.post("/v1/admin/auth/login") { json(AdminSignInRequest(email, password)) }.body<AdminSession>().accessToken
    }

    private suspend inline fun <reified T> Api.get(path: String, token: String): T {
        val response = client.get(path) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, "GET $path: ${response.text()}")
        return response.body()
    }

    private suspend inline fun <reified T> Api.post(path: String, token: String, body: Any): T {
        val response = client.post(path) { bearerAuth(token); json(body) }
        assertTrue(response.status.value in 200..299, "POST $path: ${response.status} ${response.text()}")
        return response.body()
    }

    private suspend inline fun <reified T> Api.patch(path: String, token: String, body: Any): T {
        val response = client.patch(path) { bearerAuth(token); json(body) }
        assertEquals(HttpStatusCode.OK, response.status, "PATCH $path: ${response.text()}")
        return response.body()
    }

    private suspend inline fun <reified T> Api.put(path: String, token: String, body: Any): T {
        val response = client.put(path) { bearerAuth(token); json(body) }
        assertEquals(HttpStatusCode.OK, response.status, "PUT $path: ${response.text()}")
        return response.body()
    }

    private suspend fun Api.delete(path: String, token: String): HttpResponse = client.delete(path) { bearerAuth(token) }

    private fun HttpRequestBuilder.json(body: Any) {
        val encoded = wireJson.encodeToString(serializer(body.javaClass as java.lang.reflect.Type), body)
        setBody(TextContent(encoded, ContentType.Application.Json))
    }

    private suspend fun HttpResponse.text(): String = runCatching { bodyAsText() }.getOrDefault("")
}
