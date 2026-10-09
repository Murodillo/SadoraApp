package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Plans, payments and the provider callbacks behind them.
 *
 * Amounts are `long` minor units throughout — tiyin for UZS. There is no decimal column
 * in this file on purpose: the providers count in minor units, and every conversion is
 * one more place for a price to drift by a coin.
 */

object BillingPlans : Table("billing_plans") {
    val id = text("id")
    val title = text("title")
    val period = text("period")
    val priceMinor = long("price_minor")
    val currency = text("currency")
    val trialDays = integer("trial_days")
    val highlighted = bool("highlighted")
    val active = bool("active")
    val position = integer("position")
    val appStoreProductId = text("app_store_product_id").nullable()
    val googlePlayProductId = text("google_play_product_id").nullable()
    /** `subscription` or `gift` (V44). */
    val kind = text("kind")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PaymentTransactions : Table("payment_transactions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    /** Null for a consultation's payment, which buys no plan. */
    val planId = text("plan_id").references(BillingPlans.id).nullable()
    val consultationSessionId = uuid("consultation_session_id").nullable()
    val provider = text("provider")
    val amountMinor = long("amount_minor")
    val currency = text("currency")
    val state = text("state")
    val externalId = text("external_id").nullable()
    val providerCreatedAt = long("provider_created_at").nullable()
    val paidAt = timestampWithTimeZone("paid_at").nullable()
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()
    val cancelReason = integer("cancel_reason").nullable()
    val subscriptionId = uuid("subscription_id").references(Subscriptions.id).nullable()
    /** Who paid when it was not her (V44). */
    val payerId = uuid("payer_id").nullable()
    val paymentRequestId = uuid("payment_request_id").nullable()
    val refundedAt = timestampWithTimeZone("refunded_at").nullable()
    /** The legendary pet a one-off payment buys (V45). */
    val pet = text("pet").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/** Callbacks exactly as they arrived, for the argument with a provider weeks later. */
object PaymentCallbacks : Table("payment_callbacks") {
    val id = uuid("id")
    val provider = text("provider")
    val method = text("method").nullable()
    val transactionId = uuid("transaction_id").references(PaymentTransactions.id).nullable()
    val payload = text("payload")
    val outcome = text("outcome")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

/** Her requests for someone to pay (V44). */
object PaymentRequests : Table("payment_requests") {
    val id = uuid("id")
    val ownerId = uuid("owner_id").references(Users.id)
    val kind = text("kind")
    val planId = text("plan_id").nullable()
    val consultationSessionId = uuid("consultation_session_id").nullable()
    val doctorId = uuid("doctor_id").nullable()
    /** A legendary pet as a present (V45). */
    val pet = text("pet").nullable()
    val amountMinor = long("amount_minor")
    val note = text("note").nullable()
    val partnerLinkId = uuid("partner_link_id").nullable()
    val webTokenHash = text("web_token_hash").nullable()
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val remindedAt = timestampWithTimeZone("reminded_at").nullable()
    val closedAt = timestampWithTimeZone("closed_at").nullable()
    val paidBy = uuid("paid_by").nullable()
    val transactionId = uuid("transaction_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Gifted Premium days, applied or banked (V44). */
object PremiumGiftCredits : Table("premium_gift_credits") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val days = integer("days")
    val transactionId = uuid("transaction_id")
    val createdAt = timestampWithTimeZone("created_at")
    val appliedAt = timestampWithTimeZone("applied_at").nullable()
    val subscriptionId = uuid("subscription_id").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
