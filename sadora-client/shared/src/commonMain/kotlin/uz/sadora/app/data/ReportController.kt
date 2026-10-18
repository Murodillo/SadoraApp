package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.ReportApi
import uz.sadora.contract.FeatureEntitlement
import uz.sadora.contract.PdfReportRequest

/**
 * The doctor's report: what is left of her month's free one, and the file itself.
 *
 * A refusal for allowance is kept as [locked] rather than an error strip: the screen shows
 * the way to Premium there, where the button was.
 */
class ReportController(private val api: ReportApi?) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error

    fun clearError() = calls.clearError()

    /** Her allowance; null until read, or when the server sent none. */
    var allowance by mutableStateOf<FeatureEntitlement?>(null)
        private set
    var locked by mutableStateOf(false)
        private set

    suspend fun refresh() {
        val api = api ?: return
        calls.run(silent = true) { api.options() }?.let { options ->
            allowance = options.allowance
            locked = options.allowance?.available == false
        }
    }

    suspend fun pdf(request: PdfReportRequest): ByteArray? {
        val api = api ?: return null
        val bytes = calls.run { api.pdf(request) }
        if (bytes == null) {
            if (calls.error.isAllowance()) {
                locked = true
                calls.clearError()
            }
            return null
        }
        refresh()
        return bytes
    }
}

/** Out of the month's free report, or the feature is Premium for her: the paywall's case. */
internal fun ApiFailure?.isAllowance(): Boolean = this is ApiFailure.PremiumRequired || this is ApiFailure.LimitReached
