package uz.sadora.doctor.data

import kotlinx.coroutines.flow.StateFlow
import uz.sadora.contract.AuthSession
import uz.sadora.contract.Language
import uz.sadora.contract.OtpChallenge

/**
 * Who is signed in, kept honest.
 *
 * The client app's repository, trimmed to the session: every path that changes who is
 * signed in updates [SessionStore] here rather than leaving each screen to remember.
 */
class AuthRepository(
    val api: AuthApi,
    private val session: SessionStore,
    private val device: DeviceIdentity,
    private val appVersion: String? = null,
) {
    val state: StateFlow<SessionState> = session.state

    /**
     * Called once on launch. Resolves [SessionState.Unknown] into signed-in or
     * signed-out, which is what the splash screen waits on.
     *
     * A stored token the server rejects is treated as no session at all. A refresh that
     * could not reach the server is not a rejection: she keeps her session and opens on
     * the profile the last launch saw.
     */
    suspend fun resume(): SessionState {
        if (!session.hasStoredSession()) {
            session.markSignedOut()
            return SessionState.SignedOut
        }
        return when (val refreshed = api.refreshSession()) {
            is ApiResult.Success -> {
                session.updateUser(refreshed.value.user)
                SessionState.SignedIn(refreshed.value.user)
            }
            is ApiResult.Failure -> {
                if (refreshed.failure.endsSession) {
                    session.clear()
                    return SessionState.SignedOut
                }
                // Offline, or the server is having a bad minute. The token stays; the next
                // call that gets through refreshes it.
                val cached = session.cachedSession() ?: run {
                    session.markSignedOut()
                    return SessionState.SignedOut
                }
                session.updateUser(cached.user)
                cached
            }
        }
    }

    suspend fun requestOtp(phone: String, language: Language): ApiResult<OtpChallenge> =
        api.requestOtp(phone, language)

    suspend fun verifyOtp(challengeId: String, code: String): ApiResult<AuthSession> =
        api.verifyOtp(challengeId, code, device.toDeviceInfo(appVersion)).onSuccess { authSession ->
            session.saveTokens(authSession.tokens)
            session.updateUser(authSession.user)
        }

    /**
     * Clears the local session even when the server call fails. A doctor who taps sign out
     * without a connection must not stay signed in; the refresh token she leaves behind
     * expires on its own.
     */
    suspend fun signOut(allDevices: Boolean = false) {
        api.logout(allDevices)
        session.clear()
    }
}
