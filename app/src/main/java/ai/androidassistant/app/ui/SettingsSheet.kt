package ai.androidassistant.app.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.androidassistant.app.BuildConfig
import ai.androidassistant.app.CloudProvider
import ai.androidassistant.app.LocationMode
import ai.androidassistant.app.MainViewModel
import ai.androidassistant.app.WakeWords
import ai.androidassistant.app.knownChatSourcePresets
import ai.androidassistant.app.parseListInput
import ai.androidassistant.app.automation.AutomationAccessibilityService
import ai.androidassistant.app.node.DeviceNotificationListenerService

@Composable
fun SettingsSheet(viewModel: MainViewModel) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val lifecycleOwner = LocalLifecycleOwner.current
  val instanceId by viewModel.instanceId.collectAsState()
  val displayName by viewModel.displayName.collectAsState()
  val wakeWords by viewModel.wakeWords.collectAsState()
  val statusText by viewModel.statusText.collectAsState()
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
  val cameraEnabled by viewModel.cameraEnabled.collectAsState()
  val locationMode by viewModel.locationMode.collectAsState()
  val locationPreciseEnabled by viewModel.locationPreciseEnabled.collectAsState()
  val preventSleep by viewModel.preventSleep.collectAsState()
  val canvasDebugStatusEnabled by viewModel.canvasDebugStatusEnabled.collectAsState()
  val speakerEnabled by viewModel.speakerEnabled.collectAsState()
  val elevenLabsApiKey by viewModel.elevenLabsApiKey.collectAsState()
  val elevenLabsVoiceId by viewModel.elevenLabsVoiceId.collectAsState()
  val chatListeningPackages by viewModel.chatListeningPackages.collectAsState()
  val chatListeningConversationFilters by viewModel.chatListeningConversationFilters.collectAsState()
  val currentCameraEnabled by rememberUpdatedState(cameraEnabled)

  var openAiApiKeyDraft by remember(openAiApiKey) { mutableStateOf(openAiApiKey) }
  var anthropicApiKeyDraft by remember(anthropicApiKey) { mutableStateOf(anthropicApiKey) }
  var groqApiKeyDraft by remember(groqApiKey) { mutableStateOf(groqApiKey) }
  var openRouterApiKeyDraft by remember(openRouterApiKey) { mutableStateOf(openRouterApiKey) }
  var elevenLabsApiKeyDraft by remember(elevenLabsApiKey) { mutableStateOf(elevenLabsApiKey) }
  var propAiControlEmailDraft by remember(propAiControlEmail) { mutableStateOf(propAiControlEmail) }
  var propAiControlPasswordDraft by remember { mutableStateOf("") }
  var propAiControlTenantDraft by remember { mutableStateOf("") }
  var propAiActivationKeyDraft by remember(propAiActivationKey) { mutableStateOf(propAiActivationKey) }
  var wakeWordsDraft by remember(wakeWords) { mutableStateOf(wakeWords.joinToString(", ")) }

  var openAiApiKeyVisible by remember { mutableStateOf(false) }
  var anthropicApiKeyVisible by remember { mutableStateOf(false) }
  var groqApiKeyVisible by remember { mutableStateOf(false) }
  var openRouterApiKeyVisible by remember { mutableStateOf(false) }
  var elevenLabsApiKeyVisible by remember { mutableStateOf(false) }
  var propAiActivationKeyVisible by remember { mutableStateOf(false) }
  var propAiPasswordVisible by remember { mutableStateOf(false) }

  val listState = rememberLazyListState()
  val deviceModel =
    remember {
      listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .trim()
        .ifEmpty { "Android" }
    }
  val appVersion =
    remember {
      val versionName = BuildConfig.VERSION_NAME.trim().ifEmpty { "dev" }
      if (BuildConfig.DEBUG && !versionName.contains("dev", ignoreCase = true)) {
        "$versionName-dev"
      } else {
        versionName
      }
    }
  val listItemColors =
    ListItemDefaults.colors(
      containerColor = Color.Transparent,
      headlineColor = mobileText,
      supportingColor = mobileTextSecondary,
      trailingIconColor = mobileTextSecondary,
      leadingIconColor = mobileTextSecondary,
    )
  val knownChatPackages = remember { knownChatSourcePresets.map { it.packageName }.toSet() }
  val customChatPackageText =
    remember(chatListeningPackages) {
      chatListeningPackages
        .filterNot { it in knownChatPackages }
        .joinToString(separator = "\n")
    }
  val conversationFilterText =
    remember(chatListeningConversationFilters) {
      chatListeningConversationFilters.joinToString(separator = "\n")
    }

  var showProviderMenu by remember { mutableStateOf(false) }
  val permissionRequested = remember { mutableStateMapOf<String, Boolean>() }
  fun shouldOpenSettings(permission: String): Boolean {
    val act = activity ?: return false
    val requestedBefore = permissionRequested[permission] == true
    val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(act, permission)
    return requestedBefore && !showRationale
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val cameraOk = perms[Manifest.permission.CAMERA] == true
      viewModel.setCameraEnabled(cameraOk)
      val denied =
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
          .filter { perms[it] != true }
      if (denied.isNotEmpty() && activity != null) {
        val blocked = denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  var pendingLocationRequest by remember { mutableStateOf(false) }
  var pendingPreciseToggle by remember { mutableStateOf(false) }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val fineOk = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
      val coarseOk = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      val granted = fineOk || coarseOk
      val denied =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
          .filter { perms[it] != true }
      if (denied.isNotEmpty() && activity != null) {
        val blocked = denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        if (blocked) {
          openAppSettings(context)
        }
      }

      if (pendingPreciseToggle) {
        pendingPreciseToggle = false
        viewModel.setLocationPreciseEnabled(fineOk)
        return@rememberLauncherForActivityResult
      }

      if (pendingLocationRequest) {
        pendingLocationRequest = false
        viewModel.setLocationMode(if (granted) LocationMode.WhileUsing else LocationMode.Off)
      }
    }

  var micPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      micPermissionGranted = granted
      if (!granted && activity != null) {
        val blocked =
          !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  val smsPermissionAvailable =
    remember {
      context.packageManager?.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) == true
    }
  val photosPermission =
    if (Build.VERSION.SDK_INT >= 33) {
      Manifest.permission.READ_MEDIA_IMAGES
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }
  val motionPermissionRequired = true
  val motionAvailable = remember(context) { hasMotionCapabilities(context) }

  var notificationsPermissionGranted by
    remember {
      mutableStateOf(hasNotificationsPermission(context))
    }
  val notificationsPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notificationsPermissionGranted = granted
      if (!granted && activity != null) {
        val blocked =
          !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  var notificationListenerEnabled by
    remember {
      mutableStateOf(isNotificationListenerEnabled(context))
    }
  var accessibilityEnabled by
    remember {
      mutableStateOf(isAccessibilityServiceEnabled(context))
    }
  var overlayPermissionGranted by
    remember {
      mutableStateOf(hasOverlayPermission(context))
    }

  var photosPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, photosPermission) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val photosPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      photosPermissionGranted = granted
      if (!granted && activity != null) {
        val blocked = !ActivityCompat.shouldShowRequestPermissionRationale(activity, photosPermission)
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  var contactsPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
          PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val contactsPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val readOk = perms[Manifest.permission.READ_CONTACTS] == true
      val writeOk = perms[Manifest.permission.WRITE_CONTACTS] == true
      contactsPermissionGranted = readOk && writeOk
      val denied =
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
          .filter { perms[it] != true }
      if (denied.isNotEmpty() && activity != null) {
        val blocked = denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  var calendarPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
          PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val calendarPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val readOk = perms[Manifest.permission.READ_CALENDAR] == true
      val writeOk = perms[Manifest.permission.WRITE_CALENDAR] == true
      calendarPermissionGranted = readOk && writeOk
      val denied =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
          .filter { perms[it] != true }
      if (denied.isNotEmpty() && activity != null) {
        val blocked = denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  var motionPermissionGranted by
    remember {
      mutableStateOf(
        !motionPermissionRequired ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val motionPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      motionPermissionGranted = granted
      if (!granted && activity != null) {
        val blocked =
          !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  var smsPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val smsPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      smsPermissionGranted = granted
      if (!granted && activity != null) {
        val blocked =
          !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS)
        if (blocked) {
          openAppSettings(context)
        }
      }
    }

  DisposableEffect(lifecycleOwner, context) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          val cameraPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
              PackageManager.PERMISSION_GRANTED
          if (!cameraPermissionGranted && currentCameraEnabled) {
            viewModel.setCameraEnabled(false)
          }
          micPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
              PackageManager.PERMISSION_GRANTED
          notificationsPermissionGranted = hasNotificationsPermission(context)
          notificationListenerEnabled = isNotificationListenerEnabled(context)
          accessibilityEnabled = isAccessibilityServiceEnabled(context)
          overlayPermissionGranted = hasOverlayPermission(context)
          photosPermissionGranted =
            ContextCompat.checkSelfPermission(context, photosPermission) ==
              PackageManager.PERMISSION_GRANTED
          contactsPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
              PackageManager.PERMISSION_GRANTED &&
              ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
              PackageManager.PERMISSION_GRANTED
          calendarPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
              PackageManager.PERMISSION_GRANTED &&
              ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
              PackageManager.PERMISSION_GRANTED
          motionPermissionGranted =
            !motionPermissionRequired ||
              ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
              PackageManager.PERMISSION_GRANTED
          smsPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
              PackageManager.PERMISSION_GRANTED
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  fun setCameraEnabledChecked(checked: Boolean) {
    if (!checked) {
      viewModel.setCameraEnabled(false)
      return
    }

    val cameraOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    if (cameraOk) {
      viewModel.setCameraEnabled(true)
    } else {
      val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
      if (permissions.any { shouldOpenSettings(it) }) {
        openAppSettings(context)
        return
      }
      permissions.forEach { permissionRequested[it] = true }
      permissionLauncher.launch(permissions)
    }
  }

  fun requestLocationPermissions() {
    val fineOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarseOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (fineOk || coarseOk) {
      viewModel.setLocationMode(LocationMode.WhileUsing)
    } else {
      pendingLocationRequest = true
      val permissions =
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
      if (permissions.any { shouldOpenSettings(it) }) {
        openAppSettings(context)
        return
      }
      permissions.forEach { permissionRequested[it] = true }
      locationPermissionLauncher.launch(permissions)
    }
  }

  fun setPreciseLocationChecked(checked: Boolean) {
    if (!checked) {
      viewModel.setLocationPreciseEnabled(false)
      return
    }
    val fineOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (fineOk) {
      viewModel.setLocationPreciseEnabled(true)
    } else {
      pendingPreciseToggle = true
      val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
      if (permissions.any { shouldOpenSettings(it) }) {
        openAppSettings(context)
        return
      }
      permissions.forEach { permissionRequested[it] = true }
      locationPermissionLauncher.launch(permissions)
    }
  }

  @Composable
  fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    isDirty: Boolean,
    visible: Boolean,
    onToggleVisible: () -> Unit,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = mobileCaption1, color = mobileTextSecondary) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = mobileBody.copy(color = mobileText),
        singleLine = true,
        visualTransformation =
          if (visible) {
            VisualTransformation.None
          } else {
            PasswordVisualTransformation()
          },
        trailingIcon = {
          IconButton(onClick = onToggleVisible) {
            Icon(
              imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = if (visible) "Hide API key" else "Show API key",
              tint = mobileTextSecondary,
            )
          }
        },
        colors = settingsTextFieldColors(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        Button(
          onClick = onSave,
          enabled = isDirty,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (isDirty) mobileAccent else mobileBorderStrong,
              contentColor = mobileText,
              disabledContentColor = mobileTextTertiary,
            ),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            Icons.Default.Save,
            contentDescription = null,
            modifier = Modifier.padding(end = 6.dp),
          )
          Text("Save", style = mobileCallout)
        }
      }
    }
  }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(mobileBackgroundGradient),
  ) {
    LazyColumn(
      state = listState,
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .imePadding()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              "SETTINGS",
              style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
              color = mobileAccent,
            )
            Text("Agent Configuration", style = mobileTitle2, color = mobileText)
            Text(
              "Manage capabilities, permissions, and diagnostics.",
              style = mobileCallout,
              color = mobileTextSecondary,
            )
          }
          Button(
            onClick = { viewModel.refreshPropAiProfile() },
            colors = ButtonDefaults.buttonColors(containerColor = mobileAccent, contentColor = Color(0xFF08111B)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text("Sync", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
          }
        }
      }
      item { HorizontalDivider(color = mobileBorder) }

    // Order parity: Node → Voice → Camera → Messaging → Location → Screen.
      item {
        Text(
          "NODE",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = displayName,
          onValueChange = viewModel::setDisplayName,
          label = { Text("Agent name", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          colors = settingsTextFieldColors(),
        )
        Text(
          "Shown in headers and voice labels.",
          style = mobileCaption1,
          color = mobileTextSecondary,
        )
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
          value = wakeWordsDraft,
          onValueChange = { input ->
            wakeWordsDraft = input
            viewModel.setWakeWords(WakeWords.parseCommaSeparated(input))
          },
          label = { Text("Wake words", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = false,
          minLines = 2,
          placeholder = {
            Text("hey propai, propai", style = mobileCallout, color = mobileTextTertiary)
          },
          colors = settingsTextFieldColors(),
        )
        Text(
          "Comma-separated. Used by the wake-word listener.",
          style = mobileCaption1,
          color = mobileTextSecondary,
        )
      }
    }
      item { Text("Instance ID: $instanceId", style = mobileCallout.copy(fontFamily = FontFamily.Monospace), color = mobileTextSecondary) }
      item { Text("Device: $deviceModel", style = mobileCallout, color = mobileTextSecondary) }
      item { Text("Version: $appVersion", style = mobileCallout, color = mobileTextSecondary) }
      item {
        Text(
          "Status: ${statusText.ifBlank { "Cloud: ${cloudProvider.label}" }}",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
      }

      item { HorizontalDivider(color = mobileBorder) }

      // Assistant model
      item {
        Text(
          "ASSISTANT",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Box {
          Surface(
            onClick = { showProviderMenu = true },
            shape = RoundedCornerShape(14.dp),
            color = mobileAccentSoft,
            border = BorderStroke(1.dp, mobileBorderStrong),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Provider: ${cloudProvider.label}",
                style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold),
                color = mobileText,
              )
              Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Select provider",
                tint = mobileTextSecondary,
              )
            }
          }

          DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }) {
            CloudProvider.entries.forEach { provider ->
              DropdownMenuItem(
                text = { Text(provider.label, style = mobileCallout, color = mobileText) },
                onClick = {
                  viewModel.setCloudProvider(provider)
                  showProviderMenu = false
                },
              )
            }
          }
        }
      }
      when (cloudProvider) {
        CloudProvider.OpenAI -> {
          item {
            ApiKeyField(
              label = "OpenAI API Key",
              value = openAiApiKeyDraft,
              onValueChange = { openAiApiKeyDraft = it },
              onSave = { viewModel.setOpenAiApiKey(openAiApiKeyDraft) },
              isDirty = openAiApiKeyDraft != openAiApiKey,
              visible = openAiApiKeyVisible,
              onToggleVisible = { openAiApiKeyVisible = !openAiApiKeyVisible },
            )
          }
          item {
            OutlinedTextField(
              value = openAiModel,
              onValueChange = viewModel::setOpenAiModel,
              label = { Text("OpenAI Model", style = mobileCaption1, color = mobileTextSecondary) },
              modifier = Modifier.fillMaxWidth(),
              textStyle = mobileBody.copy(color = mobileText),
              singleLine = true,
              placeholder = {
                Text(CloudProvider.OpenAI.defaultModel, style = mobileCallout, color = mobileTextTertiary)
              },
              colors = settingsTextFieldColors(),
            )
          }
        }
        CloudProvider.Anthropic -> {
          item {
            ApiKeyField(
              label = "Anthropic API Key",
              value = anthropicApiKeyDraft,
              onValueChange = { anthropicApiKeyDraft = it },
              onSave = { viewModel.setAnthropicApiKey(anthropicApiKeyDraft) },
              isDirty = anthropicApiKeyDraft != anthropicApiKey,
              visible = anthropicApiKeyVisible,
              onToggleVisible = { anthropicApiKeyVisible = !anthropicApiKeyVisible },
            )
          }
          item {
            OutlinedTextField(
              value = anthropicModel,
              onValueChange = viewModel::setAnthropicModel,
              label = { Text("Anthropic Model", style = mobileCaption1, color = mobileTextSecondary) },
              modifier = Modifier.fillMaxWidth(),
              textStyle = mobileBody.copy(color = mobileText),
              singleLine = true,
              placeholder = {
                Text(CloudProvider.Anthropic.defaultModel, style = mobileCallout, color = mobileTextTertiary)
              },
              colors = settingsTextFieldColors(),
            )
          }
        }
        CloudProvider.Groq -> {
          item {
            ApiKeyField(
              label = "Groq API Key",
              value = groqApiKeyDraft,
              onValueChange = { groqApiKeyDraft = it },
              onSave = { viewModel.setGroqApiKey(groqApiKeyDraft) },
              isDirty = groqApiKeyDraft != groqApiKey,
              visible = groqApiKeyVisible,
              onToggleVisible = { groqApiKeyVisible = !groqApiKeyVisible },
            )
          }
          item {
            OutlinedTextField(
              value = groqModel,
              onValueChange = viewModel::setGroqModel,
              label = { Text("Groq Model", style = mobileCaption1, color = mobileTextSecondary) },
              modifier = Modifier.fillMaxWidth(),
              textStyle = mobileBody.copy(color = mobileText),
              singleLine = true,
              placeholder = {
                Text(CloudProvider.Groq.defaultModel, style = mobileCallout, color = mobileTextTertiary)
              },
              colors = settingsTextFieldColors(),
            )
          }
        }
        CloudProvider.ElevenLabs -> {
          item {
            ApiKeyField(
              label = "ElevenLabs API Key",
              value = elevenLabsApiKeyDraft,
              onValueChange = { elevenLabsApiKeyDraft = it },
              onSave = { viewModel.setElevenLabsApiKey(elevenLabsApiKeyDraft) },
              isDirty = elevenLabsApiKeyDraft != elevenLabsApiKey,
              visible = elevenLabsApiKeyVisible,
              onToggleVisible = { elevenLabsApiKeyVisible = !elevenLabsApiKeyVisible },
            )
          }
          item {
            OutlinedTextField(
              value = elevenLabsModel,
              onValueChange = viewModel::setElevenLabsModel,
              label = { Text("ElevenLabs Model", style = mobileCaption1, color = mobileTextSecondary) },
              modifier = Modifier.fillMaxWidth(),
              textStyle = mobileBody.copy(color = mobileText),
              singleLine = true,
              placeholder = {
                Text(CloudProvider.ElevenLabs.defaultModel, style = mobileCallout, color = mobileTextTertiary)
              },
              colors = settingsTextFieldColors(),
            )
          }
        }
        CloudProvider.OpenRouter -> {
          item {
            ApiKeyField(
              label = "OpenRouter API Key",
              value = openRouterApiKeyDraft,
              onValueChange = { openRouterApiKeyDraft = it },
              onSave = { viewModel.setOpenRouterApiKey(openRouterApiKeyDraft) },
              isDirty = openRouterApiKeyDraft != openRouterApiKey,
              visible = openRouterApiKeyVisible,
              onToggleVisible = { openRouterApiKeyVisible = !openRouterApiKeyVisible },
            )
          }
          item {
            OutlinedTextField(
              value = openRouterModel,
              onValueChange = viewModel::setOpenRouterModel,
              label = { Text("OpenRouter Model", style = mobileCaption1, color = mobileTextSecondary) },
              modifier = Modifier.fillMaxWidth(),
              textStyle = mobileBody.copy(color = mobileText),
              singleLine = true,
              placeholder = {
                Text(CloudProvider.OpenRouter.defaultModel, style = mobileCallout, color = mobileTextTertiary)
              },
              colors = settingsTextFieldColors(),
            )
          }
        }
      }
      item {
        Text(
          "Cloud-only mode. Keys are stored encrypted on-device.",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
      }

      item { HorizontalDivider(color = mobileBorder) }

      // PropAi Sync
      item {
        Text(
          "PROPAI SYNC",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Text(
          "Link PropAi Sync to your workspace for tenant access and subscription enforcement.",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
      }
      item {
        OutlinedTextField(
          value = propAiControlBaseUrl,
          onValueChange = viewModel::setPropAiControlBaseUrl,
          label = { Text("Control API URL", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = true,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        OutlinedTextField(
          value = propAiControlEmailDraft,
          onValueChange = { propAiControlEmailDraft = it },
          label = { Text("Account email", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = true,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        OutlinedTextField(
          value = propAiControlPasswordDraft,
          onValueChange = { propAiControlPasswordDraft = it },
          label = { Text("Password", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = true,
          visualTransformation =
            if (propAiPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { propAiPasswordVisible = !propAiPasswordVisible }) {
              Icon(
                imageVector = if (propAiPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (propAiPasswordVisible) "Hide password" else "Show password",
                tint = mobileTextSecondary,
              )
            }
          },
          colors = settingsTextFieldColors(),
        )
      }
      item {
        OutlinedTextField(
          value = propAiControlTenantDraft,
          onValueChange = { propAiControlTenantDraft = it },
          label = { Text("Tenant name (for register)", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = true,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = { viewModel.loginPropAi(propAiControlEmailDraft, propAiControlPasswordDraft) },
            enabled = !propAiControlBusy,
            colors = settingsPrimaryButtonColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Login", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
          Button(
            onClick = {
              viewModel.registerPropAi(
                propAiControlEmailDraft,
                propAiControlPasswordDraft,
                propAiControlTenantDraft,
              )
            },
            enabled = !propAiControlBusy,
            colors = settingsPrimaryButtonColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Register", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
        }
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = viewModel::refreshPropAiProfile,
            enabled = !propAiControlBusy && propAiControlUserId.isNotBlank(),
            colors = settingsPrimaryButtonColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Refresh", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
          Button(
            onClick = viewModel::logoutPropAi,
            enabled = !propAiControlBusy && propAiControlUserId.isNotBlank(),
            colors = settingsDangerButtonColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Logout", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
        }
      }
      if (!propAiControlError.isNullOrBlank()) {
        item {
          Text(
            propAiControlError.orEmpty(),
            style = mobileCallout,
            color = mobileDanger,
          )
        }
      }
      item {
        val tenantSummary =
          if (propAiControlTenantName.isNotBlank()) {
            val role = propAiControlTenantRole.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            "Linked tenant: ${propAiControlTenantName}$role"
          } else {
            "Linked tenant: not linked"
          }
        Text(
          tenantSummary,
          style = mobileCallout,
          color = if (propAiControlTenantName.isNotBlank()) mobileText else mobileTextSecondary,
        )
      }
      item {
        OutlinedTextField(
          value = propAiLicenseBaseUrl,
          onValueChange = viewModel::setPropAiLicenseBaseUrl,
          label = { Text("License API URL", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = true,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        ApiKeyField(
          label = "Activation key",
          value = propAiActivationKeyDraft,
          onValueChange = { propAiActivationKeyDraft = it },
          onSave = { viewModel.setPropAiActivationKey(propAiActivationKeyDraft) },
          isDirty = propAiActivationKeyDraft != propAiActivationKey,
          visible = propAiActivationKeyVisible,
          onToggleVisible = { propAiActivationKeyVisible = !propAiActivationKeyVisible },
        )
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = viewModel::activatePropAiLicense,
            enabled = !propAiLicenseBusy,
            colors = settingsPrimaryButtonColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Activate", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
          Button(
            onClick = viewModel::refreshPropAiLicense,
            enabled = !propAiLicenseBusy && propAiActivationToken.isNotBlank(),
            colors = settingsPrimaryButtonColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Refresh", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
          Button(
            onClick = viewModel::clearPropAiLicense,
            enabled = !propAiLicenseBusy,
            colors = settingsDangerButtonColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text("Clear", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
        }
      }
      item {
        val statusLabel = if (propAiLicenseStatus.valid) "Active" else "Inactive"
        val planLabel = propAiLicenseStatus.plan ?: propAiLicenseStatus.status ?: "—"
        val expiryLabel = propAiLicenseStatus.expiresAt ?: propAiLicenseStatus.graceUntil ?: "—"
        val devicesLabel =
          propAiLicenseStatus.deviceLimit?.takeIf { it > 0 }?.let { limit ->
            val used = propAiLicenseStatus.devicesUsed ?: 0
            "$used/$limit devices"
          }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("License: $statusLabel", style = mobileCallout, color = mobileText)
          Text("Plan: $planLabel", style = mobileCallout, color = mobileTextSecondary)
          Text("Expires: $expiryLabel", style = mobileCallout, color = mobileTextSecondary)
          if (devicesLabel != null) {
            Text("Devices: $devicesLabel", style = mobileCallout, color = mobileTextSecondary)
          }
        }
      }
      if (!propAiLicenseError.isNullOrBlank()) {
        item {
          Text(
            propAiLicenseError.orEmpty(),
            style = mobileCallout,
            color = mobileDanger,
          )
        }
      }

      item { HorizontalDivider(color = mobileBorder) }

      // Voice
      item {
        Text(
          "VOICE",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Microphone permission", style = mobileHeadline) },
          supportingContent = {
            Text(
              if (micPermissionGranted) {
                "Granted. Use the Voice tab mic button to capture transcript while the app is open."
              } else {
                "Required for foreground Voice tab transcription."
              },
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = {
                if (micPermissionGranted) {
                  openAppSettings(context)
                } else {
                  if (shouldOpenSettings(Manifest.permission.RECORD_AUDIO)) {
                    openAppSettings(context)
                  } else {
                    permissionRequested[Manifest.permission.RECORD_AUDIO] = true
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  }
                }
              },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                if (micPermissionGranted) "Manage" else "Grant",
                style = mobileCallout.copy(fontWeight = FontWeight.Bold),
              )
            }
          },
        )
      }
      item {
        Text(
          "Voice wake and talk modes were removed. Voice now uses one mic on/off flow in the Voice tab while the app is open.",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Speaker playback", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Play assistant replies aloud with ElevenLabs when credentials are configured, or fall back to system speech.",
              style = mobileCallout,
            )
          },
          trailingContent = { Switch(checked = speakerEnabled, onCheckedChange = viewModel::setSpeakerEnabled) },
        )
      }
      item {
        OutlinedTextField(
          value = elevenLabsAgentId,
          onValueChange = viewModel::setElevenLabsAgentId,
          label = { Text("ElevenLabs Agent ID", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = true,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        ApiKeyField(
          label = "ElevenLabs API Key",
          value = elevenLabsApiKeyDraft,
          onValueChange = { elevenLabsApiKeyDraft = it },
          onSave = { viewModel.setElevenLabsApiKey(elevenLabsApiKeyDraft) },
          isDirty = elevenLabsApiKeyDraft != elevenLabsApiKey,
          visible = elevenLabsApiKeyVisible,
          onToggleVisible = { elevenLabsApiKeyVisible = !elevenLabsApiKeyVisible },
        )
      }
      item {
        OutlinedTextField(
          value = elevenLabsVoiceId,
          onValueChange = viewModel::setElevenLabsVoiceId,
          label = { Text("ElevenLabs Voice ID", style = mobileCaption1, color = mobileTextSecondary) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          singleLine = true,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        Text(
          "Agent ID powers the Voice tab. Voice ID is used for TTS playback in chat replies. Stored encrypted on-device.",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
      }

      item { HorizontalDivider(color = mobileBorder) }

    // Camera
      item {
        Text(
          "CAMERA",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
    item {
      ListItem(
        modifier = Modifier.settingsRowModifier(),
        colors = listItemColors,
        headlineContent = { Text("Allow Camera", style = mobileHeadline) },
        supportingContent = { Text("Allows PropAi Sync to capture photos or short video clips (foreground only).", style = mobileCallout) },
        trailingContent = { Switch(checked = cameraEnabled, onCheckedChange = ::setCameraEnabledChecked) },
      )
    }
    item {
      Text(
        "Tip: grant Microphone permission for video clips with audio.",
        style = mobileCallout,
        color = mobileTextSecondary,
      )
    }

      item { HorizontalDivider(color = mobileBorder) }

    // Messaging
      item {
        Text(
          "MESSAGING",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
    item {
      val buttonLabel =
        when {
          !smsPermissionAvailable -> "Unavailable"
          smsPermissionGranted -> "Manage"
          else -> "Grant"
        }
      ListItem(
        modifier = Modifier.settingsRowModifier(),
        colors = listItemColors,
        headlineContent = { Text("SMS Permission", style = mobileHeadline) },
        supportingContent = {
          Text(
            if (smsPermissionAvailable) {
              "Allow PropAi Sync to send SMS from this device."
            } else {
              "SMS requires a device with telephony hardware."
            },
            style = mobileCallout,
          )
        },
        trailingContent = {
            Button(
              onClick = {
                if (!smsPermissionAvailable) return@Button
                if (smsPermissionGranted) {
                  openAppSettings(context)
                } else {
                  if (shouldOpenSettings(Manifest.permission.SEND_SMS)) {
                    openAppSettings(context)
                  } else {
                    permissionRequested[Manifest.permission.SEND_SMS] = true
                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                  }
                }
              },
            enabled = smsPermissionAvailable,
            colors = settingsPrimaryButtonColors(),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text(buttonLabel, style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
        },
      )
    }

      item { HorizontalDivider(color = mobileBorder) }

    // Notifications
      item {
        Text(
          "NOTIFICATIONS",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        val buttonLabel =
          if (notificationsPermissionGranted) {
            "Manage"
          } else {
            "Grant"
          }
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("System Notifications", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Required for `system.notify` and Android foreground service alerts.",
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = {
                if (notificationsPermissionGranted || Build.VERSION.SDK_INT < 33) {
                  openAppSettings(context)
                } else {
                  if (shouldOpenSettings(Manifest.permission.POST_NOTIFICATIONS)) {
                    openAppSettings(context)
                  } else {
                    permissionRequested[Manifest.permission.POST_NOTIFICATIONS] = true
                    notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                }
              },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(buttonLabel, style = mobileCallout.copy(fontWeight = FontWeight.Bold))
            }
          },
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Notification Listener Access", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Required for `notifications.list` and `notifications.actions`.",
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = { openNotificationListenerSettings(context) },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                if (notificationListenerEnabled) "Manage" else "Enable",
                style = mobileCallout.copy(fontWeight = FontWeight.Bold),
              )
            }
          },
        )
      }
      item { HorizontalDivider(color = mobileBorder) }

      item {
        Text(
          "SPECIAL ACCESS",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Accessibility Service", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Required for screen automation flows.",
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = { openAccessibilitySettings(context) },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                if (accessibilityEnabled) "Manage" else "Enable",
                style = mobileCallout.copy(fontWeight = FontWeight.Bold),
              )
            }
          },
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Display Over Apps", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Required for floating overlays.",
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = { openOverlaySettings(context) },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                if (overlayPermissionGranted) "Manage" else "Enable",
                style = mobileCallout.copy(fontWeight = FontWeight.Bold),
              )
            }
          },
        )
      }
      item { HorizontalDivider(color = mobileBorder) }

      item {
        Text(
          "CHAT LISTENING",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Text(
          "Choose which chat apps the agent can listen to through notifications. Leave conversation filters empty to include every chat in the selected apps.",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
      }
      items(knownChatSourcePresets) { preset ->
        val selected = chatListeningPackages.any { it == preset.packageName }
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text(preset.label, style = mobileHeadline) },
          supportingContent = {
            Text(
              preset.packageName,
              style = mobileCallout.copy(fontFamily = FontFamily.Monospace),
            )
          },
          trailingContent = {
            Switch(
              checked = selected,
              onCheckedChange = { enabled ->
                val updated =
                  if (enabled) {
                    chatListeningPackages + preset.packageName
                  } else {
                    chatListeningPackages.filterNot { it == preset.packageName }
                  }
                viewModel.setChatListeningPackages(updated)
              },
            )
          },
        )
      }
      item {
        OutlinedTextField(
          value = customChatPackageText,
          onValueChange = { raw ->
            val knownSelected = chatListeningPackages.filter { it in knownChatPackages }
            viewModel.setChatListeningPackages(knownSelected + parseListInput(raw))
          },
          label = { Text("Custom Chat App Packages", style = mobileCaption1, color = mobileTextSecondary) },
          placeholder = {
            Text(
              "com.example.chat\norg.example.messenger",
              style = mobileCallout,
              color = mobileTextTertiary,
            )
          },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          minLines = 3,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        OutlinedTextField(
          value = conversationFilterText,
          onValueChange = { raw ->
            viewModel.setChatListeningConversationFilters(parseListInput(raw))
          },
          label = { Text("Conversation Filters", style = mobileCaption1, color = mobileTextSecondary) },
          placeholder = {
            Text(
              "Mom\nDesign Team\nAcme Support",
              style = mobileCallout,
              color = mobileTextTertiary,
            )
          },
          modifier = Modifier.fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          minLines = 3,
          colors = settingsTextFieldColors(),
        )
      }
      item {
        Text(
          "Only selected chat apps are listened to passively. Filters match notification title or message text, so you can narrow listening to specific chats.",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
      }
      item { HorizontalDivider(color = mobileBorder) }

    // Data access
      item {
        Text(
          "DATA ACCESS",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Photos Permission", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Required for `photos.latest`.",
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = {
                if (photosPermissionGranted) {
                  openAppSettings(context)
                } else {
                  if (shouldOpenSettings(photosPermission)) {
                    openAppSettings(context)
                  } else {
                    permissionRequested[photosPermission] = true
                    photosPermissionLauncher.launch(photosPermission)
                  }
                }
              },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                if (photosPermissionGranted) "Manage" else "Grant",
                style = mobileCallout.copy(fontWeight = FontWeight.Bold),
              )
            }
          },
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Contacts Permission", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Required for `contacts.search` and `contacts.add`.",
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = {
                if (contactsPermissionGranted) {
                  openAppSettings(context)
                } else {
                  val permissions =
                    arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                  if (permissions.any { shouldOpenSettings(it) }) {
                    openAppSettings(context)
                  } else {
                    permissions.forEach { permissionRequested[it] = true }
                    contactsPermissionLauncher.launch(permissions)
                  }
                }
              },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                if (contactsPermissionGranted) "Manage" else "Grant",
                style = mobileCallout.copy(fontWeight = FontWeight.Bold),
              )
            }
          },
        )
      }
      item {
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Calendar Permission", style = mobileHeadline) },
          supportingContent = {
            Text(
              "Required for `calendar.events` and `calendar.add`.",
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = {
                if (calendarPermissionGranted) {
                  openAppSettings(context)
                } else {
                  val permissions =
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                  if (permissions.any { shouldOpenSettings(it) }) {
                    openAppSettings(context)
                  } else {
                    permissions.forEach { permissionRequested[it] = true }
                    calendarPermissionLauncher.launch(permissions)
                  }
                }
              },
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(
                if (calendarPermissionGranted) "Manage" else "Grant",
                style = mobileCallout.copy(fontWeight = FontWeight.Bold),
              )
            }
          },
        )
      }
      item {
        val motionButtonLabel =
          when {
            !motionAvailable -> "Unavailable"
            !motionPermissionRequired -> "Manage"
            motionPermissionGranted -> "Manage"
            else -> "Grant"
          }
        ListItem(
          modifier = Modifier.settingsRowModifier(),
          colors = listItemColors,
          headlineContent = { Text("Motion Permission", style = mobileHeadline) },
          supportingContent = {
            Text(
              if (!motionAvailable) {
                "This device does not expose accelerometer or step-counter motion sensors."
              } else {
                "Required for `motion.activity` and `motion.pedometer`."
              },
              style = mobileCallout,
            )
          },
          trailingContent = {
            Button(
              onClick = {
                if (!motionAvailable) return@Button
                if (!motionPermissionRequired || motionPermissionGranted) {
                  openAppSettings(context)
                } else {
                  if (shouldOpenSettings(Manifest.permission.ACTIVITY_RECOGNITION)) {
                    openAppSettings(context)
                  } else {
                    permissionRequested[Manifest.permission.ACTIVITY_RECOGNITION] = true
                    motionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                  }
                }
              },
              enabled = motionAvailable,
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text(motionButtonLabel, style = mobileCallout.copy(fontWeight = FontWeight.Bold))
            }
          },
        )
      }
      item { HorizontalDivider(color = mobileBorder) }

    // Location
      item {
        Text(
          "LOCATION",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("Off", style = mobileHeadline) },
            supportingContent = { Text("Disable location sharing.", style = mobileCallout) },
            trailingContent = {
              RadioButton(
                selected = locationMode == LocationMode.Off,
                onClick = { viewModel.setLocationMode(LocationMode.Off) },
              )
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("While Using", style = mobileHeadline) },
            supportingContent = { Text("Only while PropAi Sync is open.", style = mobileCallout) },
            trailingContent = {
              RadioButton(
                selected = locationMode == LocationMode.WhileUsing,
                onClick = { requestLocationPermissions() },
              )
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("Precise Location", style = mobileHeadline) },
            supportingContent = { Text("Use precise GPS when available.", style = mobileCallout) },
            trailingContent = {
              Switch(
                checked = locationPreciseEnabled,
                onCheckedChange = ::setPreciseLocationChecked,
                enabled = locationMode != LocationMode.Off,
              )
            },
          )
        }
      }
      item { HorizontalDivider(color = mobileBorder) }

    // Screen
      item {
        Text(
          "SCREEN",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
    item {
      ListItem(
        modifier = Modifier.settingsRowModifier(),
        colors = listItemColors,
        headlineContent = { Text("Prevent Sleep", style = mobileHeadline) },
        supportingContent = { Text("Keeps the screen awake while PropAi Sync is open.", style = mobileCallout) },
        trailingContent = { Switch(checked = preventSleep, onCheckedChange = viewModel::setPreventSleep) },
      )
    }

      item { HorizontalDivider(color = mobileBorder) }

    // Debug
      item {
        Text(
          "DEBUG",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
    item {
      ListItem(
        modifier = Modifier.settingsRowModifier(),
        colors = listItemColors,
        headlineContent = { Text("Debug Canvas Status", style = mobileHeadline) },
        supportingContent = { Text("Show status text in the canvas when debug is enabled.", style = mobileCallout) },
        trailingContent = {
          Switch(
            checked = canvasDebugStatusEnabled,
            onCheckedChange = viewModel::setCanvasDebugStatusEnabled,
          )
        },
      )
    }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun settingsTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedContainerColor = mobileSurface,
    unfocusedContainerColor = mobileSurface,
    focusedBorderColor = mobileAccent,
    unfocusedBorderColor = mobileBorder,
    focusedTextColor = mobileText,
    unfocusedTextColor = mobileText,
    cursorColor = mobileAccent,
  )

private fun Modifier.settingsRowModifier() =
  this
    .fillMaxWidth()
    .border(width = 1.dp, color = mobileBorderStrong, shape = RoundedCornerShape(14.dp))
    .background(mobileSurfaceStrong.copy(alpha = 0.94f), RoundedCornerShape(14.dp))

private fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

@Composable
private fun settingsPrimaryButtonColors() =
  ButtonDefaults.buttonColors(
    containerColor = mobileAccent,
    contentColor = Color.White,
    disabledContainerColor = mobileAccent.copy(alpha = 0.45f),
    disabledContentColor = Color.White.copy(alpha = 0.9f),
  )

@Composable
private fun settingsDangerButtonColors() =
  ButtonDefaults.buttonColors(
    containerColor = mobileDanger,
    contentColor = Color.White,
    disabledContainerColor = mobileDanger.copy(alpha = 0.45f),
    disabledContentColor = Color.White.copy(alpha = 0.9f),
  )

private fun openAppSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    )
  context.startActivity(intent)
}

private fun openNotificationListenerSettings(context: Context) {
  val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
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
  val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
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
    )
  runCatching {
    context.startActivity(intent)
  }.getOrElse {
    openAppSettings(context)
  }
}

private fun hasNotificationsPermission(context: Context): Boolean {
  if (Build.VERSION.SDK_INT < 33) return true
  return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
  return DeviceNotificationListenerService.isAccessEnabled(context)
}

private fun hasMotionCapabilities(context: Context): Boolean {
  val sensorManager = context.getSystemService(SensorManager::class.java) ?: return false
  return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}

