package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.contract.Article
import uz.sadora.contract.ArticleBlock
import uz.sadora.contract.ArticleCategory
import uz.sadora.contract.ArticleFeed
import uz.sadora.contract.ArticleKind
import uz.sadora.contract.ArticleSummary
import uz.sadora.contract.ErrorCodes
import uz.sadora.app.i18n.StringsUz

/**
 * The Bilim library used to be three constants in the app. These pin the replacement:
 * the library is fetched once, an article is kept by slug, a locked body is a paywall
 * rather than an error, and with no backend the app claims no library at all.
 */
class LearnControllerTest {

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun summary(
        slug: String,
        premium: Boolean = false,
        locked: Boolean = false,
    ) = ArticleSummary(
        slug = slug,
        kind = ArticleKind.ARTICLE,
        categoryKey = "sleep",
        categoryLabel = "Uyqu",
        title = "Kechki tartib",
        excerpt = "Yarim soatlik amal.",
        readMinutes = 6,
        premium = premium,
        locked = locked,
    )

    private fun feed(vararg articles: ArticleSummary) = ArticleFeed(
        categories = listOf(ArticleCategory("sleep", "Uyqu", count = articles.size)),
        articles = articles.toList(),
    )

    @Test
    fun `the library is fetched once and kept so a category chip does not reload it`() = runTest {
        val recording = RecordingEngine { json(encode(feed(summary("kechki-tartib")))) }
        val learn = graph(recording).learnController()

        learn.loadFeed()
        learn.loadFeed()

        assertEquals(1, recording.paths.count { it == "/v1/articles" })
        assertEquals(1, learn.feed?.articles?.size)
        assertEquals("Uyqu", learn.feed?.categories?.first()?.label)
    }

    @Test
    fun `an article is kept by slug and asked for again only when forced`() = runTest {
        val article = Article(
            summary = summary("kechki-tartib"),
            blocks = listOf(ArticleBlock.Paragraph("Matn.")),
        )
        val recording = RecordingEngine { json(encode(article)) }
        val learn = graph(recording).learnController()

        learn.loadArticle("kechki-tartib")
        learn.loadArticle("kechki-tartib")
        assertEquals(1, recording.paths.count { it == "/v1/articles/kechki-tartib" })

        // After an upgrade the same slug must be asked for again, or the paywall stays.
        learn.loadArticle("kechki-tartib", force = true)
        assertEquals(2, recording.paths.count { it == "/v1/articles/kechki-tartib" })
    }

    @Test
    fun `a locked article arrives as content with a paywall not as an error`() = runTest {
        val locked = Article(
            summary = summary("siklni-tushunish", premium = true, locked = true),
            blocks = listOf(ArticleBlock.Paragraph("Ochiq xatboshi.")),
            truncated = true,
        )
        val recording = RecordingEngine { json(encode(locked)) }
        val learn = graph(recording).learnController()

        learn.loadArticle("siklni-tushunish")

        val held = assertNotNull(learn.article("siklni-tushunish"))
        assertTrue(held.truncated, "the screen draws the lock from this, not from a failure")
        assertEquals(1, held.blocks.size)
        assertNull(learn.error, "a paywall is not an error banner")
    }

    @Test
    fun `forgetting articles after a purchase drops the truncated copies`() = runTest {
        val recording = RecordingEngine {
            json(encode(Article(summary = summary("kechki-tartib"), truncated = true)))
        }
        val learn = graph(recording).learnController()

        learn.loadArticle("kechki-tartib")
        assertNotNull(learn.article("kechki-tartib"))

        learn.forgetArticles()
        assertNull(learn.article("kechki-tartib"))
    }

    @Test
    fun `a failure is reported and no library is invented`() = runTest {
        val recording = RecordingEngine {
            json(errorBody(ErrorCodes.INTERNAL_ERROR, "Server xatosi"), HttpStatusCode.InternalServerError)
        }
        val learn = graph(recording).learnController()

        learn.loadFeed()

        assertNull(learn.feed)
        assertEquals(StringsUz.errors.unexpected, learn.error?.readable(StringsUz.errors))
    }

    @Test
    fun `with no backend nothing is claimed`() = runTest {
        val learn = LearnController(null)
        learn.loadFeed()
        learn.loadArticle("kechki-tartib")

        assertNull(learn.feed)
        assertNull(learn.article("kechki-tartib"))
        assertNull(learn.error)
        assertTrue(learn.isOffline)
    }
}
