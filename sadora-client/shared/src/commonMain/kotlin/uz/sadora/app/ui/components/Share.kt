package uz.sadora.app.ui.components

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

/**
 * Hands a picture to the share sheet: a PNG, by the bytes, under [fileName]. The
 * prescription card uses it, so it can be shown at a pharmacy or sent to family.
 */
@Composable
expect fun rememberImageShareAction(): (png: ByteArray, fileName: String) -> Unit

/** The bitmap as PNG bytes, for [rememberImageShareAction]. */
expect fun androidx.compose.ui.graphics.ImageBitmap.encodePng(): ByteArray
