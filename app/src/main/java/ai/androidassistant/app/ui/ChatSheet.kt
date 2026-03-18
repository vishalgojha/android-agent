package ai.androidassistant.app.ui

import androidx.compose.runtime.Composable
import ai.androidassistant.app.MainViewModel
import ai.androidassistant.app.ui.chat.ChatSheetContent

@Composable
fun ChatSheet(viewModel: MainViewModel) {
  ChatSheetContent(viewModel = viewModel)
}

