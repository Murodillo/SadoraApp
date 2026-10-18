package uz.sadora.doctor.data

import kotlin.math.abs
import kotlin.math.roundToLong
import uz.sadora.contract.LabValue

// Her lab sheets, as a doctor reads them: in a record she shared, in one she attached
// to a consultation, and as a line of the thread.

/**
 * Where a sheet's photographed pages are read from. Each way the doctor came to the sheet
 * has its own route, and each is closed when that way is: the share link when she revokes
 * it, an attached record when the consultation ends, a chat line when the sheet is deleted.
 */
sealed interface LabPhotos {
    /** The cache key of one page. */
    fun key(page: Int): String

    /** A sheet sent as a line of the thread. */
    data class InMessage(val conversationId: String, val messageId: String) : LabPhotos {
        override fun key(page: Int) = "message:$messageId:$page"
    }

    /** A sheet inside the record attached by [messageId]. */
    data class InRecord(val conversationId: String, val messageId: String, val reportId: String) : LabPhotos {
        override fun key(page: Int) = "record:$messageId:$reportId:$page"
    }

    /** A sheet inside the record her QR code opened. */
    data class InShare(val token: String, val reportId: String) : LabPhotos {
        override fun key(page: Int) = "share:$token:$reportId:$page"
    }
}

/** "104", "5.6", "0.025": up to three decimals, never a trailing ".0". */
fun labNumber(value: Double): String {
    val thousandths = (abs(value) * 1000).roundToLong()
    val sign = if (value < 0 && thousandths > 0) "-" else ""
    val whole = thousandths / 1000
    val fraction = thousandths % 1000
    if (fraction == 0L) return "$sign$whole"
    return "$sign$whole." + fraction.toString().padStart(3, '0').trimEnd('0')
}

/** The result as printed — the number, or the text when it is not one — with its unit. */
fun labResult(row: LabValue): String? {
    val result = row.value?.let(::labNumber) ?: row.valueText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return listOfNotNull(result, row.unit?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" ")
}

/**
 * The laboratory's range: "120–150", "< 5" when it printed only a top, "> 30" when only
 * a bottom, else the range as printed; null when it printed none.
 */
fun labRange(row: LabValue): String? {
    val low = row.refLow
    val high = row.refHigh
    return when {
        low != null && high != null -> "${labNumber(low)}–${labNumber(high)}"
        high != null -> "< ${labNumber(high)}"
        low != null -> "> ${labNumber(low)}"
        else -> row.refText?.trim()?.takeIf { it.isNotEmpty() }
    }
}
