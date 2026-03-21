package ai.androidassistant.app.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ai.androidassistant.app.R

internal val mobileBackgroundGradient =
  Brush.verticalGradient(
    listOf(
      Color(0xFF0B0C0E),
      Color(0xFF0B0C0E),
    ),
  )

internal val mobileSurface = Color(0xFF111316)
internal val mobileSurfaceStrong = Color(0xFF151821)
internal val mobileBorder = Color(0xFF1E2330)
internal val mobileBorderStrong = Color(0xFF2A3245)
internal val mobileText = Color(0xFFF9FAFC)
internal val mobileTextSecondary = Color(0xFFCED4E1)
internal val mobileTextTertiary = Color(0xFF8E97A9)
internal val mobileAccent = Color(0xFF5BD8FF)
internal val mobileAccentSoft = Color(0xFF162531)
internal val mobileSuccess = Color(0xFF5FD6A4)
internal val mobileSuccessSoft = Color(0xFF153825)
internal val mobileWarning = Color(0xFFF2C36B)
internal val mobileWarningSoft = Color(0xFF3A2A10)
internal val mobileDanger = Color(0xFFFF7B8A)
internal val mobileDangerSoft = Color(0xFF3B161B)
internal val mobileCodeBg = Color(0xFF0E1118)
internal val mobileCodeText = Color(0xFFE8EDF7)

internal val mobileFontFamily =
  FontFamily(
    Font(resId = R.font.manrope_400_regular, weight = FontWeight.Normal),
    Font(resId = R.font.manrope_500_medium, weight = FontWeight.Medium),
    Font(resId = R.font.manrope_600_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.manrope_700_bold, weight = FontWeight.Bold),
  )

internal val mobileTitle1 =
  TextStyle(
    fontFamily = mobileFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.5).sp,
  )

internal val mobileTitle2 =
  TextStyle(
    fontFamily = mobileFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.3).sp,
  )

internal val mobileHeadline =
  TextStyle(
    fontFamily = mobileFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.1).sp,
  )

internal val mobileBody =
  TextStyle(
    fontFamily = mobileFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.sp,
  )

internal val mobileCallout =
  TextStyle(
    fontFamily = mobileFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 22.sp,
  )

internal val mobileCaption1 =
  TextStyle(
    fontFamily = mobileFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.2.sp,
  )

internal val mobileCaption2 =
  TextStyle(
    fontFamily = mobileFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.4.sp,
  )

