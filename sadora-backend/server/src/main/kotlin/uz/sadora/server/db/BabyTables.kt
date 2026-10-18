package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** Her babies (V57). Health data: like [HealthTables] it stays out of the admin's imports. */
object Babies : Table("babies") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val name = text("name")
    val sex = text("sex")
    val birthDate = date("birth_date")
    val birthWeightG = integer("birth_weight_g").nullable()
    val birthLengthCm = double("birth_length_cm").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object BabyGrowth : Table("baby_growth") {
    val id = uuid("id")
    val babyId = uuid("baby_id").references(Babies.id)
    val measuredOn = date("measured_on")
    val weightG = integer("weight_g").nullable()
    val lengthCm = double("length_cm").nullable()
    val headCm = double("head_cm").nullable()
    val note = text("note").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

/** A dose of the national calendar marked given; the calendar itself is code. */
object BabyVaccinations : Table("baby_vaccinations") {
    val babyId = uuid("baby_id").references(Babies.id)
    val code = text("code")
    val givenOn = date("given_on")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(babyId, code)
}

object BabyMilestonesTable : Table("baby_milestones") {
    val babyId = uuid("baby_id").references(Babies.id)
    val code = text("code")
    val achievedOn = date("achieved_on")
    val note = text("note").nullable()
    val photoMime = text("photo_mime").nullable()
    val photo = binary("photo").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(babyId, code)
}
