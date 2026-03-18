# 4. AI Integration (Cloud & On-Device)

## 4.1 AI Provider Interface

```kotlin
// ai/AIProvider.kt
package com.personalassistant.ai

import com.personalassistant.model.AssistantResponse

interface AIProvider {
    suspend fun generateResponse(prompt: String): AssistantResponse
    suspend fun generateWithContext(prompt: String, context: ConversationContext): AssistantResponse
    fun supportsOffline(): Boolean
    fun getProviderName(): String
    fun getMaxTokens(): Int
}

// Available Providers:
// - OpenAIProvider (GPT models)
// - GeminiProvider (Google models)
// - OpenRouterProvider (100+ models via unified API) ⭐ Recommended
// - OnDeviceAIProvider (TFLite, offline)

data class ConversationContext(
    val recentConversations: List<String>,
    val userPreferences: UserPreferences,
    val currentTime: Long,
    val location: Location? = null
)

data class UserPreferences(
    val name: String,
    val language: String,
    val tone: Tone = Tone.FRIENDLY
)

enum class Tone {
    FORMAL,
    FRIENDLY,
    CASUAL,
    PROFESSIONAL
}

data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)
```

## 4.2 Cloud AI Provider (OpenAI)

```kotlin
// ai/CloudAIProvider.kt
package com.personalassistant.ai

import com.personalassistant.data.remote.OpenAIApi
import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.CommandType
import com.personalassistant.data.preferences.AssistantPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAIProvider @Inject constructor(
    private val openAIApi: OpenAIApi,
    private val preferences: AssistantPreferences
) : AIProvider {

    private val systemPrompt = """
        You are a helpful personal assistant running on an Android device.
        Be concise, helpful, and context-aware.
        You can help with:
        - Answering questions
        - Setting reminders and alarms
        - Controlling device features
        - Providing information
        - General conversation
        
        Keep responses brief (under 100 words unless asked for more).
        If a response requires an action, indicate it clearly.
    """.trimIndent()

    override suspend fun generateResponse(prompt: String): AssistantResponse {
        return withContext(Dispatchers.IO) {
            try {
                val request = OpenAIRequest(
                    model = preferences.aiModel ?: "gpt-3.5-turbo",
                    messages = listOf(
                        Message(role = "system", content = systemPrompt),
                        Message(role = "user", content = prompt)
                    ),
                    maxTokens = 150,
                    temperature = 0.7
                )

                val response = openAIApi.chatCompletions(request)
                
                val content = response.choices.firstOrNull()?.message?.content ?: "I couldn't process that."
                
                AssistantResponse(
                    message = content,
                    type = classifyResponseType(content, prompt),
                    confidence = response.usage?.totalTokens?.let { 0.9f } ?: 0.7f
                )
            } catch (e: Exception) {
                AssistantResponse(
                    message = "I'm having trouble connecting. Please check your internet connection.",
                    type = CommandType.INFO,
                    confidence = 0.5f
                )
            }
        }
    }

    override suspend fun generateWithContext(
        prompt: String,
        context: ConversationContext
    ): AssistantResponse {
        return withContext(Dispatchers.IO) {
            try {
                val contextMessages = context.recentConversations.take(5).map {
                    Message(role = "assistant", content = it)
                }

                val messages = listOf(
                    Message(role = "system", content = systemPrompt),
                    *contextMessages.toTypedArray(),
                    Message(role = "user", content = prompt)
                )

                val request = OpenAIRequest(
                    model = preferences.aiModel ?: "gpt-3.5-turbo",
                    messages = messages,
                    maxTokens = 150,
                    temperature = 0.7
                )

                val response = openAIApi.chatCompletions(request)
                
                val content = response.choices.firstOrNull()?.message?.content ?: "I couldn't process that."
                
                AssistantResponse(
                    message = content,
                    type = classifyResponseType(content, prompt),
                    confidence = 0.85f
                )
            } catch (e: Exception) {
                AssistantResponse(
                    message = "I'm having trouble connecting. Please check your internet connection.",
                    type = CommandType.INFO,
                    confidence = 0.5f
                )
            }
        }
    }

    private fun classifyResponseType(content: String, prompt: String): CommandType {
        return when {
            content.contains("I'll open") || content.contains("Opening") -> CommandType.ACTION
            content.contains("reminder") || content.contains("alarm") -> CommandType.SETTING
            prompt.contains("?") || content.contains("is a") -> CommandType.QUESTION
            else -> CommandType.INFO
        }
    }

    override fun supportsOffline(): Boolean = false
    
    override fun getProviderName(): String = "OpenAI"
    
    override fun getMaxTokens(): Int = 4096
}

// data/remote/Models.kt
package com.personalassistant.data.remote

import com.google.gson.annotations.SerializedName

data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    val maxTokens: Int,
    val temperature: Float = 0.7
)

data class Message(
    val role: String,
    val content: String
)

data class OpenAIResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val message: Message,
    val finishReason: String
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// data/remote/OpenAIApi.kt
package com.personalassistant.data.remote

import retrofit2.http.*

interface OpenAIApi {
    
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    suspend fun chatCompletions(
        @Body request: OpenAIRequest
    ): OpenAIResponse
}
```

## 4.3 Cloud AI Provider (Google Gemini)

```kotlin
// ai/GeminiProvider.kt
package com.personalassistant.ai

import com.personalassistant.data.remote.GeminiApi
import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.CommandType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiProvider @Inject constructor(
    private val geminiApi: GeminiApi
) : AIProvider {

    private val systemPrompt = """
        You are a helpful personal assistant on an Android device.
        Be concise, context-aware, and actionable.
    """.trimIndent()

    override suspend fun generateResponse(prompt: String): AssistantResponse {
        return withContext(Dispatchers.IO) {
            try {
                val request = GeminiRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = systemPrompt))),
                        Content(parts = listOf(Part(text = prompt)))
                    )
                )

                val response = geminiApi.generateContent(request)
                
                val content = response.candidates.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text ?: "I couldn't process that."
                
                AssistantResponse(
                    message = content,
                    type = classifyResponseType(content, prompt),
                    confidence = 0.85f
                )
            } catch (e: Exception) {
                AssistantResponse(
                    message = "I'm having trouble connecting. Please check your internet connection.",
                    type = CommandType.INFO,
                    confidence = 0.5f
                )
            }
        }
    }

    override suspend fun generateWithContext(
        prompt: String,
        context: ConversationContext
    ): AssistantResponse {
        // Similar implementation with context
        return generateResponse(prompt)
    }

    private fun classifyResponseType(content: String, prompt: String): CommandType {
        return when {
            content.contains("I'll open") || content.contains("Opening") -> CommandType.ACTION
            content.contains("reminder") || content.contains("alarm") -> CommandType.SETTING
            prompt.contains("?") -> CommandType.QUESTION
            else -> CommandType.INFO
        }
    }

    override fun supportsOffline(): Boolean = false
    
    override fun getProviderName(): String = "Google Gemini"
    
    override fun getMaxTokens(): Int = 2048
}

// data/remote/GeminiModels.kt
package com.personalassistant.data.remote

data class GeminiRequest(
    val contents: List<Content>
)

data class Content(
    val parts: List<Part>,
    val role: String? = null
)

data class Part(
    val text: String
)

data class GeminiResponse(
    val candidates: List<Candidate>
)

data class Candidate(
    val content: Content,
    val finishReason: String?
)

// data/remote/GeminiApi.kt
package com.personalassistant.data.remote

import retrofit2.http.*

interface GeminiApi {
    
    @POST("models/gemini-pro:generateContent")
    @Query("key") apiKey: String
    suspend fun generateContent(
        @Body request: GeminiRequest
    ): GeminiResponse
}
```

## 4.4 On-Device AI Provider (TensorFlow Lite)

```kotlin
// ai/OnDeviceAIProvider.kt
package com.personalassistant.ai

import android.content.Context
import android.util.Log
import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.CommandType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceAIProvider @Inject constructor(
    private val context: Context
) : AIProvider {

    private var interpreter: Interpreter? = null
    private val vocabSize = 30522
    private val seqLength = 128
    private val TAG = "OnDeviceAIProvider"

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelFile = "assistant_model.tflite"
            val buffer = loadModelFile(modelFile)
            interpreter = Interpreter(buffer, Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            })
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    private fun loadModelFile(modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override suspend fun generateResponse(prompt: String): AssistantResponse {
        return try {
            // Tokenize input
            val tokens = tokenize(prompt)
            
            // Run inference
            val output = runInference(tokens)
            
            // Decode output to response
            val responseText = decodeOutput(output)
            
            AssistantResponse(
                message = responseText,
                type = classifyResponseType(responseText, prompt),
                confidence = 0.75f,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            AssistantResponse(
                message = "I couldn't process that locally. Please try again.",
                type = CommandType.INFO,
                confidence = 0.5f
            )
        }
    }

    override suspend fun generateWithContext(
        prompt: String,
        context: ConversationContext
    ): AssistantResponse {
        // For on-device, we'll use simpler context handling
        val contextPrompt = buildContextPrompt(prompt, context)
        return generateResponse(contextPrompt)
    }

    private fun tokenize(text: String): Array<Long> {
        // Simplified tokenization - in production, use proper tokenizer
        // This would integrate with BERT tokenizer or similar
        val tokens = Array(seqLength) { 0L }
        
        // Basic word splitting (placeholder - use real tokenizer)
        val words = text.split(" ").take(seqLength)
        words.forEachIndexed { index, word ->
            tokens[index] = word.hashCode().toLong() % vocabSize
        }
        
        return tokens
    }

    private fun runInference(tokens: Array<Long>): Array<FloatArray> {
        val input = Array(1) { tokens }
        val output = Array(1) { Array(seqLength) { FloatArray(vocabSize) } }
        
        interpreter?.run(input, output)
        
        return output
    }

    private fun decodeOutput(output: Array<FloatArray>): String {
        // Simplified decoding - in production, use proper decoder
        // This would use vocabulary lookup and beam search
        
        val responses = listOf(
            "I understand. How can I help?",
            "Sure, I can do that.",
            "Let me help you with that.",
            "I'm here to assist.",
            "What else would you like me to do?"
        )
        
        return responses[output[0][0].toInt() % responses.size]
    }

    private fun buildContextPrompt(prompt: String, context: ConversationContext): String {
        val recentContext = context.recentConversations.takeLast(2).joinToString(". ")
        return "$recentContext. $prompt"
    }

    private fun classifyResponseType(content: String, prompt: String): CommandType {
        return when {
            content.contains("open") || content.contains("start") -> CommandType.ACTION
            content.contains("reminder") || content.contains("set") -> CommandType.SETTING
            prompt.contains("?") -> CommandType.QUESTION
            else -> CommandType.INFO
        }
    }

    override fun supportsOffline(): Boolean = true
    
    override fun getProviderName(): String = "On-Device"
    
    override fun getMaxTokens(): Int = 128

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
```

## 4.5 Hybrid AI Manager

```kotlin
// ai/AIManager.kt
package com.personalassistant.ai

import android.content.Context
import android.net.ConnectivityManager
import com.personalassistant.data.preferences.AssistantPreferences
import com.personalassistant.model.AssistantResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AssistantPreferences,
    private val openAIProvider: OpenAIProvider,
    private val geminiProvider: GeminiProvider,
    private val onDeviceProvider: OnDeviceAIProvider
) {

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _currentProvider = MutableStateFlow<AIProvider>(onDeviceProvider)
    val currentProvider: StateFlow<AIProvider> = _currentProvider

    private val _isOnline = MutableStateFlow(isNetworkAvailable())
    val isOnline: StateFlow<Boolean> = _isOnline

    init {
        updateProvider()
    }

    fun updateProvider() {
        _isOnline.value = isNetworkAvailable()
        
        _currentProvider.value = when {
            !preferences.useCloudAI -> onDeviceProvider
            _isOnline.value && preferences.aiProvider == "gemini" -> geminiProvider
            _isOnline.value -> openAIProvider
            else -> onDeviceProvider
        }
    }

    suspend fun generateResponse(prompt: String): AssistantResponse {
        return _currentProvider.value.generateResponse(prompt)
    }

    suspend fun generateWithContext(
        prompt: String,
        context: ConversationContext
    ): AssistantResponse {
        return _currentProvider.value.generateWithContext(prompt, context)
    }

    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(
            android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) ?: false
    }

    fun switchProvider(providerName: String) {
        preferences.aiProvider = providerName
        updateProvider()
    }

    fun setUseCloudAI(useCloud: Boolean) {
        preferences.useCloudAI = useCloud
        updateProvider()
    }

    companion object {
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_ONDEVICE = "ondevice"
    }
}
```

## 4.6 Intent Classification Model

For on-device command understanding without cloud:

```kotlin
// ai/IntentClassifier.kt
package com.personalassistant.ai

import android.content.Context
import org.tensorflow.lite.Interpreter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentClassifier @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private var interpreter: Interpreter? = null
    private val inputSize = 300
    private val numClasses = 10 // Number of intent categories

    data class IntentResult(
        val intent: String,
        val confidence: Float,
        val slots: Map<String, String>
    )

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelFile = "intent_classifier.tflite"
            val buffer = context.assets.open(modelFile).use {
                val buffer = java.nio.ByteBuffer.allocateDirect(it.available())
                it.read(buffer)
                buffer.rewind()
                buffer
            }
            
            interpreter = Interpreter(buffer, Interpreter.Options().apply {
                setNumThreads(2)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun classify(text: String): IntentResult {
        // Preprocess text
        val input = preprocessText(text)
        
        // Run inference
        val output = Array(1) { FloatArray(numClasses) }
        interpreter?.run(input, output)
        
        // Find best class
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: 0
        val confidence = output[0][maxIndex]
        
        // Extract slots (simplified)
        val slots = extractSlots(text)
        
        return IntentResult(
            intent = INTENT_CLASSES[maxIndex] ?: "unknown",
            confidence = confidence,
            slots = slots
        )
    }

    private fun preprocessText(text: String): FloatArray {
        // Convert text to feature vector
        // In production, use proper embedding
        return FloatArray(inputSize) { text.hashCode().toFloat() }
    }

    private fun extractSlots(text: String): Map<String, String> {
        val slots = mutableMapOf<String, String>()
        
        // Extract time
        val timeRegex = "(\\d{1,2}:\\d{2}|\\d+ (am|pm)|noon|midnight)".toRegex(RegexOption.IGNORE_CASE)
        timeRegex.find(text)?.let { slots["time"] = it.value }
        
        // Extract date
        val dateRegex = "(today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday)".toRegex(RegexOption.IGNORE_CASE)
        dateRegex.find(text)?.let { slots["date"] = it.value }
        
        // Extract contact name
        val contactRegex = "(call|message|email)\\s+(\\w+)".toRegex(RegexOption.IGNORE_CASE)
        contactRegex.find(text)?.let { slots["contact"] = it.groupValues[2] }
        
        return slots
    }

    fun close() {
        interpreter?.close()
    }

    companion object {
        val INTENT_CLASSES = listOf(
            "set_alarm",
            "set_reminder",
            "make_call",
            "send_message",
            "open_app",
            "search_web",
            "get_weather",
            "play_music",
            "set_timer",
            "unknown"
        )
    }
}
```

## 4.7 Response Cache

```kotlin
// ai/ResponseCache.kt
package com.personalassistant.ai

import android.util.LruCache
import com.personalassistant.model.AssistantResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseCache @Inject constructor() {

    private val cache = LruCache<String, AssistantResponse>(100)

    fun get(key: String): AssistantResponse? {
        return cache.get(key)
    }

    fun put(key: String, response: AssistantResponse) {
        cache.put(key, response)
    }

    fun contains(key: String): Boolean {
        return cache.get(key) != null
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }

    fun normalizeKey(text: String): String {
        return text.lowercase().trim()
    }
}
```

## 4.8 Dependency Injection Setup

```kotlin
// di/AssistantModule.kt
package com.personalassistant.di

import android.content.Context
import com.personalassistant.ai.*
import com.personalassistant.data.local.AssistantDatabase
import com.personalassistant.data.local.ConversationDao
import com.personalassistant.data.preferences.AssistantPreferences
import com.personalassistant.data.remote.GeminiApi
import com.personalassistant.data.remote.OpenAIApi
import com.personalassistant.data.repository.AssistantRepository
import com.personalassistant.data.repository.AssistantRepositoryImpl
import com.personalassistant.services.CommandHandler
import com.personalassistant.services.SpeechRecognitionService
import com.personalassistant.services.TextToSpeechService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {

    @Provides
    @Singleton
    fun provideAssistantDatabase(
        @ApplicationContext context: Context
    ): AssistantDatabase {
        return androidx.room.Room.databaseBuilder(
            context,
            AssistantDatabase::class.java,
            AssistantDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: AssistantDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideAssistantPreferences(
        @ApplicationContext context: Context
    ): AssistantPreferences {
        return AssistantPreferences(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIApi(retrofit: Retrofit): OpenAIApi {
        return retrofit.create(OpenAIApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeminiRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGeminiApi(retrofit: Retrofit): GeminiApi {
        return retrofit.create(GeminiApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenAIProvider(
        openAIApi: OpenAIApi,
        preferences: AssistantPreferences
    ): OpenAIProvider {
        return OpenAIProvider(openAIApi, preferences)
    }

    @Provides
    @Singleton
    fun provideGeminiProvider(geminiApi: GeminiApi): GeminiProvider {
        return GeminiProvider(geminiApi)
    }

    @Provides
    @Singleton
    fun provideOnDeviceProvider(
        @ApplicationContext context: Context
    ): OnDeviceAIProvider {
        return OnDeviceAIProvider(context)
    }

    @Provides
    @Singleton
    fun provideAIManager(
        @ApplicationContext context: Context,
        preferences: AssistantPreferences,
        openAIProvider: OpenAIProvider,
        geminiProvider: GeminiProvider,
        onDeviceProvider: OnDeviceAIProvider
    ): AIManager {
        return AIManager(context, preferences, openAIProvider, geminiProvider, onDeviceProvider)
    }

    @Provides
    @Singleton
    fun provideAssistantRepository(
        conversationDao: ConversationDao,
        aiProvider: AIProvider
    ): AssistantRepository {
        return AssistantRepositoryImpl(conversationDao, aiProvider)
    }

    @Provides
    @Singleton
    fun provideCommandHandler(
        @ApplicationContext context: Context
    ): CommandHandler {
        return CommandHandler(context)
    }

    @Provides
    @Singleton
    fun provideSpeechRecognitionService(
        @ApplicationContext context: Context
    ): SpeechRecognitionService {
        return SpeechRecognitionService(context)
    }

    @Provides
    @Singleton
    fun provideTextToSpeechService(
        @ApplicationContext context: Context
    ): TextToSpeechService {
        return TextToSpeechService(context)
    }

    @Provides
    @Singleton
    fun provideIntentClassifier(
        @ApplicationContext context: Context
    ): IntentClassifier {
        return IntentClassifier(context)
    }

    @Provides
    @Singleton
    fun provideResponseCache(): ResponseCache {
        return ResponseCache()
    }
}
```

## 4.9 API Key Management

```kotlin
// data/preferences/AssistantPreferences.kt
package com.personalassistant.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "assistant_prefs")

class AssistantPreferences(private val context: Context) {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore")
    
    init {
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    var aiProvider: String
        get() = getPreference(AI_PROVIDER_KEY, "openai")
        set(value) = savePreference(AI_PROVIDER_KEY, value)

    var aiModel: String?
        get() = getPreferenceNullable(AI_MODEL_KEY)
        set(value) = savePreference(AI_MODEL_KEY, value)

    var openAIApiKey: String
        get() = getEncryptedPreference(OPENAI_API_KEY) ?: ""
        set(value) = saveEncryptedPreference(OPENAI_API_KEY, value)

    var geminiApiKey: String
        get() = getEncryptedPreference(GEMINI_API_KEY) ?: ""
        set(value) = saveEncryptedPreference(GEMINI_API_KEY, value)

    var useCloudAI: Boolean
        get() = getPreference(USE_CLOUD_AI_KEY, true)
        set(value) = savePreference(USE_CLOUD_AI_KEY, value)

    var isAutoStartEnabled: Boolean
        get() = getPreference(AUTO_START_KEY, false)
        set(value) = savePreference(AUTO_START_KEY, value)

    var userName: String
        get() = getEncryptedPreference(USER_NAME_KEY) ?: ""
        set(value) = saveEncryptedPreference(USER_NAME_KEY, value)

    private fun getPreference(key: Preferences.Key<String>, default: String): String {
        // Implementation using DataStore
        return default
    }

    private fun savePreference(key: Preferences.Key<String>, value: String) {
        // Implementation using DataStore
    }

    private fun getEncryptedPreference(key: String): String? {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey())
        // Decrypt and return
        return null
    }

    private fun saveEncryptedPreference(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        // Encrypt and save
    }

    private fun getSecretKey(): SecretKey {
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
                .build()
        )
        keyGenerator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "assistant_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        
        val AI_PROVIDER_KEY = stringPreferencesKey("ai_provider")
        val AI_MODEL_KEY = stringPreferencesKey("ai_model")
        val OPENAI_API_KEY = "openai_api_key"
        val GEMINI_API_KEY = "gemini_api_key"
        val USE_CLOUD_AI_KEY = stringPreferencesKey("use_cloud_ai")
        val AUTO_START_KEY = stringPreferencesKey("auto_start")
        val USER_NAME_KEY = "user_name"
    }
}
```

---

**Next**: [Privacy & Security](./05-privacy-security.md)
