package ai.androidassistant.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.androidassistant.app.chat.ChatMessage
import ai.androidassistant.app.chat.ChatPendingToolCall
import ai.androidassistant.app.ui.mobileCallout
import ai.androidassistant.app.ui.mobileHeadline
import ai.androidassistant.app.ui.mobileText
import ai.androidassistant.app.ui.mobileTextSecondary

@Composable
fun ChatMessageListCard(
  messages: List<ChatMessage>,
  pendingRunCount: Int,
  pendingToolCalls: List<ChatPendingToolCall>,
  streamingAssistantText: String?,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()

  // With reverseLayout the newest item is at index 0 (bottom of screen).
  LaunchedEffect(messages.size, pendingRunCount, pendingToolCalls.size, streamingAssistantText) {
    listState.animateScrollToItem(index = 0)
  }

  Box(modifier = modifier.fillMaxWidth()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      state = listState,
      reverseLayout = true,
      verticalArrangement = Arrangement.spacedBy(6.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 2.dp),
    ) {
      // With reverseLayout = true, index 0 renders at the BOTTOM.
      // So we emit newest items first: streaming → tools → typing → messages (newest→oldest).

      val stream = streamingAssistantText?.trim()
      if (!stream.isNullOrEmpty()) {
        item(key = "stream") {
          ChatStreamingAssistantBubble(text = stream)
        }
      }

      if (pendingToolCalls.isNotEmpty()) {
        item(key = "tools") {
          ChatPendingToolsBubble(toolCalls = pendingToolCalls)
        }
      }

      if (pendingRunCount > 0) {
        item(key = "typing") {
          ChatTypingIndicatorBubble()
        }
      }

      items(count = messages.size, key = { idx -> messages[messages.size - 1 - idx].id }) { idx ->
        ChatMessageBubble(message = messages[messages.size - 1 - idx])
      }
    }

    if (messages.isEmpty() && pendingRunCount == 0 && pendingToolCalls.isEmpty() && streamingAssistantText.isNullOrBlank()) {
      EmptyChatHint(modifier = Modifier.align(Alignment.Center))
    }
  }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
  androidx.compose.foundation.layout.Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("No messages yet", style = mobileHeadline, color = mobileText)
    Text(
      text = "Send the first prompt to start this session.",
      style = mobileCallout,
      color = mobileTextSecondary,
    )
  }
}

