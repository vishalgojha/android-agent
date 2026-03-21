package ai.androidassistant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.androidassistant.app.gateway.GatewayEndpoint
import ai.androidassistant.app.chat.OutgoingAttachment
import ai.androidassistant.app.node.CameraCaptureManager
import ai.androidassistant.app.node.CanvasController
import ai.androidassistant.app.node.SmsManager
import ai.androidassistant.app.propai.PropAiLicenseStatus
import ai.androidassistant.app.voice.VoiceConversationEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {
  private val runtime: NodeRuntime = (app as NodeApp).runtime

  val canvas: CanvasController = runtime.canvas
  val canvasCurrentUrl: StateFlow<String?> = runtime.canvas.currentUrl
  val canvasA2uiHydrated: StateFlow<Boolean> = runtime.canvasA2uiHydrated
  val canvasRehydratePending: StateFlow<Boolean> = runtime.canvasRehydratePending
  val canvasRehydrateErrorText: StateFlow<String?> = runtime.canvasRehydrateErrorText
  val camera: CameraCaptureManager = runtime.camera
  val sms: SmsManager = runtime.sms

  val gateways: StateFlow<List<GatewayEndpoint>> = runtime.gateways
  val discoveryStatusText: StateFlow<String> = runtime.discoveryStatusText

  val isConnected: StateFlow<Boolean> = runtime.isConnected
  val isNodeConnected: StateFlow<Boolean> = runtime.nodeConnected
  val statusText: StateFlow<String> = runtime.statusText
  val serverName: StateFlow<String?> = runtime.serverName
  val remoteAddress: StateFlow<String?> = runtime.remoteAddress
  val pendingGatewayTrust: StateFlow<NodeRuntime.GatewayTrustPrompt?> = runtime.pendingGatewayTrust
  val isForeground: StateFlow<Boolean> = runtime.isForeground
  val seamColorArgb: StateFlow<Long> = runtime.seamColorArgb
  val mainSessionKey: StateFlow<String> = runtime.mainSessionKey

  val cameraHud: StateFlow<CameraHudState?> = runtime.cameraHud
  val cameraFlashToken: StateFlow<Long> = runtime.cameraFlashToken

  val instanceId: StateFlow<String> = runtime.instanceId
  val displayName: StateFlow<String> = runtime.displayName
  val wakeWords: StateFlow<List<String>> = runtime.wakeWords
  val cameraEnabled: StateFlow<Boolean> = runtime.cameraEnabled
  val locationMode: StateFlow<LocationMode> = runtime.locationMode
  val locationPreciseEnabled: StateFlow<Boolean> = runtime.locationPreciseEnabled
  val preventSleep: StateFlow<Boolean> = runtime.preventSleep
  val micEnabled: StateFlow<Boolean> = runtime.micEnabled
  val micCooldown: StateFlow<Boolean> = runtime.micCooldown
  val micStatusText: StateFlow<String> = runtime.micStatusText
  val micLiveTranscript: StateFlow<String?> = runtime.micLiveTranscript
  val micIsListening: StateFlow<Boolean> = runtime.micIsListening
  val micQueuedMessages: StateFlow<List<String>> = runtime.micQueuedMessages
  val micConversation: StateFlow<List<VoiceConversationEntry>> = runtime.micConversation
  val micInputLevel: StateFlow<Float> = runtime.micInputLevel
  val micIsSending: StateFlow<Boolean> = runtime.micIsSending
  val speakerEnabled: StateFlow<Boolean> = runtime.speakerEnabled
  val elevenLabsApiKey: StateFlow<String> = runtime.elevenLabsApiKey
  val elevenLabsVoiceId: StateFlow<String> = runtime.elevenLabsVoiceId
  val manualEnabled: StateFlow<Boolean> = runtime.manualEnabled
  val manualHost: StateFlow<String> = runtime.manualHost
  val manualPort: StateFlow<Int> = runtime.manualPort
  val manualTls: StateFlow<Boolean> = runtime.manualTls
  val chatListeningPackages: StateFlow<List<String>> = runtime.chatListeningPackages
  val chatListeningConversationFilters: StateFlow<List<String>> = runtime.chatListeningConversationFilters
  val gatewayToken: StateFlow<String> = runtime.gatewayToken
  val onboardingCompleted: StateFlow<Boolean> = runtime.onboardingCompleted
  val welcomeMessageSent: StateFlow<Boolean> = runtime.welcomeMessageSent
  val canvasDebugStatusEnabled: StateFlow<Boolean> = runtime.canvasDebugStatusEnabled
  val llmMode: StateFlow<LlmMode> = runtime.llmMode
  val cloudProvider: StateFlow<CloudProvider> = runtime.cloudProvider
  val openAiApiKey: StateFlow<String> = runtime.openAiApiKey
  val anthropicApiKey: StateFlow<String> = runtime.anthropicApiKey
  val groqApiKey: StateFlow<String> = runtime.groqApiKey
  val openRouterApiKey: StateFlow<String> = runtime.openRouterApiKey
  val elevenLabsAgentId: StateFlow<String> = runtime.elevenLabsAgentId
  val openAiModel: StateFlow<String> = runtime.openAiModel
  val anthropicModel: StateFlow<String> = runtime.anthropicModel
  val groqModel: StateFlow<String> = runtime.groqModel
  val openRouterModel: StateFlow<String> = runtime.openRouterModel
  val elevenLabsModel: StateFlow<String> = runtime.elevenLabsModel
  val propAiControlBaseUrl: StateFlow<String> = runtime.propAiControlBaseUrl
  val propAiControlEmail: StateFlow<String> = runtime.propAiControlEmail
  val propAiControlUserId: StateFlow<String> = runtime.propAiControlUserId
  val propAiControlTenantId: StateFlow<String> = runtime.propAiControlTenantId
  val propAiControlTenantName: StateFlow<String> = runtime.propAiControlTenantName
  val propAiControlTenantRole: StateFlow<String> = runtime.propAiControlTenantRole
  val propAiLicenseBaseUrl: StateFlow<String> = runtime.propAiLicenseBaseUrl
  val propAiActivationKey: StateFlow<String> = runtime.propAiActivationKey
  val propAiActivationToken: StateFlow<String> = runtime.propAiActivationToken
  val propAiLicenseStatus: StateFlow<PropAiLicenseStatus> = runtime.propAiLicenseStatus
  val propAiControlBusy: StateFlow<Boolean> = runtime.propAiControlBusy
  val propAiControlError: StateFlow<String?> = runtime.propAiControlError
  val propAiLicenseBusy: StateFlow<Boolean> = runtime.propAiLicenseBusy
  val propAiLicenseError: StateFlow<String?> = runtime.propAiLicenseError

  val chatSessionKey: StateFlow<String> = runtime.chatSessionKey
  val chatSessionId: StateFlow<String?> = runtime.chatSessionId
  val chatMessages = runtime.chatMessages
  val chatError: StateFlow<String?> = runtime.chatError
  val chatHealthOk: StateFlow<Boolean> = runtime.chatHealthOk
  val chatThinkingLevel: StateFlow<String> = runtime.chatThinkingLevel
  val chatStreamingAssistantText: StateFlow<String?> = runtime.chatStreamingAssistantText
  val chatPendingToolCalls = runtime.chatPendingToolCalls
  val chatSessions = runtime.chatSessions
  val pendingRunCount: StateFlow<Int> = runtime.pendingRunCount

  fun setForeground(value: Boolean) {
    runtime.setForeground(value)
  }

  fun setDisplayName(value: String) {
    runtime.setDisplayName(value)
  }

  fun setWakeWords(words: List<String>) {
    runtime.setWakeWords(words)
  }

  fun setCameraEnabled(value: Boolean) {
    runtime.setCameraEnabled(value)
  }

  fun setLocationMode(mode: LocationMode) {
    runtime.setLocationMode(mode)
  }

  fun setLocationPreciseEnabled(value: Boolean) {
    runtime.setLocationPreciseEnabled(value)
  }

  fun setPreventSleep(value: Boolean) {
    runtime.setPreventSleep(value)
  }

  fun setManualEnabled(value: Boolean) {
    runtime.setManualEnabled(value)
  }

  fun setManualHost(value: String) {
    runtime.setManualHost(value)
  }

  fun setManualPort(value: Int) {
    runtime.setManualPort(value)
  }

  fun setManualTls(value: Boolean) {
    runtime.setManualTls(value)
  }

  fun setChatListeningPackages(values: List<String>) {
    runtime.setChatListeningPackages(values)
  }

  fun setChatListeningConversationFilters(values: List<String>) {
    runtime.setChatListeningConversationFilters(values)
  }

  fun setGatewayToken(value: String) {
    runtime.setGatewayToken(value)
  }

  fun setGatewayPassword(value: String) {
    runtime.setGatewayPassword(value)
  }

  fun setOnboardingCompleted(value: Boolean) {
    runtime.setOnboardingCompleted(value)
  }

  fun showWelcomeMessageIfNeeded() {
    runtime.showWelcomeMessageIfNeeded()
  }

  fun setLlmMode(mode: LlmMode) {
    runtime.setLlmMode(mode)
  }

  fun setCloudProvider(provider: CloudProvider) {
    runtime.setCloudProvider(provider)
  }

  fun setOpenAiApiKey(value: String) {
    runtime.setOpenAiApiKey(value)
  }

  fun setAnthropicApiKey(value: String) {
    runtime.setAnthropicApiKey(value)
  }

  fun setGroqApiKey(value: String) {
    runtime.setGroqApiKey(value)
  }

  fun setOpenRouterApiKey(value: String) {
    runtime.setOpenRouterApiKey(value)
  }

  fun setElevenLabsAgentId(value: String) {
    runtime.setElevenLabsAgentId(value)
  }

  fun setOpenAiModel(value: String) {
    runtime.setOpenAiModel(value)
  }

  fun setAnthropicModel(value: String) {
    runtime.setAnthropicModel(value)
  }

  fun setGroqModel(value: String) {
    runtime.setGroqModel(value)
  }

  fun setOpenRouterModel(value: String) {
    runtime.setOpenRouterModel(value)
  }

  fun setElevenLabsModel(value: String) {
    runtime.setElevenLabsModel(value)
  }

  fun setPropAiControlBaseUrl(value: String) {
    runtime.setPropAiControlBaseUrl(value)
  }

  fun setPropAiLicenseBaseUrl(value: String) {
    runtime.setPropAiLicenseBaseUrl(value)
  }

  fun setPropAiActivationKey(value: String) {
    runtime.setPropAiActivationKey(value)
  }

  fun loginPropAi(email: String, password: String) {
    runtime.loginPropAi(email, password)
  }

  fun registerPropAi(email: String, password: String, tenantName: String) {
    runtime.registerPropAi(email, password, tenantName)
  }

  fun refreshPropAiProfile() {
    runtime.refreshPropAiProfile()
  }

  fun logoutPropAi() {
    runtime.logoutPropAi()
  }

  fun activatePropAiLicense() {
    runtime.activatePropAiLicense()
  }

  fun refreshPropAiLicense() {
    runtime.refreshPropAiLicense()
  }

  fun clearPropAiLicense() {
    runtime.clearPropAiLicense()
  }

  fun setCanvasDebugStatusEnabled(value: Boolean) {
    runtime.setCanvasDebugStatusEnabled(value)
  }

  fun setVoiceScreenActive(active: Boolean) {
    runtime.setVoiceScreenActive(active)
  }

  fun setMicEnabled(enabled: Boolean) {
    runtime.setMicEnabled(enabled)
  }

  fun setSpeakerEnabled(enabled: Boolean) {
    runtime.setSpeakerEnabled(enabled)
  }

  fun setElevenLabsApiKey(value: String) {
    runtime.setElevenLabsApiKey(value)
  }

  fun setElevenLabsVoiceId(value: String) {
    runtime.setElevenLabsVoiceId(value)
  }

  fun refreshGatewayConnection() {
    runtime.refreshGatewayConnection()
  }

  fun connect(endpoint: GatewayEndpoint) {
    runtime.connect(endpoint)
  }

  fun connectManual() {
    runtime.connectManual()
  }

  fun disconnect() {
    runtime.disconnect()
  }

  fun acceptGatewayTrustPrompt() {
    runtime.acceptGatewayTrustPrompt()
  }

  fun declineGatewayTrustPrompt() {
    runtime.declineGatewayTrustPrompt()
  }

  fun handleCanvasA2UIActionFromWebView(payloadJson: String) {
    runtime.handleCanvasA2UIActionFromWebView(payloadJson)
  }

  fun requestCanvasRehydrate(source: String = "screen_tab") {
    runtime.requestCanvasRehydrate(source = source, force = true)
  }

  fun loadChat(sessionKey: String) {
    runtime.loadChat(sessionKey)
  }

  fun refreshChat() {
    runtime.refreshChat()
  }

  fun refreshChatSessions(limit: Int? = null) {
    runtime.refreshChatSessions(limit = limit)
  }

  fun setChatThinkingLevel(level: String) {
    runtime.setChatThinkingLevel(level)
  }

  fun switchChatSession(sessionKey: String) {
    runtime.switchChatSession(sessionKey)
  }

  fun abortChat() {
    runtime.abortChat()
  }

  fun sendChat(message: String, thinking: String, attachments: List<OutgoingAttachment>) {
    viewModelScope.launch(Dispatchers.Default) {
      runtime.sendChat(message = message, thinking = thinking, attachments = attachments)
    }
  }
}

