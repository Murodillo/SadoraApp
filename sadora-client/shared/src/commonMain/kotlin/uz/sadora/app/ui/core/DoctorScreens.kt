package uz.sadora.app.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import uz.sadora.app.data.DoctorController
import uz.sadora.app.data.MessagesController
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.model.Conversation
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.LoadMoreRow
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SectionHeader
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.rememberShareAction
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.DisposableEffect
import kotlin.time.Clock
import uz.sadora.app.i18n.availabilityLine
import uz.sadora.app.i18n.consultationPriceLabel
import uz.sadora.app.i18n.ratingLine
import uz.sadora.app.model.hasEnoughRatings
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorReview
import uz.sadora.contract.PaymentProvider

// A verified doctor's public page, as a reader opens it from the chat. Applying, the
// panel and answering live in the doctor's own app, sadora-doctor.

/**
 * A verified doctor as readers see her: name, specialty, where she works, how long,
 * her own words, and what she has written — and, when she takes consultations, the
 * way to write to her.
 *
 * The button reads what will happen: "Suhbatni ochish" while a consultation with her is
 * open, "Shifokorga yozish" otherwise. The second never opens anything by itself; it
 * raises the consent sheet first, because a consultation is not anonymous and she has
 * to know that before the first word, not after.
 */
@Composable
fun DoctorProfileScreen(
    doctorId: String,
    state: AppState,
    doctors: DoctorController,
    messages: MessagesController,
    onOpenPost: (CommunityPost) -> Unit,
    onOpenMenu: (CommunityPost) -> Unit,
    /** Raises the consent sheet; the shell owns it so it covers the tab bar. */
    onMessage: (DoctorProfile) -> Unit,
    /** Raises the pay sheet, for a doctor who charges and a patient who has consented before. */
    onPay: (DoctorProfile) -> Unit,
    onOpenConversation: (id: String, name: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = strings.doctors
    val t = strings.community
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val share = rememberShareAction()

    LaunchedEffect(doctorId) {
        doctors.loadProfile(doctorId)
        doctors.loadReviews(doctorId)
    }
    val profile = doctors.profile?.takeIf { it.id == doctorId }
    val reviews = doctors.reviews.takeIf { doctors.reviewsFor == doctorId }.orEmpty()
    var allReviews by remember(doctorId) { mutableStateOf(false) }

    Column(modifier) {
        SadoraTopBar(d.profileTitle, onBack = onClose)
        ScreenContent {
            doctors.error?.let { failure ->
                item { ErrorStrip(failure.readable(), onRetry = { scope.launch { doctors.loadProfile(doctorId) } }) }
            }
            if (profile == null) {
                if (doctors.error == null) item { DoctorSkeleton() }
                return@ScreenContent
            }
            item { DoctorHeader(profile) }
            if (!profile.isMe) {
                item {
                    ConsultationAction(
                        profile = profile,
                        messages = messages,
                        onMessage = { onMessage(profile) },
                        onPay = { onPay(profile) },
                        onOpenConversation = { onOpenConversation(it, profile.fullName) },
                    )
                }
            }
            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(Modifier.fillMaxWidth()) {
                        DoctorStat(Fmt.int(profile.postCount), d.statPosts, Modifier.weight(1f))
                        DoctorStat(Fmt.int(profile.answerCount), d.statAnswers, Modifier.weight(1f))
                        DoctorStat(profile.experienceYears.toString(), d.statExperience, Modifier.weight(1f))
                    }
                }
            }
            if (reviews.isNotEmpty()) {
                // A few at first; "Hammasi" opens every one, read on a page at a time.
                val folded = !allReviews && (reviews.size > MaxReviewsShown || doctors.reviewsHasMore)
                item {
                    SectionHeader(
                        d.reviewsTitle,
                        action = d.seeAll.takeIf { folded },
                        onAction = { allReviews = true },
                    )
                }
                item { ReviewsCard(if (allReviews) reviews else reviews.take(MaxReviewsShown)) }
                if (allReviews && doctors.reviewsHasMore) {
                    item(key = "more-reviews") { LoadMoreRow(reviews.size, onLoadMore = { doctors.loadMoreReviews() }) }
                }
            }
            item { Text(d.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
            item { SectionHeader(d.herPosts) }
            val posts = doctors.profilePosts
            if (posts.isEmpty()) {
                item { Text(d.noPosts, style = Sadora.type.body, color = c.muted) }
            } else {
                items(posts.size, key = { posts[it].id }) { index ->
                    val post = posts[index]
                    PostCard(
                        post = post,
                        liked = post.id in state.likedPosts,
                        saved = post.id in state.savedPosts,
                        likes = state.likeCount(post),
                        comments = state.commentCountOf(post),
                        onLike = { state.toggleLike(post.id) },
                        onSave = { state.toggleSaved(post.id) },
                        onOpen = { onOpenPost(post) },
                        onOpenAuthor = {},
                        onShare = { share("${post.body}\n\n" + t.shareSuffix) },
                        onMore = { onOpenMenu(post) },
                    )
                }
                if (doctors.profilePostsHasMore) {
                    item(key = "more-posts") { LoadMoreRow(posts.size, onLoadMore = { doctors.loadMoreProfilePosts() }) }
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
        // "Yangi shifokor" until enough patients have rated her — the same rule as her card.
        Text(
            ratingLine(profile.rating, profile.ratingCount, d),
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (hasEnoughRatings(profile.ratingCount)) c.text else c.muted2,
        )
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

/**
 * The way into a consultation with her. Whether hers is still open is read from the
 * thread list — the page knows only that one exists — so the list is fetched once if
 * the thread is not in it yet.
 */
@Composable
private fun ConsultationAction(
    profile: DoctorProfile,
    messages: MessagesController,
    onMessage: () -> Unit,
    onPay: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val conversationId = profile.conversationId
    // By id when the page names it; by doctor when it was opened a moment ago from this
    // page and the page has not been re-read since.
    val existing = conversationId?.let { id -> messages.conversations.firstOrNull { it.id == id } }
        ?: messages.conversations.firstOrNull { it.doctor?.id == profile.id }
    LaunchedEffect(conversationId) {
        if (conversationId != null && messages.conversations.none { it.id == conversationId }) messages.load()
    }
    val openId = existing?.takeIf { it.consultation?.open == true }?.id
    val paid = profile.priceMinor > 0
    // She agreed to the consent points when she first wrote; a paid doctor she has
    // written to before goes straight to paying.
    val consented = existing != null || conversationId != null
    val availability = availabilityLine(profile.availability, d, strings.dates, Clock.System.now())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SadoraCard(padding = Spacing.sm) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (availability != null) AvailabilityRow(availability, Modifier.weight(1f)) else Box(Modifier.weight(1f))
                Text(
                    consultationPriceLabel(profile.priceMinor, d),
                    style = Sadora.type.h3,
                    color = if (paid) c.text else c.successText,
                )
            }
        }
        when {
            openId != null ->
                SadoraButton(d.openConsultation, onClick = { onOpenConversation(openId) }, icon = SadoraIcons.Message)
            profile.canMessage -> {
                SadoraButton(
                    d.messageDoctor,
                    onClick = if (paid && consented) onPay else onMessage,
                    icon = SadoraIcons.Message,
                )
                Text(
                    if (paid) d.paidNote(d.price(Fmt.sum(profile.priceMinor))) else d.messageDoctorNote,
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> Text(
                d.cannotMessage,
                style = Sadora.type.body,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // A closed consultation is still her history with this doctor; it stays readable.
        val historyId = existing?.id ?: conversationId
        if (openId == null && historyId != null) {
            SadoraButton(d.viewHistory, onClick = { onOpenConversation(historyId) }, tone = ButtonTone.Secondary)
        }
    }
}

/**
 * What she agrees to before a consultation opens: free for now, a day long, her real
 * name and age seen by the doctor, an answer that is not a diagnosis, 103 for an
 * emergency. Confirming opens the consultation — or opens hers again — and the thread.
 */
@Composable
fun ConsultationConsentSheetContent(
    profile: DoctorProfile,
    messages: MessagesController,
    onStarted: (Conversation) -> Unit,
    /** She charges: the pay sheet takes over from here. */
    onNeedsPayment: () -> Unit,
    onCancel: () -> Unit,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(profile.id) { messages.clearError() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DoctorAvatar(profile.fullName, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.fullName, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f, fill = false))
                VerifiedMark(size = 16.dp)
            }
            Text(d.specialty(profile.specialty), style = Sadora.type.body, color = c.textAccent)
        }
    }
    val icons = listOf(SadoraIcons.Clock, SadoraIcons.Profile, SadoraIcons.Info, SadoraIcons.Shield)
    val paid = profile.priceMinor > 0
    // The first point is the price: "free" for most, hers and the refund rule for a doctor who charges.
    val points = if (paid) listOf(d.consentPaidPoint(d.price(Fmt.sum(profile.priceMinor)))) + d.consentPoints.drop(1) else d.consentPoints
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        points.forEachIndexed { index, point ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(32.dp).clip(Radius.chip).background(c.primary.copy(alpha = if (c.isDark) 0.24f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icons[index % icons.size], contentDescription = null, Modifier.size(IconSize.sm), tint = c.primary)
                }
                Text(point, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f).padding(top = 4.dp))
            }
        }
    }
    messages.error?.let { ErrorStrip(it.readable()) }
    SadoraButton(
        if (paid) d.consentToPay else d.consentConfirm,
        enabled = !working,
        onClick = {
            if (paid) {
                onNeedsPayment()
                return@SadoraButton
            }
            working = true
            scope.launch {
                val started = messages.startConsultation(profile.id)
                working = false
                when {
                    started != null -> onStarted(started)
                    // Her price was set after this page was read.
                    messages.takePaymentRequired() -> onNeedsPayment()
                }
            }
        },
    )
    SadoraButton(strings.common.cancel, onClick = onCancel, tone = ButtonTone.Secondary)
}

/**
 * Paying for a window with a doctor who charges: her price, what it buys, a choice of
 * Payme or Click, and one button. After that, the sheet waits.
 *
 * The provider's page opens in the browser. While the app is in front the server is
 * asked every few seconds whether the money arrived, and asked again the moment she
 * comes back from the browser — which is when it usually has. Paid, [onPaid] takes
 * her into the thread; the server opened the window itself when the callback landed.
 */
@Composable
fun ConsultationPaySheetContent(
    doctorId: String,
    doctors: DoctorController,
    onPaid: () -> Unit,
    onCancel: () -> Unit,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(doctorId) {
        doctors.clearPayError()
        // Raised from a thread, the page in memory may be another doctor's, or stale.
        if (doctors.profile?.id != doctorId || doctors.profile?.paymentProviders.isNullOrEmpty()) doctors.loadProfile(doctorId)
    }
    // Leaving the sheet ends the wait; the payment itself is the server's to settle.
    DisposableEffect(doctorId) { onDispose { doctors.cancelCheckout() } }

    val profile = doctors.profile?.takeIf { it.id == doctorId }
    val waiting = doctors.checkout?.takeIf { doctors.checkoutFor == doctorId }

    LifecycleResumeEffect(waiting?.transactionId) {
        val job = if (waiting == null) null else scope.launch { if (doctors.awaitPayment()) onPaid() }
        onPauseOrDispose { job?.cancel() }
    }

    if (profile == null) {
        doctors.error?.let { ErrorStrip(it.readable()) } ?: Skeleton(Modifier.fillMaxWidth().height(120.dp))
        SadoraButton(strings.common.cancel, onClick = onCancel, tone = ButtonTone.Secondary)
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DoctorAvatar(profile.fullName, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.fullName, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f, fill = false))
                VerifiedMark(size = 16.dp)
            }
            Text(d.specialty(profile.specialty), style = Sadora.type.body, color = c.textAccent)
        }
    }
    SadoraCard(padding = Spacing.md, verticalGap = Spacing.xxs) {
        Text(d.price(Fmt.sum(profile.priceMinor)), style = Sadora.type.h1, color = c.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(SadoraIcons.Clock, contentDescription = null, Modifier.size(IconSize.sm), tint = c.muted)
            Text(d.payWindow, style = Sadora.type.body, color = c.muted)
        }
    }

    val providers = profile.paymentProviders.filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }
    var chosen by remember(doctorId) { mutableStateOf<PaymentProvider?>(null) }
    val provider = chosen?.takeIf { it in providers } ?: providers.firstOrNull()

    if (waiting != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CircularProgressIndicator(Modifier.size(22.dp), color = c.primary, strokeWidth = 2.5.dp)
            Text(d.payWaiting, style = Sadora.type.h3, color = c.text)
        }
        Text(d.payWaitingBody, style = Sadora.type.body, color = c.muted)
        SadoraButton(d.payReopenPage, onClick = { uriHandler.openUri(waiting.url) }, tone = ButtonTone.Secondary)
        SadoraButton(strings.common.cancel, onClick = { doctors.cancelCheckout() }, tone = ButtonTone.Ghost)
        return
    }

    if (providers.isEmpty()) {
        Text(d.payNoProvider, style = Sadora.type.body, color = c.muted)
    } else {
        Text(d.payProvider, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            providers.forEach { option ->
                SelectChip(
                    label = option.displayName(),
                    selected = option == provider,
                    onClick = { chosen = option },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    doctors.payError?.let { ErrorStrip(it.readable()) }
    SadoraButton(
        d.pay,
        enabled = provider != null && !doctors.startingCheckout,
        onClick = {
            val method = provider ?: return@SadoraButton
            scope.launch {
                val session = doctors.startCheckout(doctorId, method) ?: return@launch
                uriHandler.openUri(session.url)
            }
        },
    )
    SadoraButton(strings.common.cancel, onClick = onCancel, tone = ButtonTone.Secondary)
}

private fun PaymentProvider.displayName(): String = when (this) {
    PaymentProvider.PAYME -> "Payme"
    PaymentProvider.CLICK -> "Click"
    PaymentProvider.APP_STORE -> "App Store"
    PaymentProvider.GOOGLE_PLAY -> "Google Play"
}

/** Her latest reviews: stars, the day, and the words when there are any. Never a name. */
@Composable
private fun ReviewsCard(reviews: List<DoctorReview>) {
    val d = strings.doctors
    val c = Sadora.colors
    SadoraCard(padding = Spacing.md, verticalGap = Spacing.sm) {
        reviews.forEachIndexed { index, review ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StarRow(review.rating)
                    Text(
                        d.anonymousPatient + " · " + dayMonth(review.createdAt),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                        maxLines = 1,
                    )
                }
                review.review?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = Sadora.type.body, color = c.text)
                }
            }
        }
    }
}

/** How many reviews her page shows before "Hammasi" opens the rest. */
private const val MaxReviewsShown = 5

@Composable
private fun dayMonth(at: Instant): String =
    strings.dates.dayMonth(at.toLocalDateTime(TimeZone.currentSystemDefault()).date)

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
        Skeleton(Modifier.fillMaxWidth().size(height = 72.dp, width = 0.dp))
    }
}

@Composable
private fun monthYear(at: Instant): String {
    val date = at.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return strings.dates.monthYear(date.year, date.month.ordinal + 1)
}
