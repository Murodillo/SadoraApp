package uz.sadora.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import uz.sadora.widget.WidgetHost

/**
 * The phone's side: its `/sadora/state` data item carries the watch's session, the
 * snapshot and the API root, or neither when she signed out on the phone.
 */
class PhoneListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == STATE_PATH }.forEach { event ->
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            WidgetHost.get(this).importFromPhone(
                tokensJson = map.getString(KEY_TOKENS),
                snapshotJson = map.getString(KEY_SNAPSHOT),
                baseUrl = map.getString(KEY_BASE_URL).orEmpty(),
            )
        }
        redrawSurfaces(this)
    }

    companion object {
        /** Kept equal to the phone's `WearBridge`. */
        const val STATE_PATH = "/sadora/state"
        const val KEY_TOKENS = "tokens"
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_BASE_URL = "baseUrl"
    }
}
