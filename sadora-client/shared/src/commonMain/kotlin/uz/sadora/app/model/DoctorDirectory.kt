package uz.sadora.app.model

import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.Limits

// The doctors directory as rules rather than as a screen: which doctors a filter keeps
// and in what order a sort puts them. Plain functions over the server's list, so the
// orders are pinned by tests — the server's own order is "recommended" and is never
// re-derived here, only kept.

/** How the directory is ordered. [Recommended] is the server's order, untouched. */
enum class DoctorSort { Recommended, Rating, Price, FastReply }

/** What the chips above the directory keep. Everything off keeps every doctor. */
data class DoctorFilter(
    val specialty: DoctorSpecialty? = null,
    val onlineOnly: Boolean = false,
    val freeOnly: Boolean = false,
) {
    val isActive: Boolean get() = specialty != null || onlineOnly || freeOnly
}

/**
 * Whether enough patients have rated her for the stars to mean something. Under
 * [Limits.DOCTOR_RATING_MIN] she reads "Yangi shifokor" — one early 5 or 1 would
 * otherwise speak for her whole practice — and the rating sort puts her last.
 */
fun hasEnoughRatings(ratingCount: Int): Boolean = ratingCount >= Limits.DOCTOR_RATING_MIN

/** The specialties the list actually has, in the enum's order, so no chip leads nowhere. */
fun specialtiesIn(doctors: List<DoctorListItem>): List<DoctorSpecialty> =
    DoctorSpecialty.entries.filter { specialty -> doctors.any { it.specialty == specialty } }

/**
 * The directory as the screen draws it: [filter] applied, then [sort].
 *
 * Every sort is stable, so doctors that tie keep the server's recommended order
 * between them rather than an arbitrary one.
 */
fun arrangeDoctors(
    doctors: List<DoctorListItem>,
    filter: DoctorFilter,
    sort: DoctorSort,
): List<DoctorListItem> {
    val kept = doctors.filter { doctor ->
        (filter.specialty == null || doctor.specialty == filter.specialty) &&
            (!filter.onlineOnly || doctor.onlineNow) &&
            (!filter.freeOnly || doctor.priceMinor <= 0)
    }
    return when (sort) {
        DoctorSort.Recommended -> kept
        DoctorSort.Rating -> kept.sortedWith(ByRating)
        DoctorSort.Price -> kept.sortedBy { it.priceMinor }
        // A doctor with no answered window yet has no reply time to be fast with.
        DoctorSort.FastReply -> kept.sortedWith(compareBy(nullsLast()) { it.avgFirstReplyMinutes })
    }
}

/**
 * Rated doctors first, best rating first; a tie goes to the one more patients rated.
 * Doctors under the threshold follow, by how many ratings they have — their average is
 * not shown, so it does not order them either.
 */
private val ByRating: Comparator<DoctorListItem> =
    compareByDescending<DoctorListItem> { hasEnoughRatings(it.ratingCount) }
        .thenByDescending { if (hasEnoughRatings(it.ratingCount)) it.rating ?: 0.0 else 0.0 }
        .thenByDescending { it.ratingCount }
