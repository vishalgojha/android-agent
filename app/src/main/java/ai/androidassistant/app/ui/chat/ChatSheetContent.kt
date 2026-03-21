package ai.androidassistant.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.androidassistant.app.MainViewModel
import ai.androidassistant.app.chat.OutgoingAttachment
import ai.androidassistant.app.ui.mobileBorderStrong
import ai.androidassistant.app.ui.mobileCallout
import ai.androidassistant.app.ui.mobileCaption2
import ai.androidassistant.app.ui.mobileDanger
import ai.androidassistant.app.ui.mobileSurfaceStrong
import ai.androidassistant.app.ui.mobileText
import ai.androidassistant.app.ui.mobileTextSecondary
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun ChatSheetContent(viewModel: MainViewModel) {
  val messages by viewModel.chatMessages.collectAsState()
  val errorText by viewModel.chatError.collectAsState()
  val pendingRunCount by viewModel.pendingRunCount.collectAsState()
  val sessionKey by viewModel.chatSessionKey.collectAsState()
  val mainSessionKey by viewModel.mainSessionKey.collectAsState()
  val thinkingLevel by viewModel.chatThinkingLevel.collectAsState()
  val streamingAssistantText by viewModel.chatStreamingAssistantText.collectAsState()
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val micEnabled by viewModel.micEnabled.collectAsState()
  val micIsListening by viewModel.micIsListening.collectAsState()
  val micCooldown by viewModel.micCooldown.collectAsState()
  val micLiveTranscript by viewModel.micLiveTranscript.collectAsState()
  val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
  val welcomeMessageSent by viewModel.welcomeMessageSent.collectAsState()

  LaunchedEffect(mainSessionKey) {
    viewModel.loadChat(mainSessionKey)
    viewModel.refreshChatSessions(limit = 200)
  }

  LaunchedEffect(onboardingCompleted, welcomeMessageSent, messages.size) {
    if (onboardingCompleted && !welcomeMessageSent && messages.isEmpty()) {
      viewModel.showWelcomeMessageIfNeeded()
    }
  }

  val context = LocalContext.current
  val view = LocalView.current
  val density = LocalDensity.current
  val bottomInsetPxState = remember { mutableStateOf(0) }
  val lifecycleOwner = LocalLifecycleOwner.current
  val resolver = context.contentResolver
  val scope = rememberCoroutineScope()
  var hasMicPermission by remember { mutableStateOf(context.hasRecordAudioPermission()) }
  var pendingMicEnable by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner, context) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          hasMicPermission = context.hasRecordAudioPermission()
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val requestMicPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted && pendingMicEnable) {
        viewModel.setMicEnabled(true)
      }
      pendingMicEnable = false
    }

  val attachments = remember { mutableStateListOf<PendingImageAttachment>() }
  var inputText by rememberSaveable(sessionKey) { mutableStateOf("") }
  LaunchedEffect(micLiveTranscript, micEnabled, sessionKey) {
    if (micEnabled && !micLiveTranscript.isNullOrBlank() && inputText.isBlank()) {
      inputText = micLiveTranscript!!.trim()
    }
  }

  val pickImages =
    rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
      if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
      scope.launch(Dispatchers.IO) {
        val next =
          uris.take(8).mapNotNull { uri ->
            try {
              loadImageAttachment(resolver, uri)
            } catch (_: Throwable) {
              null
            }
          }
        withContext(Dispatchers.Main) {
          attachments.addAll(next)
        }
      }
    }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp, vertical = 2.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (!errorText.isNullOrBlank()) {
      ChatErrorRail(errorText = errorText!!)
    }

    ChatMessageListCard(
      messages = messages,
      pendingRunCount = pendingRunCount,
      pendingToolCalls = pendingToolCalls,
      streamingAssistantText = streamingAssistantText,
      modifier = Modifier.weight(1f, fill = true),
    )

    DisposableEffect(view) {
      val listener =
        androidx.core.view.OnApplyWindowInsetsListener { _, insets ->
          val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
          val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
          bottomInsetPxState.value = max(ime, nav)
          insets
        }
      ViewCompat.setOnApplyWindowInsetsListener(view, listener)
      ViewCompat.requestApplyInsets(view)
      onDispose { ViewCompat.setOnApplyWindowInsetsListener(view, null) }
    }

    val bottomInsetDp = with(density) { bottomInsetPxState.value.toDp() }

    Row(modifier = Modifier.fillMaxWidth().padding(bottom = bottomInsetDp)) {
      ChatComposer(
        inputText = inputText,
        onInputChange = { inputText = it },
        pendingRunCount = pendingRunCount,
        attachments = attachments,
        micEnabled = micEnabled,
        micIsListening = micIsListening,
        micCooldown = micCooldown,
        onToggleMic = {
          if (hasMicPermission) {
            viewModel.setMicEnabled(!micEnabled)
          } else {
            pendingMicEnable = true
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
          }
        },
        onPickImages = { pickImages.launch("image/*") },
        onRemoveAttachment = { id -> attachments.removeAll { it.id == id } },
        onSend = { text ->
          val outgoing =
            attachments.map { att ->
              OutgoingAttachment(
                type = "image",
                mimeType = att.mimeType,
                fileName = att.fileName,
                base64 = att.base64,
              )
            }
          viewModel.sendChat(message = text, thinking = thinkingLevel, attachments = outgoing)
          attachments.clear()
          inputText = ""
        },
      )
    }
  }
}

@Composable
private fun ChatErrorRail(errorText: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = mobileSurfaceStrong.copy(alpha = 0.96f),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, mobileDanger),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = "CHAT ERROR",
        style = mobileCaption2.copy(letterSpacing = 0.6.sp),
        color = mobileDanger,
      )
      Text(text = errorText, style = mobileCallout, color = mobileText)
    }
  }
}

private fun Context.hasRecordAudioPermission(): Boolean {
  return (
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    )
}

private fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

data class PendingImageAttachment(
  val id: String,
  val fileName: String,
  val mimeType: String,
  val base64: String,
)

private suspend fun loadImageAttachment(resolver: ContentResolver, uri: Uri): PendingImageAttachment {
  val mimeType = resolver.getType(uri) ?: "image/*"
  val fileName = (uri.lastPathSegment ?: "image").substringAfterLast('/')
  val bytes =
    withContext(Dispatchers.IO) {
      resolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        input.copyTo(out)
        out.toByteArray()
      } ?: ByteArray(0)
    }
  if (bytes.isEmpty()) throw IllegalStateException("empty attachment")
  val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
  return PendingImageAttachment(
    id = uri.toString() + "#" + System.currentTimeMillis().toString(),
    fileName = fileName,
    mimeType = mimeType,
    base64 = base64,
  )
}

