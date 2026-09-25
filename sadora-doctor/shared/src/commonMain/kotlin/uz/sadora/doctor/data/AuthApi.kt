package uz.sadora.doctor.data

import io.ktor.client.request.setBody
import uz.sadora.contract.Ack
import uz.sadora.contract.AuthSession
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.Language
import uz.sadora.contract.LogoutRequest
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.OtpRequest
import uz.sadora.contract.OtpVerifyRequest

/**
 * Signing in and out: the same phone-and-code exchange the client app uses, against the
 * same accounts. A doctor is an ordinary account with a doctor record beside it.
 */
class AuthApi internal constructor(
    private val caller: ApiCaller,
    private val session: SessionStore,
) {
    /** [language] is the language of the SMS she receives. */
    suspend fun requestOtp(phone: String, language: Language): ApiResult<OtpChallenge> =
        caller.unauthenticated("v1/auth/otp/request") { setBody(OtpRequest(phone = phone, language = language)) }

    suspend fun verifyOtp(
        challengeId: String,
        code: String,
        device: DeviceInfo,
    ): ApiResult<AuthSession> = caller.unauthenticated("v1/auth/otp/verify") {
        setBody(OtpVerifyRequest(challengeId = challengeId, code = code, device = device))
    }

    suspend fun refreshSession(): ApiResult<AuthSession> = caller.refreshSession()

    suspend fun logout(allDevices: Boolean = false): ApiResult<Ack> {
        val refreshToken = session.currentRefreshToken()
        return caller.authenticated("v1/auth/logout", HttpMethodKind.POST) {
            setBody(LogoutRequest(refreshToken, allDevices))
        }
    }
}
