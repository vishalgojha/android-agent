package ai.androidassistant.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.androidassistant.app.automation.AutomationRule
import java.text.SimpleDateFormat
import java.util.*

/**
 * Automation tab - displays list of automation rules
 */
@Composable
fun AutomationTab(
    viewModel: AutomationViewModel,
    onNavigateToBuilder: () -> Unit
) {
    val rules by viewModel.rules.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<AutomationRule?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Automation Rules",
                style = MaterialTheme.typography.headlineMedium
            )
            
            FloatingActionButton(
                onClick = onNavigateToBuilder,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Rule"
                )
            }
        }
        
        // Rules list
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No automation rules yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap + to create your first rule",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { viewModel.toggleRule(rule.id) },
                        onDelete = { 
                            ruleToDelete = rule
                            showDeleteDialog = true 
                        }
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Rule") },
            text = { Text("Are you sure you want to delete '${ruleToDelete!!.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(ruleToDelete!!.id)
                        showDeleteDialog = false
                        ruleToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showDeleteDialog = false
                        ruleToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Individual rule card
 */
@Composable
fun RuleCard(
    rule: AutomationRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) 
                MaterialTheme.colorScheme.surfaceVariant 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Trigger info
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTriggerType(rule.triggerType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Action info
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatActionType(rule.actionType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Triggered: ${rule.triggerCount} times",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                rule.lastTriggered?.let { lastTriggered ->
                    Text(
                        text = "Last: ${formatDate(lastTriggered)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatTriggerType(type: ai.androidassistant.app.automation.TriggerType): String {
    return when (type) {
        ai.androidassistant.app.automation.TriggerType.NOTIFICATION -> "Notification"
        ai.androidassistant.app.automation.TriggerType.TIME -> "Time"
        ai.androidassistant.app.automation.TriggerType.LOCATION -> "Location"
        ai.androidassistant.app.automation.TriggerType.CONNECTIVITY -> "Connectivity"
        ai.androidassistant.app.automation.TriggerType.CALL_EVENT -> "Call"
        ai.androidassistant.app.automation.TriggerType.MESSAGE_RECEIVED -> "Message"
        ai.androidassistant.app.automation.TriggerType.VOICE_COMMAND -> "Voice"
        ai.androidassistant.app.automation.TriggerType.APP_EVENT -> "App Event"
    }
}

private fun formatActionType(type: ai.androidassistant.app.automation.ActionType): String {
    return when (type) {
        ai.androidassistant.app.automation.ActionType.ANNOUNCE -> "Announce"
        ai.androidassistant.app.automation.ActionType.SEND_MESSAGE -> "Send Message"
        ai.androidassistant.app.automation.ActionType.LAUNCH_APP -> "Launch App"
        ai.androidassistant.app.automation.ActionType.CHANGE_SETTING -> "Change Setting"
        ai.androidassistant.app.automation.ActionType.NAVIGATE -> "Navigate"
        ai.androidassistant.app.automation.ActionType.CREATE_REMINDER -> "Create Reminder"
        ai.androidassistant.app.automation.ActionType.LOG_EVENT -> "Log Event"
        ai.androidassistant.app.automation.ActionType.TRIGGER_WEBHOOK -> "Webhook"
        ai.androidassistant.app.automation.ActionType.INVOKE_COMMAND -> "Invoke Command"
    }
}

private fun formatDate(date: Date): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(date)
}

