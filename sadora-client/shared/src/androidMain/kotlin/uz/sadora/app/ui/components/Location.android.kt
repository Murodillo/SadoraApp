package uz.sadora.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

@Composable
actual fun rememberOneShotLocation(): (onResult: (GeoPoint?) -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(GeoPoint?) -> Unit>(1) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val callback = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (granted.values.any { it }) fetch(context, callback) else callback(null)
    }
    return remember(context) {
        { callback ->
            if (hasPermission(context)) {
                fetch(context, callback)
            } else {
                pending[0] = callback
                ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }
}

private fun hasPermission(context: Context) =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@Suppress("MissingPermission")
private fun fetch(context: Context, callback: (GeoPoint?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null || !hasPermission(context)) return callback(null)
    val last = runCatching {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }.getOrNull()
    // The network fix first: seconds indoors, where GPS may take a minute.
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { manager.isProviderEnabled(it) }
        ?: return callback(last?.toPoint())
    var done = false
    val finish = { found: Location? ->
        if (!done) {
            done = true
            callback((found ?: last)?.toPoint())
        }
    }
    val signal = CancellationSignal()
    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { finish(it) }
    // A phone indoors may never fix: after a few seconds the last known place will do.
    Handler(Looper.getMainLooper()).postDelayed({
        signal.cancel()
        finish(null)
    }, TIMEOUT_MILLIS)
}

private fun Location.toPoint() = GeoPoint(latitude, longitude, if (hasAccuracy()) accuracy.toInt() else null)

private const val TIMEOUT_MILLIS = 7_000L
