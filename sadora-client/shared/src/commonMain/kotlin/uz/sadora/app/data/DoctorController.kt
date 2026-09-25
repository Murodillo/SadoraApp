package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.DoctorApi
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityPost
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorStatus

/**
 * Doctors as the chat shows them: a verified doctor's public page, and whether she —
 * the woman holding this phone — is one herself.
 *
 * Applying, the panel and answering questions live in the doctor's own app
 * (sadora-doctor). An approved doctor who also uses this app still writes under her
 * name — the server decides that by account — so her approved name is mirrored onto
 * [AppState.doctorName] for the composer and her optimistic comments to say so.
 */
class DoctorController(
    private val api: DoctorApi?,
    private val state: AppState,
) {
    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    /** The doctor page on screen, replaced on every open. */
    var profile by mutableStateOf<DoctorProfile?>(null)
        private set
    val profilePosts: List<CommunityPost> get() = profile?.posts.orEmpty().map { it.toAppPost() }

    /** Whether she is an approved doctor; quiet, since most accounts are not. */
    suspend fun loadAccount() {
        val api = api ?: return
        calls.run(silent = true) { api.account() }?.let { account ->
            state.doctorName = account.fullName.takeIf { account.status == DoctorStatus.APPROVED }
        }
    }

    suspend fun loadProfile(id: String) {
        val api = api ?: return
        if (profile?.id != id) profile = null
        calls.run(silent = profile != null) { api.profile(id) }?.let { profile = it }
    }
}
