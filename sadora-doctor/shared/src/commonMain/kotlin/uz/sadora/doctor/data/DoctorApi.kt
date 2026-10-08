package uz.sadora.doctor.data

import io.ktor.client.request.setBody
import uz.sadora.contract.BadgeBoard
import uz.sadora.contract.MarkBadgesSeenRequest
import uz.sadora.contract.Ack
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorEarnings
import uz.sadora.contract.DoctorPayoutView
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.EarningLine
import uz.sadora.contract.DoctorSettings
import uz.sadora.contract.DoctorStats
import uz.sadora.contract.Page
import uz.sadora.contract.PatientHistory
import uz.sadora.contract.PatientNote
import uz.sadora.contract.PhotoUpload
import uz.sadora.contract.PhotoView
import uz.sadora.contract.QuickReply
import uz.sadora.contract.SavePatientNoteRequest
import uz.sadora.contract.SaveQuickReplyRequest
import uz.sadora.contract.UpdateDoctorSettingsRequest
import uz.sadora.contract.UpdateDoctorProfileRequest

/** Her own doctor account under `/doctor`; her public page, as readers see it, under `/doctors`. */
class DoctorApi(private val caller: ApiCaller) {

    suspend fun account(): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/me", HttpMethodKind.GET)

    suspend fun apply(request: DoctorApplicationRequest): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/application", HttpMethodKind.POST) { setBody(request) }

    suspend fun update(request: UpdateDoctorProfileRequest): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/me", HttpMethodKind.PUT) { setBody(request) }

    /**
     * Her photo, on her page, in the directory and on every byline. Accepted while the
     * application is still pending too, so the form can ask for it right after it is sent.
     */
    suspend fun uploadPhoto(upload: PhotoUpload): ApiResult<PhotoView> =
        caller.authenticated("v1/doctor/photo", HttpMethodKind.PUT) { setBody(upload) }

    suspend fun deletePhoto(): ApiResult<Ack> =
        caller.authenticated("v1/doctor/photo", HttpMethodKind.DELETE)

    /**
     * A photo's bytes, by the whole URL [photoRequestUrl] made of a `photoUrl` — hers, a
     * patient's through their consultation, or another doctor's.
     */
    suspend fun photo(url: String): ApiResult<ByteArray> =
        caller.authenticated(url, HttpMethodKind.GET)

    /** Questions no doctor has answered yet. */
    suspend fun questions(offset: Int = 0): ApiResult<List<CommunityPost>> =
        caller.authenticated("v1/doctor/questions?limit=$QUESTION_PAGE&offset=$offset", HttpMethodKind.GET)

    suspend fun profile(id: String): ApiResult<DoctorProfile> =
        caller.authenticated("v1/doctors/$id", HttpMethodKind.GET)

    /** Her posts past the few [profile] carries, newest first. */
    suspend fun posts(id: String, limit: Int = CommunityApi.PROFILE_POSTS_PAGE, offset: Int = 0): ApiResult<Page<CommunityPost>> =
        caller.authenticated("v1/doctors/$id/posts?limit=$limit&offset=$offset", HttpMethodKind.GET)

    // ---- her working day: price, hours, the busy switch, her numbers and earnings

    suspend fun settings(): ApiResult<DoctorSettings> =
        caller.authenticated("v1/doctor/settings", HttpMethodKind.GET)

    /** A null field is left as it is; hours replace the whole week. */
    suspend fun updateSettings(request: UpdateDoctorSettingsRequest): ApiResult<DoctorSettings> =
        caller.authenticated("v1/doctor/settings", HttpMethodKind.PUT) { setBody(request) }

    suspend fun stats(): ApiResult<DoctorStats> =
        caller.authenticated("v1/doctor/stats", HttpMethodKind.GET)

    /** The totals over everything, and the first page of her consultations and of her payouts. */
    suspend fun earnings(): ApiResult<DoctorEarnings> =
        caller.authenticated("v1/doctor/earnings", HttpMethodKind.GET)

    suspend fun earningLines(offset: Int): ApiResult<Page<EarningLine>> =
        caller.authenticated("v1/doctor/earnings/lines?limit=${DoctorEarnings.PAGE}&offset=$offset", HttpMethodKind.GET)

    suspend fun payouts(offset: Int): ApiResult<Page<DoctorPayoutView>> =
        caller.authenticated("v1/doctor/earnings/payouts?limit=${DoctorEarnings.PAGE}&offset=$offset", HttpMethodKind.GET)

    // ---- her badges

    /** Her board; reading it is what awards a tier newly crossed. */
    suspend fun badges(): ApiResult<BadgeBoard> =
        caller.authenticated("v1/doctor/badges", HttpMethodKind.GET)

    /** These unlocks have been played; an empty list is every one still unseen. */
    suspend fun badgesSeen(keys: List<String>): ApiResult<Ack> =
        caller.authenticated("v1/doctor/badges/seen", HttpMethodKind.POST) { setBody(MarkBadgesSeenRequest(keys)) }

    // ---- her quick replies

    suspend fun quickReplies(): ApiResult<List<QuickReply>> =
        caller.authenticated("v1/doctor/quick-replies", HttpMethodKind.GET)

    suspend fun addQuickReply(request: SaveQuickReplyRequest): ApiResult<QuickReply> =
        caller.authenticated("v1/doctor/quick-replies", HttpMethodKind.POST) { setBody(request) }

    suspend fun updateQuickReply(id: String, request: SaveQuickReplyRequest): ApiResult<QuickReply> =
        caller.authenticated("v1/doctor/quick-replies/$id", HttpMethodKind.PUT) { setBody(request) }

    suspend fun deleteQuickReply(id: String): ApiResult<Ack> =
        caller.authenticated("v1/doctor/quick-replies/$id", HttpMethodKind.DELETE)

    // ---- a patient, by the consultation she is in

    /** Her private note: never shown to the patient or to staff. */
    suspend fun note(conversationId: String): ApiResult<PatientNote> =
        caller.authenticated("v1/doctor/patients/$conversationId/note", HttpMethodKind.GET)

    /** An empty body deletes the note. */
    suspend fun saveNote(conversationId: String, body: String): ApiResult<PatientNote> =
        caller.authenticated("v1/doctor/patients/$conversationId/note", HttpMethodKind.PUT) {
            setBody(SavePatientNoteRequest(body))
        }

    /** Every window she has had with this patient, oldest first. */
    suspend fun history(conversationId: String): ApiResult<PatientHistory> =
        caller.authenticated("v1/doctor/patients/$conversationId/history", HttpMethodKind.GET)

    companion object {
        /** The work list's page; a page shorter than this is its last. */
        const val QUESTION_PAGE = 50
    }
}
