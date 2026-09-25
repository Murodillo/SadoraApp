package uz.sadora.doctor.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.contract.Language
import uz.sadora.contract.OtpChallenge
import uz.sadora.contract.UzbekPhone

/**
 * The sign-in screen's state: the number, the challenge the server issued for it, and
 * the code being typed.
 *
 * In development the server returns the code in [OtpChallenge.devCode]; it is put in
 * the field, as the client app does, rather than making the developer read the server log.
 */
class AuthController(private val repository: AuthRepository?) {

    val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    /** Nine national digits at most; the field draws them as `90 123 45 67`. */
    var phone by mutableStateOf("")
        private set

    /** Null on the phone step; set once a code has been sent. */
    var challenge by mutableStateOf<OtpChallenge?>(null)
        private set

    var code by mutableStateOf("")
        private set

    val phoneReady: Boolean get() = UzbekPhone.isValid(phone)
    val codeReady: Boolean get() = code.length == CODE_LENGTH

    fun updatePhone(raw: String) {
        phone = UzbekPhone.accept(raw)
        clearError()
    }

    fun updateCode(raw: String) {
        code = raw.filter { it.isDigit() }.take(CODE_LENGTH)
        clearError()
    }

    /** Sends a code to [phone]; true once the server has issued a challenge. */
    suspend fun requestCode(language: Language): Boolean {
        val repo = repository ?: return false
        val issued = calls.run { repo.requestOtp(normalizePhone(phone), language) } ?: return false
        challenge = issued
        code = issued.devCode.orEmpty()
        return true
    }

    /** True once the code was accepted and the session stored. */
    suspend fun verify(): Boolean {
        val repo = repository ?: return false
        val challengeId = challenge?.challengeId ?: return false
        return calls.run { repo.verifyOtp(challengeId, code) } != null
    }

    /** Back to the phone step, keeping the number she typed. */
    fun changeNumber() {
        challenge = null
        code = ""
        clearError()
    }

    suspend fun signOut() {
        repository?.signOut()
        reset()
    }

    fun reset() {
        phone = ""
        challenge = null
        code = ""
        clearError()
    }

    companion object {
        const val CODE_LENGTH = 6
    }
}

/**
 * `90 123 45 67` as typed in the field becomes `+998901234567` on the wire.
 *
 * An unparseable number is sent as its digits rather than guessed at: the field will
 * not let one through, and if one somehow does, the server should say which field is
 * wrong rather than the app inventing a country code.
 */
fun normalizePhone(input: String): String =
    UzbekPhone.toE164(input) ?: input.filter { it.isDigit() }
