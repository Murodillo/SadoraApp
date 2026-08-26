package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccountStatus {
    @SerialName("active") ACTIVE,
    @SerialName("blocked") BLOCKED,
    @SerialName("deletion_pending") DELETION_PENDING,
}

@Serializable
data class UserProfile(
    val id: String,
    val phone: String? = null,
    val email: String? = null,
    val name: String,
    val language: Language,
    val timezone: String,
    val lifeStage: LifeStage,
    val birthDate: LocalDate? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val goals: List<Goal> = emptyList(),
    val avatarUrl: String? = null,
    val onboardingCompleted: Boolean,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val createdAt: Instant,
)

/**
 * Partial update. A null field means "leave it alone", which is why every field is
 * nullable even where the stored value never is.
 */
@Serializable
data class UpdateProfileRequest(
    val name: String? = null,
    val language: Language? = null,
    val timezone: String? = null,
    val lifeStage: LifeStage? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val goals: List<Goal>? = null,
)

/**
 * Cycle baseline collected during onboarding. Only meaningful for stages where
 * [LifeStage.predictsCycle] is true; the server ignores it otherwise.
 */
@Serializable
data class CycleBaseline(
    val lastPeriodStart: LocalDate? = null,
    val averageCycleLength: Int = 28,
    val averagePeriodLength: Int = 5,
    val cycleIsRegular: Boolean = true,
)

/** Stage-specific baseline: due date for pregnancy, birth date for postpartum. */
@Serializable
data class StageBaseline(
    val dueDate: LocalDate? = null,
    val birthDate: LocalDate? = null,
    val lastPeriodStart: LocalDate? = null,
)

/**
 * The whole onboarding flow submitted in one call. Sending it as a unit means a user who
 * drops out halfway leaves no half-built profile behind.
 */
@Serializable
data class OnboardingRequest(
    val name: String,
    val language: Language,
    val timezone: String,
    val lifeStage: LifeStage,
    val birthDate: LocalDate? = null,
    val heightCm: Int? = null,
    val weightKg: Int? = null,
    val goals: List<Goal> = emptyList(),
    val cycle: CycleBaseline? = null,
    val stage: StageBaseline? = null,
    val permissions: PermissionGrants = PermissionGrants(),
    val consents: ConsentGrants = ConsentGrants(),
)

/** OS-level permissions the client reports after asking for them. */
@Serializable
data class PermissionGrants(
    val notifications: Boolean = false,
    val healthData: Boolean = false,
    val camera: Boolean = false,
)

/**
 * Explicit consents, versioned so a changed privacy policy can require a re-prompt.
 * [storeHealth] is the gate for storing anything from the health tabs at all.
 */
@Serializable
data class ConsentGrants(
    val storeHealth: Boolean = false,
    val aiInsights: Boolean = false,
    val analytics: Boolean = false,
    val marketing: Boolean = false,
    val policyVersion: String? = null,
)

@Serializable
data class Consents(
    val storeHealth: Boolean,
    val aiInsights: Boolean,
    val analytics: Boolean,
    val marketing: Boolean,
    val policyVersion: String,
    val updatedAt: Instant,
)

@Serializable
data class RegisterDeviceRequest(val device: DeviceInfo)

@Serializable
data class DeleteAccountRequest(
    /** Free-text reason, stored on the audit record. */
    val reason: String? = null,
    /** Must be the literal string `DELETE` — guards against an accidental call. */
    val confirmation: String,
)
