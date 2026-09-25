package uz.sadora.app.i18n

import uz.sadora.contract.ShopKind

/**
 * Gul, the streak, the shop and the invite screen.
 *
 * In its own file rather than in [Strings] because it is a whole section of the product
 * and the main file is already long — but under the same rule: these are interfaces, so
 * a line added here is a compile error in the two languages that have not answered it.
 *
 * One string in here is load-bearing. [coinName] is the currency's name, and it is used
 * everywhere the balance is drawn. Renaming the currency is an edit to three lines.
 */
interface RewardStrings {
    /** The currency's name — "Gul". Everything else reads off this. */
    val coinName: String

    /** "1 200 nur" — a balance with its unit, already formatted. */
    fun coins(amount: String): String

    /** "+50 nur" — an award, with its sign. */
    fun coinsGained(amount: String): String

    // ---- the streak overlay, shown once a day ----

    /** "7 kun ketma-ket" — the big line of the celebration. */
    fun streakDays(days: Int): String

    /** The first day of a run, where "1 kun ketma-ket" would read oddly. */
    val streakStarted: String

    /** The line under the number: what she has just done. */
    val streakSubtitle: String

    /** "🎉 30 kun!" — shown instead when the open landed on a milestone. */
    fun milestoneReached(days: Int): String

    /** "Yana 4 kun — keyingi bosqich" under the ring. */
    fun daysToMilestone(days: Int, milestone: Int): String

    /** Past the last milestone, where there is nothing left to count toward. */
    val streakBeyondMilestones: String

    // ---- the wallet ----

    val walletTitle: String
    val balance: String
    val earned: String
    val spent: String
    val currentStreak: String
    val longestStreak: String
    fun days(count: Int): String
    val history: String
    val historyEmpty: String
    val howToEarn: String
    /** "kuniga 3 martagacha" — the cap on an earn rate, when it has one. */
    fun perDay(times: Int): String
    fun earnReason(reason: String): String
    val openShop: String
    val inviteFriends: String

    // ---- referral ----

    val referralTitle: String
    val referralSubtitle: String
    val yourCode: String
    val copyCode: String
    val codeCopied: String
    val shareLink: String
    /** The message the share sheet sends. */
    fun shareMessage(link: String): String
    fun invitedCount(count: Int): String
    fun referralEarned(amount: String): String
    /** "Har bir do'st uchun 200 nur" — what the next accepted invite pays. */
    fun rewardPerJoin(amount: String): String
    /** What the person invited receives, said on the same card. */
    fun welcomeReward(amount: String): String
    val referralHowTitle: String
    val referralSteps: List<String>
    /** The boundary: a code pays once, for a new account. */
    val referralFairUse: String
}

/** The Gul shop: Premium, vitamins and devices. */
interface ShopStrings {
    val title: String
    val subtitle: String
    fun tab(kind: ShopKind): String
    val empty: String
    val loading: String

    /** "15% chegirma" on a partner card. */
    fun discount(percent: Int): String
    /** "145 000 so'm → 123 250 so'm" — the price before and after. */
    fun priceWas(price: String): String
    fun priceNow(price: String): String
    fun saving(amount: String): String
    /** "7 kun Premium" on a Premium card. */
    fun premiumDays(days: Int): String
    val outOfStock: String
    fun stockLeft(count: Int): String
    val notEnough: String
    fun shortBy(amount: String): String

    // ---- the redeem sheet ----

    val redeem: String
    val redeeming: String
    fun confirmTitle(product: String): String
    fun confirmBody(cost: String): String
    val confirmPremiumBody: String
    val cancel: String

    // ---- what she got ----

    val issuedTitle: String
    val issuedPremiumTitle: String
    val issuedBody: String
    val issuedPremiumBody: String
    val yourCode: String
    val copyCode: String
    val codeCopied: String
    fun validUntil(date: String): String
    val myCodes: String
    val myCodesEmpty: String
    val statusIssued: String
    val statusUsed: String
    val statusExpired: String
    val statusCancelled: String

    /** The one boundary line on the shop: what SADORA is and is not doing here. */
    val partnerNote: String
}

/** The "arrange my home screen" screen. */
interface HomeLayoutStrings {
    val title: String
    val subtitle: String
    val visible: String
    val hidden: String
    val moveUp: String
    val moveDown: String
    val reset: String
    val alwaysOn: String
    fun widget(key: String): String
    fun widgetNote(key: String): String
}
