package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.example.project.design.IconSize
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.CyclePhase
import org.example.project.model.Fmt
import org.example.project.model.SampleData
import org.example.project.model.nowTimeLabel
import org.example.project.ui.components.AiMarkHeader
import org.example.project.ui.components.CircleIconButton
import org.example.project.ui.components.noRippleClickable

private data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val time: String,
)

/** How many questions a Premium account may ask per day. Mirrors the paywall table. */
private const val DailyQuestions = 20

/**
 * "SADORA AI" — the conversation view, drawn on the deck's navy ground.
 *
 * The caller wraps it in `SadoraDarkSurface`, so everything here reads the dark
 * palette through the ordinary tokens. Two safety rails stay on screen: the note that
 * SADORA is not a diagnostic tool, and the daily question allowance.
 */
@Composable
fun AiChatScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var draft by remember { mutableStateOf("") }
    var used by remember { mutableStateOf(0) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || used >= DailyQuestions) return
        messages += ChatMessage(true, text, nowTimeLabel())
        messages += ChatMessage(false, answerFor(text, state), nowTimeLabel())
        used++
        draft = ""
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = "Ortga", onClick = onClose)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SADORA AI",
                    style = Sadora.type.h3.copy(letterSpacing = 0.22.em, fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
                Text("Shaxsiy yordamchingiz", style = Sadora.type.body, color = c.muted)
            }
            CircleIconButton(SadoraIcons.More, contentDescription = "Yana", onClick = {})
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { AiMarkHeader(Modifier.fillMaxWidth().height(150.dp)) }

            item {
                Text(
                    "Sikl ${state.cycleDay}-kun · Uyqu ${state.sleepLabel()} · " +
                        "Suv ${Fmt.litres(state.waterMl)} l asosida · $used/$DailyQuestions savol",
                    style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
                    color = c.muted2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (messages.isEmpty()) {
                item {
                    Text(
                        "Sikl, ovqatlanish, kayfiyat yoki dorilaringiz haqida so'rang — " +
                            "javob sizning ma'lumotlaringiz asosida bo'ladi.",
                        style = Sadora.type.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    )
                }
            }

            items(messages.size) { index -> ChatBubble(messages[index]) }

            item {
                Text(
                    SampleData.medicalDisclaimer,
                    style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
                    color = c.muted2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
        }

        // Topic chips — one tap asks a ready question in that area.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SampleData.aiTopics.forEach { (label, question) ->
                Box(
                    Modifier
                        .clip(Radius.chip)
                        .background(c.surface2)
                        .noRippleClickable { ask(question) }
                        .padding(horizontal = 14.dp, vertical = Spacing.xs),
                ) {
                    Text(label, style = Sadora.type.body.copy(fontWeight = FontWeight.Medium), color = c.text)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(Radius.chip)
                    .background(c.surface2)
                    .padding(horizontal = Spacing.md, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) {
                    Text("Istalgan savolni bering…", style = Sadora.type.body, color = c.muted2)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = Sadora.type.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canSend = draft.isNotBlank() && used < DailyQuestions
            Box(
                Modifier
                    .size(48.dp)
                    .clip(Radius.chip)
                    .background(if (canSend) c.heroGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.surface2, c.surface2)))
                    .noRippleClickable(enabled = canSend) { ask(draft) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SadoraIcons.Send,
                    contentDescription = "Yuborish",
                    Modifier.size(IconSize.md),
                    tint = if (canSend) c.onPrimary else c.muted2,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val c = Sadora.colors
    val shape = if (message.fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.82f)
                .clip(shape)
                .background(if (message.fromUser) c.primary else c.surface2)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                message.text,
                style = Sadora.type.body,
                color = if (message.fromUser) c.onPrimary else c.text,
            )
            Text(
                message.time,
                style = Sadora.type.caption.copy(letterSpacing = 0.02.em),
                color = if (message.fromUser) c.onPrimary.copy(alpha = 0.7f) else c.muted2,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * The answer to a question, until a model sits behind the screen.
 *
 * Rule-based on purpose: it reads the same numbers the tiles show, names which of them
 * it used, and never diagnoses. Each topic keys on the words a question in that area
 * actually contains.
 */
internal fun answerFor(question: String, state: AppState): String {
    val q = question.lowercase()
    val phase = state.currentPhase()
    val context = "Sikl ${state.cycleDay}-kun (${phase.label.lowercase()}), " +
        "uyqu ${state.sleepLabel()}, suv ${Fmt.litres(state.waterMl)} l asosida."
    val body = when {
        "charch" in q || "energiya" in q || "toliq" in q -> when (phase) {
            CyclePhase.Luteal, CyclePhase.Period ->
                "Hayzdan oldin va hayz davrida progesteron o'zgarishi uyqu va energiyaga ta'sir qilishi mumkin. " +
                    "Magniyga boy ovqatlar, yengil yurish va nafas mashqlarini sinab ko'ring."
            else ->
                "Bu fazada energiya odatda o'sadi. Suv iste'moli va uyqu davomiyligi pastroq bo'lsa, " +
                    "charchoq shundan bo'lishi mumkin — bugun ${state.waterRemainingMl} ml suv qoldi."
        }
        "ye" in q || "ovqat" in q || "taom" in q ->
            "Barqaror energiya uchun oqsil va murakkab uglevodlarni birga oling: tuxum, " +
                "yog'urt, don mahsulotlari, sabzavot. Bugun ${Fmt.int(state.caloriesEaten)} / " +
                "${Fmt.int(state.calorieGoal)} kkal qayd etilgan. Ovqatlanish rejasini tuzib beraymi?"
        "teri" in q || "akne" in q ->
            "Sikl davomida gormonlar terining yog' ishlab chiqarishini o'zgartiradi: hayz oldidan " +
                "toshmalar ko'payishi odatiy. Yumshoq tozalash, yetarli suv va uyqu yordam beradi. " +
                "Uzoq davom etsa, dermatologga ko'rsating."
        "uyqu" in q || "uxla" in q ->
            "Kecha ${state.sleepLabel()} uxlagansiz. Kechqurun ekranni kamaytirish va bir xil " +
                "vaqtda yotish uyqu sifatini yaxshilaydi. Uyqu ma'lumotlarini kuzatishda davom eting."
        "sikl" in q || "hayz" in q || "ovulyats" in q ->
            "Hozir siklning ${state.cycleDay}-kuni — ${phase.label.lowercase()}. " +
                "${phase.energyNote} Keyingi hayz taxminan ${state.daysToNextPeriod()} kundan keyin."
        else ->
            "Savolingizni tushundim. Sikl, ovqatlanish, kayfiyat va dorilaringiz bo'yicha " +
                "ma'lumotlaringizga tayanib javob bera olaman — aniqroq so'rasangiz, batafsil tushuntiraman."
    }
    return "$body\n\n$context Bu umumiy ma'lumot — tashxis emas."
}
