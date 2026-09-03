package org.example.project.data

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.example.project.model.AppState
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.OtpChallenge

/** Where the user belongs after an auth call succeeds. */
enum class AuthDestination { Onboarding, Main }

/**
 * What the screens call.
 *
 * The repository speaks in contract DTOs and [ApiResult]; the screens speak in
 * [AppState] and Uzbek strings. This sits between them so no screen imports a wire
 * type, and so the busy/error handling every call needs is written once.
 *
 * A null [repository] means no backend — previews, `@Preview`, and unit tests. Every
 * action then succeeds locally against [AppState] alone, which keeps the whole app
 * usable offline as a prototype rather than dead behind a sign-in wall.
 */
class SadoraController(
    private val repository: SadoraRepository?,
    private val state: AppState,
) {
    /** Busy and error live in [ApiCallState] so every controller handles them alike. */
    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: String? get() = calls.error

    /** True when there is no backend behind the app. */
    val isOffline: Boolean get() = repository == null

    fun clearError() = calls.clearError()

    // ---------------------------------------------------------------- auth

    /**
     * Starts phone sign-in. Returns the challenge, whose id the OTP screen needs.
     *
     * In development the server returns the code in [OtpChallenge.devCode]; the OTP
     * screen shows it rather than making the developer read the server log.
     */
    suspend fun requestOtp(phone: String): OtpChallenge? =
        call { it.requestOtp(normalizePhone(phone)) }

    suspend fun verifyOtp(challengeId: String, code: String): AuthDestination? {
        val repo = repository ?: return AuthDestination.Onboarding
        val session = call { repo.verifyOtp(challengeId, code) } ?: return null
        state.applyServerSession(session.user, session.entitlements)
        return session.user.destination()
    }

    suspend fun signInWithSocial(provider: AuthProvider, idToken: String): AuthDestination? {
        val repo = repository ?: return AuthDestination.Onboarding
        val session = call { repo.signInWithSocial(provider, idToken) } ?: return null
        state.applyServerSession(session.user, session.entitlements)
        return session.user.destination()
    }

    // ---------------------------------------------------------------- profile

    /**
     * Submits the whole onboarding flow.
     *
     * Returns false on failure so the caller keeps the user on the last step instead of
     * dropping her into an app whose server knows nothing about her.
     */
    suspend fun completeOnboarding(): Boolean {
        // Ahead of the request, so Today is already right when the flow hands over —
        // and still right if the app is opened offline before the first sync.
        state.recomputeCycleDay(Clock.System.todayIn(TimeZone.currentSystemDefault()))
        val repo = repository ?: return true
        val request = state.toOnboardingRequest(timezoneOrDefault())
        val profile = call { repo.completeOnboarding(request) } ?: return false
        state.applyServerProfile(profile, repo.state.value.entitlementsOrNull() ?: return true)
        return true
    }

    suspend fun saveProfile(): Boolean {
        val repo = repository ?: return true
        return call { repo.updateProfile(state.toUpdateProfileRequest(timezoneOrDefault())) } != null
    }

    suspend fun loadConsents() {
        val repo = repository ?: return
        call(silent = true) { repo.consents() }?.let { state.applyServerConsents(it) }
    }

    suspend fun saveConsents(): Boolean {
        val repo = repository ?: return true
        val saved = call { repo.updateConsents(state.toConsentGrants()) } ?: return false
        state.applyServerConsents(saved)
        return true
    }

    /** Call after a purchase or on returning from background. Failure is not worth a banner. */
    suspend fun refreshEntitlements() {
        val repo = repository ?: return
        call(silent = true) { repo.refreshEntitlements() }?.let {
            state.isPremium = it.tier == uz.sadora.contract.SubscriptionTier.PREMIUM
        }
    }

    // ---------------------------------------------------------------- account

    suspend fun signOut() {
        calls.run { ApiResult.Success(repository?.signOut()) }
    }

    suspend fun deleteAccount(reason: String?): Boolean {
        val repo = repository ?: return true
        return call { repo.deleteAccount(reason) } != null
    }

    // ---------------------------------------------------------------- plumbing

    /**
     * Runs one call with busy/error bookkeeping, returning the value or null.
     *
     * [silent] is for background refreshes: they still clear busy, but a failure does
     * not raise a banner over whatever the user is currently doing.
     */
    private suspend fun <T> call(
        silent: Boolean = false,
        block: suspend (SadoraRepository) -> ApiResult<T>,
    ): T? {
        val repo = repository ?: return null
        return calls.run(silent) { block(repo) }
    }

    private fun timezoneOrDefault(): String = repository?.deviceTimezone ?: "Asia/Tashkent"

    private fun uz.sadora.contract.UserProfile.destination(): AuthDestination =
        if (onboardingCompleted) AuthDestination.Main else AuthDestination.Onboarding

    private fun SessionState.entitlementsOrNull(): uz.sadora.contract.Entitlements? =
        (this as? SessionState.SignedIn)?.entitlements
}

/**
 * Uzbek text for each failure.
 *
 * Written per case rather than passing the server's message through: the server speaks
 * to several clients and its wording is not always what a phone should show.
 */
fun ApiFailure.readable(): String = when (this) {
    is ApiFailure.Network -> "Internetga ulanib bo'lmadi. Qayta urinib ko'ring."
    is ApiFailure.Validation -> fields.values.firstOrNull() ?: "Kiritilgan ma'lumot noto'g'ri."
    is ApiFailure.Unauthorized -> "Sessiya tugadi. Qaytadan kiring."
    is ApiFailure.Blocked -> "Hisob bloklangan. Qo'llab-quvvatlash bilan bog'laning."
    is ApiFailure.PremiumRequired -> "Bu imkoniyat Premium'da ochiladi."
    is ApiFailure.LimitReached ->
        if (period == "month") "Bu oylik limit tugadi." else "Bugungi limit tugadi."
    is ApiFailure.RateLimited ->
        retryAfterSeconds?.let { "Juda ko'p urinish. $it soniyadan keyin qayta urining." }
            ?: "Juda ko'p urinish. Birozdan keyin qayta urining."
    is ApiFailure.Otp -> message.ifBlank { "Kod noto'g'ri yoki muddati tugagan." }
    is ApiFailure.Unexpected -> "Nimadir noto'g'ri ketdi. Qayta urinib ko'ring."
}

/** `90 123 45 67` as typed in the field becomes `+998901234567` on the wire. */
internal fun normalizePhone(input: String): String {
    val digits = input.filter { it.isDigit() }
    return when {
        digits.startsWith("998") -> "+$digits"
        else -> "+998$digits"
    }
}
