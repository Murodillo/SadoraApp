package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** One Kegel session (V60). */
object KegelSessions : Table("kegel_sessions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val program = text("program")
    val level = integer("level")
    val doneOn = date("done_on")
    val reps = integer("reps")
    val durationSeconds = integer("duration_seconds")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

/** Her reminders ("HH:MM,HH:MM") and the program she picked; a null program follows her stage. */
object KegelSettings : Table("kegel_settings") {
    val userId = uuid("user_id").references(Users.id)
    val program = text("program").nullable()
    val reminders = text("reminders")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

/** The four-weekly leak check. Situations are comma-separated wire words. */
object KegelChecks : Table("kegel_checks") {
    val userId = uuid("user_id").references(Users.id)
    val takenOn = date("taken_on")
    val frequency = integer("frequency")
    val amount = integer("amount")
    val situations = text("situations")
    val bother = integer("bother")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId, takenOn)
}
