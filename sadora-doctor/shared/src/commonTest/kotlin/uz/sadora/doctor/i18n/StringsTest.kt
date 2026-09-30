package uz.sadora.doctor.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.FlowLevel
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.LifeStage
import uz.sadora.contract.ReportReason
import uz.sadora.doctor.data.ApiFailure
import uz.sadora.doctor.data.readable

/**
 * The interface already guarantees that every language answers every string — that is
 * why it is an interface. What it cannot guarantee is that the answer is a translation:
 * a blank, or the Uzbek pasted across, compiles perfectly well.
 */
class StringsTest {

    private val languages = listOf(StringsUz, StringsRu, StringsEn)

    private val sampleDate = LocalDate.parse("2026-09-04")
    private val now = Instant.parse("2026-09-26T09:00:00Z")

    /** Everything a language says, in one list, so a test can look at all of it. */
    private fun everything(t: Strings): List<String> = buildList {
        add(t.languageName)
        with(t.common) { addAll(listOf(appName, back, retry, cancel, saving, send)) }
        with(t.auth) {
            addAll(
                listOf(
                    title, subtitle, phoneLabel, phoneNote, sendCode, sending, codeTitle,
                    codeSubtitle("90 123 45 67"), confirm, checking, resendIn(42), resend,
                    changeNumber, codeSecrecy, devCodeFilled, otpEntered(3, 6), deleteDigit,
                ),
            )
        }
        with(t.errors) {
            addAll(
                listOf(
                    phoneInvalid, network, validation, sessionExpired, blocked, forbidden,
                    notFound, retryAfter(30), retrySoon, otpInvalid, featureClosed, unexpected,
                ),
            )
        }
        with(t.dates) {
            addAll(months)
            addAll(
                listOf(
                    dayMonth(sampleDate), monthYear(2026, 9), yesterday, justNow,
                    minutesAgo(5), hoursAgo(2), daysAgo(3),
                ),
            )
        }
        with(t.community) {
            CommunityTopic.entries.forEach { add(topic(it)) }
            addAll(
                listOf(
                    you, readMore, commentsCount(0), commentsCount(1), commentsCount(7), noComments,
                    questionTitle, postTitle, postMissing, answerHint, answerSent, newPost,
                    newPostTitle, topicLabel, postHint, postTooShort(2), publish, published,
                ),
            )
        }
        with(t.settings) {
            addAll(listOf(title, language, account, signedInAs("+998 90 123 45 67"), signOut, signOutTitle, signOutBody))
        }
        with(t.doctors) {
            DoctorSpecialty.entries.forEach { add(specialty(it)) }
            DoctorDocumentKind.entries.forEach { add(documentKind(it)) }
            addAll(introPoints)
            addAll(
                listOf(
                    verified, doctorAnswer, answeredBy(1), answeredBy(3), disclaimer, writingAs("Dr. N"),
                    profileTitle, verifiedSince("Sentabr 2026"), statPosts, statAnswers, statYears, herPosts,
                    noPosts, noPostsBody, panelTitle, introTitle, introBody, applyButton, pendingTitle,
                    pendingBody, rejectedTitle, reapply, adminNote, suspendedTitle, suspendedBody,
                    approvedTitle, approvedBody, myPage, editTitle, save, saved, questionsTitle,
                    questionsHint, questionsEmpty, questionsEmptyBody, submittedOn("4-sentabr"),
                    applyTitle, fullName, fullNameHint, specialtyLabel, workplace, workplaceHint,
                    experienceLabel, license, bio, bioHint, documents, documentsHint, addDocument,
                    remove, galleryUnavailable, submit, submitted, confirmNote,
                ),
            )
        }
        with(t.tabs) {
            addAll(listOf(greeting(8), greeting(14), greeting(22)))
            LifeStage.entries.forEach { add(lifeStage(it)) }
            CyclePhase.entries.forEach { add(phase(it)) }
            FlowLevel.entries.forEach { add(flow(it)) }
            ReportReason.entries.forEach { add(reportReason(it)) }
            HealthMetric.entries.forEach { metric -> metric(metric)?.let { add(it) } }
            addAll(
                listOf(
                    home, messages, scan, community, profile, homeSubtitle, statWaiting, statUnread,
                    scanPatient, scanPatientBody, seeAll, messagesEmpty, messagesEmptyBody, messageHint,
                    conversationClosed, unread(3), scanTitle, scanHint, cameraStarting, cameraDenied,
                    openSettings, cameraMissing, notPatientCode, pasteLabel, open, recentTitle, recentNote,
                    communityTitle, filterAll, filterDoctors, feedEmpty, feedEmptyBody, recordTitle,
                    recordGone, recordGoneBody, recordDisclaimer, generatedAt("4-sentabr, 09:00"), age(31),
                    heightLabel, weightLabel, cm(165), kg(58), days(28), memberSince("Sentabr 2026"),
                    nothingYet, cycleTitle, cycleDay, phaseLabel, lastPeriod, averageCycle, averagePeriod,
                    cycleRange, nextPeriod, estimated, pregnancyTitle, weekLabel, dueDate, birthDate,
                    lessMovement(2), symptomsTitle, symptomsWindow(90), recentDaysTitle, mindTitle,
                    daysLogged, averageMood, averageEnergy, averageStress, medsTitle, adherence(90),
                    medFinished, appointmentsTitle, nutritionTitle, averageKcal, averageWater,
                    wearableTitle, kcal(1800), ml(2000), hoursMinutes(450),
                    consultationOpen(5, 20), consultationOpen(0, 12), consultationClosed, consultationClosedBody,
                    closeConsultation, closeConfirmTitle, closeConfirmBody, closeConfirm, typing, photo, record,
                    recordCardTitle, recordCardBody, viewRecord, recordClosed, attachPhoto, photoFailed, report,
                    reportTitle, reportSent, sent, read, namesNotice, acceptsTitle, acceptsBody,
                ),
            )
        }
        with(t.work) {
            ConsultationPayment.entries.forEach { add(payment(it)) }
            (1..7).forEach { add(weekday(it)) }
            listOf(null, "doctor", "expired", "refund", "other").forEach { add(closedReason(it)) }
            addAll(
                listOf(
                    som("50 000"), free, settingsTitle, priceTitle, priceLabel, priceNote, priceTooLow,
                    priceTooHigh, commission(20), youGet("40 000"), busyTitle, busyBody, hoursTitle, hoursNote,
                    dayOff, hoursFrom, hoursTo, earlier, later, timezone("Asia/Tashkent"), hoursInvalid,
                    workDays(1), workDays(5), noHours, statsTitle, statWeek, statMonth, statTotal, openNow,
                    avgReply, duration(12), duration(65), unanswered, rating, ratingValue("4.8", 12), noRating,
                    topTopics, noValue, earningsTitle, balance, gross, commissionLine, net, paidOut, refundDue,
                    earningsNote, linesTitle, linesEmpty, linesEmptyBody, payoutsTitle, payoutsEmpty, details,
                    quickReplies, quickRepliesBody, quickRepliesEmpty, quickRepliesEmptyBody, manageReplies,
                    addReply, editReply, replyTitle, replyTitleHint, replyBody, replyBodyHint, deleteReply,
                    deleteReplyTitle, deleteReplyBody, repliesMax(30), patientTitle, patientInfo, noteTitle,
                    notePrivate, noteHint, noteSaved, historyTitle, historyEmpty, summaryLabel, reviewLabel,
                    recordsLabel, recordNumber(1), closeSummaryLabel, closeSummaryHint, writeSummary,
                    writeSummaryBody, summarySent, yourSummary, awaitingReply,
                ),
            )
        }
    }

    @Test
    fun `no language leaves a string blank`() {
        languages.forEach { t ->
            everything(t).forEachIndexed { index, text ->
                assertTrue(text.isNotBlank(), "${t.language}: string #$index is blank")
            }
        }
    }

    @Test
    fun `every language says the same number of things`() {
        val sizes = languages.map { everything(it).size }
        assertEquals(1, sizes.distinct().size, "$sizes")
    }

    @Test
    fun `the intro bullets are the same length in every language and none is blank`() {
        val points = languages.map { it.doctors.introPoints }
        assertEquals(1, points.map { it.size }.distinct().size, "${points.map { it.size }}")
        assertTrue(points.first().isNotEmpty())
        points.flatten().forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `russian and english are translations — not the uzbek copied across`() {
        val uz = everything(StringsUz)
        listOf(StringsRu, StringsEn).forEach { t ->
            val other = everything(t)
            // Names, numbers and the app's own name may coincide; most lines must not.
            val same = uz.indices.count { uz[it] == other[it] }
            assertTrue(same * 10 < uz.size, "${t.language}: $same of ${uz.size} lines are the Uzbek text")
        }
    }

    @Test
    fun `the weekdays and payment states are told apart within each language`() {
        languages.forEach { t ->
            val days = (1..7).map { t.work.weekday(it) }
            assertEquals(7, days.distinct().size, "${t.language}: $days")
            val payments = ConsultationPayment.entries.map { t.work.payment(it) }
            assertEquals(payments.size, payments.distinct().size, "${t.language}: $payments")
        }
    }

    @Test
    fun `the record's metrics are the same set in every language`() {
        val shown = languages.map { t -> HealthMetric.entries.filter { t.tabs.metric(it) != null } }
        assertEquals(1, shown.distinct().size, "$shown")
    }

    @Test
    fun `russian ages agree with their number`() {
        val t = StringsRu.tabs
        assertEquals("21 год", t.age(21))
        assertEquals("23 года", t.age(23))
        assertEquals("25 лет", t.age(25))
        assertEquals("11 лет", t.age(11))
    }

    @Test
    fun `specialties and document kinds are told apart within each language`() {
        languages.forEach { t ->
            val specialties = DoctorSpecialty.entries.map { t.doctors.specialty(it) }
            assertEquals(specialties.size, specialties.distinct().size, "${t.language}: $specialties")
            val kinds = DoctorDocumentKind.entries.map { t.doctors.documentKind(it) }
            assertEquals(kinds.size, kinds.distinct().size, "${t.language}: $kinds")
        }
    }

    @Test
    fun `every failure reads as a sentence in every language`() {
        val failures = listOf(
            ApiFailure.Network("x"),
            ApiFailure.Validation("x", emptyMap()),
            ApiFailure.Unauthorized("x"),
            ApiFailure.Blocked("x"),
            ApiFailure.Forbidden("x"),
            ApiFailure.NotFound("x"),
            ApiFailure.RateLimited("x", null),
            ApiFailure.Otp("otp_invalid", ""),
            ApiFailure.FeatureDisabled("community", "x"),
            ApiFailure.Unexpected("x"),
        )
        languages.forEach { t ->
            failures.forEach { failure ->
                val text = failure.readable(t.errors)
                assertTrue(text.isNotBlank() && text != "x", "${t.language}: $failure reads as '$text'")
            }
        }
        // The server's own words are kept where they name the field.
        assertEquals("Ism juda qisqa", ApiFailure.Validation("x", mapOf("fullName" to "Ism juda qisqa")).readable(StringsUz.errors))
    }

    @Test
    fun `ages read the way a feed reads them`() {
        val t = StringsUz.dates
        assertEquals(t.justNow, t.ago(now, now))
        assertEquals(t.minutesAgo(5), t.ago(now - 5.minutes, now))
        assertEquals(t.hoursAgo(3), t.ago(now - 3.hours, now))
        assertEquals(t.yesterday, t.ago(now - 30.hours, now))
        assertEquals(t.daysAgo(4), t.ago(now - 4.days, now))
    }

    @Test
    fun `a language code round-trips and anything else is uzbek`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromCode(it.code)) }
        assertEquals(AppLanguage.Uz, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.Uz, AppLanguage.fromCode("de"))
        AppLanguage.entries.forEach { assertEquals(it, stringsFor(it).language) }
        assertNotEquals(StringsRu.languageName, StringsUz.languageName)
    }
}
