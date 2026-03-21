package ai.androidassistant.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val propAiSyncColorScheme =
  darkColorScheme(
    primary = mobileAccent,
    onPrimary = Color(0xFF060C12),
    primaryContainer = mobileAccentSoft,
    onPrimaryContainer = mobileText,
    secondary = Color(0xFF8AD0FF),
    onSecondary = Color(0xFF061018),
    background = Color(0xFF0B0C0E),
    onBackground = mobileText,
    surface = mobileSurface,
    onSurface = mobileText,
    surfaceVariant = mobileSurfaceStrong,
    onSurfaceVariant = mobileTextSecondary,
    outline = mobileBorderStrong,
    error = mobileDanger,
    onError = Color(0xFF19070A),
  )

private val propAiSyncTypography =
  Typography(
    displayLarge = mobileTitle1,
    displayMedium = mobileTitle1,
    displaySmall = mobileTitle2,
    headlineLarge = mobileTitle2,
    headlineMedium = mobileTitle2,
    headlineSmall = mobileHeadline,
    titleLarge = mobileHeadline,
    titleMedium = mobileHeadline,
    titleSmall = mobileCallout,
    bodyLarge = mobileBody,
    bodyMedium = mobileBody,
    bodySmall = mobileCallout,
    labelLarge = mobileCallout,
    labelMedium = mobileCaption1,
    labelSmall = mobileCaption2,
  )

@Composable
fun PropAiSyncTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = propAiSyncColorScheme,
    typography = propAiSyncTypography,
    content = content,
  )
}

@Composable
fun overlayContainerColor(): Color = mobileSurfaceStrong.copy(alpha = 0.96f)

@Composable
fun overlayIconColor(): Color = mobileTextSecondary

