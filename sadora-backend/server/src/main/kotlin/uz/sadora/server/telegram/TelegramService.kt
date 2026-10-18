package uz.sadora.server.telegram

import java.security.SecureRandom
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.Language
import uz.sadora.contract.LogPeriodRequest
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.SaveDailyLogRequest
import uz.sadora.contract.TelegramLink
import uz.sadora.contract.TelegramStatus
import uz.sadora.server.config.TelegramConfig
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.UpstreamUnavailableException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.MedicationRepository
import uz.sadora.server.health.MedicationService
import uz.sadora.server.health.NutritionService
import uz.sadora.server.notify.OutboxRecord
import uz.sadora.server.notify.TARGET_CLIENT
import uz.sadora.server.notify.TARGET_TELEGRAM
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * The Telegram bot: linking her chat, answering its buttons, and sending her reminders there.
 *
 * Everything a button does goes through the same services as the app, so the consent checks
 * and the validation are the app's own. A chat nobody linked is told how to link, and nothing else.
 */
class TelegramService(
    private val config: TelegramConfig,
    private val gateway: TelegramGateway?,
    private val telegram: TelegramRepository,
    private val users: UserRepository,
    private val health: HealthService,
    private val nutrition: NutritionService,
    private val medications: MedicationService,
    private val medicationRecords: MedicationRepository,
) {
    private val random = SecureRandom()

    // ---------------------------------------------------------------- the app's side

    suspend fun status(userId: Uuid): TelegramStatus {
        val link = telegram.linkOf(userId)
        return TelegramStatus(
            available = config.isConfigured,
            linked = link != null,
            username = link?.username,
            detailed = link?.detailed ?: false,
            botUsername = config.username,
        )
    }

    suspend fun createLink(userId: Uuid): TelegramLink {
        if (!config.isConfigured) throw UpstreamUnavailableException("Telegram bot hali ulanmagan")
        val code = buildString { repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        val expiresAt = now() + CODE_TTL
        telegram.addCode(code, userId, expiresAt)
        return TelegramLink("https://t.me/${config.username}?start=$code", expiresAt)
    }

    suspend fun setDetailed(userId: Uuid, detailed: Boolean): TelegramStatus {
        if (!telegram.setDetailed(userId, detailed)) throw NotFoundException("Telegram ulanmagan")
        return status(userId)
    }

    suspend fun unlink(userId: Uuid): TelegramStatus {
        val link = telegram.linkOf(userId)
        if (telegram.unlink(userId) && link != null) {
            val language = users.findById(userId)?.language ?: Language.UZ
            runCatching { gateway?.send(link.chatId, TelegramWords(language).unlinked) }
        }
        return status(userId)
    }

    suspend fun isLinked(userId: Uuid): Boolean = config.isConfigured && telegram.linkOf(userId) != null

    // ---------------------------------------------------------------- the bot's side

    suspend fun handle(update: TgUpdate) {
        val gateway = gateway ?: return
        update.message?.let { message ->
            val chatId = message.chat.id
            if (message.chat.type != "private") return
            val text = message.text?.trim().orEmpty()
            val linked = telegram.linkByChat(chatId)
            val language = linked?.let { users.findById(it.userId)?.language } ?: guessLanguage(message.from?.languageCode)
            val words = TelegramWords(language)
            when {
                text.startsWith("/start ") -> {
                    val userId = telegram.redeem(text.removePrefix("/start ").trim())
                    if (userId == null) {
                        gateway.send(chatId, words.codeExpired)
                    } else {
                        telegram.link(userId, chatId, message.from?.username)
                        val own = TelegramWords(users.findById(userId)?.language ?: language)
                        gateway.send(chatId, own.welcome, menu(own))
                    }
                }
                text == "/stop" && linked != null -> {
                    telegram.unlink(linked.userId)
                    gateway.send(chatId, words.unlinked)
                }
                linked != null -> gateway.send(chatId, words.menu, menu(words))
                else -> gateway.send(chatId, words.notLinked)
            }
        }
        update.callback?.let { callback -> answer(gateway, callback) }
    }

    private suspend fun answer(gateway: TelegramGateway, callback: TgCallback) {
        val chatId = callback.message?.chat?.id ?: callback.from.id
        val link = telegram.linkByChat(chatId)
        val user = link?.let { users.findById(it.userId) }
        if (link == null || user == null) {
            gateway.answer(callback.id, TelegramWords(guessLanguage(callback.from.languageCode)).notLinked)
            return
        }
        val words = TelegramWords(user.language)
        val today = now().dayIn(user.timezone)
        val data = callback.data.orEmpty()
        val reply: String? = runCatching {
            when {
                data == "p" -> {
                    health.addPeriod(user.id, LogPeriodRequest(today))
                    words.periodSaved
                }
                data == "w" -> nutrition.addWater(user.id, AddWaterRequest(WATER_ML)).let { words.waterSaved(it.waterMl, it.waterGoalMl) }
                data.startsWith("m:") -> {
                    val mood = MoodLevel.entries.firstOrNull { it.score == data.removePrefix("m:").toIntOrNull() } ?: return@runCatching null
                    val log = health.log(user.id, today)
                    health.saveLog(
                        user.id,
                        today,
                        SaveDailyLogRequest(log.flow, mood, log.energy, log.stress, log.symptoms, log.note, log.fetalMovement),
                    )
                    words.moodSaved
                }
                data == "d" -> {
                    val pending = medications.day(user.id, today).doses.filter { it.status == DoseStatus.PENDING }
                    if (pending.isEmpty()) {
                        words.noDoses
                    } else {
                        gateway.send(
                            chatId,
                            words.dosesTitle,
                            pending.take(MAX_DOSE_BUTTONS).map { dose ->
                                listOf(TgButton("${dose.emoji ?: "💊"} ${dose.name} — ${clock(dose.dueAt)}", doseData(true, dose.medicationId, dose.dueOn, dose.dueAt)))
                            },
                        )
                        null
                    }
                }
                data.startsWith("dt:") || data.startsWith("ds:") -> {
                    val taken = data.startsWith("dt:")
                    val (medicationId, day, time) = parseDose(data) ?: return@runCatching null
                    medications.recordDose(user.id, medicationId, RecordDoseRequest(day, time, if (taken) DoseStatus.TAKEN else DoseStatus.SKIPPED))
                    val detailed = link.detailed && !user.discreet
                    val name = medicationRecords.byId(user.id, medicationId)?.name
                    val done = if (detailed && name != null) words.doseDone(name, clock(time), taken) else words.neutralDone(taken)
                    callback.message?.let { gateway.edit(chatId, it.messageId, done) }
                    done
                }
                else -> null
            }
        }.getOrElse { words.failed }
        gateway.answer(callback.id, reply)
        if (reply != null && !data.startsWith("d")) gateway.send(chatId, reply)
    }

    /**
     * Her reminder in Telegram as well, when she linked a chat. Messages from people stay in
     * the app: only her own reminders come here. Returns true when Telegram took it.
     */
    suspend fun deliver(record: OutboxRecord): Boolean {
        val gateway = gateway ?: return false
        if (!config.isConfigured) return false
        val link = record.link.orEmpty()
        val reminder = record.targetApp == TARGET_TELEGRAM ||
            (record.targetApp == TARGET_CLIENT && REMINDER_LINKS.any { link.startsWith(it) })
        if (!reminder) return false
        val chat = telegram.linkOf(record.userId) ?: return false
        val user = users.findById(record.userId) ?: return false
        val words = TelegramWords(user.language)
        val detailed = chat.detailed && !user.discreet
        val (text, buttons) = when {
            link.startsWith("sadora://dose/") -> {
                val parts = link.removePrefix("sadora://dose/").split('/')
                val medicationId = parts.getOrNull(0)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                val day = parts.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val time = parts.getOrNull(2)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                val name = medicationId?.let { medicationRecords.byId(user.id, it)?.name }
                val text = if (detailed && name != null && time != null) words.dose(name, clock(time)) else words.reminder
                val buttons = if (medicationId != null && day != null && time != null) {
                    listOf(listOf(TgButton(if (detailed) words.taken else words.done, doseData(true, medicationId.toString(), day, time)), TgButton(words.skipped, doseData(false, medicationId.toString(), day, time))))
                } else {
                    emptyList()
                }
                text to buttons
            }
            link.startsWith("sadora://open/kegel") -> (if (detailed) words.kegel else words.reminder) to emptyList()
            else -> (if (detailed) "${record.title}\n${record.body}" else words.reminder) to emptyList()
        }
        return runCatching { gateway.send(chat.chatId, text, buttons) != null }.getOrDefault(false)
    }

    private fun menu(words: TelegramWords): List<List<TgButton>> = listOf(
        listOf(TgButton(words.period, "p"), TgButton(words.water, "w")),
        MoodLevel.entries.map { TgButton(MOOD_FACES[it.score - 1], "m:${it.score}") },
        listOf(TgButton(words.meds, "d")),
    )

    private fun guessLanguage(code: String?): Language = when (code?.take(2)) {
        "ru" -> Language.RU
        "en" -> Language.EN
        else -> Language.UZ
    }

    companion object {
        const val CODE_LENGTH = 24
        val CODE_TTL = 15.minutes
        const val WATER_ML = 250
        const val MAX_DOSE_BUTTONS = 10
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        private val MOOD_FACES = listOf("😣", "😕", "😐", "🙂", "😄")
        private val REMINDER_LINKS = listOf("sadora://dose/", "sadora://open/kegel", "sadora://baby/")

        private fun clock(time: LocalTime) = "%02d:%02d".format(time.hour, time.minute)

        /** `dt:<uuid>:<yyyymmdd>:<hhmm>` — 52 bytes, under Telegram's 64. */
        fun doseData(taken: Boolean, medicationId: String, day: LocalDate, time: LocalTime): String =
            "${if (taken) "dt" else "ds"}:$medicationId:${day.toString().replace("-", "")}:${"%02d%02d".format(time.hour, time.minute)}"

        fun parseDose(data: String): Triple<Uuid, LocalDate, LocalTime>? = runCatching {
            val parts = data.split(':')
            val day = parts[2]
            Triple(
                Uuid.parse(parts[1]),
                LocalDate(day.substring(0, 4).toInt(), day.substring(4, 6).toInt(), day.substring(6, 8).toInt()),
                LocalTime(parts[3].substring(0, 2).toInt(), parts[3].substring(2, 4).toInt()),
            )
        }.getOrNull()
    }
}
