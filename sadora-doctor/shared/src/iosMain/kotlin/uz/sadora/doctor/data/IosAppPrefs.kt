package uz.sadora.doctor.data

import platform.Foundation.NSUserDefaults

/** `NSUserDefaults`: nothing here is secret, and the token lives in the Keychain. */
class IosAppPrefs : AppPrefs {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun readLanguage(): String? = defaults.stringForKey(KEY_LANGUAGE)

    override fun writeLanguage(code: String) {
        defaults.setObject(code, forKey = KEY_LANGUAGE)
    }

    private companion object {
        const val KEY_LANGUAGE = "sadora.doctor.language"
    }
}
