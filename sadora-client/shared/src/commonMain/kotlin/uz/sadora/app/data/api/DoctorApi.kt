package uz.sadora.app.data.api

import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorProfile

/** The doctor calls the client app makes; the doctor's own ones are in sadora-doctor. */
class DoctorApi(private val caller: ApiCaller) {

    /** Her own doctor status — only "approved" changes anything here. */
    suspend fun account(): ApiResult<DoctorAccount> =
        caller.authenticated("v1/doctor/me", HttpMethodKind.GET)

    suspend fun profile(id: String): ApiResult<DoctorProfile> =
        caller.authenticated("v1/doctors/$id", HttpMethodKind.GET)
}
