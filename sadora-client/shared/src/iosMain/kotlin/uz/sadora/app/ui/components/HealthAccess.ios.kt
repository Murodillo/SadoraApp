package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import uz.sadora.app.data.health.HealthKitPlatform
import uz.sadora.app.data.health.HealthPlatform

/** HealthKit's sheet needs no view controller of ours; the store presents it itself. */
@Composable
actual fun rememberHealthAccessRequest(platform: HealthPlatform, onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember(platform) {
        {
            scope.launch {
                callback.value((platform as? HealthKitPlatform)?.requestAccess() ?: false)
            }
        }
    }
}
