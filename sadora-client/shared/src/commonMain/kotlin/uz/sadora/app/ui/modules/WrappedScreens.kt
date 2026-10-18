package uz.sadora.app.ui.modules

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.data.WrappedController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.nav.Route
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.components.badgeArt
import uz.sadora.app.ui.components.encodePng
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.rememberImageShareAction
import uz.sadora.app.ui.components.systemReducesMotion
import uz.sadora.contract.Wrapped
import uz.sadora.contract.WrappedKind
import uz.sadora.contract.WrappedPersona
import uz.sadora.contract.WrappedSummary

/** The flower each persona is drawn as. */
internal fun WrappedPersona.art(): DrawableResource = when (this) {
    WrappedPersona.ATIRGUL -> Res.drawable.flower_rose
    WrappedPersona.LOLA -> Res.drawable.flower_tulip
    WrappedPersona.NILUFAR -> Res.drawable.flower_lotus
    WrappedPersona.BINAFSHA -> Res.drawable.flower_violet
    WrappedPersona.MOYCHECHAK -> Res.drawable.flower_chamomile
    WrappedPersona.YASMIN -> Res.drawable.flower_jasmine
}

// ---------------------------------------------------------------- Today and the list

/** "Sikl xulosangiz tayyor 🎉" on Today, while a new recap waits to be opened. */
@Composable
fun WrappedFreshCard(summary: WrappedSummary, onOpen: () -> Unit) {
    val t = strings.wrapped
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, onClick = onOpen, verticalGap = Spacing.xs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.flower_lotus, tint = c.primary, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(t.freshTitle(summary.kind), style = Sadora.type.h3, color = c.text)
                Text(t.freshBody, style = Sadora.type.body, color = c.muted)
            }
        }
        SadoraButton(t.open, onClick = onOpen)
    }
}

@Composable
fun WrappedListScreen(
    wrapped: WrappedController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.wrapped
    val c = Sadora.colors
    LaunchedEffect(Unit) { wrapped.refresh() }
    val index = wrapped.index
    Column(modifier) {
        SadoraTopBar(t.listTitle, onBack = onClose)
        ScreenContent {
            item { Text(t.listIntro, style = Sadora.type.body, color = c.muted) }
            when {
                index == null -> item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) } }
                index.items.isEmpty() -> item { Text(t.empty, style = Sadora.type.body, color = c.muted) }
                else -> index.items.forEach { summary ->
                    item(key = summary.key) {
                        SadoraCard(padding = Spacing.sm, onClick = { onOpen(Route.WrappedStory(summary.key)) }, verticalGap = 0.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                ArtTile(if (summary.kind == WrappedKind.YEAR) Res.drawable.flower_peony else Res.drawable.flower_lotus, tint = c.primary, size = 44.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(t.itemTitle(summary.kind, summary.from.year), style = Sadora.type.h3, color = c.text)
                                    Text(strings.dates.dayMonthYear(summary.from) + " – " + strings.dates.dayMonthYear(summary.to), style = Sadora.type.body, color = c.muted)
                                }
                                if (summary.fresh) SadoraBadge("●", BadgeTone.Warning)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- the slides

/** The slides of a recap, in order; a slide with nothing to say is left out. */
internal enum class WrappedSlide { CycleIntro, Forecast, Feelings, Habits, YearIntro, Streak, Cycles, YearForecast, Symptoms, Mood, Body, Mind, Gul, Persona, Outro }

internal fun Wrapped.slides(): List<WrappedSlide> = buildList {
    if (kind == WrappedKind.CYCLE) {
        add(WrappedSlide.CycleIntro)
        if (forecastErrorDays != null) add(WrappedSlide.Forecast)
        add(WrappedSlide.Feelings)
        add(WrappedSlide.Habits)
        add(WrappedSlide.Persona)
    } else {
        add(WrappedSlide.YearIntro)
        if (habits.longestRun > 0) add(WrappedSlide.Streak)
        if ((cycles?.count ?: 0) > 0) add(WrappedSlide.Cycles)
        if (cycles?.forecastErrorDays != null) add(WrappedSlide.YearForecast)
        if (topSymptoms.isNotEmpty()) add(WrappedSlide.Symptoms)
        if (moodByPhase.isNotEmpty() || bestMonth != null) add(WrappedSlide.Mood)
        add(WrappedSlide.Body)
        add(WrappedSlide.Mind)
        if (habits.gulEarned > 0 || habits.badges.isNotEmpty()) add(WrappedSlide.Gul)
        add(WrappedSlide.Persona)
        add(WrappedSlide.Outro)
    }
}

private val Palettes = listOf(
    Color(0xFF7B5CFF) to Color(0xFFE0529C),
    Color(0xFFFF7EA8) to Color(0xFFFFB38A),
    Color(0xFF5B7CF0) to Color(0xFFA78BFA),
    Color(0xFF2FB59B) to Color(0xFF6CC7F0),
    Color(0xFFF59E5B) to Color(0xFFF2617D),
    Color(0xFF9B5CF6) to Color(0xFF5B8DEF),
)

private const val SLIDE_MILLIS = 7000

/**
 * The recap as story slides: a tap on the right goes on, on the left goes back, a press holds
 * the slide. Each slide shares as the 9:16 picture on screen; "Raqamlarni yashirish" swaps her
 * health numbers for dots before she does.
 */
@Composable
fun WrappedStoryScreen(
    key: String,
    wrapped: WrappedController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.wrapped
    val closeLabel = strings.common.close
    val scope = rememberCoroutineScope()
    val share = rememberImageShareAction()
    val layer = rememberGraphicsLayer()
    val reduceMotion = systemReducesMotion()
    LaunchedEffect(key) { wrapped.open(key) }
    SystemBackHandler(onBack = onClose)
    val recap = wrapped.current?.takeIf { it.key == key }

    Box(modifier.fillMaxSize().background(Color(0xFF15111F)).statusBarsPadding().navigationBarsPadding()) {
        if (recap == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                wrapped.error?.let { ErrorStrip(it.readable(), onRetry = { scope.launch { wrapped.open(key) } }, modifier = Modifier.padding(Spacing.screen)) }
                    ?: SadoraLoader(size = 48.dp)
            }
            return@Box
        }
        val slides = remember(recap) { recap.slides() }
        var index by remember(recap) { mutableIntStateOf(0) }
        var paused by remember { mutableStateOf(false) }
        // Held after a share: the sheet covers the story, and the slide she shared should still be there after.
        var held by remember { mutableStateOf(false) }
        var hidden by remember { mutableStateOf(false) }
        val progress = remember(recap) { Animatable(0f) }
        val next = { if (index < slides.size - 1) index += 1 }
        val previous = { if (index > 0) index -= 1 }

        LaunchedEffect(index, paused || held) {
            if (paused || held) {
                progress.stop()
                return@LaunchedEffect
            }
            if (progress.value >= 1f) progress.snapTo(0f)
            val left = ((1f - progress.value) * SLIDE_MILLIS).toInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(left, easing = LinearEasing))
            if (index < slides.size - 1) {
                progress.snapTo(0f)
                index += 1
            }
        }

        Column(Modifier.fillMaxSize()) {
            // The bars: done, this one filling, the rest to come.
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                slides.indices.forEach { i ->
                    val fill = when {
                        i < index -> 1f
                        i == index -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                    }
                }
                Text(
                    "✕",
                    color = Color.White,
                    style = Sadora.type.h3,
                    modifier = Modifier.padding(start = Spacing.xs).noRippleClickable(role = Role.Button, onClick = onClose).semantics { contentDescription = closeLabel },
                )
            }

            val palette = Palettes[index % Palettes.size]
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(24.dp))
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .background(Brush.linearGradient(listOf(palette.first, palette.second)))
                        .pointerInput(slides.size) {
                            detectTapGestures(
                                onPress = {
                                    paused = true
                                    tryAwaitRelease()
                                    paused = false
                                },
                                onTap = { offset ->
                                    held = false
                                    scope.launch { progress.snapTo(0f) }
                                    if (offset.x < size.width / 3f) previous() else next()
                                },
                            )
                        },
                ) {
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = {
                            if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            else (fadeIn(tween(350)) + slideInHorizontally(tween(350)) { it / 6 }) togetherWith fadeOut(tween(200))
                        },
                        label = "slide",
                    ) { i ->
                        Column(
                            Modifier.fillMaxSize().padding(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
                        ) {
                            SlideBody(slides[i], recap, hidden, animate = !reduceMotion)
                        }
                    }
                    Text(
                        t.brand,
                        color = Color.White.copy(alpha = 0.85f),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { SelectChip(t.hideNumbers, selected = hidden, onClick = { hidden = !hidden }) }
                SadoraButton(
                    t.share,
                    onClick = {
                        held = true
                        scope.launch {
                            val png = layer.toImageBitmap().encodePng()
                            if (png.isNotEmpty()) share(png, "sadora-${recap.key}.png")
                        }
                    },
                    fillWidth = false,
                )
            }
        }
    }
}

private val White = Color.White
private val Soft = Color.White.copy(alpha = 0.82f)

@Composable
private fun Title(text: String) = Text(text, color = White, style = Sadora.type.h2.copy(fontWeight = FontWeight.Bold))

@Composable
private fun Note(text: String) = Text(text, color = Soft, style = Sadora.type.body)

/** A big number that counts up as the slide arrives; dots when her numbers are hidden. */
@Composable
private fun BigNumber(value: Int, unit: String, hidden: Boolean, animate: Boolean) {
    var started by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateIntAsState(if (started) value else 0, tween(if (animate) 900 else 0), label = "count")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(if (hidden) "••" else shown.toString(), color = White, style = Sadora.type.h1.copy(fontSize = 64.sp, fontWeight = FontWeight.Bold))
        Text(unit, color = Soft, style = Sadora.type.h3, modifier = Modifier.padding(bottom = 12.dp))
    }
}

/** A row of a label and a number, for the habit slides. */
@Composable
private fun Stat(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(value, color = White, style = Sadora.type.h2.copy(fontWeight = FontWeight.Bold), modifier = Modifier.widthIn(min = 96.dp))
        Text(label, color = Soft, style = Sadora.type.body)
    }
}

@Composable
private fun ColumnScope.SlideBody(slide: WrappedSlide, w: Wrapped, hidden: Boolean, animate: Boolean) {
    val t = strings.wrapped
    val h = w.habits
    fun n(value: Int, private: Boolean = false) = if (hidden && private) "••" else value.toString()
    when (slide) {
        WrappedSlide.CycleIntro -> {
            Title(t.cycleIntro)
            Note(strings.dates.dayMonth(w.from) + " – " + strings.dates.dayMonth(w.to))
            Spacer(Modifier.height(Spacing.sm))
            BigNumber(w.cycleLength ?: 0, t.days(w.cycleLength ?: 0), hidden, animate)
            Note(t.cycleLasted)
            w.periodLength?.let { Note(if (hidden) t.periodLasted(0).replace("0", "••") else t.periodLasted(it)) }
            if (!hidden) w.usualLength?.let { usual -> Note(t.vsUsual((w.cycleLength ?: usual) - usual)) }
        }
        WrappedSlide.Forecast -> {
            Title(t.forecastTitle)
            Text(if (hidden) "🎯" else t.forecast(w.forecastErrorDays ?: 0), color = White, style = Sadora.type.h2)
            Note(t.forecastNote)
        }
        WrappedSlide.Feelings -> {
            Symptoms(w, hidden)
            Spacer(Modifier.height(Spacing.sm))
            Moods(w, hidden)
        }
        WrappedSlide.Habits -> {
            Title(t.habitsTitle)
            Stat(n(h.daysWithApp), t.daysWithApp)
            Stat(n(h.loggedDays), t.loggedDays)
            Stat(n(h.waterGoalDays), t.waterGoalDays)
            if (h.kegelSessions > 0) Stat(n(h.kegelSessions, private = true), t.kegel)
            if (h.practiceMinutes > 0) Stat(n(h.practiceMinutes), t.practiceMinutes)
            Stat(n(h.gulEarned), t.gul)
        }
        WrappedSlide.YearIntro -> {
            Title(t.yearIntro(w.from.year))
            Spacer(Modifier.height(Spacing.sm))
            BigNumber(h.daysWithApp, t.daysWithApp, false, animate)
        }
        WrappedSlide.Streak -> {
            Title(t.streakTitle)
            BigNumber(h.longestRun, t.days(h.longestRun), false, animate)
            Note(t.longestRun(h.longestRun))
            Stat(n(h.loggedDays), t.loggedDays)
        }
        WrappedSlide.Cycles -> {
            val cy = w.cycles ?: return
            Title(if (hidden) t.cyclesTitle(0).replace("0", "••") else t.cyclesTitle(cy.count))
            cy.averageLength?.let { Stat(n(it, private = true), t.averageCycle) }
            cy.averagePeriod?.let { Stat(n(it, private = true), t.averagePeriod) }
            if (cy.shortest != null && cy.longest != null) {
                Stat(if (hidden) "••" else "${cy.shortest}–${cy.longest}", t.shortestLongest)
            }
            if (!hidden && cy.lengths.isNotEmpty()) {
                val longest = cy.lengths.max().coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    cy.lengths.forEach { len ->
                        Box(Modifier.weight(1f).fillMaxHeight(len.toFloat() / longest).clip(RoundedCornerShape(4.dp)).background(White.copy(alpha = 0.85f)))
                    }
                }
            }
        }
        WrappedSlide.YearForecast -> {
            Title(t.forecastTitle)
            Text(if (hidden) "🎯" else t.yearForecast(w.cycles?.forecastErrorDays.toString()), color = White, style = Sadora.type.h2)
            Note(t.forecastNote)
        }
        WrappedSlide.Symptoms -> Symptoms(w, hidden)
        WrappedSlide.Mood -> Moods(w, hidden)
        WrappedSlide.Body -> {
            Title(t.bodyTitle)
            Stat(n(h.waterLitres), t.waterLitres)
            Stat(n(h.waterGoalDays), t.waterGoalDays)
            h.sleepAverageMinutes?.let { Stat(if (hidden) "••" else t.hours(it), t.sleep) }
            Stat(n(h.kegelSessions, private = true), t.kegel)
        }
        WrappedSlide.Mind -> {
            Title(t.mindTitle)
            Stat(n(h.practiceMinutes), t.practiceMinutes)
            Stat(n(h.journalEntries), t.journal)
            Stat(n(h.mealsLogged), t.meals)
        }
        WrappedSlide.Gul -> {
            Title(t.gulTitle)
            BigNumber(h.gulEarned, t.gul, false, animate)
            if (h.badges.isNotEmpty()) {
                Note("${h.badges.size} ${t.badges}")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    h.badges.take(5).forEach { key -> badgeArt(key)?.let { Image(painterResource(it), null, Modifier.size(48.dp)) } }
                }
            }
        }
        WrappedSlide.Persona -> {
            Note(t.personaTitle)
            FlowerPlate(w.persona.art(), 190)
            Text(t.personaName(w.persona), color = White, style = Sadora.type.h1.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(t.personaBody(w.persona), color = White, style = Sadora.type.h3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(t.personaNote, color = Soft, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        WrappedSlide.Outro -> {
            FlowerPlate(Res.drawable.flower_lotus, 150)
            Text(t.outroTitle, color = White, style = Sadora.type.h2.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(t.outroBody, color = Soft, style = Sadora.type.body, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The flower on a white plate: the clay's soft reflections belong on white, not on the gradient. */
@Composable
private fun ColumnScope.FlowerPlate(art: DrawableResource, size: Int) {
    Box(
        Modifier.size(size.dp).align(Alignment.CenterHorizontally).clip(androidx.compose.foundation.shape.CircleShape).background(White.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(art), null, Modifier.size((size * 0.78f).dp))
    }
}

@Composable
private fun Symptoms(w: Wrapped, hidden: Boolean) {
    val t = strings.wrapped
    Title(t.symptomsTitle)
    if (w.topSymptoms.isEmpty()) {
        Note(t.noSymptoms)
        return
    }
    w.topSymptoms.forEachIndexed { i, s ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("${i + 1}", color = White, style = Sadora.type.h2.copy(fontWeight = FontWeight.Bold))
            Column {
                Text(if (hidden) "••••" else s.label, color = White, style = Sadora.type.h3)
                Text(if (hidden) "••" else "${s.days} ${t.days(s.days)}", color = Soft, style = Sadora.type.body)
            }
        }
    }
}

@Composable
private fun Moods(w: Wrapped, hidden: Boolean) {
    val t = strings.wrapped
    Title(t.moodTitle)
    if (w.moodByPhase.isEmpty()) {
        Note(t.noMood)
    } else {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Bottom) {
            w.moodByPhase.forEach { m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (hidden) 50.dp else (16 + 18 * m.average).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(White.copy(alpha = 0.85f)),
                    )
                    Text(t.phase(m.phase), color = Soft, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), maxLines = 1)
                }
            }
        }
    }
    if (!hidden) {
        w.bestMoodDay?.let { Note(t.bestDay(strings.dates.dayMonth(it))) }
        w.bestMonth?.let { Note(t.bestMonth(strings.dates.monthYear(w.from.year, it).substringBefore(" "))) }
    }
}
