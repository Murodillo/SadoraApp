package uz.sadora.app.data

import kotlinx.datetime.LocalDate
import platform.Foundation.NSUserDefaults

/** `NSUserDefaults`: a "later" is not a secret, so it stays out of the keychain. */
class IosPromptPrefs : PromptPrefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun wearableAskAfter(userId: String): LocalDate? =
        defaults.stringForKey(KeyWearable + userId)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setWearableAskAfter(userId: String, date: LocalDate) {
        defaults.setObject(date.toString(), KeyWearable + userId)
    }

    override suspend fun petTeaseAfter(userId: String): LocalDate? =
        defaults.stringForKey(KeyPetTease + userId)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setPetTeaseAfter(userId: String, date: LocalDate) {
        defaults.setObject(date.toString(), KeyPetTease + userId)
    }

    private companion object {
        const val KeyWearable = "sadora.prompts.wearableAskAfter."
        const val KeyPetTease = "sadora.prompts.petTeaseAfter."
    }
}
