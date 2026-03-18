package ai.androidassistant.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Monitors WiFi and Bluetooth connectivity changes
 */
class ConnectivityMonitor {

  companion object {
    private const val TAG = "ConnectivityMonitor"
  }

  private val rules = mutableListOf<AutomationRule>()
  private var wifiReceiver: BroadcastReceiver? = null
  private var appContext: Context? = null

  fun start(context: Context) {
    if (wifiReceiver != null) {
      return
    }
    appContext = context.applicationContext
    wifiReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, _intent: Intent) {
          val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
          val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
          val network = connectivityManager.activeNetwork
          val capabilities = connectivityManager.getNetworkCapabilities(network)
          val connected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
          val ssid =
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
              ?.ssid
              ?.removeSurrounding("\"")
              ?.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }

          Log.d(TAG, "WiFi state changed: connected=$connected ssid=$ssid")
          checkConnectivityRules(
            type = TriggerConfig.ConnectivityType.WIFI,
            ssid = ssid,
            connected = connected,
          )
        }
      }

    val wifiFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(wifiReceiver, wifiFilter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(wifiReceiver, wifiFilter)
    }

    Log.d(TAG, "Connectivity monitoring started")
  }

  fun stop() {
    val receiver = wifiReceiver ?: return
    appContext?.runCatching { unregisterReceiver(receiver) }
    wifiReceiver = null
    appContext = null
    Log.d(TAG, "Connectivity monitoring stopped")
  }

  fun replaceRules(newRules: List<AutomationRule>) {
    rules.clear()
    rules.addAll(newRules.filter { it.triggerType == TriggerType.CONNECTIVITY })
  }

  private fun checkConnectivityRules(type: TriggerConfig.ConnectivityType, ssid: String?, connected: Boolean) {
    rules
      .filter { it.isEnabled && it.triggerType == TriggerType.CONNECTIVITY }
      .forEach { rule ->
        val config = rule.triggerConfig as? TriggerConfig.ConnectivityConfig ?: return@forEach
        if (config.connectivityType != type) {
          return@forEach
        }

        val matches =
          when {
            connected && config.triggerType == TriggerConfig.ConnectivityTriggerType.CONNECTED ->
              config.ssid == null || config.ssid == ssid
            !connected && config.triggerType == TriggerConfig.ConnectivityTriggerType.DISCONNECTED -> true
            else -> false
          }

        if (matches) {
          AutomationEngine.onTriggerDetected(
            rule = rule,
            triggerData =
              mapOf(
                "type" to type.name,
                "ssid" to ssid,
                "connected" to connected.toString(),
              ),
          )
        }
      }
  }
}

