package org.example.project.ui.components

import androidx.compose.runtime.Composable

/** iOS has no system back event; the in-app back arrows are the only way back. */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
