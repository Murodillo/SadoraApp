package uz.sadora.server.notify

import kotlin.uuid.Uuid
import uz.sadora.contract.FrequencyCaps
import uz.sadora.contract.NotificationMessage
import uz.sadora.contract.NotificationSettings
import uz.sadora.contract.UpdateNotificationSettingsRequest
import uz.sadora.server.core.ValidationException

class NotificationService(private val repository: NotificationRepository) {

    suspend fun settings(userId: Uuid): NotificationSettings = repository.settingsOf(userId)

    suspend fun updateSettings(
        userId: Uuid,
        request: UpdateNotificationSettingsRequest,
    ): NotificationSettings {
        val current = repository.settingsOf(userId)
        val quietFrom = if (request.clearQuietHours) null else request.quietFrom ?: current.quietFrom
        val quietUntil = if (request.clearQuietHours) null else request.quietUntil ?: current.quietUntil

        // Half a window would silence either nothing or everything depending on which
        // half survived, so both ends are required together.
        if ((quietFrom == null) != (quietUntil == null)) {
            throw ValidationException("quietFrom", "Sokin vaqtning ikkala chegarasi ham kerak")
        }

        val updated = NotificationSettings(
            enabled = request.enabled ?: current.enabled,
            categories = request.categories ?: current.categories,
            quietFrom = quietFrom,
            quietUntil = quietUntil,
        )
        repository.saveSettings(userId, updated)
        return updated
    }

    suspend fun history(userId: Uuid, limit: Int): List<NotificationMessage> =
        repository.history(userId, limit.coerceIn(1, 200))

    suspend fun caps(): FrequencyCaps = repository.caps()

    suspend fun updateCaps(caps: FrequencyCaps): FrequencyCaps {
        if (caps.maxPerDay !in 0..50) throw ValidationException("maxPerDay", "0–50 oralig'ida")
        if (caps.maxPerWeek !in 0..300) throw ValidationException("maxPerWeek", "0–300 oralig'ida")
        if (caps.maxPerWeek < caps.maxPerDay) {
            throw ValidationException("maxPerWeek", "Kunlik chegaradan kichik bo'lishi mumkin emas")
        }
        repository.saveCaps(caps)
        return caps
    }

    suspend fun templates(): List<TemplateRecord> = repository.templates()

    suspend fun saveTemplate(record: TemplateRecord): TemplateRecord {
        if (record.title.isBlank()) throw ValidationException("title", "Bo'sh bo'lishi mumkin emas")
        if (record.body.isBlank()) throw ValidationException("body", "Bo'sh bo'lishi mumkin emas")
        repository.saveTemplate(record)
        return record
    }
}
