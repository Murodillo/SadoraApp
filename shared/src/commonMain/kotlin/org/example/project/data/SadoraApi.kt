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
import uz.sadora.contract.Ack
import uz.sadora.contract.ApiErrorResponse
import uz.sadora.contract.AuthSession
import uz.sadora.contract.Bootstrap
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.Consents
import uz.sadora.contract.DeleteAccountRequest
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.EmailRegisterRequest
import uz.sadora.contract.EmailSignInRequest
import uz.sadora.contract.Entitlements
import uz.sadora.contract.FeatureFlags
import uz.sadora.contract.LogoutRequest
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.OtpRequest
import uz.sadora.contract.OtpVerifyRequest
import uz.sadora.contract.Platform
import uz.sadora.contract.RefreshRequest
import uz.sadora.contract.RegisterDeviceRequest
import uz.sadora.contract.SocialSignInRequest
import uz.sadora.contract.SubscriptionStatus
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.contract.UserProfile

/**
 * Typed access to the API. One function per endpoint, no string building at call sites.
 *
 * Authenticated calls handle their own token refresh: a 401 triggers one refresh and one
 * retry, and a second 401 signs the user out. The refresh is serialised behind a mutex —
 * several screens loading at once produce several concurrent 401s, and refreshing once
 * per request would spend the rotating refresh token several times over and trip the
 * server's reuse detection, logging the user out for doing nothing wrong.
 */
class SadoraApi internal constructor(
    private val client: HttpClient,
    private val session: SessionStore,
) {
    private val refreshMutex = Mutex()

    // ---------------------------------------------------------------- sign-in

    suspend fun requestOtp(phone: String): ApiResult<OtpChallenge> =
        public("v1/auth/otp/request") { setBody(OtpRequest(phone = phone)) }

    suspend fun verifyOtp(
        challengeId: String,
        code: String,
        device: DeviceInfo,
    ): ApiResult<AuthSession> = public("v1/auth/otp/verify") {
        setBody(OtpVerifyRequest(challengeId = challengeId, code = code, device = device))
    }

    suspend fun signInWithSocial(request: SocialSignInRequest): ApiResult<AuthSession> =
        public("v1/auth/social") { setBody(request) }

    suspend fun registerWithEmail(request: EmailRegisterRequest): ApiResult<AuthSession> =
        public("v1/auth/email/register") { setBody(request) }

    suspend fun signInWithEmail(request: EmailSignInRequest): ApiResult<AuthSession> =
        public("v1/auth/email/login") { setBody(request) }

    /** Spends the stored refresh token for a new pair. Also used to resume on launch. */
    suspend fun refreshSession(): ApiResult<AuthSession> {
        val refreshToken = session.currentRefreshToken()
            ?: return ApiResult.Failure(ApiFailure.Unauthorized("Sessiya topilmadi"))
        return public<AuthSession>("v1/auth/refresh") { setBody(RefreshRequest(refreshToken)) }
            .onSuccess { session.saveTokens(it.tokens) }
    }

    suspend fun logout(allDevices: Boolean = false): ApiResult<Ack> {
        val refreshToken = session.currentRefreshToken()
        return authenticated<Ack>("v1/auth/logout", HttpMethodKind.POST) {
            setBody(LogoutRequest(refreshToken, allDevices))
        }
    }

    // ---------------------------------------------------------------- profile

    suspend fun bootstrap(platform: Platform): ApiResult<Bootstrap> =
        authenticated("v1/bootstrap?platform=${platform.wire()}", HttpMethodKind.GET)

    suspend fun profile(): ApiResult<UserProfile> =
        authenticated("v1/me", HttpMethodKind.GET)

    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<UserProfile> =
        authenticated("v1/me", HttpMethodKind.PATCH) { setBody(request) }

    suspend fun completeOnboarding(request: OnboardingRequest): ApiResult<UserProfile> =
        authenticated("v1/me/onboarding", HttpMethodKind.POST) { setBody(request) }

    suspend fun consents(): ApiResult<Consents> =
        authenticated("v1/me/consents", HttpMethodKind.GET)

    suspend fun updateConsents(grants: ConsentGrants): ApiResult<Consents> =
        authenticated("v1/me/consents", HttpMethodKind.PUT) { setBody(grants) }

    suspend fun registerDevice(device: DeviceInfo): ApiResult<Ack> =
        authenticated("v1/me/devices", HttpMethodKind.POST) {
            setBody(RegisterDeviceRequest(device))
        }

    suspend fun deleteAccount(reason: String?): ApiResult<Ack> =
        authenticated("v1/me", HttpMethodKind.DELETE) {
            setBody(DeleteAccountRequest(reason = reason, confirmation = "DELETE"))
        }

    // ---------------------------------------------------------------- entitlements

    suspend fun entitlements(): ApiResult<Entitlements> =
        authenticated("v1/entitlements", HttpMethodKind.GET)

    suspend fun subscription(): ApiResult<SubscriptionStatus> =
        authenticated("v1/subscription", HttpMethodKind.GET)

    suspend fun featureFlags(platform: Platform): ApiResult<FeatureFlags> =
        authenticated("v1/feature-flags?platform=${platform.wire()}", HttpMethodKind.GET)

    // ---------------------------------------------------------------- plumbing

    internal enum class HttpMethodKind { GET, POST, PATCH, PUT, DELETE }

    private suspend inline fun <reified T> public(
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = execute { client.post(path) { block() } }

    private suspend inline fun <reified T> authenticated(
        path: String,
        method: HttpMethodKind,
        crossinline block: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> {
        val tokenUsed = session.currentAccessToken()
        val first = execute<T> { send(path, method, tokenUsed, block) }
        if (first.failureOrNull !is ApiFailure.Unauthorized) return first

        val refreshed = refreshIfStillStale(tokenUsed)
        if (refreshed is ApiResult.Failure) {
            session.clear()
            return ApiResult.Failure(refreshed.failure)
        }
        // One retry. A second 401 means the refresh token is gone too.
        return execute<T> { send(path, method, session.currentAccessToken(), block) }
            .onFailure { if (it is ApiFailure.Unauthorized) session.clear() }
    }

    /**
     * Refreshes once for a burst of concurrent 401s.
     *
     * Several screens loading at once all fail with the same expired token. The first
     * one through the mutex refreshes; the rest find the access token already changed
     * and go straight to their retry. Without that check each would spend the rotating
     * refresh token in turn — extra round trips, and a wider window in which a genuinely
     * stolen token would look identical to normal traffic.
     */
    private suspend fun refreshIfStillStale(tokenUsed: String?): ApiResult<Unit> =
        refreshMutex.withLock {
            if (session.currentAccessToken() != tokenUsed) return@withLock ApiResult.Success(Unit)
            refreshSession().map { }
        }

    private suspend inline fun send(
        path: String,
        method: HttpMethodKind,
        accessToken: String?,
        crossinline block: HttpRequestBuilder.() -> Unit,
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

    private suspend inline fun <reified T> execute(
        request: () -> HttpResponse,
    ): ApiResult<T> = try {
        val response = request()
        if (response.status.isSuccess()) {
            ApiResult.Success(response.body<T>())
        } else {
            ApiResult.Failure(response.toFailure())
        }
    } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        // Everything reaching here is a transport or parsing problem, not a rejection
        // the server described. Reporting it as Network is what lets the UI offer a
        // retry rather than an apology.
        ApiResult.Failure(ApiFailure.Network(failure.message ?: "Ulanishda xatolik"))
    }

    private suspend fun HttpResponse.toFailure(): ApiFailure {
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

private fun Platform.wire(): String = name.lowercase()
