package uz.sadora.doctor.data

import io.ktor.client.request.setBody
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.UpdateDoctorProfileRequest

/** Her own doctor account under `/doctor`; her public page, as readers see it, under `/doctors`. */
class DoctorApi(private val caller: ApiCaller) {

    suspend fun account(): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/me", HttpMethodKind.GET)

    suspend fun apply(request: DoctorApplicationRequest): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/application", HttpMethodKind.POST) { setBody(request) }

    suspend fun update(request: UpdateDoctorProfileRequest): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/me", HttpMethodKind.PUT) { setBody(request) }

    /** Questions no doctor has answered yet. */
    suspend fun questions(): ApiResult<List<CommunityPost>> =
        caller.authenticated("v1/doctor/questions?limit=50", HttpMethodKind.GET)

    suspend fun profile(id: String): ApiResult<DoctorProfile> =
        caller.authenticated("v1/doctors/$id", HttpMethodKind.GET)
}
