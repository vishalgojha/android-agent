package ai.androidassistant.app.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared in-process automation engine used by the Android app UI, node runtime, and listeners.
 */
object AutomationEngine : TextToSpeech.OnInitListener {
  private const val TAG = "AutomationEngine"
  private const val PREFS_NAME = "automation_rules"
  private const val PREFS_KEY_RULES = "rules"

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val rules = CopyOnWriteArrayList<AutomationRule>()
  private val rulesFlow = MutableStateFlow<List<AutomationRule>>(emptyList())

  private val locationMonitor = LocationMonitor()
  private val connectivityMonitor = ConnectivityMonitor()
  private val timeScheduler = TimeScheduler()

  @Volatile private var initialized = false
  private lateinit var appContext: Context
  private var tts: TextToSpeech? = null

  @Volatile var invokeCommandCallback: ((String, Map<String, String>) -> Unit)? = null

  fun initialize(context: Context) {
    if (initialized) {
      return
    }
    synchronized(this) {
      if (initialized) {
        return
      }
      appContext = context.applicationContext
      tts = TextToSpeech(appContext, this)
      loadRules()
      startMonitoring()
      initialized = true
      Log.d(TAG, "Automation engine initialized with ${rules.size} rules")
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      tts?.language = Locale.US
    }
  }

  fun getRules(): List<AutomationRule> {
    ensureInitialized()
    return rules.toList()
  }

  fun getRulesFlow(): StateFlow<List<AutomationRule>> {
    ensureInitialized()
    return rulesFlow
  }

  fun addRule(rule: AutomationRule) {
    ensureInitialized()
    rules.add(rule)
    persistAndRefresh("Rule added: ${rule.name}")
  }

  fun removeRule(ruleId: String) {
    ensureInitialized()
    rules.removeAll { it.id == ruleId }
    persistAndRefresh("Rule removed: $ruleId")
  }

  fun updateRule(ruleId: String, updates: AutomationRule.() -> AutomationRule) {
    ensureInitialized()
    val index = rules.indexOfFirst { it.id == ruleId }
    if (index == -1) {
      return
    }
    rules[index] = rules[index].updates()
    persistAndRefresh("Rule updated: $ruleId")
  }

  fun enableRule(ruleId: String) {
    updateRule(ruleId) { copyWithEnabled(true) }
  }

  fun disableRule(ruleId: String) {
    updateRule(ruleId) { copyWithEnabled(false) }
  }

  fun onNotificationPosted(packageName: String, title: String?, text: String?) {
    ensureInitialized()
    rules
      .filter { it.isEnabled && it.triggerType == TriggerType.NOTIFICATION }
      .forEach { rule ->
        val config = rule.triggerConfig as? TriggerConfig.NotificationConfig ?: return@forEach
        if (config.packageName != packageName) {
          return@forEach
        }
        if (config.keyword != null && text?.contains(config.keyword, ignoreCase = true) != true) {
          return@forEach
        }
        if (config.contact != null && title?.contains(config.contact, ignoreCase = true) != true) {
          return@forEach
        }
        onTriggerDetected(
          rule = rule,
          triggerData =
            mapOf(
              "package" to packageName,
              "title" to title,
              "text" to text,
            ),
        )
      }
  }

  fun onCallTrigger(
    phoneNumber: String?,
    contactName: String?,
    eventType: TriggerConfig.CallEventType,
  ) {
    ensureInitialized()
    rules
      .filter { it.isEnabled && it.triggerType == TriggerType.CALL_EVENT }
      .forEach { rule ->
        val config = rule.triggerConfig as? TriggerConfig.CallEventConfig ?: return@forEach
        if (config.eventType != eventType) {
          return@forEach
        }
        if (config.phoneNumber != null && config.phoneNumber != phoneNumber) {
          return@forEach
        }
        if (config.contactName != null && config.contactName != contactName) {
          return@forEach
        }
        onTriggerDetected(
          rule = rule,
          triggerData =
            mapOf(
              "phone" to phoneNumber,
              "contact" to contactName,
              "type" to eventType.name,
            ),
        )
      }
  }

  fun onMessageTrigger(appPackage: String, sender: String?, message: String?) {
    ensureInitialized()
    rules
      .filter { it.isEnabled && it.triggerType == TriggerType.MESSAGE_RECEIVED }
      .forEach { rule ->
        val config = rule.triggerConfig as? TriggerConfig.MessageConfig ?: return@forEach
        if (config.appPackage != appPackage) {
          return@forEach
        }
        if (config.sender != null && config.sender != sender) {
          return@forEach
        }
        if (config.containsKeyword != null && message?.contains(config.containsKeyword, ignoreCase = true) != true) {
          return@forEach
        }
        onTriggerDetected(
          rule = rule,
          triggerData =
            mapOf(
              "package" to appPackage,
              "sender" to sender,
              "message" to message,
            ),
        )
      }
  }

  internal fun onTriggerDetected(rule: AutomationRule, triggerData: Map<String, String?>) {
    ensureInitialized()
    if (!rule.isEnabled) {
      return
    }

    scope.launch {
      try {
        executeAction(rule = rule, triggerData = triggerData)
        markRuleTriggered(rule.id, Date())
        Log.d(TAG, "Triggered automation rule: ${rule.name}")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to execute rule ${rule.name}", e)
      }
    }
  }

  private fun startMonitoring() {
    locationMonitor.start(appContext)
    connectivityMonitor.start(appContext)
    timeScheduler.start(appContext)
    syncMonitors()
  }

  private fun syncMonitors() {
    val activeRules = rules.filter { it.isEnabled }
    locationMonitor.replaceRules(activeRules)
    connectivityMonitor.replaceRules(activeRules)
    timeScheduler.replaceRules(activeRules)
  }

  private suspend fun executeAction(rule: AutomationRule, triggerData: Map<String, String?>) {
    when (val config = rule.actionConfig) {
      is ActionConfig.AnnounceConfig -> executeAnnounce(config)
      is ActionConfig.SendMessageConfig -> executeSendMessage(config)
      is ActionConfig.LaunchAppConfig -> executeLaunchApp(config)
      is ActionConfig.ChangeSettingConfig -> executeChangeSetting(config)
      is ActionConfig.NavigateConfig -> executeNavigate(config)
      is ActionConfig.ReminderConfig -> executeCreateReminder(config)
      is ActionConfig.WebhookConfig -> Log.w(TAG, "Webhook actions are not wired yet: ${config.url}")
      is ActionConfig.InvokeCommandConfig -> executeInvokeCommand(config, triggerData)
    }
  }

  private suspend fun executeAnnounce(config: ActionConfig.AnnounceConfig) {
    withContext(Dispatchers.Main) {
      tts?.setPitch(config.pitch)
      tts?.setSpeechRate(config.speed)
      tts?.speak(config.text, TextToSpeech.QUEUE_FLUSH, null, "automation-announce")
    }
  }

  private suspend fun executeSendMessage(config: ActionConfig.SendMessageConfig) {
    val intent =
      Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${Uri.encode(config.recipient)}")
        putExtra("sms_body", config.message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    launchActivity(intent)
  }

  private suspend fun executeLaunchApp(config: ActionConfig.LaunchAppConfig) {
    val intent =
      appContext.packageManager.getLaunchIntentForPackage(config.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        config.extras.forEach { (key, value) ->
          when (value) {
            is String -> putExtra(key, value)
            is Int -> putExtra(key, value)
            is Boolean -> putExtra(key, value)
          }
        }
      }
    if (intent == null) {
      Log.w(TAG, "No launch intent for package ${config.packageName}")
      return
    }
    launchActivity(intent)
  }

  private fun executeChangeSetting(config: ActionConfig.ChangeSettingConfig) {
    try {
      when (config.settingType) {
        ActionConfig.SettingType.WIFI -> {
          val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
          @Suppress("DEPRECATION")
          wifiManager.isWifiEnabled = config.value
        }
        ActionConfig.SettingType.BLUETOOTH -> {
          val adapter = appContext.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
          if (config.value) {
            @Suppress("DEPRECATION")
            adapter?.enable()
          } else {
            @Suppress("DEPRECATION")
            adapter?.disable()
          }
        }
        else -> Log.w(TAG, "Setting automation not implemented for ${config.settingType}")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to change setting ${config.settingType}", e)
    }
  }

  private suspend fun executeNavigate(config: ActionConfig.NavigateConfig) {
    val destination = Uri.encode(config.destination)
    val uri =
      if (config.latitude != null && config.longitude != null) {
        Uri.parse("geo:${config.latitude},${config.longitude}?q=$destination")
      } else {
        Uri.parse("geo:0,0?q=$destination")
      }
    val intent =
      Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (config.mapApp.isNotBlank()) {
          setPackage(config.mapApp)
        }
      }
    launchActivity(intent)
  }

  private suspend fun executeCreateReminder(config: ActionConfig.ReminderConfig) {
    val calendar =
      Calendar.getInstance().apply {
        timeInMillis = config.time
      }
    val intent =
      Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_MESSAGE, config.title)
        putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
        putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    launchActivity(intent)
  }

  private fun executeInvokeCommand(
    config: ActionConfig.InvokeCommandConfig,
    triggerData: Map<String, String?>,
  ) {
    val mergedParams = config.parameters + triggerData.filterValues { it != null }.mapValues { it.value.orEmpty() }
    invokeCommandCallback?.invoke(config.command, mergedParams)
    Log.d(TAG, "Invoked command ${config.command} with params=$mergedParams")
  }

  private suspend fun launchActivity(intent: Intent) {
    withContext(Dispatchers.Main) {
      runCatching {
        appContext.startActivity(intent)
      }.onFailure { error ->
        Log.e(TAG, "Failed to launch automation intent ${intent.action}", error)
      }
    }
  }

  private fun markRuleTriggered(ruleId: String, timestamp: Date) {
    val index = rules.indexOfFirst { it.id == ruleId }
    if (index == -1) {
      return
    }
    rules[index] = rules[index].copyWithTriggered(timestamp)
    persistAndRefresh()
  }

  private fun persistAndRefresh(logMessage: String? = null) {
    rulesFlow.value = rules.toList()
    saveRules()
    startMonitoring()
    syncMonitors()
    logMessage?.let { Log.d(TAG, it) }
  }

  private fun loadRules() {
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(PREFS_KEY_RULES, null).orEmpty()
    if (raw.isBlank()) {
      rules.clear()
      rulesFlow.value = emptyList()
      return
    }

    val decodedRules =
      runCatching {
        val jsonArray = JSONArray(raw)
        buildList {
          for (index in 0 until jsonArray.length()) {
            val rule = ruleFromJson(jsonArray.getJSONObject(index))
            if (rule != null) {
              add(rule)
            }
          }
        }
      }.getOrElse { error ->
        Log.e(TAG, "Failed to decode stored automation rules", error)
        emptyList()
      }

    rules.clear()
    rules.addAll(decodedRules)
    rulesFlow.value = rules.toList()
  }

  private fun saveRules() {
    if (!::appContext.isInitialized) {
      return
    }
    val encoded =
      JSONArray().apply {
        rules.forEach { put(ruleToJson(it)) }
      }
    appContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(PREFS_KEY_RULES, encoded.toString())
      .apply()
  }

  private fun ruleToJson(rule: AutomationRule): JSONObject {
    return JSONObject()
      .put("id", rule.id)
      .put("name", rule.name)
      .put("triggerType", rule.triggerType.name)
      .put("triggerConfig", triggerConfigToJson(rule.triggerConfig))
      .put("actionType", rule.actionType.name)
      .put("actionConfig", actionConfigToJson(rule.actionConfig))
      .put("isEnabled", rule.isEnabled)
      .put("createdAt", rule.createdAt.time)
      .put("lastTriggered", rule.lastTriggered?.time)
      .put("triggerCount", rule.triggerCount)
  }

  private fun ruleFromJson(json: JSONObject): AutomationRule? {
    val triggerType = json.enumOrNull<TriggerType>("triggerType") ?: return null
    val actionType = json.enumOrNull<ActionType>("actionType") ?: return null
    val triggerConfig = triggerConfigFromJson(triggerType, json.optJSONObject("triggerConfig") ?: return null) ?: return null
    val actionConfig = actionConfigFromJson(actionType, json.optJSONObject("actionConfig") ?: return null) ?: return null

    return AutomationRule(
      id = json.optStringOrNull("id") ?: return null,
      name = json.optStringOrNull("name") ?: return null,
      triggerType = triggerType,
      triggerConfig = triggerConfig,
      actionType = actionType,
      actionConfig = actionConfig,
      isEnabled = json.optBoolean("isEnabled", true),
      createdAt = Date(json.optLong("createdAt", System.currentTimeMillis())),
      lastTriggered = json.optNullableLong("lastTriggered")?.let(::Date),
      triggerCount = json.optLong("triggerCount", 0L),
    )
  }

  private fun triggerConfigToJson(config: TriggerConfig): JSONObject {
    return when (config) {
      is TriggerConfig.NotificationConfig ->
        JSONObject()
          .put("packageName", config.packageName)
          .put("keyword", config.keyword)
          .put("contact", config.contact)
      is TriggerConfig.TimeConfig ->
        JSONObject()
          .put("hour", config.hour)
          .put("minute", config.minute)
          .put("daysOfWeek", JSONArray(config.daysOfWeek.toList()))
          .put("repeat", config.repeat)
      is TriggerConfig.LocationConfig ->
        JSONObject()
          .put("latitude", config.latitude)
          .put("longitude", config.longitude)
          .put("radiusMeters", config.radiusMeters.toDouble())
          .put("triggerType", config.triggerType.name)
      is TriggerConfig.ConnectivityConfig ->
        JSONObject()
          .put("connectivityType", config.connectivityType.name)
          .put("ssid", config.ssid)
          .put("macAddress", config.macAddress)
          .put("triggerType", config.triggerType.name)
      is TriggerConfig.CallEventConfig ->
        JSONObject()
          .put("eventType", config.eventType.name)
          .put("phoneNumber", config.phoneNumber)
          .put("contactName", config.contactName)
      is TriggerConfig.MessageConfig ->
        JSONObject()
          .put("appPackage", config.appPackage)
          .put("sender", config.sender)
          .put("containsKeyword", config.containsKeyword)
      is TriggerConfig.VoiceCommandConfig ->
        JSONObject()
          .put("commandPhrase", config.commandPhrase)
          .put("confidenceThreshold", config.confidenceThreshold.toDouble())
      is TriggerConfig.AppConfig ->
        JSONObject()
          .put("packageName", config.packageName)
          .put("eventType", config.eventType.name)
    }
  }

  private fun triggerConfigFromJson(type: TriggerType, json: JSONObject): TriggerConfig? {
    return when (type) {
      TriggerType.NOTIFICATION ->
        TriggerConfig.NotificationConfig(
          packageName = json.optStringOrNull("packageName") ?: return null,
          keyword = json.optStringOrNull("keyword"),
          contact = json.optStringOrNull("contact"),
        )
      TriggerType.TIME ->
        TriggerConfig.TimeConfig(
          hour = json.optInt("hour"),
          minute = json.optInt("minute"),
          daysOfWeek = json.optIntSet("daysOfWeek"),
          repeat = json.optBoolean("repeat", true),
        )
      TriggerType.LOCATION ->
        TriggerConfig.LocationConfig(
          latitude = json.optNullableDouble("latitude") ?: return null,
          longitude = json.optNullableDouble("longitude") ?: return null,
          radiusMeters = (json.optNullableDouble("radiusMeters") ?: return null).toFloat(),
          triggerType = json.enumOrNull<TriggerConfig.LocationTriggerType>("triggerType") ?: return null,
        )
      TriggerType.CONNECTIVITY ->
        TriggerConfig.ConnectivityConfig(
          connectivityType = json.enumOrNull<TriggerConfig.ConnectivityType>("connectivityType") ?: return null,
          ssid = json.optStringOrNull("ssid"),
          macAddress = json.optStringOrNull("macAddress"),
          triggerType = json.enumOrNull<TriggerConfig.ConnectivityTriggerType>("triggerType") ?: return null,
        )
      TriggerType.CALL_EVENT ->
        TriggerConfig.CallEventConfig(
          eventType = json.enumOrNull<TriggerConfig.CallEventType>("eventType") ?: return null,
          phoneNumber = json.optStringOrNull("phoneNumber"),
          contactName = json.optStringOrNull("contactName"),
        )
      TriggerType.MESSAGE_RECEIVED ->
        TriggerConfig.MessageConfig(
          appPackage = json.optStringOrNull("appPackage") ?: return null,
          sender = json.optStringOrNull("sender"),
          containsKeyword = json.optStringOrNull("containsKeyword"),
        )
      TriggerType.VOICE_COMMAND ->
        TriggerConfig.VoiceCommandConfig(
          commandPhrase = json.optStringOrNull("commandPhrase") ?: return null,
          confidenceThreshold = json.optDouble("confidenceThreshold", 0.8).toFloat(),
        )
      TriggerType.APP_EVENT ->
        TriggerConfig.AppConfig(
          packageName = json.optStringOrNull("packageName") ?: return null,
          eventType = json.enumOrNull<TriggerConfig.AppEventType>("eventType") ?: return null,
        )
    }
  }

  private fun actionConfigToJson(config: ActionConfig): JSONObject {
    return when (config) {
      is ActionConfig.AnnounceConfig ->
        JSONObject()
          .put("text", config.text)
          .put("language", config.language)
          .put("pitch", config.pitch.toDouble())
          .put("speed", config.speed.toDouble())
      is ActionConfig.SendMessageConfig ->
        JSONObject()
          .put("appPackage", config.appPackage)
          .put("recipient", config.recipient)
          .put("message", config.message)
          .put("sendImmediately", config.sendImmediately)
      is ActionConfig.LaunchAppConfig ->
        JSONObject()
          .put("packageName", config.packageName)
          .put("activityClass", config.activityClass)
          .put("extras", JSONObject(config.extras))
      is ActionConfig.ChangeSettingConfig ->
        JSONObject()
          .put("settingType", config.settingType.name)
          .put("value", config.value)
          .put("wifiSsid", config.wifiSsid)
          .put("brightnessLevel", config.brightnessLevel)
      is ActionConfig.NavigateConfig ->
        JSONObject()
          .put("destination", config.destination)
          .put("latitude", config.latitude)
          .put("longitude", config.longitude)
          .put("mapApp", config.mapApp)
      is ActionConfig.ReminderConfig ->
        JSONObject()
          .put("title", config.title)
          .put("description", config.description)
          .put("time", config.time)
          .put("alarmType", config.alarmType.name)
      is ActionConfig.WebhookConfig ->
        JSONObject()
          .put("url", config.url)
          .put("method", config.method)
          .put("headers", JSONObject(config.headers))
          .put("body", config.body)
      is ActionConfig.InvokeCommandConfig ->
        JSONObject()
          .put("command", config.command)
          .put("parameters", JSONObject(config.parameters))
    }
  }

  private fun actionConfigFromJson(type: ActionType, json: JSONObject): ActionConfig? {
    return when (type) {
      ActionType.ANNOUNCE ->
        ActionConfig.AnnounceConfig(
          text = json.optStringOrNull("text") ?: return null,
          language = json.optStringOrNull("language") ?: "en-US",
          pitch = json.optDouble("pitch", 1.0).toFloat(),
          speed = json.optDouble("speed", 1.0).toFloat(),
        )
      ActionType.SEND_MESSAGE ->
        ActionConfig.SendMessageConfig(
          appPackage = json.optStringOrNull("appPackage") ?: return null,
          recipient = json.optStringOrNull("recipient") ?: return null,
          message = json.optStringOrNull("message") ?: return null,
          sendImmediately = json.optBoolean("sendImmediately", false),
        )
      ActionType.LAUNCH_APP ->
        ActionConfig.LaunchAppConfig(
          packageName = json.optStringOrNull("packageName") ?: return null,
          activityClass = json.optStringOrNull("activityClass"),
          extras = json.optJSONObject("extras").toAnyMap(),
        )
      ActionType.CHANGE_SETTING ->
        ActionConfig.ChangeSettingConfig(
          settingType = json.enumOrNull<ActionConfig.SettingType>("settingType") ?: return null,
          value = json.optBoolean("value"),
          wifiSsid = json.optStringOrNull("wifiSsid"),
          brightnessLevel = json.optNullableInt("brightnessLevel"),
        )
      ActionType.NAVIGATE ->
        ActionConfig.NavigateConfig(
          destination = json.optStringOrNull("destination") ?: return null,
          latitude = json.optNullableDouble("latitude"),
          longitude = json.optNullableDouble("longitude"),
          mapApp = json.optStringOrNull("mapApp") ?: "com.google.android.apps.maps",
        )
      ActionType.CREATE_REMINDER ->
        ActionConfig.ReminderConfig(
          title = json.optStringOrNull("title") ?: return null,
          description = json.optStringOrNull("description"),
          time = json.optLong("time"),
          alarmType = json.enumOrNull<ActionConfig.AlarmType>("alarmType") ?: return null,
        )
      ActionType.LOG_EVENT -> null
      ActionType.TRIGGER_WEBHOOK ->
        ActionConfig.WebhookConfig(
          url = json.optStringOrNull("url") ?: return null,
          method = json.optStringOrNull("method") ?: "POST",
          headers = json.optJSONObject("headers").toStringMap(),
          body = json.optStringOrNull("body"),
        )
      ActionType.INVOKE_COMMAND ->
        ActionConfig.InvokeCommandConfig(
          command = json.optStringOrNull("command") ?: return null,
          parameters = json.optJSONObject("parameters").toStringMap(),
        )
    }
  }

  private fun ensureInitialized() {
    check(initialized) { "AutomationEngine.initialize(context) must be called first" }
  }
}

private inline fun <reified T : Enum<T>> JSONObject.enumOrNull(key: String): T? {
  val raw = optStringOrNull(key) ?: return null
  return enumValues<T>().firstOrNull { it.name == raw }
}

private fun JSONObject.optStringOrNull(key: String): String? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optString(key).takeIf { it.isNotBlank() }
}

private fun JSONObject.optNullableLong(key: String): Long? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optLong(key)
}

private fun JSONObject.optNullableInt(key: String): Int? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optInt(key)
}

private fun JSONObject.optNullableDouble(key: String): Double? {
  if (!has(key) || isNull(key)) {
    return null
  }
  return optDouble(key)
}

private fun JSONObject.optIntSet(key: String): Set<Int> {
  val values = optJSONArray(key) ?: return emptySet()
  return buildSet {
    for (index in 0 until values.length()) {
      add(values.optInt(index))
    }
  }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
  if (this == null) {
    return emptyMap()
  }
  val result = linkedMapOf<String, String>()
  keys().forEach { key ->
    if (!isNull(key)) {
      result[key] = optString(key)
    }
  }
  return result
}

private fun JSONObject?.toAnyMap(): Map<String, Any?> {
  if (this == null) {
    return emptyMap()
  }
  val result = linkedMapOf<String, Any?>()
  keys().forEach { key ->
    result[key] =
      when (val value = opt(key)) {
        JSONObject.NULL -> null
        is Boolean -> value
        is Int -> value
        is Long -> value.toInt()
        is String -> value
        else -> value?.toString()
      }
  }
  return result
}

