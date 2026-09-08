package org.example.project.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import androidx.compose.ui.graphics.Color
import org.example.project.i18n.strings
import org.example.project.model.AppLanguage
import org.example.project.model.AppState
import org.example.project.model.BirthControl
import org.example.project.model.ConceptionWindow
import org.example.project.model.MaxEnteredCycles
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.model.Mood
import org.example.project.model.deviceToday
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ErrorStrip
import org.example.project.ui.components.OtpInput
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraDialog
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.noRippleClickable

// ---------------------------------------------------------------- language

/** The flag and the native name of each language, as the deck's list shows them. */
private fun AppLanguage.flag(): String = when (this) {
    AppLanguage.Uz -> "\uD83C\uDDFA\uD83C\uDDFF"
    AppLanguage.Ru -> "\uD83C\uDDF7\uD83C\uDDFA"
    AppLanguage.En -> "\uD83C\uDDEC\uD83C\uDDE7"
}

/**
 * "Choose your language" — the first question of the run.
 *
 * It comes before everything else for the obvious reason: every later screen has to
 * be readable before it can be answered. Only Uzbek is written today, so the other
 * two say so rather than switching to a half-translated app.
 */
@Composable
fun LanguageQuestion(
    state: AppState,
    progress: Float,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(900)
    val t = strings.onboarding

    QuestionScaffold(
        title = t.languageTitle,
        subtitle = t.languageSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton(t.continueLabel, onNext) } },
    ) {
        // Tapping a language changes the app under her hand — the tab bar and the
        // profile are already in it by the time she reaches the next question — so the
        // row says what it is rather than promising it for later.
        AppLanguage.entries.forEachIndexed { index, language ->
            Reveal(entry.value, from = optionStart(index, base = 0.30f, step = 0.08f)) {
                AnswerRow(
                    label = language.native,
                    leading = language.flag(),
                    note = language.english,
                    noteAlwaysVisible = true,
                    selected = state.language == language,
                    onClick = { state.language = language },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- name

/**
 * "What should we call you?" — the first question, and the one that makes the rest
 * of the flow feel addressed to someone.
 *
 * The only question in the run with no skip: the profile cannot be saved without a
 * name — the server rejects a blank one — and every screen after this addresses her
 * by it. Offering to skip would end in a refusal twenty questions later.
 */
@Composable
fun NameQuestion(
    state: AppState,
    progress: Float,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val focus = LocalFocusManager.current
    QuestionScaffold(
        title = t.nameTitle,
        subtitle = t.nameSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.name.isNotBlank()) {
                SadoraButton(t.continueLabel, onNext)
            }
        },
    ) {
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.30f) {
            SadoraTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = t.nameLabel,
                placeholder = t.nameHint,
                leadingIcon = SadoraIcons.Profile,
                // The only field on the page, so Next would have nowhere to go.
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            )
        }
        Reveal(entry.value, from = 0.42f) {
            PrivacyNote(
                t.nameNote,
            )
        }
    }
}

/**
 * The lavender note the deck puts under the personal questions.
 *
 * It is a shield and a sentence rather than a link: what happens to the answer has to
 * be readable at the moment it is being given, not one tap away.
 */
@Composable
private fun PrivacyNote(text: String) {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.tile)
            .background(c.primary.copy(alpha = if (c.isDark) 0.18f else 0.08f))
            .padding(Spacing.sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        androidx.compose.material3.Icon(
            SadoraIcons.Shield,
            contentDescription = null,
            Modifier.size(org.example.project.design.IconSize.md),
            tint = c.textAccent,
        )
        Text(text, style = Sadora.type.body, color = c.muted)
    }
}

// ---------------------------------------------------------------- birth year

private const val FirstBirthYear = 1955
private const val LastBirthYear = 2012

/**
 * Year of birth on a wheel.
 *
 * Only the year is asked for. It is the part that changes the predictions, and a
 * full date is three spins of friction for accuracy nothing here uses — so the day
 * and month already on [AppState.birthDate] are kept as they are.
 */
@Composable
fun BirthYearQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val years = remember { (FirstBirthYear..LastBirthYear).map { it.toString() } }
    val current = remember { state.birthYear() }
    var index by remember { mutableStateOf((current - FirstBirthYear).coerceIn(0, years.lastIndex)) }

    QuestionScaffold(
        title = t.birthYearTitle,
        subtitle = t.birthYearSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton(t.continueLabel, onNext) } },
    ) {
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.28f) {
            WheelPicker(
                items = years,
                selectedIndex = index,
                onSelect = {
                    index = it
                    state.setBirthYear(FirstBirthYear + it)
                },
            )
        }
    }
}

/** The year on [AppState.birthDate], or a sensible middle if it cannot be read. */
private fun AppState.birthYear(): Int =
    birthDate.split('.').lastOrNull()?.toIntOrNull()?.takeIf { it in FirstBirthYear..LastBirthYear }
        ?: 1996

/** Rewrites only the year, leaving the stored day and month alone. */
private fun AppState.setBirthYear(year: Int) {
    val parts = birthDate.split('.')
    birthDate = if (parts.size == 3) "${parts[0]}.${parts[1]}.$year" else "01.01.$year"
}

// ---------------------------------------------------------------- focus

/** The icon that stands for each goal in the grid. */
private fun Goal.icon(): ImageVector = when (this) {
    Goal.UnderstandCycle -> SadoraIcons.Journey
    Goal.SleepBetter -> SadoraIcons.Moon
    Goal.MoreEnergy -> SadoraIcons.Sparkle
    Goal.LessStress -> SadoraIcons.Heart
    Goal.EatBalanced -> SadoraIcons.Nutrition
    Goal.DrinkWater -> SadoraIcons.Drop
    Goal.BeActive -> SadoraIcons.Target
    Goal.RememberMeds -> SadoraIcons.Pill
}

/**
 * "What can we help you with?" — the multi-select grid.
 *
 * Answers here decide what Today leads with, so the question is deliberately open:
 * as many as she likes, and the footer only appears once at least one is chosen.
 */
@Composable
fun FocusQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1100)
    val t = strings.onboarding
    val goals = remember { Goal.entries.toList() }

    QuestionScaffold(
        title = t.goalsTitle,
        subtitle = t.goalsSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.goals.isNotEmpty()) {
                SadoraButton(t.continueLabel, onNext)
            }
        },
    ) {
        // A hand-built grid rather than LazyVerticalGrid: the page already scrolls,
        // and nesting a lazy grid inside a scrolling column has no intrinsic height.
        goals.chunked(2).forEachIndexed { row, pair ->
            Reveal(entry.value, from = optionStart(row, base = 0.30f, step = 0.09f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    pair.forEach { goal ->
                        AnswerTile(
                            label = strings.common.goal(goal),
                            icon = goal.icon(),
                            selected = goal in state.goals,
                            onClick = { state.toggleGoal(goal) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Spacing.xxs))
        }
    }
}

// ---------------------------------------------------------------- life stage

/** The icon and tint the deck gives each stage in the list. */
private fun LifeStage.icon(): ImageVector = when (this) {
    LifeStage.Cycle -> SadoraIcons.Journey
    LifeStage.TryingToConceive -> SadoraIcons.Sparkle
    LifeStage.Pregnancy -> SadoraIcons.Heart
    LifeStage.Postpartum -> SadoraIcons.Bloom
    LifeStage.Perimenopause -> SadoraIcons.Moon
    LifeStage.Menopause -> SadoraIcons.Target
}

private fun LifeStage.tint(): Color = when (this) {
    LifeStage.Cycle -> Color(0xFF7B61FF)
    LifeStage.TryingToConceive -> Color(0xFFFF6FB8)
    LifeStage.Pregnancy -> Color(0xFFFF8E92)
    LifeStage.Postpartum -> Color(0xFFFFB020)
    LifeStage.Perimenopause -> Color(0xFF4FC3FF)
    LifeStage.Menopause -> Color(0xFF2BA57A)
}

@Composable
fun LifeStageQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1200)
    val t = strings.onboarding
    // Nothing is selected until she picks: the stage reshapes the whole app, so it
    // should never be answered by a default she never looked at.
    var picked by remember { mutableStateOf(false) }

    QuestionScaffold(
        title = t.stageTitle,
        subtitle = t.stageSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = picked) { SadoraButton(t.continueLabel, onNext) }
        },
    ) {
        LifeStage.entries.forEachIndexed { index, stage ->
            Reveal(entry.value, from = optionStart(index, base = 0.26f, step = 0.07f)) {
                AnswerRow(
                    label = strings.stages.title(stage),
                    icon = stage.icon(),
                    tint = stage.tint(),
                    note = t.stagePromise(stage),
                    selected = picked && state.lifeStage == stage,
                    onClick = {
                        state.lifeStage = stage
                        picked = true
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- referral

/**
 * The light question in the middle of the run.
 *
 * It asks nothing personal and answers on tap, which gives the sequence a beat of
 * momentum between the two heavier stretches.
 */
@Composable
fun ReferralQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onAnswered: () -> Unit,
) {
    val entry = rememberPageEntry(800)
    val t = strings.onboarding
    val answer = state.referredByDoctor

    QuestionScaffold(
        title = t.doctorTitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = answer != null) {
                SadoraButton(t.continueLabel, onAnswered)
            }
        },
    ) {
        listOf(true to strings.common.yes, false to t.no).forEachIndexed { index, (value, label) ->
            Reveal(entry.value, from = optionStart(index, base = 0.30f)) {
                AnswerRow(
                    label = label,
                    selected = answer == value,
                    onClick = { state.referredByDoctor = value },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- cycle

@Composable
fun CycleLengthQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val lengths = remember { (21..40).map { it.toString() } }
    // Seeded from the calendar answers when there were enough of them, so this question
    // confirms what her own dates say rather than asking her to guess it twice.
    val derived = remember { state.averageFromEnteredCycles() }
    var index by remember {
        mutableStateOf((state.averageCycleLength - 21).coerceIn(0, lengths.lastIndex))
    }

    QuestionScaffold(
        title = t.cycleLengthTitle,
        subtitle = if (derived != null) {
            t.cycleLengthDerived(derived)
        } else {
            t.cycleLengthHint
        },
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton(t.continueLabel, onNext) } },
    ) {
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.28f) {
            WheelPicker(
                items = lengths,
                selectedIndex = index,
                suffix = "kun",
                onSelect = {
                    index = it
                    state.averageCycleLength = 21 + it
                },
            )
        }
    }
}

@Composable
fun PeriodLengthQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val lengths = remember { (2..10).map { it.toString() } }
    var index by remember {
        mutableStateOf((state.averagePeriodLength - 2).coerceIn(0, lengths.lastIndex))
    }

    QuestionScaffold(
        title = t.periodLengthTitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton(t.continueLabel, onNext) } },
    ) {
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.28f) {
            WheelPicker(
                items = lengths,
                selectedIndex = index,
                suffix = "kun",
                onSelect = {
                    index = it
                    state.averagePeriodLength = 2 + it
                },
            )
        }
    }
}

// ---------------------------------------------------------------- feeling

/**
 * The check-in question.
 *
 * Its answers exist to be replied to rather than measured — each one opens into a
 * sentence that takes the mood seriously, which is what stops a long form from
 * reading like an interrogation.
 */
@Composable
fun FeelingQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1000)
    val t = strings.onboarding
    var chosen by remember { mutableStateOf(-1) }
    val name = state.name.trim()

    QuestionScaffold(
        title = t.feelingTitle(name),
        subtitle = t.feelingSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = chosen >= 0) { SadoraButton(t.continueLabel, onNext) }
        },
    ) {
        t.feelings.forEachIndexed { index, (label, score, note) ->
            Reveal(entry.value, from = optionStart(index, base = 0.28f, step = 0.08f)) {
                AnswerRow(
                    label = label,
                    note = note,
                    selected = chosen == index,
                    onClick = {
                        chosen = index
                        state.mood = moodForScore(score)
                        state.moodAnswered = true
                    },
                )
            }
        }
    }
}

private fun moodForScore(score: Int) =
    Mood.entries.firstOrNull { it.score == score } ?: Mood.Ok

// ---------------------------------------------------------------- body

/**
 * Height and weight, on wheels rather than in text fields.
 *
 * Weight in particular is a question people abandon forms over, so it says outright
 * that it is optional and the skip stays available.
 */
@Composable
fun BodyQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val heights = remember { (140..200).map { it.toString() } }
    val weights = remember { (35..150).map { it.toString() } }
    var heightIndex by remember {
        mutableStateOf(((state.heightCm.toIntOrNull() ?: 164) - 140).coerceIn(0, heights.lastIndex))
    }
    var weightIndex by remember {
        mutableStateOf(((state.weightKg.toIntOrNull() ?: 58) - 35).coerceIn(0, weights.lastIndex))
    }

    QuestionScaffold(
        title = t.bodyTitle,
        subtitle = t.bodySubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton(t.continueLabel, onNext) } },
    ) {
        Reveal(entry.value, from = 0.26f) {
            Column {
                Text(t.height, style = Sadora.type.caption, color = Sadora.colors.muted2)
                WheelPicker(
                    items = heights,
                    selectedIndex = heightIndex,
                    suffix = "sm",
                    onSelect = {
                        heightIndex = it
                        state.heightCm = (140 + it).toString()
                    },
                )
            }
        }
        Reveal(entry.value, from = 0.40f) {
            Column {
                Text(t.weight, style = Sadora.type.caption, color = Sadora.colors.muted2)
                WheelPicker(
                    items = weights,
                    selectedIndex = weightIndex,
                    suffix = "kg",
                    onSelect = {
                        weightIndex = it
                        state.weightKg = (35 + it).toString()
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- permissions

/**
 * The permissions ask, phrased as a question rather than a wall of switches.
 *
 * Each row is a toggle, so the footer is always available: declining everything is
 * a valid answer and should not look like a dead end.
 */
@Composable
fun PermissionsQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1000)
    val t = strings.onboarding
    QuestionScaffold(
        title = t.permissionsTitle,
        subtitle = t.permissionsSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton(t.continueLabel, onNext) } },
    ) {
        Reveal(entry.value, from = 0.28f) {
            AnswerRow(
                label = t.permissionReminders,
                icon = SadoraIcons.Bell,
                note = t.permissionRemindersNote,
                selected = state.notificationsAllowed,
                noteAlwaysVisible = true,
                onClick = { state.notificationsAllowed = !state.notificationsAllowed },
            )
        }
        Reveal(entry.value, from = 0.36f) {
            AnswerRow(
                label = t.permissionHealth,
                icon = SadoraIcons.Heart,
                tint = Color(0xFFFF6FB8),
                note = t.permissionHealthNote,
                selected = state.healthDataAllowed,
                noteAlwaysVisible = true,
                onClick = { state.healthDataAllowed = !state.healthDataAllowed },
            )
        }
        Reveal(entry.value, from = 0.44f) {
            AnswerRow(
                label = t.permissionCamera,
                icon = SadoraIcons.Camera,
                tint = Color(0xFF4FC3FF),
                note = t.permissionCameraNote,
                selected = state.cameraAllowed,
                noteAlwaysVisible = true,
                onClick = { state.cameraAllowed = !state.cameraAllowed },
            )
        }
    }
}

// ---------------------------------------------------------------- phone

/**
 * Sign-up, reached only after the profile questions are answered.
 *
 * Asking for the number last is deliberate: by this point the flow has already
 * given something back, so the account is the last step rather than the toll gate.
 */
@Composable
fun PhoneQuestion(
    state: AppState,
    busy: Boolean,
    error: String?,
    progress: Float,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onSignInInstead: () -> Unit,
) {
    val c = Sadora.colors
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val focus = LocalFocusManager.current
    val ready = state.phone.count { it.isDigit() } >= 9

    QuestionScaffold(
        title = t.phoneTitle,
        subtitle = t.phoneSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = true) {
                SadoraButton(
                    if (busy) t.sending else t.sendCode,
                    onSubmit,
                    enabled = ready && !busy,
                )
            }
            Text(
                t.haveAccount,
                style = Sadora.type.body,
                color = c.textAccent,
                modifier = Modifier
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .noRippleClickable(onClick = onSignInInstead)
                    .padding(top = Spacing.sm),
            )
        },
    ) {
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.28f) {
            SadoraTextField(
                value = state.phone,
                onValueChange = { state.phone = it },
                label = t.phoneLabel,
                leading = "+998",
                placeholder = "90 123 45 67",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            )
        }
        if (error != null) {
            Reveal(entry.value, from = 0.36f) { ErrorStrip(error) }
        }
        Reveal(entry.value, from = 0.42f) {
            DisclaimerNote(
                t.phoneNote,
                icon = "🔒",
            )
        }
    }
}

// ---------------------------------------------------------------- otp

/**
 * The six-digit code.
 *
 * The keypad is part of the page rather than the system keyboard, which is what
 * keeps the code boxes and the digits on screen together on a short phone.
 */
@Composable
fun OtpQuestion(
    phone: String,
    code: String,
    onCode: (String) -> Unit,
    busy: Boolean,
    error: String?,
    secondsLeft: Int,
    progress: Float,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
) {
    val c = Sadora.colors
    val entry = rememberPageEntry()
    val t = strings.onboarding

    QuestionScaffold(
        title = t.codeTitle,
        subtitle = t.codeSubtitle(phone),
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = code.length == 6) {
                SadoraButton(
                    if (busy) t.checking else t.confirm,
                    onVerify,
                    enabled = !busy,
                )
            }
            Text(
                if (secondsLeft > 0) t.resendIn(secondsLeft) else t.resend,
                style = Sadora.type.body,
                color = if (secondsLeft > 0) c.muted2 else c.textAccent,
                modifier = Modifier
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .noRippleClickable(enabled = secondsLeft == 0, onClick = onResend)
                    .padding(top = Spacing.sm),
            )
        },
    ) {
        Reveal(entry.value, from = 0.24f) {
            OtpInput(code = code, length = 6)
        }
        if (error != null) {
            Reveal(entry.value, from = 0.32f) { ErrorStrip(error) }
        }
        Reveal(entry.value, from = 0.36f) {
            DisclaimerNote(
                t.codeSecrecy,
                icon = "🔒",
            )
        }
        Reveal(entry.value, from = 0.44f) {
            NumberPad(
                onDigit = { if (code.length < 6) onCode(code + it) },
                onDelete = { onCode(code.dropLast(1)) },
            )
        }
    }
}

// ---------------------------------------------------------------- last period

/**
 * "Mark the days of your last three periods."
 *
 * A tap fills in a whole period, not a single day: she is marking the days she bled,
 * and how many that is came from the previous question rather than from her tapping
 * each one. Tapping anywhere inside a marked period clears it again.
 *
 * Three periods rather than one because of how the prediction is built — the server
 * measures the gaps between consecutive starts, so three marks give it two measured
 * cycles and a spread to state confidence from, where one can only anchor an assumed
 * length. One is required, three is asked for, and continuing with fewer says why
 * before it lets her past.
 */
@Composable
fun LastPeriodQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val today = remember { deviceToday() }
    val marked = state.recentPeriodStarts.size
    var askAboutFewer by remember { mutableStateOf(false) }

    // A Box, not the Column the flow wraps each step in: the dialog has to lie over the
    // calendar, and as a Column sibling it would be laid out below it, off screen.
    Box(Modifier.fillMaxSize()) {
        QuestionScaffold(
            title = t.periodsTitle,
            subtitle = t.periodsSubtitle(state.averagePeriodLength),
            progress = progress,
            onBack = onBack,
            // No skip: without at least one period there is nothing to predict from, and a
            // cycle app that cannot predict is not worth setting up.
            onSkip = null,
            entry = entry,
            footer = {
                AnswerFooter(visible = marked > 0) {
                    SadoraButton(
                        t.continueLabel,
                        onClick = {
                            if (marked < MaxEnteredCycles) askAboutFewer = true else onNext()
                        },
                    )
                }
            },
        ) {
            Reveal(entry.value, from = 0.18f) {
                EnteredCyclesSummary(count = marked, averageCycleLength = state.averageFromEnteredCycles())
            }
            Reveal(entry.value, from = 0.26f) {
                CalendarPicker(
                    isSelected = state::isPeriodDay,
                    onSelect = { state.togglePeriodDay(it, today) },
                    today = today,
                    // Three cycles reach back about three months, so the calendar has to
                    // show enough of the year for the oldest of them to be reachable.
                    monthsBack = 5,
                    // A period cannot have started tomorrow, and one from half a year ago
                    // is not a baseline worth predicting from.
                    range = today.minus(6, DateTimeUnit.MONTH)..today,
                )
            }
        }

        // Advice, not a wall: the confirm button goes on regardless, and dismissing the
        // dialog leaves her on the calendar, which is the safer of the two outcomes.
        SadoraDialog(
            visible = askAboutFewer,
            title = t.markMore,
            body = t.markMoreBody(marked),
            confirmText = t.continueLabel,
            onConfirm = {
                askAboutFewer = false
                onNext()
            },
            cancelText = t.iWillMark,
            onDismiss = { askAboutFewer = false },
            destructive = false,
        )
    }
}

/**
 * The running read-out above the calendar.
 *
 * It reports the average as soon as two periods exist, which is the point at which the
 * question stops being a form field and starts showing her something she did not
 * already know.
 */
@Composable
private fun EnteredCyclesSummary(count: Int, averageCycleLength: Int?) {
    val c = Sadora.colors
    val t = strings.onboarding
    val filled = count.coerceAtMost(MaxEnteredCycles)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(MaxEnteredCycles) { index ->
                val on = index < filled
                val dot by animateColorAsState(
                    if (on) c.primary else c.line,
                    tween(240),
                    label = "cycle-dot",
                )
                Box(
                    Modifier
                        .size(width = if (on) 26.dp else 10.dp, height = 10.dp)
                        .clip(Radius.chip)
                        .background(dot),
                )
            }
        }
        Text(
            when {
                averageCycleLength != null ->
                    t.markedWithAverage(filled, MaxEnteredCycles, averageCycleLength)
                filled > 0 -> t.markedMoreNeeded(filled, MaxEnteredCycles)
                else -> t.markAPeriodStart
            },
            style = Sadora.type.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- regularity

@Composable
fun CycleRegularityQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(800)
    val t = strings.onboarding
    // The index rather than the value: two of the three answers mean "not regular",
    // and only the one she tapped should light up.
    var chosen by remember { mutableStateOf(-1) }

    QuestionScaffold(
        title = t.regularityTitle,
        subtitle = t.regularitySubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = chosen >= 0) { SadoraButton(t.continueLabel, onNext) }
        },
    ) {
        val options = listOf(
            Triple(true, t.regularYes, t.regularYesNote),
            Triple(false, t.regularNo, t.regularNoNote),
            Triple(false, t.regularUnknown, t.regularUnknownNote),
        )
        options.forEachIndexed { index, (regular, label, note) ->
            Reveal(entry.value, from = optionStart(index, base = 0.28f)) {
                AnswerRow(
                    label = label,
                    note = note,
                    selected = chosen == index,
                    onClick = {
                        state.cycleIsRegular = regular
                        chosen = index
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- sensitive notice

/**
 * The notice in front of the questions about contraception and conception.
 *
 * Offering to skip the whole block, rather than each question inside it, is the point:
 * someone who does not want to be asked should not have to decline three times.
 */
@Composable
fun SensitiveNoticeScreen(
    onContinue: () -> Unit,
    onSkipBlock: () -> Unit,
) {
    val c = Sadora.colors
    val entry = rememberPageEntry(900)
    val t = strings.onboarding

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Reveal(entry.value, from = 0.02f) {
            Box(
                Modifier.size(72.dp).clip(Radius.chip).background(c.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    SadoraIcons.Lock,
                    contentDescription = null,
                    Modifier.size(32.dp),
                    tint = c.textAccent,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.18f) {
            Text(
                t.sensitiveTitle,
                style = Sadora.type.h1,
                color = c.text,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Reveal(entry.value, from = 0.30f) {
            Text(
                t.sensitiveBody,
                style = Sadora.type.body,
                color = c.muted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        Reveal(entry.value, from = 0.50f) {
            SadoraButton(t.continueLabel, onContinue)
        }
        Spacer(Modifier.height(Spacing.xs))
        Reveal(entry.value, from = 0.60f) {
            Text(
                t.skipTheseQuestions,
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .noRippleClickable(onClick = onSkipBlock)
                    .padding(Spacing.sm),
            )
        }
    }
}

// ---------------------------------------------------------------- birth control

@Composable
fun BirthControlQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1200)
    val t = strings.onboarding

    QuestionScaffold(
        title = t.birthControlTitle,
        subtitle = t.birthControlSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.birthControl != null) {
                SadoraButton(t.continueLabel, onNext)
            }
        },
    ) {
        BirthControl.entries.forEachIndexed { index, option ->
            Reveal(entry.value, from = optionStart(index, base = 0.24f, step = 0.06f)) {
                AnswerRow(
                    label = t.birthControl(option),
                    note = t.birthControlNote(option),
                    selected = state.birthControl == option,
                    onClick = { state.birthControl = option },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- conception

@Composable
fun ConceptionWindowQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1000)
    val t = strings.onboarding

    QuestionScaffold(
        title = t.conceptionTitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.conceptionWindow != null) {
                SadoraButton(t.continueLabel, onNext)
            }
        },
    ) {
        ConceptionWindow.entries.forEachIndexed { index, option ->
            Reveal(entry.value, from = optionStart(index, base = 0.26f)) {
                AnswerRow(
                    label = strings.common.conceptionWindow(option),
                    note = t.conceptionNote(option),
                    selected = state.conceptionWindow == option,
                    onClick = { state.conceptionWindow = option },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- stage dates

@Composable
fun DueDateQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val today = remember { deviceToday() }

    QuestionScaffold(
        title = t.dueDateTitle,
        subtitle = t.dueDateSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.dueDate != null) {
                SadoraButton(t.continueLabel, onNext)
            }
        },
    ) {
        Reveal(entry.value, from = 0.24f) {
            CalendarPicker(
                isSelected = { it == state.dueDate },
                onSelect = { state.dueDate = it },
                today = today,
                monthsBack = 0,
                monthsForward = 9,
                range = today..today.plus(10, DateTimeUnit.MONTH),
            )
        }
    }
}

@Composable
fun BirthDateQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val t = strings.onboarding
    val today = remember { deviceToday() }

    QuestionScaffold(
        title = t.birthDateTitle,
        subtitle = t.birthDateSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.babyBirthDate != null) {
                SadoraButton(t.continueLabel, onNext)
            }
        },
    ) {
        Reveal(entry.value, from = 0.24f) {
            CalendarPicker(
                isSelected = { it == state.babyBirthDate },
                onSelect = { state.babyBirthDate = it },
                today = today,
                monthsBack = 12,
                range = today.minus(24, DateTimeUnit.MONTH)..today,
            )
        }
    }
}

// ---------------------------------------------------------------- symptoms

/**
 * The glyph each starter symptom shows.
 *
 * The words come from the strings; only the order is fixed here, so the two lists have
 * to stay the same length — which is what the test in `StringsTest` checks.
 */
private val onboardingSymptomIcons = listOf(
    SadoraIcons.Heart,
    SadoraIcons.Moon,
    SadoraIcons.Drop,
    SadoraIcons.Bloom,
    SadoraIcons.Journey,
    SadoraIcons.Sparkle,
)

/**
 * The first symptom log, taken during onboarding.
 *
 * It writes straight into [AppState.symptoms], which Today and the cycle day screen
 * already read — so the app is not empty the first time she opens it.
 */
@Composable
fun SymptomsQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1100)
    val t = strings.onboarding
    val name = state.name.trim()

    QuestionScaffold(
        title = t.symptomsTitle(name),
        subtitle = t.symptomsSubtitle,
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = true) {
                SadoraButton(
                    if (state.symptoms.isEmpty()) t.continueLabel else t.saveSymptoms,
                    onNext,
                )
            }
        },
    ) {
        t.starterSymptoms.zip(onboardingSymptomIcons).chunked(2).forEachIndexed { row, pair ->
            Reveal(entry.value, from = optionStart(row, base = 0.28f, step = 0.09f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    pair.forEach { (symptom, icon) ->
                        AnswerTile(
                            label = symptom.label,
                            icon = icon,
                            selected = symptom.label in state.symptoms,
                            onClick = { state.toggleStarterSymptom(symptom.key, symptom.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Spacing.xxs))
        }
    }
}
