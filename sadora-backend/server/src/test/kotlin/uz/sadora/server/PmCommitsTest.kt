package uz.sadora.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import uz.sadora.server.admin.pm.GitHubCommit
import uz.sadora.server.admin.pm.PmCommitSync

/** Reading task keys out of commit messages, and GitHub's commit list into rows. */
class PmCommitsTest {

    @Test
    fun `a message names tasks by their upper-case key, each once`() {
        assertEquals(listOf(12, 3), PmCommitSync.keysIn("Add the board (SAD-12)\n\nAlso closes SAD-3 and SAD-12."))
        assertEquals(emptyList(), PmCommitSync.keysIn("XSAD-4, SAD-, sad-5, SAD-12a"))
        assertEquals(listOf(7), PmCommitSync.keysIn("[SAD-7] fix"))
    }

    @Test
    fun `GitHub's commit list becomes sha, first line, author and time`() {
        val payload = """
            [
              {
                "sha": "a1b2c3",
                "html_url": "https://github.com/Murodillo/SadoraApp/commit/a1b2c3",
                "commit": {
                  "message": "Add the board SAD-1\n\nLonger body",
                  "author": { "name": "Murodillo", "date": "2026-10-23T10:00:00Z" }
                }
              }
            ]
        """.trimIndent()
        assertEquals(
            listOf(
                GitHubCommit(
                    sha = "a1b2c3",
                    url = "https://github.com/Murodillo/SadoraApp/commit/a1b2c3",
                    message = "Add the board SAD-1\n\nLonger body",
                    author = "Murodillo",
                    committedAt = Instant.parse("2026-10-23T10:00:00Z"),
                ),
            ),
            PmCommitSync.parse(payload),
        )
    }
}
