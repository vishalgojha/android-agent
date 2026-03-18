package ai.androidassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ai.androidassistant.app.automation.ActionConfig
import ai.androidassistant.app.automation.ActionType
import ai.androidassistant.app.automation.AutomationRule
import ai.androidassistant.app.automation.TriggerConfig
import ai.androidassistant.app.automation.TriggerType

private data class TriggerOption(
  val type: TriggerType,
  val title: String,
  val description: String,
)

private data class ActionOption(
  val type: ActionType,
  val title: String,
  val description: String,
)

private val supportedTriggerOptions =
  listOf(
    TriggerOption(
      type = TriggerType.NOTIFICATION,
      title = "Notification",
      description = "Trigger when a notification arrives from a selected app.",
    ),
    TriggerOption(
      type = TriggerType.TIME,
      title = "Time",
      description = "Trigger at a specific time each day.",
    ),
  )

private val supportedActionOptions =
  listOf(
    ActionOption(
      type = ActionType.ANNOUNCE,
      title = "Announce",
      description = "Speak a short message aloud with Android TTS.",
    ),
    ActionOption(
      type = ActionType.INVOKE_COMMAND,
      title = "Invoke Command",
      description = "Call a local AndroidAssistant invoke command with trigger metadata.",
    ),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleBuilderScreen(viewModel: AutomationViewModel, onNavigateBack: () -> Unit) {
  var ruleName by remember { mutableStateOf("") }
  var selectedTriggerType by remember { mutableStateOf<TriggerType?>(null) }
  var selectedActionType by remember { mutableStateOf<ActionType?>(null) }
  var showTriggerSelector by remember { mutableStateOf(false) }
  var showActionSelector by remember { mutableStateOf(false) }
  var showError by remember { mutableStateOf(false) }

  var notificationPackage by remember { mutableStateOf("com.whatsapp") }
  var notificationKeyword by remember { mutableStateOf("") }
  var timeHour by remember { mutableStateOf("8") }
  var timeMinute by remember { mutableStateOf("0") }
  var announceText by remember { mutableStateOf("Alert!") }
  var invokeCommand by remember { mutableStateOf("notifications.list") }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(16.dp),
  ) {
    TopAppBar(
      title = { Text("Create Rule") },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
      actions = {
        IconButton(
          onClick = {
            val rule =
              buildRule(
                name = ruleName,
                triggerType = selectedTriggerType,
                actionType = selectedActionType,
                notificationPackage = notificationPackage,
                notificationKeyword = notificationKeyword,
                timeHour = timeHour.toIntOrNull(),
                timeMinute = timeMinute.toIntOrNull(),
                announceText = announceText,
                invokeCommand = invokeCommand,
              )
            if (rule == null) {
              showError = true
              return@IconButton
            }
            viewModel.createRule(rule)
            onNavigateBack()
          },
        ) {
          Icon(imageVector = Icons.Default.Check, contentDescription = "Save")
        }
      },
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
      value = ruleName,
      onValueChange = {
        showError = false
        ruleName = it
      },
      label = { Text("Rule Name") },
      placeholder = { Text("e.g. WhatsApp Morning Brief") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(text = "Trigger", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    SelectionCard(
      title = supportedTriggerOptions.firstOrNull { it.type == selectedTriggerType }?.title ?: "Select Trigger",
      description =
        supportedTriggerOptions.firstOrNull { it.type == selectedTriggerType }?.description
          ?: "Only supported trigger types are shown here for now.",
      onClick = { showTriggerSelector = true },
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(text = "Action", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    SelectionCard(
      title = supportedActionOptions.firstOrNull { it.type == selectedActionType }?.title ?: "Select Action",
      description =
        supportedActionOptions.firstOrNull { it.type == selectedActionType }?.description
          ?: "Only supported action types are shown here for now.",
      onClick = { showActionSelector = true },
    )

    Spacer(modifier = Modifier.height(24.dp))

    when (selectedTriggerType) {
      TriggerType.NOTIFICATION -> {
        OutlinedTextField(
          value = notificationPackage,
          onValueChange = { notificationPackage = it },
          label = { Text("App Package") },
          supportingText = { Text("Examples: com.whatsapp, com.instagram.android") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = notificationKeyword,
          onValueChange = { notificationKeyword = it },
          label = { Text("Keyword (optional)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      }
      TriggerType.TIME -> {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = timeHour,
            onValueChange = { timeHour = it },
            label = { Text("Hour") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(112.dp),
            singleLine = true,
          )
          OutlinedTextField(
            value = timeMinute,
            onValueChange = { timeMinute = it },
            label = { Text("Minute") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(112.dp),
            singleLine = true,
          )
        }
      }
      null -> {
        Text(
          text = "Choose when this rule should fire.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      else -> Unit
    }

    Spacer(modifier = Modifier.height(24.dp))

    when (selectedActionType) {
      ActionType.ANNOUNCE -> {
        OutlinedTextField(
          value = announceText,
          onValueChange = { announceText = it },
          label = { Text("Announcement Text") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 2,
        )
      }
      ActionType.INVOKE_COMMAND -> {
        OutlinedTextField(
          value = invokeCommand,
          onValueChange = { invokeCommand = it },
          label = { Text("AndroidAssistant Command") },
          supportingText = { Text("Example: notifications.list or device.describe") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      }
      null -> {
        Text(
          text = "Choose what should happen when the trigger matches.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      else -> Unit
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "More trigger and action types can be added once the base flow is stable.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (showError) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Complete the required fields before saving.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }

  if (showTriggerSelector) {
    SelectorDialog(
      title = "Select Trigger",
      items = supportedTriggerOptions,
      itemTitle = { it.title },
      itemDescription = { it.description },
      onSelect = {
        selectedTriggerType = it.type
        showTriggerSelector = false
      },
      onDismiss = { showTriggerSelector = false },
    )
  }

  if (showActionSelector) {
    SelectorDialog(
      title = "Select Action",
      items = supportedActionOptions,
      itemTitle = { it.title },
      itemDescription = { it.description },
      onSelect = {
        selectedActionType = it.type
        showActionSelector = false
      },
      onDismiss = { showActionSelector = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionCard(title: String, description: String, onClick: () -> Unit) {
  Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null)
    }
  }
}

@Composable
private fun <T> SelectorDialog(
  title: String,
  items: List<T>,
  itemTitle: (T) -> String,
  itemDescription: (T) -> String,
  onSelect: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      LazyColumn {
        items(items) { item ->
          TextButton(
            onClick = { onSelect(item) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(text = itemTitle(item), style = MaterialTheme.typography.titleMedium)
              Text(
                text = itemDescription(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

private fun buildRule(
  name: String,
  triggerType: TriggerType?,
  actionType: ActionType?,
  notificationPackage: String,
  notificationKeyword: String,
  timeHour: Int?,
  timeMinute: Int?,
  announceText: String,
  invokeCommand: String,
): AutomationRule? {
  val trimmedName = name.trim()
  if (trimmedName.isEmpty() || triggerType == null || actionType == null) {
    return null
  }

  val triggerConfig =
    when (triggerType) {
      TriggerType.NOTIFICATION -> {
        val packageName = notificationPackage.trim()
        if (packageName.isEmpty()) {
          return null
        }
        TriggerConfig.NotificationConfig(
          packageName = packageName,
          keyword = notificationKeyword.trim().ifEmpty { null },
        )
      }
      TriggerType.TIME -> {
        val hour = timeHour ?: return null
        val minute = timeMinute ?: return null
        if (hour !in 0..23 || minute !in 0..59) {
          return null
        }
        TriggerConfig.TimeConfig(hour = hour, minute = minute)
      }
      else -> return null
    }

  val actionConfig =
    when (actionType) {
      ActionType.ANNOUNCE -> {
        val text = announceText.trim()
        if (text.isEmpty()) {
          return null
        }
        ActionConfig.AnnounceConfig(text = text)
      }
      ActionType.INVOKE_COMMAND -> {
        val command = invokeCommand.trim()
        if (command.isEmpty()) {
          return null
        }
        ActionConfig.InvokeCommandConfig(command = command)
      }
      else -> return null
    }

  return AutomationRule(
    name = trimmedName,
    triggerType = triggerType,
    triggerConfig = triggerConfig,
    actionType = actionType,
    actionConfig = actionConfig,
  )
}

