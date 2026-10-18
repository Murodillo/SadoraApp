package uz.sadora.server.baby

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.Baby
import uz.sadora.contract.BabySex
import uz.sadora.contract.SaveBabyRequest
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.Babies
import uz.sadora.server.db.BabyGrowth
import uz.sadora.server.db.BabyMilestonesTable
import uz.sadora.server.db.BabyVaccinations
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class BabyRecord(val userId: Uuid, val baby: Baby) {
    val id: Uuid get() = Uuid.parse(baby.id)
}

data class GrowthRecord(
    val id: Uuid,
    val measuredOn: LocalDate,
    val weightG: Int?,
    val lengthCm: Double?,
    val headCm: Double?,
    val note: String?,
)

data class MilestoneRecord(val code: String, val achievedOn: LocalDate, val note: String?, val hasPhoto: Boolean)

/**
 * Babies and what is kept about them. Every read a user can reach goes through [byId]
 * with her id, so another woman's baby is simply not found.
 */
class BabyRepository {

    suspend fun list(userId: Uuid): List<BabyRecord> = dbQuery {
        Babies.selectAll()
            .where { Babies.userId eq userId }
            .orderBy(Babies.birthDate to SortOrder.DESC, Babies.createdAt to SortOrder.ASC)
            .map { it.toBaby() }
    }

    suspend fun count(userId: Uuid): Long = dbQuery {
        Babies.selectAll().where { Babies.userId eq userId }.count()
    }

    suspend fun byId(userId: Uuid, id: Uuid): BabyRecord? = dbQuery {
        Babies.selectAll()
            .where { (Babies.id eq id) and (Babies.userId eq userId) }
            .singleOrNull()
            ?.toBaby()
    }

    /** Every baby born since [bornSince], with its mother: the vaccine reminder pass. */
    suspend fun bornSince(bornSince: LocalDate): List<BabyRecord> = dbQuery {
        Babies.selectAll()
            .where { Babies.birthDate greaterEq bornSince }
            .map { it.toBaby() }
    }

    suspend fun insert(userId: Uuid, request: SaveBabyRequest): Uuid = dbQuery {
        val id = Uuid.random()
        val at = now().toOffsetDateTime()
        Babies.insert {
            it[Babies.id] = id
            it[Babies.userId] = userId
            it[name] = request.name.trim()
            it[sex] = request.sex.dbValue()
            it[birthDate] = request.birthDate
            it[birthWeightG] = request.birthWeightG
            it[birthLengthCm] = request.birthLengthCm
            it[createdAt] = at
            it[updatedAt] = at
        }
        id
    }

    suspend fun update(userId: Uuid, id: Uuid, request: SaveBabyRequest): Boolean = dbQuery {
        Babies.update({ (Babies.id eq id) and (Babies.userId eq userId) }) {
            it[name] = request.name.trim()
            it[sex] = request.sex.dbValue()
            it[birthDate] = request.birthDate
            it[birthWeightG] = request.birthWeightG
            it[birthLengthCm] = request.birthLengthCm
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
    }

    /** Everything about the baby goes with it: events, growth, vaccines, milestones (CASCADE). */
    suspend fun delete(userId: Uuid, id: Uuid): Boolean = dbQuery {
        Babies.deleteWhere { (Babies.id eq id) and (Babies.userId eq userId) } > 0
    }

    // ---------------------------------------------------------------- growth

    suspend fun growth(babyId: Uuid): List<GrowthRecord> = dbQuery {
        BabyGrowth.selectAll()
            .where { BabyGrowth.babyId eq babyId }
            .orderBy(BabyGrowth.measuredOn to SortOrder.ASC, BabyGrowth.createdAt to SortOrder.ASC)
            .map {
                GrowthRecord(
                    it[BabyGrowth.id], it[BabyGrowth.measuredOn], it[BabyGrowth.weightG],
                    it[BabyGrowth.lengthCm], it[BabyGrowth.headCm], it[BabyGrowth.note],
                )
            }
    }

    suspend fun addGrowth(babyId: Uuid, record: GrowthRecord) = dbQuery {
        BabyGrowth.insert {
            it[id] = record.id
            it[BabyGrowth.babyId] = babyId
            it[measuredOn] = record.measuredOn
            it[weightG] = record.weightG
            it[lengthCm] = record.lengthCm
            it[headCm] = record.headCm
            it[note] = record.note
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    suspend fun updateGrowth(babyId: Uuid, record: GrowthRecord): Boolean = dbQuery {
        BabyGrowth.update({ (BabyGrowth.id eq record.id) and (BabyGrowth.babyId eq babyId) }) {
            it[measuredOn] = record.measuredOn
            it[weightG] = record.weightG
            it[lengthCm] = record.lengthCm
            it[headCm] = record.headCm
            it[note] = record.note
        } > 0
    }

    suspend fun deleteGrowth(babyId: Uuid, id: Uuid): Boolean = dbQuery {
        BabyGrowth.deleteWhere { (BabyGrowth.id eq id) and (BabyGrowth.babyId eq babyId) } > 0
    }

    // ---------------------------------------------------------------- vaccines

    suspend fun vaccinations(babyId: Uuid): Map<String, LocalDate> = dbQuery {
        BabyVaccinations.selectAll()
            .where { BabyVaccinations.babyId eq babyId }
            .associate { it[BabyVaccinations.code] to it[BabyVaccinations.givenOn] }
    }

    suspend fun vaccinationsOf(babyIds: Collection<Uuid>): Map<Uuid, Map<String, LocalDate>> = dbQuery {
        if (babyIds.isEmpty()) return@dbQuery emptyMap()
        BabyVaccinations.selectAll()
            .where { BabyVaccinations.babyId inList babyIds }
            .groupBy({ it[BabyVaccinations.babyId] }, { it[BabyVaccinations.code] to it[BabyVaccinations.givenOn] })
            .mapValues { (_, pairs) -> pairs.toMap() }
    }

    suspend fun markVaccine(babyId: Uuid, code: String, givenOn: LocalDate) = dbQuery {
        BabyVaccinations.upsert {
            it[BabyVaccinations.babyId] = babyId
            it[BabyVaccinations.code] = code
            it[BabyVaccinations.givenOn] = givenOn
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    suspend fun unmarkVaccine(babyId: Uuid, code: String): Boolean = dbQuery {
        BabyVaccinations.deleteWhere { (BabyVaccinations.babyId eq babyId) and (BabyVaccinations.code eq code) } > 0
    }

    // ---------------------------------------------------------------- milestones

    suspend fun milestones(babyId: Uuid): Map<String, MilestoneRecord> = dbQuery {
        BabyMilestonesTable
            .select(
                BabyMilestonesTable.code, BabyMilestonesTable.achievedOn, BabyMilestonesTable.note,
                BabyMilestonesTable.photoMime,
            )
            .where { BabyMilestonesTable.babyId eq babyId }
            .associate {
                it[BabyMilestonesTable.code] to MilestoneRecord(
                    it[BabyMilestonesTable.code],
                    it[BabyMilestonesTable.achievedOn],
                    it[BabyMilestonesTable.note],
                    it[BabyMilestonesTable.photoMime] != null,
                )
            }
    }

    /** Marks one reached. A new photo replaces the old; no photo keeps the old one. */
    suspend fun markMilestone(babyId: Uuid, code: String, achievedOn: LocalDate, note: String?, photo: Pair<String, ByteArray>?) = dbQuery {
        val existing = BabyMilestonesTable.selectAll()
            .where { (BabyMilestonesTable.babyId eq babyId) and (BabyMilestonesTable.code eq code) }
            .singleOrNull()
        if (existing == null) {
            BabyMilestonesTable.insert {
                it[BabyMilestonesTable.babyId] = babyId
                it[BabyMilestonesTable.code] = code
                it[BabyMilestonesTable.achievedOn] = achievedOn
                it[BabyMilestonesTable.note] = note
                it[photoMime] = photo?.first
                it[BabyMilestonesTable.photo] = photo?.second
                it[createdAt] = now().toOffsetDateTime()
            }
        } else {
            BabyMilestonesTable.update({ (BabyMilestonesTable.babyId eq babyId) and (BabyMilestonesTable.code eq code) }) {
                it[BabyMilestonesTable.achievedOn] = achievedOn
                it[BabyMilestonesTable.note] = note
                if (photo != null) {
                    it[photoMime] = photo.first
                    it[BabyMilestonesTable.photo] = photo.second
                }
            }
        }
    }

    suspend fun unmarkMilestone(babyId: Uuid, code: String): Boolean = dbQuery {
        BabyMilestonesTable.deleteWhere { (BabyMilestonesTable.babyId eq babyId) and (BabyMilestonesTable.code eq code) } > 0
    }

    suspend fun milestonePhoto(babyId: Uuid, code: String): Pair<String, ByteArray>? = dbQuery {
        BabyMilestonesTable.select(BabyMilestonesTable.photoMime, BabyMilestonesTable.photo)
            .where {
                (BabyMilestonesTable.babyId eq babyId) and (BabyMilestonesTable.code eq code) and
                    BabyMilestonesTable.photo.isNotNull()
            }
            .singleOrNull()
            ?.let { row -> row[BabyMilestonesTable.photoMime]!! to row[BabyMilestonesTable.photo]!! }
    }

    private fun ResultRow.toBaby() = BabyRecord(
        userId = this[Babies.userId],
        baby = Baby(
            id = this[Babies.id].toString(),
            name = this[Babies.name],
            sex = enumFromDb(this[Babies.sex], BabySex.GIRL),
            birthDate = this[Babies.birthDate],
            birthWeightG = this[Babies.birthWeightG],
            birthLengthCm = this[Babies.birthLengthCm],
            createdAt = this[Babies.createdAt].toKotlinInstant(),
        ),
    )
}
