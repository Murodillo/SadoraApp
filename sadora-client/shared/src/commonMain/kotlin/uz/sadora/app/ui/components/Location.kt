package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Int?)

/**
 * One position, once: asks for the permission if it has not been given, then reads where the
 * phone is and calls back — with null when refused, switched off, or not found within a few
 * seconds. Nothing keeps listening afterwards. SOS is the only caller.
 */
@Composable
expect fun rememberOneShotLocation(): (onResult: (GeoPoint?) -> Unit) -> Unit
