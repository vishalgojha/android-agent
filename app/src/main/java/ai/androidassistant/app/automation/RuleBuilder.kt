package ai.androidassistant.app.automation

import java.util.Calendar

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
    
    fun thenInvokeCommand(command: String, parameters: Map<String, String> = emptyMap()) = apply {
        actionType = ActionType.INVOKE_COMMAND
        actionConfig = ActionConfig.InvokeCommandConfig(command, parameters)
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
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            ))
            .thenNavigate("Work Office")
            .build(),
        
        // Rule 4: Announce WhatsApp from boss
        RuleBuilder()
            .named("Boss Message Alert")
            .whenMessageFrom("com.whatsapp", "Boss")
            .thenAnnounce("Message from your boss")
            .build(),
        
        // Rule 5: Invoke AndroidAssistant command on notification
        RuleBuilder()
            .named("Read WhatsApp Messages")
            .whenNotification("com.whatsapp", null, null)
            .thenInvokeCommand("notifications.list", mapOf("limit" to "5"))
            .build()
    )
}

