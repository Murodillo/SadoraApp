package uz.sadora.widget

import android.content.Context
import androidx.core.content.edit

/**
 * A private preferences file. The widgets run in the app's own process on Android, so the
 * app's sandbox is the shared place; nothing here is readable by another app.
 */
class PrefsKeyValues(context: Context, name: String = FILE) : KeyValues {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String?) = prefs.edit {
        if (value == null) remove(key) else putString(key, value)
    }

    override fun keys(): Set<String> = prefs.all.keys

    companion object {
        const val FILE = "sadora_widgets"
    }
}
