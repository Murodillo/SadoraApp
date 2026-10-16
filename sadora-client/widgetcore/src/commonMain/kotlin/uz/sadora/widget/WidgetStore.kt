package uz.sadora.widget

import kotlinx.serialization.json.Json

/**
 * A few strings by key, in a place both the app and its widgets can read: an App Group's
 * defaults on iOS and watchOS, a private preferences file on Android.
 */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
    fun keys(): Set<String>
}

class MemoryKeyValues : KeyValues {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
    override fun keys(): Set<String> = values.keys.toSet()
}

/**
 * The snapshot and each widget's settings.
 *
 * The snapshot names her stage and her medicines, so it lives only where the app's own
 * data does — inside the app's sandbox or its App Group — and [clear] runs at sign-out.
 */
class WidgetStore(private val values: KeyValues) {
    /**
     * Held by every [WidgetSync] over this store. On Android the app's sync and the widgets'
     * taps share one store in one process, and two read-modify-writes of the queue at once
     * would lose a tap.
     */
    val lock = kotlinx.coroutines.sync.Mutex()

    fun snapshot(): WidgetSnapshot? =
        values.get(SNAPSHOT)?.let { runCatching { json.decodeFromString(WidgetSnapshot.serializer(), it) }.getOrNull() }

    fun saveSnapshot(snapshot: WidgetSnapshot) =
        values.put(SNAPSHOT, json.encodeToString(WidgetSnapshot.serializer(), snapshot))

    fun config(widgetId: String): WidgetConfig? =
        values.get(CONFIG + widgetId)?.let { runCatching { json.decodeFromString(WidgetConfig.serializer(), it) }.getOrNull() }

    fun putConfig(widgetId: String, config: WidgetConfig) =
        values.put(CONFIG + widgetId, json.encodeToString(WidgetConfig.serializer(), config))

    fun removeConfig(widgetId: String) = values.put(CONFIG + widgetId, null)

    /** The API root the app was built against, so a widget asks the same server. */
    fun baseUrl(): String? = values.get(BASE_URL)

    fun setBaseUrl(url: String) = values.put(BASE_URL, url)

    /** Sign-out: nothing of hers stays where a widget could draw it. */
    fun clear() {
        values.keys().filter { it == SNAPSHOT || it.startsWith(CONFIG) }.forEach { values.put(it, null) }
    }

    companion object {
        private const val SNAPSHOT = "widget.snapshot"
        private const val CONFIG = "widget.config."
        private const val BASE_URL = "widget.baseUrl"

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            classDiscriminator = "kind"
        }
    }
}
