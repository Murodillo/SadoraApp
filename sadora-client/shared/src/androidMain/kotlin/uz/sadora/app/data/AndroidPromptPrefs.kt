package uz.sadora.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Ordinary preferences: a "later" is not a secret. */
class AndroidPromptPrefs(context: Context) : PromptPrefs {

    private val preferences = context.getSharedPreferences("sadora_prompts", Context.MODE_PRIVATE)

    override suspend fun wearableAskAfter(userId: String): LocalDate? = withContext(Dispatchers.IO) {
        preferences.getString(KeyWearable + userId, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    override suspend fun setWearableAskAfter(userId: String, date: LocalDate) = withContext(Dispatchers.IO) {
        preferences.edit().putString(KeyWearable + userId, date.toString()).apply()
    }

    override suspend fun petTeaseAfter(userId: String): LocalDate? = withContext(Dispatchers.IO) {
        preferences.getString(KeyPetTease + userId, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    override suspend fun setPetTeaseAfter(userId: String, date: LocalDate) = withContext(Dispatchers.IO) {
        preferences.edit().putString(KeyPetTease + userId, date.toString()).apply()
    }

    private companion object {
        const val KeyWearable = "wearable_ask_after."
        const val KeyPetTease = "pet_tease_after."
    }
}
