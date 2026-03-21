package ai.androidassistant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.androidassistant.app.MainViewModel
import ai.androidassistant.app.NodeApp
import ai.androidassistant.app.CloudProvider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.HorizontalDivider

private enum class HomeTab(
  val label: String,
  val icon: ImageVector,
) {
  Chat(label = "Chat", icon = Icons.Default.ChatBubble),
  Screen(label = "Screen", icon = Icons.AutoMirrored.Filled.ScreenShare),
  Auto(label = "Auto", icon = Icons.Default.AutoFixHigh),
  Settings(label = "Settings", icon = Icons.Default.Settings),
}

private enum class StatusVisual {
  Connected,
  Connecting,
  Warning,
  Error,
  Offline,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostOnboardingTabs(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  var activeTab by rememberSaveable { mutableStateOf(HomeTab.Chat) }
  var automationBuilderVisible by rememberSaveable { mutableStateOf(false) }
  var menuSheetVisible by rememberSaveable { mutableStateOf(false) }
  val appContext = LocalContext.current.applicationContext as NodeApp
  val automationViewModel = remember { AutomationViewModel(appContext) }

  // Stop TTS when user navigates away from voice tab
  LaunchedEffect(activeTab) {
    viewModel.setVoiceScreenActive(activeTab == HomeTab.Chat)
  }

  val statusText by viewModel.statusText.collectAsState()
  val displayName by viewModel.displayName.collectAsState()
  val cloudProvider by viewModel.cloudProvider.collectAsState()

  val statusVisual =
    remember(statusText) {
      val lower = statusText.lowercase()
      when {
        lower.contains("missing key") -> StatusVisual.Warning
        lower.contains("loading") || lower.contains("initializing") -> StatusVisual.Connecting
        lower.contains("fallback") || lower.contains("unavailable") -> StatusVisual.Warning
        lower.contains("error") || lower.contains("failed") -> StatusVisual.Error
        lower.contains("offline") -> StatusVisual.Offline
        lower.contains("ready") || lower.startsWith("cloud:") -> StatusVisual.Connected
        else -> StatusVisual.Connected
      }
    }


  val providerLabel =
    when (cloudProvider) {
      CloudProvider.Anthropic -> "Claude"
      else -> cloudProvider.label
    }

  val providerState =
    when (statusVisual) {
      StatusVisual.Connected -> "Connected"
      StatusVisual.Connecting -> "Connecting"
      StatusVisual.Warning -> "Needs setup"
      StatusVisual.Error -> "Issue"
      StatusVisual.Offline -> "Offline"
    }

  Scaffold(
    modifier = modifier,
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopStatusBar(
        statusText = "$providerLabel · $providerState",
        statusVisual = statusVisual,
        agentName = displayName,
        onMenuClick = { menuSheetVisible = true },
      )
    },
    bottomBar = {},
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .consumeWindowInsets(innerPadding)
          .background(Color.Black),
    ) {
      when (activeTab) {
        HomeTab.Chat -> ChatSheet(viewModel = viewModel)
        HomeTab.Screen -> ScreenTabScreen(viewModel = viewModel)
        HomeTab.Auto -> {
          if (automationBuilderVisible) {
            RuleBuilderScreen(
              viewModel = automationViewModel,
              onNavigateBack = { automationBuilderVisible = false },
            )
          } else {
            AutomationTab(
              viewModel = automationViewModel,
              onNavigateToBuilder = { automationBuilderVisible = true },
            )
          }
        }
        HomeTab.Settings -> SettingsSheet(viewModel = viewModel)
      }
    }

    if (menuSheetVisible) {
      ModalBottomSheet(
        onDismissRequest = { menuSheetVisible = false },
        containerColor = mobileSurfaceStrong,
      ) {
        MenuSheetItem(
          label = "Chat",
          onClick = {
            activeTab = HomeTab.Chat
            menuSheetVisible = false
          },
        )
        MenuSheetItem(
          label = "Screen",
          onClick = {
            activeTab = HomeTab.Screen
            menuSheetVisible = false
          },
        )
        MenuSheetItem(
          label = "Auto",
          onClick = {
            activeTab = HomeTab.Auto
            menuSheetVisible = false
          },
        )
        MenuSheetItem(
          label = "Settings",
          onClick = {
            activeTab = HomeTab.Settings
            menuSheetVisible = false
          },
        )
        HorizontalDivider(color = mobileBorderStrong, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.height(10.dp))
      }
    }
  }
}

@Composable
private fun ScreenTabScreen(viewModel: MainViewModel) {
  val isConnected by viewModel.isConnected.collectAsState()
  val isNodeConnected by viewModel.isNodeConnected.collectAsState()
  val canvasUrl by viewModel.canvasCurrentUrl.collectAsState()
  val canvasA2uiHydrated by viewModel.canvasA2uiHydrated.collectAsState()
  val canvasRehydratePending by viewModel.canvasRehydratePending.collectAsState()
  val canvasRehydrateErrorText by viewModel.canvasRehydrateErrorText.collectAsState()
  val isA2uiUrl = canvasUrl?.contains("/__androidassistant__/a2ui/") == true
  val showRestoreCta =
    isConnected &&
      isNodeConnected &&
      (canvasUrl.isNullOrBlank() || (isA2uiUrl && !canvasA2uiHydrated))
  val restoreCtaText =
    when {
      canvasRehydratePending -> "Restore requested. Waiting for agent…"
      !canvasRehydrateErrorText.isNullOrBlank() -> canvasRehydrateErrorText!!
      else -> "Canvas reset. Tap to restore dashboard."
    }

  Box(modifier = Modifier.fillMaxSize()) {
    CanvasScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())

    Surface(
      onClick = { viewModel.requestCanvasRehydrate(source = "screen_tab_primary") },
      modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 12.dp, vertical = 10.dp),
      shape = RoundedCornerShape(10.dp),
      color = mobileAccent,
      border = BorderStroke(1.dp, mobileAccent.copy(alpha = 0.7f)),
      shadowElevation = 0.dp,
    ) {
      Text(
        text = "Refresh",
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold),
        color = Color(0xFF08111B),
      )
    }

    if (showRestoreCta) {
      Surface(
        onClick = {
          if (canvasRehydratePending) return@Surface
          viewModel.requestCanvasRehydrate(source = "screen_tab_cta")
        },
        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = mobileSurfaceStrong.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, mobileBorderStrong),
        shadowElevation = 10.dp,
      ) {
        Text(
          text = restoreCtaText,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          style = mobileCallout.copy(fontWeight = FontWeight.Medium),
          color = mobileText,
        )
      }
    }
  }
}

@Composable
private fun TopStatusBar(
  statusText: String,
  statusVisual: StatusVisual,
  agentName: String,
  onMenuClick: () -> Unit,
) {
  val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
  val resolvedName = agentName.trim().ifEmpty { "PropAi Sync" }

  val statusColor =
    when (statusVisual) {
      StatusVisual.Connected -> mobileSuccess
      StatusVisual.Connecting -> mobileAccent
      StatusVisual.Warning -> mobileWarning
      StatusVisual.Error -> mobileDanger
      StatusVisual.Offline -> mobileTextTertiary
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .windowInsetsPadding(safeInsets)
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = resolvedName,
        style = mobileTitle2,
        color = mobileText,
      )
      IconButton(
        onClick = onMenuClick,
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = mobileSurfaceStrong),
      ) {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = "Menu",
          tint = mobileTextSecondary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Text(
      text = statusText.trim().ifEmpty { "Claude · Offline" },
      style = mobileCaption1,
      color = statusColor,
      maxLines = 1,
    )
  }
}

@Composable
private fun MenuSheetItem(label: String, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    color = Color.Transparent,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      style = mobileCallout.copy(fontWeight = FontWeight.SemiBold),
      color = mobileText,
    )
  }
}

