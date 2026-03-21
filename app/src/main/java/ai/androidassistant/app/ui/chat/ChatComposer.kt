package ai.androidassistant.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.androidassistant.app.ui.mobileAccent
import ai.androidassistant.app.ui.mobileBorderStrong
import ai.androidassistant.app.ui.mobileCaption1
import ai.androidassistant.app.ui.mobileSurface
import ai.androidassistant.app.ui.mobileSurfaceStrong
import ai.androidassistant.app.ui.mobileText
import ai.androidassistant.app.ui.mobileTextSecondary
import ai.androidassistant.app.ui.mobileTextTertiary

@Composable
fun ChatComposer(
  inputText: String,
  onInputChange: (String) -> Unit,
  pendingRunCount: Int,
  attachments: List<PendingImageAttachment>,
  micEnabled: Boolean,
  micIsListening: Boolean,
  micCooldown: Boolean,
  onToggleMic: () -> Unit,
  onPickImages: () -> Unit,
  onRemoveAttachment: (id: String) -> Unit,
  onSend: (text: String) -> Unit,
) {
  val canSend = pendingRunCount == 0 && (inputText.trim().isNotEmpty() || attachments.isNotEmpty())
  val micActive = micEnabled || micIsListening
  val sendNow = {
    if (canSend) {
      val text = inputText
      onInputChange("")
      onSend(text)
    }
  }

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {

    if (attachments.isNotEmpty()) {
      AttachmentsStrip(attachments = attachments, onRemoveAttachment = onRemoveAttachment)
    }

    OutlinedTextField(
      value = inputText,
      onValueChange = onInputChange,
      modifier =
        Modifier
          .fillMaxWidth()
          .height(56.dp)
          .onPreviewKeyEvent { event ->
            val nativeEvent = event.nativeKeyEvent
            if (nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_ENTER && nativeEvent.action == AndroidKeyEvent.ACTION_UP) {
              sendNow()
              true
            } else {
              false
            }
          },
      placeholder = { Text("Message PropAi Sync", style = mobileBodyStyle(), color = mobileTextTertiary) },
      minLines = 1,
      maxLines = 3,
      textStyle = mobileBodyStyle().copy(color = mobileText),
      shape = RoundedCornerShape(28.dp),
      colors = chatTextFieldColors(),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions =
        KeyboardActions(
          onSend = { sendNow() },
        ),
      trailingIcon = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onPickImages,
            enabled = true,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = mobileSurfaceStrong),
          ) {
            Icon(
              imageVector = Icons.Default.AttachFile,
              contentDescription = "Attach",
              tint = mobileTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onToggleMic,
            enabled = !micCooldown,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = mobileSurfaceStrong),
          ) {
            Icon(
              imageVector = if (micActive) Icons.Default.Mic else Icons.Default.MicOff,
              contentDescription = if (micActive) "Mic on" else "Mic off",
              tint = if (micActive) mobileText else mobileTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      },
    )

  }
}

@Composable
private fun AttachmentsStrip(
  attachments: List<PendingImageAttachment>,
  onRemoveAttachment: (id: String) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (att in attachments) {
      AttachmentChip(
        fileName = att.fileName,
        onRemove = { onRemoveAttachment(att.id) },
      )
    }
  }
}

@Composable
private fun AttachmentChip(fileName: String, onRemove: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = mobileSurfaceStrong,
    border = BorderStroke(1.dp, mobileBorderStrong),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = fileName,
        style = mobileCaption1,
        color = mobileText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(999.dp),
        color = mobileSurfaceStrong,
        border = BorderStroke(1.dp, mobileBorderStrong),
      ) {
        Text(
          text = "×",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
          color = mobileTextSecondary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun chatTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedContainerColor = mobileSurfaceStrong,
    unfocusedContainerColor = mobileSurfaceStrong,
    focusedBorderColor = mobileBorderStrong,
    unfocusedBorderColor = mobileBorderStrong,
    focusedTextColor = mobileText,
    unfocusedTextColor = mobileText,
    cursorColor = mobileAccent,
  )

@Composable
private fun mobileBodyStyle() =
  MaterialTheme.typography.bodyMedium.copy(
    fontFamily = ai.androidassistant.app.ui.mobileFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 22.sp,
  )

