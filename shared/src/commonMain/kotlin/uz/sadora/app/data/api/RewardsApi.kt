package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.DailyCheckInResult
import uz.sadora.contract.HomeLayout
import uz.sadora.contract.HomeWidget
import uz.sadora.contract.RedeemRequest
import uz.sadora.contract.RedeemResult
import uz.sadora.contract.Redemption
import uz.sadora.contract.ReferralStatus
import uz.sadora.contract.RewardsSummary
import uz.sadora.contract.SaveHomeLayoutRequest
import uz.sadora.contract.ShopCatalog

/**
 * Streaks, Nur, the shop and the home layout.
 *
 * `checkIn` is the only call the app makes without being asked to: it runs once on
 * entering the tab shell, and the server decides whether that open was worth anything.
 * Everything else is opened by a screen.
 */
class RewardsApi(private val caller: ApiCaller) {

    suspend fun checkIn(): ApiResult<DailyCheckInResult> =
        caller.authenticated("v1/rewards/check-in", HttpMethodKind.POST)

    suspend fun summary(): ApiResult<RewardsSummary> =
        caller.authenticated("v1/rewards", HttpMethodKind.GET)

    suspend fun referral(): ApiResult<ReferralStatus> =
        caller.authenticated("v1/rewards/referral", HttpMethodKind.GET)

    suspend fun catalogue(): ApiResult<ShopCatalog> =
        caller.authenticated("v1/shop", HttpMethodKind.GET)

    suspend fun redemptions(): ApiResult<List<Redemption>> =
        caller.authenticated("v1/shop/redemptions", HttpMethodKind.GET)

    suspend fun redeem(productId: String): ApiResult<RedeemResult> =
        caller.authenticated("v1/shop/redeem", HttpMethodKind.POST) {
            setBody(RedeemRequest(productId))
        }

    suspend fun homeLayout(): ApiResult<HomeLayout> =
        caller.authenticated("v1/me/home-layout", HttpMethodKind.GET)

    suspend fun saveHomeLayout(widgets: List<HomeWidget>): ApiResult<HomeLayout> =
        caller.authenticated("v1/me/home-layout", HttpMethodKind.PUT) {
            setBody(SaveHomeLayoutRequest(widgets))
        }

    suspend fun resetHomeLayout(): ApiResult<HomeLayout> =
        caller.authenticated("v1/me/home-layout", HttpMethodKind.DELETE)
}
