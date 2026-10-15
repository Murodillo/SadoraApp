package uz.sadora.server.widget

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import uz.sadora.server.notify.PushSender

/**
 * Tells her widgets and watch to redraw when something they show has changed elsewhere —
 * a dose marked on the watch, water added on a tablet, a stage changed in Settings.
 *
 * Coalesced per account: a burst of taps is one silent push sent [window] after the
 * first, because iOS rations background wakes and spends them on whoever asks least.
 */
class WidgetRefresher(
    private val tokensOf: suspend (Uuid) -> List<String>,
    private val sender: PushSender,
    private val window: Duration,
    private val scope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(WidgetRefresher::class.java)
    private val pending = ConcurrentHashMap.newKeySet<Uuid>()

    fun changed(userId: Uuid) {
        if (!pending.add(userId)) return
        scope.launch {
            delay(window)
            // Released before sending, so a change made while the push is in flight
            // schedules the next one rather than being folded into this one.
            pending.remove(userId)
            val tokens = tokensOf(userId)
            if (tokens.isEmpty()) return@launch
            runCatching { sender.sendSilent(mapOf("type" to TYPE), tokens) }
                .onFailure { logger.warn("Widget refresh push failed", it) }
        }
    }

    companion object {
        /** The data `type` the apps recognise as "redraw your widgets". */
        const val TYPE = "widget_refresh"
    }
}
