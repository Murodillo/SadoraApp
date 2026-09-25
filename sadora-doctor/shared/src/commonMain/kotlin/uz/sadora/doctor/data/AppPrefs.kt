package uz.sadora.doctor.data

/**
 * The phone's own settings — not the account's, and nothing secret: which language the
 * app is in. Plain preferences on Android, `NSUserDefaults` on iOS.
 */
interface AppPrefs {
    /** The language code last chosen (`uz`, `ru`, `en`), or null before she chose one. */
    fun readLanguage(): String?
    fun writeLanguage(code: String)
}

/** For tests and previews; forgets everything with the process. */
class InMemoryAppPrefs(private var language: String? = null) : AppPrefs {
    override fun readLanguage(): String? = language
    override fun writeLanguage(code: String) { language = code }
}
