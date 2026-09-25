package uz.sadora.app.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.sadora.contract.PaymentProvider

/**
 * Google Play Billing, for the build Play installed.
 *
 * Play's purchase sheet needs the Activity on screen, which the graph outlives; the
 * activity hands itself over in [attach] each time it resumes, and only a weak reference
 * is kept so a finished one can go.
 */
class AndroidStoreBilling(context: Context) : StoreBilling {

    override val provider: PaymentProvider = PaymentProvider.GOOGLE_PLAY

    private var activity: WeakReference<Activity> = WeakReference(null)
    private val updates = MutableSharedFlow<Pair<BillingResult, List<Purchase>?>>(extraBufferCapacity = 1)
    private val connecting = Mutex()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> updates.tryEmit(result to purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    override suspend fun prices(productIds: List<String>): Map<String, String> {
        if (!connect()) return emptyMap()
        return details(productIds).associate { it.productId to (it.baseOffer()?.priceText() ?: "") }
            .filterValues { it.isNotEmpty() }
    }

    override suspend fun purchase(productId: String, accountId: String): StoreOutcome {
        if (!connect()) return StoreOutcome.Failed("Google Play is not available")
        val details = details(listOf(productId)).firstOrNull()
            ?: return StoreOutcome.Failed("Play does not sell $productId")
        val offer = details.baseOffer() ?: return StoreOutcome.Failed("No base plan for $productId")
        val activity = activity.get() ?: return StoreOutcome.Failed("No screen to show the sheet on")

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            // The server checks this against the account posting the receipt.
            .setObfuscatedAccountId(accountId)
            .build()

        val launched = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, params) }
        when (launched.responseCode) {
            BillingClient.BillingResponseCode.OK -> Unit
            BillingClient.BillingResponseCode.USER_CANCELED -> return StoreOutcome.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> return ownedOutcome(productId)
            else -> return StoreOutcome.Failed(launched.debugMessage)
        }

        val (result, purchases) = updates.first()
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases.orEmpty().firstOrNull { productId in it.products }
                    ?: return StoreOutcome.Failed("Play returned no purchase")
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> StoreOutcome.Purchased(purchase.toReceipt(productId))
                    Purchase.PurchaseState.PENDING -> StoreOutcome.Pending
                    else -> StoreOutcome.Failed("Unknown purchase state")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> StoreOutcome.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> ownedOutcome(productId)
            else -> StoreOutcome.Failed(result.debugMessage)
        }
    }

    override suspend fun owned(): List<StoreReceipt> {
        if (!connect()) return emptyList()
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        return result.purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { purchase -> purchase.products.map { purchase.toReceipt(it) } }
    }

    override suspend fun finish(receipt: StoreReceipt) {
        if (!receipt.needsFinish || !connect()) return
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(receipt.token).build())
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun ownedOutcome(productId: String): StoreOutcome =
        owned().firstOrNull { it.productId == productId }?.let { StoreOutcome.Purchased(it) }
            ?: StoreOutcome.Failed("Play says it is owned but lists no purchase")

    private suspend fun details(productIds: List<String>): List<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        return client.queryProductDetails(params).productDetailsList.orEmpty()
    }

    /** The base plan — the offer with no offer id — rather than an introductory one. */
    private fun ProductDetails.baseOffer(): ProductDetails.SubscriptionOfferDetails? =
        subscriptionOfferDetails?.let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() }

    /** The recurring price: the last pricing phase, after any trial or intro phases. */
    private fun ProductDetails.SubscriptionOfferDetails.priceText(): String? =
        pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice

    private fun Purchase.toReceipt(productId: String) =
        StoreReceipt(productId = productId, token = purchaseToken, needsFinish = !isAcknowledged)

    private suspend fun connect(): Boolean = connecting.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }
}
