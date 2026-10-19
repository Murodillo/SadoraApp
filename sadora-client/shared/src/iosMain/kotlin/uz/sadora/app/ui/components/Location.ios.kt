package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.NSTimer
import platform.darwin.NSObject

/** Held while a request is open: the manager and its delegate would otherwise be collected. */
private val open = mutableSetOf<OneShot>()

@Composable
actual fun rememberOneShotLocation(): (onResult: (GeoPoint?) -> Unit) -> Unit = remember {
    { callback -> OneShot(callback).start() }
}

@OptIn(ExperimentalForeignApi::class)
private class OneShot(private val callback: (GeoPoint?) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var done = false

    fun start() {
        open += this
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyBest
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> finish(null)
            else -> manager.requestLocation()
        }
        NSTimer.scheduledTimerWithTimeInterval(8.0, repeats = false) { _ -> finish(manager.location?.toPoint()) }
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> finish(null)
            else -> Unit
        }
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        finish((didUpdateLocations.lastOrNull() as? CLLocation)?.toPoint())
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        finish(manager.location?.toPoint())
    }

    private fun finish(point: GeoPoint?) {
        if (done) return
        done = true
        manager.delegate = null
        open -= this
        callback(point)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toPoint(): GeoPoint =
    coordinate.useContents { GeoPoint(latitude, longitude, horizontalAccuracy.toInt().takeIf { it >= 0 }) }
