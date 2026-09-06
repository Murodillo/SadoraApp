package org.example.project.data.api

import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.Article
import uz.sadora.contract.ArticleFeed

/**
 * The Bilim library.
 *
 * Both calls return what this reader may see: a draft never appears, and a premium body
 * arrives truncated with the lock already decided server-side.
 */
class LearnApi(private val caller: ApiCaller) {

    suspend fun feed(categoryKey: String? = null): ApiResult<ArticleFeed> {
        val query = categoryKey?.let { "?category=$it" }.orEmpty()
        return caller.authenticated("v1/articles$query", HttpMethodKind.GET)
    }

    suspend fun article(slug: String): ApiResult<Article> =
        caller.authenticated("v1/articles/$slug", HttpMethodKind.GET)
}
