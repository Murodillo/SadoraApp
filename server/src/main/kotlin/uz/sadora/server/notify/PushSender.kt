package uz.sadora.server.notify

import org.slf4j.LoggerFactory

/**
 * Delivery to the device.
 *
 * Behind an interface because the push credentials are an open question — the same shape
 * as the SMS sender. Swapping [LoggingPushSender] for FCM and APNs is one class.
 */
interface PushSender {
    /** Returns false when delivery failed and the row should be marked failed. */
    suspend fun send(record: OutboxRecord, pushTokens: List<String>): Boolean
}

class LoggingPushSender : PushSender {
    private val logger = LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(record: OutboxRecord, pushTokens: List<String>): Boolean {
        // The category only: a title and body are a medicine's name or a line of a DM.
        logger.info("PUSH [{}] to {} device(s), not sent: FCM is not configured", record.category, pushTokens.size)
        return true
    }
}
