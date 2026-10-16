package uz.sadora.widget

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.TokenPair

/**
 * What every host does with its store, session and sync — the iOS and watchOS hosts and
 * the Android one (phone widgets and Wear OS) — written once.
 */
class WidgetOps(
    private val store: WidgetStore,
    private val tokens: CompanionTokens,
    private val sync: WidgetSync,
    private val today: () -> LocalDate,
) {
    /** A water button: queued and drawn at once, then sent. */
    suspend fun tapWater(ml: Int): WidgetSync.Outcome {
        sync.addWater(ml, today())
        return sync.refresh()
    }

    /** A dose button or a notification's answer; [day] and [time] as the link carries them. */
    suspend fun tapDose(medicationId: String, day: String, time: String, taken: Boolean): WidgetSync.Outcome {
        sync.markDose(
            medicationId,
            LocalDate.parse(day),
            LocalTime.parse(time),
            if (taken) DoseStatus.TAKEN else DoseStatus.SKIPPED,
        )
        return sync.refresh()
    }

    /**
     * What the phone sent the watch. Both JSONs null is sign-out; a null [tokensJson] alone
     * keeps the session the watch already has.
     */
    fun importFromPhone(tokensJson: String?, snapshotJson: String?, baseUrl: String) {
        if (tokensJson == null && snapshotJson == null) {
            store.clear()
            tokens.clear()
            return
        }
        if (baseUrl.isNotBlank()) store.setBaseUrl(baseUrl)
        tokensJson?.let {
            val pair = WidgetStore.json.decodeFromString(TokenPair.serializer(), it)
            tokens.save(pair.accessToken, pair.refreshToken)
        }
        snapshotJson?.let {
            val incoming = WidgetStore.json.decodeFromString(WidgetSnapshot.serializer(), it)
            // Taps made on the watch and not yet sent stay queued on top of the phone's picture.
            store.saveSnapshot(incoming.copy(pending = store.snapshot()?.pending.orEmpty()))
        }
    }
}
