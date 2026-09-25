package uz.sadora.doctor.i18n

import kotlinx.datetime.LocalDate
import uz.sadora.contract.CommunityTopic

object StringsEn : Strings {
    override val language = AppLanguage.En
    override val languageName = "English"

    override val common = object : CommonStrings {
        override val appName = "Sadora Doctor"
        override val back = "Back"
        override val retry = "Try again"
        override val cancel = "Cancel"
        override val saving = "Saving…"
        override val send = "Send"
    }

    override val auth = object : AuthStrings {
        override val title = "Sadora for doctors"
        override val subtitle = "Sign in with the number you use in the Sadora app. Apply, and once " +
            "you're verified, answer women's questions."
        override val phoneLabel = "Phone number"
        override val phoneNote = "The number is only used to sign in. We'll text a one-time code to it."
        override val sendCode = "Send the code"
        override val sending = "Sending…"
        override val codeTitle = "Enter the code"
        override fun codeSubtitle(phone: String) = "We sent a 6-digit code to +998 $phone."
        override val confirm = "Confirm"
        override val checking = "Checking…"
        override fun resendIn(seconds: Int) = "Send again · ${seconds}s"
        override val resend = "Send the code again"
        override val changeNumber = "Change the number"
        override val codeSecrecy = "Never share the code. Sadora staff will not ask for it."
        override val devCodeFilled = "The test server sent the code back, so it's filled in."
        override fun otpEntered(entered: Int, length: Int) = "Verification code: $entered of $length digits entered"
        override val deleteDigit = "Delete the last digit"
    }

    override val errors = object : ErrorStrings {
        override val phoneInvalid = "That number is incomplete, or no operator uses that code"
        override val network = "Could not reach the internet. Try again."
        override val validation = "Something you entered is not right."
        override val sessionExpired = "Your session has ended. Please sign in again."
        override val blocked = "This account is blocked. Please contact Sadora."
        override val forbidden = "You cannot do this."
        override val notFound = "Not found — it may have been deleted."
        override fun retryAfter(seconds: Int) = "Too many attempts. Try again in $seconds s."
        override val retrySoon = "Too many attempts. Try again shortly."
        override val otpInvalid = "That code is wrong or has expired."
        override val featureClosed = "This section is closed for now."
        override val unexpected = "Something went wrong. Try again."
    }

    override val dates = object : DateStrings {
        override val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        override fun dayMonth(date: LocalDate) = "${date.day} ${months[date.month.ordinal]}"

        override val yesterday = "Yesterday"
        override val justNow = "just now"
        override fun minutesAgo(minutes: Int) = "$minutes min ago"
        override fun hoursAgo(hours: Int) = if (hours == 1) "1 hour ago" else "$hours hours ago"
        override fun daysAgo(days: Int) = "$days days ago"
    }

    override val community = object : CommunityStrings {
        override fun topic(topic: CommunityTopic) = when (topic) {
            CommunityTopic.CYCLE -> "Cycle"
            CommunityTopic.PREGNANCY -> "Pregnancy"
            CommunityTopic.WELLBEING -> "Wellbeing"
            CommunityTopic.BODY -> "Body"
        }
        override val you = "you"
        override val readMore = "…more"
        override fun commentsCount(count: Int) = when (count) {
            0 -> "Replies"
            1 -> "1 reply"
            else -> "$count replies"
        }
        override val noComments = "No replies yet. Be the first."
        override val questionTitle = "Question"
        override val postTitle = "Post"
        override val postMissing = "This post was deleted or hidden."
        override val answerHint = "Write your answer"
        override val answerSent = "Your answer was sent"
        override val newPost = "Write a post"
        override val newPostTitle = "New post"
        override val topicLabel = "Topic"
        override val postHint = "Share a useful tip or an explanation for women…"
        override fun postTooShort(min: Int) = "At least $min characters"
        override val publish = "Publish"
        override val published = "Post published"
    }

    override val settings = object : SettingsStrings {
        override val title = "Settings"
        override val language = "Language"
        override val account = "Account"
        override fun signedInAs(phone: String) = "Signed in as $phone"
        override val signOut = "Sign out"
        override val signOutTitle = "Sign out?"
        override val signOutBody = "You'll need an SMS code to sign back in."
    }

    override val doctors: DoctorStrings = DoctorStringsEn
}
