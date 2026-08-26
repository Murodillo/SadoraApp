package uz.sadora.server.user

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.Consents
import uz.sadora.contract.Goal
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.UserProfile

/** A row of `users`, with the enums and timestamps already converted. */
data class UserRecord(
    val id: Uuid,
    val phone: String?,
    val email: String?,
    val passwordHash: String?,
    val name: String,
    val language: Language,
    val timezone: String,
    val lifeStage: LifeStage,
    val birthDate: LocalDate?,
    val heightCm: Int?,
    val weightKg: Int?,
    val avatarUrl: String?,
    val onboardingCompleted: Boolean,
    val status: AccountStatus,
    val blockedReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastActiveAt: Instant?,
    val deletionRequestedAt: Instant?,
) {
    /**
     * Goals live in their own table, so the caller supplies them. Note what is absent:
     * nothing derived from health data ever reaches this DTO.
     */
    fun toProfile(goals: List<Goal>): UserProfile = UserProfile(
        id = id.toString(),
        phone = phone,
        email = email,
        name = name,
        language = language,
        timezone = timezone,
        lifeStage = lifeStage,
        birthDate = birthDate,
        heightCm = heightCm,
        weightKg = weightKg,
        goals = goals,
        avatarUrl = avatarUrl,
        onboardingCompleted = onboardingCompleted,
        status = status,
        createdAt = createdAt,
    )
}

data class ConsentRecord(
    val storeHealth: Boolean,
    val aiInsights: Boolean,
    val analytics: Boolean,
    val marketing: Boolean,
    val policyVersion: String,
    val updatedAt: Instant,
) {
    fun toDto(): Consents = Consents(
        storeHealth = storeHealth,
        aiInsights = aiInsights,
        analytics = analytics,
        marketing = marketing,
        policyVersion = policyVersion,
        updatedAt = updatedAt,
    )
}

/** What a new account needs before it has been through onboarding. */
data class NewUser(
    val phone: String? = null,
    val email: String? = null,
    val passwordHash: String? = null,
    val name: String = "",
    val language: Language = Language.UZ,
    val timezone: String,
)
