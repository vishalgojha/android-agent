package ai.androidassistant.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ai.androidassistant.app.voice.VoiceWakeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class AgentRuntime(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val commandRouter = LocalCommandRouter(appContext)

  private val _statusText = MutableStateFlow("Starting")
  val statusText: StateFlow<String> = _statusText.asStateFlow()

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val voiceWakeManager =
    VoiceWakeManager(
      context = appContext,
      scope = scope,
      onCommand = { command ->
        _statusText.value = "Heard command"
        commandRouter.handleCommand(command)
      },
    )

  init {
    voiceWakeManager.setTriggerWords(loadWakeWords())
    scope.launch {
      voiceWakeManager.statusText.collectLatest { status ->
        _statusText.value = status
      }
    }
    scope.launch {
      voiceWakeManager.isListening.collectLatest { listening ->
        _isListening.value = listening
      }
    }
  }

  fun start() {
    if (!hasMicPermission()) {
      voiceWakeManager.stop("Microphone permission required")
      _statusText.value = "Microphone permission required"
      return
    }
    voiceWakeManager.start()
  }

  fun stop() {
    voiceWakeManager.stop("Off")
  }

  fun shutdown() {
    stop()
    commandRouter.shutdown()
    scope.cancel()
  }

  fun hasMicPermission(): Boolean {
    return ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  }

  private fun loadWakeWords(): List<String> {
    val words = appContext.resources.getStringArray(R.array.wake_words).toList()
    return words.map { it.trim() }.filter { it.isNotEmpty() }
  }
}

