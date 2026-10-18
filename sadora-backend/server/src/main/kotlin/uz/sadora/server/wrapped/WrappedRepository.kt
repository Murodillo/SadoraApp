package uz.sadora.server.wrapped

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import uz.sadora.contract.CoinReasons
import uz.sadora.server.db.dbQuery

/** What she did in a window, counted in the database; and which recaps she has opened. */
class WrappedRepository {

    data class Counts(
        val openedOn: List<LocalDate>,
        val waterGoalDays: Int,
        val waterLitres: Int,
        val kegelSessions: Int,
        val practiceMinutes: Int,
        val journalEntries: Int,
        val mealsLogged: Int,
        val gulEarned: Int,
        val badges: List<String>,
    )

    suspend fun counts(userId: Uuid, from: LocalDate, to: LocalDate): Counts = dbQuery {
        // The id is a parsed Uuid and the dates are LocalDate: interpolating them injects nothing.
        val u = "'$userId'::uuid"
        val range = "BETWEEN '$from'::date AND '$to'::date"
        val opened = ArrayList<LocalDate>()
        exec("SELECT DISTINCT earned_on FROM coin_ledger WHERE user_id = $u AND reason = '${CoinReasons.DAILY_OPEN}' AND earned_on $range ORDER BY 1") { rows ->
            while (rows.next()) opened += LocalDate.parse(rows.getString(1))
        }
        val badges = ArrayList<String>()
        exec(
            "SELECT DISTINCT badge FROM user_badges WHERE user_id = $u AND (earned_at AT TIME ZONE 'UTC')::date $range ORDER BY 1",
        ) { rows -> while (rows.next()) badges += rows.getString(1) }
        var counts = IntArray(7)
        exec(
            """
            SELECT
              (SELECT count(DISTINCT earned_on) FROM coin_ledger WHERE user_id = $u AND reason = '${CoinReasons.WATER_GOAL}' AND earned_on $range),
              (SELECT coalesce(sum(water_ml), 0) / 1000 FROM daily_logs WHERE user_id = $u AND log_date $range),
              (SELECT count(*) FROM kegel_sessions WHERE user_id = $u AND done_on $range),
              (SELECT coalesce(sum(duration_seconds), 0) / 60 FROM mind_practices WHERE user_id = $u AND completed_at::date $range),
              (SELECT count(*) FROM journal_entries WHERE user_id = $u AND entry_date $range),
              (SELECT count(*) FROM meals WHERE user_id = $u AND log_date $range),
              (SELECT coalesce(sum(amount), 0) FROM coin_ledger WHERE user_id = $u AND amount > 0
                 AND reason <> '${CoinReasons.ADMIN_ADJUSTMENT}' AND earned_on $range)
            """.trimIndent(),
        ) { rows -> if (rows.next()) counts = IntArray(7) { rows.getLong(it + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() } }
        Counts(opened, counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6], badges)
    }

    suspend fun seen(userId: Uuid): Set<String> = dbQuery {
        val keys = HashSet<String>()
        exec("SELECT key FROM wrapped_seen WHERE user_id = '$userId'::uuid") { rows -> while (rows.next()) keys += rows.getString(1) }
        keys
    }

    /** [key] has passed [WrappedService.KEY], so it is digits, dashes and a known word. */
    suspend fun markSeen(userId: Uuid, key: String) = dbQuery {
        require(WrappedService.KEY.matches(key))
        exec("INSERT INTO wrapped_seen (user_id, key) VALUES ('$userId'::uuid, '$key') ON CONFLICT DO NOTHING")
        Unit
    }
}
