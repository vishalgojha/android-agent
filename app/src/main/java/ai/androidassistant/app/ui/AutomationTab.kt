package ai.androidassistant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            .background(mobileBackgroundGradient)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Automation",
                style = mobileTitle2,
                color = mobileText
            )

            Button(
                onClick = onNavigateToBuilder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mobileAccent,
                    contentColor = Color(0xFF08111B)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Rule",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("New rule", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
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
                    Text(
                        text = "No automation rules yet",
                        style = mobileHeadline,
                        color = mobileTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 6.dp)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (rule.isEnabled) mobileSurfaceStrong else mobileSurfaceStrong.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, mobileBorderStrong),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.name,
                    style = mobileHeadline,
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
                            tint = mobileDanger
                        )
                    }
                    
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Trigger info
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = mobileTextTertiary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTriggerType(rule.triggerType),
                    style = mobileCaption1,
                    color = mobileTextSecondary
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
                    tint = mobileTextTertiary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatActionType(rule.actionType),
                    style = mobileCaption1,
                    color = mobileTextSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Triggered: ${rule.triggerCount} times",
                    style = mobileCaption2,
                    color = mobileTextSecondary
                )
                
                rule.lastTriggered?.let { lastTriggered ->
                    Text(
                        text = "Last: ${formatDate(lastTriggered)}",
                        style = mobileCaption2,
                        color = mobileTextSecondary
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

