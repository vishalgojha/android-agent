package ai.androidassistant.app.automation

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
    val actionConfig: ActionConfig,      // Action details
    val isEnabled: Boolean = true,
    val createdAt: Date = Date(),
    val lastTriggered: Date? = null,
    val triggerCount: Long = 0
) {
    fun copyWithTriggered(timestamp: Date): AutomationRule {
        return this.copy(
            lastTriggered = timestamp,
            triggerCount = this.triggerCount + 1
        )
    }
    
    fun copyWithEnabled(enabled: Boolean): AutomationRule {
        return this.copy(isEnabled = enabled)
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
    
    enum class CallEventType { INCOMING, OUTGOING, MISSED }
    
    data class MessageConfig(
        val appPackage: String,
        val sender: String? = null,
        val containsKeyword: String? = null
    ) : TriggerConfig()
    
    data class VoiceCommandConfig(
        val commandPhrase: String,
        val confidenceThreshold: Float = 0.8f
    ) : TriggerConfig()
    
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
    INVOKE_COMMAND         // Call AndroidAssistant invoke command
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
    
    data class InvokeCommandConfig(
        val command: String,
        val parameters: Map<String, String> = emptyMap()
    ) : ActionConfig()
}

