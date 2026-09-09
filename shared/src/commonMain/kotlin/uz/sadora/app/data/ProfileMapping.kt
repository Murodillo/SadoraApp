package uz.sadora.app.data

import uz.sadora.app.model.AppLanguage
import kotlinx.datetime.daysUntil
import uz.sadora.app.model.AppState
import uz.sadora.app.model.deviceToday
import uz.sadora.app.model.BirthControl
import uz.sadora.app.model.ConceptionWindow
import uz.sadora.app.model.Goal
import uz.sadora.app.model.LifeStage
import uz.sadora.contract.ConsentGrants
import uz.sadora.contract.Consents
import uz.sadora.contract.CycleBaseline
import uz.sadora.contract.Entitlements
import uz.sadora.contract.OnboardingCheckIn
import uz.sadora.contract.OnboardingRequest
import uz.sadora.contract.PermissionGrants
import uz.sadora.contract.StageBaseline
import uz.sadora.contract.UpdateProfileRequest
import uz.sadora.contract.SubscriptionTier
import uz.sadora.contract.UserProfile
import uz.sadora.contract.BirthControl as WireBirthControl
import uz.sadora.contract.ConceptionWindow as WireConceptionWindow
import uz.sadora.contract.Goal as WireGoal
import uz.sadora.contract.Language as WireLanguage
import uz.sadora.contract.LifeStage as WireLifeStage
import uz.sadora.contract.UzbekPhone

/**
 * Copies a server profile onto the in-memory store the screens read from.
 *
 * The wire enums and the UI enums are kept separate on purpose: the UI ones carry Uzbek
 * labels and palettes, the wire ones carry the spelling the backend stores. Mapping them
 * here means renaming a label never changes what goes over the network.
 *
 * A value the app does not recognise is left at whatever it already was rather than
 * guessed at — a server that gains a seventh life stage should not silently move a user
 * to a stage the app cannot render.
 */
fun AppState.applyServerProfile(profile: UserProfile, entitlements: Entitlements) {
    name = profile.name
    email = profile.email.orEmpty()
    // The field shows the local part behind a fixed "+998"; the wire carries the whole
    // number, so the prefix comes off here or the screen would show it twice.
    phone = profile.phone.orEmpty().toLocalPhone()
    profile.birthDate?.let { birthDate = it.toDisplayDate() }
    profile.heightCm?.let { heightCm = it.toString() }
    profile.weightKg?.let { weightKg = it.toString() }
    profile.language.toAppLanguage()?.let { language = it }
    hasWearable = profile.hasWearable
    profile.lifeStage.toAppLifeStage()?.let { lifeStage = it }

    goals.clear()
    goals.addAll(profile.goals.mapNotNull { it.toAppGoal() })

    // The stage anchor, and the two counts the screens read off it. Computing them here
    // rather than on the screen keeps one answer to "which week is she in" — the header,
    // the card and the knowledge link cannot disagree about it.
    profile.stage?.let { stage ->
        dueDate = stage.dueDate
        childBirthDate = stage.birthDate
        val today = deviceToday()
        stage.dueDate?.let { due ->
            // Forty weeks from conception to the due date, counted backwards from it.
            val daysToDue = today.daysUntil(due)
            pregnancyWeek = ((PregnancyDays - daysToDue) / 7).coerceIn(1, 42)
        }
        stage.birthDate?.let { born ->
            postpartumWeek = (born.daysUntil(today) / 7).coerceAtLeast(0)
        }
    }

    isPremium = entitlements.tier == SubscriptionTier.PREMIUM
}

/** A full-term pregnancy, in days — the constant the week count is measured against. */
private const val PregnancyDays = 280

/**
 * Applies a freshly authenticated session.
 *
 * An account that has not finished onboarding has an empty profile on the server, while
 * the answers collected so far live only on [AppState] — sign-up now comes *after* the
 * profile questions. Applying that empty profile would wipe every one of them, so only
 * the entitlements are taken until there is a real profile to apply.
 */
fun AppState.applyServerSession(profile: UserProfile, entitlements: Entitlements) {
    if (profile.onboardingCompleted) {
        applyServerProfile(profile, entitlements)
    } else {
        isPremium = entitlements.tier == SubscriptionTier.PREMIUM
    }
}

private fun WireLanguage.toAppLanguage(): AppLanguage? = when (this) {
    WireLanguage.UZ -> AppLanguage.Uz
    WireLanguage.RU -> AppLanguage.Ru
    WireLanguage.EN -> AppLanguage.En
}

private fun WireLifeStage.toAppLifeStage(): LifeStage? = when (this) {
    WireLifeStage.CYCLE -> LifeStage.Cycle
    WireLifeStage.TRYING_TO_CONCEIVE -> LifeStage.TryingToConceive
    WireLifeStage.PREGNANCY -> LifeStage.Pregnancy
    WireLifeStage.POSTPARTUM -> LifeStage.Postpartum
    WireLifeStage.PERIMENOPAUSE -> LifeStage.Perimenopause
    WireLifeStage.MENOPAUSE -> LifeStage.Menopause
}

private fun WireGoal.toAppGoal(): Goal? = when (this) {
    WireGoal.UNDERSTAND_CYCLE -> Goal.UnderstandCycle
    WireGoal.SLEEP_BETTER -> Goal.SleepBetter
    WireGoal.MORE_ENERGY -> Goal.MoreEnergy
    WireGoal.LESS_STRESS -> Goal.LessStress
    WireGoal.EAT_BALANCED -> Goal.EatBalanced
    WireGoal.DRINK_WATER -> Goal.DrinkWater
    WireGoal.BE_ACTIVE -> Goal.BeActive
    WireGoal.REMEMBER_MEDS -> Goal.RememberMeds
}

/** The app writes dates as `14.03.1994`; the wire format is ISO. */
private fun kotlinx.datetime.LocalDate.toDisplayDate(): String =
    "${day.pad()}.${monthNumber.pad()}.$year"

private fun Int.pad(): String = toString().padStart(2, '0')

/** `+998901234567` -> `90 123 45 67`, the way the phone field is typed. */
internal fun String.toLocalPhone(): String {
    // Digits, not the mask: the field stores digits and draws the mask itself.
    return UzbekPhone.accept(this)
}

// ---------------------------------------------------------------- app -> wire

/**
 * The whole onboarding flow as one request.
 *
 * Sent as a unit deliberately: a user who drops out halfway leaves no half-built
 * profile on the server, which is also why the app keeps her in the flow until this
 * call succeeds.
 */
fun AppState.toOnboardingRequest(timezone: String): OnboardingRequest = OnboardingRequest(
    name = name,
    language = language.toWire(),
    timezone = timezone,
    lifeStage = lifeStage.toWire(),
    birthDate = birthDate.toWireDate(),
    heightCm = heightCm.toIntOrNull(),
    weightKg = weightKg.toIntOrNull(),
    goals = goals.map { it.toWire() },
    // The server ignores the cycle baseline for stages that do not predict one.
    cycle = if (lifeStage.predictsCycle) {
        CycleBaseline(
            // The anchor the server predicts from when nothing is logged yet. Sending
            // it is what makes Today show a real cycle day on the first launch after
            // onboarding rather than an empty state.
            lastPeriodStart = lastPeriodStart,
            averageCycleLength = averageCycleLength,
            averagePeriodLength = averagePeriodLength,
            cycleIsRegular = cycleIsRegular,
            conceptionWindow = conceptionWindow?.toWire(),
            birthControl = birthControl?.toWire(),
        )
    } else {
        null
    },
    stage = toStageBaseline(),
    permissions = toPermissionGrants(),
    consents = toConsentGrants(),
    referredByDoctor = referredByDoctor,
    hasWearable = hasWearable,
    // Blank means she typed nothing; the server treats an unknown code as "no code"
    // rather than as an error, so a typo never blocks a sign-up.
    inviteCode = pendingInviteCode?.trim()?.takeIf { it.isNotEmpty() },
    firstCheckIn = toFirstCheckIn(),
)

/**
 * The check-in taken before sign-up — the "how do you feel" answer and the symptoms
 * she ticked — so the day she joined is not empty on the server.
 *
 * Null when she skipped both, so the server does not write an empty day. The tiles
 * record their catalogue key as she taps them, so what is sent does not depend on the
 * language she read them in.
 */
private fun AppState.toFirstCheckIn(): OnboardingCheckIn? {
    val keys = starterSymptomKeys.toList()
    val mood = if (moodAnswered) mood.toWire() else null
    if (mood == null && keys.isEmpty()) return null
    return OnboardingCheckIn(mood = mood, symptomKeys = keys)
}

/**
 * The stage-specific dates, or null for the stages that have none.
 *
 * Only the date belonging to the chosen stage is sent: a due date left over from a
 * pregnancy the user has since moved on from would otherwise follow her into
 * postpartum and be read as current.
 */
private fun AppState.toStageBaseline(): StageBaseline? = when (lifeStage) {
    LifeStage.Pregnancy -> dueDate?.let { StageBaseline(dueDate = it) }
    LifeStage.Postpartum -> babyBirthDate?.let { StageBaseline(birthDate = it) }
    else -> lastPeriodStart?.let { StageBaseline(lastPeriodStart = it) }
}

/** A partial update carrying only the fields the profile screens can edit. */
fun AppState.toUpdateProfileRequest(timezone: String? = null): UpdateProfileRequest =
    UpdateProfileRequest(
        name = name,
        language = language.toWire(),
        timezone = timezone,
        lifeStage = lifeStage.toWire(),
        birthDate = birthDate.toWireDate(),
        heightCm = heightCm.toIntOrNull(),
        weightKg = weightKg.toIntOrNull(),
        goals = goals.map { it.toWire() },
    )

fun AppState.toConsentGrants(): ConsentGrants = ConsentGrants(
    storeHealth = consentStoreHealth,
    aiInsights = consentAiInsights,
    analytics = consentAnalytics,
    marketing = false,
)

fun AppState.toPermissionGrants(): PermissionGrants = PermissionGrants(
    notifications = notificationsAllowed,
    healthData = healthDataAllowed,
    camera = cameraAllowed,
)

/** Copies server consents back onto the store, so Privacy shows what is actually set. */
fun AppState.applyServerConsents(consents: Consents) {
    consentStoreHealth = consents.storeHealth
    consentAiInsights = consents.aiInsights
    consentAnalytics = consents.analytics
}

private fun AppLanguage.toWire(): WireLanguage = when (this) {
    AppLanguage.Uz -> WireLanguage.UZ
    AppLanguage.Ru -> WireLanguage.RU
    AppLanguage.En -> WireLanguage.EN
}

private fun LifeStage.toWire(): WireLifeStage = when (this) {
    LifeStage.Cycle -> WireLifeStage.CYCLE
    LifeStage.TryingToConceive -> WireLifeStage.TRYING_TO_CONCEIVE
    LifeStage.Pregnancy -> WireLifeStage.PREGNANCY
    LifeStage.Postpartum -> WireLifeStage.POSTPARTUM
    LifeStage.Perimenopause -> WireLifeStage.PERIMENOPAUSE
    LifeStage.Menopause -> WireLifeStage.MENOPAUSE
}

private fun ConceptionWindow.toWire(): WireConceptionWindow = when (this) {
    ConceptionWindow.JustStarted -> WireConceptionWindow.JUST_STARTED
    ConceptionWindow.UnderThreeMonths -> WireConceptionWindow.UNDER_3_MONTHS
    ConceptionWindow.ThreeToSix -> WireConceptionWindow.THREE_TO_SIX_MONTHS
    ConceptionWindow.SixToTwelve -> WireConceptionWindow.SIX_TO_TWELVE_MONTHS
    ConceptionWindow.OverAYear -> WireConceptionWindow.OVER_A_YEAR
}

private fun BirthControl.toWire(): WireBirthControl = when (this) {
    BirthControl.None -> WireBirthControl.NONE
    BirthControl.StillUsing -> WireBirthControl.STILL_USING
    BirthControl.Pill -> WireBirthControl.PILL
    BirthControl.Iud -> WireBirthControl.IUD
    BirthControl.Barrier -> WireBirthControl.BARRIER
    BirthControl.Other -> WireBirthControl.OTHER
    BirthControl.Undisclosed -> WireBirthControl.UNDISCLOSED
}

private fun Goal.toWire(): WireGoal = when (this) {
    Goal.UnderstandCycle -> WireGoal.UNDERSTAND_CYCLE
    Goal.SleepBetter -> WireGoal.SLEEP_BETTER
    Goal.MoreEnergy -> WireGoal.MORE_ENERGY
    Goal.LessStress -> WireGoal.LESS_STRESS
    Goal.EatBalanced -> WireGoal.EAT_BALANCED
    Goal.DrinkWater -> WireGoal.DRINK_WATER
    Goal.BeActive -> WireGoal.BE_ACTIVE
    Goal.RememberMeds -> WireGoal.REMEMBER_MEDS
}

/**
 * Parses the `14.03.1994` the personal-details field produces.
 *
 * Returns null rather than throwing on anything unparseable — the field is free text and
 * optional, and a half-typed date must not take down the request that carries it.
 */
internal fun String.toWireDate(): kotlinx.datetime.LocalDate? {
    val parts = trim().split('.')
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31 || year !in 1900..2100) return null
    return runCatching { kotlinx.datetime.LocalDate(year, month, day) }.getOrNull()
}
