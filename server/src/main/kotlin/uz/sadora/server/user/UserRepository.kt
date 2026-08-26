package uz.sadora.server.user

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.CycleBaseline
import uz.sadora.contract.DeviceInfo
import uz.sadora.contract.Goal
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.StageBaseline
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AuthIdentities
import uz.sadora.server.db.ConsentEvents
import uz.sadora.server.db.CycleBaselines
import uz.sadora.server.db.Devices
import uz.sadora.server.db.StageBaselines
import uz.sadora.server.db.UserConsents
import uz.sadora.server.db.UserGoals
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb
import uz.sadora.server.db.dbQuery

/**
 * All reads and writes against the account tables.
 *
 * Deliberately health-free: nothing here touches cycle logs, symptoms, mood or AI
 * transcripts, which is what lets the admin endpoints reuse it without a second
 * "safe subset" repository.
 */
class UserRepository {

    // ---------------------------------------------------------------- lookups

    suspend fun findById(id: Uuid): UserRecord? = dbQuery {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUserRecord()
    }

    suspend fun findByPhone(phone: String): UserRecord? = dbQuery {
        Users.selectAll().where { Users.phone eq phone }.singleOrNull()?.toUserRecord()
    }

    /** Email is matched case-insensitively; addresses are stored already normalised. */
    suspend fun findByEmail(email: String): UserRecord? = dbQuery {
        Users.selectAll().where { Users.email eq email.normalizeEmail() }
            .singleOrNull()?.toUserRecord()
    }

    suspend fun findByProviderSubject(provider: AuthProvider, subject: String): UserRecord? = dbQuery {
        (AuthIdentities innerJoin Users)
            .selectAll()
            .where { (AuthIdentities.provider eq provider.dbValue()) and (AuthIdentities.subject eq subject) }
            .singleOrNull()
            ?.toUserRecord()
    }

    suspend fun goalsOf(userId: Uuid): List<Goal> = dbQuery { readGoals(userId) }

    private fun readGoals(userId: Uuid): List<Goal> =
        UserGoals.selectAll().where { UserGoals.userId eq userId }
            .mapNotNull { enumFromDb<Goal>(it[UserGoals.goal]) }

    // ---------------------------------------------------------------- creation

    suspend fun create(newUser: NewUser): UserRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now().toOffsetDateTime()
        Users.insert {
            it[Users.id] = id
            it[phone] = newUser.phone
            it[email] = newUser.email?.normalizeEmail()
            it[passwordHash] = newUser.passwordHash
            it[name] = newUser.name
            it[language] = newUser.language.dbValue()
            it[timezone] = newUser.timezone
            it[lifeStage] = LifeStage.CYCLE.dbValue()
            it[onboardingCompleted] = false
            it[status] = AccountStatus.ACTIVE.dbValue()
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
            it[lastActiveAt] = timestamp
        }
        // A fresh account starts with every consent withheld — nothing is opted in by
        // default, including analytics.
        UserConsents.insert {
            it[userId] = id
            it[storeHealth] = false
            it[aiInsights] = false
            it[analytics] = false
            it[marketing] = false
            it[policyVersion] = ""
            it[updatedAt] = timestamp
        }
        Users.selectAll().where { Users.id eq id }.single().toUserRecord()
    }

    suspend fun linkIdentity(userId: Uuid, provider: AuthProvider, subject: String, email: String?) {
        dbQuery {
            AuthIdentities.insert {
                it[id] = Uuid.random()
                it[AuthIdentities.userId] = userId
                it[AuthIdentities.provider] = provider.dbValue()
                it[AuthIdentities.subject] = subject
                it[AuthIdentities.email] = email?.normalizeEmail()
                it[createdAt] = now().toOffsetDateTime()
            }
        }
    }

    // ---------------------------------------------------------------- updates

    /**
     * Partial profile update. Every parameter defaults to null meaning "unchanged", so
     * `null` cannot be used to clear a field — the dedicated setters below do that.
     */
    suspend fun updateProfile(
        userId: Uuid,
        name: String? = null,
        language: Language? = null,
        timezone: String? = null,
        lifeStage: LifeStage? = null,
        birthDate: LocalDate? = null,
        heightCm: Int? = null,
        weightKg: Int? = null,
    ): Unit = dbQuery {
        applyProfileUpdate(userId, name, language, timezone, lifeStage, birthDate, heightCm, weightKg)
    }

    suspend fun replaceGoals(userId: Uuid, goals: List<Goal>): Unit = dbQuery {
        applyGoals(userId, goals)
    }

    suspend fun markOnboarded(userId: Uuid): Unit = dbQuery { applyOnboarded(userId) }

    // ------------------------------------------------- in-transaction primitives
    //
    // Onboarding writes to six tables and must land as a unit — a user who is marked
    // onboarded but has no consent row is worse than one who has to try again. These
    // run inside a transaction the caller already opened; the suspend functions above
    // are the same writes with a transaction of their own.

    fun applyProfileUpdate(
        userId: Uuid,
        name: String? = null,
        language: Language? = null,
        timezone: String? = null,
        lifeStage: LifeStage? = null,
        birthDate: LocalDate? = null,
        heightCm: Int? = null,
        weightKg: Int? = null,
    ) {
        Users.update({ Users.id eq userId }) { statement ->
            name?.let { statement[Users.name] = it }
            language?.let { statement[Users.language] = it.dbValue() }
            timezone?.let { statement[Users.timezone] = it }
            lifeStage?.let { statement[Users.lifeStage] = it.dbValue() }
            birthDate?.let { statement[Users.birthDate] = it }
            heightCm?.let { statement[Users.heightCm] = it }
            weightKg?.let { statement[Users.weightKg] = it }
            statement[Users.updatedAt] = now().toOffsetDateTime()
        }
    }

    fun applyGoals(userId: Uuid, goals: List<Goal>) {
        UserGoals.deleteWhere { UserGoals.userId eq userId }
        goals.distinct().forEach { goal ->
            UserGoals.insert {
                it[UserGoals.userId] = userId
                it[UserGoals.goal] = goal.dbValue()
            }
        }
    }

    fun applyOnboarded(userId: Uuid) {
        Users.update({ Users.id eq userId }) {
            it[onboardingCompleted] = true
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    fun applyCycleBaseline(userId: Uuid, baseline: CycleBaseline) {
        CycleBaselines.upsert(CycleBaselines.userId) {
            it[CycleBaselines.userId] = userId
            it[lastPeriodStart] = baseline.lastPeriodStart
            it[averageCycleLength] = baseline.averageCycleLength
            it[averagePeriodLength] = baseline.averagePeriodLength
            it[isRegular] = baseline.cycleIsRegular
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    fun applyStageBaseline(userId: Uuid, baseline: StageBaseline) {
        StageBaselines.upsert(StageBaselines.userId) {
            it[StageBaselines.userId] = userId
            it[dueDate] = baseline.dueDate
            it[childBirthDate] = baseline.birthDate
            it[lastPeriodStart] = baseline.lastPeriodStart
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    fun applyConsents(
        userId: Uuid,
        grants: ConsentGrants,
        policyVersion: String,
        source: String = "app",
    ) {
        val timestamp = now().toOffsetDateTime()
        UserConsents.upsert(UserConsents.userId) {
            it[UserConsents.userId] = userId
            it[storeHealth] = grants.storeHealth
            it[aiInsights] = grants.aiInsights
            it[analytics] = grants.analytics
            it[marketing] = grants.marketing
            it[UserConsents.policyVersion] = policyVersion
            it[updatedAt] = timestamp
        }
        listOf(
            "store_health" to grants.storeHealth,
            "ai_insights" to grants.aiInsights,
            "analytics" to grants.analytics,
            "marketing" to grants.marketing,
        ).forEach { (key, granted) ->
            ConsentEvents.insert {
                it[id] = Uuid.random()
                it[ConsentEvents.userId] = userId
                it[consentKey] = key
                it[ConsentEvents.granted] = granted
                it[ConsentEvents.policyVersion] = policyVersion
                it[consentSource] = source
                it[createdAt] = timestamp
            }
        }
    }

    suspend fun touchLastActive(userId: Uuid): Unit = dbQuery {
        Users.update({ Users.id eq userId }) { it[lastActiveAt] = now().toOffsetDateTime() }
    }

    suspend fun setStatus(userId: Uuid, status: AccountStatus, reason: String?): Unit = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[Users.status] = status.dbValue()
            it[blockedReason] = reason
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    /**
     * Deletion is a two-step process: this marks the account and stops it signing in.
     * The erasure job that follows is a sprint-3 deliverable, so the row survives until
     * then and support can still answer "when did she ask?".
     */
    suspend fun requestDeletion(userId: Uuid): Unit = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[status] = AccountStatus.DELETION_PENDING.dbValue()
            it[deletionRequestedAt] = now().toOffsetDateTime()
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    // ---------------------------------------------------------------- consent

    suspend fun consentsOf(userId: Uuid): ConsentRecord? = dbQuery {
        UserConsents.selectAll().where { UserConsents.userId eq userId }.singleOrNull()?.let { row ->
            ConsentRecord(
                storeHealth = row[UserConsents.storeHealth],
                aiInsights = row[UserConsents.aiInsights],
                analytics = row[UserConsents.analytics],
                marketing = row[UserConsents.marketing],
                policyVersion = row[UserConsents.policyVersion],
                updatedAt = row[UserConsents.updatedAt].toKotlinInstant(),
            )
        }
    }

    /**
     * Writes the current state and appends one event per consent. The event rows are the
     * legally interesting part: they answer "what did she agree to, and when".
     */
    suspend fun saveConsents(
        userId: Uuid,
        grants: ConsentGrants,
        policyVersion: String,
        source: String = "app",
    ): Unit = dbQuery { applyConsents(userId, grants, policyVersion, source) }

    // ---------------------------------------------------------------- baselines

    suspend fun saveCycleBaseline(userId: Uuid, baseline: CycleBaseline): Unit =
        dbQuery { applyCycleBaseline(userId, baseline) }

    suspend fun saveStageBaseline(userId: Uuid, baseline: StageBaseline): Unit =
        dbQuery { applyStageBaseline(userId, baseline) }

    // ---------------------------------------------------------------- devices

    suspend fun registerDevice(userId: Uuid, device: DeviceInfo): Unit = dbQuery {
        val existing = Devices.selectAll()
            .where { (Devices.userId eq userId) and (Devices.deviceId eq device.deviceId) }
            .singleOrNull()
        val timestamp = now().toOffsetDateTime()
        if (existing == null) {
            Devices.insert {
                it[id] = Uuid.random()
                it[Devices.userId] = userId
                it[deviceId] = device.deviceId
                it[platform] = device.platform.dbValue()
                it[osVersion] = device.osVersion
                it[appVersion] = device.appVersion
                it[model] = device.model
                it[pushToken] = device.pushToken
                it[timezone] = device.timezone
                it[createdAt] = timestamp
                it[lastSeenAt] = timestamp
            }
        } else {
            Devices.update({ Devices.id eq existing[Devices.id] }) {
                it[platform] = device.platform.dbValue()
                it[osVersion] = device.osVersion
                it[appVersion] = device.appVersion
                it[model] = device.model
                // A null push token means "the client did not send one this time", not
                // "unregister" — keep whatever we already had.
                device.pushToken?.let { token -> it[pushToken] = token }
                device.timezone?.let { zone -> it[timezone] = zone }
                it[lastSeenAt] = timestamp
            }
        }
    }

    // ---------------------------------------------------------------- admin listing

    data class UserFilter(
        val query: String? = null,
        val status: AccountStatus? = null,
        val language: Language? = null,
        val lifeStage: LifeStage? = null,
        val registeredAfter: Instant? = null,
        val registeredBefore: Instant? = null,
    )

    suspend fun list(filter: UserFilter, limit: Int, offset: Long): Pair<List<UserRecord>, Long> =
        dbQuery {
            var query = Users.selectAll()
            filter.status?.let { status -> query = query.andWhere { Users.status eq status.dbValue() } }
            filter.language?.let { lang -> query = query.andWhere { Users.language eq lang.dbValue() } }
            filter.lifeStage?.let { stage -> query = query.andWhere { Users.lifeStage eq stage.dbValue() } }
            filter.registeredAfter?.let { from ->
                query = query.andWhere { Users.createdAt greaterEq from.toOffsetDateTime() }
            }
            filter.registeredBefore?.let { until ->
                query = query.andWhere { Users.createdAt less until.toOffsetDateTime() }
            }
            filter.query?.takeIf { it.isNotBlank() }?.let { term ->
                val pattern = "%${term.trim().lowercase()}%"
                query = query.andWhere {
                    (Users.phone.lowerCase() like pattern) or
                        (Users.email.lowerCase() like pattern) or
                        (Users.name.lowerCase() like pattern)
                }
            }
            val total = query.count()
            val page = query
                .orderBy(Users.createdAt to SortOrder.DESC)
                .limit(limit)
                .offset(offset)
                .map { it.toUserRecord() }
            page to total
        }
}

/** Stored lowercase so `findByEmail` and the unique index agree on what a duplicate is. */
internal fun String.normalizeEmail(): String = trim().lowercase()

private fun ResultRow.toUserRecord(): UserRecord = UserRecord(
    id = this[Users.id],
    phone = this[Users.phone],
    email = this[Users.email],
    passwordHash = this[Users.passwordHash],
    name = this[Users.name],
    language = enumFromDb(this[Users.language], Language.UZ),
    timezone = this[Users.timezone],
    lifeStage = enumFromDb(this[Users.lifeStage], LifeStage.CYCLE),
    birthDate = this[Users.birthDate],
    heightCm = this[Users.heightCm],
    weightKg = this[Users.weightKg],
    avatarUrl = this[Users.avatarUrl],
    onboardingCompleted = this[Users.onboardingCompleted],
    status = enumFromDb(this[Users.status], AccountStatus.ACTIVE),
    blockedReason = this[Users.blockedReason],
    createdAt = this[Users.createdAt].toKotlinInstant(),
    updatedAt = this[Users.updatedAt].toKotlinInstant(),
    lastActiveAt = this[Users.lastActiveAt]?.toKotlinInstant(),
    deletionRequestedAt = this[Users.deletionRequestedAt]?.toKotlinInstant(),
)
