package ai.androidassistant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ai.androidassistant.app.ui.PropAiSyncTheme
import ai.androidassistant.app.ui.RootScreen

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      PropAiSyncTheme {
        RootScreen(viewModel = viewModel)
      }
    }
  }
}

