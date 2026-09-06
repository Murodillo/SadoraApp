package org.example.project.data

import io.ktor.client.request.setBody
import uz.sadora.contract.Ack
import uz.sadora.contract.AuthSession
import uz.sadora.contract.Bootstrap
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.Consents
import uz.sadora.contract.DeleteAccountRequest
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.Entitlements
import uz.sadora.contract.FeatureFlags
import uz.sadora.contract.LogoutRequest
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.OtpRequest
import uz.sadora.contract.OtpVerifyRequest
import uz.sadora.contract.Platform
import uz.sadora.contract.RegisterDeviceRequest
import uz.sadora.contract.SocialSignInRequest
import uz.sadora.contract.SubscriptionStatus
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.contract.UserProfile

/**
 * Account, profile and entitlement endpoints.
 *
 * The health domains live in their own API classes over the same [ApiCaller]; this one
 * keeps what every screen needs regardless of which tab it is on.
 */
class SadoraApi internal constructor(
    private val caller: ApiCaller,
    private val session: SessionStore,
) {
    // ---------------------------------------------------------------- sign-in

    suspend fun requestOtp(phone: String): ApiResult<OtpChallenge> =
        caller.unauthenticated("v1/auth/otp/request") { setBody(OtpRequest(phone = phone)) }

    suspend fun verifyOtp(
        challengeId: String,
        code: String,
        device: DeviceInfo,
    ): ApiResult<AuthSession> = caller.unauthenticated("v1/auth/otp/verify") {
        setBody(OtpVerifyRequest(challengeId = challengeId, code = code, device = device))
    }

    suspend fun signInWithSocial(request: SocialSignInRequest): ApiResult<AuthSession> =
        caller.unauthenticated("v1/auth/social") { setBody(request) }

    suspend fun refreshSession(): ApiResult<AuthSession> = caller.refreshSession()

    suspend fun logout(allDevices: Boolean = false): ApiResult<Ack> {
        val refreshToken = session.currentRefreshToken()
        return caller.authenticated("v1/auth/logout", HttpMethodKind.POST) {
            setBody(LogoutRequest(refreshToken, allDevices))
        }
    }

    // ---------------------------------------------------------------- profile

    suspend fun bootstrap(platform: Platform): ApiResult<Bootstrap> =
        caller.authenticated("v1/bootstrap?platform=${platform.wire()}", HttpMethodKind.GET)

    suspend fun profile(): ApiResult<UserProfile> =
        caller.authenticated("v1/me", HttpMethodKind.GET)

    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<UserProfile> =
        caller.authenticated("v1/me", HttpMethodKind.PATCH) { setBody(request) }

    suspend fun completeOnboarding(request: OnboardingRequest): ApiResult<UserProfile> =
        caller.authenticated("v1/me/onboarding", HttpMethodKind.POST) { setBody(request) }

    suspend fun consents(): ApiResult<Consents> =
        caller.authenticated("v1/me/consents", HttpMethodKind.GET)

    suspend fun updateConsents(grants: ConsentGrants): ApiResult<Consents> =
        caller.authenticated("v1/me/consents", HttpMethodKind.PUT) { setBody(grants) }

    suspend fun registerDevice(device: DeviceInfo): ApiResult<Ack> =
        caller.authenticated("v1/me/devices", HttpMethodKind.POST) {
            setBody(RegisterDeviceRequest(device))
        }

    suspend fun deleteAccount(reason: String?): ApiResult<Ack> =
        caller.authenticated("v1/me", HttpMethodKind.DELETE) {
            setBody(DeleteAccountRequest(reason = reason, confirmation = "DELETE"))
        }

    // ---------------------------------------------------------------- entitlements

    suspend fun entitlements(): ApiResult<Entitlements> =
        caller.authenticated("v1/entitlements", HttpMethodKind.GET)

    suspend fun subscription(): ApiResult<SubscriptionStatus> =
        caller.authenticated("v1/subscription", HttpMethodKind.GET)

    suspend fun featureFlags(platform: Platform): ApiResult<FeatureFlags> =
        caller.authenticated("v1/feature-flags?platform=${platform.wire()}", HttpMethodKind.GET)
}

internal fun Platform.wire(): String = name.lowercase()
