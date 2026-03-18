# Android Assistant - Installation Guide

## How to Install on Your Mobile Phone 📱

This guide shows you how to build and install the AI Assistant app on your Android phone.

---

## Method 1: Android Studio (Recommended for Development)

### Step 1: Install Android Studio

```
1. Download from: https://developer.android.com/studio
2. Install on your computer (Windows/Mac/Linux)
3. Open Android Studio
```

### Step 2: Create New Project

```
1. File → New → New Project
2. Select: "Empty Views Activity"
3. Configure:
   - Name: AI Assistant
   - Package: com.yourname.assistant
   - Language: Kotlin
   - Minimum SDK: API 26 (Android 8.0)
   - Build Configuration Language: Kotlin DSL
4. Click Finish
```

### Step 3: Copy Guide Code to Project

```
Your project structure should look like:

app/
├── src/
│   ├── main/
│   │   ├── java/com/yourname/assistant/
│   │   │   ├── AssistantService.kt          (from 03-core-components.md)
│   │   │   ├── automation/
│   │   │   │   ├── AutomationEngine.kt      (from 14-tasker-automation-engine.md)
│   │   │   │   ├── AutomationRule.kt
│   │   │   │   ├── NotificationListener.kt
│   │   │   │   ├── LocationMonitor.kt
│   │   │   │   ├── ConnectivityMonitor.kt
│   │   │   │   ├── RuleBuilder.kt
│   │   │   │   ├── NaturalLanguageParser.kt
│   │   │   │   └── PermissionHelper.kt
│   │   │   ├── ai/
│   │   │   │   ├── VoiceAutomationHandler.kt
│   │   │   │   └── AIProvider.kt            (from 04-ai-integration.md)
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt          (from 07-ui-ux.md)
│   │   │   │   └── AssistantViewModel.kt
│   │   │   └── AssistantApplication.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml
│   │   │   ├── drawable/
│   │   │   │   └── ic_automation.xml
│   │   │   └── values/
│   │   │       └── strings.xml
│   │   └── AndroidManifest.xml              (update with permissions)
│   ├── test/                                (unit tests)
│   └── androidTest/                         (instrumentation tests)
├── build.gradle.kts                         (app-level)
└── build.gradle.kts                         (project-level)
```

### Step 4: Add Dependencies

```kotlin
// file: app/build.gradle.kts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.yourname.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourname.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Android Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    
    // Location Services (for geofencing)
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Networking (for webhooks & AI)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Room Database (for rule persistence)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    
    // Work Manager (for scheduled tasks)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

### Step 5: Sync & Build

```
1. Click "Sync Now" (top right)
2. Fix any errors
3. Build → Build Bundle(s) / APK(s) → Build APK
4. Wait for build to complete
```

### Step 6: Install on Phone

#### Option A: USB Cable

```
1. Enable USB Debugging on phone:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → USB Debugging → ON

2. Connect phone to computer via USB

3. In Android Studio:
   - Click Run (green play button)
   - Select your phone from device list
   - App installs and launches automatically
```

#### Option B: WiFi (Android 11+)

```
1. On phone:
   - Settings → Developer Options
   - Wireless Debugging → ON
   - Tap "Pair device with pairing code"
   - Note the pairing code

2. On computer (Terminal/Command Prompt):
   cd to your project platform-tools folder
   ./adb pair 192.168.1.100:12345  (use your phone's IP:port)
   Enter pairing code when prompted

3. Connect:
   ./adb connect 192.168.1.100:5555

4. Install:
   ./adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Method 2: Direct APK Installation (No Computer)

### Step 1: Build APK on Computer

```
1. Open project in Android Studio
2. Build → Build Bundle(s) / APK(s) → Build APK
3. Find APK at: app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Transfer APK to Phone

```
Option A: Google Drive
1. Upload APK to Google Drive
2. Open Drive app on phone
3. Download APK

Option B: USB Transfer
1. Connect phone to computer
2. Copy APK to phone's Download folder
3. Disconnect phone

Option C: Email/WhatsApp
1. Email APK to yourself
2. Or send via WhatsApp
3. Download on phone
```

### Step 3: Install APK on Phone

```
1. Open File Manager app
2. Navigate to Download folder
3. Tap on app-debug.apk
4. Allow "Install from unknown sources" if prompted
5. Tap Install
6. Wait for installation
7. Tap Open
```

### Step 4: Grant Permissions

```
First Launch Setup:
1. Open AI Assistant app
2. Grant permissions when prompted:
   □ Notification Access
   □ Location (Always allow)
   □ Microphone
   □ Phone
   □ SMS
   □ Contacts
3. Complete setup wizard
4. Done! ✅
```

---

## Method 3: Quick Testing (For Developers)

### Using ADB Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Install on specific device (multiple devices)
adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk

# Uninstall
adb uninstall com.yourname.assistant

# Clear app data
adb shell pm clear com.yourname.assistant

# View logs
adb logcat | grep -i "Assistant\|Automation"

# Launch app
adb shell am start -n com.yourname.assistant/.ui.MainActivity
```

---

## Troubleshooting

### Problem: Build fails

```
Common fixes:
□ Sync Gradle files (File → Sync Project)
□ Clean Build (Build → Clean Project)
□ Rebuild (Build → Rebuild Project)
□ Invalidate Caches (File → Invalidate Caches → Restart)
□ Update Android Studio to latest version
□ Check minSdk/targetSdk in build.gradle
```

### Problem: App crashes on launch

```
Check:
□ AndroidManifest.xml has all permissions
□ All services declared in manifest
□ API keys configured (OpenRouter, etc.)
□ Check logcat for error messages
□ Grant all runtime permissions
```

### Problem: Automation not working

```
Check:
□ Notification Access granted
□ Location permission set to "Always allow"
□ Battery optimization disabled for app
□ Foreground service running (check notification)
□ Rules saved correctly (check app settings)
```

### Problem: Can't install APK

```
Fixes:
□ Enable "Install from Unknown Sources"
   Settings → Apps → Special Access → Install Unknown Apps
□ Allow your file manager/browser
□ Check APK is not corrupted (rebuild)
□ Ensure enough storage space
□ Android version compatible (minSdk 26+)
```

---

## Post-Installation Setup

### First-Time Configuration

```
1. Open AI Assistant
2. Complete Setup Wizard:
   □ Sign in / Skip
   □ Grant all permissions
   □ Select AI provider (OpenRouter recommended)
   □ Enter API key
   □ Choose default model
   □ Configure voice settings
   □ Test microphone
3. Create First Automation Rule:
   Settings → Automation → Create Rule
   Example: "Tell me when Mom calls"
4. Test Rule:
   Ask someone to call you
   Assistant should announce caller
5. Done! ✅
```

### Recommended Settings

```
AI Provider:
- Provider: OpenRouter (free tier available)
- Model: Llama 3 8B (free) or GPT-4 (paid)
- API Key: Get from openrouter.ai

Automation:
- Start on boot: ON
- Battery optimization: OFF (whitelist)
- Notification access: GRANTED
- Location: Always allow

Voice:
- Language: Your preferred language
- Wake word: Optional (battery drain)
- TTS speed: 1.0x (adjust as needed)

Privacy:
- Data collection: Minimal
- Analytics: OFF (optional)
- Cloud backup: ON (optional)
```

---

## Quick Reference

### Build Commands

```bash
# Debug build (fast, for testing)
./gradlew assembleDebug

# Release build (optimized, for production)
./gradlew assembleRelease

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Generate code coverage
./gradlew jacocoTestReport
```

### Install Commands

```bash
# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Install release APK
adb install -r app/build/outputs/apk/release/app-release.apk

# Install with grant all permissions
adb install -g -r app/build/outputs/apk/debug/app-debug.apk
```

### APK Locations

```
Debug:  app/build/outputs/apk/debug/app-debug.apk
Release: app/build/outputs/apk/release/app-release.apk
```

---

## Summary

### Installation Methods

| Method | Best For | Difficulty | Speed |
|--------|----------|------------|-------|
| Android Studio + USB | Development | ⭐⭐ Easy | Fast |
| Android Studio + WiFi | Wireless testing | ⭐⭐⭐ Medium | Fast |
| Direct APK | End users | ⭐ Easy | Medium |
| ADB commands | Developers | ⭐⭐ Easy | Fastest |

### Recommended Workflow

```
Development:
1. Code in Android Studio
2. Run on phone via USB (instant deploy)
3. Test features
4. Fix issues
5. Repeat

Production:
1. Build release APK
2. Sign with keystore
3. Test on multiple devices
4. Distribute (Play Store / sideload)
```

---

## Next Steps

1. **Build your first APK** - Follow Method 1
2. **Install on phone** - Test basic features
3. **Configure AI provider** - Get API key
4. **Create automation rules** - Test Tasker-style features
5. **Customize UI** - Make it your own
6. **Share with friends** - Build release APK

---

**Related Guides**:
- [Android Setup](./02-android-setup.md) - Project setup details
- [Core Components](./03-core-components.md) - Service implementation
- [Tasker Automation Engine](./14-tasker-automation-engine.md) - Automation code
- [Testing & Deployment](./08-testing-deployment.md) - Production checklist
