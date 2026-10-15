package uz.sadora.app.data.widget

import uz.sadora.app.data.ApiFailure
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.SadoraGraph
import uz.sadora.contract.RecordDoseRequest
import uz.sadora.contract.WidgetSummary
import uz.sadora.widget.PendingAction
import uz.sadora.widget.WidgetApi
import uz.sadora.widget.WidgetCall

/** [WidgetApi] over the app's own session: what the app uses when it drives the widgets itself. */
class AppWidgetApi(private val graph: SadoraGraph) : WidgetApi {
    override suspend fun summary(): WidgetCall<WidgetSummary> = graph.widgetSummaryApi.summary().toCall()

    override suspend fun send(action: PendingAction): WidgetCall<Unit> = when (action) {
        is PendingAction.Water -> graph.nutritionApi.addWater(action.ml, action.clientId).toCall { }
        is PendingAction.Dose -> graph.medicationApi
            .recordDose(action.medicationId, RecordDoseRequest(action.dueOn, action.dueAt, action.status))
            .toCall { }
    }

    private fun <T> ApiResult<T>.toCall(): WidgetCall<T> = toCall { it }

    private fun <T, R> ApiResult<T>.toCall(map: (T) -> R): WidgetCall<R> = when (this) {
        is ApiResult.Success -> WidgetCall.Ok(map(value))
        is ApiResult.Failure -> when (failure) {
            is ApiFailure.Network -> WidgetCall.Offline
            is ApiFailure.Unauthorized, is ApiFailure.Blocked -> WidgetCall.SignedOut
            else -> if (failure.isServerSide()) WidgetCall.Offline else WidgetCall.Rejected
        }
    }

    /** A 5xx or a rate limit is the server's moment, not the tap's fault: keep it for later. */
    private fun ApiFailure.isServerSide(): Boolean = this is ApiFailure.Unexpected || this is ApiFailure.RateLimited
}
