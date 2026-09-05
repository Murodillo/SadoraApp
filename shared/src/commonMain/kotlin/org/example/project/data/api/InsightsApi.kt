package org.example.project.data.api

import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.InsightsSummary

/**
 * The trend windows behind Tahlillar — and behind the weekly charts on Mind and Uyqu,
 * which draw the same series over seven days.
 */
class InsightsApi(private val caller: ApiCaller) {

    suspend fun summary(days: Int): ApiResult<InsightsSummary> =
        caller.authenticated("v1/insights?days=$days", HttpMethodKind.GET)
}
