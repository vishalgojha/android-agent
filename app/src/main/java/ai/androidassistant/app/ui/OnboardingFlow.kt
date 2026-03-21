package ai.androidassistant.app.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.androidassistant.app.LocationMode
import ai.androidassistant.app.CloudProvider
import ai.androidassistant.app.MainViewModel
import ai.androidassistant.app.R
import ai.androidassistant.app.automation.AutomationAccessibilityService
import ai.androidassistant.app.gateway.GatewayEndpoint
import ai.androidassistant.app.node.DeviceNotificationListenerService
import ai.androidassistant.app.propai.PropAiLicenseStatus

private enum class OnboardingStep(val index: Int, val label: String) {
  Welcome(1, "Welcome"),
  Permissions(2, "Permissions"),
  FinalCheck(3, "Finish"),
}

private enum class GatewayInputMode {
  SetupCode,
  Manual,
}

private enum class PermissionToggle {
  Location,
  Notifications,
  Microphone,
  Camera,
  Photos,
  Contacts,
  Calendar,
  Motion,
  Sms,
}

private enum class SpecialAccessToggle {
  NotificationListener,
  Accessibility,
  Overlay,
}

private val onboardingBackgroundGradient =
  listOf(
    Color(0xFF030305),
    Color(0xFF06070A),
    Color(0xFF0A0E14),
    Color(0xFF0F151E),
  )
private val onboardingSurface = Color(0xB30C1016)
private val onboardingBorder = Color(0x332A3A52)
private val onboardingBorderStrong = Color(0x66556B8A)
private val onboardingText = Color(0xFFF4F6FA)
private val onboardingTextSecondary = Color(0xFFB9C1D3)
private val onboardingTextTertiary = Color(0xFF788297)
private val onboardingAccent = Color(0xFF7AD9FF)
private val onboardingAccentSoft = Color(0x332B5166)
private val onboardingSuccess = Color(0xFF6ED4A7)
private val onboardingWarning = Color(0xFFF3BF6A)
private val onboardingCommandBg = Color(0xE0080B12)
private val onboardingCommandBorder = Color(0xFF202838)
private val onboardingCommandAccent = Color(0xFF6ED4A7)
private val onboardingCommandText = Color(0xFFE7ECF7)

private val onboardingFontFamily =
  FontFamily(
    Font(resId = R.font.manrope_400_regular, weight = FontWeight.Normal),
    Font(resId = R.font.manrope_500_medium, weight = FontWeight.Medium),
    Font(resId = R.font.manrope_600_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.manrope_700_bold, weight = FontWeight.Bold),
  )

private val onboardingDisplayStyle =
  TextStyle(
    fontFamily = onboardingFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.8).sp,
  )

private val onboardingTitle1Style =
  TextStyle(
    fontFamily = onboardingFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.5).sp,
  )

private val onboardingHeadlineStyle =
  TextStyle(
    fontFamily = onboardingFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.1).sp,
  )

private val onboardingBodyStyle =
  TextStyle(
    fontFamily = onboardingFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 22.sp,
  )

private val onboardingCalloutStyle =
  TextStyle(
    fontFamily = onboardingFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  )

private val onboardingCaption1Style =
  TextStyle(
    fontFamily = onboardingFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp,
  )

private val onboardingCaption2Style =
  TextStyle(
    fontFamily = onboardingFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.4.sp,
  )

@Composable
fun OnboardingFlow(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val cloudProvider by viewModel.cloudProvider.collectAsState()
  val openAiApiKey by viewModel.openAiApiKey.collectAsState()
  val anthropicApiKey by viewModel.anthropicApiKey.collectAsState()
  val groqApiKey by viewModel.groqApiKey.collectAsState()
  val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
  val openAiModel by viewModel.openAiModel.collectAsState()
  val anthropicModel by viewModel.anthropicModel.collectAsState()
  val groqModel by viewModel.groqModel.collectAsState()
  val openRouterModel by viewModel.openRouterModel.collectAsState()
  val elevenLabsModel by viewModel.elevenLabsModel.collectAsState()
  val elevenLabsApiKey by viewModel.elevenLabsApiKey.collectAsState()
  val elevenLabsAgentId by viewModel.elevenLabsAgentId.collectAsState()
  val propAiControlBaseUrl by viewModel.propAiControlBaseUrl.collectAsState()
  val propAiControlEmail by viewModel.propAiControlEmail.collectAsState()
  val propAiControlUserId by viewModel.propAiControlUserId.collectAsState()
  val propAiControlTenantName by viewModel.propAiControlTenantName.collectAsState()
  val propAiControlTenantRole by viewModel.propAiControlTenantRole.collectAsState()
  val propAiLicenseBaseUrl by viewModel.propAiLicenseBaseUrl.collectAsState()
  val propAiActivationKey by viewModel.propAiActivationKey.collectAsState()
  val propAiActivationToken by viewModel.propAiActivationToken.collectAsState()
  val propAiLicenseStatus by viewModel.propAiLicenseStatus.collectAsState()
  val propAiControlBusy by viewModel.propAiControlBusy.collectAsState()
  val propAiControlError by viewModel.propAiControlError.collectAsState()
  val propAiLicenseBusy by viewModel.propAiLicenseBusy.collectAsState()
  val propAiLicenseError by viewModel.propAiLicenseError.collectAsState()

  var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }

  val lifecycleOwner = LocalLifecycleOwner.current

  val smsAvailable =
    remember(context) {
      context.packageManager?.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) == true
    }
  val motionAvailable =
    remember(context) {
      hasMotionCapabilities(context)
    }
  val motionPermissionRequired = true
  val notificationsPermissionRequired = Build.VERSION.SDK_INT >= 33
  val photosPermission =
    if (Build.VERSION.SDK_INT >= 33) {
      Manifest.permission.READ_MEDIA_IMAGES
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }

  var enableLocation by rememberSaveable { mutableStateOf(false) }
  var enableNotifications by
    rememberSaveable {
      mutableStateOf(
        !notificationsPermissionRequired ||
          isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS),
      )
    }
  var enableNotificationListener by
    rememberSaveable {
      mutableStateOf(isNotificationListenerEnabled(context))
    }
  var enableAccessibility by
    rememberSaveable {
      mutableStateOf(isAccessibilityServiceEnabled(context))
    }
  var enableOverlay by
    rememberSaveable {
      mutableStateOf(hasOverlayPermission(context))
    }
  var enableMicrophone by rememberSaveable { mutableStateOf(false) }
  var enableCamera by rememberSaveable { mutableStateOf(false) }
  var enablePhotos by rememberSaveable { mutableStateOf(false) }
  var enableContacts by rememberSaveable { mutableStateOf(false) }
  var enableCalendar by rememberSaveable { mutableStateOf(false) }
  var enableMotion by
    rememberSaveable {
      mutableStateOf(
        motionAvailable &&
          (!motionPermissionRequired || isPermissionGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)),
      )
    }
  var enableSms by
    rememberSaveable {
      mutableStateOf(smsAvailable && isPermissionGranted(context, Manifest.permission.SEND_SMS))
    }

  var pendingPermissionToggle by remember { mutableStateOf<PermissionToggle?>(null) }
  var pendingSpecialAccessToggle by remember { mutableStateOf<SpecialAccessToggle?>(null) }

  fun setPermissionToggleEnabled(toggle: PermissionToggle, enabled: Boolean) {
    when (toggle) {
      PermissionToggle.Location -> enableLocation = enabled
      PermissionToggle.Notifications -> enableNotifications = enabled
      PermissionToggle.Microphone -> enableMicrophone = enabled
      PermissionToggle.Camera -> enableCamera = enabled
      PermissionToggle.Photos -> enablePhotos = enabled
      PermissionToggle.Contacts -> enableContacts = enabled
      PermissionToggle.Calendar -> enableCalendar = enabled
      PermissionToggle.Motion -> enableMotion = enabled && motionAvailable
      PermissionToggle.Sms -> enableSms = enabled && smsAvailable
    }
  }

  fun isPermissionToggleGranted(toggle: PermissionToggle): Boolean =
    when (toggle) {
      PermissionToggle.Location ->
        isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
          isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
      PermissionToggle.Notifications ->
        !notificationsPermissionRequired ||
          isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
      PermissionToggle.Microphone -> isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)
      PermissionToggle.Camera -> isPermissionGranted(context, Manifest.permission.CAMERA)
      PermissionToggle.Photos -> isPermissionGranted(context, photosPermission)
      PermissionToggle.Contacts ->
        isPermissionGranted(context, Manifest.permission.READ_CONTACTS) &&
          isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)
      PermissionToggle.Calendar ->
        isPermissionGranted(context, Manifest.permission.READ_CALENDAR) &&
          isPermissionGranted(context, Manifest.permission.WRITE_CALENDAR)
      PermissionToggle.Motion ->
        !motionAvailable ||
          !motionPermissionRequired ||
          isPermissionGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)
      PermissionToggle.Sms ->
        !smsAvailable || isPermissionGranted(context, Manifest.permission.SEND_SMS)
    }

  fun setSpecialAccessToggleEnabled(toggle: SpecialAccessToggle, enabled: Boolean) {
    when (toggle) {
      SpecialAccessToggle.NotificationListener -> enableNotificationListener = enabled
      SpecialAccessToggle.Accessibility -> enableAccessibility = enabled
      SpecialAccessToggle.Overlay -> enableOverlay = enabled
    }
  }

  val enabledPermissionSummary =
    remember(
      enableLocation,
      enableNotifications,
      enableNotificationListener,
      enableAccessibility,
      enableOverlay,
      enableMicrophone,
      enableCamera,
      enablePhotos,
      enableContacts,
      enableCalendar,
      enableMotion,
      enableSms,
      smsAvailable,
      motionAvailable,
    ) {
      val enabled = mutableListOf<String>()
      if (enableLocation) enabled += "Location"
      if (enableNotifications) enabled += "Notifications"
      if (enableNotificationListener) enabled += "Notification listener"
      if (enableAccessibility) enabled += "Accessibility service"
      if (enableOverlay) enabled += "Display over other apps"
      if (enableMicrophone) enabled += "Microphone"
      if (enableCamera) enabled += "Camera"
      if (enablePhotos) enabled += "Photos"
      if (enableContacts) enabled += "Contacts"
      if (enableCalendar) enabled += "Calendar"
      if (enableMotion && motionAvailable) enabled += "Motion"
      if (smsAvailable && enableSms) enabled += "SMS"
      if (enabled.isEmpty()) "None selected" else enabled.joinToString(", ")
    }

  val proceedFromPermissions: () -> Unit = proceed@{
    var openedSpecialSetup = false
    if (enableNotificationListener && !isNotificationListenerEnabled(context)) {
      openNotificationListenerSettings(context)
      openedSpecialSetup = true
    }
    if (enableAccessibility && !isAccessibilityServiceEnabled(context)) {
      openAccessibilitySettings(context)
      openedSpecialSetup = true
    }
    if (enableOverlay && !hasOverlayPermission(context)) {
      openOverlaySettings(context)
      openedSpecialSetup = true
    }
    if (openedSpecialSetup) {
      return@proceed
    }
    step = OnboardingStep.FinalCheck
  }

  val togglePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      val pendingToggle = pendingPermissionToggle ?: return@rememberLauncherForActivityResult
      setPermissionToggleEnabled(pendingToggle, isPermissionToggleGranted(pendingToggle))
      pendingPermissionToggle = null
    }

  val requestPermissionToggle: (PermissionToggle, Boolean, List<String>) -> Unit =
    request@{ toggle, enabled, permissions ->
      if (!enabled) {
        setPermissionToggleEnabled(toggle, false)
        return@request
      }
      if (isPermissionToggleGranted(toggle)) {
        setPermissionToggleEnabled(toggle, true)
        return@request
      }
      val missing = permissions.distinct().filterNot { isPermissionGranted(context, it) }
      if (missing.isEmpty()) {
        setPermissionToggleEnabled(toggle, isPermissionToggleGranted(toggle))
        return@request
      }
      pendingPermissionToggle = toggle
      togglePermissionLauncher.launch(missing.toTypedArray())
    }

  val requestSpecialAccessToggle: (SpecialAccessToggle, Boolean) -> Unit =
    request@{ toggle, enabled ->
      if (!enabled) {
        setSpecialAccessToggleEnabled(toggle, false)
        pendingSpecialAccessToggle = null
        return@request
      }
      val grantedNow =
        when (toggle) {
          SpecialAccessToggle.NotificationListener -> isNotificationListenerEnabled(context)
          SpecialAccessToggle.Accessibility -> isAccessibilityServiceEnabled(context)
          SpecialAccessToggle.Overlay -> hasOverlayPermission(context)
        }
      if (grantedNow) {
        setSpecialAccessToggleEnabled(toggle, true)
        pendingSpecialAccessToggle = null
        return@request
      }
      pendingSpecialAccessToggle = toggle
      when (toggle) {
        SpecialAccessToggle.NotificationListener -> openNotificationListenerSettings(context)
        SpecialAccessToggle.Accessibility -> openAccessibilitySettings(context)
        SpecialAccessToggle.Overlay -> openOverlaySettings(context)
      }
    }

  DisposableEffect(lifecycleOwner, context, pendingSpecialAccessToggle) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event != Lifecycle.Event.ON_RESUME) {
          return@LifecycleEventObserver
        }
        when (pendingSpecialAccessToggle) {
          SpecialAccessToggle.NotificationListener -> {
            setSpecialAccessToggleEnabled(
              SpecialAccessToggle.NotificationListener,
              isNotificationListenerEnabled(context),
            )
            pendingSpecialAccessToggle = null
          }
          SpecialAccessToggle.Accessibility -> {
            setSpecialAccessToggleEnabled(
              SpecialAccessToggle.Accessibility,
              isAccessibilityServiceEnabled(context),
            )
            pendingSpecialAccessToggle = null
          }
          SpecialAccessToggle.Overlay -> {
            setSpecialAccessToggleEnabled(
              SpecialAccessToggle.Overlay,
              hasOverlayPermission(context),
            )
            pendingSpecialAccessToggle = null
          }
          null -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(onboardingBackgroundGradient)),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .imePadding()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Column(
          modifier = Modifier.padding(top = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "FIRST RUN",
            style = onboardingCaption1Style.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
            color = onboardingAccent,
          )
          Text(
            "PropAi Sync\nMobile Setup",
            style = onboardingDisplayStyle.copy(lineHeight = 38.sp),
            color = onboardingText,
          )
          Text(
            "Step ${step.index} of ${OnboardingStep.entries.size}",
            style = onboardingCaption1Style,
            color = onboardingAccent,
          )
        }
        StepRailWrap(current = step)

        when (step) {
          OnboardingStep.Welcome -> WelcomeStep()
          OnboardingStep.Permissions ->
            PermissionsStep(
              enableLocation = enableLocation,
              enableNotifications = enableNotifications,
              enableNotificationListener = enableNotificationListener,
              enableAccessibility = enableAccessibility,
              enableOverlay = enableOverlay,
              enableMicrophone = enableMicrophone,
              enableCamera = enableCamera,
              enablePhotos = enablePhotos,
              enableContacts = enableContacts,
              enableCalendar = enableCalendar,
              enableMotion = enableMotion,
              motionAvailable = motionAvailable,
              motionPermissionRequired = motionPermissionRequired,
              enableSms = enableSms,
              smsAvailable = smsAvailable,
              context = context,
              onLocationChange = { checked ->
                requestPermissionToggle(
                  PermissionToggle.Location,
                  checked,
                  listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  ),
                )
              },
              onNotificationsChange = { checked ->
                if (!notificationsPermissionRequired) {
                  setPermissionToggleEnabled(PermissionToggle.Notifications, checked)
                } else {
                  requestPermissionToggle(
                    PermissionToggle.Notifications,
                    checked,
                    listOf(Manifest.permission.POST_NOTIFICATIONS),
                  )
                }
              },
              onNotificationListenerChange = { checked ->
                requestSpecialAccessToggle(SpecialAccessToggle.NotificationListener, checked)
              },
              onAccessibilityChange = { checked ->
                requestSpecialAccessToggle(SpecialAccessToggle.Accessibility, checked)
              },
              onOverlayChange = { checked ->
                requestSpecialAccessToggle(SpecialAccessToggle.Overlay, checked)
              },
              onMicrophoneChange = { checked ->
                requestPermissionToggle(
                  PermissionToggle.Microphone,
                  checked,
                  listOf(Manifest.permission.RECORD_AUDIO),
                )
              },
              onCameraChange = { checked ->
                requestPermissionToggle(
                  PermissionToggle.Camera,
                  checked,
                  listOf(Manifest.permission.CAMERA),
                )
              },
              onPhotosChange = { checked ->
                requestPermissionToggle(
                  PermissionToggle.Photos,
                  checked,
                  listOf(photosPermission),
                )
              },
              onContactsChange = { checked ->
                requestPermissionToggle(
                  PermissionToggle.Contacts,
                  checked,
                  listOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                  ),
                )
              },
              onCalendarChange = { checked ->
                requestPermissionToggle(
                  PermissionToggle.Calendar,
                  checked,
                  listOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR,
                  ),
                )
              },
              onMotionChange = { checked ->
                if (!motionAvailable) {
                  setPermissionToggleEnabled(PermissionToggle.Motion, false)
                } else if (!motionPermissionRequired) {
                  setPermissionToggleEnabled(PermissionToggle.Motion, checked)
                } else {
                  requestPermissionToggle(
                    PermissionToggle.Motion,
                    checked,
                    listOf(Manifest.permission.ACTIVITY_RECOGNITION),
                  )
                }
              },
              onSmsChange = { checked ->
                if (!smsAvailable) {
                  setPermissionToggleEnabled(PermissionToggle.Sms, false)
                } else {
                  requestPermissionToggle(
                    PermissionToggle.Sms,
                    checked,
                    listOf(Manifest.permission.SEND_SMS),
                  )
                }
              },
            )
          OnboardingStep.FinalCheck ->
            FinalStep(
              enabledPermissions = enabledPermissionSummary,
              cloudProvider = cloudProvider,
              openAiApiKey = openAiApiKey,
              anthropicApiKey = anthropicApiKey,
              groqApiKey = groqApiKey,
              openRouterApiKey = openRouterApiKey,
              openAiModel = openAiModel,
              anthropicModel = anthropicModel,
              groqModel = groqModel,
              openRouterModel = openRouterModel,
              elevenLabsModel = elevenLabsModel,
              elevenLabsApiKey = elevenLabsApiKey,
              elevenLabsAgentId = elevenLabsAgentId,
              propAiControlBaseUrl = propAiControlBaseUrl,
              propAiControlEmail = propAiControlEmail,
              propAiControlUserId = propAiControlUserId,
              propAiControlTenantName = propAiControlTenantName,
              propAiControlTenantRole = propAiControlTenantRole,
              propAiControlBusy = propAiControlBusy,
              propAiControlError = propAiControlError,
              propAiLicenseBaseUrl = propAiLicenseBaseUrl,
              propAiActivationKey = propAiActivationKey,
              propAiActivationToken = propAiActivationToken,
              propAiLicenseStatus = propAiLicenseStatus,
              propAiLicenseBusy = propAiLicenseBusy,
              propAiLicenseError = propAiLicenseError,
              onProviderChange = viewModel::setCloudProvider,
              onOpenAiApiKeyChange = viewModel::setOpenAiApiKey,
              onAnthropicApiKeyChange = viewModel::setAnthropicApiKey,
              onGroqApiKeyChange = viewModel::setGroqApiKey,
              onOpenRouterApiKeyChange = viewModel::setOpenRouterApiKey,
              onElevenLabsApiKeyChange = viewModel::setElevenLabsApiKey,
              onOpenAiModelChange = viewModel::setOpenAiModel,
              onAnthropicModelChange = viewModel::setAnthropicModel,
              onGroqModelChange = viewModel::setGroqModel,
              onOpenRouterModelChange = viewModel::setOpenRouterModel,
              onElevenLabsModelChange = viewModel::setElevenLabsModel,
              onElevenLabsAgentIdChange = viewModel::setElevenLabsAgentId,
              onPropAiControlBaseUrlChange = viewModel::setPropAiControlBaseUrl,
              onPropAiLicenseBaseUrlChange = viewModel::setPropAiLicenseBaseUrl,
              onPropAiActivationKeyChange = viewModel::setPropAiActivationKey,
              onPropAiLogin = viewModel::loginPropAi,
              onPropAiRegister = viewModel::registerPropAi,
              onPropAiRefreshProfile = viewModel::refreshPropAiProfile,
              onPropAiLogout = viewModel::logoutPropAi,
              onPropAiActivateLicense = viewModel::activatePropAiLicense,
              onPropAiRefreshLicense = viewModel::refreshPropAiLicense,
              onPropAiClearLicense = viewModel::clearPropAiLicense,
            )
        }
      }

      Spacer(Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val backEnabled = step != OnboardingStep.Welcome
        Surface(
          modifier = Modifier.size(52.dp),
          shape = RoundedCornerShape(14.dp),
          color = onboardingSurface,
          border = androidx.compose.foundation.BorderStroke(1.dp, if (backEnabled) onboardingBorderStrong else onboardingBorder),
        ) {
          IconButton(
            onClick = {
              step =
                when (step) {
                  OnboardingStep.Welcome -> OnboardingStep.Welcome
                  OnboardingStep.Permissions -> OnboardingStep.Welcome
                  OnboardingStep.FinalCheck -> OnboardingStep.Permissions
                }
            },
            enabled = backEnabled,
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = if (backEnabled) onboardingTextSecondary else onboardingTextTertiary,
            )
          }
        }

        when (step) {
          OnboardingStep.Welcome -> {
            Button(
              onClick = { step = OnboardingStep.Permissions },
              modifier = Modifier.weight(1f).height(52.dp),
              shape = RoundedCornerShape(14.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = onboardingAccent,
                  contentColor = Color.White,
                  disabledContainerColor = onboardingAccent.copy(alpha = 0.45f),
                  disabledContentColor = Color.White,
                ),
            ) {
              Text("Next", style = onboardingHeadlineStyle.copy(fontWeight = FontWeight.Bold))
            }
          }
          OnboardingStep.Permissions -> {
            Button(
              onClick = {
                viewModel.setCameraEnabled(enableCamera)
                viewModel.setLocationMode(if (enableLocation) LocationMode.WhileUsing else LocationMode.Off)
                proceedFromPermissions()
              },
              modifier = Modifier.weight(1f).height(52.dp),
              shape = RoundedCornerShape(14.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = onboardingAccent,
                  contentColor = Color.White,
                  disabledContainerColor = onboardingAccent.copy(alpha = 0.45f),
                  disabledContentColor = Color.White,
                ),
            ) {
              Text("Next", style = onboardingHeadlineStyle.copy(fontWeight = FontWeight.Bold))
            }
          }
          OnboardingStep.FinalCheck -> {
            Button(
              onClick = { viewModel.setOnboardingCompleted(true) },
              modifier = Modifier.weight(1f).height(52.dp),
              shape = RoundedCornerShape(14.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = onboardingAccent,
                  contentColor = Color.White,
                  disabledContainerColor = onboardingAccent.copy(alpha = 0.45f),
                  disabledContentColor = Color.White,
                ),
            ) {
              Text("Finish", style = onboardingHeadlineStyle.copy(fontWeight = FontWeight.Bold))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StepRailWrap(current: OnboardingStep) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    HorizontalDivider(color = onboardingBorder)
    StepRail(current = current)
    HorizontalDivider(color = onboardingBorder)
  }
}

@Composable
private fun StepRail(current: OnboardingStep) {
  val steps = OnboardingStep.entries
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    steps.forEach { step ->
      val complete = step.index < current.index
      val active = step.index == current.index
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(5.dp)
              .background(
                color =
                  when {
                    complete -> onboardingSuccess
                    active -> onboardingAccent
                    else -> onboardingBorder
                  },
                shape = RoundedCornerShape(999.dp),
              ),
        )
        Text(
          text = step.label,
          style = onboardingCaption2Style.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold),
          color = if (active) onboardingAccent else onboardingTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun WelcomeStep() {
  StepShell(title = "What You Get") {
  Bullet("Everything runs from this device with cloud inference via your selected provider.")
    Bullet("Pair with PropAi Sync via QR to enable WhatsApp automation.")
    Bullet("Enable only the permissions and capabilities you want.")
    Bullet("Finish setup, then start chatting.")
  }
}

@Composable
private fun GatewayStep(
  inputMode: GatewayInputMode,
  advancedOpen: Boolean,
  setupCode: String,
  gateways: List<GatewayEndpoint>,
  discoveryStatusText: String,
  discoveryEnabled: Boolean,
  manualHost: String,
  manualPort: String,
  manualTls: Boolean,
  gatewayToken: String,
  gatewayPassword: String,
  gatewayError: String?,
  onScanQrClick: () -> Unit,
  onAdvancedOpenChange: (Boolean) -> Unit,
  onInputModeChange: (GatewayInputMode) -> Unit,
  onSetupCodeChange: (String) -> Unit,
  onManualHostChange: (String) -> Unit,
  onManualPortChange: (String) -> Unit,
  onManualTlsChange: (Boolean) -> Unit,
  onDiscoveredGatewaySelect: (GatewayEndpoint) -> Unit,
  onTokenChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
) {
  val resolvedEndpoint = remember(setupCode) { decodeGatewaySetupCode(setupCode)?.url?.let { parseGatewayEndpoint(it)?.displayUrl } }
  val manualResolvedEndpoint = remember(manualHost, manualPort, manualTls) { composeGatewayManualUrl(manualHost, manualPort, manualTls)?.let { parseGatewayEndpoint(it)?.displayUrl } }

  StepShell(title = "Gateway Connection") {
    GuideBlock(title = "Scan onboarding QR") {
      Text("Run these on the gateway host:", style = onboardingCalloutStyle, color = onboardingTextSecondary)
      CommandBlock("propai-sync qr")
      Text(
        "If propai-sync qr says the gateway is only bound to loopback, rerun it with --public-url or expose the gateway on LAN/Tailscale first.",
        style = onboardingCalloutStyle,
        color = onboardingTextSecondary,
      )
      Text("Then scan with this device.", style = onboardingCalloutStyle, color = onboardingTextSecondary)
    }
    Button(
      onClick = onScanQrClick,
      modifier = Modifier.fillMaxWidth().height(48.dp),
      shape = RoundedCornerShape(12.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = onboardingAccent,
          contentColor = Color.White,
        ),
    ) {
      Text("Scan QR code", style = onboardingHeadlineStyle.copy(fontWeight = FontWeight.Bold))
    }
    if (!resolvedEndpoint.isNullOrBlank()) {
      Text("QR captured. Review endpoint below.", style = onboardingCalloutStyle, color = onboardingSuccess)
      ResolvedEndpoint(endpoint = resolvedEndpoint)
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      color = onboardingSurface,
      border = androidx.compose.foundation.BorderStroke(1.dp, onboardingBorderStrong),
      onClick = { onAdvancedOpenChange(!advancedOpen) },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("Advanced setup", style = onboardingHeadlineStyle, color = onboardingText)
          Text("Paste setup code or enter host/port manually.", style = onboardingCaption1Style, color = onboardingTextSecondary)
        }
        Icon(
          imageVector = if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (advancedOpen) "Collapse advanced setup" else "Expand advanced setup",
          tint = onboardingTextSecondary,
        )
      }
    }

    AnimatedVisibility(visible = advancedOpen) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GuideBlock(title = "Manual setup commands") {
          Text("Run these on the gateway host:", style = onboardingCalloutStyle, color = onboardingTextSecondary)
          CommandBlock("propai-sync qr --setup-code-only")
          CommandBlock("propai-sync qr --json")
          CommandBlock("propai-sync qr --public-url wss://gateway.example.com")
          Text(
            "`--json` prints `setupCode` and `gatewayUrl`. Use `--public-url` when the gateway stays loopback-only on the host.",
            style = onboardingCalloutStyle,
            color = onboardingTextSecondary,
          )
          DiscoveryHintBlock(
            gateways = gateways,
            discoveryStatusText = discoveryStatusText,
            discoveryEnabled = discoveryEnabled,
            onDiscoveredGatewaySelect = onDiscoveredGatewaySelect,
          )
        }
        GatewayModeToggle(inputMode = inputMode, onInputModeChange = onInputModeChange)

        if (inputMode == GatewayInputMode.SetupCode) {
          Text("SETUP CODE", style = onboardingCaption1Style.copy(letterSpacing = 0.9.sp), color = onboardingTextSecondary)
          OutlinedTextField(
            value = setupCode,
            onValueChange = onSetupCodeChange,
            placeholder = { Text("Paste code from `propai-sync qr --setup-code-only`", color = onboardingTextTertiary, style = onboardingBodyStyle) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            textStyle = onboardingBodyStyle.copy(fontFamily = FontFamily.Monospace, color = onboardingText),
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = onboardingSurface,
                unfocusedContainerColor = onboardingSurface,
                focusedBorderColor = onboardingAccent,
                unfocusedBorderColor = onboardingBorder,
                focusedTextColor = onboardingText,
                unfocusedTextColor = onboardingText,
                cursorColor = onboardingAccent,
              ),
          )
          if (!resolvedEndpoint.isNullOrBlank()) {
            ResolvedEndpoint(endpoint = resolvedEndpoint)
          }
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickFillChip(label = "Android Emulator", onClick = {
              onManualHostChange("10.0.2.2")
              onManualPortChange("18789")
              onManualTlsChange(false)
            })
            QuickFillChip(label = "Localhost", onClick = {
              onManualHostChange("127.0.0.1")
              onManualPortChange("18789")
              onManualTlsChange(false)
            })
          }

          Text("HOST", style = onboardingCaption1Style.copy(letterSpacing = 0.9.sp), color = onboardingTextSecondary)
          OutlinedTextField(
            value = manualHost,
            onValueChange = onManualHostChange,
            placeholder = { Text("10.0.2.2", color = onboardingTextTertiary, style = onboardingBodyStyle) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = onboardingBodyStyle.copy(color = onboardingText),
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = onboardingSurface,
                unfocusedContainerColor = onboardingSurface,
                focusedBorderColor = onboardingAccent,
                unfocusedBorderColor = onboardingBorder,
                focusedTextColor = onboardingText,
                unfocusedTextColor = onboardingText,
                cursorColor = onboardingAccent,
              ),
          )

          Text("PORT", style = onboardingCaption1Style.copy(letterSpacing = 0.9.sp), color = onboardingTextSecondary)
          OutlinedTextField(
            value = manualPort,
            onValueChange = onManualPortChange,
            placeholder = { Text("18789", color = onboardingTextTertiary, style = onboardingBodyStyle) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = onboardingBodyStyle.copy(fontFamily = FontFamily.Monospace, color = onboardingText),
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = onboardingSurface,
                unfocusedContainerColor = onboardingSurface,
                focusedBorderColor = onboardingAccent,
                unfocusedBorderColor = onboardingBorder,
                focusedTextColor = onboardingText,
                unfocusedTextColor = onboardingText,
                cursorColor = onboardingAccent,
              ),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("Use TLS", style = onboardingHeadlineStyle, color = onboardingText)
              Text("Switch to secure websocket (`wss`).", style = onboardingCalloutStyle.copy(lineHeight = 18.sp), color = onboardingTextSecondary)
            }
            Switch(
              checked = manualTls,
              onCheckedChange = onManualTlsChange,
              colors =
                SwitchDefaults.colors(
                  checkedTrackColor = onboardingAccent,
                  uncheckedTrackColor = onboardingBorderStrong,
                  checkedThumbColor = Color.White,
                  uncheckedThumbColor = Color.White,
                ),
            )
          }

          Text("TOKEN (OPTIONAL)", style = onboardingCaption1Style.copy(letterSpacing = 0.9.sp), color = onboardingTextSecondary)
          OutlinedTextField(
            value = gatewayToken,
            onValueChange = onTokenChange,
            placeholder = { Text("token", color = onboardingTextTertiary, style = onboardingBodyStyle) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            textStyle = onboardingBodyStyle.copy(color = onboardingText),
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = onboardingSurface,
                unfocusedContainerColor = onboardingSurface,
                focusedBorderColor = onboardingAccent,
                unfocusedBorderColor = onboardingBorder,
                focusedTextColor = onboardingText,
                unfocusedTextColor = onboardingText,
                cursorColor = onboardingAccent,
              ),
          )

          Text("PASSWORD (OPTIONAL)", style = onboardingCaption1Style.copy(letterSpacing = 0.9.sp), color = onboardingTextSecondary)
          OutlinedTextField(
            value = gatewayPassword,
            onValueChange = onPasswordChange,
            placeholder = { Text("password", color = onboardingTextTertiary, style = onboardingBodyStyle) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            textStyle = onboardingBodyStyle.copy(color = onboardingText),
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedContainerColor = onboardingSurface,
                unfocusedContainerColor = onboardingSurface,
                focusedBorderColor = onboardingAccent,
                unfocusedBorderColor = onboardingBorder,
                focusedTextColor = onboardingText,
                unfocusedTextColor = onboardingText,
                cursorColor = onboardingAccent,
              ),
          )

          if (!manualResolvedEndpoint.isNullOrBlank()) {
            ResolvedEndpoint(endpoint = manualResolvedEndpoint)
          }
        }
      }
    }

    if (!gatewayError.isNullOrBlank()) {
      Text(gatewayError, color = onboardingWarning, style = onboardingCaption1Style)
    }
  }
}

@Composable
private fun DiscoveryHintBlock(
  gateways: List<GatewayEndpoint>,
  discoveryStatusText: String,
  discoveryEnabled: Boolean,
  onDiscoveredGatewaySelect: (GatewayEndpoint) -> Unit,
) {
  Text(
    "Android emulator uses `10.0.2.2`. Real devices should use a discovered LAN/Tailscale host or a manual host entry.",
    style = onboardingCalloutStyle,
    color = onboardingTextSecondary,
  )

  if (gateways.isEmpty()) {
    Text(
      if (discoveryEnabled) {
        discoveryStatusText.ifBlank { "Searching for gateways..." }
      } else {
        "Gateway discovery permission is off. You can still continue with setup code or manual host entry."
      },
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
    return
  }

  Text(
    "Discovered gateways",
    style = onboardingCaption1Style.copy(letterSpacing = 0.9.sp),
    color = onboardingTextSecondary,
  )
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    gateways.forEach { endpoint ->
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = onboardingSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, onboardingBorderStrong),
        onClick = { onDiscoveredGatewaySelect(endpoint) },
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(endpoint.name, style = onboardingHeadlineStyle, color = onboardingText)
          Text(endpointDisplayLabel(endpoint), style = onboardingCalloutStyle, color = onboardingTextSecondary)
        }
      }
    }
  }
}

@Composable
private fun GuideBlock(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(onboardingAccent.copy(alpha = 0.4f)))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = onboardingHeadlineStyle, color = onboardingText)
      content()
    }
  }
}

@Composable
private fun GatewayModeToggle(
  inputMode: GatewayInputMode,
  onInputModeChange: (GatewayInputMode) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    GatewayModeChip(
      label = "Setup Code",
      active = inputMode == GatewayInputMode.SetupCode,
      onClick = { onInputModeChange(GatewayInputMode.SetupCode) },
      modifier = Modifier.weight(1f),
    )
    GatewayModeChip(
      label = "Manual",
      active = inputMode == GatewayInputMode.Manual,
      onClick = { onInputModeChange(GatewayInputMode.Manual) },
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun GatewayModeChip(
  label: String,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    modifier = modifier.height(40.dp),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = if (active) onboardingAccent else onboardingSurface,
        contentColor = if (active) Color.White else onboardingText,
      ),
    border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color(0xFF184DAF) else onboardingBorderStrong),
  ) {
    Text(
      text = label,
      style = onboardingCaption1Style.copy(fontWeight = FontWeight.Bold),
    )
  }
}

@Composable
private fun QuickFillChip(
  label: String,
  onClick: () -> Unit,
) {
  TextButton(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    colors =
      ButtonDefaults.textButtonColors(
        containerColor = onboardingAccentSoft,
        contentColor = onboardingAccent,
      ),
  ) {
    Text(label, style = onboardingCaption1Style.copy(fontWeight = FontWeight.SemiBold))
  }
}

private fun endpointPreferredHost(endpoint: GatewayEndpoint): String {
  return endpoint.tailnetDns?.trim().takeIf { !it.isNullOrEmpty() }
    ?: endpoint.lanHost?.trim().takeIf { !it.isNullOrEmpty() }
    ?: endpoint.host
}

private fun endpointPreferredPort(endpoint: GatewayEndpoint): Int {
  return endpoint.gatewayPort ?: endpoint.port
}

private fun endpointDisplayLabel(endpoint: GatewayEndpoint): String {
  val scheme = if (endpoint.tlsEnabled) "https" else "http"
  return "$scheme://${endpointPreferredHost(endpoint)}:${endpointPreferredPort(endpoint)}"
}

@Composable
private fun ResolvedEndpoint(endpoint: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    HorizontalDivider(color = onboardingBorder)
    Text(
      "RESOLVED ENDPOINT",
      style = onboardingCaption2Style.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp),
      color = onboardingTextSecondary,
    )
    Text(
      endpoint,
      style = onboardingCalloutStyle.copy(fontFamily = FontFamily.Monospace),
      color = onboardingText,
    )
    HorizontalDivider(color = onboardingBorder)
  }
}

@Composable
private fun StepShell(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
    HorizontalDivider(color = onboardingBorder)
    Column(modifier = Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(title, style = onboardingTitle1Style, color = onboardingText)
      content()
    }
    HorizontalDivider(color = onboardingBorder)
  }
}

@Composable
private fun InlineDivider() {
  HorizontalDivider(color = onboardingBorder)
}

@Composable
private fun PermissionsStep(
  enableLocation: Boolean,
  enableNotifications: Boolean,
  enableNotificationListener: Boolean,
  enableAccessibility: Boolean,
  enableOverlay: Boolean,
  enableMicrophone: Boolean,
  enableCamera: Boolean,
  enablePhotos: Boolean,
  enableContacts: Boolean,
  enableCalendar: Boolean,
  enableMotion: Boolean,
  motionAvailable: Boolean,
  motionPermissionRequired: Boolean,
  enableSms: Boolean,
  smsAvailable: Boolean,
  context: Context,
  onLocationChange: (Boolean) -> Unit,
  onNotificationsChange: (Boolean) -> Unit,
  onNotificationListenerChange: (Boolean) -> Unit,
  onAccessibilityChange: (Boolean) -> Unit,
  onOverlayChange: (Boolean) -> Unit,
  onMicrophoneChange: (Boolean) -> Unit,
  onCameraChange: (Boolean) -> Unit,
  onPhotosChange: (Boolean) -> Unit,
  onContactsChange: (Boolean) -> Unit,
  onCalendarChange: (Boolean) -> Unit,
  onMotionChange: (Boolean) -> Unit,
  onSmsChange: (Boolean) -> Unit,
) {
  val locationGranted =
    isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
      isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
  val photosPermission =
    if (Build.VERSION.SDK_INT >= 33) {
      Manifest.permission.READ_MEDIA_IMAGES
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }
  val contactsGranted =
    isPermissionGranted(context, Manifest.permission.READ_CONTACTS) &&
      isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)
  val calendarGranted =
    isPermissionGranted(context, Manifest.permission.READ_CALENDAR) &&
      isPermissionGranted(context, Manifest.permission.WRITE_CALENDAR)
  val motionGranted =
    if (!motionAvailable) {
      false
    } else if (!motionPermissionRequired) {
      true
    } else {
      isPermissionGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)
    }
  val notificationListenerGranted = isNotificationListenerEnabled(context)
  val accessibilityGranted = isAccessibilityServiceEnabled(context)
  val overlayGranted = hasOverlayPermission(context)

  StepShell(title = "Permissions") {
    Text(
      "Enable only what you need now. You can change everything later in Settings.",
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
    PermissionToggleRow(
      title = "Location",
      subtitle = "location.get (while app is open)",
      checked = enableLocation,
      granted = locationGranted,
      onCheckedChange = onLocationChange,
    )
    InlineDivider()
    if (Build.VERSION.SDK_INT >= 33) {
      PermissionToggleRow(
        title = "Notifications",
        subtitle = "system.notify and foreground alerts",
        checked = enableNotifications,
        granted = isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS),
        onCheckedChange = onNotificationsChange,
      )
      InlineDivider()
    }
    PermissionToggleRow(
      title = "Notification listener",
      subtitle = "Listen to WhatsApp and other chat notifications",
      checked = enableNotificationListener,
      granted = notificationListenerGranted,
      onCheckedChange = onNotificationListenerChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Accessibility service",
      subtitle = "Screen automation and on-device actions",
      checked = enableAccessibility,
      granted = accessibilityGranted,
      onCheckedChange = onAccessibilityChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Display over other apps",
      subtitle = "Show floating assistant UI",
      checked = enableOverlay,
      granted = overlayGranted,
      onCheckedChange = onOverlayChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Microphone",
      subtitle = "Foreground Voice tab transcription",
      checked = enableMicrophone,
      granted = isPermissionGranted(context, Manifest.permission.RECORD_AUDIO),
      onCheckedChange = onMicrophoneChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Camera",
      subtitle = "camera.snap and camera.clip",
      checked = enableCamera,
      granted = isPermissionGranted(context, Manifest.permission.CAMERA),
      onCheckedChange = onCameraChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Photos",
      subtitle = "photos.latest",
      checked = enablePhotos,
      granted = isPermissionGranted(context, photosPermission),
      onCheckedChange = onPhotosChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Contacts",
      subtitle = "contacts.search and contacts.add",
      checked = enableContacts,
      granted = contactsGranted,
      onCheckedChange = onContactsChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Calendar",
      subtitle = "calendar.events and calendar.add",
      checked = enableCalendar,
      granted = calendarGranted,
      onCheckedChange = onCalendarChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Motion",
      subtitle = "motion.activity and motion.pedometer",
      checked = enableMotion,
      granted = motionGranted,
      onCheckedChange = onMotionChange,
      enabled = motionAvailable,
      statusOverride = if (!motionAvailable) "Unavailable on this device" else null,
    )
    if (smsAvailable) {
      InlineDivider()
      PermissionToggleRow(
        title = "SMS",
        subtitle = "Allow SMS sending",
        checked = enableSms,
        granted = isPermissionGranted(context, Manifest.permission.SEND_SMS),
        onCheckedChange = onSmsChange,
      )
    }
    Text("All settings can be changed later in Settings.", style = onboardingCalloutStyle, color = onboardingTextSecondary)
  }
}

@Composable
private fun PermissionToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  granted: Boolean,
  enabled: Boolean = true,
  statusOverride: String? = null,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = onboardingHeadlineStyle, color = onboardingText)
      Text(subtitle, style = onboardingCalloutStyle.copy(lineHeight = 18.sp), color = onboardingTextSecondary)
      Text(
        statusOverride ?: if (granted) "Granted" else "Not granted",
        style = onboardingCaption1Style,
        color = if (granted) onboardingSuccess else onboardingTextSecondary,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors =
        SwitchDefaults.colors(
          checkedTrackColor = onboardingAccent,
          uncheckedTrackColor = onboardingBorderStrong,
          checkedThumbColor = Color.White,
          uncheckedThumbColor = Color.White,
        ),
    )
  }
}

@Composable
private fun FinalStep(
  enabledPermissions: String,
  cloudProvider: CloudProvider,
  openAiApiKey: String,
  anthropicApiKey: String,
  groqApiKey: String,
  openRouterApiKey: String,
  openAiModel: String,
  anthropicModel: String,
  groqModel: String,
  openRouterModel: String,
  elevenLabsModel: String,
  elevenLabsApiKey: String,
  elevenLabsAgentId: String,
  propAiControlBaseUrl: String,
  propAiControlEmail: String,
  propAiControlUserId: String,
  propAiControlTenantName: String,
  propAiControlTenantRole: String,
  propAiControlBusy: Boolean,
  propAiControlError: String?,
  propAiLicenseBaseUrl: String,
  propAiActivationKey: String,
  propAiActivationToken: String,
  propAiLicenseStatus: PropAiLicenseStatus,
  propAiLicenseBusy: Boolean,
  propAiLicenseError: String?,
  onProviderChange: (CloudProvider) -> Unit,
  onOpenAiApiKeyChange: (String) -> Unit,
  onAnthropicApiKeyChange: (String) -> Unit,
  onGroqApiKeyChange: (String) -> Unit,
  onOpenRouterApiKeyChange: (String) -> Unit,
  onElevenLabsApiKeyChange: (String) -> Unit,
  onOpenAiModelChange: (String) -> Unit,
  onAnthropicModelChange: (String) -> Unit,
  onGroqModelChange: (String) -> Unit,
  onOpenRouterModelChange: (String) -> Unit,
  onElevenLabsModelChange: (String) -> Unit,
  onElevenLabsAgentIdChange: (String) -> Unit,
  onPropAiControlBaseUrlChange: (String) -> Unit,
  onPropAiLicenseBaseUrlChange: (String) -> Unit,
  onPropAiActivationKeyChange: (String) -> Unit,
  onPropAiLogin: (String, String) -> Unit,
  onPropAiRegister: (String, String, String) -> Unit,
  onPropAiRefreshProfile: () -> Unit,
  onPropAiLogout: () -> Unit,
  onPropAiActivateLicense: () -> Unit,
  onPropAiRefreshLicense: () -> Unit,
  onPropAiClearLicense: () -> Unit,
) {
  var showProviderMenu by remember { mutableStateOf(false) }
  var propAiEmailDraft by remember(propAiControlEmail) { mutableStateOf(propAiControlEmail) }
  var propAiPasswordDraft by remember { mutableStateOf("") }
  var propAiTenantDraft by remember { mutableStateOf("") }

  StepShell(title = "Cloud setup") {
    Text(
      "Choose a cloud provider for chat. You can update this later in Settings.",
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
    Box {
      Surface(
        onClick = { showProviderMenu = true },
        shape = RoundedCornerShape(14.dp),
        color = onboardingSurface,
        border = BorderStroke(1.dp, onboardingBorderStrong),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            "Provider: ${cloudProvider.label}",
            style = onboardingCaption1Style.copy(fontWeight = FontWeight.SemiBold),
            color = onboardingText,
          )
          Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = "Select provider",
            tint = onboardingTextSecondary,
          )
        }
      }
      DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }) {
        CloudProvider.entries.forEach { provider ->
          DropdownMenuItem(
            text = { Text(provider.label, style = onboardingBodyStyle, color = onboardingText) },
            onClick = {
              onProviderChange(provider)
              showProviderMenu = false
            },
          )
        }
      }
    }

    when (cloudProvider) {
      CloudProvider.OpenAI -> {
        OnboardingTextField(
          value = openAiApiKey,
          onValueChange = onOpenAiApiKeyChange,
          label = "OpenAI API key",
          isSecret = true,
        )
        OnboardingTextField(
          value = openAiModel,
          onValueChange = onOpenAiModelChange,
          label = "OpenAI model",
          placeholder = CloudProvider.OpenAI.defaultModel,
        )
      }
      CloudProvider.Anthropic -> {
        OnboardingTextField(
          value = anthropicApiKey,
          onValueChange = onAnthropicApiKeyChange,
          label = "Anthropic API key",
          isSecret = true,
        )
        OnboardingTextField(
          value = anthropicModel,
          onValueChange = onAnthropicModelChange,
          label = "Anthropic model",
          placeholder = CloudProvider.Anthropic.defaultModel,
        )
      }
      CloudProvider.Groq -> {
        OnboardingTextField(
          value = groqApiKey,
          onValueChange = onGroqApiKeyChange,
          label = "Groq API key",
          isSecret = true,
        )
        OnboardingTextField(
          value = groqModel,
          onValueChange = onGroqModelChange,
          label = "Groq model",
          placeholder = CloudProvider.Groq.defaultModel,
        )
      }
      CloudProvider.OpenRouter -> {
        OnboardingTextField(
          value = openRouterApiKey,
          onValueChange = onOpenRouterApiKeyChange,
          label = "OpenRouter API key",
          isSecret = true,
        )
        OnboardingTextField(
          value = openRouterModel,
          onValueChange = onOpenRouterModelChange,
          label = "OpenRouter model",
          placeholder = CloudProvider.OpenRouter.defaultModel,
        )
      }
      CloudProvider.ElevenLabs -> {
        OnboardingTextField(
          value = elevenLabsApiKey,
          onValueChange = onElevenLabsApiKeyChange,
          label = "ElevenLabs API key",
          isSecret = true,
        )
        OnboardingTextField(
          value = elevenLabsModel,
          onValueChange = onElevenLabsModelChange,
          label = "ElevenLabs model",
          placeholder = CloudProvider.ElevenLabs.defaultModel,
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
      "Voice (ElevenLabs)",
      style = onboardingCaption1Style.copy(fontWeight = FontWeight.SemiBold),
      color = onboardingText,
    )
    OnboardingTextField(
      value = elevenLabsApiKey,
      onValueChange = onElevenLabsApiKeyChange,
      label = "ElevenLabs API key",
      isSecret = true,
    )
    OnboardingTextField(
      value = elevenLabsAgentId,
      onValueChange = onElevenLabsAgentIdChange,
      label = "ElevenLabs Agent ID",
    )
    Text(
      "Voice tab uses ElevenLabs Conversational AI. Add a voice ID later in Settings for TTS playback.",
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )

    Spacer(modifier = Modifier.height(16.dp))
    Text(
      "PropAi Sync",
      style = onboardingCaption1Style.copy(fontWeight = FontWeight.SemiBold),
      color = onboardingText,
    )
    Text(
      "Link PropAi Sync to your workspace for tenant access and license checks.",
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
    OnboardingTextField(
      value = propAiControlBaseUrl,
      onValueChange = onPropAiControlBaseUrlChange,
      label = "Control API URL",
    )
    OnboardingTextField(
      value = propAiEmailDraft,
      onValueChange = { propAiEmailDraft = it },
      label = "Account email",
    )
    OnboardingTextField(
      value = propAiPasswordDraft,
      onValueChange = { propAiPasswordDraft = it },
      label = "Password",
      isSecret = true,
    )
    OnboardingTextField(
      value = propAiTenantDraft,
      onValueChange = { propAiTenantDraft = it },
      label = "Tenant name (for register)",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = { onPropAiLogin(propAiEmailDraft, propAiPasswordDraft) },
        enabled = !propAiControlBusy,
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = onboardingAccent, contentColor = Color.White),
      ) {
        Text("Login", style = onboardingCalloutStyle)
      }
      Button(
        onClick = { onPropAiRegister(propAiEmailDraft, propAiPasswordDraft, propAiTenantDraft) },
        enabled = !propAiControlBusy,
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = onboardingAccent, contentColor = Color.White),
      ) {
        Text("Register", style = onboardingCalloutStyle)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = onPropAiRefreshProfile,
        enabled = !propAiControlBusy && propAiControlUserId.isNotBlank(),
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = onboardingAccent, contentColor = Color.White),
      ) {
        Text("Refresh", style = onboardingCalloutStyle)
      }
      Button(
        onClick = onPropAiLogout,
        enabled = !propAiControlBusy && propAiControlUserId.isNotBlank(),
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = onboardingSurface, contentColor = onboardingText),
      ) {
        Text("Logout", style = onboardingCalloutStyle)
      }
    }
    if (!propAiControlError.isNullOrBlank()) {
      Text(propAiControlError.orEmpty(), style = onboardingCalloutStyle, color = onboardingWarning)
    }
    Text(
      if (propAiControlTenantName.isNotBlank()) {
        val role = propAiControlTenantRole.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        "Linked tenant: ${propAiControlTenantName}$role"
      } else {
        "Linked tenant: not linked"
      },
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
    OnboardingTextField(
      value = propAiLicenseBaseUrl,
      onValueChange = onPropAiLicenseBaseUrlChange,
      label = "License API URL",
    )
    OnboardingTextField(
      value = propAiActivationKey,
      onValueChange = onPropAiActivationKeyChange,
      label = "Activation key",
      isSecret = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = onPropAiActivateLicense,
        enabled = !propAiLicenseBusy,
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = onboardingAccent, contentColor = Color.White),
      ) {
        Text("Activate", style = onboardingCalloutStyle)
      }
      Button(
        onClick = onPropAiRefreshLicense,
        enabled = !propAiLicenseBusy && propAiActivationToken.isNotBlank(),
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = onboardingAccent, contentColor = Color.White),
      ) {
        Text("Refresh", style = onboardingCalloutStyle)
      }
      Button(
        onClick = onPropAiClearLicense,
        enabled = !propAiLicenseBusy,
        modifier = Modifier.weight(1f).height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = onboardingSurface, contentColor = onboardingText),
      ) {
        Text("Clear", style = onboardingCalloutStyle)
      }
    }
    val licenseLabel = if (propAiLicenseStatus.valid) "Active" else "Inactive"
    val planLabel = propAiLicenseStatus.plan ?: propAiLicenseStatus.status ?: "—"
    val expiryLabel = propAiLicenseStatus.expiresAt ?: propAiLicenseStatus.graceUntil ?: "—"
    Text(
      "License: $licenseLabel · Plan: $planLabel · Expires: $expiryLabel",
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
    if (!propAiLicenseError.isNullOrBlank()) {
      Text(propAiLicenseError.orEmpty(), style = onboardingCalloutStyle, color = onboardingWarning)
    }

    SummaryField(label = "Enabled Permissions", value = enabledPermissions)
    Text("You can change these later in Settings.", style = onboardingCalloutStyle, color = onboardingTextSecondary)
  }
}

@Composable
private fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    isSecret: Boolean = false,
  ) {
    var revealSecret by remember { mutableStateOf(false) }
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label, style = onboardingCaption1Style, color = onboardingTextSecondary) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      placeholder = {
        if (placeholder != null) {
          Text(placeholder, style = onboardingBodyStyle, color = onboardingTextTertiary)
        }
      },
      textStyle = onboardingBodyStyle.copy(color = onboardingText),
      shape = RoundedCornerShape(14.dp),
      visualTransformation =
        if (isSecret && !revealSecret) {
          PasswordVisualTransformation()
        } else {
          VisualTransformation.None
        },
      trailingIcon =
        if (isSecret) {
          {
            IconButton(onClick = { revealSecret = !revealSecret }) {
              Icon(
                imageVector = if (revealSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (revealSecret) "Hide API key" else "Show API key",
                tint = onboardingTextSecondary,
              )
            }
          }
        } else {
          null
        },
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedContainerColor = onboardingSurface,
          unfocusedContainerColor = onboardingSurface,
          focusedBorderColor = onboardingAccent,
          unfocusedBorderColor = onboardingBorder,
          focusedTextColor = onboardingText,
          unfocusedTextColor = onboardingText,
          cursorColor = onboardingAccent,
        ),
    )
  }

@Composable
private fun SummaryField(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      label,
      style = onboardingCaption2Style.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
      color = onboardingTextSecondary,
    )
    Text(value, style = onboardingHeadlineStyle, color = onboardingText)
    HorizontalDivider(color = onboardingBorder)
  }
}

@Composable
private fun CommandBlock(command: String) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(onboardingCommandBg, RoundedCornerShape(12.dp))
        .border(width = 1.dp, color = onboardingCommandBorder, shape = RoundedCornerShape(12.dp)),
  ) {
    Box(modifier = Modifier.width(3.dp).height(42.dp).background(onboardingCommandAccent))
    Text(
      command,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      style = onboardingCalloutStyle,
      fontFamily = FontFamily.Monospace,
      color = onboardingCommandText,
    )
  }
}

@Composable
private fun Bullet(text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
    Box(
      modifier =
        Modifier
          .padding(top = 7.dp)
          .size(8.dp)
          .background(onboardingAccentSoft, CircleShape),
    )
    Box(
      modifier =
        Modifier
          .padding(top = 9.dp)
          .size(4.dp)
          .background(onboardingAccent, CircleShape),
    )
    Text(text, style = onboardingBodyStyle, color = onboardingTextSecondary, modifier = Modifier.weight(1f))
  }
}

private fun isPermissionGranted(context: Context, permission: String): Boolean {
  return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
  return DeviceNotificationListenerService.isAccessEnabled(context)
}

private fun openNotificationListenerSettings(context: Context) {
  val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching {
    context.startActivity(intent)
  }.getOrElse {
    openAppSettings(context)
  }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
  val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
  val enabledServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
  val expected = ComponentName(context, AutomationAccessibilityService::class.java).flattenToString()
  return enabledServices.any { it.id == expected }
}

private fun openAccessibilitySettings(context: Context) {
  val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching {
    context.startActivity(intent)
  }.getOrElse {
    openAppSettings(context)
  }
}

private fun hasOverlayPermission(context: Context): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    Settings.canDrawOverlays(context)
  } else {
    true
  }
}

private fun openOverlaySettings(context: Context) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
  val intent =
    Intent(
      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching {
    context.startActivity(intent)
  }.getOrElse {
    openAppSettings(context)
  }
}

private fun openAppSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  context.startActivity(intent)
}

private fun hasMotionCapabilities(context: Context): Boolean {
  val sensorManager = context.getSystemService(SensorManager::class.java) ?: return false
  return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}

