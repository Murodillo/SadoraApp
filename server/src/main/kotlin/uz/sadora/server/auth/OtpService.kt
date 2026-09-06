package uz.sadora.server.auth

import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.OtpChallenge
import uz.sadora.server.cache.Cache
import uz.sadora.server.config.OtpConfig
import uz.sadora.server.core.RateLimitedException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.randomNumericCode
import uz.sadora.server.core.sha256
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.OtpChallenges
import uz.sadora.server.db.dbQuery

/**
 * Phone sign-in codes.
 *
 * Three defences, because SMS costs money and an OTP endpoint is the cheapest thing on
 * the internet to abuse: a per-phone hourly send cap, a per-challenge attempt cap, and a
 * short expiry. The code is stored only as a hash and is consumed on first success.
 */
class OtpService(
    private val config: OtpConfig,
    private val cache: Cache,
    private val sender: OtpSender,
) {
    private val logger = LoggerFactory.getLogger(OtpService::class.java)

    suspend fun request(rawPhone: String, requestIp: String?): OtpChallenge {
        val phone = PhoneNumbers.normalize(rawPhone)

        val sendsThisHour = cache.increment("otp:sends:$phone", 1.hours)
        if (sendsThisHour > config.maxPerPhonePerHour) {
            logger.warn("OTP send cap hit for {}", PhoneNumbers.mask(phone))
            throw RateLimitedException(
                "Juda ko'p kod so'raldi. Birozdan keyin urinib ko'ring.",
                retryAfterSeconds = 3600,
            )
        }

        // A fixed code is a dev convenience so a tester on a real phone can type the
        // same digits every time; AppConfig refuses it outside development.
        val code = config.fixedCode ?: randomNumericCode(config.codeLength)
        val challengeId = Uuid.random()
        val expiresAt = now() + config.ttl

        dbQuery {
            OtpChallenges.insert {
                it[id] = challengeId
                it[OtpChallenges.phone] = phone
                it[codeHash] = sha256(code)
                it[purpose] = "sign_in"
                it[attempts] = 0
                it[maxAttempts] = config.maxAttempts
                it[OtpChallenges.expiresAt] = expiresAt.toOffsetDateTime()
                it[createdAt] = now().toOffsetDateTime()
                it[OtpChallenges.requestIp] = requestIp
            }
        }

        sender.send(phone, code)

        return OtpChallenge(
            challengeId = challengeId.toString(),
            expiresAt = expiresAt,
            resendAfterSeconds = config.resendAfter.inWholeSeconds.toInt(),
            attemptsLeft = config.maxAttempts,
            // Only ever populated in dev and stage; AppConfig refuses it in production.
            devCode = code.takeIf { config.exposeCode },
        )
    }

    /**
     * Returns the phone number the challenge was issued for, and marks it spent.
     *
     * A wrong code costs an attempt whether or not the challenge exists, so probing for
     * valid challenge ids gains nothing.
     */
    suspend fun verify(challengeId: String, code: String): String {
        val id = runCatching { Uuid.parse(challengeId) }.getOrNull()
            ?: throw ValidationException("challengeId", "Noto'g'ri format")

        val challenge = dbQuery {
            OtpChallenges.selectAll().where { OtpChallenges.id eq id }.singleOrNull()
        } ?: throw ValidationException("challengeId", "Kod topilmadi")

        val expiresAt = challenge[OtpChallenges.expiresAt].toKotlinInstant()
        if (challenge[OtpChallenges.consumedAt] != null) {
            throw ApiOtpException(ErrorCodes.OTP_EXPIRED, "Bu kod allaqachon ishlatilgan")
        }
        if (expiresAt <= now()) {
            throw ApiOtpException(ErrorCodes.OTP_EXPIRED, "Kod muddati tugadi")
        }

        val attempts = challenge[OtpChallenges.attempts]
        if (attempts >= challenge[OtpChallenges.maxAttempts]) {
            throw ApiOtpException(
                ErrorCodes.OTP_TOO_MANY_ATTEMPTS,
                "Juda ko'p urinish. Yangi kod so'rang.",
            )
        }

        if (challenge[OtpChallenges.codeHash] != sha256(code)) {
            dbQuery {
                OtpChallenges.update({ OtpChallenges.id eq id }) {
                    it[OtpChallenges.attempts] = attempts + 1
                }
            }
            throw ApiOtpException(ErrorCodes.OTP_INVALID, "Kod noto'g'ri")
        }

        dbQuery {
            OtpChallenges.update({ OtpChallenges.id eq id }) {
                it[consumedAt] = now().toOffsetDateTime()
            }
        }
        return challenge[OtpChallenges.phone]
    }

    /** How long the client must wait before the resend button becomes active again. */
    suspend fun hasLiveChallenge(rawPhone: String): Boolean {
        val phone = PhoneNumbers.normalize(rawPhone)
        return dbQuery {
            OtpChallenges.selectAll()
                .where {
                    (OtpChallenges.phone eq phone) and
                        OtpChallenges.consumedAt.isNull() and
                        (OtpChallenges.expiresAt greater now().toOffsetDateTime())
                }
                .empty()
                .not()
        }
    }
}

/** Carries the specific OTP failure code so the client can word the message itself. */
class ApiOtpException(code: String, message: String) :
    uz.sadora.server.core.ApiException(io.ktor.http.HttpStatusCode.BadRequest, code, message)

/**
 * Sending is behind an interface because the SMS provider is still an open question in
 * the proposal. Swapping [LoggingOtpSender] for a real gateway is a one-line change.
 */
interface OtpSender {
    suspend fun send(phone: String, code: String)
}

class LoggingOtpSender : OtpSender {
    private val logger = LoggerFactory.getLogger(LoggingOtpSender::class.java)

    override suspend fun send(phone: String, code: String) {
        logger.info("OTP for {} is {} (no SMS provider configured)", PhoneNumbers.mask(phone), code)
    }
}
