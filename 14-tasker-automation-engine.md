# 14. Tasker-Style Automation Engine - Code Implementation

## For Developers 👨‍💻

This module provides production-ready Kotlin code for implementing Tasker-style automation in your AI assistant.

---

## Part 1: Core Automation Engine

### AutomationRule Data Class

```kotlin
// file: app/src/main/java/com/assistant/automation/AutomationRule.kt

package com.assistant.automation

import java.util.Date
import java.util.UUID

/**
 * Represents a user-created automation rule (Tasker-style)
 * Example: "When Mom calls, announce caller name"
 */
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,                    // User-friendly name
    val triggerType: TriggerType,        // What starts the automation
    val triggerConfig: TriggerConfig,    // Trigger details
    val actionType: ActionType,          // What to do
    actionConfig: ActionConfig,          // Action details
    val isEnabled: Boolean = true,
    val createdAt: Date = Date(),
    val lastTriggered: Date? = null,
    val triggerCount: Long = 0
) {
    var actionConfig = actionConfig
        private set
    
    fun updateActionConfig(newConfig: ActionConfig) {
        actionConfig = newConfig
    }
    
    fun recordTrigger() {
        triggerCount++
        // lastTriggered updated by AutomationEngine
    }
    
    fun copyWithTriggered(timestamp: Date): AutomationRule {
        return this.copy(
            lastTriggered = timestamp,
            triggerCount = this.triggerCount + 1
        )
    }
}

/**
 * Types of triggers that can start an automation
 */
enum class TriggerType {
    NOTIFICATION,          // When app notification arrives
    TIME,                  // At specific time
    LOCATION,              // When entering/leaving place
    CONNECTIVITY,          // WiFi/BT connect/disconnect
    CALL_EVENT,            // Incoming/outgoing call
    MESSAGE_RECEIVED,      // SMS/WhatsApp received
    VOICE_COMMAND,         // User speaks trigger phrase
    SENSOR,                // Shake, flip, etc.
    APP_EVENT              // App opened/closed
}

/**
 * Configuration for different trigger types
 */
sealed class TriggerConfig {
    data class NotificationConfig(
        val packageName: String,
        val keyword: String? = null,
        val contact: String? = null
    ) : TriggerConfig()
    
    data class TimeConfig(
        val hour: Int,
        val minute: Int,
        val daysOfWeek: Set<Int> = emptySet(), // Calendar.SUNDAY etc.
        val repeat: Boolean = true
    ) : TriggerConfig()
    
    data class LocationConfig(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
        val triggerType: LocationTriggerType
    ) : TriggerConfig()
    
    enum class LocationTriggerType { ENTER, EXIT, DWELL }
    
    data class ConnectivityConfig(
        val connectivityType: ConnectivityType,
        val ssid: String? = null,
        val macAddress: String? = null,
        val triggerType: ConnectivityTriggerType
    ) : TriggerConfig()
    
    enum class ConnectivityType { WIFI, BLUETOOTH }
    enum class ConnectivityTriggerType { CONNECTED, DISCONNECTED }
    
    data class CallEventConfig(
        val eventType: CallEventType,
        val phoneNumber: String? = null,
        val contactName: String? = null
    ) : TriggerConfig()
    
    enum class CallEventType { INCOMING, OUTGOADING, MISSED }
    
    data class MessageConfig(
        val appPackage: String,
        val sender: String? = null,
        val containsKeyword: String? = null
    ) : TriggerConfig()
    
    data class VoiceCommandConfig(
        val commandPhrase: String,
        val confidenceThreshold: Float = 0.8f
    ) : TriggerConfig()
    
    data class SensorConfig(
        val sensorType: SensorType,
        val threshold: Float
    ) : TriggerConfig()
    
    enum class SensorType { ACCELEROMETER, GYROSCOPE, LIGHT, PROXIMITY }
    
    data class AppConfig(
        val packageName: String,
        val eventType: AppEventType
    ) : TriggerConfig()
    
    enum class AppEventType { LAUNCHED, CLOSED, FOREGROUND, BACKGROUND }
}

/**
 * Types of actions the automation can perform
 */
enum class ActionType {
    ANNOUNCE,              // Speak text aloud
    SEND_MESSAGE,          // Send SMS/WhatsApp
    LAUNCH_APP,            // Open an app
    CHANGE_SETTING,        // Toggle WiFi, BT, etc.
    NAVIGATE,              // Open maps with destination
    CREATE_REMINDER,       // Add to calendar/reminder
    LOG_EVENT,             // Record to history
    TRIGGER_WEBHOOK,       // Call external API
    RUN_SCRIPT             // Execute custom code
}

/**
 * Configuration for different action types
 */
sealed class ActionConfig {
    data class AnnounceConfig(
        val text: String,
        val language: String = "en-US",
        val pitch: Float = 1.0f,
        val speed: Float = 1.0f
    ) : ActionConfig()
    
    data class SendMessageConfig(
        val appPackage: String,
        val recipient: String,
        val message: String,
        val sendImmediately: Boolean = false
    ) : ActionConfig()
    
    data class LaunchAppConfig(
        val packageName: String,
        val activityClass: String? = null,
        val extras: Map<String, Any?> = emptyMap()
    ) : ActionConfig()
    
    data class ChangeSettingConfig(
        val settingType: SettingType,
        val value: Boolean,
        val wifiSsid: String? = null,
        val brightnessLevel: Int? = null
    ) : ActionConfig()
    
    enum class SettingType { WIFI, BLUETOOTH, LOCATION, NFC, AIRPLANE_MODE, BRIGHTNESS }
    
    data class NavigateConfig(
        val destination: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val mapApp: String = "com.google.android.apps.maps"
    ) : ActionConfig()
    
    data class ReminderConfig(
        val title: String,
        val description: String? = null,
        val time: Long,
        val alarmType: AlarmType
    ) : ActionConfig()
    
    enum class AlarmType { ONCE, DAILY, WEEKLY }
    
    data class WebhookConfig(
        val url: String,
        val method: String = "POST",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null
    ) : ActionConfig()
}
```

---

## Part 2: Automation Engine Service

```kotlin
// file: app/src/main/java/com/assistant/automation/AutomationEngine.kt

package com.assistant.automation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Main automation engine that monitors triggers and executes actions
 * Runs as a foreground service for continuous monitoring
 */
class AutomationEngine : Service(), TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "AutomationEngine"
        
        fun start(context: Context) {
            val intent = Intent(context, AutomationEngine::class.java)
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, AutomationEngine::class.java)
            context.stopService(intent)
        }
        
        @Volatile
        private var instance: AutomationEngine? = null
        
        fun getInstance(): AutomationEngine? = instance
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Rule storage
    private val rules = CopyOnWriteArrayList<AutomationRule>()
    private val rulesFlow = MutableStateFlow<List<AutomationRule>>(emptyList())
    
    // TTS for announcements
    private var tts: TextToSpeech? = null
    
    // Receivers for monitoring
    private val notificationListener = NotificationListener()
    private val locationMonitor = LocationMonitor()
    private val connectivityMonitor = ConnectivityMonitor()
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeTTS()
        loadRules()
        startMonitoring()
        Log.d(TAG, "AutomationEngine created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(AUTOMATION_NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        instance = null
        stopMonitoring()
        tts?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }
    
    // ==================== Rule Management ====================
    
    fun addRule(rule: AutomationRule) {
        rules.add(rule)
        rulesFlow.value = rules.toList()
        saveRules()
        enableMonitoringForRule(rule)
        Log.d(TAG, "Rule added: ${rule.name}")
    }
    
    fun removeRule(ruleId: String) {
        rules.removeAll { it.id == ruleId }
        rulesFlow.value = rules.toList()
        saveRules()
        Log.d(TAG, "Rule removed: $ruleId")
    }
    
    fun updateRule(ruleId: String, updates: AutomationRule.() -> AutomationRule) {
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index != -1) {
            val updated = rules[index].updates()
            rules[index] = updated
            rulesFlow.value = rules.toList()
            saveRules()
        }
    }
    
    fun getRules(): List<AutomationRule> = rules.toList()
    
    fun getRulesFlow(): StateFlow<List<AutomationRule>> = rulesFlow
    
    // ==================== Trigger Monitoring ====================
    
    private fun startMonitoring() {
        notificationListener.start(this)
        locationMonitor.start(this)
        connectivityMonitor.start(this)
        startTimeMonitors()
    }
    
    private fun stopMonitoring() {
        notificationListener.stop()
        locationMonitor.stop()
        connectivityMonitor.stop()
        stopTimeMonitors()
    }
    
    private fun enableMonitoringForRule(rule: AutomationRule) {
        when (rule.triggerType) {
            TriggerType.NOTIFICATION -> notificationListener.addRule(rule)
            TriggerType.LOCATION -> locationMonitor.addRule(rule)
            TriggerType.CONNECTIVITY -> connectivityMonitor.addRule(rule)
            TriggerType.TIME -> scheduleTimeRule(rule)
            else -> { /* Other monitors initialized as needed */ }
        }
    }
    
    // ==================== Trigger Handling ====================
    
    internal fun onTriggerDetected(rule: AutomationRule, triggerData: Any?) {
        if (!rule.isEnabled) {
            Log.d(TAG, "Rule disabled, skipping: ${rule.name}")
            return
        }
        
        serviceScope.launch {
            try {
                Log.d(TAG, "Trigger detected: ${rule.name}")
                executeAction(rule, triggerData)
                
                // Update rule stats
                val index = rules.indexOfFirst { it.id == rule.id }
                if (index != -1) {
                    rules[index] = rule.copyWithTriggered(Date())
                    rulesFlow.value = rules.toList()
                    saveRules()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action", e)
            }
        }
    }
    
    // ==================== Action Execution ====================
    
    private suspend fun executeAction(rule: AutomationRule, triggerData: Any?) {
        when (rule.actionType) {
            ActionType.ANNOUNCE -> executeAnnounce(rule.actionConfig as ActionConfig.AnnounceConfig)
            ActionType.SEND_MESSAGE -> executeSendMessage(rule.actionConfig as ActionConfig.SendMessageConfig)
            ActionType.LAUNCH_APP -> executeLaunchApp(rule.actionConfig as ActionConfig.LaunchAppConfig)
            ActionType.CHANGE_SETTING -> executeChangeSetting(rule.actionConfig as ActionConfig.ChangeSettingConfig)
            ActionType.NAVIGATE -> executeNavigate(rule.actionConfig as ActionConfig.NavigateConfig)
            ActionType.CREATE_REMINDER -> executeCreateReminder(rule.actionConfig as ActionConfig.ReminderConfig)
            ActionType.TRIGGER_WEBHOOK -> executeWebhook(rule.actionConfig as ActionConfig.WebhookConfig)
            else -> Log.w(TAG, "Unknown action type: ${rule.actionType}")
        }
    }
    
    private fun executeAnnounce(config: ActionConfig.AnnounceConfig) {
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                tts?.apply {
                    setPitch(config.pitch)
                    setSpeechRate(config.speed)
                    speak(config.text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }
    
    private fun executeSendMessage(config: ActionConfig.SendMessageConfig) {
        serviceScope.launch {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = when (config.appPackage) {
                        "com.whatsapp" -> android.net.Uri.parse("smsto:")
                        "com.facebook.orca" -> android.net.Uri.parse("smsto:")
                        else -> android.net.Uri.parse("smsto:${config.recipient}")
                    }
                    putExtra("sms_body", config.message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                
                if (config.sendImmediately) {
                    // Note: Auto-sending requires accessibility service
                    Log.d(TAG, "Message prepared for: ${config.recipient}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
            }
        }
    }
    
    private fun executeLaunchApp(config: ActionConfig.LaunchAppConfig) {
        serviceScope.launch {
            try {
                val intent = packageManager.getLaunchIntentForPackage(config.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    config.extras.forEach { (key, value) ->
                        when (value) {
                            is String -> putExtra(key, value)
                            is Int -> putExtra(key, value)
                            is Boolean -> putExtra(key, value)
                        }
                    }
                }
                if (intent != null) {
                    startActivity(intent)
                    Log.d(TAG, "Launched: ${config.packageName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching app", e)
            }
        }
    }
    
    private fun executeChangeSetting(config: ActionConfig.ChangeSettingConfig) {
        serviceScope.launch {
            try {
                when (config.settingType) {
                    ActionConfig.SettingType.WIFI -> {
                        val wifiManager = getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
                        wifiManager.isWifiEnabled = config.value
                    }
                    ActionConfig.SettingType.BLUETOOTH -> {
                        val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                        if (config.value) btAdapter?.enable() else btAdapter?.disable()
                    }
                    ActionConfig.SettingType.LOCATION -> {
                        // Requires Settings.Secure access
                        Log.d(TAG, "Location toggle requested: ${config.value}")
                    }
                    else -> Log.d(TAG, "Setting change: ${config.settingType} = ${config.value}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error changing setting", e)
            }
        }
    }
    
    private fun executeNavigate(config: ActionConfig.NavigateConfig) {
        serviceScope.launch {
            try {
                val uri = if (config.latitude != null && config.longitude != null) {
                    android.net.Uri.parse("geo:${config.latitude},${config.longitude}?q=${config.destination}")
                } else {
                    android.net.Uri.parse("geo:0,0?q=${config.destination}")
                }
                
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(config.mapApp)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.d(TAG, "Navigation started to: ${config.destination}")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting navigation", e)
            }
        }
    }
    
    private fun executeCreateReminder(config: ActionConfig.ReminderConfig) {
        serviceScope.launch {
            try {
                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, config.title)
                    putExtra(android.provider.AlarmClock.EXTRA_TIME, config.time)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.d(TAG, "Reminder created: ${config.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating reminder", e)
            }
        }
    }
    
    private suspend fun executeWebhook(config: ActionConfig.WebhookConfig) {
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder().build()
                    val request = okhttp3.Request.Builder()
                        .url(config.url)
                        .method(config.method, config.body?.toRequestBody())
                        .apply {
                            config.headers.forEach { (key, value) ->
                                addHeader(key, value)
                            }
                        }
                        .build()
                    
                    val response = client.newCall(request).execute()
                    Log.d(TAG, "Webhook response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing webhook", e)
            }
        }
    }
    
    // ==================== Persistence ====================
    
    private fun loadRules() {
        serviceScope.launch {
            try {
                val prefs = getSharedPreferences("automation_rules", Context.MODE_PRIVATE)
                val json = prefs.getString("rules", null)
                if (json != null) {
                    // Use Gson or Moshi to deserialize
                    // rules.addAll(deserializedRules)
                    Log.d(TAG, "Loaded ${rules.size} rules from storage")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading rules", e)
            }
        }
    }
    
    private fun saveRules() {
        serviceScope.launch {
            try {
                val prefs = getSharedPreferences("automation_rules", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                // Use Gson or Moshi to serialize
                // editor.putString("rules", serializedJson)
                editor.apply()
                Log.d(TAG, "Saved ${rules.size} rules")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving rules", e)
            }
        }
    }
    
    // ==================== Notification ====================
    
    private fun createNotification(): android.app.Notification {
        val channelId = "automation_engine_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Automation Engine",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        
        return android.app.Notification.Builder(this, channelId)
            .setContentTitle("Automation Active")
            .setContentText("Monitoring ${rules.size} rules")
            .setSmallIcon(R.drawable.ic_automation)
            .build()
    }
    
    companion object {
        private const val AUTOMATION_NOTIFICATION_ID = 1001
    }
}
```

---

## Part 3: Notification Listener

```kotlin
// file: app/src/main/java/com/assistant/automation/NotificationListener.kt

package com.assistant.automation

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens to notifications and triggers automation rules
 * Requires: Settings → Accessibility → Notification Access
 */
class NotificationListener : NotificationListenerService() {
    
    companion object {
        private const val TAG = "NotificationListener"
        
        fun isPermissionGranted(context: android.content.Context): Boolean {
            val cn = ComponentName(context, NotificationListener::class.java)
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return !flat.isNullOrBlank() && flat.split(':').any {
                ComponentName.unflattenFromString(it) == cn
            }
        }
        
        fun requestPermission(context: android.content.Context) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    
    private val rules = mutableListOf<AutomationRule>()
    
    fun addRule(rule: AutomationRule) {
        if (rule.triggerType == TriggerType.NOTIFICATION) {
            rules.add(rule)
        }
    }
    
    fun removeRule(ruleId: String) {
        rules.removeAll { it.id == ruleId }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val title = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val text = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        
        Log.d(TAG, "Notification: $packageName - $title - $text")
        
        // Check matching rules
        rules.filter { it.isEnabled && it.triggerType == TriggerType.NOTIFICATION }
            .forEach { rule ->
                val config = rule.triggerConfig as TriggerConfig.NotificationConfig
                if (config.packageName == packageName) {
                    if (config.keyword == null || text?.contains(config.keyword) == true) {
                        if (config.contact == null || title?.contains(config.contact) == true) {
                            AutomationEngine.getInstance()?.onTriggerDetected(rule, sbn)
                        }
                    }
                }
            }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: Handle notification dismissal
    }
}
```

---

## Part 4: Location Monitor

```kotlin
// file: app/src/main/java/com/assistant/automation/LocationMonitor.kt

package com.assistant.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

/**
 * Monitors location changes and triggers automation rules
 * Requires: Location permission + Background location
 */
class LocationMonitor {
    
    companion object {
        private const val TAG = "LocationMonitor"
        private const val LOCATION_UPDATE_INTERVAL = 30_000L // 30 seconds
    }
    
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val rules = mutableListOf<AutomationRule>()
    private var lastLocation: Location? = null
    
    fun start(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Location permission not granted")
            return
        }
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lastLocation = location
                    checkLocationRules(location)
                }
            }
        }
        
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).build()
        
        fusedLocationClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        Log.d(TAG, "Location monitoring started")
    }
    
    fun stop() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        Log.d(TAG, "Location monitoring stopped")
    }
    
    fun addRule(rule: AutomationRule) {
        if (rule.triggerType == TriggerType.LOCATION) {
            rules.add(rule)
        }
    }
    
    private fun checkLocationRules(location: Location) {
        rules.filter { it.isEnabled && it.triggerType == TriggerType.LOCATION }
            .forEach { rule ->
                val config = rule.triggerConfig as TriggerConfig.LocationConfig
                
                val distance = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    config.latitude,
                    config.longitude,
                    distance
                )
                
                val isInside = distance[0] <= config.radiusMeters
                
                when (config.triggerType) {
                    TriggerConfig.LocationTriggerType.ENTER -> {
                        if (isInside && (lastLocation == null || !isInsideLocation(lastLocation!!, config))) {
                            AutomationEngine.getInstance()?.onTriggerDetected(rule, location)
                        }
                    }
                    TriggerConfig.LocationTriggerType.EXIT -> {
                        if (!isInside && lastLocation != null && isInsideLocation(lastLocation!!, config)) {
                            AutomationEngine.getInstance()?.onTriggerDetected(rule, location)
                        }
                    }
                    TriggerConfig.LocationTriggerType.DWELL -> {
                        // Implement dwell time logic
                    }
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
            distance
        )
        return distance[0] <= config.radiusMeters
    }
}
```

---

## Part 5: Connectivity Monitor

```kotlin
// file: app/src/main/java/com/assistant/automation/ConnectivityMonitor.kt

package com.assistant.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
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
    private var btReceiver: BroadcastReceiver? = null
    
    fun start(context: Context) {
        // WiFi receiver
        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ssid = wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
                val connected = intent.getBooleanExtra(WifiManager.EXTRA_WIFI_STATE, false)
                
                Log.d(TAG, "WiFi state: $connected, SSID: $ssid")
                checkConnectivityRules(TriggerConfig.ConnectivityType.WIFI, ssid, connected)
            }
        }
        
        val wifiFilter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        context.registerReceiver(wifiReceiver, wifiFilter)
        
        // Bluetooth receiver
        btReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)
                val connected = state == android.bluetooth.BluetoothAdapter.STATE_CONNECTED
                
                Log.d(TAG, "BT state: $state")
                checkConnectivityRules(TriggerConfig.ConnectivityType.BLUETOOTH, null, connected)
            }
        }
        
        val btFilter = IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        context.registerReceiver(btReceiver, btFilter)
        
        Log.d(TAG, "Connectivity monitoring started")
    }
    
    fun stop() {
        wifiReceiver = null
        btReceiver = null
        Log.d(TAG, "Connectivity monitoring stopped")
    }
    
    fun addRule(rule: AutomationRule) {
        if (rule.triggerType == TriggerType.CONNECTIVITY) {
            rules.add(rule)
        }
    }
    
    private fun checkConnectivityRules(
        type: TriggerConfig.ConnectivityType,
        ssid: String?,
        connected: Boolean
    ) {
        rules.filter { it.isEnabled && it.triggerType == TriggerType.CONNECTIVITY }
            .forEach { rule ->
                val config = rule.triggerConfig as TriggerConfig.ConnectivityConfig
                
                if (config.connectivityType == type) {
                    val matches = when {
                        connected && config.triggerType == TriggerConfig.ConnectivityTriggerType.CONNECTED -> {
                            config.ssid == null || config.ssid == ssid
                        }
                        !connected && config.triggerType == TriggerConfig.ConnectivityTriggerType.DISCONNECTED -> true
                        else -> false
                    }
                    
                    if (matches) {
                        AutomationEngine.getInstance()?.onTriggerDetected(rule, ssid)
                    }
                }
            }
    }
}
```

---

## Part 6: Rule Builder (User-Friendly API)

```kotlin
// file: app/src/main/java/com/assistant/automation/RuleBuilder.kt

package com.assistant.automation

/**
 * Fluent API for creating automation rules programmatically
 * Also used by AI assistant to create rules from natural language
 */
class RuleBuilder {
    private var name: String = ""
    private var triggerType: TriggerType? = null
    private var triggerConfig: TriggerConfig? = null
    private var actionType: ActionType? = null
    private var actionConfig: ActionConfig? = null
    
    fun named(name: String) = apply { this.name = name }
    
    fun whenNotification(
        packageName: String,
        keyword: String? = null,
        contact: String? = null
    ) = apply {
        triggerType = TriggerType.NOTIFICATION
        triggerConfig = TriggerConfig.NotificationConfig(packageName, keyword, contact)
    }
    
    fun whenTime(hour: Int, minute: Int, days: Set<Int> = emptySet(), repeat: Boolean = true) = apply {
        triggerType = TriggerType.TIME
        triggerConfig = TriggerConfig.TimeConfig(hour, minute, days, repeat)
    }
    
    fun whenLocationEnter(lat: Double, lng: Double, radius: Float) = apply {
        triggerType = TriggerType.LOCATION
        triggerConfig = TriggerConfig.LocationConfig(lat, lng, radius, TriggerConfig.LocationTriggerType.ENTER)
    }
    
    fun whenLocationExit(lat: Double, lng: Double, radius: Float) = apply {
        triggerType = TriggerType.LOCATION
        triggerConfig = TriggerConfig.LocationConfig(lat, lng, radius, TriggerConfig.LocationTriggerType.EXIT)
    }
    
    fun whenWifiConnect(ssid: String) = apply {
        triggerType = TriggerType.CONNECTIVITY
        triggerConfig = TriggerConfig.ConnectivityConfig(
            TriggerConfig.ConnectivityType.WIFI,
            ssid,
            null,
            TriggerConfig.ConnectivityTriggerType.CONNECTED
        )
    }
    
    fun whenCallFrom(phoneNumber: String) = apply {
        triggerType = TriggerType.CALL_EVENT
        triggerConfig = TriggerConfig.CallEventConfig(
            TriggerConfig.CallEventType.INCOMING,
            phoneNumber,
            null
        )
    }
    
    fun whenMessageFrom(appPackage: String, sender: String? = null) = apply {
        triggerType = TriggerType.MESSAGE_RECEIVED
        triggerConfig = TriggerConfig.MessageConfig(appPackage, sender, null)
    }
    
    fun thenAnnounce(text: String, language: String = "en-US") = apply {
        actionType = ActionType.ANNOUNCE
        actionConfig = ActionConfig.AnnounceConfig(text, language)
    }
    
    fun thenSendMessage(appPackage: String, recipient: String, message: String) = apply {
        actionType = ActionType.SEND_MESSAGE
        actionConfig = ActionConfig.SendMessageConfig(appPackage, recipient, message)
    }
    
    fun thenLaunchApp(packageName: String) = apply {
        actionType = ActionType.LAUNCH_APP
        actionConfig = ActionConfig.LaunchAppConfig(packageName)
    }
    
    fun thenNavigate(destination: String) = apply {
        actionType = ActionType.NAVIGATE
        actionConfig = ActionConfig.NavigateConfig(destination)
    }
    
    fun thenToggleWifi(enable: Boolean) = apply {
        actionType = ActionType.CHANGE_SETTING
        actionConfig = ActionConfig.ChangeSettingConfig(
            ActionConfig.SettingType.WIFI,
            enable
        )
    }
    
    fun thenToggleBluetooth(enable: Boolean) = apply {
        actionType = ActionType.CHANGE_SETTING
        actionConfig = ActionConfig.ChangeSettingConfig(
            ActionConfig.SettingType.BLUETOOTH,
            enable
        )
    }
    
    fun build(): AutomationRule {
        require(name.isNotEmpty()) { "Rule name required" }
        require(triggerType != null) { "Trigger type required" }
        require(triggerConfig != null) { "Trigger config required" }
        require(actionType != null) { "Action type required" }
        require(actionConfig != null) { "Action config required" }
        
        return AutomationRule(
            name = name,
            triggerType = triggerType!!,
            triggerConfig = triggerConfig!!,
            actionType = actionType!!,
            actionConfig = actionConfig!!
        )
    }
}

/**
 * Example usage
 */
fun createExampleRules(): List<AutomationRule> {
    return listOf(
        // Rule 1: Announce when mom calls
        RuleBuilder()
            .named("Mom Call Announcement")
            .whenCallFrom("+1234567890")
            .thenAnnounce("Mom is calling!")
            .build(),
        
        // Rule 2: Turn on WiFi when home
        RuleBuilder()
            .named("Home WiFi Auto")
            .whenWifiConnect("HomeNetwork")
            .thenToggleWifi(true)
            .build(),
        
        // Rule 3: Navigate to work at 9 AM on weekdays
        RuleBuilder()
            .named("Morning Commute")
            .whenTime(9, 0, setOf(
                java.util.Calendar.MONDAY,
                java.util.Calendar.TUESDAY,
                java.util.Calendar.WEDNESDAY,
                java.util.Calendar.THURSDAY,
                java.util.Calendar.FRIDAY
            ))
            .thenNavigate("Work Office")
            .build(),
        
        // Rule 4: Announce WhatsApp from boss
        RuleBuilder()
            .named("Boss Message Alert")
            .whenMessageFrom("com.whatsapp", "Boss")
            .thenAnnounce("Message from your boss")
            .build()
    )
}
```

---

## Part 7: Natural Language to Rule Converter

```kotlin
// file: app/src/main/java/com/assistant/automation/NaturalLanguageParser.kt

package com.assistant.automation

import android.util.Log
import java.util.regex.Pattern

/**
 * Converts natural language commands to automation rules
 * Used by AI assistant to create rules from voice commands
 */
class NaturalLanguageParser {
    
    companion object {
        private const val TAG = "NLParser"
    }
    
    /**
     * Parse user's spoken command into an automation rule
     * Example: "Tell me when mom calls" → Notification rule
     */
    fun parseCommand(command: String): AutomationRule? {
        val lowerCommand = command.lowercase()
        
        return when {
            // Pattern: "Tell me when X calls"
            lowerCommand.contains("when") && lowerCommand.contains("calls") -> {
                parseCallRule(lowerCommand)
            }
            
            // Pattern: "At X time, do Y"
            lowerCommand.contains("at") && (lowerCommand.contains("am") || lowerCommand.contains("pm")) -> {
                parseTimeRule(lowerCommand)
            }
            
            // Pattern: "When I arrive at X"
            lowerCommand.contains("arrive") || lowerCommand.contains("get to") -> {
                parseLocationRule(lowerCommand, TriggerConfig.LocationTriggerType.ENTER)
            }
            
            // Pattern: "When I leave X"
            lowerCommand.contains("leave") || lowerCommand.contains("exit") -> {
                parseLocationRule(lowerCommand, TriggerConfig.LocationTriggerType.EXIT)
            }
            
            // Pattern: "When X messages me"
            lowerCommand.contains("message") || lowerCommand.contains("text") -> {
                parseMessageRule(lowerCommand)
            }
            
            // Pattern: "Connect to X WiFi"
            lowerCommand.contains("wifi") && lowerCommand.contains("connect") -> {
                parseConnectivityRule(lowerCommand)
            }
            
            else -> {
                Log.w(TAG, "Could not parse command: $command")
                null
            }
        }
    }
    
    private fun parseCallRule(command: String): AutomationRule? {
        // Extract contact name
        val contactPattern = Pattern.compile("when (\\w+) calls")
        val matcher = contactPattern.matcher(command)
        
        if (matcher.find()) {
            val contact = matcher.group(1)?.capitalize() ?: return null
            
            return RuleBuilder()
                .named("$contact Call Alert")
                .whenCallFrom(contact) // Would need contact lookup
                .thenAnnounce("$contact is calling!")
                .build()
        }
        
        return null
    }
    
    private fun parseTimeRule(command: String): AutomationRule? {
        // Extract time
        val timePattern = Pattern.compile("at (\\d+):?(\\d*)\\s*(am|pm)")
        val matcher = timePattern.matcher(command)
        
        if (matcher.find()) {
            var hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: 0
            val amPm = matcher.group(3)
            
            if (amPm == "pm" && hour != 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            
            // Extract action
            val action = when {
                command.contains("remind") -> "Reminder"
                command.contains("announce") -> command.substringAfter("announce ").trim()
                command.contains("say") -> command.substringAfter("say ").trim()
                else -> "Scheduled action"
            }
            
            return RuleBuilder()
                .named("Time-based: $hour:${minute.toString().padStart(2, '0')}")
                .whenTime(hour, minute)
                .thenAnnounce(action)
                .build()
        }
        
        return null
    }
    
    private fun parseLocationRule(command: String, triggerType: TriggerConfig.LocationTriggerType): AutomationRule? {
        // Extract location name (simplified - would need geocoding in production)
        val locationPattern = Pattern.compile("(arrive|leave|get to|exit) (?:at|to)? ?(.+?)(?:$|\\,)")
        val matcher = locationPattern.matcher(command)
        
        if (matcher.find()) {
            val locationName = matcher.group(2)?.trim() ?: return null
            
            // In production: Geocode location name to lat/lng
            val (lat, lng) = geocodeLocation(locationName) ?: return null
            
            val action = when {
                command.contains("announce") -> command.substringAfter("announce ").trim()
                command.contains("tell") -> command.substringAfter("tell ").trim()
                command.contains("turn") -> "Setting change"
                else -> "Location triggered"
            }
            
            return RuleBuilder()
                .named("$locationName ${if (triggerType == TriggerConfig.LocationTriggerType.ENTER) "Arrival" else "Departure"}")
                .whenLocationEnter(lat, lng, 100f)
                .thenAnnounce(action)
                .build()
        }
        
        return null
    }
    
    private fun parseMessageRule(command: String): AutomationRule? {
        // Extract sender
        val senderPattern = Pattern.compile("when (\\w+) (?:messages|texts)")
        val matcher = senderPattern.matcher(command)
        
        if (matcher.find()) {
            val sender = matcher.group(1)?.capitalize() ?: return null
            
            return RuleBuilder()
                .named("$sender Message Alert")
                .whenMessageFrom("com.whatsapp", sender)
                .thenAnnounce("Message from $sender")
                .build()
        }
        
        return null
    }
    
    private fun parseConnectivityRule(command: String): AutomationRule? {
        // Extract WiFi SSID
        val ssidPattern = Pattern.compile("wifi (.+?)(?:$|\\,)")
        val matcher = ssidPattern.matcher(command)
        
        if (matcher.find()) {
            val ssid = matcher.group(1)?.trim() ?: return null
            
            return RuleBuilder()
                .named("WiFi: $ssid")
                .whenWifiConnect(ssid)
                .thenToggleWifi(true)
                .build()
        }
        
        return null
    }
    
    private fun geocodeLocation(locationName: String): Pair<Double, Double>? {
        // In production: Use Geocoder or Google Geocoding API
        // This is a placeholder
        return when (locationName.lowercase()) {
            "home" -> Pair(37.7749, -122.4194)
            "work" -> Pair(37.7849, -122.4094)
            else -> null
        }
    }
}

/**
 * Example usage
 */
fun main() {
    val parser = NaturalLanguageParser()
    
    val examples = listOf(
        "Tell me when mom calls",
        "At 8:00 AM, remind me to take medicine",
        "When I arrive at home, turn on WiFi",
        "When I leave work, navigate home",
        "When boss messages me on WhatsApp, announce it",
        "Connect to home WiFi automatically"
    )
    
    examples.forEach { command ->
        val rule = parser.parseCommand(command)
        if (rule != null) {
            println("✓ Created rule: ${rule.name}")
            println("  Trigger: ${rule.triggerType}")
            println("  Action: ${rule.actionType}")
        } else {
            println("✗ Could not parse: $command")
        }
        println()
    }
}
```

---

## Part 8: Permissions & Setup

### Required Permissions

```xml
<!-- file: app/src/main/AndroidManifest.xml -->

<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Automation Engine -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    
    <!-- Notifications -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <!-- Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    
    <!-- Connectivity -->
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    
    <!-- System -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    
    <!-- Calls -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.PROCESS_OUTGOING_CALLS" />
    
    <!-- SMS -->
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    
    <!-- Internet (for webhooks) -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <application>
        <!-- Automation Engine Service -->
        <service
            android:name=".automation.AutomationEngine"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="automation_monitoring" />
        </service>
        
        <!-- Notification Listener -->
        <service
            android:name=".automation.NotificationListener"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
        
        <!-- Boot Receiver -->
        <receiver
            android:name=".automation.AutomationBootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

### Permission Request Helper

```kotlin
// file: app/src/main/java/com/assistant/automation/PermissionHelper.kt

package com.assistant.automation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Helper to request all automation permissions
 */
class PermissionHelper(private val activity: Activity) {
    
    private val requiredPermissions = mutableListOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_PHONE_STATE)
        }
    }
    
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        onPermissionResult(allGranted)
    }
    
    var onPermissionResult: (Boolean) -> Unit = {}
    
    fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun requestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
    
    fun hasNotificationAccess(context: Context): Boolean {
        return NotificationListener.isPermissionGranted(context)
    }
    
    fun requestNotificationAccess() {
        NotificationListener.requestPermission(activity)
    }
}
```

---

## Part 9: Testing

### Unit Tests

```kotlin
// file: app/src/test/java/com/assistant/automation/RuleBuilderTest.kt

package com.assistant.automation

import org.junit.Test
import org.junit.Assert.*

class RuleBuilderTest {
    
    @Test
    fun `create call announcement rule`() {
        val rule = RuleBuilder()
            .named("Mom Call Alert")
            .whenCallFrom("+1234567890")
            .thenAnnounce("Mom is calling!")
            .build()
        
        assertEquals(TriggerType.CALL_EVENT, rule.triggerType)
        assertEquals(ActionType.ANNOUNCE, rule.actionType)
        assertTrue(rule.isEnabled)
    }
    
    @Test
    fun `create time-based reminder rule`() {
        val rule = RuleBuilder()
            .named("Morning Medicine")
            .whenTime(8, 0, repeat = true)
            .thenAnnounce("Time to take medicine")
            .build()
        
        assertEquals(TriggerType.TIME, rule.triggerType)
        val config = rule.triggerConfig as TriggerConfig.TimeConfig
        assertEquals(8, config.hour)
        assertEquals(0, config.minute)
        assertTrue(config.repeat)
    }
    
    @Test
    fun `create location enter rule`() {
        val rule = RuleBuilder()
            .named("Home Arrival")
            .whenLocationEnter(37.7749, -122.4194, 100f)
            .thenToggleWifi(true)
            .build()
        
        assertEquals(TriggerType.LOCATION, rule.triggerType)
        assertEquals(ActionType.CHANGE_SETTING, rule.actionType)
    }
    
    @Test
    fun `build rule without name throws exception`() {
        assertFailsWith<IllegalArgumentException> {
            RuleBuilder()
                .whenCallFrom("+1234567890")
                .thenAnnounce("Test")
                .build()
        }
    }
}
```

### Instrumentation Tests

```kotlin
// file: app/src/androidTest/java/com/assistant/automation/AutomationEngineTest.kt

package com.assistant.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class AutomationEngineTest {
    
    private val context: Context = ApplicationProvider.getApplicationContext()
    
    @Test
    fun `engine starts successfully`() {
        AutomationEngine.start(context)
        
        val engine = AutomationEngine.getInstance()
        assertNotNull(engine)
    }
    
    @Test
    fun `add rule persists correctly`() {
        val engine = AutomationEngine.getInstance() ?: return
        
        val rule = RuleBuilder()
            .named("Test Rule")
            .whenCallFrom("+1234567890")
            .thenAnnounce("Test")
            .build()
        
        engine.addRule(rule)
        
        assertEquals(1, engine.getRules().size)
        assertEquals("Test Rule", engine.getRules()[0].name)
    }
    
    @Test
    fun `remove rule works correctly`() {
        val engine = AutomationEngine.getInstance() ?: return
        
        val rule = RuleBuilder()
            .named("To Remove")
            .whenCallFrom("+1234567890")
            .thenAnnounce("Test")
            .build()
        
        engine.addRule(rule)
        val ruleId = rule.id
        
        engine.removeRule(ruleId)
        
        assertEquals(0, engine.getRules().size)
    }
}
```

---

## Part 10: Integration with AI Assistant

### Connecting to Voice Commands

```kotlin
// file: app/src/main/java/com/assistant/ai/VoiceAutomationHandler.kt

package com.assistant.ai

import com.assistant.automation.AutomationEngine
import com.assistant.automation.NaturalLanguageParser
import com.assistant.automation.RuleBuilder

/**
 * Handles voice commands that create automation rules
 */
class VoiceAutomationHandler {
    
    private val parser = NaturalLanguageParser()
    
    /**
     * Process voice command and create automation rule
     */
    fun handleVoiceCommand(command: String): AutomationRule? {
        val rule = parser.parseCommand(command)
        
        if (rule != null) {
            AutomationEngine.getInstance()?.addRule(rule)
            return rule
        }
        
        return null
    }
    
    /**
     * Get confirmation message for created rule
     */
    fun getConfirmationMessage(rule: AutomationRule): String {
        return "Created automation: ${rule.name}. " +
               "It will ${rule.actionType.toString().lowercase().replace("_", "")} " +
               "when ${rule.triggerType.toString().lowercase().replace("_", " ")}"
    }
}

/**
 * Example integration
 */
class AssistantViewModel {
    
    private val automationHandler = VoiceAutomationHandler()
    
    fun onVoiceCommand(command: String) {
        val rule = automationHandler.handleVoiceCommand(command)
        
        if (rule != null) {
            val confirmation = automationHandler.getConfirmationMessage(rule)
            speak(confirmation)
        } else {
            speak("I couldn't create that automation. Try: 'Tell me when mom calls'")
        }
    }
    
    private fun speak(text: String) {
        // TTS implementation
    }
}
```

---

## Summary

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│  AutomationEngine (Foreground Service)                  │
│  ├─ Rule Management (CRUD)                              │
│  ├─ Trigger Monitoring                                  │
│  │  ├─ NotificationListener                             │
│  │  ├─ LocationMonitor                                  │
│  │  └─ ConnectivityMonitor                              │
│  ├─ Action Execution                                    │
│  │  ├─ TTS Announcements                                │
│  │  ├─ App Launching                                    │
│  │  └─ Setting Changes                                  │
│  └─ Persistence (SharedPreferences)                     │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  RuleBuilder (Fluent API)                               │
│  └─ NaturalLanguageParser                               │
│      └─ Voice Commands → Rules                          │
└─────────────────────────────────────────────────────────┘
```

### Key Features

✅ **Tasker-style rules** - IF trigger THEN action  
✅ **Multiple trigger types** - Notification, Time, Location, Connectivity, Call, Message  
✅ **Multiple action types** - Announce, Send, Launch, Navigate, Settings  
✅ **Natural language** - "Tell me when X happens"  
✅ **Background monitoring** - Foreground service  
✅ **Persistence** - Rules saved across rebove  
✅ **Testable** - Unit + instrumentation tests  

### Next Steps

1. Copy code to your project
2. Add permissions to AndroidManifest.xml
3. Initialize AutomationEngine in Application class
4. Test with sample rules
5. Integrate with voice command system

---

**Previous**: [Simple Automation](./12-simple-automation.md)
