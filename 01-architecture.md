# 1. Project Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ MainActivity│  │  Settings   │  │  NotificationPanel  │  │
│  │    (UI)     │  │   Screen    │  │     (Overlay)       │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                      DOMAIN LAYER                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Assistant  │  │   Command   │  │     User            │  │
│  │  UseCases   │  │   Handler   │  │     Preferences     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                       DATA LAYER                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Repository │  │   Local     │  │     Remote          │  │
│  │  Interfaces │  │   DB (Room) │  │     API (Retrofit)  │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    INFRASTRUCTURE LAYER                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Foreground │  │  Broadcast  │  │     Accessibility   │  │
│  │  Service    │  │  Receivers  │  │     Service         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

### Presentation Layer
- **MainActivity**: Main entry point, permission requests, initial setup
- **SettingsScreen**: User preferences, API keys, privacy settings
- **NotificationPanel**: Floating overlay for quick interactions

### Domain Layer
- **AssistantUseCases**: Business logic for assistant operations
- **CommandHandler**: Parse and route user commands
- **UserPreferences**: Manage user settings and learning

### Data Layer
- **Repositories**: Abstract data sources
- **Room Database**: Local storage for conversations, preferences
- **Retrofit**: API calls to cloud AI services

### Infrastructure Layer
- **ForegroundService**: Background operation core
- **BroadcastReceivers**: System event listeners
- **AccessibilityService**: Screen content reading (optional)

## Design Patterns Used

### 1. Repository Pattern
```kotlin
interface AssistantRepository {
    suspend fun processCommand(command: String): AssistantResponse
    suspend fun getConversationHistory(): List<Conversation>
    suspend fun saveConversation(conversation: Conversation)
}

class AssistantRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val remoteDataSource: RemoteDataSource
) : AssistantRepository {
    // Implementation
}
```

### 2. Observer Pattern
```kotlin
class AssistantViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    fun processCommand(command: String) {
        viewModelScope.launch {
            // Process and emit state
        }
    }
}
```

### 3. Strategy Pattern (AI Provider)
```kotlin
interface AIProvider {
    suspend fun generateResponse(prompt: String): AIResponse
    fun supportsOffline(): Boolean
}

class OpenAIProvider(private val apiKey: String) : AIProvider
class GeminiProvider(private val apiKey: String) : AIProvider
class OnDeviceProvider(private val model: Interpreter) : AIProvider
```

### 4. Singleton (Dependency Injection)
```kotlin
// Using Hilt for DI
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideAssistantRepository(
        localDataSource: LocalDataSource,
        remoteDataSource: RemoteDataSource
    ): AssistantRepository {
        return AssistantRepositoryImpl(localDataSource, remoteDataSource)
    }
}
```

## Data Flow

```
User Input (Voice/Text)
        ↓
    CommandParser
        ↓
    IntentRecognizer
        ↓
    ┌───────────────┐
    │  Cloud AI     │ ←→ API (OpenAI/Gemini)
    │  OR           │
    │  On-Device    │ ←→ TFLite Model
    └───────────────┘
        ↓
    ResponseFormatter
        ↓
    ActionExecutor
        ↓
    Output (TTS/Display)
```

## Module Dependencies

```kotlin
// build.gradle.kts (app level)
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // Architecture Components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // ML/AI
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    
    // Speech
    implementation("androidx.speech:speech:1.0.0-alpha03")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

## Thread Architecture

```
Main Thread (UI)
    ├── Display updates
    └── User interactions

Background Thread (IO)
    ├── Database operations
    └── File I/O

Coroutine Scope (Default)
    ├── AI processing
    └── Network calls

WorkManager Thread
    └── Scheduled tasks
```

## Scalability Considerations

1. **Modular Design**: Each feature in separate module
2. **Interface Abstraction**: Easy to swap AI providers
3. **Lazy Loading**: Load models only when needed
4. **Caching**: Cache frequent responses
5. **Rate Limiting**: Respect API quotas

---

**Next**: [Android Setup & Configuration](./02-android-setup.md)
