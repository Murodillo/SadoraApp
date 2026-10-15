package uz.sadora.widget

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.AuthSession
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.RefreshRequest
import uz.sadora.contract.WidgetSummary

/**
 * The companion session's two tokens. Kept in the Keychain group the app shares with its
 * widget extension and watch, or the Wear OS app's own encrypted store — never in the
 * plain defaults the snapshot uses.
 */
class CompanionTokens(private val secure: KeyValues) {
    fun access(): String? = secure.get(ACCESS)
    fun refresh(): String? = secure.get(REFRESH)

    fun save(access: String, refresh: String) {
        secure.put(ACCESS, access)
        secure.put(REFRESH, refresh)
    }

    fun clear() {
        secure.put(ACCESS, null)
        secure.put(REFRESH, null)
    }

    private companion object {
        const val ACCESS = "companion.access"
        const val REFRESH = "companion.refresh"
    }
}

/**
 * [WidgetApi] over a companion session: the iOS widget extension and the watch, which
 * run outside the app and cannot borrow its tokens (see `POST /v1/auth/companion`).
 */
class CompanionApi(
    /** Read per call: the app writes it when it hands the widgets their session. */
    private val baseUrl: () -> String?,
    private val tokens: CompanionTokens,
    private val client: HttpClient,
) : WidgetApi {
    private val base: String get() = baseUrl().orEmpty().trimEnd('/') + "/"
    private val refreshLock = Mutex()

    override suspend fun summary(): WidgetCall<WidgetSummary> =
        call(WidgetSummary.serializer()) { access -> client.get(base + "v1/widget/summary") { auth(access) } }

    override suspend fun send(action: PendingAction): WidgetCall<Unit> = call(null) { access ->
        when (action) {
            is PendingAction.Water -> client.post(base + "v1/nutrition/water") {
                auth(access)
                json(WidgetStore.json.encodeToString(AddWaterRequest.serializer(), AddWaterRequest(action.ml, action.clientId)))
            }
            is PendingAction.Dose -> client.post(base + "v1/meds/${action.medicationId}/doses") {
                auth(access)
                json(WidgetStore.json.encodeToString(RecordDoseRequest.serializer(), RecordDoseRequest(action.dueOn, action.dueAt, action.status)))
            }
        }
    }

    private suspend fun <T> call(
        serializer: KSerializer<T>?,
        request: suspend (access: String) -> HttpResponse,
    ): WidgetCall<T> {
        val access = tokens.access() ?: return WidgetCall.SignedOut
        if (baseUrl() == null) return WidgetCall.SignedOut
        val first = attempt(serializer) { request(access) }
        if (first !is Unauthorized) return first.asCall()
        return when (refresh(access)) {
            WidgetCall.Offline -> WidgetCall.Offline
            is WidgetCall.Ok -> {
                val again = attempt(serializer) { request(tokens.access().orEmpty()) }
                if (again is Unauthorized) signOut() else again.asCall()
            }
            else -> signOut()
        }
    }

    /** Spends the refresh token once for any number of 401s that raced here together. */
    private suspend fun refresh(staleAccess: String): WidgetCall<Unit> = refreshLock.withLock {
        if (tokens.access() != staleAccess) return@withLock WidgetCall.Ok(Unit)
        val refresh = tokens.refresh() ?: return@withLock WidgetCall.SignedOut
        val answer = attempt(AuthSession.serializer()) {
            client.post(base + "v1/auth/refresh") {
                json(WidgetStore.json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh)))
            }
        }
        when (answer) {
            is Done -> {
                tokens.save(answer.value.tokens.accessToken, answer.value.tokens.refreshToken)
                WidgetCall.Ok(Unit)
            }
            Unauthorized, Refused -> WidgetCall.SignedOut
            NoConnection -> WidgetCall.Offline
        }
    }

    private fun <T> signOut(): WidgetCall<T> {
        tokens.clear()
        return WidgetCall.SignedOut
    }

    private sealed interface Attempt<out T> {
        fun asCall(): WidgetCall<T> = when (this) {
            is Done -> WidgetCall.Ok(value)
            Refused -> WidgetCall.Rejected
            NoConnection, Unauthorized -> WidgetCall.Offline
        }
    }
    private data class Done<T>(val value: T) : Attempt<T>
    private data object Unauthorized : Attempt<Nothing>
    private data object Refused : Attempt<Nothing>
    private data object NoConnection : Attempt<Nothing>

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> attempt(serializer: KSerializer<T>?, block: suspend () -> HttpResponse): Attempt<T> {
        val response = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return NoConnection
        }
        return when {
            response.status == HttpStatusCode.Unauthorized -> Unauthorized
            response.status.isSuccess() -> {
                if (serializer == null) return Done(Unit as T)
                runCatching { WidgetStore.json.decodeFromString(serializer, response.bodyAsText()) }
                    .fold({ Done(it) }, { NoConnection })
            }
            // A 5xx or a rate limit is the server's moment, not her tap's fault: retry later.
            response.status.value >= 500 || response.status == HttpStatusCode.TooManyRequests -> NoConnection
            else -> Refused
        }
    }

    private fun HttpRequestBuilder.auth(access: String) = bearerAuth(access)

    private fun HttpRequestBuilder.json(body: String) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
