package uz.sadora.server.billing

import java.security.MessageDigest
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.server.config.ClickConfig

/**
 * Click's merchant callbacks: Prepare, then Complete.
 *
 * Click posts form fields and signs them with an MD5 of a fixed concatenation. The
 * signature is the only thing standing between this endpoint and anyone who knows an
 * order id, so it is checked first, compared in constant time, and a failure answers
 * with their `-1` rather than a stack trace.
 *
 * Amounts arrive in so'm with decimals; everything stored here is tiyin, and the
 * comparison happens in tiyin so "39900.00" and "39900" are the same payment.
 */
class ClickGateway(
    private val repository: BillingRepository,
    private val billing: BillingService,
    private val config: ClickConfig,
) {

    @Serializable
    data class Request(
        val clickTransId: String,
        val serviceId: String,
        val clickPaydocId: String? = null,
        val merchantTransId: String,
        val merchantPrepareId: String? = null,
        val amount: String,
        val action: Int,
        val error: Int = 0,
        val signTime: String,
        val signString: String,
    )

    @Serializable
    data class Response(
        val click_trans_id: String? = null,
        val merchant_trans_id: String? = null,
        val merchant_prepare_id: String? = null,
        val merchant_confirm_id: String? = null,
        val error: Int,
        val error_note: String,
    )

    suspend fun prepare(request: Request): Response {
        val failure = verify(request, prepareStage = true)
        if (failure != null) return failure

        val transaction = transactionFor(request) ?: return error(request, ORDER_NOT_FOUND, "Order not found")
        if (transaction.state != PaymentState.PENDING) {
            return error(request, ALREADY_PAID, "Order is not pending")
        }
        if (!ClickProtocol.amountMatches(request.amount, transaction.amountMinor)) {
            return error(request, INCORRECT_AMOUNT, "Incorrect amount")
        }
        if (!repository.attachExternalId(transaction.id, request.clickTransId, null)) {
            return error(request, ALREADY_PAID, "Order already has a transaction")
        }

        return Response(
            click_trans_id = request.clickTransId,
            merchant_trans_id = transaction.id.toString(),
            // Click echoes this back on Complete; the transaction id is the natural one.
            merchant_prepare_id = transaction.id.toString(),
            error = SUCCESS,
            error_note = "Success",
        )
    }

    suspend fun complete(request: Request): Response {
        val failure = verify(request, prepareStage = false)
        if (failure != null) return failure

        val transaction = repository.byExternalId(PaymentProvider.CLICK, request.clickTransId)
            ?: transactionFor(request)
            ?: return error(request, ORDER_NOT_FOUND, "Order not found")

        // Click reports its own failure by sending a negative error with Complete; the
        // order goes back to being unpaid rather than being granted.
        if (request.error < 0) {
            if (transaction.state == PaymentState.PENDING) {
                repository.markCancelled(transaction.id, request.error, PaymentState.FAILED)
            }
            return error(request, TRANSACTION_CANCELLED, "Transaction cancelled")
        }

        if (!ClickProtocol.amountMatches(request.amount, transaction.amountMinor)) {
            return error(request, INCORRECT_AMOUNT, "Incorrect amount")
        }

        // Repeated Completes answer from the row: the subscription is granted once.
        if (transaction.state == PaymentState.PENDING) billing.activate(transaction)

        return Response(
            click_trans_id = request.clickTransId,
            merchant_trans_id = transaction.id.toString(),
            merchant_confirm_id = transaction.id.toString(),
            error = SUCCESS,
            error_note = "Success",
        )
    }

    // ---------------------------------------------------------------- signature

    private fun verify(request: Request, prepareStage: Boolean): Response? {
        val expected = ClickProtocol.expectedSignature(request, config.secretKey, prepareStage)
            ?: return error(request, SIGN_CHECK_FAILED, "Merchant is not configured")
        if (!constantTimeEquals(expected, request.signString.lowercase())) {
            return error(request, SIGN_CHECK_FAILED, "Signature check failed")
        }
        if (config.serviceId != null && request.serviceId != config.serviceId) {
            return error(request, SIGN_CHECK_FAILED, "Unknown service")
        }
        return null
    }

    private suspend fun transactionFor(request: Request): TransactionRecord? {
        val id = runCatching { Uuid.parse(request.merchantTransId) }.getOrNull() ?: return null
        return repository.transaction(id)
    }

    private fun error(request: Request, code: Int, note: String) = Response(
        click_trans_id = request.clickTransId,
        merchant_trans_id = request.merchantTransId,
        error = code,
        error_note = note,
    )

    companion object {
        // Click's own result codes.
        const val SUCCESS = 0
        const val SIGN_CHECK_FAILED = -1
        const val INCORRECT_AMOUNT = -2
        const val ALREADY_PAID = -4
        const val ORDER_NOT_FOUND = -5
        const val TRANSACTION_CANCELLED = -9
    }
}

/**
 * The parts of Click's protocol that are pure arithmetic and string handling.
 *
 * Separated from the gateway so they can be tested without a database or a billing
 * service behind them: a signature and an amount comparison are exactly the places where
 * a quiet mistake charges the wrong sum or lets a stranger mark an order paid.
 */
object ClickProtocol {

    /**
     * `md5(click_trans_id + service_id + secret + merchant_trans_id [+ merchant_prepare_id]
     * + amount + action + sign_time)` — the prepare stage omits the prepare id, which is
     * the only difference between the two.
     */
    fun expectedSignature(
        request: ClickGateway.Request,
        secret: String?,
        prepareStage: Boolean,
    ): String? {
        if (secret == null) return null
        val middle = if (prepareStage) "" else request.merchantPrepareId.orEmpty()
        return md5(
            request.clickTransId +
                request.serviceId +
                secret +
                request.merchantTransId +
                middle +
                request.amount +
                request.action +
                request.signTime,
        )
    }

    /** "39900.00" and "39900" are the same payment; both become 3 990 000 tiyin. */
    fun amountMatches(amount: String, expectedMinor: Long): Boolean {
        val parsed = amount.trim().toBigDecimalOrNull() ?: return false
        val minor = parsed.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        return minor == expectedMinor
    }
}

private fun md5(value: String): String =
    MessageDigest.getInstance("MD5")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

/**
 * Compared without an early exit. The comparison is over a hash of a shared secret, and
 * a length-dependent early return leaks how much of it was right.
 */
private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var difference = 0
    for (index in a.indices) difference = difference or (a[index].code xor b[index].code)
    return difference == 0
}

private fun String.toBigDecimalOrNull(): java.math.BigDecimal? =
    runCatching { java.math.BigDecimal(this) }.getOrNull()
