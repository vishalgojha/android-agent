@file:Suppress("DEPRECATION")

package ai.androidassistant.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.androidassistant.app.propai.PropAiLicenseStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

class SecurePrefs(context: Context) {
  companion object {
    val defaultWakeWords: List<String> = listOf("propai", "hey propai", "okay propai")
    private const val displayNameKey = "node.displayName"
    private const val locationModeKey = "location.enabledMode"
    private const val voiceWakeModeKey = "voiceWake.mode"
    private const val chatListeningPackagesKey = "notifications.chatListening.packages"
    private const val chatListeningConversationFiltersKey = "notifications.chatListening.conversations"
    private const val elevenLabsApiKeyKey = "voice.elevenlabs.apiKey"
    private const val elevenLabsVoiceIdKey = "voice.elevenlabs.voiceId"
    private const val llmModeKey = "llm.mode"
    private const val llmProviderKey = "llm.provider"
    private const val llmOpenAiApiKeyKey = "llm.openai.apiKey"
    private const val llmAnthropicApiKeyKey = "llm.anthropic.apiKey"
    private const val llmGroqApiKeyKey = "llm.groq.apiKey"
    private const val llmOpenRouterApiKeyKey = "llm.openrouter.apiKey"
    private const val llmElevenLabsAgentIdKey = "llm.elevenlabs.agentId"
    private const val llmOpenAiModelKey = "llm.openai.model"
    private const val llmAnthropicModelKey = "llm.anthropic.model"
    private const val llmGroqModelKey = "llm.groq.model"
    private const val llmOpenRouterModelKey = "llm.openrouter.model"
    private const val llmElevenLabsModelKey = "llm.elevenlabs.model"
    private const val propAiControlBaseUrlKey = "propai.control.baseUrl"
    private const val propAiControlTokenKey = "propai.control.token"
    private const val propAiControlEmailKey = "propai.control.email"
    private const val propAiControlUserIdKey = "propai.control.userId"
    private const val propAiControlTenantIdKey = "propai.control.tenantId"
    private const val propAiControlTenantNameKey = "propai.control.tenantName"
    private const val propAiControlTenantRoleKey = "propai.control.tenantRole"
    private const val propAiLicenseBaseUrlKey = "propai.license.baseUrl"
    private const val propAiLicenseActivationKeyKey = "propai.license.activationKey"
    private const val propAiLicenseActivationTokenKey = "propai.license.activationToken"
    private const val propAiLicenseValidKey = "propai.license.valid"
    private const val propAiLicenseStatusKey = "propai.license.status"
    private const val propAiLicensePlanKey = "propai.license.plan"
    private const val propAiLicenseEntitlementsKey = "propai.license.entitlements"
    private const val propAiLicenseExpiresAtKey = "propai.license.expiresAt"
    private const val propAiLicenseGraceUntilKey = "propai.license.graceUntil"
    private const val propAiLicenseRefreshAtKey = "propai.license.refreshAt"
    private const val propAiLicenseDeviceLimitKey = "propai.license.deviceLimit"
    private const val propAiLicenseDevicesUsedKey = "propai.license.devicesUsed"
    private const val propAiLicenseMessageKey = "propai.license.message"
    private const val propAiLicenseCodeKey = "propai.license.code"
    private const val propAiLicenseLastValidatedKey = "propai.license.lastValidatedAt"
    private const val onboardingWelcomeSentKey = "onboarding.welcomeSent"
    private const val plainPrefsName = "androidassistant.node"
    private const val securePrefsName = "androidassistant.node.secure"
    private const val defaultPropAiControlBaseUrl = "https://propai-control.up.railway.app"
    private const val defaultPropAiLicenseBaseUrl = "https://propailicense.up.railway.app"
  }

  private val appContext = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true }
  private val plainPrefs: SharedPreferences =
    appContext.getSharedPreferences(plainPrefsName, Context.MODE_PRIVATE)

  private val masterKey by lazy {
    MasterKey.Builder(appContext)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
  }
  private val securePrefs: SharedPreferences by lazy { createSecurePrefs(appContext, securePrefsName) }

  private val _instanceId = MutableStateFlow(loadOrCreateInstanceId())
  val instanceId: StateFlow<String> = _instanceId

  private val _displayName =
    MutableStateFlow(loadOrMigrateDisplayName(context = context))
  val displayName: StateFlow<String> = _displayName

  private val _cameraEnabled = MutableStateFlow(plainPrefs.getBoolean("camera.enabled", true))
  val cameraEnabled: StateFlow<Boolean> = _cameraEnabled

  private val _locationMode = MutableStateFlow(loadLocationMode())
  val locationMode: StateFlow<LocationMode> = _locationMode

  private val _locationPreciseEnabled =
    MutableStateFlow(plainPrefs.getBoolean("location.preciseEnabled", true))
  val locationPreciseEnabled: StateFlow<Boolean> = _locationPreciseEnabled

  private val _preventSleep = MutableStateFlow(plainPrefs.getBoolean("screen.preventSleep", true))
  val preventSleep: StateFlow<Boolean> = _preventSleep

  private val _manualEnabled =
    MutableStateFlow(plainPrefs.getBoolean("gateway.manual.enabled", false))
  val manualEnabled: StateFlow<Boolean> = _manualEnabled

  private val _manualHost =
    MutableStateFlow(plainPrefs.getString("gateway.manual.host", "") ?: "")
  val manualHost: StateFlow<String> = _manualHost

  private val _manualPort =
    MutableStateFlow(plainPrefs.getInt("gateway.manual.port", 18789))
  val manualPort: StateFlow<Int> = _manualPort

  private val _manualTls =
    MutableStateFlow(plainPrefs.getBoolean("gateway.manual.tls", true))
  val manualTls: StateFlow<Boolean> = _manualTls

  private val _gatewayToken = MutableStateFlow("")
  val gatewayToken: StateFlow<String> = _gatewayToken

  private val _onboardingCompleted =
    MutableStateFlow(plainPrefs.getBoolean("onboarding.completed", false))
  val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted

  private val _welcomeMessageSent =
    MutableStateFlow(plainPrefs.getBoolean(onboardingWelcomeSentKey, false))
  val welcomeMessageSent: StateFlow<Boolean> = _welcomeMessageSent

  private val _lastDiscoveredStableId =
    MutableStateFlow(
      plainPrefs.getString("gateway.lastDiscoveredStableID", "") ?: "",
    )
  val lastDiscoveredStableId: StateFlow<String> = _lastDiscoveredStableId

  private val _canvasDebugStatusEnabled =
    MutableStateFlow(plainPrefs.getBoolean("canvas.debugStatusEnabled", false))
  val canvasDebugStatusEnabled: StateFlow<Boolean> = _canvasDebugStatusEnabled

  private val _wakeWords = MutableStateFlow(loadWakeWords())
  val wakeWords: StateFlow<List<String>> = _wakeWords

  private val _voiceWakeMode = MutableStateFlow(loadVoiceWakeMode())
  val voiceWakeMode: StateFlow<VoiceWakeMode> = _voiceWakeMode

  private val _chatListeningPackages = MutableStateFlow(loadChatListeningPackages())
  val chatListeningPackages: StateFlow<List<String>> = _chatListeningPackages

  private val _chatListeningConversationFilters =
    MutableStateFlow(loadChatListeningConversationFilters())
  val chatListeningConversationFilters: StateFlow<List<String>> = _chatListeningConversationFilters

  private val _talkEnabled = MutableStateFlow(plainPrefs.getBoolean("talk.enabled", false))
  val talkEnabled: StateFlow<Boolean> = _talkEnabled

  private val _speakerEnabled = MutableStateFlow(plainPrefs.getBoolean("voice.speakerEnabled", false))
  val speakerEnabled: StateFlow<Boolean> = _speakerEnabled

  private val _elevenLabsApiKey =
    MutableStateFlow(securePrefs.getString(elevenLabsApiKeyKey, "") ?: "")
  val elevenLabsApiKey: StateFlow<String> = _elevenLabsApiKey

  private val _elevenLabsVoiceId =
    MutableStateFlow(securePrefs.getString(elevenLabsVoiceIdKey, "") ?: "")
  val elevenLabsVoiceId: StateFlow<String> = _elevenLabsVoiceId

  private val _llmMode = MutableStateFlow(loadLlmMode())
  val llmMode: StateFlow<LlmMode> = _llmMode

  private val _cloudProvider = MutableStateFlow(loadCloudProvider())
  val cloudProvider: StateFlow<CloudProvider> = _cloudProvider

  private val _openAiApiKey =
    MutableStateFlow(securePrefs.getString(llmOpenAiApiKeyKey, "") ?: "")
  val openAiApiKey: StateFlow<String> = _openAiApiKey

  private val _anthropicApiKey =
    MutableStateFlow(securePrefs.getString(llmAnthropicApiKeyKey, "") ?: "")
  val anthropicApiKey: StateFlow<String> = _anthropicApiKey

  private val _groqApiKey =
    MutableStateFlow(securePrefs.getString(llmGroqApiKeyKey, "") ?: "")
  val groqApiKey: StateFlow<String> = _groqApiKey

  private val _openRouterApiKey =
    MutableStateFlow(securePrefs.getString(llmOpenRouterApiKeyKey, "") ?: "")
  val openRouterApiKey: StateFlow<String> = _openRouterApiKey

  private val _elevenLabsAgentId =
    MutableStateFlow(plainPrefs.getString(llmElevenLabsAgentIdKey, "") ?: "")
  val elevenLabsAgentId: StateFlow<String> = _elevenLabsAgentId

  private val _openAiModel =
    MutableStateFlow(
      plainPrefs.getString(llmOpenAiModelKey, CloudProvider.OpenAI.defaultModel)
        ?: CloudProvider.OpenAI.defaultModel,
    )
  val openAiModel: StateFlow<String> = _openAiModel

  private val _anthropicModel =
    MutableStateFlow(
      plainPrefs.getString(llmAnthropicModelKey, CloudProvider.Anthropic.defaultModel)
        ?: CloudProvider.Anthropic.defaultModel,
    )
  val anthropicModel: StateFlow<String> = _anthropicModel

  private val _groqModel =
    MutableStateFlow(
      plainPrefs.getString(llmGroqModelKey, CloudProvider.Groq.defaultModel)
        ?: CloudProvider.Groq.defaultModel,
    )
  val groqModel: StateFlow<String> = _groqModel

  private val _openRouterModel =
    MutableStateFlow(
      plainPrefs.getString(llmOpenRouterModelKey, CloudProvider.OpenRouter.defaultModel)
        ?: CloudProvider.OpenRouter.defaultModel,
    )
  val openRouterModel: StateFlow<String> = _openRouterModel

  private val _elevenLabsModel =
    MutableStateFlow(
      plainPrefs.getString(llmElevenLabsModelKey, CloudProvider.ElevenLabs.defaultModel)
        ?: CloudProvider.ElevenLabs.defaultModel,
    )
  val elevenLabsModel: StateFlow<String> = _elevenLabsModel

  private val _propAiControlBaseUrl = MutableStateFlow(loadPropAiControlBaseUrl())
  val propAiControlBaseUrl: StateFlow<String> = _propAiControlBaseUrl

  private val _propAiControlToken =
    MutableStateFlow(securePrefs.getString(propAiControlTokenKey, "") ?: "")
  val propAiControlToken: StateFlow<String> = _propAiControlToken

  private val _propAiControlEmail =
    MutableStateFlow(plainPrefs.getString(propAiControlEmailKey, "") ?: "")
  val propAiControlEmail: StateFlow<String> = _propAiControlEmail

  private val _propAiControlUserId =
    MutableStateFlow(plainPrefs.getString(propAiControlUserIdKey, "") ?: "")
  val propAiControlUserId: StateFlow<String> = _propAiControlUserId

  private val _propAiControlTenantId =
    MutableStateFlow(plainPrefs.getString(propAiControlTenantIdKey, "") ?: "")
  val propAiControlTenantId: StateFlow<String> = _propAiControlTenantId

  private val _propAiControlTenantName =
    MutableStateFlow(plainPrefs.getString(propAiControlTenantNameKey, "") ?: "")
  val propAiControlTenantName: StateFlow<String> = _propAiControlTenantName

  private val _propAiControlTenantRole =
    MutableStateFlow(plainPrefs.getString(propAiControlTenantRoleKey, "") ?: "")
  val propAiControlTenantRole: StateFlow<String> = _propAiControlTenantRole

  private val _propAiLicenseBaseUrl = MutableStateFlow(loadPropAiLicenseBaseUrl())
  val propAiLicenseBaseUrl: StateFlow<String> = _propAiLicenseBaseUrl

  private val _propAiActivationKey =
    MutableStateFlow(securePrefs.getString(propAiLicenseActivationKeyKey, "") ?: "")
  val propAiActivationKey: StateFlow<String> = _propAiActivationKey

  private val _propAiActivationToken =
    MutableStateFlow(securePrefs.getString(propAiLicenseActivationTokenKey, "") ?: "")
  val propAiActivationToken: StateFlow<String> = _propAiActivationToken

  private val _propAiLicenseStatus = MutableStateFlow(loadPropAiLicenseStatus())
  val propAiLicenseStatus: StateFlow<PropAiLicenseStatus> = _propAiLicenseStatus

  fun setLastDiscoveredStableId(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString("gateway.lastDiscoveredStableID", trimmed) }
    _lastDiscoveredStableId.value = trimmed
  }

  fun setDisplayName(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(displayNameKey, trimmed) }
    _displayName.value = trimmed
  }

  fun setCameraEnabled(value: Boolean) {
    plainPrefs.edit { putBoolean("camera.enabled", value) }
    _cameraEnabled.value = value
  }

  fun setLocationMode(mode: LocationMode) {
    plainPrefs.edit { putString(locationModeKey, mode.rawValue) }
    _locationMode.value = mode
  }

  fun setLocationPreciseEnabled(value: Boolean) {
    plainPrefs.edit { putBoolean("location.preciseEnabled", value) }
    _locationPreciseEnabled.value = value
  }

  fun setPreventSleep(value: Boolean) {
    plainPrefs.edit { putBoolean("screen.preventSleep", value) }
    _preventSleep.value = value
  }

  fun setManualEnabled(value: Boolean) {
    plainPrefs.edit { putBoolean("gateway.manual.enabled", value) }
    _manualEnabled.value = value
  }

  fun setManualHost(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString("gateway.manual.host", trimmed) }
    _manualHost.value = trimmed
  }

  fun setManualPort(value: Int) {
    plainPrefs.edit { putInt("gateway.manual.port", value) }
    _manualPort.value = value
  }

  fun setManualTls(value: Boolean) {
    plainPrefs.edit { putBoolean("gateway.manual.tls", value) }
    _manualTls.value = value
  }

  fun setGatewayToken(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString("gateway.manual.token", trimmed) }
    _gatewayToken.value = trimmed
  }

  fun setGatewayPassword(value: String) {
    saveGatewayPassword(value)
  }

  fun setOnboardingCompleted(value: Boolean) {
    plainPrefs.edit { putBoolean("onboarding.completed", value) }
    _onboardingCompleted.value = value
  }

  fun setWelcomeMessageSent(value: Boolean) {
    plainPrefs.edit { putBoolean(onboardingWelcomeSentKey, value) }
    _welcomeMessageSent.value = value
  }

  fun setCanvasDebugStatusEnabled(value: Boolean) {
    plainPrefs.edit { putBoolean("canvas.debugStatusEnabled", value) }
    _canvasDebugStatusEnabled.value = value
  }

  fun loadGatewayToken(): String? {
    val manual =
      _gatewayToken.value.trim().ifEmpty {
        val stored = securePrefs.getString("gateway.manual.token", null)?.trim().orEmpty()
        if (stored.isNotEmpty()) _gatewayToken.value = stored
        stored
      }
    if (manual.isNotEmpty()) return manual
    val key = "gateway.token.${_instanceId.value}"
    val stored = securePrefs.getString(key, null)?.trim()
    return stored?.takeIf { it.isNotEmpty() }
  }

  fun saveGatewayToken(token: String) {
    val key = "gateway.token.${_instanceId.value}"
    securePrefs.edit { putString(key, token.trim()) }
  }

  fun loadGatewayPassword(): String? {
    val key = "gateway.password.${_instanceId.value}"
    val stored = securePrefs.getString(key, null)?.trim()
    return stored?.takeIf { it.isNotEmpty() }
  }

  fun saveGatewayPassword(password: String) {
    val key = "gateway.password.${_instanceId.value}"
    securePrefs.edit { putString(key, password.trim()) }
  }

  fun loadGatewayTlsFingerprint(stableId: String): String? {
    val key = "gateway.tls.$stableId"
    return plainPrefs.getString(key, null)?.trim()?.takeIf { it.isNotEmpty() }
  }

  fun saveGatewayTlsFingerprint(stableId: String, fingerprint: String) {
    val key = "gateway.tls.$stableId"
    plainPrefs.edit { putString(key, fingerprint.trim()) }
  }

  fun getString(key: String): String? {
    return securePrefs.getString(key, null)
  }

  fun putString(key: String, value: String) {
    securePrefs.edit { putString(key, value) }
  }

  fun remove(key: String) {
    securePrefs.edit { remove(key) }
  }

  private fun createSecurePrefs(context: Context, name: String): SharedPreferences {
    return EncryptedSharedPreferences.create(
      context,
      name,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  private fun loadOrCreateInstanceId(): String {
    val existing = plainPrefs.getString("node.instanceId", null)?.trim()
    if (!existing.isNullOrBlank()) return existing
    val fresh = UUID.randomUUID().toString()
    plainPrefs.edit { putString("node.instanceId", fresh) }
    return fresh
  }

  private fun loadOrMigrateDisplayName(context: Context): String {
    val existing = plainPrefs.getString(displayNameKey, null)?.trim().orEmpty()
    val normalized = existing.lowercase()
    val legacyNames =
      setOf(
        "androidassistant",
        "android assistant",
        "androidassistant node",
        "android assistant node",
        "androidassistant app",
        "android assistant app",
        "android node",
        "archon",
      )
    if (existing.isNotEmpty() && normalized !in legacyNames && normalized != "propai sync") return existing

    val resolved = "PropAi Sync"

    plainPrefs.edit { putString(displayNameKey, resolved) }
    return resolved
  }

  fun setWakeWords(words: List<String>) {
    val sanitized = WakeWords.sanitize(words, defaultWakeWords)
    val encoded =
      JsonArray(sanitized.map { JsonPrimitive(it) }).toString()
    plainPrefs.edit { putString("voiceWake.triggerWords", encoded) }
    _wakeWords.value = sanitized
  }

  fun setVoiceWakeMode(mode: VoiceWakeMode) {
    plainPrefs.edit { putString(voiceWakeModeKey, mode.rawValue) }
    _voiceWakeMode.value = mode
  }

  fun setChatListeningPackages(values: List<String>) {
    val sanitized = sanitizeChatListeningPackages(values)
    val encoded = JsonArray(sanitized.map { JsonPrimitive(it) }).toString()
    plainPrefs.edit { putString(chatListeningPackagesKey, encoded) }
    _chatListeningPackages.value = sanitized
  }

  fun setChatListeningConversationFilters(values: List<String>) {
    val sanitized = sanitizeChatListeningConversationFilters(values)
    val encoded = JsonArray(sanitized.map { JsonPrimitive(it) }).toString()
    plainPrefs.edit { putString(chatListeningConversationFiltersKey, encoded) }
    _chatListeningConversationFilters.value = sanitized
  }

  fun setTalkEnabled(value: Boolean) {
    plainPrefs.edit { putBoolean("talk.enabled", value) }
    _talkEnabled.value = value
  }

  fun setSpeakerEnabled(value: Boolean) {
    plainPrefs.edit { putBoolean("voice.speakerEnabled", value) }
    _speakerEnabled.value = value
  }

  fun setElevenLabsApiKey(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(elevenLabsApiKeyKey, trimmed) }
    _elevenLabsApiKey.value = trimmed
  }

  fun setElevenLabsVoiceId(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(elevenLabsVoiceIdKey, trimmed) }
    _elevenLabsVoiceId.value = trimmed
  }

  fun setLlmMode(mode: LlmMode) {
    plainPrefs.edit { putString(llmModeKey, mode.rawValue) }
    _llmMode.value = mode
  }

  fun setCloudProvider(provider: CloudProvider) {
    plainPrefs.edit { putString(llmProviderKey, provider.id) }
    _cloudProvider.value = provider
  }

  fun setOpenAiApiKey(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(llmOpenAiApiKeyKey, trimmed) }
    _openAiApiKey.value = trimmed
  }

  fun setAnthropicApiKey(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(llmAnthropicApiKeyKey, trimmed) }
    _anthropicApiKey.value = trimmed
  }

  fun setGroqApiKey(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(llmGroqApiKeyKey, trimmed) }
    _groqApiKey.value = trimmed
  }

  fun setOpenRouterApiKey(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(llmOpenRouterApiKeyKey, trimmed) }
    _openRouterApiKey.value = trimmed
  }

  fun setElevenLabsAgentId(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(llmElevenLabsAgentIdKey, trimmed) }
    _elevenLabsAgentId.value = trimmed
  }

  fun setOpenAiModel(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(llmOpenAiModelKey, trimmed) }
    _openAiModel.value = trimmed
  }

  fun setAnthropicModel(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(llmAnthropicModelKey, trimmed) }
    _anthropicModel.value = trimmed
  }

  fun setGroqModel(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(llmGroqModelKey, trimmed) }
    _groqModel.value = trimmed
  }

  fun setOpenRouterModel(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(llmOpenRouterModelKey, trimmed) }
    _openRouterModel.value = trimmed
  }

  fun setElevenLabsModel(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(llmElevenLabsModelKey, trimmed) }
    _elevenLabsModel.value = trimmed
  }

  fun setPropAiControlBaseUrl(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(propAiControlBaseUrlKey, trimmed) }
    _propAiControlBaseUrl.value = trimmed
  }

  fun setPropAiControlToken(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(propAiControlTokenKey, trimmed) }
    _propAiControlToken.value = trimmed
  }

  fun setPropAiControlEmail(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(propAiControlEmailKey, trimmed) }
    _propAiControlEmail.value = trimmed
  }

  fun setPropAiControlUserId(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(propAiControlUserIdKey, trimmed) }
    _propAiControlUserId.value = trimmed
  }

  fun setPropAiControlTenant(id: String, name: String, role: String) {
    val trimmedId = id.trim()
    val trimmedName = name.trim()
    val trimmedRole = role.trim()
    plainPrefs.edit {
      putString(propAiControlTenantIdKey, trimmedId)
      putString(propAiControlTenantNameKey, trimmedName)
      putString(propAiControlTenantRoleKey, trimmedRole)
    }
    _propAiControlTenantId.value = trimmedId
    _propAiControlTenantName.value = trimmedName
    _propAiControlTenantRole.value = trimmedRole
  }

  fun clearPropAiControl() {
    securePrefs.edit { remove(propAiControlTokenKey) }
    plainPrefs.edit {
      remove(propAiControlEmailKey)
      remove(propAiControlUserIdKey)
      remove(propAiControlTenantIdKey)
      remove(propAiControlTenantNameKey)
      remove(propAiControlTenantRoleKey)
    }
    _propAiControlToken.value = ""
    _propAiControlEmail.value = ""
    _propAiControlUserId.value = ""
    _propAiControlTenantId.value = ""
    _propAiControlTenantName.value = ""
    _propAiControlTenantRole.value = ""
  }

  fun setPropAiLicenseBaseUrl(value: String) {
    val trimmed = value.trim()
    plainPrefs.edit { putString(propAiLicenseBaseUrlKey, trimmed) }
    _propAiLicenseBaseUrl.value = trimmed
  }

  fun setPropAiActivationKey(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(propAiLicenseActivationKeyKey, trimmed) }
    _propAiActivationKey.value = trimmed
  }

  fun setPropAiActivationToken(value: String) {
    val trimmed = value.trim()
    securePrefs.edit { putString(propAiLicenseActivationTokenKey, trimmed) }
    _propAiActivationToken.value = trimmed
  }

  fun setPropAiLicenseStatus(status: PropAiLicenseStatus) {
    val entitlementsJson =
      JsonArray(status.entitlements.map { JsonPrimitive(it) }).toString()
    plainPrefs.edit {
      putBoolean(propAiLicenseValidKey, status.valid)
      putString(propAiLicenseStatusKey, status.status.orEmpty())
      putString(propAiLicensePlanKey, status.plan.orEmpty())
      putString(propAiLicenseEntitlementsKey, entitlementsJson)
      putString(propAiLicenseExpiresAtKey, status.expiresAt.orEmpty())
      putString(propAiLicenseGraceUntilKey, status.graceUntil.orEmpty())
      putString(propAiLicenseRefreshAtKey, status.refreshAt.orEmpty())
      putInt(propAiLicenseDeviceLimitKey, status.deviceLimit ?: 0)
      putInt(propAiLicenseDevicesUsedKey, status.devicesUsed ?: 0)
      putString(propAiLicenseMessageKey, status.message.orEmpty())
      putString(propAiLicenseCodeKey, status.code.orEmpty())
      putString(propAiLicenseLastValidatedKey, status.lastValidatedAt.orEmpty())
    }
    _propAiLicenseStatus.value = status
  }

  fun clearPropAiLicenseStatus() {
    plainPrefs.edit {
      remove(propAiLicenseValidKey)
      remove(propAiLicenseStatusKey)
      remove(propAiLicensePlanKey)
      remove(propAiLicenseEntitlementsKey)
      remove(propAiLicenseExpiresAtKey)
      remove(propAiLicenseGraceUntilKey)
      remove(propAiLicenseRefreshAtKey)
      remove(propAiLicenseDeviceLimitKey)
      remove(propAiLicenseDevicesUsedKey)
      remove(propAiLicenseMessageKey)
      remove(propAiLicenseCodeKey)
      remove(propAiLicenseLastValidatedKey)
    }
    _propAiLicenseStatus.value = loadPropAiLicenseStatus()
  }

  private fun loadVoiceWakeMode(): VoiceWakeMode {
    val raw = plainPrefs.getString(voiceWakeModeKey, null)
    val resolved = VoiceWakeMode.fromRawValue(raw)

    // Default ON (foreground) when unset.
    if (raw.isNullOrBlank()) {
      plainPrefs.edit { putString(voiceWakeModeKey, resolved.rawValue) }
    }

    return resolved
  }

  private fun loadLlmMode(): LlmMode {
    val raw = plainPrefs.getString(llmModeKey, null)
    val resolved = LlmMode.fromRawValue(raw)
    if (raw.isNullOrBlank()) {
      plainPrefs.edit { putString(llmModeKey, resolved.rawValue) }
    }
    return resolved
  }

  private fun loadCloudProvider(): CloudProvider {
    val raw = plainPrefs.getString(llmProviderKey, null)
    if (raw.isNullOrBlank()) {
      plainPrefs.edit { putString(llmProviderKey, CloudProvider.OpenRouter.id) }
      return CloudProvider.OpenRouter
    }
    return CloudProvider.fromId(raw)
  }

  private fun loadPropAiControlBaseUrl(): String {
    val raw = plainPrefs.getString(propAiControlBaseUrlKey, null)?.trim().orEmpty()
    if (raw.isNotEmpty()) return raw
    plainPrefs.edit { putString(propAiControlBaseUrlKey, defaultPropAiControlBaseUrl) }
    return defaultPropAiControlBaseUrl
  }

  private fun loadPropAiLicenseBaseUrl(): String {
    val raw = plainPrefs.getString(propAiLicenseBaseUrlKey, null)?.trim().orEmpty()
    if (raw.isNotEmpty()) return raw
    plainPrefs.edit { putString(propAiLicenseBaseUrlKey, defaultPropAiLicenseBaseUrl) }
    return defaultPropAiLicenseBaseUrl
  }

  private fun loadPropAiLicenseStatus(): PropAiLicenseStatus {
    val entitlements = loadJsonStringList(propAiLicenseEntitlementsKey) { it }
    val valid = plainPrefs.getBoolean(propAiLicenseValidKey, false)
    val status = plainPrefs.getString(propAiLicenseStatusKey, null)?.trim().orEmpty().ifEmpty { null }
    val plan = plainPrefs.getString(propAiLicensePlanKey, null)?.trim().orEmpty().ifEmpty { null }
    val expiresAt = plainPrefs.getString(propAiLicenseExpiresAtKey, null)?.trim().orEmpty().ifEmpty { null }
    val graceUntil = plainPrefs.getString(propAiLicenseGraceUntilKey, null)?.trim().orEmpty().ifEmpty { null }
    val refreshAt = plainPrefs.getString(propAiLicenseRefreshAtKey, null)?.trim().orEmpty().ifEmpty { null }
    val deviceLimit = plainPrefs.getInt(propAiLicenseDeviceLimitKey, 0).takeIf { it > 0 }
    val devicesUsed = plainPrefs.getInt(propAiLicenseDevicesUsedKey, 0).takeIf { it >= 0 }
    val message = plainPrefs.getString(propAiLicenseMessageKey, null)?.trim().orEmpty().ifEmpty { null }
    val code = plainPrefs.getString(propAiLicenseCodeKey, null)?.trim().orEmpty().ifEmpty { null }
    val lastValidatedAt =
      plainPrefs.getString(propAiLicenseLastValidatedKey, null)?.trim().orEmpty().ifEmpty { null }

    return PropAiLicenseStatus(
      valid = valid,
      status = status,
      plan = plan,
      entitlements = entitlements,
      expiresAt = expiresAt,
      graceUntil = graceUntil,
      refreshAt = refreshAt,
      deviceLimit = deviceLimit,
      devicesUsed = devicesUsed,
      activationToken = null,
      message = message,
      code = code,
      lastValidatedAt = lastValidatedAt,
    )
  }

  private fun loadLocationMode(): LocationMode {
    val raw = plainPrefs.getString(locationModeKey, "off")
    val resolved = LocationMode.fromRawValue(raw)
    if (raw?.trim()?.lowercase() == "always") {
      plainPrefs.edit { putString(locationModeKey, resolved.rawValue) }
    }
    return resolved
  }

  private fun loadWakeWords(): List<String> {
    val raw = plainPrefs.getString("voiceWake.triggerWords", null)?.trim()
    if (raw.isNullOrEmpty()) return defaultWakeWords
    return try {
      val element = json.parseToJsonElement(raw)
      val array = element as? JsonArray ?: return defaultWakeWords
      val decoded =
        array.mapNotNull { item ->
          when (item) {
            is JsonNull -> null
            is JsonPrimitive -> item.content.trim().takeIf { it.isNotEmpty() }
            else -> null
          }
        }
      WakeWords.sanitize(decoded, defaultWakeWords)
    } catch (_: Throwable) {
      defaultWakeWords
    }
  }

  private fun loadChatListeningPackages(): List<String> {
    return loadJsonStringList(chatListeningPackagesKey, ::sanitizeChatListeningPackages)
  }

  private fun loadChatListeningConversationFilters(): List<String> {
    return loadJsonStringList(
      chatListeningConversationFiltersKey,
      ::sanitizeChatListeningConversationFilters,
    )
  }

  private fun loadJsonStringList(
    key: String,
    sanitizer: (List<String>) -> List<String>,
  ): List<String> {
    val raw = plainPrefs.getString(key, null)?.trim()
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
      val element = json.parseToJsonElement(raw)
      val array = element as? JsonArray ?: return emptyList()
      val decoded =
        array.mapNotNull { item ->
          when (item) {
            is JsonNull -> null
            is JsonPrimitive -> item.content.trim().takeIf { it.isNotEmpty() }
            else -> null
          }
        }
      sanitizer(decoded)
    } catch (_: Throwable) {
      emptyList()
    }
  }
}

