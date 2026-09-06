package org.example.project.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.example.project.data.api.LearnApi
import uz.sadora.contract.Article
import uz.sadora.contract.ArticleFeed

/**
 * The library, as the app holds it.
 *
 * The feed is fetched whole and filtered locally: the categories arrive with it, the
 * whole library is a few dozen cards, and re-asking the server on every chip tap would
 * make a filter feel like a page load. Articles are cached by slug so going back to the
 * list and into the same piece again does not refetch it.
 *
 * A null API means no backend, and then the screens say the library is unavailable
 * rather than drawing a library that does not exist.
 */
class LearnController(private val api: LearnApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val isOffline: Boolean get() = api == null

    var feed by mutableStateOf<ArticleFeed?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private val articles = mutableStateMapOf<String, Article>()

    fun article(slug: String): Article? = articles[slug]

    suspend fun loadFeed(force: Boolean = false) {
        val api = api ?: return
        if (!force && feed != null) return

        var refusal: ApiFailure? = null
        val loaded = calls.run(silent = true) { api.feed().onFailure { refusal = it } }
        when {
            loaded != null -> {
                feed = loaded
                error = null
            }
            refusal != null -> error = refusal.readable()
        }
    }

    /**
     * Loads one article.
     *
     * [force] is used after an upgrade: the same slug then comes back with the rest of
     * its body, and a cached truncated copy would keep the paywall on screen.
     */
    suspend fun loadArticle(slug: String, force: Boolean = false) {
        val api = api ?: return
        if (!force && articles.containsKey(slug)) return

        var refusal: ApiFailure? = null
        val loaded = calls.run(silent = true) { api.article(slug).onFailure { refusal = it } }
        when {
            loaded != null -> {
                articles[slug] = loaded
                error = null
            }
            refusal != null -> error = refusal.readable()
        }
    }

    /** Called after a purchase, so locked bodies are asked for again rather than assumed. */
    fun forgetArticles() = articles.clear()
}
