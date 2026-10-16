package uz.sadora.server.notify

import org.slf4j.LoggerFactory

/**
 * Delivery to the device.
 *
 * Behind an interface because the push credentials are an open question — the same shape
 * as the SMS sender. Swapping [LoggingPushSender] for FCM and APNs is one class.
 */
interface PushSender {
    /**
     * Returns false when delivery failed and the row should be marked failed.
     * [drawsDoseActions] are the tokens whose app draws a dose reminder itself, with its
     * buttons (`DeviceFeatures.DOSE_ACTIONS`); the rest get an ordinary notification.
     */
    suspend fun send(record: OutboxRecord, pushTokens: List<String>, drawsDoseActions: Set<String> = emptySet()): Boolean

    /**
     * A message that shows nothing and only wakes the app — a widget's "redraw now".
     * Nothing is queued or retried for it: a lost one is caught up by the widget's own
     * periodic refresh.
     */
    suspend fun sendSilent(data: Map<String, String>, pushTokens: List<String>): Boolean
}

class LoggingPushSender : PushSender {
    private val logger = LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(record: OutboxRecord, pushTokens: List<String>, drawsDoseActions: Set<String>): Boolean {
        // The category only: a title and body are a medicine's name or a line of a DM.
        logger.info("PUSH [{}] to {} device(s), not sent: FCM is not configured", record.category, pushTokens.size)
        return true
    }

    override suspend fun sendSilent(data: Map<String, String>, pushTokens: List<String>): Boolean {
        logger.debug("SILENT PUSH {} to {} device(s), not sent", data["type"], pushTokens.size)
        return true
    }
}
