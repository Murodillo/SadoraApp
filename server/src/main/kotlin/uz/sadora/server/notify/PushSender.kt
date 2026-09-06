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
        logger.info(
            "PUSH [{}] to {} device(s): {} — {}",
            record.category,
            pushTokens.size,
            record.title,
            record.body,
        )
        return true
    }
}
