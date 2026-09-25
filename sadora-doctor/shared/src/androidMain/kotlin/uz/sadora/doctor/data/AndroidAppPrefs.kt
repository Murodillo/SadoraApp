package uz.sadora.doctor.data

import android.content.Context

/** Plain preferences: nothing here is secret, and the token lives elsewhere. */
class AndroidAppPrefs(context: Context) : AppPrefs {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readLanguage(): String? = preferences.getString(KEY_LANGUAGE, null)

    override fun writeLanguage(code: String) {
        preferences.edit().putString(KEY_LANGUAGE, code).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "sadora.doctor.prefs"
        const val KEY_LANGUAGE = "language"
    }
}
