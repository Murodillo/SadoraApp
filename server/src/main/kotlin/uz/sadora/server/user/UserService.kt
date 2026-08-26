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
import uz.sadora.server.core.isValidTimeZone
import uz.sadora.server.core.now
import uz.sadora.server.db.dbQuery
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext

class UserService(
    private val users: UserRepository,
    private val entitlements: EntitlementService,
    private val flags: FeatureFlagService,
    private val refreshTokens: RefreshTokenService,
    private val audit: AuditService,
    private val config: AppConfig,
) {

    suspend fun requireUser(userId: Uuid): UserRecord =
        users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")

    suspend fun profile(userId: Uuid): UserProfile {
        val user = requireUser(userId)
        return user.toProfile(users.goalsOf(userId))
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
            user = user.toProfile(users.goalsOf(userId)),
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
        request.heightCm?.let {
            if (it !in 80..250) throw ValidationException("heightCm", "80–250 oralig'ida bo'lishi kerak")
        }
        request.weightKg?.let {
            if (it !in 25..300) throw ValidationException("weightKg", "25–300 oralig'ida bo'lishi kerak")
        }

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
        if (request.name.isBlank()) throw ValidationException("name", "Bo'sh bo'lishi mumkin emas")
        request.heightCm?.let {
            if (it !in 80..250) throw ValidationException("heightCm", "80–250 oralig'ida bo'lishi kerak")
        }
        request.weightKg?.let {
            if (it !in 25..300) throw ValidationException("weightKg", "25–300 oralig'ida bo'lishi kerak")
        }

        val cycleBaseline = request.cycle?.takeIf { request.lifeStage.predictsCycle }
        cycleBaseline?.let { baseline ->
            if (baseline.averageCycleLength !in 15..60) {
                throw ValidationException("cycle.averageCycleLength", "15–60 kun oralig'ida")
            }
            if (baseline.averagePeriodLength !in 1..15) {
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
            users.applyOnboarded(userId)
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
