package uz.sadora.server.admin

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.Page
import uz.sadora.contract.SubscriptionSource
import uz.sadora.contract.SubscriptionTier
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RefreshTokenService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.db.Devices
import uz.sadora.server.db.dbQuery
import uz.sadora.server.entitlement.EntitlementRepository
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.entitlement.SubscriptionRepository
import uz.sadora.server.flags.FeatureFlagRepository
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagRule
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * Everything the admin panel does to the account, money and rules tables.
 *
 * The health-data boundary from section 17 of the TZ is enforced structurally: this
 * class depends only on [UserRepository], [EntitlementRepository] and
 * [SubscriptionRepository], none of which can reach a cycle log, a symptom, a mood entry
 * or an AI transcript. There is no "operator override" to add later without changing
 * these dependencies, which is the point.
 */
class AdminService(
    private val users: UserRepository,
    private val entitlementRepository: EntitlementRepository,
    private val entitlementService: EntitlementService,
    private val subscriptions: SubscriptionRepository,
    private val flagRepository: FeatureFlagRepository,
    private val flagService: FeatureFlagService,
    private val audit: AuditService,
) {

    // ---------------------------------------------------------------- users

    suspend fun listUsers(
        filter: UserRepository.UserFilter,
        limit: Int,
        offset: Long,
    ): Page<AdminUserSummary> {
        val (records, total) = users.list(filter, limit, offset)
        val summaries = records.map { record ->
            val subscription = entitlementRepository.activeSubscription(record.id)
            record.toSummary(subscription?.tier ?: SubscriptionTier.FREE)
        }
        return Page(summaries, total, limit, offset.toInt())
    }

    suspend fun userCard(userId: Uuid): AdminUserCard {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val active = entitlementRepository.activeSubscription(userId)
        val history = subscriptions.historyOf(userId)
        val usage = entitlementRepository.usage(userId, now().dayIn(user.timezone))

        val devices = dbQuery {
            Devices.selectAll()
                .where { Devices.userId eq userId }
                .orderBy(Devices.lastSeenAt to SortOrder.DESC)
                .map { row ->
                    AdminDeviceView(
                        deviceId = row[Devices.deviceId],
                        platform = row[Devices.platform],
                        model = row[Devices.model],
                        appVersion = row[Devices.appVersion],
                        lastSeenAt = row[Devices.lastSeenAt].toKotlinInstant(),
                    )
                }
        }

        return AdminUserCard(
            general = user.toSummary(active?.tier ?: SubscriptionTier.FREE),
            subscription = AdminSubscriptionView(
                tier = active?.tier ?: SubscriptionTier.FREE,
                source = active?.source,
                expiresAt = active?.expiresAt,
                inGracePeriod = active?.inGracePeriod ?: false,
                history = history.map {
                    AdminSubscriptionHistoryItem(it.source, it.productId, it.startedAt, it.expiresAt)
                },
            ),
            technical = AdminTechnicalView(
                devices = devices,
                featureUsage = usage.map { (key, counters) ->
                    AdminUsageView(key, counters.today, counters.thisMonth)
                }.sortedBy { it.featureKey },
                timezone = user.timezone,
            ),
        )
    }

    suspend fun setBlocked(
        userId: Uuid,
        request: BlockUserRequest,
        admin: AdminPrincipal,
        refreshTokens: RefreshTokenService,
        context: RequestContext,
    ) {
        if (request.reason.isBlank()) {
            throw ValidationException("reason", "Sabab ko'rsatilishi shart")
        }
        users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")

        if (request.blocked) {
            users.setStatus(userId, AccountStatus.BLOCKED, request.reason)
            // Blocking has to take effect now, not when the access token expires.
            refreshTokens.revokeAllForUser(userId, "blocked_by_admin")
        } else {
            users.setStatus(userId, AccountStatus.ACTIVE, null)
        }

        audit.record(
            admin.entry(
                action = if (request.blocked) AuditActions.USER_BLOCKED else AuditActions.USER_UNBLOCKED,
                entityType = "user",
                entityId = userId.toString(),
                reason = request.reason,
                context = context,
            ),
        )
    }

    suspend fun grantPremium(
        userId: Uuid,
        request: GrantPremiumRequest,
        admin: AdminPrincipal,
        context: RequestContext,
    ) {
        if (request.reason.isBlank()) {
            throw ValidationException("reason", "Sabab ko'rsatilishi shart")
        }
        users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        subscriptions.grant(
            userId = userId,
            source = SubscriptionSource.MANUAL,
            expiresAt = request.expiresAt,
            grantedBy = admin.adminId,
            reason = request.reason,
        )
        audit.record(
            admin.entry(
                action = AuditActions.SUBSCRIPTION_GRANTED,
                entityType = "user",
                entityId = userId.toString(),
                reason = request.reason,
                metadata = mapOf("expiresAt" to (request.expiresAt?.toString() ?: "never")),
                context = context,
            ),
        )
    }

    // ---------------------------------------------------------------- entitlements

    suspend fun featureDefinitions(): List<FeatureDefinitionView> =
        entitlementRepository.definitions().map {
            FeatureDefinitionView(
                key = it.key,
                description = it.description,
                freeEnabled = it.freeEnabled,
                premiumEnabled = it.premiumEnabled,
                freeDailyLimit = it.freeDailyLimit,
                freeMonthlyLimit = it.freeMonthlyLimit,
                premiumDailyLimit = it.premiumDailyLimit,
                premiumMonthlyLimit = it.premiumMonthlyLimit,
            )
        }

    suspend fun updateFeatureDefinition(
        key: String,
        request: UpdateFeatureDefinitionRequest,
        admin: AdminPrincipal,
        context: RequestContext,
    ) {
        val updated = entitlementRepository.updateDefinition(
            key = key,
            freeEnabled = request.freeEnabled,
            premiumEnabled = request.premiumEnabled,
            freeDailyLimit = request.freeDailyLimit,
            freeMonthlyLimit = request.freeMonthlyLimit,
            premiumDailyLimit = request.premiumDailyLimit,
            premiumMonthlyLimit = request.premiumMonthlyLimit,
            updatedBy = admin.adminId,
        )
        if (!updated) throw NotFoundException("Bunday funksiya yo'q: $key")
        entitlementService.invalidateDefinitions()

        audit.record(
            admin.entry(
                action = AuditActions.ENTITLEMENT_DEFINITION_UPDATED,
                entityType = "feature_definition",
                entityId = key,
                metadata = mapOf(
                    "freeEnabled" to request.freeEnabled.toString(),
                    "premiumEnabled" to request.premiumEnabled.toString(),
                    "freeDailyLimit" to request.freeDailyLimit.toString(),
                    "premiumDailyLimit" to request.premiumDailyLimit.toString(),
                ),
                context = context,
            ),
        )
    }

    suspend fun setOverride(
        userId: Uuid,
        featureKey: String,
        request: SetOverrideRequest,
        admin: AdminPrincipal,
        context: RequestContext,
    ) {
        if (request.reason.isBlank()) {
            throw ValidationException("reason", "Sabab ko'rsatilishi shart")
        }
        entitlementRepository.setOverride(
            userId = userId,
            featureKey = featureKey,
            enabled = request.enabled,
            dailyLimit = request.dailyLimit,
            monthlyLimit = request.monthlyLimit,
            reason = request.reason,
            expiresAt = request.expiresAt,
            createdBy = admin.adminId,
        )
        audit.record(
            admin.entry(
                action = AuditActions.ENTITLEMENT_OVERRIDE_SET,
                entityType = "user",
                entityId = userId.toString(),
                reason = request.reason,
                metadata = mapOf("feature" to featureKey),
                context = context,
            ),
        )
    }

    suspend fun clearOverride(
        userId: Uuid,
        featureKey: String,
        admin: AdminPrincipal,
        context: RequestContext,
    ) {
        if (!entitlementRepository.clearOverride(userId, featureKey)) {
            throw NotFoundException("Bunday override yo'q")
        }
        audit.record(
            admin.entry(
                action = AuditActions.ENTITLEMENT_OVERRIDE_CLEARED,
                entityType = "user",
                entityId = userId.toString(),
                metadata = mapOf("feature" to featureKey),
                context = context,
            ),
        )
    }

    // ---------------------------------------------------------------- flags

    suspend fun flags(): List<FlagView> {
        val rulesByFlag = flagRepository.rules().groupBy { it.flagKey }
        return flagRepository.all().map { definition ->
            FlagView(
                key = definition.key,
                description = definition.description,
                enabled = definition.enabled,
                defaultValue = definition.defaultValue,
                rules = rulesByFlag[definition.key].orEmpty().map { it.toView() },
            )
        }
    }

    suspend fun updateFlag(
        key: String,
        request: UpdateFlagRequest,
        admin: AdminPrincipal,
        context: RequestContext,
    ) {
        if (!flagRepository.setEnabled(key, request.enabled, request.defaultValue, admin.adminId)) {
            throw NotFoundException("Bunday flag yo'q: $key")
        }
        flagService.invalidate()
        audit.record(
            admin.entry(
                action = AuditActions.FEATURE_FLAG_UPDATED,
                entityType = "feature_flag",
                entityId = key,
                metadata = mapOf(
                    "enabled" to request.enabled.toString(),
                    "default" to request.defaultValue.toString(),
                ),
                context = context,
            ),
        )
    }

    suspend fun addFlagRule(
        key: String,
        request: CreateFlagRuleRequest,
        admin: AdminPrincipal,
        context: RequestContext,
    ): String {
        if (request.rolloutPercentage !in 0..100) {
            throw ValidationException("rolloutPercentage", "0–100 oralig'ida bo'lishi kerak")
        }
        val id = flagRepository.addRule(
            FlagRule(
                id = Uuid.random(),
                flagKey = key,
                environment = request.environment,
                country = request.country,
                language = request.language,
                lifeStage = request.lifeStage,
                platform = request.platform,
                cohort = request.cohort,
                rolloutPercentage = request.rolloutPercentage,
                value = request.value,
                priority = request.priority,
            ),
        )
        flagService.invalidate()
        audit.record(
            admin.entry(
                action = AuditActions.FEATURE_FLAG_RULE_ADDED,
                entityType = "feature_flag",
                entityId = key,
                metadata = mapOf(
                    "ruleId" to id.toString(),
                    "rollout" to request.rolloutPercentage.toString(),
                ),
                context = context,
            ),
        )
        return id.toString()
    }

    suspend fun removeFlagRule(
        key: String,
        ruleId: Uuid,
        admin: AdminPrincipal,
        context: RequestContext,
    ) {
        if (!flagRepository.removeRule(ruleId)) throw NotFoundException("Bunday qoida yo'q")
        flagService.invalidate()
        audit.record(
            admin.entry(
                action = AuditActions.FEATURE_FLAG_RULE_REMOVED,
                entityType = "feature_flag",
                entityId = key,
                metadata = mapOf("ruleId" to ruleId.toString()),
                context = context,
            ),
        )
    }
}

private fun UserRecord.toSummary(tier: SubscriptionTier) = AdminUserSummary(
    id = id.toString(),
    name = name,
    phone = phone,
    email = email,
    language = language,
    lifeStage = lifeStage,
    tier = tier,
    status = status,
    registeredAt = createdAt,
    lastActiveAt = lastActiveAt,
)

private fun FlagRule.toView() = FlagRuleView(
    id = id.toString(),
    environment = environment,
    country = country,
    language = language,
    lifeStage = lifeStage,
    platform = platform,
    cohort = cohort,
    rolloutPercentage = rolloutPercentage,
    value = value,
    priority = priority,
)

private fun AdminPrincipal.entry(
    action: String,
    entityType: String,
    entityId: String,
    reason: String? = null,
    metadata: Map<String, String> = emptyMap(),
    context: RequestContext,
) = AuditEntry(
    actorType = ActorType.ADMIN,
    actorId = adminId,
    actorLabel = role.name.lowercase(),
    action = action,
    entityType = entityType,
    entityId = entityId,
    reason = reason,
    metadata = metadata,
    ip = context.ip,
    userAgent = context.userAgent,
)
