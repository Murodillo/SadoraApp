package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import uz.sadora.app.data.health.HealthPlatform

/**
 * The system's own permission sheet for the phone's health store.
 *
 * A handle rather than a suspend call because Android can only show the sheet through
 * an Activity result registered during composition. [onResult] is true when reading can
 * go ahead — on iOS that means the sheet was shown, since HealthKit never says whether
 * reading was allowed.
 */
@Composable
expect fun rememberHealthAccessRequest(platform: HealthPlatform, onResult: (Boolean) -> Unit): () -> Unit
