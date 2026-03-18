# 6. Battery Optimization

## 6.1 Power Management Service

```kotlin
// services/PowerManagerService.kt
package com.personalassistant.services

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.*
import com.personalassistant.data.preferences.AssistantPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerManagerService @Inject constructor(
    @Inject @ApplicationContext private val context: Context,
    private val preferences: AssistantPreferences
) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock: PowerManager.WakeLock

    private val _batteryState = MutableStateFlow(BatteryState.NORMAL)
    val batteryState: StateFlow<BatteryState> = _batteryState

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PersonalAssistant::WakeLock"
        )
        wakeLock.setReferenceCounted(false)
        
        monitorBattery()
    }

    private fun monitorBattery() {
        scope.launch {
            // Monitor battery state periodically
            while (true) {
                updateBatteryState()
                kotlinx.coroutines.delay(60000) // Check every minute
            }
        }
    }

    private fun updateBatteryState() {
        val batteryLevel = getBatteryLevel()
        val isCharging = isCharging()
        val isPowerSaveMode = powerManager.isPowerSaveMode

        _batteryState.value = when {
            isCharging -> BatteryState.CHARGING
            batteryLevel < 15 -> BatteryState.CRITICAL
            isPowerSaveMode -> BatteryState.POWER_SAVE
            else -> BatteryState.NORMAL
        }

        adjustBehavior(_batteryState.value)
    }

    private fun getBatteryLevel(): Int {
        // Implementation using BatteryManager
        return 80 // Placeholder
    }

    private fun isCharging(): Boolean {
        // Implementation using BatteryManager
        return false // Placeholder
    }

    private fun adjustBehavior(state: BatteryState) {
        when (state) {
            BatteryState.CRITICAL -> {
                // Minimize all operations
                preferences.useCloudAI = false
                disableListening()
                reduceSyncFrequency()
            }
            BatteryState.POWER_SAVE -> {
                // Reduce background activity
                reduceSyncFrequency()
                disableListening()
            }
            BatteryState.CHARGING -> {
                // Full functionality
                preferences.useCloudAI = true
                enableListening()
                increaseSyncFrequency()
            }
            BatteryState.NORMAL -> {
                // Normal operation
                enableListening()
            }
        }
    }

    fun acquireWakeLock(timeout: Long = 600000) { // 10 minutes default
        if (!wakeLock.isHeld) {
            try {
                wakeLock.acquire(timeout)
                Log.d(TAG, "WakeLock acquired")
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring WakeLock", e)
            }
        }
    }

    fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "WakeLock released")
        }
    }

    private fun disableListening() {
        // Stop speech recognition
        // Reduce microphone usage
    }

    private fun enableListening() {
        // Resume speech recognition
    }

    private fun reduceSyncFrequency() {
        // Reduce API call frequency
        // Increase cache usage
    }

    private fun increaseSyncFrequency() {
        // Normal API call frequency
    }

    companion object {
        private const val TAG = "PowerManagerService"
    }
}

sealed class BatteryState {
    object NORMAL : BatteryState()
    object POWER_SAVE : BatteryState()
    object CRITICAL : BatteryState()
    object CHARGING : BatteryState()
}
```

## 6.2 WorkManager for Background Tasks

```kotlin
// workers/AssistantWorker.kt
package com.personalassistant.workers

import android.content.Context
import androidx.work.*
import com.personalassistant.data.repository.AssistantRepository
import com.personalassistant.services.PowerManagerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AssistantWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var repository: AssistantRepository

    @Inject
    lateinit var powerManager: PowerManagerService

    override suspend fun doWork(): Result {
        return try {
            when (inputData.getString(ACTION_KEY)) {
                ACTION_SYNC -> syncData()
                ACTION_CLEANUP -> cleanupData()
                ACTION_PREFETCH -> prefetchData()
                else -> Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncData(): Result {
        // Sync conversation history
        // Only run when battery is sufficient
        return if (powerManager.batteryState.value !in listOf(
            BatteryState.CRITICAL,
            BatteryState.POWER_SAVE
        )) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private suspend fun cleanupData(): Result {
        // Delete old conversations
        repository.clearOldConversations()
        return Result.success()
    }

    private suspend fun prefetchData(): Result {
        // Prefetch common responses
        // Cache frequently used data
        return Result.success()
    }

    companion object {
        const val ACTION_KEY = "action"
        const val ACTION_SYNC = "sync"
        const val ACTION_CLEANUP = "cleanup"
        const val ACTION_PREFETCH = "prefetch"

        fun scheduleSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<AssistantWorker>()
                .setInputData(workDataOf(ACTION_KEY to ACTION_SYNC))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresCharging(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        fun scheduleCleanup(context: Context) {
            val request = PeriodicWorkRequestBuilder<AssistantWorker>(
                7, TimeUnit.DAYS
            )
                .setInputData(workDataOf(ACTION_KEY to ACTION_CLEANUP))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        fun schedulePrefetch(context: Context) {
            val request = PeriodicWorkRequestBuilder<AssistantWorker>(
                1, TimeUnit.HOURS
            )
                .setInputData(workDataOf(ACTION_KEY to ACTION_PREFETCH))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
```

## 6.3 Doze Mode Compatibility

```kotlin
// util/DozeModeHandler.kt
package com.personalassistant.util

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DozeModeHandler @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun isDeviceIdle(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(): Boolean {
        // This should be triggered from UI with user consent
        return false
    }

    fun scheduleIdleJob(runnable: Runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
            
            val jobInfo = android.app.job.JobInfo.Builder(
                JOB_ID,
                android.content.ComponentName(context, IdleJobService::class.java)
            )
                .setRequiresDeviceIdle(true)
                .setPersisted(true)
                .build()

            jobScheduler.schedule(jobInfo)
        }
    }

    companion object {
        private const val TAG = "DozeModeHandler"
        private const val JOB_ID = 1001
    }
}

// services/IdleJobService.kt
package com.personalassistant.services

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

class IdleJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Running in idle mode")
        
        // Perform low-priority tasks here
        // This runs when device is idle
        
        return false // Job is complete
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Idle job stopped")
        return true // Reschedule
    }

    companion object {
        private const val TAG = "IdleJobService"
    }
}
```

## 6.4 Network Optimization

```kotlin
// util/NetworkOptimizer.kt
package com.personalassistant.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkOptimizer @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkQuality = MutableStateFlow(NetworkQuality.UNKNOWN)
    val networkQuality: StateFlow<NetworkQuality> = _networkQuality

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetworkQuality(network)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    updateNetworkQuality(network)
                }

                override fun onLost(network: Network) {
                    _networkQuality.value = NetworkQuality.NONE
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        }
    }

    private fun updateNetworkQuality(network: Network?) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        _networkQuality.value = when {
            capabilities == null -> NetworkQuality.UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkQuality.EXCELLENT
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                when {
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NR) -> NetworkQuality.EXCELLENT // 5G
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_LTE) -> NetworkQuality.GOOD // 4G
                    else -> NetworkQuality.FAIR
                }
            }
            else -> NetworkQuality.FAIR
        }
    }

    fun shouldUseCloudAI(): Boolean {
        return when (_networkQuality.value) {
            NetworkQuality.EXCELLENT, NetworkQuality.GOOD -> true
            NetworkQuality.FAIR -> false // Use cache
            NetworkQuality.NONE, NetworkQuality.UNKNOWN -> false
        }
    }

    fun getEstimatedLatency(): Long {
        return when (_networkQuality.value) {
            NetworkQuality.EXCELLENT -> 50
            NetworkQuality.GOOD -> 150
            NetworkQuality.FAIR -> 500
            NetworkQuality.NONE -> -1
            NetworkQuality.UNKNOWN -> 300
        }
    }

    fun unregisterCallback() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }
}

enum class NetworkQuality {
    UNKNOWN,
    NONE,
    FAIR,
    GOOD,
    EXCELLENT
}
```

## 6.5 Response Caching Strategy

```kotlin
// util/ResponseCacheManager.kt
package com.personalassistant.util

import android.content.Context
import android.util.LruCache
import com.personalassistant.model.AssistantResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseCacheManager @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private val memoryCache = LruCache<String, AssistantResponse>(50)
    private val diskCache = context.getCacheDir()

    fun get(normalizedPrompt: String): AssistantResponse? {
        // Check memory first
        memoryCache.get(normalizedPrompt)?.let {
            return it
        }

        // Check disk cache
        val diskFile = getDiskCacheFile(normalizedPrompt)
        if (diskFile.exists() && !isExpired(diskFile)) {
            val response = readFromDisk(diskFile)
            memoryCache.put(normalizedPrompt, response)
            return response
        }

        return null
    }

    fun put(normalizedPrompt: String, response: AssistantResponse) {
        memoryCache.put(normalizedPrompt, response)
        writeToDisk(normalizedPrompt, response)
    }

    fun contains(normalizedPrompt: String): Boolean {
        return memoryCache.get(normalizedPrompt) != null ||
                getDiskCacheFile(normalizedPrompt).exists()
    }

    fun clear() {
        memoryCache.evictAll()
        diskCache.listFiles()?.forEach { it.delete() }
    }

    fun normalizePrompt(prompt: String): String {
        return prompt.lowercase().trim()
    }

    private fun getDiskCacheFile(key: String): java.io.File {
        val fileName = java.net.URLEncoder.encode(key, "UTF-8")
        return java.io.File(diskCache, "cache_$fileName")
    }

    private fun isExpired(file: java.io.File): Boolean {
        val age = System.currentTimeMillis() - file.lastModified()
        return age > CACHE_TTL // 1 hour default
    }

    private fun readFromDisk(file: java.io.File): AssistantResponse {
        // Read and deserialize
        return AssistantResponse("Cached response", com.personalassistant.model.CommandType.INFO)
    }

    private fun writeToDisk(key: String, response: AssistantResponse) {
        val file = getDiskCacheFile(key)
        file.writeText(response.message) // Simplified
    }

    companion object {
        private const val CACHE_TTL = 60 * 60 * 1000L // 1 hour
    }
}
```

## 6.6 Battery Stats Monitoring

```kotlin
// util/BatteryStatsTracker.kt
package com.personalassistant.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryStatsTracker @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private val _batteryStats = MutableStateFlow(BatteryStats())
    val batteryStats: StateFlow<BatteryStats> = _batteryStats

    private var currentLevel = 0
    private var scale = 0

    init {
        context.registerReceiver(
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    
                    if (level != -1 && scale != -1) {
                        currentLevel = level
                        val percentage = (level.toFloat() / scale.toFloat()) * 100
                        
                        _batteryStats.value = BatteryStats(
                            level = level,
                            scale = scale,
                            percentage = percentage.toInt(),
                            isCharging = intent.getIntExtra(
                                BatteryManager.EXTRA_STATUS,
                                -1
                            ) == BatteryManager.BATTERY_STATUS_CHARGING,
                            temperature = getTemperature(),
                            voltage = getVoltage()
                        )
                    }
                }
            },
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }

    private fun getTemperature(): Int {
        // Read from battery stats
        return 350 // Placeholder (tenths of degree)
    }

    private fun getVoltage(): Int {
        // Read from battery stats
        return 3800 // Placeholder (millivolts)
    }

    fun getEstimatedTimeRemaining(): Long {
        // Calculate based on usage patterns
        return when {
            _batteryStats.value.percentage < 20 -> 30 * 60 * 1000L // 30 minutes
            _batteryStats.value.percentage < 50 -> 2 * 60 * 60 * 1000L // 2 hours
            else -> 4 * 60 * 60 * 1000L // 4 hours
        }
    }

    fun isBatteryHealthy(): Boolean {
        val temp = _batteryStats.value.temperature
        return temp < 450 && temp > 100 // Healthy range
    }

    data class BatteryStats(
        val level: Int = 0,
        val scale: Int = 0,
        val percentage: Int = 0,
        val isCharging: Boolean = false,
        val temperature: Int = 0,
        val voltage: Int = 0
    )
}
```

## 6.7 Energy-Efficient AI Settings

```kotlin
// data/preferences/EnergyPreferences.kt
package com.personalassistant.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnergyPreferences @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.dataStore

    var maxTokensPerRequest: Int
        get() = getIntPreference(MAX_TOKENS_KEY, 150)
        set(value) = saveIntPreference(MAX_TOKENS_KEY, value)

    var requestTimeoutSeconds: Int
        get() = getIntPreference(TIMEOUT_KEY, 30)
        set(value) = saveIntPreference(TIMEOUT_KEY, value)

    var cacheEnabled: Boolean
        get() = getBooleanPreference(CACHE_ENABLED_KEY, true)
        set(value) = saveBooleanPreference(CACHE_ENABLED_KEY, value)

    var prefetchEnabled: Boolean
        get() = getBooleanPreference(PREFETCH_KEY, false)
        set(value) = saveBooleanPreference(PREFETCH_KEY, value)

    var lowPowerMode: Boolean
        get() = getBooleanPreference(LOW_POWER_KEY, false)
        set(value) = saveBooleanPreference(LOW_POWER_KEY, value)

    private suspend fun getIntPreference(key: androidx.datastore.preferences.core.Preferences.Key<Int>, default: Int): Int {
        return dataStore.data.map { preferences ->
            preferences[key] ?: default
        }.first()
    }

    private suspend fun saveIntPreference(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private suspend fun getBooleanPreference(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, default: Boolean): Boolean {
        return dataStore.data.map { preferences ->
            preferences[key] ?: default
        }.first()
    }

    private suspend fun saveBooleanPreference(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    companion object {
        val MAX_TOKENS_KEY = intPreferencesKey("max_tokens")
        val TIMEOUT_KEY = intPreferencesKey("request_timeout")
        val CACHE_ENABLED_KEY = intPreferencesKey("cache_enabled")
        val PREFETCH_KEY = intPreferencesKey("prefetch_enabled")
        val LOW_POWER_KEY = intPreferencesKey("low_power_mode")
    }
}
```

## 6.8 Battery Optimization Settings UI

```kotlin
// ui/settings/BatterySettingsScreen.kt
package com.personalassistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.data.preferences.EnergyPreferences
import com.personalassistant.services.PowerManagerService

@Composable
fun BatterySettingsScreen(
    energyPreferences: EnergyPreferences,
    powerManager: PowerManagerService
) {
    var maxTokens by remember { mutableIntStateOf(energyPreferences.maxTokensPerRequest) }
    var cacheEnabled by remember { mutableStateOf(energyPreferences.cacheEnabled) }
    var lowPowerMode by remember { mutableStateOf(energyPreferences.lowPowerMode) }
    var prefetchEnabled by remember { mutableStateOf(energyPreferences.prefetchEnabled) }

    val batteryState by powerManager.batteryState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Battery Optimization",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        BatteryStateIndicator(batteryState)

        Spacer(modifier = Modifier.height(24.dp))

        SliderWithLabel(
            label = "Max Tokens per Request",
            value = maxTokens.toFloat(),
            onValueChange = { 
                maxTokens = it.toInt()
                energyPreferences.maxTokensPerRequest = maxTokens
            },
            valueRange = 50f..500f
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingToggle(
            title = "Enable Response Caching",
            subtitle = "Reduces API calls by caching responses",
            checked = cacheEnabled,
            onCheckedChange = { 
                cacheEnabled = it
                energyPreferences.cacheEnabled = it
            }
        )

        SettingToggle(
            title = "Low Power Mode",
            subtitle = "Minimize background activity",
            checked = lowPowerMode,
            onCheckedChange = { 
                lowPowerMode = it
                energyPreferences.lowPowerMode = it
            }
        )

        SettingToggle(
            title = "Prefetch Responses",
            subtitle = "Pre-load common responses (uses more battery)",
            checked = prefetchEnabled,
            onCheckedChange = { 
                prefetchEnabled = it
                energyPreferences.prefetchEnabled = it
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Current State: ${batteryStateToString(batteryState)}",
            style = MaterialTheme.typography.bodySmall,
            color = when (batteryState) {
                is BatteryState.CRITICAL -> MaterialTheme.colorScheme.error
                is BatteryState.POWER_SAVE -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun BatteryStateIndicator(state: BatteryState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is BatteryState.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                is BatteryState.POWER_SAVE -> MaterialTheme.colorScheme.tertiaryContainer
                is BatteryState.CHARGING -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(
            text = when (state) {
                is BatteryState.CRITICAL -> "⚠️ Critical Battery - Limited Functionality"
                is BatteryState.POWER_SAVE -> "🔋 Power Save Mode Active"
                is BatteryState.CHARGING -> "⚡ Charging - Full Functionality"
                else -> "✓ Normal Operation"
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text("${value.toInt()} tokens", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun batteryStateToString(state: BatteryState): String {
    return when (state) {
        is BatteryState.NORMAL -> "Normal"
        is BatteryState.POWER_SAVE -> "Power Save"
        is BatteryState.CRITICAL -> "Critical"
        is BatteryState.CHARGING -> "Charging"
    }
}
```

---

**Next**: [UI/UX Implementation](./07-ui-ux.md)
