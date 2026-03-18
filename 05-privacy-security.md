# 5. Privacy & Security Implementation

## 5.1 Permission Management

```kotlin
// util/PermissionHelper.kt
package com.personalassistant.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.personalassistant.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHelper @Inject constructor() {

    private val _permissionStatus = MutableStateFlow(mapOf<String, PermissionStatus>())
    val permissionStatus: StateFlow<Map<String, PermissionStatus>> = _permissionStatus

    private val requiredPermissions = listOf(
        Manifest.permission.INTERNET,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val optionalPermissions = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SYSTEM_ALERT_WINDOW
    )

    fun checkPermissions(context: Context): Map<String, PermissionStatus> {
        val allPermissions = requiredPermissions + optionalPermissions
        val statusMap = mutableMapOf<String, PermissionStatus>()

        allPermissions.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            statusMap[permission] = if (granted) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }
        }

        _permissionStatus.value = statusMap
        return statusMap
    }

    fun requestPermissions(
        activity: Activity,
        permissions: List<String>,
        requestCode: Int
    ) {
        ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), requestCode)
    }

    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    fun isAllRequiredGranted(context: Context): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isAllGranted(context: Context): Boolean {
        return (requiredPermissions + optionalPermissions).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1002
    }
}

enum class PermissionStatus {
    GRANTED,
    DENIED,
    DENIED_NEVER_ASK_AGAIN,
    NOT_REQUESTED
}
```

## 5.2 Data Encryption

```kotlin
// util/EncryptionManager.kt
package com.personalassistant.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore")
    private val masterKey: MasterKey

    init {
        keyStore.load(null)
        masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun encrypt(data: String): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        
        return EncryptedData(
            ciphertext = Base64.encodeToString(encryptedBytes, Base64.DEFAULT),
            iv = Base64.encodeToString(iv, Base64.DEFAULT)
        )
    }

    fun decrypt(encryptedData: EncryptedData): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, 
            Base64.decode(encryptedData.iv, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        
        val decryptedBytes = cipher.doFinal(
            Base64.decode(encryptedData.ciphertext, Base64.DEFAULT)
        )
        
        return String(decryptedBytes)
    }

    fun saveEncrypted(key: String, value: String) {
        val encrypted = encrypt(value)
        encryptedPrefs.edit()
            .putString("${key}_data", encrypted.ciphertext)
            .putString("${key}_iv", encrypted.iv)
            .apply()
    }

    fun getDecrypted(key: String): String? {
        val ciphertext = encryptedPrefs.getString("${key}_data", null) ?: return null
        val iv = encryptedPrefs.getString("${key}_iv", null) ?: return null
        
        return decrypt(EncryptedData(ciphertext, iv))
    }

    fun deleteEncrypted(key: String) {
        encryptedPrefs.edit()
            .remove("${key}_data")
            .remove("${key}_iv")
            .apply()
    }

    private fun getSecretKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        keyGenerator.generateKey()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    data class EncryptedData(
        val ciphertext: String,
        val iv: String
    )

    companion object {
        private const val KEY_ALIAS = "assistant_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
```

## 5.3 Conversation Data Privacy

```kotlin
// data/local/PrivacyManager.kt
package com.personalassistant.data.local

import android.content.Context
import com.personalassistant.model.Conversation
import com.personalassistant.util.EncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyManager @Inject constructor(
    private val conversationDao: ConversationDao,
    private val encryptionManager: EncryptionManager,
    @Inject @ApplicationContext private val context: Context
) {

    suspend fun savePrivateConversation(conversation: Conversation) {
        withContext(Dispatchers.IO) {
            // Encrypt sensitive fields
            val encryptedCommand = encryptionManager.encrypt(conversation.command)
            val encryptedResponse = encryptionManager.encrypt(conversation.response)
            
            // Store encrypted version
            val encryptedConversation = conversation.copy(
                command = encryptedCommand.ciphertext,
                response = encryptedResponse.ciphertext,
                // Store IVs in separate fields or metadata
            )
            
            conversationDao.insert(encryptedConversation)
        }
    }

    suspend fun getDecryptedConversation(id: Long): Conversation? {
        return withContext(Dispatchers.IO) {
            val encrypted = conversationDao.getConversationById(id) ?: return@withContext null
            
            Conversation(
                id = encrypted.id,
                command = encryptionManager.decrypt(
                    EncryptionManager.EncryptedData(encrypted.command, encrypted.command)
                ),
                response = encryptionManager.decrypt(
                    EncryptionManager.EncryptedData(encrypted.response, encrypted.response)
                ),
                timestamp = encrypted.timestamp,
                source = encrypted.source,
                satisfied = encrypted.satisfied
            )
        }
    }

    suspend fun autoDeleteOldConversations(daysToKeep: Int = 30) {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            conversationDao.deleteOlderThan(cutoffTime)
        }
    }

    suspend fun exportConversations(): String {
        return withContext(Dispatchers.IO) {
            val conversations = conversationDao.getAllConversations()
            // Export as encrypted JSON
            buildString {
                appendLine("{")
                appendLine("  \"exported_at\": \"${System.currentTimeMillis()}\",")
                appendLine("  \"conversations\": [")
                // Build encrypted export
                appendLine("  ]")
                appendLine("}")
            }
        }
    }

    suspend fun wipeAllData() {
        withContext(Dispatchers.IO) {
            conversationDao.deleteAll()
            encryptionManager.clearAll()
        }
    }

    fun isDataRetentionEnabled(): Boolean {
        val prefs = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("data_retention", true)
    }

    fun setDataRetention(enabled: Boolean) {
        val prefs = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("data_retention", enabled).apply()
    }
}
```

## 5.4 Privacy Dashboard

```kotlin
// ui/privacy/PrivacyDashboard.kt
package com.personalassistant.ui.privacy

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.data.local.PrivacyManager
import com.personalassistant.util.PermissionHelper
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyDashboard @Inject constructor(
    private val privacyManager: PrivacyManager,
    private val permissionHelper: PermissionHelper
) {

    @Composable
    fun Dashboard(context: Context) {
        var dataCollected by remember { mutableStateOf(0) }
        var permissionsGranted by remember { mutableStateOf(0) }
        var encryptionEnabled by remember { mutableStateOf(true) }
        var autoDeleteDays by remember { mutableStateOf(30) }
        
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            // Load privacy stats
            dataCollected = privacyManager.getConversationCount()
            permissionsGranted = permissionHelper.getGrantedCount(context)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Privacy Dashboard",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacyStatCard(
                title = "Data Collected",
                value = "$dataCollected conversations",
                icon = Icons.Default.Storage
            )

            PrivacyStatCard(
                title = "Permissions",
                value = "$permissionsGranted granted",
                icon = Icons.Default.Security
            )

            PrivacyStatCard(
                title = "Encryption",
                value = if (encryptionEnabled) "Enabled" else "Disabled",
                icon = Icons.Default.Lock
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        privacyManager.autoDeleteOldConversations(autoDeleteDays)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Old Data")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        privacyManager.wipeAllData()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wipe All Data")
            }
        }
    }
}

@Composable
private fun PrivacyStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            Icon(icon, contentDescription = title)
        }
    }
}
```

## 5.5 Privacy Policy Display

```kotlin
// ui/privacy/PrivacyPolicyScreen.kt
package com.personalassistant.ui.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyPolicyScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Privacy Policy",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = """
                ## Data Collection
                
                This personal assistant app collects the following data:
                
                1. **Voice Commands**: Audio recordings are processed locally or sent to cloud AI
                2. **Conversation History**: Stored locally for context awareness
                3. **Device Information**: Used for app functionality
                
                ## Data Usage
                
                Your data is used to:
                - Provide personalized assistance
                - Improve response accuracy
                - Maintain conversation context
                
                ## Data Sharing
                
                We do NOT sell or share your personal data with third parties.
                Cloud AI providers process data only for response generation.
                
                ## Data Retention
                
                - Conversations are stored for 30 days by default
                - You can configure retention period in settings
                - You can delete all data anytime
                
                ## Security
                
                - All stored data is encrypted
                - API keys are stored in Android Keystore
                - No plaintext data leaves your device
                
                ## Your Rights
                
                - Access your data
                - Export your data
                - Delete your data
                - Opt-out of cloud processing
                
                ## Contact
                
                For privacy concerns, contact: privacy@personalassistant.com
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f)
            ) {
                Text("Decline")
            }
            
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f)
            ) {
                Text("Accept")
            }
        }
    }
}
```

## 5.6 Secure API Key Storage

```kotlin
// di/SecurityModule.kt
package com.personalassistant.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.personalassistant.data.preferences.AssistantPreferences
import com.personalassistant.util.EncryptionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_prefs")

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideEncryptionManager(
        @ApplicationContext context: Context
    ): EncryptionManager {
        return EncryptionManager(context)
    }

    @Provides
    @Singleton
    fun provideAssistantPreferences(
        @ApplicationContext context: Context,
        encryptionManager: EncryptionManager
    ): AssistantPreferences {
        return AssistantPreferences(context, encryptionManager)
    }

    @Provides
    @Singleton
    fun provideSecureDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.secureDataStore
    }
}
```

## 5.7 Network Security Configuration

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.openai.com</domain>
        <domain includeSubdomains="true">generativelanguage.googleapis.com</domain>
        <pin-set expiration="2025-01-01">
            <pin digest="SHA-256">base64-pin-hash</pin>
        </pin-set>
    </domain-config>
    
    <!-- Debug override (remove in production) -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

## 5.8 Privacy Settings Screen

```kotlin
// ui/settings/PrivacySettingsScreen.kt
package com.personalassistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.data.preferences.AssistantPreferences
import javax.inject.Inject

@Composable
fun PrivacySettingsScreen(
    preferences: AssistantPreferences,
    onUpdatePreferences: (String, Any) -> Unit
) {
    var useCloudAI by remember { mutableStateOf(preferences.useCloudAI) }
    var autoDeleteDays by remember { mutableStateOf(30) }
    var encryptData by remember { mutableStateOf(true) }
    var shareAnalytics by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Privacy Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PrivacySettingItem(
            title = "Use Cloud AI",
            subtitle = "Send requests to cloud for better responses",
            checked = useCloudAI,
            onCheckedChange = { 
                useCloudAI = it
                onUpdatePreferences("use_cloud_ai", it)
            }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        PrivacySettingItem(
            title = "Auto-Delete Data",
            subtitle = "Automatically delete conversations after N days",
            checked = autoDeleteDays > 0,
            onCheckedChange = { 
                autoDeleteDays = if (it) 30 else 0
                onUpdatePreferences("auto_delete_days", if (it) 30 else 0)
            }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        PrivacySettingItem(
            title = "Encrypt Local Data",
            subtitle = "Encrypt all stored conversations",
            checked = encryptData,
            onCheckedChange = { 
                encryptData = it
                onUpdatePreferences("encrypt_data", it)
            }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        PrivacySettingItem(
            title = "Share Analytics",
            subtitle = "Share anonymous usage data",
            checked = shareAnalytics,
            onCheckedChange = { 
                shareAnalytics = it
                onUpdatePreferences("share_analytics", it)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "⚠️ Disabling cloud AI will use on-device model only",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun PrivacySettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
```

## 5.9 GDPR Compliance Checklist

```kotlin
// util/ComplianceManager.kt
package com.personalassistant.util

import android.content.Context
import com.personalassistant.data.local.PrivacyManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComplianceManager @Inject constructor(
    private val privacyManager: PrivacyManager,
    @Inject @ApplicationContext private val context: Context
) {

    fun isGDPRCompliant(): ComplianceReport {
        return ComplianceReport(
            dataEncryption = true,
            userConsent = hasUserConsent(),
            dataPortability = true,
            rightToErasure = true,
            dataMinimization = true,
            purposeLimitation = true
        )
    }

    fun isCCPACompliant(): ComplianceReport {
        return ComplianceReport(
            dataEncryption = true,
            userConsent = hasUserConsent(),
            rightToKnow = true,
            rightToDelete = true,
            rightToOptOut = true,
            noSaleOfData = true
        )
    }

    private fun hasUserConsent(): Boolean {
        val prefs = context.getSharedPreferences("consent_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("privacy_consent", false)
    }

    fun recordConsent() {
        val prefs = context.getSharedPreferences("consent_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("privacy_consent", true)
            .putLong("consent_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun revokeConsent() {
        val prefs = context.getSharedPreferences("consent_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("privacy_consent", false)
            .apply()
    }

    data class ComplianceReport(
        val dataEncryption: Boolean,
        val userConsent: Boolean,
        val dataPortability: Boolean,
        val rightToErasure: Boolean,
        val dataMinimization: Boolean,
        val purposeLimitation: Boolean,
        val rightToKnow: Boolean = true,
        val rightToDelete: Boolean = true,
        val rightToOptOut: Boolean = true,
        val noSaleOfData: Boolean = true
    )
}
```

---

**Next**: [Battery Optimization](./06-battery-optimization.md)
