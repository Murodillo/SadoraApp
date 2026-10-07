package uz.sadora.app.data

import kotlinx.datetime.LocalDate

/**
 * When the app may next ask one of its occasional questions, per account.
 *
 * Only the "later" lives here: an answer is saved on the profile, where every phone
 * sees it. A "later" is a promise about this phone's timing and nothing else, so plain
 * preferences are enough, and a reinstall that forgets it only means one early ask.
 */
interface PromptPrefs {
    suspend fun wearableAskAfter(userId: String): LocalDate?
    suspend fun setWearableAskAfter(userId: String, date: LocalDate)

    /** When a free account may next see the pet asleep; null before the first glimpse is booked. */
    suspend fun petTeaseAfter(userId: String): LocalDate?
    suspend fun setPetTeaseAfter(userId: String, date: LocalDate)

    /** In memory: tests, previews, and a build with no platform store. */
    class InMemory : PromptPrefs {
        private val dates = mutableMapOf<String, LocalDate>()
        private val teases = mutableMapOf<String, LocalDate>()
        override suspend fun wearableAskAfter(userId: String): LocalDate? = dates[userId]
        override suspend fun setWearableAskAfter(userId: String, date: LocalDate) {
            dates[userId] = date
        }
        override suspend fun petTeaseAfter(userId: String): LocalDate? = teases[userId]
        override suspend fun setPetTeaseAfter(userId: String, date: LocalDate) {
            teases[userId] = date
        }
    }
}
