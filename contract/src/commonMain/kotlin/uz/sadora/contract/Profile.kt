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

/** How long she has been trying to conceive, asked once during onboarding. */
@Serializable
enum class ConceptionWindow {
    @SerialName("just_started") JUST_STARTED,
    @SerialName("under_3_months") UNDER_3_MONTHS,
    @SerialName("three_to_six_months") THREE_TO_SIX_MONTHS,
    @SerialName("six_to_twelve_months") SIX_TO_TWELVE_MONTHS,
    @SerialName("over_a_year") OVER_A_YEAR,
}

/**
 * Contraception used in the six months before onboarding.
 *
 * It matters for predictions rather than for demographics: hormonal methods suppress
 * ovulation, so a cycle recorded shortly after stopping one is not yet a baseline the
 * app should predict from.
 */
@Serializable
enum class BirthControl {
    @SerialName("none") NONE,
    @SerialName("still_using") STILL_USING,
    @SerialName("pill") PILL,
    @SerialName("iud") IUD,
    @SerialName("barrier") BARRIER,
    @SerialName("other") OTHER,
    @SerialName("undisclosed") UNDISCLOSED,
}

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
    /** Only asked of users trying to conceive. */
    val conceptionWindow: ConceptionWindow? = null,
    val birthControl: BirthControl? = null,
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
