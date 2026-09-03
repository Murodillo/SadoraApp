package org.example.project.ui.components

import androidx.compose.runtime.Composable

/**
 * Hands [text] to the platform's own share sheet.
 *
 * A real share rather than a toast that claims one: a post someone wants to pass on is
 * usually going to one specific person in a messaging app, and only the system sheet
 * knows which apps those are.
 */
@Composable
expect fun rememberShareAction(): (String) -> Unit
