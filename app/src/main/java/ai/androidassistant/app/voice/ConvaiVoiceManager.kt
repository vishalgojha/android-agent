package ai.androidassistant.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import ai.androidassistant.app.SecurePrefs
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class ConvaiVoiceManager(
  private val context: Context,
  private val scope: CoroutineScope,
  private val prefs: SecurePrefs,
  private val httpClient: OkHttpClient = OkHttpClient(),
) : VoiceSession {
  companion object {
    private const val tag = "ConvaiVoice"
    private const val defaultSampleRate = 16000
    private const val maxConversationEntries = 40
  }

  private val _micEnabled = MutableStateFlow(false)
  override val micEnabled: StateFlow<Boolean> = _micEnabled

  private val _micCooldown = MutableStateFlow(false)
  override val micCooldown: StateFlow<Boolean> = _micCooldown

  private val _isListening = MutableStateFlow(false)
  override val isListening: StateFlow<Boolean> = _isListening

  private val _statusText = MutableStateFlow("Mic off")
  override val statusText: StateFlow<String> = _statusText

  private val _liveTranscript = MutableStateFlow<String?>(null)
  override val liveTranscript: StateFlow<String?> = _liveTranscript

  private val _queuedMessages = MutableStateFlow<List<String>>(emptyList())
  override val queuedMessages: StateFlow<List<String>> = _queuedMessages

  private val _conversation = MutableStateFlow<List<VoiceConversationEntry>>(emptyList())
  override val conversation: StateFlow<List<VoiceConversationEntry>> = _conversation

  private val _inputLevel = MutableStateFlow(0f)
  override val inputLevel: StateFlow<Float> = _inputLevel

  private val _isSending = MutableStateFlow(false)
  override val isSending: StateFlow<Boolean> = _isSending

  private var ws: WebSocket? = null
  private var audioRecord: AudioRecord? = null
  private var recordJob: Job? = null
  private var playbackTrack: AudioTrack? = null
  private var playbackSampleRate: Int = defaultSampleRate
  @Volatile private var playbackEnabled: Boolean = true
  private var pendingAssistantId: String? = null

  init {
    scope.launch {
      prefs.speakerEnabled.collect { enabled ->
        playbackEnabled = enabled
        if (!enabled) {
          stopPlayback()
        }
      }
    }
  }

  override fun setMicEnabled(enabled: Boolean) {
    if (_micEnabled.value == enabled) return
    _micEnabled.value = enabled
    if (enabled) {
      start()
    } else {
      stop()
    }
  }

  private fun start() {
    if (!hasMicPermission()) {
      _statusText.value = "Microphone permission required"
      _micEnabled.value = false
      return
    }
    val apiKey = prefs.elevenLabsApiKey.value.trim()
    val agentId = prefs.elevenLabsAgentId.value.trim()
    if (apiKey.isEmpty()) {
      _statusText.value = "ElevenLabs API key required"
      _micEnabled.value = false
      return
    }
    if (agentId.isEmpty()) {
      _statusText.value = "ElevenLabs agent ID required"
      _micEnabled.value = false
      return
    }

    _statusText.value = "Connecting…"
    _isListening.value = false
    _liveTranscript.value = null
    _micCooldown.value = false

    scope.launch {
      try {
        val signedUrl = fetchSignedUrl(apiKey, agentId)
        openSocket(signedUrl)
      } catch (err: Throwable) {
        _statusText.value = "Voice connect failed: ${err.message ?: err::class.simpleName}"
        _micEnabled.value = false
      }
    }
  }

  private fun stop() {
    _micCooldown.value = true
    _isListening.value = false
    _statusText.value = "Mic off"
    _liveTranscript.value = null
    recordJob?.cancel()
    recordJob = null
    runCatching { audioRecord?.stop() }
    runCatching { audioRecord?.release() }
    audioRecord = null
    ws?.close(1000, "mic off")
    ws = null
    stopPlayback()
    scope.launch {
      delay(300)
      _micCooldown.value = false
    }
  }

  private fun openSocket(signedUrl: String) {
    val request = Request.Builder().url(signedUrl).build()
    ws =
      httpClient.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(tag, "convai socket open")
            val initPayload =
              JSONObject()
                .put("type", "conversation_initiation_client_data")
                .put(
                  "conversation_config_override",
                  JSONObject()
                    .put("conversation", JSONObject().put("text_only", false))
                    .put(
                      "audio",
                      JSONObject()
                        .put("sample_rate", defaultSampleRate)
                        .put("encoding", "pcm_s16le")
                        .put("channels", 1),
                    ),
                )
            webSocket.send(initPayload.toString())
            startAudioCapture(webSocket)
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            handleSocketMessage(text)
          }

          override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(tag, "convai socket failure: ${t.message}")
            _statusText.value = "Voice connection failed"
            _isListening.value = false
            _micEnabled.value = false
          }

          override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(tag, "convai socket closed: $code $reason")
            _isListening.value = false
            _statusText.value = "Disconnected"
          }
        },
      )
  }

  private fun startAudioCapture(socket: WebSocket) {
    val record =
      startAudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        ?: startAudioRecord(MediaRecorder.AudioSource.MIC)
    if (record == null) {
      _statusText.value = "Microphone unavailable"
      _micEnabled.value = false
      return
    }
    audioRecord = record
    _isListening.value = true
    _statusText.value = "Listening"

    recordJob?.cancel()
    recordJob =
      scope.launch(Dispatchers.IO) {
        val chunkSize = 640
        val buffer = ByteArray(chunkSize)
        var errorReads = 0
        while (_micEnabled.value && !Thread.currentThread().isInterrupted) {
          val read = record.read(buffer, 0, buffer.size)
          if (read > 0) {
            errorReads = 0
            updateInputLevel(buffer, read)
            val encoded = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
            val audioMsg =
              JSONObject()
                .put("type", "user_audio_chunk")
                .put("audio", encoded)
                .put("audio_base_64", encoded)
                .put(
                  "audio_format",
                  JSONObject()
                    .put("sample_rate", defaultSampleRate)
                    .put("encoding", "pcm_s16le")
                    .put("channels", 1),
                )
            socket.send(audioMsg.toString())
          } else if (read < 0) {
            errorReads += 1
            if (errorReads >= 3) {
              Log.w(tag, "AudioRecord read error: $read")
              _statusText.value = "Microphone error"
              _micEnabled.value = false
              break
            }
          }
        }
        socket.send(JSONObject().put("type", "user_audio_end").toString())
      }
  }

  private fun startAudioRecord(source: Int): AudioRecord? {
    val record = createAudioRecord(source) ?: return null
    return try {
      record.startRecording()
      record
    } catch (err: Throwable) {
      Log.w(tag, "Failed to start recording (source=$source)", err)
      record.release()
      null
    }
  }

  private fun createAudioRecord(source: Int): AudioRecord? {
    val minBuffer =
      AudioRecord.getMinBufferSize(
        defaultSampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
      return null
    }
    val bufferSize = maxOf(minBuffer, defaultSampleRate / 2)
    val record =
      AudioRecord(
        source,
        defaultSampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
      )
    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      return null
    }
    return record
  }

  private fun handleSocketMessage(raw: String) {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
    when (json.optString("type")) {
      "ping" -> {
        val eventId = json.optJSONObject("ping_event")?.optLong("event_id") ?: return
        ws?.send(JSONObject().put("type", "pong").put("event_id", eventId).toString())
      }
      "user_transcript" -> {
        val event = json.optJSONObject("user_transcript_event")
        val text = event?.optString("user_transcript")?.trim().orEmpty()
        val isFinal = event?.optBoolean("is_final") ?: false
        if (text.isNotEmpty()) {
          if (isFinal) {
            _liveTranscript.value = null
            appendConversation(role = VoiceConversationRole.User, text = text, isStreaming = false)
            _isSending.value = true
          } else {
            _liveTranscript.value = text
          }
        }
      }
      "agent_response", "agent_response_chunk" -> {
        val event = json.optJSONObject("agent_response_event")
        val text = event?.optString("agent_response")?.trim().orEmpty()
        if (text.isNotEmpty()) {
          upsertAssistant(text, isFinal = json.optString("type") == "agent_response")
        }
      }
      "agent_response_correction" -> {
        val event = json.optJSONObject("agent_response_correction_event")
        val text = event?.optString("corrected_agent_response")?.trim().orEmpty()
        if (text.isNotEmpty()) {
          upsertAssistant(text, isFinal = true)
        }
      }
      "audio", "audio_chunk", "agent_audio" -> {
        playAudioFromJson(json)
      }
      else -> {
        // Some servers wrap events; attempt to parse audio if present.
        if (json.has("audio") || json.optJSONObject("audio_event") != null) {
          playAudioFromJson(json)
        }
      }
    }
  }

  private fun playAudioFromJson(json: JSONObject) {
    if (!playbackEnabled) return
    val audioEvent = json.optJSONObject("audio_event")
    val base64 =
      when {
        audioEvent != null -> audioEvent.optString("audio_base_64").ifBlank { audioEvent.optString("audio") }
        else -> json.optString("audio_base_64").ifBlank { json.optString("audio") }
      }
    if (base64.isNullOrBlank()) return
    val sampleRate = resolveSampleRate(json, audioEvent)
    val decoded =
      try {
        Base64.decode(base64, Base64.DEFAULT)
      } catch (_: Throwable) {
        return
      }
    playPcm(decoded, sampleRate)
  }

  private fun resolveSampleRate(json: JSONObject, audioEvent: JSONObject?): Int {
    val format = audioEvent?.optJSONObject("audio_format") ?: json.optJSONObject("audio_format")
    val rate =
      when {
        format != null -> format.optInt("sample_rate", 0)
        audioEvent != null -> audioEvent.optInt("sample_rate_hz", 0)
        else -> json.optInt("sample_rate_hz", 0)
      }
    return if (rate > 0) rate else defaultSampleRate
  }

  private fun playPcm(data: ByteArray, sampleRate: Int) {
    if (!playbackEnabled) return
    if (playbackTrack == null || playbackSampleRate != sampleRate) {
      stopPlayback()
      playbackSampleRate = sampleRate
      val minBuffer =
        AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build(),
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build(),
          )
          .setBufferSizeInBytes(minBuffer)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      playbackTrack = track
      track.play()
    }
    playbackTrack?.write(data, 0, data.size)
    _isSending.value = false
  }

  private fun stopPlayback() {
    playbackTrack?.stop()
    playbackTrack?.release()
    playbackTrack = null
  }

  private fun upsertAssistant(text: String, isFinal: Boolean) {
    if (pendingAssistantId == null) {
      pendingAssistantId =
        appendConversation(
          role = VoiceConversationRole.Assistant,
          text = text,
          isStreaming = !isFinal,
        )
    } else {
      updateConversationEntry(pendingAssistantId!!, text, isStreaming = !isFinal)
    }
    if (isFinal) {
      pendingAssistantId = null
      _isSending.value = false
    }
  }

  private fun appendConversation(
    role: VoiceConversationRole,
    text: String,
    isStreaming: Boolean,
  ): String {
    val id = UUID.randomUUID().toString()
    _conversation.value =
      (_conversation.value + VoiceConversationEntry(id = id, role = role, text = text, isStreaming = isStreaming))
        .takeLast(maxConversationEntries)
    return id
  }

  private fun updateConversationEntry(id: String, text: String, isStreaming: Boolean) {
    val current = _conversation.value
    if (current.isEmpty()) return
    _conversation.value =
      current.map { entry ->
        if (entry.id == id) entry.copy(text = text, isStreaming = isStreaming) else entry
      }
  }

  private fun updateInputLevel(buffer: ByteArray, read: Int) {
    if (read <= 0) return
    var sum = 0.0
    var i = 0
    while (i + 1 < read) {
      val sample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort().toInt()
      sum += sample * sample.toDouble()
      i += 2
    }
    val rms = sqrt(sum / (read / 2.0))
    val level = (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
    _inputLevel.value = level
  }

  private fun hasMicPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
  }

  private suspend fun fetchSignedUrl(apiKey: String, agentId: String): String {
    val url = "https://api.elevenlabs.io/v1/convai/conversation/get-signed-url?agent_id=$agentId"
    val request =
      Request.Builder()
        .url(url)
        .get()
        .header("xi-api-key", apiKey)
        .header("Accept", "application/json")
        .build()

    return withContext(Dispatchers.IO) {
      httpClient.newCall(request).execute().use { response ->
        val bodyText = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IllegalStateException("ElevenLabs signed URL failed: ${response.code}")
        }
        val json = JSONObject(bodyText)
        json.optString("signed_url").trim().ifEmpty {
          throw IllegalStateException("ElevenLabs signed URL missing")
        }
      }
    }
  }
}
