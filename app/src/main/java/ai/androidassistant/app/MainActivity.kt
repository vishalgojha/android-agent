package ai.androidassistant.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import ai.androidassistant.app.ui.AndroidAssistantTheme
import ai.androidassistant.app.ui.RootScreen

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      maybeStartAgentService()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    maybeStartAgentService()
    setContent {
      AndroidAssistantTheme {
        RootScreen(viewModel = viewModel)
      }
    }
    if (!hasAllRequiredPermissions()) {
      permissionLauncher.launch(requiredPermissions().toTypedArray())
    }
  }

  override fun onResume() {
    super.onResume()
    maybeStartAgentService()
  }

  private fun hasAllRequiredPermissions(): Boolean {
    return requiredPermissions().all { perm ->
      ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun maybeStartAgentService() {
    if (hasAllRequiredPermissions()) {
      AgentForegroundService.start(this)
    }
  }

  private fun requiredPermissions(): List<String> {
    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return permissions
  }
}

