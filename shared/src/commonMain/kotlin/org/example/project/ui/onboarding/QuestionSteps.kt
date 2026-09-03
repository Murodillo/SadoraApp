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
import org.example.project.model.AppState
import org.example.project.model.BirthControl
import org.example.project.model.ConceptionWindow
import org.example.project.model.MaxEnteredCycles
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.model.Mood
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ErrorStrip
import org.example.project.ui.components.OtpInput
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraDialog
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.noRippleClickable

// ---------------------------------------------------------------- name

/**
 * "What should we call you?" — the first question, and the one that makes the rest
 * of the flow feel addressed to someone.
 */
@Composable
fun NameQuestion(
    state: AppState,
    progress: Float,
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry()
    val focus = LocalFocusManager.current
    QuestionScaffold(
        title = "Sizni qanday chaqiraylik?",
        subtitle = "Keling, tanishamiz. Bu ismni keyin ham o'zgartira olasiz.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        // Language belongs on the first page someone reads, not behind a step of its
        // own: by the time a separate question could ask, she has already had to read
        // her way here.
        topEnd = {
            LanguageSwitch(
                selected = state.language,
                onSelect = { state.language = it },
            )
        },
        footer = {
            AnswerFooter(visible = state.name.isNotBlank()) {
                SadoraButton("Davom etish", onNext)
            }
        },
    ) {
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.30f) {
            SadoraTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = "Ism",
                placeholder = "Ismingiz",
                // The only field on the page, so Next would have nowhere to go.
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            )
        }
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
    val years = remember { (FirstBirthYear..LastBirthYear).map { it.toString() } }
    val current = remember { state.birthYear() }
    var index by remember { mutableStateOf((current - FirstBirthYear).coerceIn(0, years.lastIndex)) }

    QuestionScaffold(
        title = "Qaysi yilda tug'ilgansiz?",
        subtitle = "Yosh bashoratlarni aniqroq qiladi.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton("Davom etish", onNext) } },
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
    val goals = remember { Goal.entries.toList() }

    QuestionScaffold(
        title = "Sizga nimada yordam beraylik?",
        subtitle = "Xohlaganingizcha tanlang.",
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.goals.isNotEmpty()) {
                SadoraButton("Davom etish", onNext)
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
                            label = goal.label,
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

/** What SADORA says back once a stage is chosen. */
private fun LifeStage.reassurance(): String = when (this) {
    LifeStage.Cycle -> "Siklingizni kuzatamiz va keyingi hayzni oldindan aytamiz."
    LifeStage.TryingToConceive ->
        "Unumdor kunlarni belgilaymiz va tayyorgarlikda yoningizda bo'lamiz."
    LifeStage.Pregnancy -> "Har haftaning o'zgarishlarini va tekshiruvlarni kuzatamiz."
    LifeStage.Postpartum -> "Tiklanish, emizish va kayfiyatga alohida e'tibor beramiz."
    LifeStage.Perimenopause -> "Simptomlar, uyqu va energiyani birga kuzatib boramiz."
    LifeStage.Menopause -> "Salomatlik maqsadlariga qaratilgan kundalik yordam beramiz."
}

@Composable
fun LifeStageQuestion(
    state: AppState,
    progress: Float,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val entry = rememberPageEntry(1200)
    // Nothing is selected until she picks: the stage reshapes the whole app, so it
    // should never be answered by a default she never looked at.
    var picked by remember { mutableStateOf(false) }

    QuestionScaffold(
        title = "Hozir qaysi bosqichdasiz?",
        subtitle = "Keyingi savollar va ilovaning o'zi shu tanlovga moslashadi.",
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = picked) { SadoraButton("Davom etish", onNext) }
        },
    ) {
        LifeStage.entries.forEachIndexed { index, stage ->
            Reveal(entry.value, from = optionStart(index, base = 0.26f, step = 0.07f)) {
                AnswerRow(
                    label = stage.title,
                    leading = stage.glyph,
                    note = stage.reassurance(),
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
    progress: Float,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onAnswered: () -> Unit,
) {
    val entry = rememberPageEntry(800)
    var answer by remember { mutableStateOf<Boolean?>(null) }

    QuestionScaffold(
        title = "SADORA'ni sizga shifokor tavsiya qildimi?",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = answer != null) {
                SadoraButton("Davom etish", onAnswered)
            }
        },
    ) {
        listOf(true to "Ha", false to "Yo'q").forEachIndexed { index, (value, label) ->
            Reveal(entry.value, from = optionStart(index, base = 0.30f)) {
                AnswerRow(
                    label = label,
                    selected = answer == value,
                    onClick = { answer = value },
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
    val lengths = remember { (21..40).map { it.toString() } }
    // Seeded from the calendar answers when there were enough of them, so this question
    // confirms what her own dates say rather than asking her to guess it twice.
    val derived = remember { state.averageFromEnteredCycles() }
    var index by remember {
        mutableStateOf((state.averageCycleLength - 21).coerceIn(0, lengths.lastIndex))
    }

    QuestionScaffold(
        title = "Siklingiz odatda necha kun davom etadi?",
        subtitle = if (derived != null) {
            "Belgilagan sanalaringizdan $derived kun chiqdi. Noto'g'ri bo'lsa, to'g'rilang."
        } else {
            "Aniq bilmasangiz, taxminiy son ham yetarli — keyin o'zi aniqlashadi."
        },
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton("Davom etish", onNext) } },
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
    val lengths = remember { (2..10).map { it.toString() } }
    var index by remember {
        mutableStateOf((state.averagePeriodLength - 2).coerceIn(0, lengths.lastIndex))
    }

    QuestionScaffold(
        title = "Hayz necha kun davom etadi?",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton("Davom etish", onNext) } },
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

/** The five moods, each with what SADORA says back. */
private val feelings = listOf(
    Triple("Yaxshi — hammasi joyida 🙂", 4, "Ajoyib. Shu holatni ushlab turishga yordam beramiz."),
    Triple("Charchaganman 😴", 2, "Uyqu va energiyani birinchi o'ringa qo'yamiz."),
    Triple("Xavotirdaman 😟", 2, "Sekin boshlaymiz. Faqat o'zingiz xohlagan narsani yozasiz."),
    Triple("Tanamni yaxshiroq bilmoqchiman ✨", 3, "Aynan shu uchun ham SADORA bor."),
)

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
    var chosen by remember { mutableStateOf(-1) }
    val name = state.name.trim()

    QuestionScaffold(
        title = if (name.isEmpty()) "O'zingizni qanday his qilyapsiz?" else "$name, o'zingizni qanday his qilyapsiz?",
        subtitle = "Rostini ayting — javobingizga qarab boshlashni moslaymiz.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = chosen >= 0) { SadoraButton("Davom etish", onNext) }
        },
    ) {
        feelings.forEachIndexed { index, (label, score, note) ->
            Reveal(entry.value, from = optionStart(index, base = 0.28f, step = 0.08f)) {
                AnswerRow(
                    label = label,
                    note = note,
                    selected = chosen == index,
                    onClick = {
                        chosen = index
                        state.mood = moodForScore(score)
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
    val heights = remember { (140..200).map { it.toString() } }
    val weights = remember { (35..150).map { it.toString() } }
    var heightIndex by remember {
        mutableStateOf(((state.heightCm.toIntOrNull() ?: 164) - 140).coerceIn(0, heights.lastIndex))
    }
    var weightIndex by remember {
        mutableStateOf(((state.weightKg.toIntOrNull() ?: 58) - 35).coerceIn(0, weights.lastIndex))
    }

    QuestionScaffold(
        title = "Bo'y va vazningiz",
        subtitle = "Ixtiyoriy. Hech kimga ko'rsatilmaydi va istalgan vaqtda o'chiriladi.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton("Davom etish", onNext) } },
    ) {
        Reveal(entry.value, from = 0.26f) {
            Column {
                Text("Bo'y", style = Sadora.type.caption, color = Sadora.colors.muted2)
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
                Text("Vazn", style = Sadora.type.caption, color = Sadora.colors.muted2)
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
    QuestionScaffold(
        title = "Nimalarga ruxsat berasiz?",
        subtitle = "Har birini keyin Profil bo'limidan o'zgartira olasiz.",
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = { AnswerFooter(visible = true) { SadoraButton("Davom etish", onNext) } },
    ) {
        Reveal(entry.value, from = 0.28f) {
            AnswerRow(
                label = "Eslatmalar",
                leading = "🔔",
                note = "Hayz, dori va tekshiruv vaqtini eslatib turamiz.",
                selected = state.notificationsAllowed,
                noteAlwaysVisible = true,
                onClick = { state.notificationsAllowed = !state.notificationsAllowed },
            )
        }
        Reveal(entry.value, from = 0.36f) {
            AnswerRow(
                label = "Salomatlik ma'lumotlari",
                leading = "❤️",
                note = "Qadamlar va uyquni soatingizdan o'qiymiz.",
                selected = state.healthDataAllowed,
                noteAlwaysVisible = true,
                onClick = { state.healthDataAllowed = !state.healthDataAllowed },
            )
        }
        Reveal(entry.value, from = 0.44f) {
            AnswerRow(
                label = "Kamera",
                leading = "📷",
                note = "Ovqatni suratga olib, tarkibini aniqlash uchun.",
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
    val focus = LocalFocusManager.current
    val ready = state.phone.count { it.isDigit() } >= 9

    QuestionScaffold(
        title = "Raqamingizni kiriting",
        subtitle = "Javoblaringizni saqlash uchun bir martalik kod yuboramiz.",
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = true) {
                SadoraButton(
                    if (busy) "Yuborilmoqda…" else "Kodni yuborish",
                    onSubmit,
                    enabled = ready && !busy,
                )
            }
            Text(
                "Hisobim bor · Kirish",
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
                label = "Telefon raqami",
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
                "Raqam faqat kirish uchun ishlatiladi va reklama uchun berilmaydi.",
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

    QuestionScaffold(
        title = "Kodni kiriting",
        subtitle = "+998 $phone raqamiga 6 xonali kod yubordik.",
        progress = progress,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = code.length == 6) {
                SadoraButton(
                    if (busy) "Tekshirilmoqda…" else "Tasdiqlash",
                    onVerify,
                    enabled = !busy,
                )
            }
            Text(
                if (secondsLeft > 0) "Qayta yuborish · ${secondsLeft}s" else "Kodni qayta yuborish",
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
                "Kodni hech kimga aytmang. SADORA xodimlari kodni so'ramaydi.",
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
    val today = remember { deviceToday() }
    val marked = state.recentPeriodStarts.size
    var askAboutFewer by remember { mutableStateOf(false) }

    // A Box, not the Column the flow wraps each step in: the dialog has to lie over the
    // calendar, and as a Column sibling it would be laid out below it, off screen.
    Box(Modifier.fillMaxSize()) {
        QuestionScaffold(
            title = "Oxirgi hayzlaringiz qachon bo'lgan?",
            subtitle = "Hayz boshlangan kunni bosing — qolgan " +
                "${state.averagePeriodLength} kun o'zi belgilanadi. Keyin kunlarni " +
                "bittalab qo'shish yoki olib tashlash mumkin.",
            progress = progress,
            onBack = onBack,
            // No skip: without at least one period there is nothing to predict from, and a
            // cycle app that cannot predict is not worth setting up.
            onSkip = null,
            entry = entry,
            footer = {
                AnswerFooter(visible = marked > 0) {
                    SadoraButton(
                        "Davom etish",
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
            title = "Yana belgilaysizmi?",
            body = "Hozir $marked ta hayz belgilandi. Uchtasi belgilansa, siklingiz " +
                "uzunligini o'lchay olamiz va bashorat ancha aniq bo'ladi.",
            confirmText = "Baribir davom etish",
            onConfirm = {
                askAboutFewer = false
                onNext()
            },
            cancelText = "Belgilayman",
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
                    "$filled/$MaxEnteredCycles belgilandi · o'rtacha sikl $averageCycleLength kun"
                filled > 0 -> "$filled/$MaxEnteredCycles belgilandi · yana belgilang"
                else -> "Hayz boshlangan kunni belgilang"
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
    // The index rather than the value: two of the three answers mean "not regular",
    // and only the one she tapped should light up.
    var chosen by remember { mutableStateOf(-1) }

    QuestionScaffold(
        title = "Siklingiz muntazammi?",
        subtitle = "Har oy taxminan bir xil kunda keladimi?",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = chosen >= 0) { SadoraButton("Davom etish", onNext) }
        },
    ) {
        val options = listOf(
            Triple(true, "Ha, muntazam", "Yaxshi — bashoratlar boshidanoq aniqroq bo'ladi."),
            Triple(
                false,
                "Yo'q, o'zgarib turadi",
                "Buni hisobga olamiz va bashoratlarga ishonch darajasini ko'rsatamiz.",
            ),
            Triple(
                false,
                "Bilmayman",
                "Muammo emas. Bir necha oy kuzatgach, o'zi ayon bo'ladi.",
            ),
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
                Modifier.size(64.dp).clip(Radius.chip).background(c.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", style = Sadora.type.display, color = c.accentText)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.18f) {
            Text(
                "Keyingi savollar shaxsiy",
                style = Sadora.type.h1,
                color = c.text,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Reveal(entry.value, from = 0.30f) {
            Text(
                "Kontratsepsiya va homiladorlikni rejalashtirish haqida so'raymiz. " +
                    "Bu savollar bashoratlarni aniqroq qiladi, lekin javob berish " +
                    "majburiy emas.",
                style = Sadora.type.body,
                color = c.muted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        Reveal(entry.value, from = 0.50f) {
            SadoraButton("Davom etish", onContinue)
        }
        Spacer(Modifier.height(Spacing.xs))
        Reveal(entry.value, from = 0.60f) {
            Text(
                "Bu savollarni o'tkazib yuborish",
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

    QuestionScaffold(
        title = "Oxirgi 6 oyda kontratsepsiyadan foydalanganmisiz?",
        subtitle = "Ba'zi usullar siklga ta'sir qiladi, shuning uchun so'rayapmiz.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.birthControl != null) {
                SadoraButton("Davom etish", onNext)
            }
        },
    ) {
        BirthControl.entries.forEachIndexed { index, option ->
            Reveal(entry.value, from = optionStart(index, base = 0.24f, step = 0.06f)) {
                AnswerRow(
                    label = option.label,
                    note = option.note(),
                    selected = state.birthControl == option,
                    onClick = { state.birthControl = option },
                )
            }
        }
    }
}

/** What SADORA does with each answer, said plainly. */
private fun BirthControl.note(): String? = when (this) {
    BirthControl.Pill, BirthControl.Iud ->
        "Gormonal usuldan keyin sikl bir necha oy tiklanadi — bashoratlarni ehtiyotkorlik bilan beramiz."
    BirthControl.StillUsing ->
        "Gormonal usul davomida ovulyatsiya bo'lmaydi, shuning uchun unumdor kunlarni ko'rsatmaymiz."
    else -> null
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

    QuestionScaffold(
        title = "Qachondan beri homiladorlikka harakat qilyapsiz?",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.conceptionWindow != null) {
                SadoraButton("Davom etish", onNext)
            }
        },
    ) {
        ConceptionWindow.entries.forEachIndexed { index, option ->
            Reveal(entry.value, from = optionStart(index, base = 0.26f)) {
                AnswerRow(
                    label = option.label,
                    note = option.note(),
                    selected = state.conceptionWindow == option,
                    onClick = { state.conceptionWindow = option },
                )
            }
        }
    }
}

private fun ConceptionWindow.note(): String? = when (this) {
    ConceptionWindow.JustStarted ->
        "Yo'lning boshi — savollar ko'p bo'ladi va biz har birida yoningizdamiz."
    ConceptionWindow.SixToTwelve, ConceptionWindow.OverAYear ->
        "Bir yildan oshgan bo'lsa, shifokorga murojaat qilish tavsiya etiladi. Buni ham eslatib turamiz."
    else -> null
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
    val today = remember { deviceToday() }

    QuestionScaffold(
        title = "Tug'ilish sanasi qachon kutilyapti?",
        subtitle = "Shifokor aytgan taxminiy sanani belgilang.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.dueDate != null) {
                SadoraButton("Davom etish", onNext)
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
    val today = remember { deviceToday() }

    QuestionScaffold(
        title = "Farzandingiz qachon tug'ilgan?",
        subtitle = "Tiklanish bosqichlarini shu sanadan hisoblaymiz.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = state.babyBirthDate != null) {
                SadoraButton("Davom etish", onNext)
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

/** The symptoms the onboarding check-in offers, with the glyph each shows. */
private val onboardingSymptoms = listOf(
    "Qorin og'rig'i" to SadoraIcons.Heart,
    "Charchoq" to SadoraIcons.Moon,
    "Shishish" to SadoraIcons.Drop,
    "Ko'krak og'rig'i" to SadoraIcons.Bloom,
    "Bel og'rig'i" to SadoraIcons.Journey,
    "Bosh og'rig'i" to SadoraIcons.Sparkle,
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
    val name = state.name.trim()

    QuestionScaffold(
        title = if (name.isEmpty()) "Bugun nimani sezyapsiz?" else "$name, bugun nimani sezyapsiz?",
        subtitle = "Bir nechtasini tanlashingiz mumkin. Hech qaysisi bo'lmasa — o'tkazib yuboring.",
        progress = progress,
        onBack = onBack,
        onSkip = onSkip,
        entry = entry,
        footer = {
            AnswerFooter(visible = true) {
                SadoraButton(
                    if (state.symptoms.isEmpty()) "Davom etish" else "Belgilarni saqlash",
                    onNext,
                )
            }
        },
    ) {
        onboardingSymptoms.chunked(2).forEachIndexed { row, pair ->
            Reveal(entry.value, from = optionStart(row, base = 0.28f, step = 0.09f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    pair.forEach { (label, icon) ->
                        AnswerTile(
                            label = label,
                            icon = icon,
                            selected = label in state.symptoms,
                            onClick = {
                                if (!state.symptoms.remove(label)) state.symptoms.add(label)
                            },
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
