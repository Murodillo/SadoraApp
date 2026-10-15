package uz.sadora.app.data.api

import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.WidgetSummary

/** The one answer the home-screen widgets and the watch draw from. */
class WidgetSummaryApi(private val caller: ApiCaller) {
    suspend fun summary(): ApiResult<WidgetSummary> =
        caller.authenticated("v1/widget/summary", HttpMethodKind.GET)
}
