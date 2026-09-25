package uz.sadora.doctor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.sadora.contract.TokenPair
import uz.sadora.contract.UserProfile

/** Whether anyone is signed in, and if so who. */
sealed class SessionState {
    /** Before the stored token has been checked — the splash screen's state. */
    data object Unknown : SessionState()

    data object SignedOut : SessionState()

    /**
     * The account she signed in with — the same one the client app uses. Whether it is
     * a doctor's is a separate question, answered by `v1/doctor/me`.
     */
    data class SignedIn(val user: UserProfile) : SessionState()
}

/**
 * The single owner of the tokens and the signed-in user.
 *
 * The access token is held in memory and the refresh token in [TokenStorage]; both are
 * written through one mutex, because the refresh path can be entered concurrently by
 * several in-flight requests and the last writer must not be a stale one.
 */
class SessionStore(private val storage: TokenStorage) {

    private val mutex = Mutex()

    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var accessToken: String? = null
    private var refreshToken: String? = null

    suspend fun currentAccessToken(): String? = mutex.withLock { accessToken }

    suspend fun currentRefreshToken(): String? = mutex.withLock {
        refreshToken ?: storage.readRefreshToken()?.also { refreshToken = it }
    }

    suspend fun saveTokens(tokens: TokenPair) = mutex.withLock {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        storage.writeRefreshToken(tokens.refreshToken)
    }

    /** Publishes who is signed in, and keeps a copy for the next launch without a network. */
    suspend fun updateUser(user: UserProfile) {
        _state.value = SessionState.SignedIn(user)
        // A snapshot that fails to write costs only the offline launch, never the session.
        runCatching {
            storage.writeSessionSnapshot(SadoraJson.encodeToString(UserProfile.serializer(), user))
        }
    }

    /** The profile the last session saw, or null when there is none or it no longer parses. */
    suspend fun cachedSession(): SessionState.SignedIn? = runCatching {
        val stored = storage.readSessionSnapshot() ?: return@runCatching null
        SessionState.SignedIn(SadoraJson.decodeFromString(UserProfile.serializer(), stored))
    }.getOrNull()

    fun markSignedOut() {
        _state.value = SessionState.SignedOut
    }

    /** True when a stored refresh token exists, so the splash can try to resume. */
    suspend fun hasStoredSession(): Boolean = currentRefreshToken() != null

    suspend fun clear() {
        mutex.withLock {
            accessToken = null
            refreshToken = null
            storage.clear()
        }
        markSignedOut()
    }
}
