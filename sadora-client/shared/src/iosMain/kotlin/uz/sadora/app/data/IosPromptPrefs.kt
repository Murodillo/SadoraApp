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

    private companion object {
        const val KeyWearable = "sadora.prompts.wearableAskAfter."
    }
}
