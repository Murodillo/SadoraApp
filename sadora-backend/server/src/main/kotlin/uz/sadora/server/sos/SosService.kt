package uz.sadora.server.sos

import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import uz.sadora.contract.Language
import uz.sadora.contract.Limits
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.SaveSosContactsRequest
import uz.sadora.contract.SosAlertRequest
import uz.sadora.contract.SosAlertResult
import uz.sadora.contract.SosContact
import uz.sadora.contract.SosState
import uz.sadora.contract.UzbekPhone
import uz.sadora.server.auth.PhoneNumbers
import uz.sadora.server.auth.TextMessageSender
import uz.sadora.server.core.RateLimitedException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.partner.PartnerRepository
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * SOS: her contacts, and the alert. The alert goes out to everyone at once — SMS to each
 * contact and to Yaqinim's phone, a push to Yaqinim's app — and one failure never holds up
 * the rest. Nothing here calls 103: the phone does that itself, before anything else.
 */
class SosService(
    private val sos: SosRepository,
    private val users: UserRepository,
    private val partners: PartnerRepository,
    private val notifications: NotificationRepository,
    private val sms: TextMessageSender,
    /** Sends a queued push now rather than at the next tick. */
    private val deliverSoon: (Uuid) -> Unit = {},
) {
    private val logger = LoggerFactory.getLogger(SosService::class.java)

    suspend fun state(userId: Uuid): SosState {
        users.findById(userId) ?: throw ValidationException("user", "Foydalanuvchi topilmadi")
        return SosState(contacts = sos.contacts(userId), partnerName = partnerOf(userId)?.name)
    }

    suspend fun saveContacts(userId: Uuid, request: SaveSosContactsRequest): SosState {
        if (request.contacts.size > Limits.SOS_MAX_CONTACTS) {
            throw ValidationException("contacts", "Ko'pi bilan ${Limits.SOS_MAX_CONTACTS} ta kontakt")
        }
        val cleaned = request.contacts.map { contact ->
            val name = contact.name.trim()
            if (name.isEmpty() || name.length > MAX_NAME) throw ValidationException("name", "Ismni yozing")
            val phone = UzbekPhone.toE164(contact.phone) ?: throw ValidationException("phone", "Telefon raqami noto'g'ri")
            SosContact(name, phone)
        }.distinctBy { it.phone }
        sos.replaceContacts(userId, cleaned)
        return state(userId)
    }

    suspend fun alert(userId: Uuid, request: SosAlertRequest): SosAlertResult {
        val user = users.findById(userId) ?: throw ValidationException("user", "Foydalanuvchi topilmadi")
        if (sos.alertsSince(userId, now() - 1.hours) >= Limits.SOS_ALERTS_PER_HOUR) {
            throw RateLimitedException("Signal juda ko'p yuborildi. 103 ga qo'ng'iroq qiling.")
        }
        val location = location(request)
        val partner = partnerOf(userId)
        val phones = (sos.contacts(userId).map { it.phone } + listOfNotNull(partner?.phone)).distinct()
            .filter { it != user.phone }
        val text = SosWords.sms(user, location)

        val results = coroutineScope {
            phones.map { phone ->
                async {
                    runCatching { sms.sendText(phone, text) }
                        .onFailure { logger.warn("SOS SMS to {} failed", PhoneNumbers.mask(phone), it) }
                        .isSuccess
                }
            }.awaitAll()
        }
        val pushed = partner?.let { notifyPartner(user, it, location) } ?: false
        val sent = results.count { it }
        sos.addAlert(userId, location?.first, location?.second, sent, results.size - sent)
        logger.warn("SOS alert from {}: {} SMS sent, {} failed, partner pushed {}", userId, sent, results.size - sent, pushed)
        return SosAlertResult(sent, results.size - sent, pushed, location != null)
    }

    private suspend fun notifyPartner(user: UserRecord, partner: UserRecord, location: Pair<Double, Double>?): Boolean =
        runCatching {
            val queued = notifications.enqueue(
                userId = partner.id,
                // SYSTEM: never capped, never held for quiet hours.
                category = NotificationCategory.SYSTEM,
                title = SosWords.pushTitle(user.name, partner.language),
                body = SosWords.pushBody(location, user.phone, partner.language),
                scheduledFor = now(),
                dedupeKey = "sos:${user.id}:${now().toEpochMilliseconds()}",
                status = NotificationStatus.QUEUED,
                suppressedReason = null,
                link = "sadora://yaqinim",
            )
            if (queued) deliverSoon(partner.id)
            queued
        }.getOrDefault(false)

    private suspend fun partnerOf(userId: Uuid): UserRecord? =
        partners.activeOf(userId)?.takeIf { it.pausedAt == null }?.partnerId?.let { users.findById(it) }

    private fun location(request: SosAlertRequest): Pair<Double, Double>? {
        val lat = request.latitude ?: return null
        val lon = request.longitude ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    private companion object {
        const val MAX_NAME = 40
    }
}

/** The alert's words: short, plain, and the same in every SMS so nobody has to decode it. */
object SosWords {
    fun map(location: Pair<Double, Double>) =
        "https://maps.google.com/?q=${"%.5f".format(location.first)},${"%.5f".format(location.second)}"

    fun sms(user: UserRecord, location: Pair<Double, Double>?): String {
        val name = user.name.ifBlank { "SADORA" }
        val phone = user.phone?.let { " Tel: $it" }.orEmpty()
        return when (user.language) {
            Language.RU -> "SADORA SOS: $name просит о помощи! " +
                (location?.let { "Где она: ${map(it)}" } ?: "Место не определено.") + phone
            Language.EN -> "SADORA SOS: $name needs help! " +
                (location?.let { "Location: ${map(it)}" } ?: "Location unknown.") + phone
            Language.UZ -> "SADORA SOS: $name yordam so'rayapti! " +
                (location?.let { "Joylashuv: ${map(it)}" } ?: "Joylashuv aniqlanmadi.") + phone
        }
    }

    fun pushTitle(name: String, language: Language) = when (language) {
        Language.UZ -> "🆘 $name yordam so'rayapti"
        Language.RU -> "🆘 $name просит о помощи"
        Language.EN -> "🆘 $name needs help"
    }

    fun pushBody(location: Pair<Double, Double>?, phone: String?, language: Language): String {
        val place = location?.let(::map)
        val call = phone?.let { " $it" }.orEmpty()
        return when (language) {
            Language.UZ -> (place?.let { "Joylashuv: $it." } ?: "Joylashuv aniqlanmadi.") + " Qo'ng'iroq qiling:$call"
            Language.RU -> (place?.let { "Где она: $it." } ?: "Место не определено.") + " Позвоните:$call"
            Language.EN -> (place?.let { "Location: $it." } ?: "Location unknown.") + " Call her:$call"
        }
    }
}
