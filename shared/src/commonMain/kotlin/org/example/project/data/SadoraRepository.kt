package org.example.project.data

import kotlinx.coroutines.flow.StateFlow
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.AuthSession
import uz.sadora.contract.Bootstrap
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.Consents
import uz.sadora.contract.EmailRegisterRequest
import uz.sadora.contract.EmailSignInRequest
import uz.sadora.contract.Entitlements
import uz.sadora.contract.FeatureFlags
import uz.sadora.contract.Language
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.SocialSignInRequest
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.contract.UserProfile

/**
 * What the screens talk to.
 *
 * Its job beyond forwarding calls is to keep [SessionStore] honest: every path that
 * changes who is signed in, or what they are entitled to, updates the store here rather
 * than leaving each screen to remember. A screen that forgot would leave the app showing
 * Free content to someone who had just paid.
 */
class SadoraRepository(
    val api: SadoraApi,
    private val session: SessionStore,
    private val device: DeviceIdentity,
    private val appVersion: String? = null,
) {
    val state: StateFlow<SessionState> = session.state

    /** The phone's timezone, which profile and onboarding requests have to carry. */
    val deviceTimezone: String get() = device.timezone

    /**
     * Called once on launch. Resolves [SessionState.Unknown] into signed-in or
     * signed-out, which is what the splash screen waits on.
     *
     * A stored token that the server rejects is treated as no session at all — the user
     * sees the sign-in screen, not an error about a token she never knew existed.
     */
    suspend fun resume(): SessionState {
        if (!session.hasStoredSession()) {
            session.markSignedOut()
            return SessionState.SignedOut
        }
        val refreshed = api.refreshSession()
        if (refreshed is ApiResult.Failure) {
            session.clear()
            return SessionState.SignedOut
        }
        return when (val bootstrap = api.bootstrap(device.platform)) {
            is ApiResult.Success -> bootstrap.value.applyToSession()
            is ApiResult.Failure -> {
                // The session is valid but the call failed — most likely the network.
                // Fall back to what the refresh already told us rather than signing out.
                val value = (refreshed as ApiResult.Success).value
                session.updateProfile(value.user, value.entitlements)
                SessionState.SignedIn(value.user, value.entitlements)
            }
        }
    }

    // ---------------------------------------------------------------- sign-in

    suspend fun requestOtp(phone: String): ApiResult<OtpChallenge> = api.requestOtp(phone)

    suspend fun verifyOtp(challengeId: String, code: String): ApiResult<AuthSession> =
        api.verifyOtp(challengeId, code, device.toDeviceInfo(appVersion)).applySession()

    suspend fun signInWithEmail(email: String, password: String): ApiResult<AuthSession> =
        api.signInWithEmail(
            EmailSignInRequest(email, password, device.toDeviceInfo(appVersion)),
        ).applySession()

    suspend fun registerWithEmail(
        email: String,
        password: String,
        name: String,
        language: Language = Language.UZ,
    ): ApiResult<AuthSession> = api.registerWithEmail(
        EmailRegisterRequest(email, password, name, language, device.toDeviceInfo(appVersion)),
    ).applySession()

    suspend fun signInWithSocial(
        provider: AuthProvider,
        idToken: String,
        fullName: String? = null,
    ): ApiResult<AuthSession> = api.signInWithSocial(
        SocialSignInRequest(provider, idToken, fullName, device.toDeviceInfo(appVersion)),
    ).applySession()

    // ---------------------------------------------------------------- profile

    suspend fun completeOnboarding(request: OnboardingRequest): ApiResult<UserProfile> =
        api.completeOnboarding(request).applyProfile()

    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<UserProfile> =
        api.updateProfile(request).applyProfile()

    suspend fun consents(): ApiResult<Consents> = api.consents()

    suspend fun updateConsents(grants: ConsentGrants): ApiResult<Consents> =
        api.updateConsents(grants)

    suspend fun featureFlags(): ApiResult<FeatureFlags> = api.featureFlags(device.platform)

    /** Call after anything that can change the tier — a purchase, or returning from background. */
    suspend fun refreshEntitlements(): ApiResult<Entitlements> =
        api.entitlements().onSuccess { entitlements ->
            (session.state.value as? SessionState.SignedIn)?.let {
                session.updateProfile(it.user, entitlements)
            }
        }

    suspend fun registerPushToken(token: String) =
        api.registerDevice(device.toDeviceInfo(appVersion, pushToken = token))

    // ---------------------------------------------------------------- ending a session

    /**
     * Clears the local session even when the server call fails. A user who taps sign out
     * on a plane must not stay signed in, and the refresh token she leaves behind expires
     * on its own.
     */
    suspend fun signOut(allDevices: Boolean = false) {
        api.logout(allDevices)
        session.clear()
    }

    suspend fun deleteAccount(reason: String?): ApiResult<Unit> =
        api.deleteAccount(reason).map { }.onSuccess { session.clear() }

    // ---------------------------------------------------------------- plumbing

    private suspend fun ApiResult<AuthSession>.applySession(): ApiResult<AuthSession> =
        onSuccess { authSession ->
            session.saveTokens(authSession.tokens)
            session.updateProfile(authSession.user, authSession.entitlements)
        }

    private fun ApiResult<UserProfile>.applyProfile(): ApiResult<UserProfile> =
        onSuccess { profile ->
            val current = session.state.value as? SessionState.SignedIn ?: return@onSuccess
            session.updateProfile(profile, current.entitlements)
        }

    private fun Bootstrap.applyToSession(): SessionState.SignedIn {
        session.updateProfile(user, entitlements)
        return SessionState.SignedIn(user, entitlements)
    }
}
