package org.example.project.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.example.project.data.AuthDestination
import org.example.project.data.SadoraController
import org.example.project.nav.AppPhase
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.OtpChallenge
import org.example.project.model.AppLanguage
import org.example.project.model.AppState
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.ConsentRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.OptionRow
import org.example.project.ui.components.OtpInput
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCheckbox
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.TabSwitch
import org.example.project.ui.components.noRippleClickable

/** Shared layout for a numbered onboarding step: header, body, pinned footer. */
@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    step: String?,
    onBack: (() -> Unit)?,
    footer: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    Column(Modifier.fillMaxSize()) {
        SadoraTopBar(title = "", onBack = onBack, step = step)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = Sadora.type.h1, color = c.text)
                Text(subtitle, style = Sadora.type.body, color = c.muted)
            }
            content()
            Spacer(Modifier.height(Spacing.xs))
        }
        Column(
            Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            footer()
        }
    }
}

// ---------------------------------------------------------------- splash

/**
 * Splash. The wordmark breathes and the progress line animates while the app
 * restores session state.
 */
@Composable
fun SplashScreen(onReady: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    LaunchedEffect(Unit) {
        delay(1400)
        onReady()
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.secondary, c.primary))),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", style = Sadora.type.display, color = if (c.isDark) c.bg else androidx.compose.ui.graphics.Color.White)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "SADORA",
            style = Sadora.type.h1.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.28f, androidx.compose.ui.unit.TextUnitType.Em)),
            color = c.text,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text("HAR BIR AYOL. HAR BIR LAHZA.", style = Sadora.type.caption, color = c.secondary)
        Spacer(Modifier.height(Spacing.xl))
        org.example.project.ui.components.SadoraProgressBar(0.6f, gradient = true, height = 4.dp)
        Spacer(Modifier.height(Spacing.sm))
        Text("Ma'lumotlar yuklanmoqda…", style = Sadora.type.body, color = c.muted)
    }
}

// ---------------------------------------------------------------- intro

private data class IntroContent(
    val title: String,
    val body: String,
    val primary: String,
    val secondary: String,
)

private val introSlides = listOf(
    IntroContent(
        "Har bir ayol. Har bir lahza.",
        "Sikl, homiladorlik, menopauza, uyqu, ovqatlanish va kayfiyat — bir joyda, sizga moslashgan holda.",
        "Davom etish",
        "O'tkazib yuborish",
    ),
    IntroContent(
        "Raqamlarni tushunadigan yordamchi",
        "SADORA AI ma'lumotlaringizni o'qib, bugun nima muhim ekanini oddiy tilda aytadi.",
        "Davom etish",
        "O'tkazib yuborish",
    ),
    IntroContent(
        "Ma'lumot sizniki",
        "Nimani yozish va nimani ulashishni o'zingiz tanlaysiz. Istalgan vaqtda eksport yoki o'chirish mumkin.",
        "Boshlash",
        "Hisobim bor",
    ),
)

@Composable
fun IntroSlide(index: Int, onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val slide = introSlides[index]
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        ImagePlaceholder(
            Modifier.fillMaxWidth().aspectRatio(1.05f),
            shape = Radius.card,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(introSlides.size) { dot ->
                Box(
                    Modifier
                        .size(width = if (dot == index) 22.dp else 7.dp, height = 7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (dot == index) c.primary else c.line),
                )
            }
        }
        Text(slide.title, style = Sadora.type.h1, color = c.text)
        Text(slide.body, style = Sadora.type.body, color = c.muted)
        Spacer(Modifier.weight(1f))
        SadoraButton(slide.primary, onNext)
        SadoraButton(slide.secondary, onSkip, tone = ButtonTone.Ghost)
        Spacer(Modifier.height(Spacing.md))
    }
}

// ---------------------------------------------------------------- 1/9 language

@Composable
fun LanguageStep(state: AppState, step: String?, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(
        title = "Tilni tanlang",
        subtitle = "Keyinroq Profil bo'limida o'zgartirishingiz mumkin.",
        step = step,
        onBack = onBack,
        footer = { SadoraButton("Davom etish", onNext) },
    ) {
        AppLanguage.entries.forEach { language ->
            OptionRow(
                title = language.native,
                subtitle = language.english,
                leading = language.code,
                selected = state.language == language,
                onClick = { state.language = language },
            )
        }
    }
}

// ---------------------------------------------------------------- 2/9 sign up

@Composable
fun SignUpStep(
    state: AppState,
    controller: SadoraController,
    step: String?,
    onBack: () -> Unit,
    onChallenge: (OtpChallenge) -> Unit,
    onAuthenticated: (AuthDestination) -> Unit,
    onSignInInstead: () -> Unit,
) {
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(0) }
    var agreed by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }

    StepScaffold(
        title = "Hisob yaratish",
        subtitle = "Ma'lumotlaringiz shifrlangan holda saqlanadi.",
        step = step,
        onBack = onBack,
        footer = {
            SadoraButton(
                if (method == 0) "Kodni yuborish" else "Ro'yxatdan o'tish",
                enabled = agreed && !controller.busy,
                onClick = {
                    scope.launch {
                        if (method == 0) {
                            controller.requestOtp(state.phone)?.let(onChallenge)
                        } else {
                            controller.registerWithEmail(state.email, password, state.name)
                                ?.let(onAuthenticated)
                        }
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hisobim bor · ", style = Sadora.type.body, color = c.muted)
                Text(
                    "Kirish",
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textAccent,
                    modifier = Modifier.noRippleClickable(onClick = onSignInInstead),
                )
            }
        },
    ) {
        SadoraButton(
            "Apple bilan davom etish",
            tone = ButtonTone.Secondary,
            leading = "",
            enabled = !controller.busy,
            // The real token comes from the platform SDK; until that is wired the call
            // goes out with an empty one and the server rejects it, surfacing an error
            // rather than silently pretending to sign in.
            onClick = {
                scope.launch {
                    controller.signInWithSocial(AuthProvider.APPLE, "")?.let(onAuthenticated)
                }
            },
        )
        SadoraButton(
            "Google bilan davom etish",
            tone = ButtonTone.Secondary,
            leading = "G",
            enabled = !controller.busy,
            onClick = {
                scope.launch {
                    controller.signInWithSocial(AuthProvider.GOOGLE, "")?.let(onAuthenticated)
                }
            },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
            Text("  yoki  ", style = Sadora.type.body, color = c.muted)
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
        }

        TabSwitch(listOf("Telefon", "E-mail"), method, { method = it })

        if (method == 0) {
            SadoraTextField(
                value = state.phone,
                onValueChange = { state.phone = it },
                leading = "+998",
                placeholder = "90 123 45 67",
                keyboardType = KeyboardType.Phone,
            )
        } else {
            SadoraTextField(
                value = state.email,
                onValueChange = { state.email = it },
                placeholder = "siz@example.com",
                keyboardType = KeyboardType.Email,
            )
            SadoraTextField(
                value = password,
                onValueChange = { password = it },
                label = "Parol",
                placeholder = "kamida 8 belgi",
                isPassword = true,
                keyboardType = KeyboardType.Password,
            )
        }

        controller.error?.let { org.example.project.ui.components.ErrorStrip(it) }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SadoraCheckbox(agreed, { agreed = it })
            Text(
                "Foydalanish shartlari va Maxfiylik siyosati ga roziman",
                style = Sadora.type.body,
                color = c.muted,
            )
        }
    }
}

// ---------------------------------------------------------------- 3/9 OTP

@Composable
fun OtpStep(
    state: AppState,
    controller: SadoraController,
    challenge: OtpChallenge?,
    step: String?,
    onBack: () -> Unit,
    onVerified: (AuthDestination) -> Unit,
) {
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    // In development the server hands back the code, so there is nothing to go and read.
    var code by remember(challenge) { mutableStateOf(challenge?.devCode.orEmpty()) }
    var current by remember(challenge) { mutableStateOf(challenge) }
    var secondsLeft by remember(current) {
        mutableStateOf(current?.resendAfterSeconds ?: 42)
    }
    val attemptsLeft = current?.attemptsLeft ?: 3

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    StepScaffold(
        title = "Kodni kiriting",
        subtitle = "+998 ${state.phone} raqamiga 6 xonali kod yubordik.",
        step = step,
        onBack = onBack,
        footer = {
            SadoraButton(
                "Tasdiqlash",
                onClick = {
                    val challengeId = current?.challengeId
                    scope.launch {
                        if (challengeId == null) {
                            // No backend behind the app — accept the code and move on.
                            onVerified(AuthDestination.Onboarding)
                        } else {
                            controller.verifyOtp(challengeId, code)?.let(onVerified)
                        }
                    }
                },
                enabled = code.length == 6 && !controller.busy,
            )
        },
    ) {
        OtpInput(code, isError = controller.error != null)

        // Digit pad kept simple: the design shows a partially entered code.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Qayta yuborish",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (secondsLeft == 0) c.textAccent else c.muted2,
                modifier = Modifier.noRippleClickable(enabled = secondsLeft == 0) {
                    scope.launch {
                        controller.requestOtp(state.phone)?.let {
                            current = it
                            code = it.devCode.orEmpty()
                        }
                    }
                },
            )
            Text(
                "00:" + secondsLeft.toString().padStart(2, '0'),
                style = Sadora.type.body,
                color = c.muted,
            )
        }

        controller.error?.let { message ->
            org.example.project.ui.components.ErrorStrip(
                if (attemptsLeft > 0) "$message Yana $attemptsLeft marta urinish mumkin." else message,
            )
        }

        DisclaimerNote(
            "Kodni hech kimga aytmang. SADORA xodimlari kodni so'ramaydi.",
            icon = "🔒",
        )

        NumberPad(
            onDigit = {
                if (code.length < 6) code += it
                controller.clearError()
            },
            onDelete = {
                code = code.dropLast(1)
                controller.clearError()
            },
        )
    }
}

/** Numeric keypad for the OTP step. */
@Composable
private fun NumberPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val c = Sadora.colors
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(Radius.field)
                            .background(if (key.isEmpty()) androidx.compose.ui.graphics.Color.Transparent else c.surface)
                            .noRippleClickable(enabled = key.isNotEmpty()) {
                                if (key == "⌫") onDelete() else onDigit(key)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = Sadora.type.h2, color = c.text)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 4/9 personal

@Composable
fun PersonalStep(state: AppState, step: String?, onBack: () -> Unit, onNext: () -> Unit) {
    val c = Sadora.colors
    StepScaffold(
        title = "Siz haqingizda",
        subtitle = "Bu ma'lumotlar hisob-kitoblarni aniqroq qiladi.",
        step = step,
        onBack = onBack,
        footer = {
            SadoraButton("Davom etish", onNext)
            SadoraButton("Hozircha o'tkazib yuborish", onNext, tone = ButtonTone.Ghost)
        },
    ) {
        SadoraTextField(state.name, { state.name = it }, label = "Ism", placeholder = "Ismingiz")
        SadoraTextField(
            state.birthDate,
            { state.birthDate = it },
            label = "Tug'ilgan sana",
            trailing = "▾",
            keyboardType = KeyboardType.Number,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SadoraTextField(
                state.heightCm,
                { state.heightCm = it },
                label = "Bo'y",
                suffix = "sm",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            SadoraTextField(
                state.weightKg,
                { state.weightKg = it },
                label = "Vazn",
                suffix = "kg",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Vazn ixtiyoriy va hech qachon boshqalarga ko'rsatilmaydi.",
            style = Sadora.type.body,
            color = c.muted,
        )
    }
}

// ---------------------------------------------------------------- 5/9 goals

@Composable
fun GoalsStep(state: AppState, step: String?, onBack: () -> Unit, onNext: () -> Unit) {
    val c = Sadora.colors
    StepScaffold(
        title = "Maqsadlaringiz",
        subtitle = "Bir nechtasini tanlang.",
        step = step,
        onBack = onBack,
        footer = {
            Text(
                "${state.goals.size} tanlandi",
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.padding(bottom = Spacing.xxs),
            )
            SadoraButton("Davom etish", onNext, enabled = state.goals.isNotEmpty())
        },
    ) {
        ChipFlowRow {
            Goal.entries.forEach { goal ->
                SelectChip(
                    label = goal.label,
                    selected = goal in state.goals,
                    onClick = { state.toggleGoal(goal) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 6/9 life stage

@Composable
fun LifeStageStep(state: AppState, step: String?, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(
        title = "Hayot bosqichi",
        subtitle = "Keyingi ekranlar shu tanlovga qarab shartli ko'rsatiladi.",
        step = step,
        onBack = onBack,
        footer = { SadoraButton("Davom etish", onNext) },
    ) {
        LifeStage.entries.forEach { stage ->
            OptionRow(
                title = stage.title,
                subtitle = stage.subtitle,
                leading = stage.glyph,
                selected = state.lifeStage == stage,
                onClick = { state.lifeStage = stage },
            )
        }
    }
}

// ---------------------------------------------------------------- 7/9 permissions

@Composable
fun PermissionsStep(state: AppState, step: String?, onBack: () -> Unit, onNext: () -> Unit) {
    StepScaffold(
        title = "Ruxsatlar",
        subtitle = "Har birini keyinroq o'zgartirishingiz mumkin.",
        step = step,
        onBack = onBack,
        footer = {
            SadoraButton("Ruxsat berish", onNext)
            SadoraButton("Hozir emas", onNext, tone = ButtonTone.Ghost)
        },
    ) {
        OptionRow(
            title = "Bildirishnomalar",
            subtitle = "Dori vaqti, hayz eslatmasi",
            leading = "🔔",
            selected = state.notificationsAllowed,
            onClick = { state.notificationsAllowed = !state.notificationsAllowed },
        )
        OptionRow(
            title = "Salomatlik ma'lumotlari",
            subtitle = "Qadamlar, uyqu, puls",
            leading = "⌚",
            selected = state.healthDataAllowed,
            onClick = { state.healthDataAllowed = !state.healthDataAllowed },
        )
        OptionRow(
            title = "Kamera",
            subtitle = "Ovqat skaneri uchun",
            leading = "📷",
            selected = state.cameraAllowed,
            onClick = { state.cameraAllowed = !state.cameraAllowed },
        )
        DisclaimerNote(
            "Ruxsat bermasangiz ham ilova to'liq ishlaydi — ma'lumotni qo'lda kiritish mumkin.",
        )
    }
}

// ---------------------------------------------------------------- 8/9 privacy

@Composable
fun PrivacyStep(
    state: AppState,
    controller: SadoraController,
    step: String?,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val c = Sadora.colors
    StepScaffold(
        title = "Maxfiylik va rozilik",
        subtitle = "Nimaga rozilik berishni o'zingiz tanlaysiz.",
        step = step,
        onBack = onBack,
        footer = {
            SadoraButton("Qabul qilaman", onNext, enabled = state.consentStoreHealth)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Maxfiylik siyosati", style = Sadora.type.body, color = c.textAccent)
                Text("Shartlar", style = Sadora.type.body, color = c.textAccent)
            }
        },
    ) {
        ConsentRow(
            title = "Salomatlik ma'lumotlarini saqlash",
            body = "Ilova ishlashi uchun zarur. Ma'lumot shifrlangan holda saqlanadi.",
            checked = state.consentStoreHealth,
            onCheckedChange = { state.consentStoreHealth = it },
            required = true,
        )
        ConsentRow(
            title = "AI xulosalar uchun ishlatish",
            body = "Shaxsiy xulosa va tavsiyalar tayyorlash uchun.",
            checked = state.consentAiInsights,
            onCheckedChange = { state.consentAiInsights = it },
        )
        ConsentRow(
            title = "Anonim analitika",
            body = "Ixtiyoriy. Ilovani yaxshilashga yordam beradi.",
            checked = state.consentAnalytics,
            onCheckedChange = { state.consentAnalytics = it },
        )
    }
}

// ---------------------------------------------------------------- ready

@Composable
fun ReadyStep(state: AppState, controller: SadoraController, onEnter: () -> Unit) {
    val c = Sadora.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.success.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = Sadora.type.display, color = c.success)
        }
        Text("Hammasi tayyor, ${state.name}", style = Sadora.type.h1, color = c.text)
        Text(
            "Bugun ekranini siklingiz, maqsadlaringiz va ulangan qurilmalaringiz asosida sozladik.",
            style = Sadora.type.body,
            color = c.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SummaryLine("${state.lifeStage.title} yoqildi")
            if (state.healthDataAllowed) SummaryLine("Apple Health ulandi")
            SummaryLine("${state.goals.size} maqsad belgilandi")
        }
        Spacer(Modifier.weight(1f))
        controller.error?.let { org.example.project.ui.components.ErrorStrip(it) }
        SadoraButton(
            if (controller.busy) "Saqlanmoqda…" else "SADORA'ga kirish",
            onEnter,
            enabled = !controller.busy,
        )
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun SummaryLine(text: String) {
    val c = Sadora.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("✓", style = Sadora.type.h3, color = c.success)
        Text(text, style = Sadora.type.h3, color = c.text)
    }
}

// ---------------------------------------------------------------- sign in

@Composable
fun SignInScreen(
    state: AppState,
    controller: SadoraController,
    onSignedIn: (AppPhase) -> Unit,
    onRegisterInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }

    fun finish(destination: AuthDestination) {
        onSignedIn(
            if (destination == AuthDestination.Main) AppPhase.Main else AppPhase.Onboarding,
        )
    }

    fun submit() {
        scope.launch {
            // The field is labelled "telefon raqami", but the backend signs in by
            // e-mail and password. Whichever the user typed, send it as the identifier.
            val identifier = state.email.ifBlank { state.phone }
            controller.signInWithEmail(identifier, password)?.let(::finish)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.secondary, c.primary))),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", style = Sadora.type.h1, color = if (c.isDark) c.bg else androidx.compose.ui.graphics.Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Xush kelibsiz", style = Sadora.type.h1, color = c.text)
            Text("Hisobingizga kirib davom eting", style = Sadora.type.body, color = c.muted)
        }
        SadoraTextField(
            state.email,
            { state.email = it },
            label = "E-mail",
            placeholder = "siz@example.com",
            keyboardType = KeyboardType.Email,
        )
        SadoraTextField(
            password,
            { password = it },
            label = "Parol",
            placeholder = "••••••••",
            isPassword = true,
            trailing = "◡",
            keyboardType = KeyboardType.Password,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SadoraCheckbox(rememberMe, { rememberMe = it })
                Text("Eslab qolish", style = Sadora.type.body, color = c.text)
            }
            Text("Parolni tikladingizmi?", style = Sadora.type.body, color = c.textAccent)
        }
        controller.error?.let { org.example.project.ui.components.ErrorStrip(it) }
        SadoraButton(
            if (controller.busy) "Kirilmoqda…" else "Kirish",
            onClick = ::submit,
            enabled = password.isNotBlank() && !controller.busy,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
            Text("  yoki  ", style = Sadora.type.body, color = c.muted)
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(
                "Google",
                tone = ButtonTone.Secondary,
                leading = "G",
                enabled = !controller.busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        controller.signInWithSocial(AuthProvider.GOOGLE, "")?.let(::finish)
                    }
                },
            )
            SadoraButton(
                "Face ID",
                tone = ButtonTone.Secondary,
                leading = "☉",
                enabled = !controller.busy,
                modifier = Modifier.weight(1f),
                onClick = ::submit,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hisobingiz yo'qmi? ", style = Sadora.type.body, color = c.muted)
            Text(
                "Ro'yxatdan o'tish",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.noRippleClickable(onClick = onRegisterInstead),
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
