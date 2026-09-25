package uz.sadora.server.user

import kotlin.uuid.Uuid
import uz.sadora.contract.Bootstrap
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.Consents
import uz.sadora.contract.DeleteAccountRequest
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.Entitlements
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.Platform
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.contract.UserProfile
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RefreshTokenService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.config.AppConfig
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import kotlinx.datetime.LocalDate
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.dayIn
import uz.sadora.contract.Limits
import uz.sadora.server.core.isValidTimeZone
import uz.sadora.server.core.now
import uz.sadora.server.db.dbQuery
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.rewards.RewardsService

class UserService(
    private val users: UserRepository,
    private val entitlements: EntitlementService,
    private val flags: FeatureFlagService,
    private val refreshTokens: RefreshTokenService,
    private val audit: AuditService,
    private val config: AppConfig,
    /**
     * The reward scheme, for the one thing onboarding owes it: the invite code she
     * arrived with.
     *
     * Claimed here rather than by a second call from the app so the reward lands in the
     * same moment the account does — an app that dropped the connection between the two
     * calls would owe somebody an invite nobody could prove.
     */
    private val rewards: RewardsService? = null,
    /** See [requestDeletion]: the request must close every door at once, not one. */
    private val accountGate: uz.sadora.server.plugins.AccountGate? = null,
    private val shares: uz.sadora.server.share.ShareRepository? = null,
) {

    suspend fun requireUser(userId: Uuid): UserRecord =
        users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")

    suspend fun profile(userId: Uuid): UserProfile {
        val user = requireUser(userId)
        return user.toProfile(users.goalsOf(userId), users.stageBaselineOf(userId))
    }

    suspend fun entitlements(userId: Uuid): Entitlements {
        val user = requireUser(userId)
        return entitlements.resolve(userId, user.timezone)
    }

    /**
     * Everything the client needs on cold start. One call rather than four, because on a
     * slow connection four sequential round trips is the difference between the app
     * feeling instant and feeling broken.
     */
    suspend fun bootstrap(userId: Uuid, platform: Platform?): Bootstrap {
        val user = requireUser(userId)
        users.touchLastActive(userId)
        return Bootstrap(
            user = user.toProfile(users.goalsOf(userId), users.stageBaselineOf(userId)),
            entitlements = entitlements.resolve(userId, user.timezone),
            flags = flags.evaluate(
                FlagContext(
                    userId = userId,
                    environment = config.environment,
                    language = user.language,
                    lifeStage = user.lifeStage,
                    platform = platform,
                ),
            ),
            consents = consents(userId),
            serverTime = now(),
            minimumAppVersion = config.minimumAppVersion,
        )
    }

    suspend fun updateProfile(
        userId: Uuid,
        request: UpdateProfileRequest,
        context: RequestContext,
    ): UserProfile {
        request.timezone?.let {
            if (!isValidTimeZone(it)) throw ValidationException("timezone", "Noma'lum vaqt mintaqasi")
        }
        request.name?.let(::validateName)
        request.heightCm?.let(::validateHeight)
        request.weightKg?.let(::validateWeight)
        request.birthDate?.let(::validateBirthDate)

        users.updateProfile(
            userId = userId,
            name = request.name?.trim()?.takeIf { it.isNotEmpty() },
            language = request.language,
            timezone = request.timezone,
            lifeStage = request.lifeStage,
            birthDate = request.birthDate,
            heightCm = request.heightCm,
            weightKg = request.weightKg,
        )
        request.goals?.let { users.replaceGoals(userId, it) }

        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.USER_PROFILE_UPDATED,
                entityType = "user",
                entityId = userId.toString(),
                // Field names only — the values are profile data and do not belong in a
                // log that support can read.
                metadata = mapOf("fields" to request.changedFields().joinToString(",")),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        return profile(userId)
    }

    /**
     * Onboarding lands in one call so a user who drops out halfway leaves no half-built
     * profile. The baselines are stage-aware: a pregnancy user's cycle answers are not
     * stored, because the app never predicts a cycle for her.
     *
     * Everything is validated before the first write, and the writes then run in a
     * single transaction. Both halves matter — an earlier version validated the cycle
     * length after replacing the goal rows, so a rejected request cleared the goals the
     * user had set on a previous attempt.
     */
    suspend fun completeOnboarding(
        userId: Uuid,
        request: OnboardingRequest,
        context: RequestContext,
    ): UserProfile {
        if (!isValidTimeZone(request.timezone)) {
            throw ValidationException("timezone", "Noma'lum vaqt mintaqasi")
        }
        validateName(request.name)
        request.heightCm?.let(::validateHeight)
        request.weightKg?.let(::validateWeight)
        request.birthDate?.let(::validateBirthDate)

        val cycleBaseline = request.cycle?.takeIf { request.lifeStage.predictsCycle }
        cycleBaseline?.let { baseline ->
            if (baseline.averageCycleLength !in Limits.CYCLE_LENGTH_DAYS) {
                throw ValidationException("cycle.averageCycleLength", "15–60 kun oralig'ida")
            }
            if (baseline.averagePeriodLength !in Limits.PERIOD_LENGTH_DAYS) {
                throw ValidationException("cycle.averagePeriodLength", "1–15 kun oralig'ida")
            }
        }

        dbQuery {
            users.applyProfileUpdate(
                userId = userId,
                name = request.name.trim(),
                language = request.language,
                timezone = request.timezone,
                lifeStage = request.lifeStage,
                birthDate = request.birthDate,
                heightCm = request.heightCm,
                weightKg = request.weightKg,
            )
            users.applyGoals(userId, request.goals)
            cycleBaseline?.let { users.applyCycleBaseline(userId, it) }
            request.stage?.let { users.applyStageBaseline(userId, it) }
            users.applyConsents(userId, request.consents, config.policyVersion)
            users.applyOnboarded(userId, request.referredByDoctor, request.hasWearable)
        }

        // Outside the transaction: an invite that could not be paid must not undo a
        // sign-up, and the claim is idempotent, so the worst case is a missing reward.
        request.inviteCode?.takeIf { it.isNotBlank() }?.let { code ->
            runCatching { rewards?.claimReferral(userId, code) }
        }

        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.USER_ONBOARDED,
                entityType = "user",
                entityId = userId.toString(),
                metadata = mapOf(
                    "lifeStage" to request.lifeStage.name.lowercase(),
                    "notifications" to request.permissions.notifications.toString(),
                    "healthData" to request.permissions.healthData.toString(),
                    "referredByDoctor" to (request.referredByDoctor?.toString() ?: "skipped"),
                    "hasWearable" to (request.hasWearable?.toString() ?: "skipped"),
                ),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        return profile(userId)
    }

    suspend fun consents(userId: Uuid): Consents =
        users.consentsOf(userId)?.toDto()
            ?: Consents(
                storeHealth = false,
                aiInsights = false,
                analytics = false,
                marketing = false,
                policyVersion = "",
                updatedAt = now(),
            )

    suspend fun updateConsents(
        userId: Uuid,
        grants: ConsentGrants,
        context: RequestContext,
    ): Consents {
        users.saveConsents(userId, grants, config.policyVersion)
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.USER_CONSENT_CHANGED,
                entityType = "user",
                entityId = userId.toString(),
                metadata = mapOf(
                    "storeHealth" to grants.storeHealth.toString(),
                    "aiInsights" to grants.aiInsights.toString(),
                    "analytics" to grants.analytics.toString(),
                    "marketing" to grants.marketing.toString(),
                    "policyVersion" to config.policyVersion,
                ),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        return consents(userId)
    }

    suspend fun registerDevice(userId: Uuid, device: DeviceInfo) {
        users.registerDevice(userId, device)
    }

    /**
     * Marks the account for deletion and signs every device out immediately. The erasure
     * itself is a separate job — the user should stop having access the moment she asks,
     * without waiting for it to run.
     */
    suspend fun requestDeletion(
        userId: Uuid,
        request: DeleteAccountRequest,
        context: RequestContext,
    ) {
        if (request.confirmation != DELETE_CONFIRMATION) {
            throw ValidationException("confirmation", "'$DELETE_CONFIRMATION' deb yozing")
        }
        users.requestDeletion(userId)
        refreshTokens.revokeAllForUser(userId, "account_deletion")
        // The access token in her phone is refused from the next request, and a doctor
        // link she made earlier stops opening: asking for deletion means nobody reads
        // the data any more, starting now.
        accountGate?.forget(userId)
        shares?.revokeAll(userId, now())
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.USER_DELETION_REQUESTED,
                entityType = "user",
                entityId = userId.toString(),
                reason = request.reason,
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    /**
     * The four profile fields a person types, checked in one place.
     *
     * Both the onboarding request and the profile update reach them, and the two had
     * drifted: onboarding refused a blank name, the update accepted one of any length,
     * and neither looked at the birth date at all — so a profile could carry a name
     * longer than the column and a birthday in 1815.
     */
    private fun validateName(raw: String) {
        if (raw.isBlank()) throw ValidationException("name", "Bo'sh bo'lishi mumkin emas")
        if (raw.trim().length > Limits.NAME_MAX) {
            throw ValidationException("name", "Eng ko'pi ${Limits.NAME_MAX} belgi")
        }
    }

    private fun validateHeight(value: Int) {
        if (value !in Limits.HEIGHT_CM) {
            throw ValidationException("heightCm", "80–250 oralig'ida bo'lishi kerak")
        }
    }

    private fun validateWeight(value: Int) {
        if (value !in Limits.WEIGHT_KG) {
            throw ValidationException("weightKg", "25–300 oralig'ida bo'lishi kerak")
        }
    }

    private fun validateBirthDate(value: LocalDate) {
        if (value.year !in Limits.BIRTH_YEAR) {
            throw ValidationException("birthDate", "Tug'ilgan yil noto'g'ri")
        }
        if (value > now().dayIn(DEFAULT_TIMEZONE)) {
            throw ValidationException("birthDate", "Kelajakdagi sana bo'lishi mumkin emas")
        }
    }

    private companion object {
        const val DELETE_CONFIRMATION = "DELETE"
    }
}

/** Field names of everything the request actually sets — used for the audit metadata. */
private fun UpdateProfileRequest.changedFields(): List<String> = buildList {
    if (name != null) add("name")
    if (language != null) add("language")
    if (timezone != null) add("timezone")
    if (lifeStage != null) add("lifeStage")
    if (birthDate != null) add("birthDate")
    if (heightCm != null) add("heightCm")
    if (weightKg != null) add("weightKg")
    if (goals != null) add("goals")
}
