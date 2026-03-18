package ai.androidassistant.app.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Monitors location changes and triggers automation rules
 * Requires: Location permission + Background location
 */
class LocationMonitor {

  companion object {
    private const val TAG = "LocationMonitor"
    private const val LOCATION_UPDATE_INTERVAL = 30_000L
  }

  private var fusedLocationClient: FusedLocationProviderClient? = null
  private var locationCallback: LocationCallback? = null
  private val rules = mutableListOf<AutomationRule>()
  private var lastLocation: Location? = null

  fun start(context: Context) {
    if (locationCallback != null) {
      return
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "Location permission not granted")
      return
    }

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    locationCallback =
      object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
          val location = locationResult.lastLocation ?: return
          val previousLocation = lastLocation
          checkLocationRules(location = location, previousLocation = previousLocation)
          lastLocation = location
        }
      }

    val request =
      LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, LOCATION_UPDATE_INTERVAL).build()

    fusedLocationClient?.requestLocationUpdates(request, requireNotNull(locationCallback), Looper.getMainLooper())
    Log.d(TAG, "Location monitoring started")
  }

  fun stop() {
    locationCallback?.let { callback ->
      fusedLocationClient?.removeLocationUpdates(callback)
    }
    locationCallback = null
    Log.d(TAG, "Location monitoring stopped")
  }

  fun replaceRules(newRules: List<AutomationRule>) {
    rules.clear()
    rules.addAll(newRules.filter { it.triggerType == TriggerType.LOCATION })
  }

  private fun checkLocationRules(location: Location, previousLocation: Location?) {
    rules
      .filter { it.isEnabled && it.triggerType == TriggerType.LOCATION }
      .forEach { rule ->
        val config = rule.triggerConfig as? TriggerConfig.LocationConfig ?: return@forEach

        val distance = FloatArray(1)
        Location.distanceBetween(
          location.latitude,
          location.longitude,
          config.latitude,
          config.longitude,
          distance,
        )

        val isInside = distance[0] <= config.radiusMeters
        val wasInside = previousLocation?.let { isInsideLocation(it, config) } ?: false

        when (config.triggerType) {
          TriggerConfig.LocationTriggerType.ENTER -> {
            if (isInside && !wasInside) {
              AutomationEngine.onTriggerDetected(
                rule = rule,
                triggerData =
                  mapOf(
                    "location" to "${location.latitude},${location.longitude}",
                    "place" to "Unknown",
                  ),
              )
            }
          }
          TriggerConfig.LocationTriggerType.EXIT -> {
            if (!isInside && wasInside) {
              AutomationEngine.onTriggerDetected(
                rule = rule,
                triggerData =
                  mapOf(
                    "location" to "${location.latitude},${location.longitude}",
                    "place" to "Unknown",
                  ),
              )
            }
          }
          TriggerConfig.LocationTriggerType.DWELL -> Unit
        }
      }
  }

  private fun isInsideLocation(location: Location, config: TriggerConfig.LocationConfig): Boolean {
    val distance = FloatArray(1)
    Location.distanceBetween(
      location.latitude,
      location.longitude,
      config.latitude,
      config.longitude,
      distance,
    )
    return distance[0] <= config.radiusMeters
  }
}

