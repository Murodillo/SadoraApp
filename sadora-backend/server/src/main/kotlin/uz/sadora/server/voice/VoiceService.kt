package uz.sadora.server.voice

import java.util.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.Limits
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.SaveDailyLogRequest
import uz.sadora.contract.SaveVoiceLogRequest
import uz.sadora.contract.SymptomEntry
import uz.sadora.contract.UpdatePeriodRequest
import uz.sadora.contract.VoiceDose
import uz.sadora.contract.VoiceLogDraft
import uz.sadora.contract.VoiceLogRequest
import uz.sadora.contract.VoiceOptions
import uz.sadora.contract.VoicePeriod
import uz.sadora.contract.VoiceSymptom
import uz.sadora.server.ai.AiSource
import uz.sadora.server.ai.AiUsageEntry
import uz.sadora.server.ai.AiUsageRecorder
import uz.sadora.server.ai.ModelUnavailableException
import uz.sadora.server.ai.VoiceChoice
import uz.sadora.server.ai.VoiceHeard
import uz.sadora.server.ai.VoiceListener
import uz.sadora.server.config.AiConfig
import uz.sadora.server.core.UpstreamUnavailableException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.resolveTimeZone
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.MedicationService
import uz.sadora.server.health.NutritionService

/**
 * Logging by voice. [hear] turns a recording into a draft and saves nothing; [save] writes the
 * draft she kept through the same services the app's own screens use.
 */
class VoiceService(
    private val access: HealthAccess,
    private val health: HealthService,
    private val nutrition: NutritionService,
    private val medications: MedicationService,
    private val listener: VoiceListener?,
    private val aiConfig: AiConfig? = null,
    private val usage: AiUsageRecorder? = null,
) {

    suspend fun options(userId: Uuid): VoiceOptions {
        val user = access.requireUser(userId)
        val available = listener != null
        return VoiceOptions(available, if (available) access.feature(userId, FeatureKeys.VOICE_LOG, user.timezone) else null)
    }

    suspend fun hear(userId: Uuid, request: VoiceLogRequest): VoiceLogDraft {
        val user = access.requireWritable(userId, FeatureKeys.VOICE_LOG)
        val model = listener ?: throw UpstreamUnavailableException("Ovoz bilan belgilash hozircha ishlamayapti")
        if (request.mimeType !in MIME_TYPES) throw ValidationException("mimeType", "Bu audio turi qo'llab-quvvatlanmaydi")
        val bytes = runCatching { Base64.getDecoder().decode(request.audioBase64.trim()) }
            .getOrElse { throw ValidationException("audio", "Yozuvni o'qib bo'lmadi") }
        if (bytes.isEmpty()) throw ValidationException("audio", "Yozuvni o'qib bo'lmadi")
        if (bytes.size > Limits.VOICE_MAX_BYTES) throw ValidationException("audio", "Yozuv juda uzun")

        val today = now().dayIn(user.timezone)
        val catalogue = health.symptomCatalogue(userId, lifeStage = null)
        val doses = medications.day(userId, today).doses
        val medicines = doses.distinctBy { it.medicationId }.map { VoiceChoice(it.medicationId, it.name) }

        access.consume(userId, FeatureKeys.VOICE_LOG, user.timezone)
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val answer = try {
            withTimeout(TIMEOUT) {
                model.hear(request.audioBase64, request.mimeType, user.language, catalogue.map { VoiceChoice(it.key, it.label) }, medicines)
            }
        } catch (failure: Exception) {
            if (failure !is ModelUnavailableException && failure !is TimeoutCancellationException) throw failure
            usage?.record(
                AiUsageEntry(
                    userId = userId,
                    source = AiSource.FALLBACK,
                    model = model.name,
                    feature = FeatureKeys.VOICE_LOG,
                    latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                    outcome = "error",
                    errorCode = (failure as? ModelUnavailableException)?.code ?: "timeout",
                ),
            )
            throw UpstreamUnavailableException("Ovozni tushunib bo'lmadi. Qaytadan urinib ko'ring.")
        }
        usage?.record(
            AiUsageEntry(
                userId = userId,
                source = AiSource.MODEL,
                model = answer.model,
                feature = FeatureKeys.VOICE_LOG,
                promptTokens = answer.promptTokens,
                completionTokens = answer.completionTokens,
                costMicros = aiConfig?.costMicros(answer.promptTokens, answer.completionTokens) ?: 0,
                latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                outcome = "ok",
            ),
        )
        val nowTime = now().toLocalDateTime(resolveTimeZone(user.timezone)).time
        return clean(
            answer.heard,
            labels = catalogue.associate { it.key to it.label },
            pending = doses.filter { it.status == DoseStatus.PENDING }.map { Triple(it.medicationId, it.name, it.dueAt) },
            now = nowTime,
        )
    }

    suspend fun save(userId: Uuid, request: SaveVoiceLogRequest) {
        val user = access.requireUser(userId)
        val today = now().dayIn(user.timezone)
        if (request.mood != null || request.symptoms.isNotEmpty()) {
            val log = health.log(userId, today)
            val symptoms = (log.symptoms + request.symptoms.filterNot { key -> log.symptoms.any { it.key == key } }.map { SymptomEntry(it) })
            health.saveLog(
                userId,
                today,
                SaveDailyLogRequest(log.flow, request.mood ?: log.mood, log.energy, log.stress, symptoms, log.note, log.fetalMovement),
            )
        }
        request.waterMl?.takeIf { it in 1..MAX_WATER_ML }?.let { nutrition.addWater(userId, AddWaterRequest(it)) }
        request.doses.forEach { dose ->
            val id = runCatching { Uuid.parse(dose.medicationId) }.getOrElse { throw ValidationException("doses", "Dori topilmadi") }
            medications.recordDose(userId, id, RecordDoseRequest(today, dose.dueAt, DoseStatus.TAKEN))
        }
        when (request.period) {
            VoicePeriod.STARTED -> health.addPeriod(userId, LogPeriodRequest(today))
            VoicePeriod.ENDED -> {
                val open = health.periods(userId).firstOrNull { it.isOngoing }
                    ?: throw ValidationException("period", "Davom etayotgan hayz yo'q")
                health.updatePeriod(userId, Uuid.parse(open.id), UpdatePeriodRequest(endedOn = today))
            }
            null -> Unit
        }
    }

    companion object {
        val MIME_TYPES = setOf("audio/aac", "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3", "audio/ogg", "audio/flac")
        val TIMEOUT = 40.seconds
        const val MAX_WATER_ML = 3000

        /** The model's answer, held to her lists: unknown keys and medicines are dropped. */
        fun clean(heard: VoiceHeard, labels: Map<String, String>, pending: List<Triple<String, String, LocalTime>>, now: LocalTime): VoiceLogDraft {
            val transcript = heard.transcript.trim().take(MAX_TRANSCRIPT)
            if (!heard.understood) return VoiceLogDraft(transcript = transcript, understood = false)
            val doses = heard.medications.distinct().mapNotNull { id ->
                // The dose nearest to now among today's still open ones: "I took it" means that one.
                pending.filter { it.first == id }
                    .minByOrNull { kotlin.math.abs(it.third.toSecondOfDay() - now.toSecondOfDay()) }
                    ?.let { VoiceDose(it.first, it.second, it.third) }
            }
            val draft = VoiceLogDraft(
                transcript = transcript,
                mood = MoodLevel.entries.firstOrNull { it.name.equals(heard.mood, ignoreCase = true) },
                symptoms = heard.symptoms.distinct().mapNotNull { key -> labels[key]?.let { VoiceSymptom(key, it) } },
                waterMl = heard.waterMl?.takeIf { it in 1..MAX_WATER_ML },
                doses = doses,
                period = VoicePeriod.entries.firstOrNull { it.name.equals(heard.period, ignoreCase = true) },
            )
            return if (draft.isEmpty) draft.copy(understood = false) else draft
        }

        private const val MAX_TRANSCRIPT = 600
    }
}
