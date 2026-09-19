package uz.sadora.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import uz.sadora.contract.Entitlements
import uz.sadora.contract.TokenPair
import uz.sadora.contract.UserProfile

/** Whether anyone is signed in, and if so who. */
sealed class SessionState {
    /** Before the stored token has been checked — the splash screen's state. */
    data object Unknown : SessionState()

    data object SignedOut : SessionState()

    data class SignedIn(
        val user: UserProfile,
        val entitlements: Entitlements,
    ) : SessionState() {
        /** Onboarding is a gate, not a screen the user can skip past. */
        val needsOnboarding: Boolean get() = !user.onboardingCompleted
    }
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
    private var accessExpiresAt: Instant? = null
    private var refreshToken: String? = null

    suspend fun currentAccessToken(): String? = mutex.withLock { accessToken }

    suspend fun currentRefreshToken(): String? = mutex.withLock {
        refreshToken ?: storage.readRefreshToken()?.also { refreshToken = it }
    }

    suspend fun saveTokens(tokens: TokenPair) = mutex.withLock {
        accessToken = tokens.accessToken
        accessExpiresAt = tokens.accessExpiresAt
        refreshToken = tokens.refreshToken
        storage.writeRefreshToken(tokens.refreshToken)
    }

    /** Publishes who is signed in, and keeps a copy for the next launch without a network. */
    suspend fun updateProfile(user: UserProfile, entitlements: Entitlements) {
        _state.value = SessionState.SignedIn(user, entitlements)
        // A snapshot that fails to write costs only the offline launch, never the session.
        runCatching {
            val snapshot = buildJsonObject {
                put(SNAPSHOT_USER, SadoraJson.encodeToJsonElement(UserProfile.serializer(), user))
                put(SNAPSHOT_ENTITLEMENTS, SadoraJson.encodeToJsonElement(Entitlements.serializer(), entitlements))
            }
            storage.writeSessionSnapshot(snapshot.toString())
        }
    }

    /** The profile the last session saw, or null when there is none or it no longer parses. */
    suspend fun cachedSession(): SessionState.SignedIn? = runCatching {
        val stored = storage.readSessionSnapshot() ?: return@runCatching null
        val snapshot: JsonObject = SadoraJson.parseToJsonElement(stored).jsonObject
        SessionState.SignedIn(
            user = SadoraJson.decodeFromJsonElement(UserProfile.serializer(), snapshot.getValue(SNAPSHOT_USER)),
            entitlements = SadoraJson.decodeFromJsonElement(Entitlements.serializer(), snapshot.getValue(SNAPSHOT_ENTITLEMENTS)),
        )
    }.getOrNull()

    private companion object {
        const val SNAPSHOT_USER = "user"
        const val SNAPSHOT_ENTITLEMENTS = "entitlements"
    }

    fun markSignedOut() {
        _state.value = SessionState.SignedOut
    }

    /** True when a stored refresh token exists, so the splash can try to resume. */
    suspend fun hasStoredSession(): Boolean = currentRefreshToken() != null

    suspend fun clear() {
        mutex.withLock {
            accessToken = null
            accessExpiresAt = null
            refreshToken = null
            storage.clear()
        }
        markSignedOut()
    }
}
