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
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PaymentTransactions : Table("payment_transactions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val planId = text("plan_id").references(BillingPlans.id)
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
