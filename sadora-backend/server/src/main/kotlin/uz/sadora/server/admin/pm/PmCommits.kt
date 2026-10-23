package uz.sadora.server.admin.pm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import uz.sadora.server.config.PmConfig
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AppSettings
import uz.sadora.server.db.dbQuery

/** One commit as GitHub lists it, cut to what a task shows. */
data class GitHubCommit(val sha: String, val url: String, val message: String, val author: String, val committedAt: Instant)

/**
 * Commits whose message names a task — "… SAD-12" — shown on that task.
 *
 * The repository is public, so the server reads GitHub's commit list itself every few
 * minutes: no secret in CI, and no way in from outside to open for a webhook. A key is
 * only a link; nothing moves a card because a commit mentions it. A commit seen on `dev`
 * and later on `main` keeps the branch it was first seen on.
 */
class PmCommitSync(private val config: PmConfig, private val http: HttpClient) {
    private val logger = LoggerFactory.getLogger(PmCommitSync::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile private var lastSuccessAt: Instant? = null
    @Volatile private var lastError: String? = null

    fun state(): PmCommitSyncState = PmCommitSyncState(config.commitPoll, config.githubRepo, lastSuccessAt, lastError)

    fun start() {
        if (!config.commitPoll) return
        job = scope.launch {
            while (isActive) {
                runCatching { pass() }
                    .onSuccess { lastSuccessAt = now(); lastError = null }
                    .onFailure {
                        lastError = it.message?.take(200) ?: it::class.simpleName
                        logger.warn("Commit pass failed: {}", lastError)
                    }
                delay(5.minutes)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }

    /**
     * Reads each branch newest first until it meets the head the last pass saw. Not by date:
     * GitHub dates a commit when it was made, and dev often waits days for a push. The first
     * page is read every time, so a task created after its commit still picks it up.
     */
    suspend fun pass() {
        for (branch in config.branches) {
            val cursor = CURSOR_PREFIX + branch
            val seen = dbQuery {
                AppSettings.selectAll().where { AppSettings.key eq cursor }.singleOrNull()?.get(AppSettings.value)
            }
            var head: String? = null
            for (page in 1..MAX_PAGES) {
                val commits = fetch(branch, page)
                if (page == 1) head = commits.firstOrNull()?.sha
                ingest(branch, commits)
                if (commits.size < PAGE_SIZE || commits.any { it.sha == seen }) break
            }
            if (head != null && head != seen) {
                dbQuery {
                    AppSettings.upsert {
                        it[key] = cursor
                        it[value] = head
                        it[updatedAt] = now().toOffsetDateTime()
                    }
                }
            }
        }
    }

    /** Links each commit to the existing tasks its message names. */
    suspend fun ingest(branch: String, commits: List<GitHubCommit>) {
        val wanted = commits.associateWith { keysIn(it.message) }.filterValues { it.isNotEmpty() }
        if (wanted.isEmpty()) return
        dbQuery {
            val numbers = wanted.values.flatten().distinct()
            val ids = PmTasks.select(PmTasks.id, PmTasks.number)
                .where { PmTasks.number inList numbers }
                .associate { it[PmTasks.number] to it[PmTasks.id] }
            wanted.forEach { (commit, keys) ->
                keys.mapNotNull(ids::get).forEach { taskId ->
                    PmTaskCommits.insertIgnore {
                        it[PmTaskCommits.taskId] = taskId
                        it[sha] = commit.sha
                        it[PmTaskCommits.branch] = branch
                        it[message] = commit.message.take(MESSAGE_MAX)
                        it[author] = commit.author.take(120)
                        it[url] = commit.url
                        it[committedAt] = commit.committedAt.toOffsetDateTime()
                    }
                }
            }
        }
    }

    private suspend fun fetch(branch: String, page: Int): List<GitHubCommit> {
        val response = http.get("https://api.github.com/repos/${config.githubRepo}/commits") {
            parameter("sha", branch)
            parameter("per_page", PAGE_SIZE)
            parameter("page", page)
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "sadora-server")
            config.githubToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        // A branch that does not exist yet (main before the release) is not a failure.
        if (response.status.value == 404 || response.status.value == 409) return emptyList()
        if (!response.status.isSuccess()) error("GitHub ${response.status.value} for $branch")
        return parse(response.bodyAsText())
    }

    @Serializable
    private data class Wire(val sha: String, @SerialName("html_url") val htmlUrl: String, val commit: WireCommit)

    @Serializable
    private data class WireCommit(val message: String, val author: WireAuthor? = null)

    @Serializable
    private data class WireAuthor(val name: String? = null, val date: String? = null)

    internal companion object {
        /** Per branch: the newest commit the last pass read. */
        const val CURSOR_PREFIX = "pm_commit_head:"
        const val PAGE_SIZE = 100
        /** A first pass, or a push of more than this many commits, reads no further back. */
        const val MAX_PAGES = 10
        const val MESSAGE_MAX = 2000
        private val KEY = Regex("(?<![A-Za-z0-9-])SAD-(\\d{1,9})(?![A-Za-z0-9])")
        private val json = Json { ignoreUnknownKeys = true }

        fun keysIn(message: String): List<Int> = KEY.findAll(message).map { it.groupValues[1].toInt() }.distinct().toList()

        fun parse(payload: String): List<GitHubCommit> = json.decodeFromString<List<Wire>>(payload).map { wire ->
            GitHubCommit(
                sha = wire.sha,
                url = wire.htmlUrl,
                message = wire.commit.message,
                author = wire.commit.author?.name ?: "?",
                committedAt = wire.commit.author?.date?.let(Instant::parse) ?: Instant.fromEpochSeconds(0),
            )
        }
    }
}
