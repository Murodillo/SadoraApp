package uz.sadora.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import uz.sadora.widget.WidgetHost

/**
 * The phone's side: `/sadora/session` carries the watch's session (or a sign-out),
 * `/sadora/state` the latest snapshot. Two items, so a new snapshot never replaces the
 * one that carried the session before the watch saw it.
 */
class PhoneListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val host = WidgetHost.get(this)
        events.filter { it.type == DataEvent.TYPE_CHANGED }.forEach { event ->
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val baseUrl = map.getString(KEY_BASE_URL).orEmpty()
            when (event.dataItem.uri.path) {
                SESSION_PATH -> if (map.getBoolean(KEY_SIGN_OUT, false)) {
                    host.importFromPhone(null, null, "")
                } else {
                    map.getString(KEY_TOKENS)?.let { host.importFromPhone(it, null, baseUrl) }
                }
                STATE_PATH -> map.getString(KEY_SNAPSHOT)?.let { host.importFromPhone(null, it, baseUrl) }
            }
        }
        redrawSurfaces(this)
    }

    companion object {
        /** Kept equal to the phone's `AndroidWidgetPlatform`. */
        const val SESSION_PATH = "/sadora/session"
        const val STATE_PATH = "/sadora/state"
        const val KEY_TOKENS = "tokens"
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_SIGN_OUT = "signOut"
    }
}
