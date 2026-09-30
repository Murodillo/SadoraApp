package uz.sadora.doctor.data

import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Language

/**
 * What a patient's QR code opens, read as data rather than as the web page it links to.
 *
 * The link is hers — random, expiring, revocable from her phone — and the route behind it
 * is public, so the doctor's token adds nothing here but is harmless to send. An expired,
 * revoked or mistyped token answers 404, which arrives as [ApiFailure.NotFound].
 */
class PatientApi(private val caller: ApiCaller) {

    suspend fun record(token: String, language: Language): ApiResult<DoctorSummary> =
        caller.authenticated("share/$token/json?lang=${language.name.lowercase()}", HttpMethodKind.GET)
}

/**
 * The share token inside whatever the camera read, or null when it is not a Sadora
 * patient link. The QR code encodes `<server>/share/<token>`; the server's host differs
 * between production, staging and a developer's tunnel, so only the path is checked —
 * the token is then sent to this app's own server, never to the host in the code.
 */
fun patientTokenOf(scanned: String): String? {
    val text = scanned.trim()
    val marker = text.indexOf("/share/")
    val raw = if (marker >= 0) text.substring(marker + "/share/".length) else text
    val token = raw.substringBefore('?').substringBefore('#').substringBefore('/')
    return token.takeIf { it.length in TokenLength && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
}

/** A share token is 32 random bytes in URL-safe base64: 43 characters. Some slack either way. */
private val TokenLength = 20..128
