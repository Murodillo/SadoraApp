package uz.sadora.server.billing

import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.server.config.PaymeConfig
import uz.sadora.server.core.now

/**
 * Payme's Merchant API.
 *
 * It is JSON-RPC over one endpoint, and it is deliberately not RESTful: Payme calls *us*,
 * repeatedly, and expects the same answer every time for the same question. So each
 * method here is written to be idempotent and to answer from stored state rather than
 * from what happened during this request.
 *
 * Errors are theirs, not ours: the codes below are from their specification and the app's
 * own error envelope never appears on this endpoint. A wrong code is not cosmetic —
 * Payme's retry behaviour and its operator screens are driven by it.
 */
class PaymeGateway(
    private val repository: BillingRepository,
    private val billing: BillingService,
    private val config: PaymeConfig,
) {

    /** Payme authenticates with Basic `Paycom:<key>` on every call. */
    fun authorizes(header: String?): Boolean {
        val key = config.key ?: return false
        val encoded = header?.removePrefix("Basic ")?.trim() ?: return false
        val decoded = runCatching {
            java.util.Base64.getDecoder().decode(encoded).decodeToString()
        }.getOrNull() ?: return false
        val (login, secret) = decoded.split(":", limit = 2).takeIf { it.size == 2 } ?: return false
        return login == config.login && secret == key
    }

    suspend fun handle(request: JsonObject): JsonObject {
        val id = request["id"] ?: JsonPrimitive(0)
        val method = request["method"]?.jsonPrimitive?.content
        val params = request["params"]?.jsonObject ?: JsonObject(emptyMap())

        return try {
            val result = when (method) {
                "CheckPerformTransaction" -> checkPerform(params)
                "CreateTransaction" -> createTransaction(params)
                "PerformTransaction" -> performTransaction(params)
                "CancelTransaction" -> cancelTransaction(params)
                "CheckTransaction" -> checkTransaction(params)
                "GetStatement" -> getStatement(params)
                else -> throw PaymeError(METHOD_NOT_FOUND, "Method not found")
            }
            buildJsonObject {
                put("id", id)
                put("result", result)
            }
        } catch (error: PaymeError) {
            buildJsonObject {
                put("id", id)
                put(
                    "error",
                    buildJsonObject {
                        put("code", error.code)
                        put("message", error.message ?: "")
                        error.data?.let { put("data", it) }
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------------- methods

    private suspend fun checkPerform(params: JsonObject): JsonObject {
        val transaction = transactionFrom(params)
        requireAmount(params, transaction)
        if (transaction.state != PaymentState.PENDING) {
            throw PaymeError(CANNOT_PERFORM, "Transaction is not pending")
        }
        return buildJsonObject { put("allow", true) }
    }

    private suspend fun createTransaction(params: JsonObject): JsonObject {
        val paymeId = params.string("id") ?: throw PaymeError(CANNOT_PERFORM, "id required")
        val time = params.long("time")

        // Asked twice with the same id — and Payme will ask twice — this must describe
        // the transaction it already created, not make another one.
        repository.byExternalId(PaymentProvider.PAYME, paymeId)?.let { existing ->
            if (existing.state != PaymentState.PENDING) {
                throw PaymeError(CANNOT_PERFORM, "Transaction is not pending")
            }
            return existing.asState(TRANSACTION_CREATED)
        }

        val transaction = transactionFrom(params)
        requireAmount(params, transaction)
        if (transaction.state != PaymentState.PENDING) {
            throw PaymeError(CANNOT_PERFORM, "Transaction is not pending")
        }
        // A second Payme transaction against an order that already has one is refused
        // here rather than quietly replacing it.
        if (!repository.attachExternalId(transaction.id, paymeId, time)) {
            throw PaymeError(CANNOT_PERFORM, "Order already has a transaction")
        }
        return transaction.asState(TRANSACTION_CREATED, providerTime = time)
    }

    private suspend fun performTransaction(params: JsonObject): JsonObject {
        val paymeId = params.string("id") ?: throw PaymeError(TRANSACTION_NOT_FOUND, "Transaction not found")
        val transaction = repository.byExternalId(PaymentProvider.PAYME, paymeId)
            ?: throw PaymeError(TRANSACTION_NOT_FOUND, "Transaction not found")

        return when (transaction.state) {
            // The first delivery grants the subscription; every later one answers from
            // the row and grants nothing.
            PaymentState.PENDING -> {
                billing.activate(transaction)
                val updated = repository.transaction(transaction.id) ?: transaction
                updated.asState(TRANSACTION_PERFORMED)
            }
            PaymentState.PAID -> transaction.asState(TRANSACTION_PERFORMED)
            else -> throw PaymeError(CANNOT_PERFORM, "Transaction is cancelled")
        }
    }

    private suspend fun cancelTransaction(params: JsonObject): JsonObject {
        val paymeId = params.string("id") ?: throw PaymeError(TRANSACTION_NOT_FOUND, "Transaction not found")
        val reason = params.long("reason")?.toInt()
        val transaction = repository.byExternalId(PaymentProvider.PAYME, paymeId)
            ?: throw PaymeError(TRANSACTION_NOT_FOUND, "Transaction not found")

        // A paid subscription is not withdrawn by a cancel callback: refunds are an
        // operator decision, and quietly revoking access on a provider's message is how
        // someone loses what she paid for.
        if (transaction.state == PaymentState.PENDING) {
            repository.markCancelled(transaction.id, reason)
        }
        val updated = repository.transaction(transaction.id) ?: transaction
        return updated.asState(if (updated.state == PaymentState.PAID) TRANSACTION_PERFORMED else TRANSACTION_CANCELLED)
    }

    private suspend fun checkTransaction(params: JsonObject): JsonObject {
        val paymeId = params.string("id") ?: throw PaymeError(TRANSACTION_NOT_FOUND, "Transaction not found")
        val transaction = repository.byExternalId(PaymentProvider.PAYME, paymeId)
            ?: throw PaymeError(TRANSACTION_NOT_FOUND, "Transaction not found")
        return transaction.asState(transaction.paymeState(), full = true)
    }

    private suspend fun getStatement(params: JsonObject): JsonObject {
        val from = params.long("from") ?: 0
        val to = params.long("to") ?: Long.MAX_VALUE
        val rows = repository.recent(limit = 1000)
            .filter { it.provider == PaymentProvider.PAYME && it.externalId != null }
            .filter { (it.providerCreatedAt ?: it.createdAt.toEpochMilliseconds()) in from..to }
        return buildJsonObject {
            put(
                "transactions",
                kotlinx.serialization.json.JsonArray(
                    rows.map { row ->
                        buildJsonObject {
                            put("id", row.externalId)
                            put("time", row.providerCreatedAt ?: row.createdAt.toEpochMilliseconds())
                            put("amount", row.amountMinor)
                            put("account", buildJsonObject { put(config.accountField, row.id.toString()) })
                            put("create_time", row.createdAt.toEpochMilliseconds())
                            put("perform_time", row.paidAt?.toEpochMilliseconds() ?: 0)
                            put("cancel_time", row.cancelledAt?.toEpochMilliseconds() ?: 0)
                            put("transaction", row.id.toString())
                            put("state", row.paymeState())
                            put("reason", row.cancelReason)
                        }
                    },
                ),
            )
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun transactionFrom(params: JsonObject): TransactionRecord {
        val account = params["account"]?.jsonObject
            ?: throw PaymeError(ACCOUNT_INVALID, "Account required", config.accountField)
        val raw = account[config.accountField]?.jsonPrimitive?.content
            ?: throw PaymeError(ACCOUNT_INVALID, "Order id required", config.accountField)
        val id = runCatching { Uuid.parse(raw) }.getOrNull()
            ?: throw PaymeError(ACCOUNT_INVALID, "Order not found", config.accountField)
        return repository.transaction(id)
            ?: throw PaymeError(ACCOUNT_INVALID, "Order not found", config.accountField)
    }

    /** The amount is checked to the tiyin: a mismatch is theirs to explain, not ours to accept. */
    private fun requireAmount(params: JsonObject, transaction: TransactionRecord) {
        val amount = params.long("amount") ?: throw PaymeError(AMOUNT_INVALID, "Amount required")
        if (amount != transaction.amountMinor) throw PaymeError(AMOUNT_INVALID, "Wrong amount")
    }

    private fun TransactionRecord.asState(
        state: Int,
        providerTime: Long? = null,
        full: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("create_time", providerTime ?: providerCreatedAt ?: createdAt.toEpochMilliseconds())
        put("transaction", id.toString())
        put("state", state)
        if (state == TRANSACTION_PERFORMED || full) {
            put("perform_time", paidAt?.toEpochMilliseconds() ?: 0)
        }
        if (state == TRANSACTION_CANCELLED || full) {
            put("cancel_time", cancelledAt?.toEpochMilliseconds() ?: 0)
            put("reason", cancelReason)
        }
    }

    private fun TransactionRecord.paymeState(): Int = when (state) {
        PaymentState.PENDING -> TRANSACTION_CREATED
        PaymentState.PAID -> TRANSACTION_PERFORMED
        PaymentState.CANCELLED, PaymentState.FAILED -> TRANSACTION_CANCELLED
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.content?.toLongOrNull()

    companion object {
        // Payme's own transaction states.
        const val TRANSACTION_CREATED = 1
        const val TRANSACTION_PERFORMED = 2
        const val TRANSACTION_CANCELLED = -1

        // Payme's own error codes. These are theirs; do not renumber them.
        const val TRANSPORT_ERROR = -32300
        const val METHOD_NOT_FOUND = -32601
        const val UNAUTHORIZED = -32504
        const val CANNOT_PERFORM = -31008
        const val TRANSACTION_NOT_FOUND = -31003
        const val AMOUNT_INVALID = -31001
        const val ACCOUNT_INVALID = -31050
    }
}

/** A Payme-shaped failure: their code, and the field name when the account is wrong. */
class PaymeError(val code: Int, message: String, field: String? = null) : Exception(message) {
    /** Payme puts the offending account field name here, and its operator screens show it. */
    val data: JsonElement? = field?.let { JsonPrimitive(it) }
}

/** The unauthenticated answer, in their envelope rather than the app's. */
fun paymeUnauthorized(): JsonObject = buildJsonObject {
    put("id", 0)
    put(
        "error",
        buildJsonObject {
            put("code", PaymeGateway.UNAUTHORIZED)
            put("message", "Insufficient privileges")
        },
    )
}
