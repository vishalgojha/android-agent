# 9. OpenRouter Integration

## Why OpenRouter?

OpenRouter is an API aggregator that provides:
- **Multiple Models**: Access to 100+ LLMs (GPT-4, Claude, Llama, Mistral, etc.)
- **Unified API**: Single endpoint for all models
- **Cost Optimization**: Choose models by price/performance
- **Fallback Support**: Automatic failover between models
- **No Vendor Lock-in**: Easy to switch models

## 9.1 OpenRouter API Provider

```kotlin
// ai/OpenRouterProvider.kt
package com.personalassistant.ai

import com.personalassistant.data.remote.OpenRouterApi
import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.CommandType
import com.personalassistant.data.preferences.AssistantPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterProvider @Inject constructor(
    private val openRouterApi: OpenRouterApi,
    private val preferences: AssistantPreferences
) : AIProvider {

    private val systemPrompt = """
        You are a helpful personal assistant running on an Android device.
        Be concise, helpful, and context-aware.
        
        Capabilities:
        - Answer questions
        - Set reminders and alarms
        - Control device features
        - Provide information
        - General conversation
        
        Keep responses brief (under 100 words unless asked for more).
        If a response requires an action, indicate it clearly.
        Use natural, conversational tone.
    """.trimIndent()

    override suspend fun generateResponse(prompt: String): AssistantResponse {
        return withContext(Dispatchers.IO) {
            try {
                val request = OpenRouterRequest(
                    model = preferences.openRouterModel ?: "meta-llama/llama-3-8b-instruct:free",
                    messages = listOf(
                        OpenRouterMessage(role = "system", content = systemPrompt),
                        OpenRouterMessage(role = "user", content = prompt)
                    ),
                    maxTokens = 150,
                    temperature = 0.7f
                )

                val response = openRouterApi.chatCompletions(request)
                
                val content = response.choices.firstOrNull()?.message?.content 
                    ?: "I couldn't process that."
                
                AssistantResponse(
                    message = content,
                    type = classifyResponseType(content, prompt),
                    confidence = response.usage?.totalTokens?.let { 0.9f } ?: 0.7f,
                    timestamp = System.currentTimeMillis()
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
                    OpenRouterMessage(role = "assistant", content = it)
                }

                val messages = listOf(
                    OpenRouterMessage(role = "system", content = systemPrompt),
                    *contextMessages.toTypedArray(),
                    OpenRouterMessage(role = "user", content = prompt)
                )

                val request = OpenRouterRequest(
                    model = preferences.openRouterModel ?: "meta-llama/llama-3-8b-instruct:free",
                    messages = messages,
                    maxTokens = 150,
                    temperature = 0.7f
                )

                val response = openRouterApi.chatCompletions(request)
                
                val content = response.choices.firstOrNull()?.message?.content 
                    ?: "I couldn't process that."
                
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
    
    override fun getProviderName(): String = "OpenRouter"
    
    override fun getMaxTokens(): Int = 4096
    
    fun getAvailableModels(): List<OpenRouterModel> {
        // Would fetch from API in production
        return OpenRouterModel.recommendedModels
    }
}
```

## 9.2 OpenRouter API Models

```kotlin
// data/remote/OpenRouterModels.kt
package com.personalassistant.data.remote

import com.google.gson.annotations.SerializedName

data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val maxTokens: Int,
    val temperature: Float = 0.7f,
    val stream: Boolean = false,
    val presencePenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val topP: Float? = null
)

data class OpenRouterMessage(
    val role: String,
    val content: String,
    val name: String? = null
)

data class OpenRouterResponse(
    val id: String,
    val choices: List<OpenRouterChoice>,
    val usage: OpenRouterUsage?,
    val model: String
)

data class OpenRouterChoice(
    val message: OpenRouterMessage,
    val finishReason: String,
    val index: Int
)

data class OpenRouterUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

data class OpenRouterModel(
    val id: String,
    val name: String,
    val creator: String,
    val description: String?,
    val contextLength: Int,
    val pricing: Pricing,
    val topProvider: TopProvider
)

data class Pricing(
    val prompt: Double,
    val completion: Double
)

data class TopProvider(
    val maxCompletionTokens: Int,
    val isMistral: Boolean
)

// OpenRouterApi.kt
package com.personalassistant.data.remote

import retrofit2.http.*

interface OpenRouterApi {
    
    @POST("chat/completions")
    @Headers(
        "Content-Type: application/json",
        "HTTP-Referer: https://personalassistant.app",
        "X-Title: Personal Assistant"
    )
    suspend fun chatCompletions(
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
    
    @GET("models")
    suspend fun listModels(): OpenRouterModelsResponse
}

data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel>
)
```

## 9.3 Recommended Models Configuration

```kotlin
// ai/OpenRouterModelConfig.kt
package com.personalassistant.ai

object OpenRouterModelConfig {
    
    val recommendedModels = listOf(
        OpenRouterModelInfo(
            id = "meta-llama/llama-3-8b-instruct:free",
            name = "Llama 3 8B (Free)",
            useCase = "Default - Free tier",
            contextLength = 8192,
            costPer1kTokens = 0.0
        ),
        OpenRouterModelInfo(
            id = "meta-llama/llama-3-70b-instruct",
            name = "Llama 3 70B",
            useCase = "Best balance of quality/cost",
            contextLength = 8192,
            costPer1kTokens = 0.00008
        ),
        OpenRouterModelInfo(
            id = "google/gemini-pro-1.5",
            name = "Gemini Pro 1.5",
            useCase = "Complex reasoning",
            contextLength = 128000,
            costPer1kTokens = 0.00005
        ),
        OpenRouterModelInfo(
            id = "anthropic/claude-3-haiku",
            name = "Claude 3 Haiku",
            useCase = "Fast responses",
            contextLength = 200000,
            costPer1kTokens = 0.000025
        ),
        OpenRouterModelInfo(
            id = "mistralai/mistral-7b-instruct:free",
            name = "Mistral 7B (Free)",
            useCase = "Alternative free tier",
            contextLength = 8192,
            costPer1kTokens = 0.0
        ),
        OpenRouterModelInfo(
            id = "openai/gpt-3.5-turbo",
            name = "GPT-3.5 Turbo",
            useCase = "General purpose",
            contextLength = 16385,
            costPer1kTokens = 0.00005
        ),
        OpenRouterModelInfo(
            id = "openai/gpt-4-turbo",
            name = "GPT-4 Turbo",
            useCase = "Highest quality",
            contextLength = 128000,
            costPer1kTokens = 0.0001
        )
    )
    
    fun getModelById(id: String): OpenRouterModelInfo? {
        return recommendedModels.find { it.id == id }
    }
    
    fun getFreeModels(): List<OpenRouterModelInfo> {
        return recommendedModels.filter { it.costPer1kTokens == 0.0 }
    }
    
    fun getBestValueModel(): OpenRouterModelInfo {
        return recommendedModels.find { 
            it.id == "meta-llama/llama-3-70b-instruct" 
        } ?: recommendedModels.first()
    }
}

data class OpenRouterModelInfo(
    val id: String,
    val name: String,
    val useCase: String,
    val contextLength: Int,
    val costPer1kTokens: Double
)
```

## 9.4 OpenRouter Dependency Injection

```kotlin
// di/OpenRouterModule.kt
package com.personalassistant.di

import android.content.Context
import com.personalassistant.ai.OpenRouterProvider
import com.personalassistant.data.preferences.AssistantPreferences
import com.personalassistant.data.remote.OpenRouterApi
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
object OpenRouterModule {

    @Provides
    @Singleton
    fun provideOpenRouterOkHttpClient(
        preferences: AssistantPreferences
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val authInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer ${preferences.openRouterApiKey}")
                .build()
            chain.proceed(newRequest)
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenRouterRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://openrouter.ai/api/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenRouterApi(retrofit: Retrofit): OpenRouterApi {
        return retrofit.create(OpenRouterApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenRouterProvider(
        openRouterApi: OpenRouterApi,
        preferences: AssistantPreferences
    ): OpenRouterProvider {
        return OpenRouterProvider(openRouterApi, preferences)
    }
}
```

## 9.5 AI Manager with OpenRouter Support

```kotlin
// ai/AIManager.kt (Updated)
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
    private val openRouterProvider: OpenRouterProvider,
    private val onDeviceProvider: OnDeviceAIProvider
) {

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _currentProvider = MutableStateFlow<AIProvider>(onDeviceProvider)
    val currentProvider: StateFlow<AIProvider> = _currentProvider

    private val _isOnline = MutableStateFlow(isNetworkAvailable())
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _availableProviders = MutableStateFlow(listOf<String>())
    val availableProviders: StateFlow<List<String>> = _availableProviders

    init {
        updateProvider()
        _availableProviders.value = listOf(
            PROVIDER_OPENAI,
            PROVIDER_GEMINI,
            PROVIDER_OPENROUTER,
            PROVIDER_ONDEVICE
        )
    }

    fun updateProvider() {
        _isOnline.value = isNetworkAvailable()
        
        _currentProvider.value = when {
            !preferences.useCloudAI -> onDeviceProvider
            preferences.aiProvider == PROVIDER_GEMINI && _isOnline.value -> geminiProvider
            preferences.aiProvider == PROVIDER_OPENROUTER && _isOnline.value -> openRouterProvider
            preferences.aiProvider == PROVIDER_OPENAI && _isOnline.value -> openAIProvider
            _isOnline.value -> openRouterProvider // Default to OpenRouter
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

    fun updateOpenRouterModel(modelId: String) {
        preferences.openRouterModel = modelId
    }

    companion object {
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENROUTER = "openrouter"
        const val PROVIDER_ONDEVICE = "ondevice"
    }
}
```

## 9.6 Preferences Update

```kotlin
// data/preferences/AssistantPreferences.kt (Updated)
package com.personalassistant.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantPreferences @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {
    // ... existing code ...

    var openRouterApiKey: String
        get() = getEncryptedPreference(OPENROUTER_API_KEY) ?: ""
        set(value) = saveEncryptedPreference(OPENROUTER_API_KEY, value)

    var openRouterModel: String?
        get() = getPreferenceNullable(OPENROUTER_MODEL_KEY)
        set(value) = savePreference(OPENROUTER_MODEL_KEY, value)

    companion object {
        // ... existing keys ...
        
        val OPENROUTER_API_KEY = "openrouter_api_key"
        val OPENROUTER_MODEL_KEY = stringPreferencesKey("openrouter_model")
    }
}
```

## 9.7 OpenRouter Settings Screen

```kotlin
// ui/settings/OpenRouterSettingsScreen.kt
package com.personalassistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.ai.OpenRouterModelConfig
import com.personalassistant.data.preferences.AssistantPreferences

@Composable
fun OpenRouterSettingsScreen(
    preferences: AssistantPreferences,
    onUpdateModel: (String) -> Unit,
    onUpdateApiKey: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf(preferences.openRouterApiKey) }
    var selectedModel by remember { mutableStateOf(preferences.openRouterModel ?: "") }
    var showModelPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "OpenRouter Settings",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { 
                    apiKey = it
                    onUpdateApiKey(it)
                },
                label = { Text("API Key") },
                placeholder = { Text("sk-or-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                text = "Model Selection",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showModelPicker = true },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = OpenRouterModelConfig.getModelById(selectedModel)?.name 
                                ?: "Select Model",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = OpenRouterModelConfig.getModelById(selectedModel)?.useCase 
                                ?: "Choose the best model for your needs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ChevronRight,
                        contentDescription = "Select"
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = "Recommended Models",
                style = MaterialTheme.typography.titleSmall
            )
        }

        items(OpenRouterModelConfig.recommendedModels.take(5)) { model ->
            ModelInfoCard(
                model = model,
                isSelected = model.id == selectedModel,
                onClick = {
                    selectedModel = model.id
                    onUpdateModel(model.id)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "💡 Tip",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start with free models (Llama 3 8B, Mistral 7B) to test. Upgrade to paid models for better quality.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            models = OpenRouterModelConfig.recommendedModels,
            selectedModel = selectedModel,
            onModelSelected = { 
                selectedModel = it
                onUpdateModel(it)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun ModelInfoCard(
    model: com.personalassistant.ai.OpenRouterModelInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                        else MaterialTheme.colorScheme.onSurface
                )
                if (model.costPer1kTokens == 0.0) {
                    Text(
                        text = "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Text(
                        text = "$${model.costPer1kTokens}/1k",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.useCase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Context: ${model.contextLength} tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<com.personalassistant.ai.OpenRouterModelInfo>,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Model") },
        text = {
            LazyColumn {
                items(models) { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModelSelected(model.id) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.bodyLarge)
                            Text(model.useCase, style = MaterialTheme.typography.bodySmall)
                        }
                        if (model.id == selectedModel) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Check,
                                contentDescription = "Selected"
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
        }
    )
}
```

## 9.8 Model Fallback Strategy

```kotlin
// ai/OpenRouterFallbackStrategy.kt
package com.personalassistant.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterFallbackStrategy @Inject constructor() {

    private val _fallbackChain = MutableStateFlow<List<String>>(emptyList())
    val fallbackChain: StateFlow<List<String>> = _fallbackChain

    init {
        setupDefaultFallbackChain()
    }

    private fun setupDefaultFallbackChain() {
        _fallbackChain.value = listOf(
            // Primary (best quality)
            "openai/gpt-4-turbo",
            // Fallback 1 (good balance)
            "meta-llama/llama-3-70b-instruct",
            // Fallback 2 (free tier)
            "meta-llama/llama-3-8b-instruct:free",
            // Fallback 3 (alternative free)
            "mistralai/mistral-7b-instruct:free"
        )
    }

    fun getNextModel(currentModel: String): String {
        val currentIndex = _fallbackChain.value.indexOf(currentModel)
        return if (currentIndex >= 0 && currentIndex < _fallbackChain.value.size - 1) {
            _fallbackChain.value[currentIndex + 1]
        } else {
            _fallbackChain.value.last()
        }
    }

    fun getPrimaryModel(): String {
        return _fallbackChain.value.firstOrNull() 
            ?: "meta-llama/llama-3-8b-instruct:free"
    }

    fun configureFallbackChain(models: List<String>) {
        _fallbackChain.value = models
    }

    fun resetToDefaults() {
        setupDefaultFallbackChain()
    }
}
```

## 9.9 Cost Tracking

```kotlin
// util/OpenRouterCostTracker.kt
package com.personalassistant.util

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.personalassistant.ai.OpenRouterModelConfig
import com.personalassistant.data.preferences.AssistantPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterCostTracker @Inject constructor(
    private val context: Context,
    private val preferences: AssistantPreferences
) {
    private val dataStore = context.dataStore

    private var totalPromptTokens = 0
    private var totalCompletionTokens = 0
    private var totalCost = 0.0

    fun trackUsage(modelId: String, promptTokens: Int, completionTokens: Int) {
        totalPromptTokens += promptTokens
        totalCompletionTokens += completionTokens
        
        val modelInfo = OpenRouterModelConfig.getModelById(modelId)
        val cost = calculateCost(modelInfo, promptTokens, completionTokens)
        totalCost += cost
        
        saveUsage()
    }

    private fun calculateCost(
        modelInfo: OpenRouterModelInfo?,
        promptTokens: Int,
        completionTokens: Int
    ): Double {
        val rate = modelInfo?.costPer1kTokens ?: 0.0001
        return ((promptTokens + completionTokens) / 1000.0) * rate
    }

    fun getTotalCost(): Double = totalCost

    fun getTotalTokens(): Int = totalPromptTokens + totalCompletionTokens

    fun getUsageStats(): UsageStats {
        return UsageStats(
            totalPromptTokens = totalPromptTokens,
            totalCompletionTokens = totalCompletionTokens,
            totalCost = totalCost,
            averageCostPerRequest = if (totalPromptTokens > 0) totalCost / (totalPromptTokens / 100) else 0.0
        )
    }

    fun resetStats() {
        totalPromptTokens = 0
        totalCompletionTokens = 0
        totalCost = 0.0
    }

    private fun saveUsage() {
        // Persist to DataStore
    }

    fun getCostEstimate(): Flow<String> {
        return dataStore.data.map { prefs ->
            when {
                totalCost < 0.01 -> "Free tier"
                totalCost < 1.0 -> "$${String.format("%.2f", totalCost)}"
                else -> "$${String.format("%.2f", totalCost)} (This month)"
            }
        }
    }

    data class UsageStats(
        val totalPromptTokens: Int,
        val totalCompletionTokens: Int,
        val totalCost: Double,
        val averageCostPerRequest: Double
    )

    companion object {
        val TOTAL_COST_KEY = doublePreferencesKey("openrouter_total_cost")
        val TOTAL_TOKENS_KEY = androidx.datastore.preferences.core.intPreferencesKey("openrouter_total_tokens")
    }
}
```

## 9.10 OpenRouter Dashboard

```kotlin
// ui/dashboard/OpenRouterDashboard.kt
package com.personalassistant.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.ai.OpenRouterModelConfig
import com.personalassistant.util.OpenRouterCostTracker
import java.text.NumberFormat

@Composable
fun OpenRouterDashboard(
    costTracker: OpenRouterCostTracker,
    currentModel: String
) {
    val usageStats by costTracker.getUsageStats().collectAsState(
        initial = OpenRouterCostTracker.UsageStats(0, 0, 0.0, 0.0)
    )
    
    val currentModelInfo = OpenRouterModelConfig.getModelById(currentModel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "OpenRouter Dashboard",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Current Model",
                value = currentModelInfo?.name ?: "Unknown",
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "Total Cost",
                value = NumberFormat.getCurrencyInstance().format(usageStats.totalCost),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Prompt Tokens",
                value = usageStats.totalPromptTokens.toString(),
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "Completion Tokens",
                value = usageStats.totalCompletionTokens.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { costTracker.resetStats() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Usage Stats")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { /* Navigate to model picker */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Change Model")
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

## 9.11 Getting OpenRouter API Key

```markdown
## OpenRouter Setup Guide

### Step 1: Create Account
1. Visit https://openrouter.ai
2. Click "Sign Up" or "Login"
3. Create account with email or OAuth

### Step 2: Get API Key
1. Go to Dashboard → Keys
2. Click "Create Key"
3. Copy the key (starts with `sk-or-`)
4. Save securely (password manager recommended)

### Step 3: Configure in App
1. Open Personal Assistant app
2. Go to Settings → AI Provider
3. Select "OpenRouter"
4. Paste your API key
5. Choose a model

### Step 4: Start Using
- Free models work immediately
- Paid models require adding credits
- Monitor usage in dashboard

### Pricing Tips
- Free tier: Llama 3 8B, Mistral 7B
- Best value: Llama 3 70B ($0.08/1M tokens)
- Premium: GPT-4 Turbo ($0.10/1M tokens)

### Security Notes
- Never share your API key
- Store in Android Keystore
- Rotate keys periodically
- Monitor usage regularly
```

---

## Summary

OpenRouter integration adds:
✅ **100+ models** through single API  
✅ **Free tier options** for testing  
✅ **Cost optimization** with model selection  
✅ **Automatic fallback** for reliability  
✅ **Usage tracking** for budget control  

### Quick Start
1. Get API key from openrouter.ai
2. Add to app settings
3. Choose model (start with free tier)
4. Monitor usage in dashboard

---

**Previous**: [Testing & Deployment](./08-testing-deployment.md)
