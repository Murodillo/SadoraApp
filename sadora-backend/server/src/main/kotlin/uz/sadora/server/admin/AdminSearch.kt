package uz.sadora.server.admin

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.contract.SubscriptionTier
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.db.DoctorProfiles
import uz.sadora.server.db.PaymentTransactions
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.escapeLike
import uz.sadora.server.entitlement.EntitlementRepository
import uz.sadora.server.user.UserRepository

@Serializable
data class AdminSearchResults(
    val users: List<AdminUserSummary> = emptyList(),
    val doctors: List<AdminSearchDoctor> = emptyList(),
    val payments: List<AdminSearchPayment> = emptyList(),
)

@Serializable
data class AdminSearchDoctor(val id: String, val fullName: String, val specialty: String, val status: String)

@Serializable
data class AdminSearchPayment(
    val id: String,
    val userId: String,
    val payerId: String? = null,
    val provider: String,
    val amountMinor: Long,
    val state: String,
    val createdAt: Instant,
)

/**
 * The panel's one search box (Ctrl+K): an account by phone, email, name or id; a doctor
 * by name, licence or id; a payment by its id or the provider's. The same lookups the
 * list pages already offer, a few rows each, so an operator lands on the card in one step.
 */
class AdminSearchService(
    private val users: UserRepository,
    private val entitlements: EntitlementRepository,
) {

    suspend fun search(raw: String): AdminSearchResults {
        val term = raw.trim().take(64)
        if (term.length < MIN_LENGTH) return AdminSearchResults()
        val id = runCatching { Uuid.parse(term) }.getOrNull()
        // "90 123 45 67" and "+998-90-123" find the stored "+998901234567".
        val phoneDigits = term.filter { it.isDigit() }.takeIf { term.all { c -> c.isDigit() || c in PHONE_PUNCTUATION } }

        val accounts = buildList {
            if (id != null) users.findById(id)?.let { add(it) }
            val query = phoneDigits?.takeIf { it.length >= MIN_LENGTH } ?: term
            addAll(users.list(UserRepository.UserFilter(query = query), LIMIT, 0L).first)
        }.distinctBy { it.id }.take(LIMIT)

        return AdminSearchResults(
            users = accounts.map { it.toSummary(entitlements.activeSubscription(it.id)?.tier ?: SubscriptionTier.FREE) },
            doctors = doctors(term, id),
            payments = payments(term, id),
        )
    }

    private suspend fun doctors(term: String, id: Uuid?): List<AdminSearchDoctor> = dbQuery {
        val pattern = "%${term.lowercase().escapeLike()}%"
        DoctorProfiles.selectAll()
            .where {
                val byText = (DoctorProfiles.fullName.lowerCase() like pattern) or (DoctorProfiles.licenseNumber.lowerCase() like pattern)
                if (id != null) byText or (DoctorProfiles.id eq id) else byText
            }
            .orderBy(DoctorProfiles.submittedAt to SortOrder.DESC)
            .limit(LIMIT)
            .map {
                AdminSearchDoctor(
                    id = it[DoctorProfiles.id].toString(),
                    fullName = it[DoctorProfiles.fullName],
                    specialty = it[DoctorProfiles.specialty],
                    status = it[DoctorProfiles.status],
                )
            }
    }

    /** Exact only: a payment id is copied from a provider's cabinet or a receipt, never typed in part. */
    private suspend fun payments(term: String, id: Uuid?): List<AdminSearchPayment> = dbQuery {
        PaymentTransactions.selectAll()
            .where { if (id != null) (PaymentTransactions.id eq id) or (PaymentTransactions.externalId eq term) else PaymentTransactions.externalId eq term }
            .orderBy(PaymentTransactions.createdAt to SortOrder.DESC)
            .limit(LIMIT)
            .map {
                AdminSearchPayment(
                    id = it[PaymentTransactions.id].toString(),
                    userId = it[PaymentTransactions.userId].toString(),
                    payerId = it[PaymentTransactions.payerId]?.toString(),
                    provider = it[PaymentTransactions.provider],
                    amountMinor = it[PaymentTransactions.amountMinor],
                    state = it[PaymentTransactions.state],
                    createdAt = it[PaymentTransactions.createdAt].toKotlinInstant(),
                )
            }
    }

    private companion object {
        const val MIN_LENGTH = 2
        const val LIMIT = 6
        const val PHONE_PUNCTUATION = " +-()"
    }
}
