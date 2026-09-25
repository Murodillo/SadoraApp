package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorDocumentUpload
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.Limits
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.PanelState
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.CapturedPhoto
import uz.sadora.doctor.ui.components.ChipFlowRow
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.IconTile
import uz.sadora.doctor.ui.components.Motion
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SectionHeader
import uz.sadora.doctor.ui.components.SelectChip
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.SuccessCheck
import uz.sadora.doctor.ui.components.acceptText
import uz.sadora.doctor.ui.components.rememberPhotoCapture

// Ported from the client app, where these screens were first written. The panel is the
// root here rather than a page behind the profile, so it has no back arrow and carries
// the settings button instead.

// ---------------------------------------------------------------- a doctor's page

/**
 * Her page as readers see it: name, specialty, where she works, how long, her own words,
 * and what she has written — so she can check how she appears before anyone else does.
 */
@Composable
fun DoctorProfileScreen(
    doctorId: String,
    doctors: DoctorController,
    onOpenPost: (String) -> Unit,
    onNewPost: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    LaunchedEffect(doctorId) { doctors.loadProfile(doctorId) }
    val profile = doctors.profile?.takeIf { it.id == doctorId }
    val error = doctors.profileCalls.error

    Column(modifier) {
        SadoraTopBar(
            d.profileTitle,
            onBack = onClose,
            trailing = if (profile?.isMe == true) {
                { CircleIconButton(SadoraIcons.Plus, contentDescription = strings.community.newPost, onClick = onNewPost) }
            } else null,
        )
        ScreenContent {
            error?.let { failure ->
                item { ErrorStrip(failure.readable(), onRetry = { scope.launch { doctors.loadProfile(doctorId) } }) }
            }
            if (profile == null) {
                if (error == null) item { DoctorSkeleton() }
                return@ScreenContent
            }
            item { DoctorHeader(profile) }
            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(Modifier.fillMaxWidth()) {
                        DoctorStat(profile.postCount.toString(), d.statPosts, Modifier.weight(1f))
                        DoctorStat(profile.answerCount.toString(), d.statAnswers, Modifier.weight(1f))
                        DoctorStat(profile.experienceYears.toString(), d.statYears, Modifier.weight(1f))
                    }
                }
            }
            item { Text(d.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
            item { SectionHeader(d.herPosts) }
            val posts = doctors.profilePosts
            if (posts.isEmpty()) {
                item {
                    EmptyState(
                        title = d.noPosts,
                        body = d.noPostsBody,
                        actionText = strings.community.newPost.takeIf { profile.isMe },
                        onAction = onNewPost,
                    )
                }
            } else {
                items(posts.size, key = { posts[it].id }) { index ->
                    val post = posts[index]
                    PostCard(post = post, onOpen = { onOpenPost(post.id) })
                }
            }
        }
    }
}

@Composable
private fun DoctorHeader(profile: DoctorProfile) {
    val d = strings.doctors
    val c = Sadora.colors
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DoctorAvatar(profile.fullName, size = 84.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(profile.fullName, style = Sadora.type.h2, color = c.text, textAlign = TextAlign.Center)
            VerifiedMark(size = 20.dp)
        }
        Text(d.specialty(profile.specialty), style = Sadora.type.h3, color = c.textAccent)
        Text(profile.workplace, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
        Text(d.verifiedSince(monthYear(profile.verifiedSince)), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
        profile.bio?.let {
            Text(
                it,
                style = Sadora.type.body,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
    }
}

@Composable
private fun DoctorStat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Sadora.type.h2, color = c.text)
        Text(label, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DoctorSkeleton() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Skeleton(Modifier.size(84.dp), shape = Radius.chip)
        Skeleton(Modifier.size(width = 180.dp, height = 22.dp))
        Skeleton(Modifier.size(width = 120.dp, height = 16.dp))
        Skeleton(Modifier.fillMaxWidth().height(72.dp))
    }
}

// ---------------------------------------------------------------- the panel

/**
 * The doctor's own screen, whatever stage she is at: the offer before she applies,
 * the wait, the admin's note after a rejection or a suspension, and — once approved —
 * her page, her editable details and the questions still waiting for a doctor.
 */
@Composable
fun DoctorPanelScreen(
    doctors: DoctorController,
    onApply: () -> Unit,
    onOpenPage: (String) -> Unit,
    onOpenQuestion: (String) -> Unit,
    onNewPost: () -> Unit,
    onOpenSettings: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    LaunchedEffect(doctors) { doctors.loadAccount(silent = false) }
    val account = doctors.account
    LaunchedEffect(account?.status) {
        if (account?.status == DoctorStatus.APPROVED) doctors.loadQuestions()
    }

    // The status card starts from what the panel showed last time, so a change made
    // elsewhere — the form sent, a review come back — plays here as a change.
    val panel = doctors.panelState
    var shown by remember { mutableStateOf(doctors.shownPanel ?: panel) }
    LaunchedEffect(panel) {
        shown = panel
        doctors.shownPanel = panel
    }

    // A question she has just answered is drawn once more, marked, and then let go: the
    // list animates it out and closes the gap.
    val rows = doctors.questionRows
    val anyAnswered = rows.any { it.answered }
    LaunchedEffect(anyAnswered) {
        if (anyAnswered) {
            delay(AnsweredLingerMillis)
            doctors.settleAnswered()
        }
    }

    Column(modifier) {
        SadoraTopBar(
            d.panelTitle,
            trailing = { CircleIconButton(SadoraIcons.Settings, contentDescription = strings.settings.title, onClick = onOpenSettings) },
        )
        ScreenContent(animateItems = true) {
            doctors.error?.let { failure ->
                item(key = "error") {
                    ErrorStrip(
                        failure.readable(),
                        onRetry = {
                            scope.launch {
                                doctors.loadAccount(silent = false)
                                if (doctors.account?.status == DoctorStatus.APPROVED) doctors.loadQuestions()
                            }
                        },
                    )
                }
            }
            item(key = "status") {
                AnimatedContent(
                    targetState = shown,
                    contentKey = { it::class },
                    transitionSpec = {
                        (fadeIn(tween(Motion.Standard)) + scaleIn(tween(Motion.Standard, easing = Motion.Emphasized), initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(Motion.Quick))) using SizeTransform(clip = false)
                    },
                    label = "panel-status",
                ) { state ->
                    when (state) {
                        PanelState.Loading -> if (doctors.error == null) DoctorSkeleton()
                        PanelState.Intro -> IntroCard(onApply)
                        is PanelState.Pending -> StatusCard(
                            title = d.pendingTitle,
                            body = d.pendingBody,
                            tint = c.secondary,
                            footer = state.submittedAt?.let { d.submittedOn(dayMonth(it)) },
                            // The application went in: the same tick that marks a sent answer.
                            leading = { SuccessCheck(size = 44.dp) },
                        )
                        is PanelState.Rejected -> StatusCard(title = d.rejectedTitle, body = null, note = state.note, tint = c.danger) {
                            SadoraButton(d.reapply, onClick = onApply)
                        }
                        is PanelState.Suspended ->
                            StatusCard(title = d.suspendedTitle, body = d.suspendedBody, note = state.note, tint = c.danger)
                        is PanelState.Approved -> ApprovedCard(
                            state.account,
                            onOpenPage = { state.account.profileId?.let(onOpenPage) },
                            onNewPost = onNewPost,
                        )
                    }
                }
            }
            if (panel is PanelState.Approved) {
                item(key = "edit") { EditDoctorCard(panel.account, doctors, onSaved = { onToast(d.saved) }) }
                item(key = "questions-title") { SectionHeader(d.questionsTitle) }
                item(key = "questions-hint") { Text(d.questionsHint, style = Sadora.type.body, color = c.muted) }
                if (!doctors.questionsLoaded && doctors.error == null) {
                    item(key = "questions-loading") { Skeleton(Modifier.fillMaxWidth().height(120.dp), shape = Radius.card) }
                }
                if (doctors.questionsLoaded && rows.isEmpty()) {
                    item(key = "questions-empty") {
                        EmptyState(title = d.questionsEmpty, body = d.questionsEmptyBody, actionText = null, onAction = {})
                    }
                }
                items(rows.size, key = { rows[it].post.id }) { index ->
                    val row = rows[index]
                    PostCard(post = row.post, onOpen = { onOpenQuestion(row.post.id) }, answered = row.answered)
                }
            }
        }
    }
}

/** How long a just-answered question stays on the list, marked, before it leaves. */
private const val AnsweredLingerMillis = 1_400L

@Composable
private fun IntroCard(onApply: () -> Unit) {
    val d = strings.doctors
    val c = Sadora.colors
    SadoraCard {
        IconTile(SadoraIcons.Shield, tint = c.primary, size = 52.dp)
        Text(d.introTitle, style = Sadora.type.h2, color = c.text)
        Text(d.introBody, style = Sadora.type.body, color = c.muted)
        d.introPoints.forEach { point ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
                VerifiedMark(size = 18.dp)
                Text(point, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
            }
        }
        SadoraButton(d.applyButton, onClick = onApply)
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String?,
    tint: Color,
    note: String? = null,
    footer: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val d = strings.doctors
    val c = Sadora.colors
    SadoraCard {
        leading?.invoke()
        Text(title, style = Sadora.type.h2, color = tint)
        body?.let { Text(it, style = Sadora.type.body, color = c.muted) }
        note?.let {
            Column(
                Modifier.fillMaxWidth().clip(Radius.cardSmall).background(c.surface2).padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(d.adminNote, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                Text(it, style = Sadora.type.body, color = c.text)
            }
        }
        footer?.let { Text(it, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
        action?.invoke()
    }
}

@Composable
private fun ApprovedCard(account: DoctorAccount, onOpenPage: () -> Unit, onNewPost: () -> Unit) {
    val d = strings.doctors
    val c = Sadora.colors
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DoctorAvatar(account.fullName.orEmpty(), size = 52.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(account.fullName.orEmpty(), style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f, fill = false))
                    VerifiedMark()
                }
                account.specialty?.let { Text(d.specialty(it), style = Sadora.type.body, color = c.textAccent) }
            }
        }
        Text(d.approvedTitle, style = Sadora.type.h3, color = c.text)
        Text(d.approvedBody, style = Sadora.type.body, color = c.muted)
        SadoraButton(strings.community.newPost, onClick = onNewPost, icon = SadoraIcons.Plus)
        SadoraButton(d.myPage, onClick = onOpenPage, tone = ButtonTone.Secondary, enabled = account.profileId != null)
    }
}

/** Workplace and bio: what she may change without another review. */
@Composable
private fun EditDoctorCard(account: DoctorAccount, doctors: DoctorController, onSaved: () -> Unit) {
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var workplace by remember(account.workplace) { mutableStateOf(account.workplace.orEmpty()) }
    var bio by remember(account.bio) { mutableStateOf(account.bio.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val changed = workplace.trim() != account.workplace.orEmpty() || bio.trim() != account.bio.orEmpty()
    SadoraCard {
        Text(d.editTitle, style = Sadora.type.h3, color = c.text)
        SadoraTextField(
            value = workplace,
            onValueChange = { workplace = acceptText(it, Limits.DOCTOR_WORKPLACE_MAX) },
            label = d.workplace,
            placeholder = d.workplaceHint,
        )
        SadoraTextField(
            value = bio,
            onValueChange = { bio = acceptText(it, Limits.DOCTOR_BIO_MAX) },
            label = d.bio,
            placeholder = d.bioHint,
            singleLine = false,
        )
        SadoraButton(
            if (saving) strings.common.saving else d.save,
            enabled = changed && workplace.isNotBlank() && !saving,
            onClick = {
                saving = true
                scope.launch {
                    if (doctors.update(workplace = workplace.trim(), bio = bio.trim())) onSaved()
                    saving = false
                }
            },
        )
    }
}

// ---------------------------------------------------------------- the application

/** One page of proof she picked, with the kind she says it is. */
private data class PickedDocument(val photo: CapturedPhoto, val kind: DoctorDocumentKind)

/**
 * The application form. Prefilled from a rejected application, so trying again is a
 * correction, not a retyping. The documents are always picked again: the server keeps
 * them for the admin and never sends them back.
 */
@Composable
fun DoctorApplyScreen(
    doctors: DoctorController,
    onSubmitted: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val previous = doctors.account
    val calls = doctors.applyCalls

    var fullName by remember { mutableStateOf(previous?.fullName.orEmpty()) }
    var specialty by remember { mutableStateOf(previous?.specialty) }
    var workplace by remember { mutableStateOf(previous?.workplace.orEmpty()) }
    var experience by remember { mutableStateOf(previous?.experienceYears?.toString().orEmpty()) }
    var license by remember { mutableStateOf(previous?.licenseNumber.orEmpty()) }
    var bio by remember { mutableStateOf(previous?.bio.orEmpty()) }
    val documents = remember { mutableStateListOf<PickedDocument>() }
    var sending by remember { mutableStateOf(false) }

    // The next page is a diploma until there is one, then a licence, then "other".
    val picker = rememberPhotoCapture { photo ->
        if (documents.size < Limits.DOCTOR_DOCUMENTS_MAX) {
            val kind = when {
                documents.none { it.kind == DoctorDocumentKind.DIPLOMA } -> DoctorDocumentKind.DIPLOMA
                documents.none { it.kind == DoctorDocumentKind.LICENSE } -> DoctorDocumentKind.LICENSE
                else -> DoctorDocumentKind.OTHER
            }
            documents.add(PickedDocument(photo, kind))
        }
    }

    LaunchedEffect(Unit) { calls.clearError() }

    val years = experience.toIntOrNull()
    val canSend = fullName.trim().length >= Limits.DOCTOR_NAME_MIN &&
        specialty != null &&
        workplace.isNotBlank() &&
        years != null && years in Limits.DOCTOR_EXPERIENCE_YEARS &&
        license.isNotBlank() &&
        documents.isNotEmpty() &&
        !sending

    Column(modifier) {
        SadoraTopBar(d.applyTitle, onBack = onClose)
        ScreenContent(stagger = false) {
            item {
                SadoraTextField(
                    value = fullName,
                    onValueChange = { fullName = acceptText(it, Limits.DOCTOR_NAME_MAX) },
                    label = d.fullName,
                    placeholder = d.fullNameHint,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // Drawn like the text fields' own labels above and below it.
                    Text(d.specialtyLabel.uppercase(), style = Sadora.type.caption, color = c.muted)
                    ChipFlowRow {
                        DoctorSpecialty.entries.forEach { option ->
                            SelectChip(label = d.specialty(option), selected = specialty == option, onClick = { specialty = option })
                        }
                    }
                }
            }
            item {
                SadoraTextField(
                    value = workplace,
                    onValueChange = { workplace = acceptText(it, Limits.DOCTOR_WORKPLACE_MAX) },
                    label = d.workplace,
                    placeholder = d.workplaceHint,
                )
            }
            item {
                SadoraTextField(
                    value = experience,
                    onValueChange = { value -> experience = value.filter(Char::isDigit).take(2) },
                    label = d.experienceLabel,
                    keyboardType = KeyboardType.Number,
                )
            }
            item {
                SadoraTextField(
                    value = license,
                    onValueChange = { license = acceptText(it, Limits.DOCTOR_LICENSE_MAX) },
                    label = d.license,
                )
            }
            item {
                SadoraTextField(
                    value = bio,
                    onValueChange = { bio = acceptText(it, Limits.DOCTOR_BIO_MAX) },
                    label = d.bio,
                    placeholder = d.bioHint,
                    singleLine = false,
                )
            }
            item {
                // The card grows and shrinks with the pages in it rather than jumping.
                SadoraCard(Modifier.animateContentSize(tween(Motion.Standard, easing = Motion.Emphasized))) {
                    Text(d.documents, style = Sadora.type.h3, color = c.text)
                    Text(d.documentsHint, style = Sadora.type.body, color = c.muted)
                    documents.forEachIndexed { index, document ->
                        key(document.photo) {
                            ArrivingPage {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Icon(SadoraIcons.Document, contentDescription = null, Modifier.size(IconSize.md), tint = c.primary)
                                        Text(
                                            "${index + 1}. ${d.documentKind(document.kind)}",
                                            style = Sadora.type.h3,
                                            color = c.text,
                                            modifier = Modifier.weight(1f),
                                        )
                                        PillButton(d.remove, onClick = { documents.removeAt(index) })
                                    }
                                    ChipFlowRow(horizontalGap = Spacing.xxs) {
                                        DoctorDocumentKind.entries.forEach { kind ->
                                            SelectChip(
                                                label = d.documentKind(kind),
                                                selected = document.kind == kind,
                                                onClick = { documents[index] = document.copy(kind = kind) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (documents.size < Limits.DOCTOR_DOCUMENTS_MAX) {
                        if (picker.available) {
                            SadoraButton(
                                d.addDocument,
                                onClick = picker::pickFromGallery,
                                tone = ButtonTone.Secondary,
                                icon = SadoraIcons.Camera,
                            )
                        } else {
                            Text(d.galleryUnavailable, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }
            }
            calls.error?.let { failure -> item { ErrorStrip(failure.readable()) } }
            item { Text(d.confirmNote, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
            item {
                SadoraButton(
                    if (sending) strings.auth.sending else d.submit,
                    enabled = canSend,
                    onClick = {
                        val chosen = specialty ?: return@SadoraButton
                        val experienceYears = years ?: return@SadoraButton
                        sending = true
                        scope.launch {
                            val ok = doctors.apply(
                                DoctorApplicationRequest(
                                    fullName = fullName.trim(),
                                    specialty = chosen,
                                    workplace = workplace.trim(),
                                    experienceYears = experienceYears,
                                    licenseNumber = license.trim(),
                                    bio = bio.trim().ifEmpty { null },
                                    documents = documents.map {
                                        DoctorDocumentUpload(it.kind, it.photo.base64, it.photo.mimeType)
                                    },
                                ),
                            )
                            sending = false
                            if (ok) onSubmitted()
                        }
                    },
                )
            }
        }
    }
}

/** A picked page fades and unfolds into the card instead of appearing in one frame. */
@Composable
private fun ArrivingPage(content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = true
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(Motion.Standard)) + expandVertically(tween(Motion.Standard, easing = Motion.Emphasized)),
    ) {
        content()
    }
}

@Composable
private fun monthYear(at: Instant): String {
    val date = at.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return strings.dates.monthYear(date.year, date.month.ordinal + 1)
}

@Composable
private fun dayMonth(at: Instant): String =
    strings.dates.dayMonth(at.toLocalDateTime(TimeZone.currentSystemDefault()).date)
