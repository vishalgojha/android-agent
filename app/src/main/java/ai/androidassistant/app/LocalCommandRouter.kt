package ai.androidassistant.app

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import ai.androidassistant.app.node.DeviceHandler
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

class LocalCommandRouter(
  context: Context,
) : TextToSpeech.OnInitListener {
  private val appContext = context.applicationContext
  private val deviceHandler = DeviceHandler(appContext)
  private val tts = TextToSpeech(appContext, this)
  private val ttsReady = CompletableDeferred<Boolean>()
  private val json = Json { ignoreUnknownKeys = true }

  override fun onInit(status: Int) {
    val ok = status == TextToSpeech.SUCCESS
    if (ok) {
      tts.language = Locale.US
    }
    if (!ttsReady.isCompleted) {
      ttsReady.complete(ok)
    }
  }

  suspend fun handleCommand(command: String) {
    val normalized = command.trim().lowercase()
    when {
      normalized.isEmpty() -> return
      normalized.startsWith("help") || normalized.contains("what can you do") -> {
        speak("I can tell you the time, date, or battery level. Try saying: what's the battery.")
      }
      normalized.startsWith("time") || normalized.contains(" time") -> {
        val now = LocalTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        speak("It's $formatted.")
      }
      normalized.startsWith("date") || normalized.contains(" date") || normalized.contains(" day") -> {
        val today = LocalDate.now()
        val formatted = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
        speak("Today is $formatted.")
      }
      normalized.contains("battery") -> {
        val response = buildBatteryResponse()
        speak(response)
      }
      normalized.startsWith("say ") -> {
        val text = command.drop(4).trim()
        if (text.isNotEmpty()) {
          speak(text)
        }
      }
      else -> {
        speak("Sorry, I can only help with time, date, or battery right now.")
      }
    }
  }

  fun shutdown() {
    tts.shutdown()
  }

  private suspend fun speak(text: String) {
    val ok = ttsReady.await()
    if (!ok) return
    withContext(Dispatchers.Main) {
      tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-${SystemClock.uptimeMillis()}")
    }
  }

  private fun buildBatteryResponse(): String {
    val result = deviceHandler.handleDeviceStatus(null)
    if (!result.ok || result.payloadJson.isNullOrBlank()) {
      return "I couldn't read the battery status."
    }

    val payload =
      try {
        json.parseToJsonElement(result.payloadJson).jsonObject
      } catch (_: Throwable) {
        return "I couldn't read the battery status."
      }
    val battery = payload["battery"] as? JsonObject ?: return "Battery status is unavailable."
    val level = (battery["level"] as? JsonPrimitive)?.doubleOrNull
    val percent = level?.let { (it * 100).roundToInt().coerceIn(0, 100) }
    val state = (battery["state"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.trim()

    return when {
      percent == null && state.isNullOrEmpty() -> "Battery status is unavailable."
      percent != null && !state.isNullOrEmpty() -> "Battery is at $percent percent and $state."
      percent != null -> "Battery is at $percent percent."
      else -> "Battery state is $state."
    }
  }
}

