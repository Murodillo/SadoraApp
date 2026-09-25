package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import uz.sadora.contract.CommunityComment
import uz.sadora.contract.CommunityPost
import uz.sadora.contract.Limits
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.MinTouchTarget
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.Motion
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SuccessCheck
import uz.sadora.doctor.ui.components.acceptText
import uz.sadora.doctor.ui.components.noRippleClickable

// Ported from the client app's chat (SecretChatScreen.kt): the card and the comment rows
// read exactly as the women reading them see them. What a doctor does not do here —
// like, save, share, report — is left out rather than drawn and disabled.

// ---------------------------------------------------------------- post

/**
 * One post: the head, the text cut at a few lines with "…ko'proq" inline, and how many
 * replies it has. The card itself opens the post's page; only the "more" link expands
 * in place.
 */
@Composable
internal fun PostCard(
    post: CommunityPost,
    /** Null on the post's own page, where there is nowhere further to open it to. */
    onOpen: (() -> Unit)?,
    /** False on the post's own page, where the whole text is the point. */
    foldable: Boolean = true,
    /** She has just answered it: the card says so on its way out of the work list. */
    answered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.community
    val doctor = post.doctor
    SadoraCard(modifier = modifier, onClick = onOpen.takeUnless { answered }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (doctor != null) DoctorAvatar(post.alias) else AliasAvatar(post.alias, post.tint)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        if (post.isMine) "${post.alias} · ${t.you}" else post.alias,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (doctor != null) VerifiedMark()
                }
                Text(
                    // A doctor's specialty leads: it is why her post is worth reading.
                    (doctor?.let { strings.doctors.specialty(it.specialty) + " · " } ?: "") +
                        "${t.topic(post.topic)} · " + strings.dates.ago(post.createdAt, Clock.System.now()),
                    style = Sadora.type.body,
                    color = c.muted2,
                    maxLines = 1,
                )
            }
        }
        // A long post is cut at the fold so the list stays a list, with the link on the
        // same line as the cut. Tapping it expands in place; tapping the text opens the page.
        var expanded by remember(post.id) { mutableStateOf(false) }
        val folded = foldable && !expanded && post.body.length > FoldLength
        if (folded) {
            Text(
                buildAnnotatedString {
                    append(post.body.take(FoldLength).trimEnd())
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "more",
                            styles = TextLinkStyles(SpanStyle(color = c.textAccent, fontWeight = FontWeight.Medium)),
                        ) { expanded = true },
                    ) { append(t.readMore) }
                },
                style = Sadora.type.body,
                color = c.text,
            )
        } else {
            Text(post.body, style = Sadora.type.body, color = c.text)
        }
        // A question a doctor has answered says so on the card — and when that doctor is
        // her, a moment ago, the chip unfolds in rather than appearing.
        AnimatedVisibility(
            visible = doctor == null && post.doctorAnswers > 0,
            enter = fadeIn(tween(Motion.Standard)) + expandVertically(tween(Motion.Standard, easing = Motion.Emphasized)),
            exit = fadeOut(tween(Motion.Quick)) + shrinkVertically(tween(Motion.Quick)),
        ) {
            DoctorAnsweredChip(post.doctorAnswers, Modifier.noRippleClickable(onClick = onOpen ?: {}))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SadoraIcons.Message, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted)
            Text(
                t.commentsCount(post.commentCount),
                style = Sadora.type.body.copy(fontWeight = FontWeight.Medium),
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
            // Pops in with the success tick when she has just answered it. The state starts
            // hidden, so the mark plays its entrance even on a card that is drawn answered.
            val shown = remember { MutableTransitionState(false) }
            shown.targetState = answered
            AnimatedVisibility(
                visibleState = shown,
                enter = fadeIn(tween(Motion.Standard)) + scaleIn(tween(Motion.Standard, easing = Motion.Emphasized), initialScale = 0.8f),
                exit = fadeOut(tween(Motion.Quick)),
            ) {
                AnsweredMark()
            }
        }
    }
}

/** "Javobingiz yuborildi", with the tick written in — on a question she has just answered. */
@Composable
private fun AnsweredMark() {
    val c = Sadora.colors
    Row(
        Modifier
            .clip(Radius.chip)
            .background(c.success.copy(alpha = if (c.isDark) 0.22f else 0.12f))
            .padding(start = 4.dp, end = Spacing.xs, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SuccessCheck(size = 18.dp)
        Text(
            strings.community.answerSent,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
            color = c.successText,
            maxLines = 1,
        )
    }
}

/** Characters a post shows before it is folded. About six lines on a phone. */
private const val FoldLength = 280

/** The client's alias tints, in its order, so an alias keeps the colour she knows it by. */
@Composable
private fun avatarTints(): List<Color> {
    val c = Sadora.colors
    return listOf(c.primary, c.secondary, c.accent, c.success)
}

@Composable
internal fun AliasAvatar(alias: String, tint: Int, size: Dp = 36.dp) {
    val tints = avatarTints()
    val colour = tints[tint.mod(tints.size)]
    Box(
        Modifier.size(size).clip(Radius.chip).background(colour.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(alias.take(1).uppercase(), style = Sadora.type.h3, color = colour)
    }
}

// ---------------------------------------------------------------- comments

/** One comment on the post page: the alias, its age, and the text. */
@Composable
internal fun CommentRow(comment: CommunityComment, modifier: Modifier = Modifier) {
    if (comment.doctor != null) {
        DoctorCommentRow(comment, modifier)
        return
    }
    val c = Sadora.colors
    val t = strings.community
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AliasAvatar(comment.alias, comment.tint, size = 30.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                (if (comment.isMine) "${comment.alias} (${t.you})" else comment.alias) +
                    " · " + strings.dates.ago(comment.createdAt, Clock.System.now()),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.muted2,
            )
            Text(comment.body, style = Sadora.type.body, color = c.text)
        }
    }
}

/**
 * A verified doctor's answer: set apart on a tinted card, labelled, with her name,
 * her specialty and the check mark, and the reminder that it is not a diagnosis.
 */
@Composable
private fun DoctorCommentRow(comment: CommunityComment, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val d = strings.doctors
    val doctor = comment.doctor ?: return
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.primary.copy(alpha = if (c.isDark) 0.16f else 0.07f))
            .border(1.dp, c.primary.copy(alpha = 0.35f), Radius.cardSmall)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            d.doctorAnswer.uppercase(),
            style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold),
            color = c.textAccent,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            DoctorAvatar(comment.alias, size = 30.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        if (comment.isMine) "${comment.alias} (${strings.community.you})" else comment.alias,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VerifiedMark(size = 14.dp)
                }
                Text(
                    d.specialty(doctor.specialty) + " · " + strings.dates.ago(comment.createdAt, Clock.System.now()),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted2,
                )
            }
        }
        Text(comment.body, style = Sadora.type.body, color = c.text)
        Text(d.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    }
}

/**
 * The answer field with its send button, which appears only once there is something to
 * send. The client's comment field, grown to several lines: an answer is rarely one.
 */
@Composable
internal fun AnswerInput(
    /** False when it did not go: the text then stays in the field instead of being lost. */
    onSend: suspend (String) -> Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = strings.community.answerHint,
    maxLength: Int = Limits.COMMENT_MAX,
) {
    val c = Sadora.colors
    val t = strings.common
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendScope = rememberCoroutineScope()
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SadoraTextField(
            value = draft,
            onValueChange = { draft = acceptText(it, maxLength) },
            placeholder = placeholder,
            singleLine = false,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = draft.isNotBlank(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .clip(Radius.chip)
                    .background(c.primary)
                    .noRippleClickable(enabled = !sending, role = Role.Button) {
                        val text = draft.trim()
                        sending = true
                        sendScope.launch {
                            // Cleared only once it is sent: a failed answer keeps its text.
                            if (onSend(text)) draft = ""
                            sending = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SadoraIcons.ArrowUp,
                    contentDescription = t.send,
                    Modifier.size(IconSize.md),
                    tint = c.onPrimary,
                )
            }
        }
    }
}

/** The small print under a field: "Kamida 2 ta belgi", "120 / 2000". */
@Composable
internal fun FieldNote(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    val c = Sadora.colors
    Text(
        text,
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
        color = if (warn) c.danger else c.muted2,
        modifier = modifier.defaultMinSize(minHeight = 16.dp),
    )
}
