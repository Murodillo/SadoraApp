package org.example.project.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.AuthSession
import uz.sadora.contract.RefreshRequest

enum class HttpMethodKind { GET, POST, PATCH, PUT, DELETE }

/**
 * One place where a request becomes an [ApiResult].
 *
 * Extracted from [SadoraApi] once the health domains arrived: without it every new area
 * of the product either grows the same class or copies the refresh-and-retry logic, and
 * the second copy is the one that gets the concurrency wrong.
 */
class ApiCaller internal constructor(
    private val client: HttpClient,
    private val session: SessionStore,
) {
    private val refreshMutex = Mutex()

    suspend inline fun <reified T> unauthenticated(
        path: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = execute { postTo(path, block) }

    /**
     * Sends with the current access token; on a 401 refreshes once and retries once. A
     * second 401 means the refresh token is gone too, so the session is cleared.
     */
    suspend inline fun <reified T> authenticated(
        path: String,
        method: HttpMethodKind,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> {
        val tokenUsed = currentAccessToken()
        val first = execute<T> { send(path, method, tokenUsed, block) }
        if (first.failureOrNull !is ApiFailure.Unauthorized) return first

        val refreshed = refreshIfStillStale(tokenUsed)
        if (refreshed is ApiResult.Failure) {
            clearSession()
            return ApiResult.Failure(refreshed.failure)
        }
        return execute<T> { send(path, method, currentAccessToken(), block) }
            .onFailure { if (it is ApiFailure.Unauthorized) clearSession() }
    }

    suspend fun currentAccessToken(): String? = session.currentAccessToken()

    suspend fun clearSession() = session.clear()

    /**
     * Spends the stored refresh token for a new pair.
     *
     * Lives here rather than in [SadoraApi] because the retry path above needs it, and a
     * refresh that went through the retry path could recurse.
     */
    suspend fun refreshSession(): ApiResult<AuthSession> {
        val refreshToken = session.currentRefreshToken()
            ?: return ApiResult.Failure(ApiFailure.Unauthorized("Sessiya topilmadi"))
        return unauthenticated<AuthSession>("v1/auth/refresh") { setBody(RefreshRequest(refreshToken)) }
            .onSuccess { session.saveTokens(it.tokens) }
    }

    /**
     * Refreshes once for a burst of concurrent 401s.
     *
     * Several screens loading at once all fail with the same expired token. The first
     * through the mutex refreshes; the rest find the access token already changed and go
     * straight to their retry. Without that check each would spend the rotating refresh
     * token in turn — extra round trips, and a wider window in which a genuinely stolen
     * token would look identical to normal traffic.
     */
    suspend fun refreshIfStillStale(tokenUsed: String?): ApiResult<Unit> =
        refreshMutex.withLock {
            if (session.currentAccessToken() != tokenUsed) return@withLock ApiResult.Success(Unit)
            refreshSession().map { }
        }

    suspend fun postTo(path: String, block: HttpRequestBuilder.() -> Unit): HttpResponse =
        client.post(path, block)

    suspend fun send(
        path: String,
        method: HttpMethodKind,
        accessToken: String?,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val configure: HttpRequestBuilder.() -> Unit = {
            accessToken?.let { header("Authorization", "Bearer $it") }
            block()
        }
        return when (method) {
            HttpMethodKind.GET -> client.get(path, configure)
            HttpMethodKind.POST -> client.post(path, configure)
            HttpMethodKind.PATCH -> client.patch(path, configure)
            HttpMethodKind.PUT -> client.put(path, configure)
            HttpMethodKind.DELETE -> client.delete(path, configure)
        }
    }

    suspend inline fun <reified T> execute(request: () -> HttpResponse): ApiResult<T> = try {
        val response = request()
        if (response.status.isSuccess()) {
            ApiResult.Success(response.body<T>())
        } else {
            ApiResult.Failure(response.toFailure())
        }
    } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        // Everything reaching here is a transport or parsing problem, not a rejection the
        // server described. Reporting it as Network is what lets the UI offer a retry
        // rather than an apology.
        ApiResult.Failure(ApiFailure.Network(failure.message ?: "Ulanishda xatolik"))
    }

    suspend fun HttpResponse.toFailure(): ApiFailure {
        val parsed = runCatching { body<ApiErrorResponse>() }.getOrNull()
        if (parsed != null) return ApiFailure.from(parsed.error)
        // A gateway or proxy answered instead of the API, so there is no error envelope.
        return when (status) {
            HttpStatusCode.Unauthorized -> ApiFailure.Unauthorized("Sessiya tugadi")
            HttpStatusCode.TooManyRequests -> ApiFailure.RateLimited("Juda ko'p so'rov", null)
            else -> ApiFailure.Unexpected("Server xatosi (${status.value})")
        }
    }
}
