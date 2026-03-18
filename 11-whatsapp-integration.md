# 11. WhatsApp Integration

### ⚠️ Important Legal Notice

**Before implementing:**
- Review [WhatsApp Terms of Service](https://www.whatsapp.com/legal/terms)
- Automated messaging may violate ToS
- Use only for personal automation (not spam/commercial)
- Consider official [WhatsApp Business API](https://developers.facebook.com/docs/whatsapp) for business use
- Respect user privacy and consent

---

## Architecture Options

### Option 1: Baileys Server + Android App (Cloud-Based)

**Best for:** Full WhatsApp functionality, message sending/receiving

```
┌─────────────────────┐         ┌─────────────────────┐
│   Android Device    │         │   VPS/Cloud Server  │
│                     │         │                     │
│  Assistant App      │  REST   │  Node.js + Baileys  │
│  ◄─────────────────┼─────────►  WhatsApp Web       │
│                     │  WS     │                     │
└─────────────────────┘         └─────────────────────┘
```

### Option 2: Accessibility Service (On-Device)

**Best for:** Reading messages, no server needed

```
┌──────────────────────────────────────┐
│         Android Device               │
│                                      │
│  Assistant Service                   │
│  ◄─── Accessibility Service ────────►│
│  ◄─── Notification Listener ────────►│
│                                      │
│  WhatsApp App                        │
└──────────────────────────────────────┘
```

### Option 3: Notification Listener (Simple)

**Best for:** Read-only message monitoring

```
┌──────────────────────────────────────┐
│         Android Device               │
│                                      │
│  Notification Listener               │
│  ◄─── System Notifications ─────────►│
│                                      │
│  WhatsApp Notifications              │
└──────────────────────────────────────┘
```

---

## Option 1: Baileys Server Implementation

### 11.1 Server Setup (Node.js)

```javascript
// server/index.js
const { default: makeWASocket, useMultiFileAuthState } = require('@whiskeysockets/baileys')
const express = require('express')
const ws = require('ws')
const cors = require('cors')

const app = express()
app.use(cors())
app.use(express.json())

let socket = null
let authState = null

// Initialize WhatsApp connection
async function connectToWhatsApp() {
    const { state, saveCreds } = await useMultiFileAuthState('auth_info')
    authState = state
    
    socket = makeWASocket({
        auth: state,
        printQRInTerminal: true
    })
    
    socket.ev.on('connection.update', (update) => {
        const { connection, lastDisconnect } = update
        if (connection === 'close') {
            const shouldReconnect = lastDisconnect.error?.output?.statusCode !== 401
            if (shouldReconnect) {
                connectToWhatsApp()
            }
        } else if (connection === 'open') {
            console.log('✅ Connected to WhatsApp')
        }
    })
    
    socket.ev.on('creds.update', saveCreds)
    
    // Listen for messages
    socket.ev.on('messages.upsert', async (m) => {
        const msg = m.messages[0]
        if (msg.key.fromMe) return // Ignore self messages
        
        const messageData = {
            id: msg.key.id,
            from: msg.key.remoteJid,
            message: msg.message?.conversation || msg.message?.extendedTextMessage?.text,
            timestamp: msg.messageTimestamp,
            pushName: msg.pushName
        }
        
        // Broadcast to connected Android clients
        broadcastToClients(messageData)
    })
}

// WebSocket for real-time messaging
const wss = new ws.WebSocketServer({ port: 8080 })
const clients = new Set()

wss.on('connection', (ws) => {
    clients.add(ws)
    console.log('📱 Android client connected')
    
    ws.on('close', () => {
        clients.delete(ws)
    })
})

function broadcastToClients(data) {
    clients.forEach(client => {
        if (client.readyState === ws.OPEN) {
            client.send(JSON.stringify(data))
        }
    })
}

// REST API for sending messages
app.post('/api/send-message', async (req, res) => {
    const { to, message } = req.body
    
    try {
        const result = await socket.sendMessage(to, { text: message })
        res.json({ success: true, messageId: result.key.id })
    } catch (error) {
        res.status(500).json({ success: false, error: error.message })
    }
})

// Get recent messages
app.get('/api/messages', async (req, res) => {
    // Implement message storage/retrieval
    res.json({ messages: [] })
})

// Health check
app.get('/api/health', (req, res) => {
    res.json({ status: socket ? 'connected' : 'disconnected' })
})

// Start server
connectToWhatsApp()
app.listen(3000, () => {
    console.log('🚀 Server running on port 3000')
})
wss.on('listening', () => {
    console.log('🔌 WebSocket running on port 8080')
})
```

### 11.2 Server Package.json

```json
{
  "name": "whatsapp-baileys-server",
  "version": "1.0.0",
  "main": "index.js",
  "dependencies": {
    "@whiskeysockets/baileys": "^6.5.0",
    "express": "^4.18.2",
    "ws": "^8.14.2",
    "cors": "^2.8.5",
    "qrcode-terminal": "^0.12.0"
  }
}
```

### 11.3 Android Client - WhatsApp Service

```kotlin
// services/WhatsAppService.kt
package com.personalassistant.services

import android.content.Context
import android.util.Log
import com.personalassistant.data.remote.WhatsAppApi
import com.personalassistant.model.WhatsAppMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    val messages: StateFlow<List<WhatsAppMessage>> = _messages

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val whatsAppApi: WhatsAppApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        retrofit2.Retrofit.Builder()
            .baseUrl("https://your-server.com/")
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(WhatsAppApi::class.java)
    }

    fun connect(serverUrl: String) {
        scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING

                val request = okhttp3.Request.Builder()
                    .url("ws://$serverUrl:8080")
                    .build()

                webSocket = OkHttpClient().newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "🔌 WebSocket connected")
                        _connectionState.value = ConnectionState.CONNECTED
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val message = com.google.gson.Gson().fromJson(
                                text,
                                WhatsAppMessage::class.java
                            )
                            val currentList = _messages.value.toMutableList()
                            currentList.add(0, message)
                            _messages.value = currentList
                            Log.d(TAG, "📨 New message: ${message.message}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing message", e)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "❌ WebSocket error", t)
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "🔌 WebSocket closed")
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    suspend fun sendMessage(to: String, message: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = whatsAppApi.sendMessage(
                    SendMessageRequest(
                        to = to,
                        message = message
                    )
                )
                if (response.success) {
                    Result.success(response.messageId ?: "")
                } else {
                    Result.failure(Exception("Failed to send message"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecentMessages(): List<WhatsAppMessage> {
        return withContext(Dispatchers.IO) {
            try {
                whatsAppApi.getMessages().messages
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun disconnect() {
        webSocket?.cancel()
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    companion object {
        private const val TAG = "WhatsAppService"
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

// data/remote/WhatsAppModels.kt
package com.personalassistant.data.remote

import com.google.gson.annotations.SerializedName

data class WhatsAppMessage(
    val id: String,
    val from: String,
    val message: String,
    val timestamp: Long,
    val pushName: String?,
    val isRead: Boolean = false
)

data class SendMessageRequest(
    val to: String,
    val message: String
)

data class SendMessageResponse(
    val success: Boolean,
    val messageId: String?,
    val error: String? = null
)

data class GetMessagesResponse(
    val messages: List<WhatsAppMessage>
)

// WhatsAppApi.kt
package com.personalassistant.data.remote

import retrofit2.http.*

interface WhatsAppApi {
    
    @POST("api/send-message")
    suspend fun sendMessage(@Body request: SendMessageRequest): SendMessageResponse
    
    @GET("api/messages")
    suspend fun getMessages(): GetMessagesResponse
    
    @GET("api/health")
    suspend fun healthCheck(): HealthResponse
}

data class HealthResponse(
    val status: String
)
```

### 11.4 Android - WhatsApp UI Screen

```kotlin
// ui/whatsapp/WhatsAppScreen.kt
package com.personalassistant.ui.whatsapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.model.WhatsAppMessage
import com.personalassistant.services.WhatsAppService
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WhatsAppScreen(
    viewModel: WhatsAppViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var showSendMessageDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "WhatsApp Messages",
                style = MaterialTheme.typography.headlineMedium
            )
            
            ConnectionIndicator(state = connectionState)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Messages list
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No messages yet")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    WhatsAppMessageCard(message = message)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Send button
        Button(
            onClick = { showSendMessageDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = connectionState == com.personalassistant.services.ConnectionState.CONNECTED
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Send Message")
        }
    }

    if (showSendMessageDialog) {
        SendMessageDialog(
            onSend = { to, message ->
                viewModel.sendMessage(to, message)
                showSendMessageDialog = false
            },
            onDismiss = { showSendMessageDialog = false }
        )
    }
}

@Composable
private fun ConnectionIndicator(state: com.personalassistant.services.ConnectionState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is com.personalassistant.services.ConnectionState.CONNECTED -> 
                    MaterialTheme.colorScheme.primaryContainer
                is com.personalassistant.services.ConnectionState.CONNECTING -> 
                    MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Text(
            text = when (state) {
                is com.personalassistant.services.ConnectionState.CONNECTED -> "● Connected"
                is com.personalassistant.services.ConnectionState.CONNECTING -> "● Connecting..."
                else -> "● Disconnected"
            },
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun WhatsAppMessageCard(message: WhatsAppMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.pushName ?: formatNumber(message.from),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SendMessageDialog(
    onSend: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var number by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Message") },
        text = {
            Column {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+1234567890") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    placeholder = { Text("Type your message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(number, message) },
                enabled = number.isNotBlank() && message.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatNumber(number: String): String {
    return number.replace("@s.whatsapp.net", "")
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}
```

---

## Option 2: Accessibility Service (On-Device)

### 11.5 WhatsApp Accessibility Service

```kotlin
// services/WhatsAppAccessibilityService.kt
package com.personalassistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppAccessibilityService : AccessibilityService() {

    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    val messages: StateFlow<List<WhatsAppMessage>> = _messages

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "✅ WhatsApp Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Check if event is from WhatsApp
        val packageName = event.packageName?.toString()
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                extractMessageFromWindow(event)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                extractMessageFromNotification(event)
            }
        }
    }

    private fun extractMessageFromWindow(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        // Find message text views
        val messageNodes = findNodesByClassName(rootNode, "android.widget.TextView")
        
        messageNodes.forEach { node ->
            val text = node.text?.toString()
            if (!text.isNullOrEmpty() && isValidMessage(text)) {
                val message = WhatsAppMessage(
                    id = System.currentTimeMillis().toString(),
                    from = "Unknown",
                    message = text,
                    timestamp = System.currentTimeMillis() / 1000,
                    pushName = "Contact"
                )
                
                val currentList = _messages.value.toMutableList()
                currentList.add(0, message)
                _messages.value = currentList
                
                Log.d(TAG, "📨 Captured message: $text")
            }
        }
    }

    private fun extractMessageFromNotification(event: AccessibilityEvent) {
        val text = event.text?.firstOrNull()?.toString()
        if (!text.isNullOrEmpty()) {
            // Parse notification text: "John: Hello"
            val parts = text.split(": ", limit = 2)
            if (parts.size == 2) {
                val message = WhatsAppMessage(
                    id = System.currentTimeMillis().toString(),
                    from = parts[0],
                    message = parts[1],
                    timestamp = System.currentTimeMillis() / 1000,
                    pushName = parts[0]
                )
                
                val currentList = _messages.value.toMutableList()
                currentList.add(0, message)
                _messages.value = currentList
            }
        }
    }

    private fun findNodesByClassName(
        node: AccessibilityNodeInfo,
        className: String
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            
            if (className == child.className?.toString()) {
                results.add(child)
            } else {
                results.addAll(findNodesByClassName(child, className))
            }
            
            child.recycle()
        }
        
        return results
    }

    private fun isValidMessage(text: String): Boolean {
        return text.length > 2 && text.length < 500
    }

    override fun onInterrupt() {
        Log.d(TAG, "❌ Accessibility Service interrupted")
    }

    companion object {
        private const val TAG = "WhatsAppA11yService"
    }
}
```

### 11.6 Notification Listener Service

```kotlin
// services/WhatsAppNotificationListener.kt
package com.personalassistant.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WhatsAppNotificationListener : NotificationListenerService() {

    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    val messages: StateFlow<List<WhatsAppMessage>> = _messages

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // Check if notification is from WhatsApp
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: return
        val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: return

        // Parse: "John Doe: Hey, how are you?"
        val parts = text.split(": ", limit = 2)
        val from = title
        val message = if (parts.size > 1) parts[1] else parts[0]

        val whatsappMessage = WhatsAppMessage(
            id = System.currentTimeMillis().toString(),
            from = from,
            message = message,
            timestamp = sbn.postTime / 1000,
            pushName = from
        )

        val currentList = _messages.value.toMutableList()
        currentList.add(0, whatsappMessage)
        _messages.value = currentList

        Log.d(TAG, "📨 Notification captured: $from - $message")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Handle notification removal if needed
    }

    companion object {
        private const val TAG = "WhatsAppNotification"
    }
}
```

### 11.7 AndroidManifest.xml Updates

```xml
<!-- Accessibility Service -->
<service
    android:name=".services.WhatsAppAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/whatsapp_accessibility_config" />
</service>

<!-- Notification Listener -->
<service
    android:name=".services.WhatsAppNotificationListener"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true" />
```

```xml
<!-- res/xml/whatsapp_accessibility_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagIncludeNotImportantViews|flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:settingsActivity="com.personalassistant.ui.settings.WhatsAppSettingsActivity"
    android:description="@string/whatsapp_accessibility_description" />
```

---

## Option 3: Official WhatsApp Business API

### 11.8 Business API Integration

```kotlin
// data/remote/WhatsAppBusinessApi.kt
package com.personalassistant.data.remote

import retrofit2.http.*

interface WhatsAppBusinessApi {
    
    @POST("v1/messages")
    @Headers("Authorization: Bearer {token}")
    suspend fun sendTemplateMessage(
        @Body request: TemplateMessageRequest
    ): MessageResponse
    
    @POST("v1/messages")
    @Headers("Authorization: Bearer {token}")
    suspend fun sendTextMessage(
        @Body request: TextMessageRequest
    ): MessageResponse
}

data class TemplateMessageRequest(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "template",
    val template: Template
)

data class TextMessageRequest(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "text",
    val text: TextContent
)

data class Template(
    val name: String,
    val language: Language,
    val components: List<Component>? = null
)

data class Language(
    val code: String
)

data class TextContent(
    val body: String
)

data class MessageResponse(
    val messages: List<MessageStatus>?
)

data class MessageStatus(
    val id: String
)
```

---

## Comparison Table

| Feature | Baileys Server | Accessibility | Notification | Business API |
|---------|---------------|---------------|--------------|--------------|
| **Read Messages** | ✅ Full | ✅ Limited | ✅ Limited | ❌ No |
| **Send Messages** | ✅ Full | ❌ No | ❌ No | ✅ Limited |
| **Server Required** | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **Setup Complexity** | High | Medium | Low | Medium |
| **ToS Risk** | ⚠️ Medium | ✅ Low | ✅ Low | ✅ None |
| **Real-time** | ✅ Yes | ✅ Yes | ✅ Yes | ⚠️ Webhook |
| **Media Support** | ✅ Yes | ❌ No | ❌ No | ✅ Yes |
| **Group Chats** | ✅ Yes | ⚠️ Limited | ⚠️ Limited | ✅ Yes |

---

## Recommendation

### For Personal Use:
**Start with Notification Listener** (Option 3)
- ✅ Easy to implement
- ✅ No server needed
- ✅ Low ToS risk
- ❌ Read-only

### For Full Features:
**Use Baileys Server** (Option 1)
- ✅ Complete functionality
- ✅ Send + receive
- ❌ Requires server
- ⚠️ ToS considerations

### For Business:
**Use Official Business API** (Option 4)
- ✅ Fully compliant
- ✅ Business features
- ❌ Limited personal use
- ❌ Approval required

---

## Security & Privacy

```kotlin
// util/WhatsAppSecurityManager.kt
package com.personalassistant.util

import android.content.Context
import android.util.Log
import com.personalassistant.data.preferences.AssistantPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppSecurityManager @Inject constructor(
    @Inject @ApplicationContext private val context: Context,
    private val preferences: AssistantPreferences
) {

    fun validateServerUrl(url: String): Boolean {
        return url.startsWith("https://") || url.startsWith("wss://")
    }

    fun encryptPhoneNumber(phone: String): String {
        // Encrypt before storing
        return phone // Implement encryption
    }

    fun shouldStoreMessages(): Boolean {
        return preferences.whatsappStoreMessages
    }

    fun getRetentionDays(): Int {
        return preferences.whatsappDataRetentionDays
    }

    fun isNotificationListenerEnabled(): Boolean {
        return preferences.whatsappNotificationEnabled
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 4096
        const val RATE_LIMIT_MESSAGES_PER_MINUTE = 20
    }
}
```

---

## Summary

| Approach | Best For | Complexity | ToS Risk |
|----------|----------|------------|----------|
| **Baileys Server** | Full features | High | ⚠️ Medium |
| **Accessibility** | Read messages | Medium | ✅ Low |
| **Notification** | Simple monitoring | Low | ✅ Low |
| **Business API** | Business use | Medium | ✅ None |

### Quick Start (Notification - Easiest)
1. Add Notification Listener permission
2. Filter WhatsApp notifications
3. Parse message text
4. Display in assistant UI

### Full Setup (Baileys Server)
1. Deploy Node.js server with Baileys
2. Scan QR code to connect WhatsApp
3. Android app connects via WebSocket
4. Send/receive messages through API

---

**Previous**: [Vision Capabilities](./10-vision-capabilities.md)
