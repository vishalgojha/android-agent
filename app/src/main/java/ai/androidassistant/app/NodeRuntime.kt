package ai.androidassistant.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import ai.androidassistant.app.automation.AutomationEngine
import ai.androidassistant.app.chat.ChatController
import ai.androidassistant.app.chat.ChatMemoryStore
import ai.androidassistant.app.chat.ChatMessage
import ai.androidassistant.app.chat.ChatPendingToolCall
import ai.androidassistant.app.chat.ChatSessionEntry
import ai.androidassistant.app.chat.OutgoingAttachment
import ai.androidassistant.app.cloud.CloudChatMessage
import ai.androidassistant.app.cloud.CloudLlmClient
import ai.androidassistant.app.gateway.DeviceAuthStore
import ai.androidassistant.app.gateway.DeviceIdentityStore
import ai.androidassistant.app.gateway.GatewayDiscovery
import ai.androidassistant.app.gateway.GatewayEndpoint
import ai.androidassistant.app.gateway.GatewaySession
import ai.androidassistant.app.gateway.probeGatewayTlsFingerprint
import ai.androidassistant.app.node.*
import ai.androidassistant.app.protocol.AndroidAssistantCanvasA2UIAction
import ai.androidassistant.app.propai.PropAiAuthResult
import ai.androidassistant.app.propai.PropAiControlClient
import ai.androidassistant.app.propai.PropAiLicenseClient
import ai.androidassistant.app.propai.PropAiLicenseStatus
import ai.androidassistant.app.propai.PropAiWhatsappMessage
import ai.androidassistant.app.voice.ConvaiVoiceManager
import ai.androidassistant.app.voice.MicCaptureManager
import ai.androidassistant.app.voice.TalkModeManager
import ai.androidassistant.app.voice.VoiceConversationEntry
import ai.androidassistant.app.voice.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class NodeRuntime(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val cloudOnly = AppConfig.CLOUD_ONLY
  private val localSessionMode = cloudOnly

  val prefs = SecurePrefs(appContext)
  private val cloudClient = CloudLlmClient()
  private val deviceAuthStore = DeviceAuthStore(prefs)
  val canvas = CanvasController()
  val camera = CameraCaptureManager(appContext)
  val location = LocationCaptureManager(appContext)
  val sms = SmsManager(appContext)
  private val json = Json { ignoreUnknownKeys = true }
  private val chatListeningStore = ChatListeningStore(appContext)
  private val chatMemoryStore = ChatMemoryStore(appContext, json)
  private val propAiControlClient = PropAiControlClient()
  private val propAiLicenseClient = PropAiLicenseClient()

  private val _propAiControlBusy = MutableStateFlow(false)
  val propAiControlBusy: StateFlow<Boolean> = _propAiControlBusy.asStateFlow()
  private val _propAiControlError = MutableStateFlow<String?>(null)
  val propAiControlError: StateFlow<String?> = _propAiControlError.asStateFlow()
  private val _propAiLicenseBusy = MutableStateFlow(false)
  val propAiLicenseBusy: StateFlow<Boolean> = _propAiLicenseBusy.asStateFlow()
  private val _propAiLicenseError = MutableStateFlow<String?>(null)
  val propAiLicenseError: StateFlow<String?> = _propAiLicenseError.asStateFlow()

  private val externalAudioCaptureActive = MutableStateFlow(false)

  private val discovery = GatewayDiscovery(appContext, scope = scope, enabled = !localSessionMode)
  val gateways: StateFlow<List<GatewayEndpoint>> = discovery.gateways
  val discoveryStatusText: StateFlow<String> = discovery.statusText

  private val identityStore = DeviceIdentityStore(appContext)
  private var connectedEndpoint: GatewayEndpoint? = null

  private val cameraHandler: CameraHandler = CameraHandler(
    appContext = appContext,
    camera = camera,
    externalAudioCaptureActive = externalAudioCaptureActive,
    showCameraHud = ::showCameraHud,
    triggerCameraFlash = ::triggerCameraFlash,
    invokeErrorFromThrowable = { invokeErrorFromThrowable(it) },
  )

  private val debugHandler: DebugHandler = DebugHandler(
    appContext = appContext,
    identityStore = identityStore,
  )

  private val locationHandler: LocationHandler = LocationHandler(
    appContext = appContext,
    location = location,
    json = json,
    isForeground = { _isForeground.value },
    locationPreciseEnabled = { locationPreciseEnabled.value },
  )

  private val deviceHandler: DeviceHandler = DeviceHandler(
    appContext = appContext,
  )

  private val notificationsHandler: NotificationsHandler = NotificationsHandler(
    appContext = appContext,
  )

  private val systemHandler: SystemHandler = SystemHandler(
    appContext = appContext,
  )

  private val photosHandler: PhotosHandler = PhotosHandler(
    appContext = appContext,
  )

  private val contactsHandler: ContactsHandler = ContactsHandler(
    appContext = appContext,
  )

  private val calendarHandler: CalendarHandler = CalendarHandler(
    appContext = appContext,
  )

  private val motionHandler: MotionHandler = MotionHandler(
    appContext = appContext,
  )

  private val smsHandlerImpl: SmsHandler = SmsHandler(
    sms = sms,
  )

  private val a2uiHandler: A2UIHandler = A2UIHandler(
    canvas = canvas,
    json = json,
    getNodeCanvasHostUrl = { nodeSession.currentCanvasHostUrl() },
    getOperatorCanvasHostUrl = { operatorSession.currentCanvasHostUrl() },
  )

  private val connectionManager: ConnectionManager = ConnectionManager(
    prefs = prefs,
    cameraEnabled = { cameraEnabled.value },
    locationMode = { locationMode.value },
    voiceWakeMode = { VoiceWakeMode.Off },
    motionActivityAvailable = { motionHandler.isActivityAvailable() },
    motionPedometerAvailable = { motionHandler.isPedometerAvailable() },
    smsAvailable = { sms.canSendSms() },
    hasRecordAudioPermission = { hasRecordAudioPermission() },
    manualTls = { manualTls.value },
  )

  private val invokeDispatcher: InvokeDispatcher = InvokeDispatcher(
    canvas = canvas,
    cameraHandler = cameraHandler,
    locationHandler = locationHandler,
    deviceHandler = deviceHandler,
    notificationsHandler = notificationsHandler,
    systemHandler = systemHandler,
    photosHandler = photosHandler,
    contactsHandler = contactsHandler,
    calendarHandler = calendarHandler,
    motionHandler = motionHandler,
    smsHandler = smsHandlerImpl,
    a2uiHandler = a2uiHandler,
    debugHandler = debugHandler,
    isForeground = { _isForeground.value },
    cameraEnabled = { cameraEnabled.value },
    locationEnabled = { locationMode.value != LocationMode.Off },
    smsAvailable = { sms.canSendSms() },
    debugBuild = { BuildConfig.DEBUG },
    refreshNodeCanvasCapability = { nodeSession.refreshNodeCanvasCapability() },
    onCanvasA2uiPush = {
      _canvasA2uiHydrated.value = true
      _canvasRehydratePending.value = false
      _canvasRehydrateErrorText.value = null
    },
    onCanvasA2uiReset = { _canvasA2uiHydrated.value = false },
    motionActivityAvailable = { motionHandler.isActivityAvailable() },
    motionPedometerAvailable = { motionHandler.isPedometerAvailable() },
  )

  data class GatewayTrustPrompt(
    val endpoint: GatewayEndpoint,
    val fingerprintSha256: String,
  )

  private fun assistantSystemPrompt(): String {
    val tenantName = prefs.propAiControlTenantName.value.trim()
    val tenantRole = prefs.propAiControlTenantRole.value.trim()
    val tenantLine =
      if (tenantName.isNotEmpty()) {
        val roleSuffix = tenantRole.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
        "PropAi Sync account: $tenantName$roleSuffix."
      } else {
        "PropAi Sync account: not linked."
      }
    return "You are PropAi Sync, a general assistant focused on real estate and realtor workflows.\n" +
      "$tenantLine\n" +
      "Be concise, action oriented, and ask for missing details when needed."
  }

  private suspend fun generateAssistantResponse(prompt: String): String {
    return generateAssistantResponse(messages = emptyList(), prompt = prompt)
  }

  private suspend fun generateAssistantResponse(
    messages: List<ChatMessage>,
    prompt: String,
  ): String {
    val cleaned = prompt.trim()
    if (cleaned.isEmpty()) return ""
    val chatContext = buildChatListeningContext(cleaned)
    val cloudMessages = buildCloudMessages(messages)

    val provider = prefs.cloudProvider.value
    val (apiKey, modelOrAgent) =
      when (provider) {
        CloudProvider.OpenAI ->
          prefs.openAiApiKey.value.trim() to
            prefs.openAiModel.value.trim().ifBlank { provider.defaultModel }
        CloudProvider.Anthropic ->
          prefs.anthropicApiKey.value.trim() to
            prefs.anthropicModel.value.trim().ifBlank { provider.defaultModel }
        CloudProvider.Groq ->
          prefs.groqApiKey.value.trim() to
            prefs.groqModel.value.trim().ifBlank { provider.defaultModel }
        CloudProvider.OpenRouter ->
          prefs.openRouterApiKey.value.trim() to
            prefs.openRouterModel.value.trim().ifBlank { provider.defaultModel }
        CloudProvider.ElevenLabs ->
          prefs.elevenLabsApiKey.value.trim() to
            prefs.elevenLabsModel.value.trim().ifBlank { provider.defaultModel }
      }

    if (apiKey.isBlank()) {
      return "${provider.label} is not configured. Add API key in Settings."
    }
    if (modelOrAgent.isBlank()) {
      return "${provider.label} model is missing. Add it in Settings."
    }

    val timeoutMs =
      when (provider) {
        CloudProvider.OpenRouter -> 60_000L
        else -> 45_000L
      }
    val cloudResult =
      withTimeoutOrNull(timeoutMs) {
        cloudClient.generateResponse(
          provider = provider,
          apiKey = apiKey,
          model = modelOrAgent,
          messages = cloudMessages.ifEmpty {
            listOf(CloudChatMessage(role = "user", content = cleaned))
          },
          systemPrompt = assistantSystemPrompt().let { base ->
            chatContext?.let { "$base\n\n$it" } ?: base
          },
        )
      } ?: return "${provider.label} request timed out. Please try again."

    return cloudResult.getOrElse { error ->
      val message = error.message?.takeIf { it.isNotBlank() } ?: "Unknown error"
      "${provider.label} error: $message"
    }.takeIf { it.isNotBlank() }
      ?: "${provider.label} response was empty."
  }

  private suspend fun buildChatListeningContext(prompt: String): String? {
    if (!isChatListeningQuery(prompt)) return null
    val filters = inferChatPackageFilters(prompt)
    val recentMessages =
      chatListeningStore.recentMessages(
        limit = 40,
        withinMs = 1000L * 60L * 60L * 24L,
        packageFilters = filters,
      )
    val whatsappRemote =
      if (filters.any { it.contains("whatsapp") }) {
        loadPropAiWhatsappMessages(limit = 40, withinMs = 1000L * 60L * 60L * 24L)
      } else {
        emptyList()
      }
    val headerLines = mutableListOf<String>()
    if (recentMessages.isNotEmpty()) {
      headerLines +=
        "Recent chat messages captured on this device (local-only). Use them only when relevant:"
      headerLines += formatChatMessagesForPrompt(recentMessages)
    }
    if (whatsappRemote.isNotEmpty()) {
      headerLines +=
        "Recent WhatsApp messages from PropAi Sync (server-side Baileys). Use them only when relevant:"
      headerLines += formatPropAiWhatsappMessagesForPrompt(whatsappRemote)
    }
    if (headerLines.isEmpty()) {
      return "Recent chat messages (device + PropAi Sync). Use them only when relevant:\n- None captured yet."
    }
    return headerLines.joinToString("\n")
  }

  private suspend fun loadPropAiWhatsappMessages(
    limit: Int,
    withinMs: Long,
  ): List<PropAiWhatsappMessage> {
    val baseUrl = prefs.propAiControlBaseUrl.value.trim()
    val token = prefs.propAiControlToken.value.trim()
    if (baseUrl.isEmpty() || token.isEmpty()) return emptyList()
    return propAiControlClient
      .listWhatsappMessages(
        baseUrl = baseUrl,
        token = token,
        limit = limit,
        withinMs = withinMs,
      )
      .getOrElse { emptyList() }
  }

  private fun buildCloudMessages(messages: List<ChatMessage>): List<CloudChatMessage> {
    if (messages.isEmpty()) return emptyList()
    return messages.takeLast(40).mapNotNull { message ->
      val role = message.role.lowercase(Locale.getDefault())
      if (role != "user" && role != "assistant") return@mapNotNull null
      val textParts =
        message.content.mapNotNull { item ->
          when {
            item.type == "text" && !item.text.isNullOrBlank() -> item.text
            item.type != "text" -> {
              val name = item.fileName?.takeIf { it.isNotBlank() } ?: item.type
              val mime = item.mimeType?.takeIf { it.isNotBlank() }
              if (mime != null) {
                "[Attachment: $name · $mime]"
              } else {
                "[Attachment: $name]"
              }
            }
            else -> null
          }
        }
      val merged = textParts.joinToString("\n").trim()
      if (merged.isBlank()) return@mapNotNull null
      CloudChatMessage(role = role, content = merged)
    }
  }

  private fun isChatListeningQuery(prompt: String): Boolean {
    val lower = prompt.lowercase(Locale.getDefault())
    val keywords =
      listOf(
        "whatsapp",
        "message",
        "messages",
        "chat",
        "dm",
        "text",
        "texts",
        "telegram",
        "signal",
        "slack",
        "discord",
        "imessage",
        "sms",
      )
    return keywords.any { lower.contains(it) }
  }

  private fun inferChatPackageFilters(prompt: String): List<String> {
    val lower = prompt.lowercase(Locale.getDefault())
    val filters = mutableListOf<String>()
    if (lower.contains("whatsapp")) {
      filters += "whatsapp"
    }
    if (lower.contains("telegram")) {
      filters += "telegram"
    }
    if (lower.contains("signal")) {
      filters += "signal"
    }
    if (lower.contains("slack")) {
      filters += "slack"
    }
    if (lower.contains("discord")) {
      filters += "discord"
    }
    if (lower.contains("sms") || lower.contains("text")) {
      filters += "messaging"
      filters += "sms"
    }
    return filters
  }

  private fun formatChatMessagesForPrompt(messages: List<ChatListeningMessage>): String {
    val formatter =
      DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    val builder = StringBuilder()
    var remaining = 3000
    for (message in messages.sortedByDescending { it.timestampMs }) {
      if (remaining <= 0) break
      val timestamp = formatter.format(Instant.ofEpochMilli(message.timestampMs))
      val appName = readableChatAppName(message.packageName)
      val sender = message.sender?.trim().takeIf { !it.isNullOrBlank() } ?: "Unknown"
      val conversation = message.conversation?.trim().takeIf { !it.isNullOrBlank() }
      val convoSuffix = conversation?.let { " • $it" } ?: ""
      val text = message.text.replace("\n", " ").trim().take(260)
      val line = "- [$timestamp] ($appName$convoSuffix) $sender: $text"
      if (line.length + 1 > remaining) break
      builder.append(line).append("\n")
      remaining -= line.length + 1
    }
    return builder.toString().trimEnd()
  }

  private fun formatPropAiWhatsappMessagesForPrompt(messages: List<PropAiWhatsappMessage>): String {
    val formatter =
      DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    val builder = StringBuilder()
    var remaining = 3000
    for (message in messages.sortedByDescending { it.timestampMs }) {
      if (remaining <= 0) break
      val timestamp = formatter.format(Instant.ofEpochMilli(message.timestampMs))
      val sender = message.sender?.trim().takeIf { !it.isNullOrBlank() } ?: "Unknown"
      val convo = message.chatId?.trim().takeIf { !it.isNullOrBlank() }
      val convoSuffix = convo?.let { " • $it" } ?: ""
      val text = message.text.replace("\n", " ").trim().take(260)
      val line = "- [$timestamp] (WhatsApp$convoSuffix) $sender: $text"
      if (line.length + 1 > remaining) break
      builder.append(line).append("\n")
      remaining -= line.length + 1
    }
    return builder.toString().trimEnd()
  }

  private fun readableChatAppName(packageName: String): String {
    val lower = packageName.lowercase(Locale.getDefault())
    return when {
      "whatsapp" in lower -> "WhatsApp"
      "telegram" in lower -> "Telegram"
      "signal" in lower -> "Signal"
      "slack" in lower -> "Slack"
      "discord" in lower -> "Discord"
      "messaging" in lower || lower.endsWith("sms") -> "Messages"
      else -> packageName
    }
  }

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
  private val _nodeConnected = MutableStateFlow(false)
  val nodeConnected: StateFlow<Boolean> = _nodeConnected.asStateFlow()

  private val _statusText =
    MutableStateFlow(
      when {
        cloudOnly -> "Cloud: ${prefs.cloudProvider.value.label} (missing key)"
        else -> "Offline"
      },
    )
  val statusText: StateFlow<String> = _statusText.asStateFlow()

  private val _pendingGatewayTrust = MutableStateFlow<GatewayTrustPrompt?>(null)
  val pendingGatewayTrust: StateFlow<GatewayTrustPrompt?> = _pendingGatewayTrust.asStateFlow()

  private val _mainSessionKey = MutableStateFlow("main")
  val mainSessionKey: StateFlow<String> = _mainSessionKey.asStateFlow()

  private val cameraHudSeq = AtomicLong(0)
  private val _cameraHud = MutableStateFlow<CameraHudState?>(null)
  val cameraHud: StateFlow<CameraHudState?> = _cameraHud.asStateFlow()

  private val _cameraFlashToken = MutableStateFlow(0L)
  val cameraFlashToken: StateFlow<Long> = _cameraFlashToken.asStateFlow()

  private val _canvasA2uiHydrated = MutableStateFlow(false)
  val canvasA2uiHydrated: StateFlow<Boolean> = _canvasA2uiHydrated.asStateFlow()
  private val _canvasRehydratePending = MutableStateFlow(false)
  val canvasRehydratePending: StateFlow<Boolean> = _canvasRehydratePending.asStateFlow()
  private val _canvasRehydrateErrorText = MutableStateFlow<String?>(null)
  val canvasRehydrateErrorText: StateFlow<String?> = _canvasRehydrateErrorText.asStateFlow()

  private val _serverName = MutableStateFlow<String?>(null)
  val serverName: StateFlow<String?> = _serverName.asStateFlow()

  private val _remoteAddress = MutableStateFlow<String?>(null)
  val remoteAddress: StateFlow<String?> = _remoteAddress.asStateFlow()

  private val _seamColorArgb = MutableStateFlow(DEFAULT_SEAM_COLOR_ARGB)
  val seamColorArgb: StateFlow<Long> = _seamColorArgb.asStateFlow()

  private val _isForeground = MutableStateFlow(true)
  val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

  private var lastAutoA2uiUrl: String? = null
  private var didAutoRequestCanvasRehydrate = false
  private val canvasRehydrateSeq = AtomicLong(0)
  private var operatorConnected = false
  private var operatorStatusText: String = "Offline"
  private var nodeStatusText: String = "Offline"

  private val operatorSession =
    GatewaySession(
      scope = scope,
      identityStore = identityStore,
      deviceAuthStore = deviceAuthStore,
      onConnected = { name, remote, mainSessionKey ->
        operatorConnected = true
        operatorStatusText = "Connected"
        _serverName.value = name
        _remoteAddress.value = remote
        _seamColorArgb.value = DEFAULT_SEAM_COLOR_ARGB
        applyMainSessionKey(mainSessionKey)
        updateStatus()
        voiceSession.onGatewayConnectionChanged(true)
        scope.launch {
          refreshBrandingFromGateway()
          if (voiceReplySpeakerLazy.isInitialized()) {
            voiceReplySpeaker.refreshConfig()
          }
        }
      },
      onDisconnected = { message ->
        operatorConnected = false
        operatorStatusText = message
        _serverName.value = null
        _remoteAddress.value = null
        _seamColorArgb.value = DEFAULT_SEAM_COLOR_ARGB
        if (!isCanonicalMainSessionKey(_mainSessionKey.value)) {
          _mainSessionKey.value = "main"
        }
        chat.applyMainSessionKey(resolveMainSessionKey())
        chat.onDisconnected(message)
        updateStatus()
        voiceSession.onGatewayConnectionChanged(false)
      },
      onEvent = { event, payloadJson ->
        handleGatewayEvent(event, payloadJson)
      },
    )

  private val nodeSession =
    GatewaySession(
      scope = scope,
      identityStore = identityStore,
      deviceAuthStore = deviceAuthStore,
      onConnected = { _, _, _ ->
        _nodeConnected.value = true
        nodeStatusText = "Connected"
        didAutoRequestCanvasRehydrate = false
        _canvasA2uiHydrated.value = false
        _canvasRehydratePending.value = false
        _canvasRehydrateErrorText.value = null
        updateStatus()
        maybeNavigateToA2uiOnConnect()
      },
      onDisconnected = { message ->
        _nodeConnected.value = false
        nodeStatusText = message
        didAutoRequestCanvasRehydrate = false
        _canvasA2uiHydrated.value = false
        _canvasRehydratePending.value = false
        _canvasRehydrateErrorText.value = null
        updateStatus()
        showLocalCanvasOnDisconnect()
      },
      onEvent = { _, _ -> },
      onInvoke = { req ->
        invokeDispatcher.handleInvoke(req.command, req.paramsJson)
      },
      onTlsFingerprint = { stableId, fingerprint ->
        prefs.saveGatewayTlsFingerprint(stableId, fingerprint)
      },
    )

  init {
    AutomationEngine.initialize(appContext)
    AutomationEngine.invokeCommandCallback = { command, params ->
      scope.launch {
        val paramsJson =
          if (params.isEmpty()) {
            null
          } else {
            buildJsonObject {
              params.forEach { (key, value) ->
                put(key, JsonPrimitive(value))
              }
            }.toString()
          }
        invokeDispatcher.handleInvoke(command, paramsJson)
      }
    }

    DeviceNotificationListenerService.setNodeEventSink { event, payloadJson ->
      scope.launch {
        nodeSession.sendNodeEvent(event = event, payloadJson = payloadJson)
      }
    }
  }

  private val chat: ChatController =
    ChatController(
      scope = scope,
      session = operatorSession,
      json = json,
      supportsChatSubscribe = false,
      localMode = localSessionMode,
      localResponder = { messages, prompt -> generateAssistantResponse(messages, prompt) },
      memoryStore = chatMemoryStore,
      onAssistantReply = { text ->
        if (prefs.speakerEnabled.value) {
          voiceReplySpeaker.speakAssistantReply(text)
        }
      },
      onUserMessage = {
        // New user turn should interrupt any active TTS.
        talkMode.stopTts()
        if (voiceReplySpeakerLazy.isInitialized()) {
          voiceReplySpeaker.stopTts()
        }
      },
    )
  private val voiceReplySpeakerLazy: Lazy<TalkModeManager> = lazy {
    // Reuse the existing TalkMode speech engine (ElevenLabs + deterministic system-TTS fallback)
    // without enabling the legacy talk capture loop.
    TalkModeManager(
      context = appContext,
      scope = scope,
      session = operatorSession,
      supportsChatSubscribe = false,
      isConnected = { localSessionMode || operatorConnected },
    ).also { speaker ->
      speaker.setPlaybackEnabled(prefs.speakerEnabled.value)
    }
  }
  private val voiceReplySpeaker: TalkModeManager
    get() = voiceReplySpeakerLazy.value

  private fun shouldUseConvaiVoice(): Boolean {
    if (cloudOnly) return true
    return prefs.llmMode.value == LlmMode.Cloud
  }

  private val voiceSession: VoiceSession by lazy {
    if (shouldUseConvaiVoice()) {
      ConvaiVoiceManager(
        context = appContext,
        scope = scope,
        prefs = prefs,
      )
    } else {
      MicCaptureManager(
        context = appContext,
        scope = scope,
        sendToGateway = { message, onRunIdKnown ->
          val idempotencyKey = UUID.randomUUID().toString()
          // Notify MicCaptureManager of the idempotency key *before* the network
          // call so pendingRunId is set before any chat events can arrive.
          onRunIdKnown(idempotencyKey)
          val params =
            buildJsonObject {
              put("sessionKey", JsonPrimitive(resolveMainSessionKey()))
              put("message", JsonPrimitive(message))
              put("thinking", JsonPrimitive(chatThinkingLevel.value))
              put("timeoutMs", JsonPrimitive(30_000))
              put("idempotencyKey", JsonPrimitive(idempotencyKey))
            }
          val response = operatorSession.request("chat.send", params.toString())
          parseChatSendRunId(response) ?: idempotencyKey
        },
        localMode = localSessionMode,
        localResponder = { prompt -> generateAssistantResponse(prompt) },
        speakAssistantReply = { text ->
          // Skip if TalkModeManager is handling TTS (ttsOnAllResponses) to avoid
          // double-speaking the same assistant reply from both pipelines.
          if (!talkMode.ttsOnAllResponses) {
            voiceReplySpeaker.speakAssistantReply(text)
          }
        },
      )
    }
  }

  val micStatusText: StateFlow<String>
    get() = voiceSession.statusText

  val micLiveTranscript: StateFlow<String?>
    get() = voiceSession.liveTranscript

  val micIsListening: StateFlow<Boolean>
    get() = voiceSession.isListening

  val micEnabled: StateFlow<Boolean>
    get() = voiceSession.micEnabled

  val micCooldown: StateFlow<Boolean>
    get() = voiceSession.micCooldown

  val micQueuedMessages: StateFlow<List<String>>
    get() = voiceSession.queuedMessages

  val micConversation: StateFlow<List<VoiceConversationEntry>>
    get() = voiceSession.conversation

  val micInputLevel: StateFlow<Float>
    get() = voiceSession.inputLevel

  val micIsSending: StateFlow<Boolean>
    get() = voiceSession.isSending

  private val talkMode: TalkModeManager by lazy {
    TalkModeManager(
      context = appContext,
      scope = scope,
      session = operatorSession,
      supportsChatSubscribe = true,
      isConnected = { localSessionMode || operatorConnected },
    ).also { manager ->
      manager.setPlaybackEnabled(prefs.speakerEnabled.value)
    }
  }

  private fun applyMainSessionKey(candidate: String?) {
    val trimmed = normalizeMainKey(candidate) ?: return
    if (isCanonicalMainSessionKey(_mainSessionKey.value)) return
    if (_mainSessionKey.value == trimmed) return
    _mainSessionKey.value = trimmed
    talkMode.setMainSessionKey(trimmed)
    chat.applyMainSessionKey(trimmed)
  }

  private fun applyPropAiAuth(auth: PropAiAuthResult, fallbackEmail: String) {
    val token = auth.token.trim()
    if (token.isNotEmpty()) {
      prefs.setPropAiControlToken(token)
    }
    val email = auth.user?.email?.trim().orEmpty().ifEmpty { fallbackEmail.trim() }
    if (email.isNotEmpty()) {
      prefs.setPropAiControlEmail(email)
    }
    val userId = auth.user?.id?.trim().orEmpty()
    if (userId.isNotEmpty()) {
      prefs.setPropAiControlUserId(userId)
    }

    val tenant = auth.tenants.firstOrNull()
    if (tenant != null) {
      prefs.setPropAiControlTenant(tenant.id, tenant.name, tenant.role)
    } else {
      prefs.setPropAiControlTenant("", "", "")
    }
  }

  private fun applyPropAiLicenseStatus(status: PropAiLicenseStatus) {
    val activationToken = status.activationToken?.trim().orEmpty()
    if (activationToken.isNotEmpty()) {
      prefs.setPropAiActivationToken(activationToken)
    }
    prefs.setPropAiLicenseStatus(status)
    _propAiLicenseError.value =
      if (status.valid) {
        null
      } else {
        status.message?.takeIf { it.isNotBlank() } ?: "License inactive."
      }
  }

  private fun updateStatus() {
    if (localSessionMode) {
      _isConnected.value = true
      _nodeConnected.value = true
      return
    }
    _isConnected.value = operatorConnected
    val operator = operatorStatusText.trim()
    val node = nodeStatusText.trim()
    _statusText.value =
      when {
        operatorConnected && _nodeConnected.value -> "Connected"
        operatorConnected && !_nodeConnected.value -> "Connected (node offline)"
        !operatorConnected && _nodeConnected.value ->
          if (operator.isNotEmpty() && operator != "Offline") {
            "Connected (operator: $operator)"
          } else {
            "Connected (operator offline)"
          }
        operator.isNotBlank() && operator != "Offline" -> operator
        else -> node
      }
  }

  private fun resolveMainSessionKey(): String {
    val trimmed = _mainSessionKey.value.trim()
    return if (trimmed.isEmpty()) "main" else trimmed
  }

  private fun maybeNavigateToA2uiOnConnect() {
    val a2uiUrl = a2uiHandler.resolveA2uiHostUrl() ?: return
    val current = canvas.currentUrl()?.trim().orEmpty()
    if (current.isEmpty() || current == lastAutoA2uiUrl) {
      lastAutoA2uiUrl = a2uiUrl
      canvas.navigate(a2uiUrl)
    }
  }

  private fun showLocalCanvasOnDisconnect() {
    lastAutoA2uiUrl = null
    _canvasA2uiHydrated.value = false
    _canvasRehydratePending.value = false
    _canvasRehydrateErrorText.value = null
    canvas.navigate("")
  }

  fun requestCanvasRehydrate(source: String = "manual", force: Boolean = true) {
    scope.launch {
      if (!_nodeConnected.value) {
        _canvasRehydratePending.value = false
        _canvasRehydrateErrorText.value = "Node offline. Reconnect and retry."
        return@launch
      }
      if (!force && didAutoRequestCanvasRehydrate) return@launch
      didAutoRequestCanvasRehydrate = true
      val requestId = canvasRehydrateSeq.incrementAndGet()
      _canvasRehydratePending.value = true
      _canvasRehydrateErrorText.value = null

      val sessionKey = resolveMainSessionKey()
      val prompt =
        "Restore canvas now for session=$sessionKey source=$source. " +
          "If existing A2UI state exists, replay it immediately. " +
          "If not, create and render a compact mobile-friendly dashboard in Canvas."
      val sent =
        nodeSession.sendNodeEvent(
          event = "agent.request",
          payloadJson =
            buildJsonObject {
              put("message", JsonPrimitive(prompt))
              put("sessionKey", JsonPrimitive(sessionKey))
              put("thinking", JsonPrimitive("low"))
              put("deliver", JsonPrimitive(false))
            }.toString(),
        )
      if (!sent) {
        if (!force) {
          didAutoRequestCanvasRehydrate = false
        }
        if (canvasRehydrateSeq.get() == requestId) {
          _canvasRehydratePending.value = false
          _canvasRehydrateErrorText.value = "Failed to request restore. Tap to retry."
        }
        Log.w("AndroidAssistantCanvas", "canvas rehydrate request failed ($source): transport unavailable")
        return@launch
      }
      scope.launch {
        delay(20_000)
        if (canvasRehydrateSeq.get() != requestId) return@launch
        if (!_canvasRehydratePending.value) return@launch
        if (_canvasA2uiHydrated.value) return@launch
        _canvasRehydratePending.value = false
        _canvasRehydrateErrorText.value = "No canvas update yet. Tap to retry."
      }
    }
  }

  val instanceId: StateFlow<String> = prefs.instanceId
  val displayName: StateFlow<String> = prefs.displayName
  val wakeWords: StateFlow<List<String>> = prefs.wakeWords
  val cameraEnabled: StateFlow<Boolean> = prefs.cameraEnabled
  val locationMode: StateFlow<LocationMode> = prefs.locationMode
  val locationPreciseEnabled: StateFlow<Boolean> = prefs.locationPreciseEnabled
  val preventSleep: StateFlow<Boolean> = prefs.preventSleep
  val manualEnabled: StateFlow<Boolean> = prefs.manualEnabled
  val manualHost: StateFlow<String> = prefs.manualHost
  val manualPort: StateFlow<Int> = prefs.manualPort
  val manualTls: StateFlow<Boolean> = prefs.manualTls
  val chatListeningPackages: StateFlow<List<String>> = prefs.chatListeningPackages
  val chatListeningConversationFilters: StateFlow<List<String>> = prefs.chatListeningConversationFilters
  val gatewayToken: StateFlow<String> = prefs.gatewayToken
  val onboardingCompleted: StateFlow<Boolean> = prefs.onboardingCompleted
  val welcomeMessageSent: StateFlow<Boolean> = prefs.welcomeMessageSent
  val llmMode: StateFlow<LlmMode> = prefs.llmMode
  val cloudProvider: StateFlow<CloudProvider> = prefs.cloudProvider
  val openAiApiKey: StateFlow<String> = prefs.openAiApiKey
  val anthropicApiKey: StateFlow<String> = prefs.anthropicApiKey
  val groqApiKey: StateFlow<String> = prefs.groqApiKey
  val openRouterApiKey: StateFlow<String> = prefs.openRouterApiKey
  val elevenLabsAgentId: StateFlow<String> = prefs.elevenLabsAgentId
  val openAiModel: StateFlow<String> = prefs.openAiModel
  val anthropicModel: StateFlow<String> = prefs.anthropicModel
  val groqModel: StateFlow<String> = prefs.groqModel
  val openRouterModel: StateFlow<String> = prefs.openRouterModel
  val elevenLabsModel: StateFlow<String> = prefs.elevenLabsModel
  val propAiControlBaseUrl: StateFlow<String> = prefs.propAiControlBaseUrl
  val propAiControlToken: StateFlow<String> = prefs.propAiControlToken
  val propAiControlEmail: StateFlow<String> = prefs.propAiControlEmail
  val propAiControlUserId: StateFlow<String> = prefs.propAiControlUserId
  val propAiControlTenantId: StateFlow<String> = prefs.propAiControlTenantId
  val propAiControlTenantName: StateFlow<String> = prefs.propAiControlTenantName
  val propAiControlTenantRole: StateFlow<String> = prefs.propAiControlTenantRole
  val propAiLicenseBaseUrl: StateFlow<String> = prefs.propAiLicenseBaseUrl
  val propAiActivationKey: StateFlow<String> = prefs.propAiActivationKey
  val propAiActivationToken: StateFlow<String> = prefs.propAiActivationToken
  val propAiLicenseStatus: StateFlow<PropAiLicenseStatus> = prefs.propAiLicenseStatus
  fun setGatewayToken(value: String) = prefs.setGatewayToken(value)
  fun setGatewayPassword(value: String) = prefs.setGatewayPassword(value)
  fun setOnboardingCompleted(value: Boolean) = prefs.setOnboardingCompleted(value)

  fun showWelcomeMessageIfNeeded() {
    if (!prefs.onboardingCompleted.value) return
    if (prefs.welcomeMessageSent.value) return
    if (chat.messages.value.isNotEmpty()) return
    val welcomeMessage =
      "Hi! I'm PropAI Sync — your AI assistant for real estate. I can help you:\n" +
        "• Search and match properties\n" +
        "• Handle client queries\n" +
        "• Manage WhatsApp follow-ups\n\n" +
        "What would you like to do first?"
    chat.injectAssistantMessage(welcomeMessage)
    prefs.setWelcomeMessageSent(true)
  }
  fun setLlmMode(mode: LlmMode) = prefs.setLlmMode(mode)
  fun setCloudProvider(provider: CloudProvider) = prefs.setCloudProvider(provider)
  fun setOpenAiApiKey(value: String) = prefs.setOpenAiApiKey(value)
  fun setAnthropicApiKey(value: String) = prefs.setAnthropicApiKey(value)
  fun setGroqApiKey(value: String) = prefs.setGroqApiKey(value)
  fun setOpenRouterApiKey(value: String) = prefs.setOpenRouterApiKey(value)
  fun setElevenLabsAgentId(value: String) = prefs.setElevenLabsAgentId(value)
  fun setOpenAiModel(value: String) = prefs.setOpenAiModel(value)
  fun setAnthropicModel(value: String) = prefs.setAnthropicModel(value)
  fun setGroqModel(value: String) = prefs.setGroqModel(value)
  fun setOpenRouterModel(value: String) = prefs.setOpenRouterModel(value)
  fun setElevenLabsModel(value: String) = prefs.setElevenLabsModel(value)
  fun setPropAiControlBaseUrl(value: String) = prefs.setPropAiControlBaseUrl(value)
  fun setPropAiLicenseBaseUrl(value: String) = prefs.setPropAiLicenseBaseUrl(value)
  fun setPropAiActivationKey(value: String) = prefs.setPropAiActivationKey(value)

  fun loginPropAi(email: String, password: String) {
    val baseUrl = prefs.propAiControlBaseUrl.value.trim()
    val normalizedEmail = email.trim()
    if (baseUrl.isEmpty()) {
      _propAiControlError.value = "Set the PropAi Control API URL."
      return
    }
    if (normalizedEmail.isEmpty() || password.isBlank()) {
      _propAiControlError.value = "Email and password are required."
      return
    }
    _propAiControlBusy.value = true
    _propAiControlError.value = null
    scope.launch {
      val result = propAiControlClient.login(baseUrl, normalizedEmail, password)
      _propAiControlBusy.value = false
      result
        .onSuccess { auth ->
          applyPropAiAuth(auth, normalizedEmail)
          _propAiControlError.value = null
        }
        .onFailure { error ->
          _propAiControlError.value = error.message ?: "Login failed."
        }
    }
  }

  fun registerPropAi(email: String, password: String, tenantName: String) {
    val baseUrl = prefs.propAiControlBaseUrl.value.trim()
    val normalizedEmail = email.trim()
    val normalizedTenant = tenantName.trim()
    if (baseUrl.isEmpty()) {
      _propAiControlError.value = "Set the PropAi Control API URL."
      return
    }
    if (normalizedEmail.isEmpty() || password.isBlank() || normalizedTenant.isEmpty()) {
      _propAiControlError.value = "Email, password, and tenant name are required."
      return
    }
    _propAiControlBusy.value = true
    _propAiControlError.value = null
    scope.launch {
      val result = propAiControlClient.register(baseUrl, normalizedEmail, password, normalizedTenant)
      _propAiControlBusy.value = false
      result
        .onSuccess { auth ->
          applyPropAiAuth(auth, normalizedEmail)
          _propAiControlError.value = null
        }
        .onFailure { error ->
          _propAiControlError.value = error.message ?: "Registration failed."
        }
    }
  }

  fun refreshPropAiProfile() {
    val baseUrl = prefs.propAiControlBaseUrl.value.trim()
    val token = prefs.propAiControlToken.value.trim()
    if (baseUrl.isEmpty() || token.isEmpty()) return
    _propAiControlBusy.value = true
    _propAiControlError.value = null
    scope.launch {
      val result = propAiControlClient.me(baseUrl, token)
      _propAiControlBusy.value = false
      result
        .onSuccess { auth ->
          applyPropAiAuth(auth, prefs.propAiControlEmail.value.trim())
          _propAiControlError.value = null
        }
        .onFailure { error ->
          _propAiControlError.value = error.message ?: "Failed to refresh profile."
        }
    }
  }

  fun logoutPropAi() {
    prefs.clearPropAiControl()
    _propAiControlError.value = null
  }

  fun activatePropAiLicense() {
    val baseUrl = prefs.propAiLicenseBaseUrl.value.trim()
    val activationKey = prefs.propAiActivationKey.value.trim()
    if (baseUrl.isEmpty()) {
      _propAiLicenseError.value = "Set the PropAi License API URL."
      return
    }
    if (activationKey.isEmpty()) {
      _propAiLicenseError.value = "Enter an activation key."
      return
    }
    _propAiLicenseBusy.value = true
    _propAiLicenseError.value = null
    val deviceId = prefs.instanceId.value
    val deviceName = DeviceNames.bestDefaultNodeName(appContext)
    val appVersion = BuildConfig.VERSION_NAME
    scope.launch {
      val result =
        propAiLicenseClient.activate(
          baseUrl = baseUrl,
          activationKey = activationKey,
          deviceId = deviceId,
          appVersion = appVersion,
          deviceName = deviceName,
        )
      _propAiLicenseBusy.value = false
      result
        .onSuccess { status ->
          applyPropAiLicenseStatus(status)
        }
        .onFailure { error ->
          _propAiLicenseError.value = error.message ?: "Activation failed."
        }
    }
  }

  fun refreshPropAiLicense() {
    val baseUrl = prefs.propAiLicenseBaseUrl.value.trim()
    val activationToken = prefs.propAiActivationToken.value.trim()
    if (baseUrl.isEmpty() || activationToken.isEmpty()) {
      _propAiLicenseError.value = "Activation token missing. Activate first."
      return
    }
    _propAiLicenseBusy.value = true
    _propAiLicenseError.value = null
    val deviceId = prefs.instanceId.value
    val deviceName = DeviceNames.bestDefaultNodeName(appContext)
    val appVersion = BuildConfig.VERSION_NAME
    scope.launch {
      val result =
        propAiLicenseClient.refresh(
          baseUrl = baseUrl,
          activationToken = activationToken,
          deviceId = deviceId,
          appVersion = appVersion,
          deviceName = deviceName,
        )
      _propAiLicenseBusy.value = false
      result
        .onSuccess { status ->
          applyPropAiLicenseStatus(status)
        }
        .onFailure { error ->
          _propAiLicenseError.value = error.message ?: "License refresh failed."
        }
    }
  }

  fun clearPropAiLicense() {
    prefs.setPropAiActivationToken("")
    prefs.clearPropAiLicenseStatus()
    _propAiLicenseError.value = null
  }
  val lastDiscoveredStableId: StateFlow<String> = prefs.lastDiscoveredStableId
  val canvasDebugStatusEnabled: StateFlow<Boolean> = prefs.canvasDebugStatusEnabled

  private var didAutoConnect = false

  val chatSessionKey: StateFlow<String> = chat.sessionKey
  val chatSessionId: StateFlow<String?> = chat.sessionId
  val chatMessages: StateFlow<List<ChatMessage>> = chat.messages
  val chatError: StateFlow<String?> = chat.errorText
  val chatHealthOk: StateFlow<Boolean> = chat.healthOk
  val chatThinkingLevel: StateFlow<String> = chat.thinkingLevel
  val chatStreamingAssistantText: StateFlow<String?> = chat.streamingAssistantText
  val chatPendingToolCalls: StateFlow<List<ChatPendingToolCall>> = chat.pendingToolCalls
  val chatSessions: StateFlow<List<ChatSessionEntry>> = chat.sessions
  val pendingRunCount: StateFlow<Int> = chat.pendingRunCount

  init {
    if (localSessionMode) {
      _isConnected.value = true
      _nodeConnected.value = true
      voiceSession.onGatewayConnectionChanged(true)
      if (cloudOnly) {
        scope.launch {
          combine(
            prefs.cloudProvider.map { it as Any? },
            prefs.openAiApiKey.map { it as Any? },
            prefs.openAiModel.map { it as Any? },
            prefs.anthropicApiKey.map { it as Any? },
            prefs.anthropicModel.map { it as Any? },
            prefs.groqApiKey.map { it as Any? },
            prefs.groqModel.map { it as Any? },
            prefs.openRouterApiKey.map { it as Any? },
            prefs.openRouterModel.map { it as Any? },
            prefs.elevenLabsApiKey.map { it as Any? },
            prefs.elevenLabsModel.map { it as Any? },
            prefs.propAiLicenseStatus.map { it as Any? },
            prefs.propAiActivationToken.map { it as Any? },
          ) { values ->
            val provider = values[0] as CloudProvider
            val openAiKey = values[1] as String
            val openAiModel = values[2] as String
            val anthropicKey = values[3] as String
            val anthropicModel = values[4] as String
            val groqKey = values[5] as String
            val groqModel = values[6] as String
            val openRouterKey = values[7] as String
            val openRouterModel = values[8] as String
            val elevenLabsKey = values[9] as String
            val elevenLabsModel = values[10] as String
            val propAiStatus = values[11] as PropAiLicenseStatus
            val propAiActivationToken = values[12] as String
            val (key, model) =
              when (provider) {
                CloudProvider.OpenAI -> openAiKey to openAiModel
                CloudProvider.Anthropic -> anthropicKey to anthropicModel
                CloudProvider.Groq -> groqKey to groqModel
                CloudProvider.OpenRouter -> openRouterKey to openRouterModel
                CloudProvider.ElevenLabs -> elevenLabsKey to elevenLabsModel
              }
            val propAiLabel =
              when {
                propAiActivationToken.trim().isEmpty() && !propAiStatus.valid -> "PropAi: Unlinked"
                propAiStatus.valid ->
                  "PropAi: ${propAiStatus.plan ?: propAiStatus.status ?: "Active"}"
                else -> "PropAi: ${propAiStatus.status ?: "Inactive"}"
              }
            when {
              key.trim().isBlank() -> "Cloud: ${provider.label} (missing key) · $propAiLabel"
              model.trim().isBlank() -> "Cloud: ${provider.label} (missing model) · $propAiLabel"
              else -> "Cloud: ${provider.label} · $propAiLabel"
            }
          }.collect { status ->
            _statusText.value = status
          }
        }
      }
    }

    if (prefs.voiceWakeMode.value != VoiceWakeMode.Off) {
      prefs.setVoiceWakeMode(VoiceWakeMode.Off)
    }

    if (!localSessionMode) {
      scope.launch {
        prefs.loadGatewayToken()
      }
    }

    scope.launch {
      prefs.talkEnabled.collect { enabled ->
        // Voice session handles STT + send to gateway or ConvAI streaming.
        voiceSession.setMicEnabled(enabled)
        if (!localSessionMode && enabled) {
          // Mic on = user is on voice screen and wants TTS responses.
          talkMode.ttsOnAllResponses = true
          scope.launch { talkMode.ensureChatSubscribed() }
        }
        externalAudioCaptureActive.value = enabled
      }
    }

    scope.launch {
      val token = prefs.propAiControlToken.value.trim()
      if (token.isNotEmpty()) {
        refreshPropAiProfile()
      }
      val activationToken = prefs.propAiActivationToken.value.trim()
      val licenseUrl = prefs.propAiLicenseBaseUrl.value.trim()
      if (activationToken.isNotEmpty() && licenseUrl.isNotEmpty()) {
        refreshPropAiLicense()
      }
    }

    if (!localSessionMode) {
      scope.launch(Dispatchers.Default) {
        gateways.collect { list ->
          if (list.isNotEmpty()) {
            // Security: don't let an unauthenticated discovery feed continuously steer autoconnect.
            // UX parity with iOS: only set once when unset.
            if (lastDiscoveredStableId.value.trim().isEmpty()) {
              prefs.setLastDiscoveredStableId(list.first().stableId)
            }
          }
          if (didAutoConnect) return@collect
          if (_isConnected.value) return@collect

          if (manualEnabled.value) {
            val host = manualHost.value.trim()
            val port = manualPort.value
            if (host.isNotEmpty() && port in 1..65535) {
              // Security: autoconnect only to previously trusted gateways (stored TLS pin).
              if (!manualTls.value) return@collect
              val stableId = GatewayEndpoint.manual(host = host, port = port).stableId
              val storedFingerprint = prefs.loadGatewayTlsFingerprint(stableId)?.trim().orEmpty()
              if (storedFingerprint.isEmpty()) return@collect

              didAutoConnect = true
              connect(GatewayEndpoint.manual(host = host, port = port))
            }
            return@collect
          }

          val targetStableId = lastDiscoveredStableId.value.trim()
          if (targetStableId.isEmpty()) return@collect
          val target = list.firstOrNull { it.stableId == targetStableId } ?: return@collect

          // Security: autoconnect only to previously trusted gateways (stored TLS pin).
          val storedFingerprint = prefs.loadGatewayTlsFingerprint(target.stableId)?.trim().orEmpty()
          if (storedFingerprint.isEmpty()) return@collect

          didAutoConnect = true
          connect(target)
        }
      }
    }

    scope.launch {
      combine(
        canvasDebugStatusEnabled,
        statusText,
        serverName,
        remoteAddress,
      ) { debugEnabled, status, server, remote ->
        Quad(debugEnabled, status, server, remote)
      }.distinctUntilChanged()
        .collect { (debugEnabled, status, server, remote) ->
          canvas.setDebugStatusEnabled(debugEnabled)
          if (!debugEnabled) return@collect
          canvas.setDebugStatus(status, server ?: remote)
        }
    }
  }

  fun setForeground(value: Boolean) {
    _isForeground.value = value
    if (!value) {
      stopActiveVoiceSession()
    }
  }

  fun setDisplayName(value: String) {
    prefs.setDisplayName(value)
  }

  fun setWakeWords(words: List<String>) {
    prefs.setWakeWords(words)
  }

  fun setCameraEnabled(value: Boolean) {
    prefs.setCameraEnabled(value)
  }

  fun setLocationMode(mode: LocationMode) {
    prefs.setLocationMode(mode)
  }

  fun setLocationPreciseEnabled(value: Boolean) {
    prefs.setLocationPreciseEnabled(value)
  }

  fun setPreventSleep(value: Boolean) {
    prefs.setPreventSleep(value)
  }

  fun setManualEnabled(value: Boolean) {
    prefs.setManualEnabled(value)
  }

  fun setManualHost(value: String) {
    prefs.setManualHost(value)
  }

  fun setManualPort(value: Int) {
    prefs.setManualPort(value)
  }

  fun setManualTls(value: Boolean) {
    prefs.setManualTls(value)
  }

  fun setChatListeningPackages(values: List<String>) {
    prefs.setChatListeningPackages(values)
  }

  fun setChatListeningConversationFilters(values: List<String>) {
    prefs.setChatListeningConversationFilters(values)
  }

  fun setCanvasDebugStatusEnabled(value: Boolean) {
    prefs.setCanvasDebugStatusEnabled(value)
  }

  fun setVoiceScreenActive(active: Boolean) {
    if (!active) {
      stopActiveVoiceSession()
    }
    // Don't re-enable on active=true; mic toggle drives that
  }

  fun setMicEnabled(value: Boolean) {
    prefs.setTalkEnabled(value)
    if (value) {
      // Tapping mic on interrupts any active TTS (barge-in)
      talkMode.stopTts()
      if (!localSessionMode) {
        talkMode.ttsOnAllResponses = true
        scope.launch { talkMode.ensureChatSubscribed() }
      }
    }
    voiceSession.setMicEnabled(value)
    externalAudioCaptureActive.value = value
  }

  val speakerEnabled: StateFlow<Boolean>
    get() = prefs.speakerEnabled

  val elevenLabsApiKey: StateFlow<String>
    get() = prefs.elevenLabsApiKey

  val elevenLabsVoiceId: StateFlow<String>
    get() = prefs.elevenLabsVoiceId

  fun setSpeakerEnabled(value: Boolean) {
    prefs.setSpeakerEnabled(value)
    if (voiceReplySpeakerLazy.isInitialized()) {
      voiceReplySpeaker.setPlaybackEnabled(value)
    }
    // Keep TalkMode in sync so speaker mute works when ttsOnAllResponses is active.
    talkMode.setPlaybackEnabled(value)
  }

  fun setElevenLabsApiKey(value: String) {
    prefs.setElevenLabsApiKey(value)
    if (voiceReplySpeakerLazy.isInitialized()) {
      scope.launch { voiceReplySpeaker.refreshConfig() }
    }
    scope.launch { talkMode.refreshConfig() }
  }

  fun setElevenLabsVoiceId(value: String) {
    prefs.setElevenLabsVoiceId(value)
    if (voiceReplySpeakerLazy.isInitialized()) {
      scope.launch { voiceReplySpeaker.refreshConfig() }
    }
    scope.launch { talkMode.refreshConfig() }
  }

  private fun stopActiveVoiceSession() {
    talkMode.ttsOnAllResponses = false
    talkMode.stopTts()
    voiceSession.setMicEnabled(false)
    prefs.setTalkEnabled(false)
    externalAudioCaptureActive.value = false
  }

  fun refreshGatewayConnection() {
    if (localSessionMode) return
    val endpoint =
      connectedEndpoint ?: run {
        _statusText.value = "Failed: no cached gateway endpoint"
        return
      }
    operatorStatusText = "Connecting…"
    updateStatus()
    val token = prefs.loadGatewayToken()
    val password = prefs.loadGatewayPassword()
    val tls = connectionManager.resolveTlsParams(endpoint)
    operatorSession.connect(endpoint, token, password, connectionManager.buildOperatorConnectOptions(), tls)
    nodeSession.connect(endpoint, token, password, connectionManager.buildNodeConnectOptions(), tls)
    operatorSession.reconnect()
    nodeSession.reconnect()
  }

  fun connect(endpoint: GatewayEndpoint) {
    if (localSessionMode) return
    val tls = connectionManager.resolveTlsParams(endpoint)
    if (tls?.required == true && tls.expectedFingerprint.isNullOrBlank()) {
      // First-time TLS: capture fingerprint, ask user to verify out-of-band, then store and connect.
      _statusText.value = "Verify gateway TLS fingerprint…"
      scope.launch {
        val fp = probeGatewayTlsFingerprint(endpoint.host, endpoint.port) ?: run {
          _statusText.value = "Failed: can't read TLS fingerprint"
          return@launch
        }
        _pendingGatewayTrust.value = GatewayTrustPrompt(endpoint = endpoint, fingerprintSha256 = fp)
      }
      return
    }

    connectedEndpoint = endpoint
    operatorStatusText = "Connecting…"
    nodeStatusText = "Connecting…"
    updateStatus()
    val token = prefs.loadGatewayToken()
    val password = prefs.loadGatewayPassword()
    operatorSession.connect(endpoint, token, password, connectionManager.buildOperatorConnectOptions(), tls)
    nodeSession.connect(endpoint, token, password, connectionManager.buildNodeConnectOptions(), tls)
  }

  fun acceptGatewayTrustPrompt() {
    if (localSessionMode) return
    val prompt = _pendingGatewayTrust.value ?: return
    _pendingGatewayTrust.value = null
    prefs.saveGatewayTlsFingerprint(prompt.endpoint.stableId, prompt.fingerprintSha256)
    connect(prompt.endpoint)
  }

  fun declineGatewayTrustPrompt() {
    if (localSessionMode) return
    _pendingGatewayTrust.value = null
    _statusText.value = "Offline"
  }

  private fun hasRecordAudioPermission(): Boolean {
    return (
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
      )
  }

  fun connectManual() {
    if (localSessionMode) return
    val host = manualHost.value.trim()
    val port = manualPort.value
    if (host.isEmpty() || port <= 0 || port > 65535) {
      _statusText.value = "Failed: invalid manual host/port"
      return
    }
    connect(GatewayEndpoint.manual(host = host, port = port))
  }

  fun disconnect() {
    if (localSessionMode) return
    connectedEndpoint = null
    _pendingGatewayTrust.value = null
    operatorSession.disconnect()
    nodeSession.disconnect()
  }

  fun handleCanvasA2UIActionFromWebView(payloadJson: String) {
    scope.launch {
      if (localSessionMode) return@launch
      val trimmed = payloadJson.trim()
      if (trimmed.isEmpty()) return@launch

      val root =
        try {
          json.parseToJsonElement(trimmed).asObjectOrNull() ?: return@launch
        } catch (_: Throwable) {
          return@launch
        }

      val userActionObj = (root["userAction"] as? JsonObject) ?: root
      val actionId = (userActionObj["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifEmpty {
        java.util.UUID.randomUUID().toString()
      }
      val name = AndroidAssistantCanvasA2UIAction.extractActionName(userActionObj) ?: return@launch

      val surfaceId =
        (userActionObj["surfaceId"] as? JsonPrimitive)?.content?.trim().orEmpty().ifEmpty { "main" }
      val sourceComponentId =
        (userActionObj["sourceComponentId"] as? JsonPrimitive)?.content?.trim().orEmpty().ifEmpty { "-" }
      val contextJson = (userActionObj["context"] as? JsonObject)?.toString()

      val sessionKey = resolveMainSessionKey()
      val message =
        AndroidAssistantCanvasA2UIAction.formatAgentMessage(
          actionName = name,
          sessionKey = sessionKey,
          surfaceId = surfaceId,
          sourceComponentId = sourceComponentId,
          host = displayName.value,
          instanceId = instanceId.value.lowercase(),
          contextJson = contextJson,
        )

      val connected = _nodeConnected.value
      var error: String? = null
      if (connected) {
        val sent =
          nodeSession.sendNodeEvent(
            event = "agent.request",
            payloadJson =
              buildJsonObject {
                put("message", JsonPrimitive(message))
                put("sessionKey", JsonPrimitive(sessionKey))
                put("thinking", JsonPrimitive("low"))
                put("deliver", JsonPrimitive(false))
                put("key", JsonPrimitive(actionId))
              }.toString(),
          )
        if (!sent) {
          error = "send failed"
        }
      } else {
        error = "gateway not connected"
      }

      try {
        canvas.eval(
          AndroidAssistantCanvasA2UIAction.jsDispatchA2UIActionStatus(
            actionId = actionId,
            ok = connected && error == null,
            error = error,
          ),
        )
      } catch (_: Throwable) {
        // ignore
      }
    }
  }

  fun loadChat(sessionKey: String) {
    val key = sessionKey.trim().ifEmpty { resolveMainSessionKey() }
    chat.load(key)
  }

  fun refreshChat() {
    chat.refresh()
  }

  fun refreshChatSessions(limit: Int? = null) {
    chat.refreshSessions(limit = limit)
  }

  fun setChatThinkingLevel(level: String) {
    chat.setThinkingLevel(level)
  }

  fun switchChatSession(sessionKey: String) {
    chat.switchSession(sessionKey)
  }

  fun abortChat() {
    chat.abort()
  }

  fun sendChat(message: String, thinking: String, attachments: List<OutgoingAttachment>) {
    chat.sendMessage(message = message, thinkingLevel = thinking, attachments = attachments)
  }

  private fun handleGatewayEvent(event: String, payloadJson: String?) {
    voiceSession.handleGatewayEvent(event, payloadJson)
    talkMode.handleGatewayEvent(event, payloadJson)
    chat.handleGatewayEvent(event, payloadJson)
  }

  private fun parseChatSendRunId(response: String): String? {
    return try {
      val root = json.parseToJsonElement(response).asObjectOrNull() ?: return null
      root["runId"].asStringOrNull()
    } catch (_: Throwable) {
      null
    }
  }

  private suspend fun refreshBrandingFromGateway() {
    if (!_isConnected.value) return
    try {
      val res = operatorSession.request("config.get", "{}")
      val root = json.parseToJsonElement(res).asObjectOrNull()
      val config = root?.get("config").asObjectOrNull()
      val ui = config?.get("ui").asObjectOrNull()
      val raw = ui?.get("seamColor").asStringOrNull()?.trim()
      val sessionCfg = config?.get("session").asObjectOrNull()
      val mainKey = normalizeMainKey(sessionCfg?.get("mainKey").asStringOrNull())
      applyMainSessionKey(mainKey)

      val parsed = parseHexColorArgb(raw)
      _seamColorArgb.value = parsed ?: DEFAULT_SEAM_COLOR_ARGB
    } catch (_: Throwable) {
      // ignore
    }
  }

  private fun triggerCameraFlash() {
    // Token is used as a pulse trigger; value doesn't matter as long as it changes.
    _cameraFlashToken.value = SystemClock.elapsedRealtimeNanos()
  }

  private fun showCameraHud(message: String, kind: CameraHudKind, autoHideMs: Long? = null) {
    val token = cameraHudSeq.incrementAndGet()
    _cameraHud.value = CameraHudState(token = token, kind = kind, message = message)

    if (autoHideMs != null && autoHideMs > 0) {
      scope.launch {
        delay(autoHideMs)
        if (_cameraHud.value?.token == token) _cameraHud.value = null
      }
    }
  }

}

