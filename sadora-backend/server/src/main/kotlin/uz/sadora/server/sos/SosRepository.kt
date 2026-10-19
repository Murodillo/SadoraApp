package uz.sadora.server.sos

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import uz.sadora.contract.SosContact
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.SosAlerts
import uz.sadora.server.db.SosContacts
import uz.sadora.server.db.dbQuery

/** Her SOS contacts and the alerts she sent. */
class SosRepository {

    suspend fun contacts(userId: Uuid): List<SosContact> = dbQuery {
        SosContacts.selectAll()
            .where { SosContacts.userId eq userId }
            .orderBy(SosContacts.position to SortOrder.ASC)
            .map { SosContact(it[SosContacts.name], it[SosContacts.phone]) }
    }

    suspend fun replaceContacts(userId: Uuid, contacts: List<SosContact>) = dbQuery {
        SosContacts.deleteWhere { SosContacts.userId eq userId }
        val at = now().toOffsetDateTime()
        contacts.forEachIndexed { i, contact ->
            SosContacts.insert {
                it[SosContacts.userId] = userId
                it[position] = i
                it[name] = contact.name
                it[phone] = contact.phone
                it[createdAt] = at
            }
        }
    }

    suspend fun alertsSince(userId: Uuid, since: Instant): Int = dbQuery {
        SosAlerts.selectAll()
            .where { (SosAlerts.userId eq userId) and (SosAlerts.createdAt greaterEq since.toOffsetDateTime()) }
            .count()
            .toInt()
    }

    suspend fun addAlert(userId: Uuid, latitude: Double?, longitude: Double?, sent: Int, failed: Int) = dbQuery {
        SosAlerts.insert {
            it[id] = Uuid.random()
            it[SosAlerts.userId] = userId
            it[SosAlerts.latitude] = latitude
            it[SosAlerts.longitude] = longitude
            it[smsSent] = sent
            it[smsFailed] = failed
            it[createdAt] = now().toOffsetDateTime()
        }
    }
}
