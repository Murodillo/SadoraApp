package uz.sadora.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import uz.sadora.app.data.health.HealthConnectPlatform
import uz.sadora.app.data.health.HealthPlatform

/**
 * Health Connect's permission screen, through its own Activity result contract.
 *
 * The answer is re-read from Health Connect rather than taken from the result: the
 * contract returns only what changed in this visit, so a second visit that grants
 * nothing new would otherwise look like a refusal.
 */
@Composable
actual fun rememberHealthAccessRequest(platform: HealthPlatform, onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        scope.launch { callback.value(platform.hasAccess()) }
    }
    return remember(platform, launcher) {
        {
            val healthConnect = platform as? HealthConnectPlatform
            if (healthConnect == null) {
                callback.value(false)
            } else {
                scope.launch {
                    runCatching { launcher.launch(healthConnect.requestedPermissions()) }
                        .onFailure { callback.value(false) }
                }
            }
        }
    }
}
