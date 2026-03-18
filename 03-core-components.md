# 3. Core Components Implementation

## 3.1 Foreground Service (AssistantService)

The backbone of silent background operation.

```kotlin
// AssistantService.kt
package com.personalassistant.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personalassistant.AssistantApplication
import com.personalassistant.R
import com.personalassistant.model.AssistantState
import com.personalassistant.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class AssistantService : Service() {

    @Inject
    lateinit var commandHandler: CommandHandler
    
    @Inject
    lateinit var aiProvider: AIProvider

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState

    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AssistantService created")
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForeground()
            ACTION_STOP -> stopSelf()
            ACTION_PROCESS_COMMAND -> {
                val command = intent.getStringExtra(EXTRA_COMMAND)
                command?.let { processCommand(it) }
            }
            ACTION_TOGGLE_LISTENING -> toggleListening()
        }
        return START_STICKY
    }

    private fun startForeground() {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
        
        _assistantState.value = AssistantState.ACTIVE
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, com.personalassistant.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AssistantApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_assistant)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun processCommand(command: String) {
        serviceScope.launch {
            try {
                _assistantState.value = AssistantState.PROCESSING
                
                val response = aiProvider.generateResponse(command)
                
                withContext(Dispatchers.Main) {
                    // Update UI or trigger action
                    commandHandler.handleResponse(response)
                }
                
                _assistantState.value = AssistantState.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                _assistantState.value = AssistantState.ERROR
            }
        }
    }

    private fun toggleListening() {
        isListening = !isListening
        if (isListening) {
            startListening()
        } else {
            stopListening()
        }
    }

    private fun startListening() {
        // Initialize speech recognition
        // This would integrate with SpeechRecognizer
        Log.d(TAG, "Started listening")
    }

    private fun stopListening() {
        // Clean up speech recognition
        Log.d(TAG, "Stopped listening")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "AssistantService destroyed")
    }

    companion object {
        private const val TAG = "AssistantService"
        
        const val ACTION_START = "com.personalassistant.START_SERVICE"
        const val ACTION_STOP = "com.personalassistant.STOP_SERVICE"
        const val ACTION_PROCESS_COMMAND = "com.personalassistant.PROCESS_COMMAND"
        const val ACTION_TOGGLE_LISTENING = "com.personalassistant.TOGGLE_LISTENING"
        
        const val EXTRA_COMMAND = "extra_command"

        fun start(context: Context) {
            Intent(context, AssistantService::class.java).apply {
                action = ACTION_START
                context.startForegroundService(this)
            }
        }

        fun stop(context: Context) {
            Intent(context, AssistantService::class.java).apply {
                action = ACTION_STOP
                context.startService(this)
            }
        }

        fun processCommand(context: Context, command: String) {
            Intent(context, AssistantService::class.java).apply {
                action = ACTION_PROCESS_COMMAND
                putExtra(EXTRA_COMMAND, command)
                context.startService(this)
            }
        }
    }
}
```

## 3.2 Broadcast Receivers

### Boot Receiver
```kotlin
// BootReceiver.kt
package com.personalassistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalassistant.data.preferences.AssistantPreferences
import com.personalassistant.services.AssistantService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: AssistantPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_QUICKBOOT_POWERON) {
            
            if (preferences.isAutoStartEnabled) {
                AssistantService.start(context)
            }
        }
    }
}
```

### Connectivity Receiver
```kotlin
// ConnectivityReceiver.kt
package com.personalassistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import com.personalassistant.ai.AIProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConnectivityReceiver : BroadcastReceiver() {

    @Inject
    lateinit var aiProvider: AIProvider

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val isConnected = isNetworkAvailable(context)
            
            scope.launch {
                if (isConnected) {
                    // Sync pending requests
                    // Switch to cloud AI if available
                } else {
                    // Switch to offline mode
                    // Queue requests for later
                }
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork?.let { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected ?: false
        }
    }
}
```

## 3.3 Command Handler

```kotlin
// CommandHandler.kt
package com.personalassistant.services

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import com.personalassistant.model.Command
import com.personalassistant.model.CommandType
import com.personalassistant.model.AssistantResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun handleResponse(response: AssistantResponse) {
        when (response.type) {
            CommandType.INFO -> speakResponse(response.message)
            CommandType.ACTION -> executeAction(response.action)
            CommandType.QUESTION -> fetchAndSpeak(response.query)
            CommandType.SETTING -> updateSetting(response.setting)
        }
    }

    private fun speakResponse(message: String) {
        // Trigger TTS service
        TextToSpeechService.speak(context, message)
    }

    private fun executeAction(action: String?) {
        when (action) {
            "open_camera" -> openCamera()
            "open_contacts" -> openContacts()
            "set_alarm" -> openAlarmApp()
            "play_music" -> openMusicApp()
            "take_note" -> openNotesApp()
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    private fun openCamera() {
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }

    private fun openContacts() {
        Intent(Intent.ACTION_VIEW).apply {
            data = ContactsContract.Contacts.CONTENT_URI
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }

    private fun openAlarmApp() {
        Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }

    private fun openMusicApp() {
        Intent(android.provider.MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }

    private fun openNotesApp() {
        Intent(Intent.ACTION_INSERT).apply {
            type = "vnd.android.cursor.item/note"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }

    companion object {
        private const val TAG = "CommandHandler"
    }
}
```

## 3.4 Speech Recognition Service

```kotlin
// SpeechRecognitionService.kt
package com.personalassistant.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRecognitionService @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(): Flow<SpeechRecognitionResult> = callbackFlow {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechRecognitionResult.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(SpeechRecognitionResult.Started)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }

            override fun onEndOfSpeech() {
                trySend(SpeechRecognitionResult.Ended)
            }

            override fun onError(error: Int) {
                trySend(SpeechRecognitionResult.Error(getErrorString(error)))
                close()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                
                matches?.firstOrNull()?.let { text ->
                    trySend(SpeechRecognitionResult.Success(text, confidence?.firstOrNull() ?: 0f))
                }
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { text ->
                    trySend(SpeechRecognitionResult.Partial(text))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Custom events
            }
        })

        speechRecognizer?.startListening(recognizerIntent)

        awaitClose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    fun cancelListening() {
        speechRecognizer?.cancel()
    }

    private fun getErrorString(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Unknown error"
        }
    }
}

sealed class SpeechRecognitionResult {
    object Ready : SpeechRecognitionResult()
    object Started : SpeechRecognitionResult()
    object Ended : SpeechRecognitionResult()
    data class Partial(val text: String) : SpeechRecognitionResult()
    data class Success(val text: String, val confidence: Float) : SpeechRecognitionResult()
    data class Error(val message: String) : SpeechRecognitionResult()
}
```

## 3.5 Text-to-Speech Service

```kotlin
// TextToSpeechService.kt
package com.personalassistant.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechService @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    fun init() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported")
                isInitialized = false
            } else {
                textToSpeech?.setPitch(1.0f)
                textToSpeech?.setSpeechRate(1.0f)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "Speaking started")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "Speaking done")
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Speaking error")
                    }
                })
                isInitialized = true
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
            isInitialized = false
        }
    }

    fun speak(text: String) {
        if (!isInitialized) {
            init()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }

    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }

    companion object {
        private const val TAG = "TextToSpeechService"

        fun speak(context: Context, text: String) {
            val tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US)
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }
}
```

## 3.6 Data Models

```kotlin
// models/Conversation.kt
package com.personalassistant.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val command: String,
    val response: String,
    val timestamp: Date = Date(),
    val source: CommandSource = CommandSource.VOICE,
    val satisfied: Boolean = false
)

enum class CommandSource {
    VOICE,
    TEXT,
    GESTURE,
    SYSTEM_EVENT
}

// models/Command.kt
package com.personalassistant.model

data class Command(
    val text: String,
    val source: CommandSource,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f
)

enum class CommandType {
    INFO,           // Provide information
    ACTION,         // Perform action
    QUESTION,       // Answer question
    SETTING,        // Change setting
    NAVIGATION,     // Navigate somewhere
    REMINDER,       // Set reminder
    UNKNOWN         // Unclassified
}

// models/AssistantResponse.kt
package com.personalassistant.model

data class AssistantResponse(
    val message: String,
    val type: CommandType,
    val action: String? = null,
    val query: String? = null,
    val setting: SettingChange? = null,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class SettingChange(
    val settingName: String,
    val newValue: String
)

// models/AssistantState.kt
package com.personalassistant.model

sealed class AssistantState {
    object IDLE : AssistantState()
    object LISTENING : AssistantState()
    object PROCESSING : AssistantState()
    object SPEAKING : AssistantState()
    object ACTIVE : AssistantState()
    object ERROR : AssistantState()
}
```

## 3.7 Room Database

```kotlin
// data/local/AssistantDatabase.kt
package com.personalassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.personalassistant.model.Conversation

@Database(
    entities = [Conversation::class],
    version = 1,
    exportSchema = false
)
abstract class AssistantDatabase : RoomDatabase() {
    
    abstract fun conversationDao(): ConversationDao

    companion object {
        const val DATABASE_NAME = "assistant_db"
    }
}

// data/local/ConversationDao.kt
package com.personalassistant.data.local

import androidx.room.*
import com.personalassistant.model.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getConversationsSince(startTime: Long): List<Conversation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<Conversation>)

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
```

## 3.8 Repository Implementation

```kotlin
// data/repository/AssistantRepository.kt
package com.personalassistant.data.repository

import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.Conversation
import kotlinx.coroutines.flow.Flow

interface AssistantRepository {
    suspend fun processCommand(command: String): AssistantResponse
    suspend fun saveConversation(conversation: Conversation)
    fun getConversationHistory(): Flow<List<Conversation>>
    suspend fun clearHistory()
}

// data/repository/AssistantRepositoryImpl.kt
package com.personalassistant.data.repository

import com.personalassistant.ai.AIProvider
import com.personalassistant.data.local.ConversationDao
import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.Conversation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val aiProvider: AIProvider
) : AssistantRepository {

    override suspend fun processCommand(command: String): AssistantResponse {
        return aiProvider.generateResponse(command)
    }

    override suspend fun saveConversation(conversation: Conversation) {
        conversationDao.insert(conversation)
    }

    override fun getConversationHistory(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
    }

    override suspend fun clearHistory() {
        conversationDao.deleteAll()
    }
}
```

---

**Next**: [AI Integration](./04-ai-integration.md)
