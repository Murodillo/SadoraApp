package org.example.project.ui.components

import androidx.compose.runtime.Composable

/**
 * Runs [onBack] when the platform asks to go back — the Android back button or
 * predictive-back gesture. iOS has no such system event (the app draws its own back
 * arrows), so the iOS actual is a no-op.
 *
 * While [enabled] is false the event falls through to the platform, which on Android
 * means leaving the app — the right outcome only when there is nothing left to pop.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)
