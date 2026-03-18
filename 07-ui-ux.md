# 7. UI/UX Implementation

## 7.1 Main Activity with Jetpack Compose

```kotlin
// ui/MainActivity.kt
package com.personalassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalassistant.services.AssistantService
import com.personalassistant.ui.theme.PersonalAssistantTheme
import com.personalassistant.ui.components.AssistantOrb
import com.personalassistant.ui.components.CommandInput
import com.personalassistant.ui.components.ConversationList
import com.personalassistant.viewmodel.AssistantViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start assistant service
        AssistantService.start(this)
        
        // Check permissions
        checkPermissions()
        
        setContent {
            PersonalAssistantTheme {
                val viewModel: AssistantViewModel = viewModel()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AssistantScreen(viewModel)
                }
            }
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val needRequest = requiredPermissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needRequest) {
            permissionLauncher.launch(requiredPermissions)
        }
    }
}

@Composable
fun AssistantScreen(viewModel: AssistantViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            AssistantHeader(
                title = "Personal Assistant",
                subtitle = uiState.statusText,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Assistant Orb (Visual indicator)
            AssistantOrb(
                state = uiState.assistantState,
                modifier = Modifier
                    .size(200.dp)
                    .weight(1f)
            )

            // Conversation History
            ConversationList(
                conversations = uiState.conversations,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = 16.dp)
            )

            // Command Input
            CommandInput(
                onTextSubmit = { viewModel.processCommand(it) },
                onVoiceClick = { viewModel.startListening() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Status badge
        StatusBadge(
            state = uiState.assistantState,
            modifier = Modifier
                .align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun AssistantHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusBadge(
    state: com.personalassistant.model.AssistantState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = when (state) {
            is com.personalassistant.model.AssistantState.LISTENING -> MaterialTheme.colorScheme.primary
            is com.personalassistant.model.AssistantState.PROCESSING -> MaterialTheme.colorScheme.tertiary
            is com.personalassistant.model.AssistantState.SPEAKING -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = state.toString().lowercase().replace("_", " "),
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
```

## 7.2 Assistant Orb (Visual Feedback)

```kotlin
// ui/components/AssistantOrb.kt
package com.personalassistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personalassistant.model.AssistantState

@Composable
fun AssistantOrb(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val colors = when (state) {
        is AssistantState.IDLE -> listOf(
            Color(0xFF6200EE),
            Color(0xFF3700B3)
        )
        is AssistantState.LISTENING -> listOf(
            Color(0xFF03DAC6),
            Color(0xFF018786)
        )
        is AssistantState.PROCESSING -> listOf(
            Color(0xFFFFB300),
            Color(0xFFFF8F00)
        )
        is AssistantState.SPEAKING -> listOf(
            Color(0xFF03DAC6),
            Color(0xFF03DAC6)
        )
        is AssistantState.ACTIVE -> listOf(
            Color(0xFF6200EE),
            Color(0xFF3700B3)
        )
        is AssistantState.ERROR -> listOf(
            Color(0xFFB00020),
            Color(0xFF8B0000)
        )
    }

    Canvas(modifier = modifier.size(200.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (size.width / 2) * scale

        // Draw gradient circle
        drawCircle(
            brush = Brush.radialGradient(colors),
            radius = radius,
            center = center
        )

        // Draw rotating ring
        drawCircle(
            brush = Brush.sweepGradient(colors),
            radius = radius * 0.8f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
            alpha = 0.5f
        )
    }
}
```

## 7.3 Command Input Component

```kotlin
// ui/components/CommandInput.kt
package com.personalassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun CommandInput(
    onTextSubmit: (String) -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask me anything...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank()) {
                            onTextSubmit(text)
                            text = ""
                        }
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (isListening) {
                        onVoiceClick() // Stop listening
                    } else {
                        onVoiceClick() // Start listening
                    }
                    isListening = !isListening
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isListening) 
                        MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListening) 
                        MaterialTheme.colorScheme.onPrimary 
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onTextSubmit(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

## 7.4 Conversation List

```kotlin
// ui/components/ConversationList.kt
package com.personalassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalassistant.model.Conversation
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationList(
    conversations: List<Conversation>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                text = "Recent Conversations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (conversations.isEmpty()) {
                Text(
                    text = "No conversations yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(conversations.take(5)) { conversation ->
                        ConversationItem(conversation)
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(conversation: Conversation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.command,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = conversation.response,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatTime(conversation.timestamp.time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
```

## 7.5 Floating Overlay (System Alert Window)

```kotlin
// ui/overlay/OverlayService.kt
package com.personalassistant.ui.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.personalassistant.R
import com.personalassistant.services.AssistantService
import javax.inject.Inject

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
    }

    private fun createOverlay() {
        val layoutInflater = LayoutInflater.from(this)
        overlayView = layoutInflater.inflate(R.layout.overlay_layout, null)

        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
        }

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        // Setup click listeners
        overlayView.findViewById<View>(R.id.mic_button).setOnClickListener {
            AssistantService.processCommand(this, "listening")
        }

        windowManager.addView(overlayView, params)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
```

## 7.6 Overlay Layout

```xml
<!-- res/layout/overlay_layout.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center"
    android:padding="8dp"
    android:background="@drawable/overlay_background">

    <ImageButton
        android:id="@+id/mic_button"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_mic"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="Voice input"
        android:padding="8dp" />

    <TextView
        android:id="@+id/status_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Tap to speak"
        android:textColor="@android:color/white"
        android:layout_marginStart="8dp"
        android:textSize="14sp" />

</LinearLayout>
```

```xml
<!-- res/drawable/overlay_background.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#806200EE" />
    <corners android:radius="24dp" />
</shape>
```

## 7.7 Settings Screen

```kotlin
// ui/settings/SettingsScreen.kt
package com.personalassistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onNavigateToPrivacy: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateToAI: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        item {
            SettingsCategory(title = "Assistant")
        }

        item {
            SettingsItem(
                icon = Icons.Default.Psychology,
                title = "AI Provider",
                subtitle = "Choose your AI backend",
                onClick = onNavigateToAI
            )
        }

        item {
            SettingsItem(
                icon = Icons.Default.Security,
                title = "Privacy",
                subtitle = "Data & permissions",
                onClick = onNavigateToPrivacy
            )
        }

        item {
            SettingsItem(
                icon = Icons.Default.BatteryStd,
                title = "Battery",
                subtitle = "Power optimization",
                onClick = onNavigateToBattery
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SettingsCategory(title = "App")
        }

        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "Version 1.0.0",
                onClick = onNavigateToAbout
            )
        }

        item {
            SettingsItem(
                icon = Icons.Default.Help,
                title = "Help",
                subtitle = "FAQs & support",
                onClick = { /* Navigate to help */ }
            )
        }
    }
}

@Composable
private fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

## 7.8 Theme Definition

```kotlin
// ui/theme/Theme.kt
package com.personalassistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun PersonalAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

```kotlin
// ui/theme/Color.kt
package com.personalassistant.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Assistant specific colors
val AssistantListening = Color(0xFF03DAC6)
val AssistantProcessing = Color(0xFFFFB300)
val AssistantSpeaking = Color(0xFF03DAC6)
val AssistantError = Color(0xFFB00020)
```

## 7.9 Navigation Setup

```kotlin
// ui/navigation/NavGraph.kt
package com.personalassistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.personalassistant.ui.settings.SettingsScreen
import com.personalassistant.ui.settings.PrivacySettingsScreen
import com.personalassistant.ui.settings.BatterySettingsScreen
import com.personalassistant.ui.AssistantScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Privacy : Screen("privacy")
    object Battery : Screen("battery")
    object AI : Screen("ai")
    object About : Screen("about")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            AssistantScreen()
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                onNavigateToBattery = { navController.navigate(Screen.Battery.route) },
                onNavigateToAI = { navController.navigate(Screen.AI.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
        
        composable(Screen.Privacy.route) {
            PrivacySettingsScreen()
        }
        
        composable(Screen.Battery.route) {
            BatterySettingsScreen()
        }
        
        composable(Screen.AI.route) {
            AIProviderSettingsScreen()
        }
        
        composable(Screen.About.route) {
            AboutScreen()
        }
    }
}
```

---

**Next**: [Testing & Deployment](./08-testing-deployment.md)
