# 8. Testing & Deployment

## 8.1 Unit Testing

```kotlin
// test/AssistantRepositoryTest.kt
package com.personalassistant.test

import com.personalassistant.ai.AIProvider
import com.personalassistant.data.repository.AssistantRepositoryImpl
import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.CommandType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class AssistantRepositoryTest {

    @Mock
    private lateinit var aiProvider: AIProvider

    private lateinit var repository: AssistantRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = AssistantRepositoryImpl(aiProvider)
    }

    @Test
    fun processCommand_returnsValidResponse() = runTest {
        // Given
        val command = "What's the weather?"
        val expectedResponse = AssistantResponse(
            message = "The weather is sunny",
            type = CommandType.QUESTION,
            confidence = 0.9f
        )
        
        `when`(aiProvider.generateResponse(command)).thenReturn(expectedResponse)

        // When
        val result = repository.processCommand(command)

        // Then
        assertEquals(expectedResponse.message, result.message)
        assertEquals(expectedResponse.type, result.type)
        assertEquals(expectedResponse.confidence, result.confidence, 0.01f)
    }

    @Test
    fun processCommand_handlesEmptyCommand() = runTest {
        // Given
        val command = ""
        val expectedResponse = AssistantResponse(
            message = "Please provide a valid command",
            type = CommandType.INFO,
            confidence = 0.5f
        )
        
        `when`(aiProvider.generateResponse(command)).thenReturn(expectedResponse)

        // When
        val result = repository.processCommand(command)

        // Then
        assertNotNull(result)
        assertEquals(CommandType.INFO, result.type)
    }

    @Test
    fun processCommand_handlesException() = runTest {
        // Given
        val command = "test"
        `when`(aiProvider.generateResponse(command)).thenThrow(RuntimeException("API Error"))

        // When & Then
        try {
            repository.processCommand(command)
            fail("Expected exception")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("API Error") ?: false)
        }
    }
}
```

## 8.2 Integration Testing

```kotlin
// test/AssistantServiceIntegrationTest.kt
package com.personalassistant.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalassistant.services.AssistantService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantServiceIntegrationTest {

    private lateinit var context: Context
    private lateinit var service: AssistantService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        service = AssistantService()
    }

    @Test
    fun service_creation_isSuccessful() {
        // Given
        service.onCreate()

        // Then
        assertTrue(service.assistantState.value is com.personalassistant.model.AssistantState.ACTIVE)
    }

    @Test
    fun service_startCommand_returnsSticky() {
        // Given
        val intent = android.content.Intent(context, AssistantService::class.java).apply {
            action = AssistantService.ACTION_START
        }

        // When
        val result = service.onStartCommand(intent, 0, 1)

        // Then
        assertEquals(android.app.Service.START_STICKY, result)
    }

    @Test
    fun service_stopCommand_stopsService() {
        // Given
        service.onCreate()
        val stopIntent = android.content.Intent(context, AssistantService::class.java).apply {
            action = AssistantService.ACTION_STOP
        }

        // When
        service.onStartCommand(stopIntent, 0, 1)

        // Then
        assertTrue(service.assistantState.value is com.personalassistant.model.AssistantState.IDLE)
    }
}
```

## 8.3 UI Testing with Compose

```kotlin
// test/AssistantScreenTest.kt
package com.personalassistant.test

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalassistant.ui.AssistantScreen
import com.personalassistant.viewmodel.AssistantViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: AssistantViewModel

    @Test
    fun screen_displaysAssistantOrb() {
        // Given
        composeTestRule.setContent {
            AssistantScreen(viewModel)
        }

        // Then
        composeTestRule
            .onNodeWithTag("assistant_orb")
            .assertExists()
            .assertDisplayed()
    }

    @Test
    fun screen_displaysCommandInput() {
        // Given
        composeTestRule.setContent {
            AssistantScreen(viewModel)
        }

        // Then
        composeTestRule
            .onNodeWithTag("command_input")
            .assertExists()
            .assertDisplayed()
    }

    @Test
    fun screen_micButton_triggersListening() {
        // Given
        composeTestRule.setContent {
            AssistantScreen(viewModel)
        }

        // When
        composeTestRule
            .onNodeWithTag("mic_button")
            .performClick()

        // Then
        composeTestRule
            .onNodeWithTag("listening_indicator")
            .assertExists()
    }

    @Test
    fun screen_textInput_submitsCommand() {
        // Given
        composeTestRule.setContent {
            AssistantScreen(viewModel)
        }

        // When
        composeTestRule
            .onNodeWithTag("command_input")
            .performTextInput("Hello assistant")
        
        composeTestRule
            .onNodeWithTag("send_button")
            .performClick()

        // Then
        composeTestRule
            .onNodeWithTag("conversation_item")
            .assertExists()
    }
}
```

## 8.4 End-to-End Testing

```kotlin
// test/AssistantE2ETest.kt
package com.personalassistant.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalassistant.R
import com.personalassistant.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun fullFlow_voiceCommand_processesSuccessfully() {
        // Given: App is launched

        // When: User taps mic button
        Espresso.onView(ViewMatchers.withId(R.id.mic_button))
            .perform(ViewActions.click())

        // Then: Listening indicator appears
        Espresso.onView(ViewMatchers.withId(R.id.listening_indicator))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun fullFlow_textCommand_processesSuccessfully() {
        // Given: App is launched

        // When: User types command
        Espresso.onView(ViewMatchers.withId(R.id.command_input))
            .perform(ViewActions.typeText("Set a reminder"))
            .perform(ViewActions.closeSoftKeyboard())
        
        Espresso.onView(ViewMatchers.withId(R.id.send_button))
            .perform(ViewActions.click())

        // Then: Response appears
        Espresso.onView(ViewMatchers.withId(R.id.response_text))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun fullFlow_settings_opensSuccessfully() {
        // Given: App is launched

        // When: User opens settings
        Espresso.onView(ViewMatchers.withId(R.id.settings_button))
            .perform(ViewActions.click())

        // Then: Settings screen appears
        Espresso.onView(ViewMatchers.withText("Settings"))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}
```

## 8.5 Performance Testing

```kotlin
// test/PerformanceTest.kt
package com.personalassistant.test

import android.os.SystemClock
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalassistant.ai.AIManager
import com.personalassistant.model.AssistantResponse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class PerformanceTest {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Inject
    lateinit var aiManager: AIManager

    @Test
    fun responseGeneration_performance() {
        benchmarkRule.measureRepeated {
            val response: AssistantResponse = 
                aiManager.generateResponse("What's the time?")
            
            // Ensure response is not null
            assert(response != null)
        }
    }

    @Test
    fun commandProcessing_latency() {
        val startTime = SystemClock.elapsedRealtime()
        
        val response = aiManager.generateResponse("Hello")
        
        val endTime = SystemClock.elapsedRealtime()
        val latency = endTime - startTime
        
        // Assert latency is acceptable (< 500ms for on-device)
        assert(latency < 500) { "Latency too high: $latency ms" }
    }

    @Test
    fun memoryUsage_test() {
        val runtime = Runtime.getRuntime()
        
        // Force garbage collection
        runtime.gc()
        
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Process multiple commands
        repeat(100) {
            aiManager.generateResponse("Test command $it")
        }
        
        runtime.gc()
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val memoryIncrease = finalMemory - initialMemory
        
        // Assert memory increase is acceptable (< 10MB)
        assert(memoryIncrease < 10 * 1024 * 1024) {
            "Memory leak detected: ${memoryIncrease / 1024 / 1024}MB"
        }
    }
}
```

## 8.6 Battery Impact Testing

```kotlin
// test/BatteryImpactTest.kt
package com.personalassistant.test

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalassistant.services.AssistantService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryImpactTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun service_batteryConsumption_acceptable() {
        // Given
        val initialBatteryLevel = getBatteryLevel()
        
        // When: Run service for 1 hour
        AssistantService.start(context)
        
        Thread.sleep(60 * 60 * 1000) // 1 hour
        
        val finalBatteryLevel = getBatteryLevel()
        val batteryDrain = initialBatteryLevel - finalBatteryLevel

        // Then: Battery drain should be < 5% per hour
        assertTrue("Battery drain too high: $batteryDrain%", batteryDrain < 5)
    }

    @Test
    fun wakelock_batteryImpact_acceptable() {
        // Given
        val initialBatteryLevel = getBatteryLevel()
        
        // When: Acquire wakelock for 30 minutes
        val service = AssistantService()
        service.onCreate()
        service.acquireWakeLock(30 * 60 * 1000)
        
        Thread.sleep(30 * 60 * 1000)
        
        service.releaseWakeLock()
        
        val finalBatteryLevel = getBatteryLevel()
        val batteryDrain = initialBatteryLevel - finalBatteryLevel

        // Then: Battery drain should be < 3% per 30 minutes
        assertTrue("Wakelock drain too high: $batteryDrain%", batteryDrain < 3)
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 1
        
        return (level.toFloat() / scale.toFloat() * 100).toInt()
    }
}
```

## 8.7 Security Testing

```kotlin
// test/SecurityTest.kt
package com.personalassistant.test

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalassistant.util.EncryptionManager
import com.personalassistant.data.preferences.AssistantPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class SecurityTest {

    @Inject
    lateinit var encryptionManager: EncryptionManager

    @Inject
    lateinit var preferences: AssistantPreferences

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun encryption_dataIsEncrypted() {
        // Given
        val originalData = "sensitive_api_key_12345"

        // When
        val encrypted = encryptionManager.encrypt(originalData)
        val decrypted = encryptionManager.decrypt(encrypted)

        // Then
        assertEquals(originalData, decrypted)
        assertNotEquals(originalData, encrypted.ciphertext)
    }

    @Test
    fun apiKey_storage_isSecure() {
        // Given
        val apiKey = "sk-test-1234567890"

        // When
        preferences.openAIApiKey = apiKey
        val retrieved = preferences.openAIApiKey

        // Then
        assertEquals(apiKey, retrieved)
        // Verify key is not stored in plaintext
        verifyNoPlaintextStorage()
    }

    @Test
    fun permissions_areRequestedCorrectly() {
        // Given & When: Check permission status
        
        // Then: Required permissions should be requested
        assertTrue("Internet permission required", 
            hasPermission(android.Manifest.permission.INTERNET))
        assertTrue("Foreground service permission required",
            hasPermission(android.Manifest.permission.FOREGROUND_SERVICE))
    }

    private fun hasPermission(permission: String): Boolean {
        return android.content.pm.PackageManager.PERMISSION_GRANTED ==
            android.content.ContextCompat.checkSelfPermission(
                context,
                permission
            )
    }

    private fun verifyNoPlaintextStorage() {
        // Check shared prefs for plaintext keys
        val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val allEntries = prefs.all
        
        // Verify no API keys stored in plaintext
        allEntries.forEach { (key, value) ->
            assertFalse("Plaintext key found: $key", 
                value.toString().contains("sk-") || 
                value.toString().contains("AIza"))
        }
    }
}
```

## 8.8 CI/CD Pipeline Configuration

```yaml
# .github/workflows/build.yml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Run unit tests
      run: ./gradlew test

    - name: Run lint
      run: ./gradlew lint

    - name: Build debug APK
      run: ./gradlew assembleDebug

    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk

  test:
    runs-on: ubuntu-latest
    needs: build

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Run instrumented tests
      uses: reactivecircus/android-emulator-runner@v2
      with:
        api-level: 30
        script: ./gradlew connectedCheck

  deploy:
    runs-on: ubuntu-latest
    needs: [build, test]
    if: github.ref == 'refs/heads/main'

    steps:
    - uses: actions/checkout@v3

    - name: Build release APK
      run: ./gradlew assembleRelease

    - name: Upload to Play Store
      uses: r0adkll/upload-google-play@v1
      with:
        serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT }}
        packageName: com.personalassistant
        releaseFiles: app/build/outputs/apk/release/app-release.apk
        track: internal
        status: completed
```

## 8.9 Release Checklist

```markdown
## Pre-Release Checklist

### Code Quality
- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] Lint checks passing
- [ ] Code review completed
- [ ] Security audit completed

### Performance
- [ ] Battery impact < 5%/hour
- [ ] Memory usage < 100MB
- [ ] Response latency < 500ms (on-device)
- [ ] Response latency < 2s (cloud)

### Security
- [ ] All data encrypted at rest
- [ ] API keys in Keystore
- [ ] No hardcoded secrets
- [ ] Network security config applied
- [ ] Privacy policy updated

### Privacy
- [ ] GDPR compliance verified
- [ ] CCPA compliance verified
- [ ] User consent flow implemented
- [ ] Data export feature working
- [ ] Data deletion feature working

### Documentation
- [ ] User documentation complete
- [ ] API documentation complete
- [ ] Privacy policy published
- [ ] Terms of service published

### Distribution
- [ ] Version code incremented
- [ ] Version name updated
- [ ] Release notes written
- [ ] Screenshots updated
- [ ] Store listing optimized

### Post-Release
- [ ] Crash monitoring enabled
- [ ] Analytics tracking configured
- [ ] User feedback channel ready
- [ ] Support team briefed
```

## 8.10 Monitoring & Analytics

```kotlin
// util/AnalyticsManager.kt
package com.personalassistant.util

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsManager @Inject constructor(
    @Inject @ApplicationContext private val context: Context
) {

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    fun logEvent(eventName: String, params: Map<String, String>) {
        val bundle = android.os.Bundle()
        params.forEach { (key, value) ->
            bundle.putString(key, value)
        }
        firebaseAnalytics.logEvent(eventName, bundle)
    }

    fun logAssistantCommand(command: String, source: String) {
        logEvent("assistant_command", mapOf(
            "command" to command,
            "source" to source,
            "timestamp" to System.currentTimeMillis().toString()
        ))
    }

    fun logResponseTime(responseTimeMs: Long, provider: String) {
        logEvent("response_time", mapOf(
            "time_ms" to responseTimeMs.toString(),
            "provider" to provider
        ))
    }

    fun logError(error: Throwable, context: String) {
        crashlytics.recordException(error)
        crashlytics.setCustomKey("error_context", context)
    }

    fun logBatteryImpact(batteryDrainPercent: Double, durationMinutes: Long) {
        logEvent("battery_impact", mapOf(
            "drain_percent" to batteryDrainPercent.toString(),
            "duration_minutes" to durationMinutes.toString()
        ))
    }

    fun setUserProperties(userId: String, preferences: Map<String, String>) {
        firebaseAnalytics.setUserId(userId)
        preferences.forEach { (key, value) ->
            firebaseAnalytics.setUserProperty(key, value)
        }
    }

    fun setCrashlyticsKeys(data: Map<String, String>) {
        data.forEach { (key, value) ->
            crashlytics.setCustomKey(key, value)
        }
    }
}
```

## 8.11 Play Store Listing

```markdown
## App Title
Personal AI Assistant - Your Silent Companion

## Short Description
An intelligent personal assistant that runs silently in the background, ready to help whenever you need.

## Full Description

**Your Personal AI Assistant**

Transform your Android phone into an intelligent companion with our privacy-first AI assistant.

**Key Features:**

✓ **Silent Background Operation** - Runs quietly without interrupting your workflow
✓ **Voice & Text Input** - Interact naturally through speech or typing
✓ **Context Awareness** - Remembers your preferences and conversation history
✓ **Privacy-First Design** - All data encrypted, optional on-device processing
✓ **Battery Efficient** - Optimized for minimal power consumption
✓ **Multiple AI Providers** - Choose between OpenAI, Gemini, or on-device AI

**What Can It Do?**

- Answer questions instantly
- Set reminders and alarms
- Control device features
- Provide real-time information
- Learn from your preferences

**Privacy Commitment:**

- No data sold to third parties
- Optional cloud processing
- Local data encryption
- Easy data export/deletion
- Transparent permissions

**System Requirements:**

- Android 8.0 or higher
- 100MB free storage
- Internet connection (for cloud AI)

**Permissions:**

- Microphone: Voice input
- Notifications: Status updates
- Internet: Cloud AI processing
- Contacts: Optional integration

Download now and experience the future of personal assistance!

## Screenshots

1. Main screen with assistant orb
2. Conversation history view
3. Settings screen
4. Privacy dashboard
5. Battery optimization settings

## Category
Productivity

## Content Rating
Everyone

## Contact
support@personalassistant.com
```

---

## Summary

This comprehensive guide covers all aspects of building an AI personal assistant for Android:

1. **Architecture** - Clean architecture with modular design
2. **Setup** - Complete Android project configuration
3. **Core Components** - Services, receivers, handlers
4. **AI Integration** - Cloud and on-device providers
5. **Privacy** - Encryption, consent, compliance
6. **Battery** - Optimization and power management
7. **UI/UX** - Modern Compose interface
8. **Testing** - Unit, integration, E2E, performance

### Next Steps

1. Clone this guide structure
2. Set up Android Studio project
3. Implement modules incrementally
4. Test thoroughly
5. Deploy to Play Store

Good luck building your AI personal assistant! 🚀
