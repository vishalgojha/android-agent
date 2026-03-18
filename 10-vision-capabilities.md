# 10. Vision Capabilities

## Overview

Your AI assistant can now **see and understand images** using vision-enabled models:

| Model | Vision Support | Cost |
|-------|---------------|------|
| GPT-4 Turbo | ✅ Yes | $0.10/1M tokens |
| GPT-4 Vision | ✅ Yes | $0.10/1M tokens |
| Claude 3 (all variants) | ✅ Yes | $0.25-3/1M tokens |
| Llava 1.5 | ✅ Yes | Free |
| Gemini Pro Vision | ✅ Yes | Free tier |
| Llama 3 | ❌ No | - |

## 10.1 Vision-Enabled AI Provider

```kotlin
// ai/VisionAIProvider.kt
package com.personalassistant.ai

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.personalassistant.data.remote.OpenRouterApi
import com.personalassistant.data.remote.OpenRouterMessage
import com.personalassistant.data.remote.OpenRouterRequest
import com.personalassistant.model.AssistantResponse
import com.personalassistant.model.CommandType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionAIProvider @Inject constructor(
    private val openRouterApi: OpenRouterApi
) : AIProvider {

    override suspend fun generateResponse(prompt: String): AssistantResponse {
        // Text-only fallback
        return AssistantResponse(
            message = "Vision requires an image. Please provide one.",
            type = CommandType.INFO
        )
    }

    override suspend fun generateWithContext(
        prompt: String,
        context: ConversationContext
    ): AssistantResponse {
        return generateResponse(prompt)
    }

    /**
     * Analyze an image with text prompt
     */
    suspend fun analyzeImage(
        bitmap: Bitmap,
        prompt: String
    ): AssistantResponse {
        return withContext(Dispatchers.IO) {
            try {
                // Convert bitmap to base64
                val base64Image = bitmapToBase64(bitmap)
                
                // Build vision request
                val request = OpenRouterRequest(
                    model = "openai/gpt-4-turbo", // Vision-capable model
                    messages = listOf(
                        OpenRouterMessage(
                            role = "user",
                            content = buildVisionContent(prompt, base64Image)
                        )
                    ),
                    maxTokens = 500,
                    temperature = 0.7f
                )

                val response = openRouterApi.chatCompletions(request)
                
                val content = response.choices.firstOrNull()?.message?.content 
                    ?: "I couldn't analyze the image."
                
                AssistantResponse(
                    message = content,
                    type = CommandType.INFO,
                    confidence = 0.85f
                )
            } catch (e: Exception) {
                AssistantResponse(
                    message = "Error analyzing image: ${e.message}",
                    type = CommandType.INFO,
                    confidence = 0.5f
                )
            }
        }
    }

    /**
     * Analyze image from URI (camera/gallery)
     */
    suspend fun analyzeImageFromUri(
        uri: Uri,
        prompt: String
    ): AssistantResponse {
        return withContext(Dispatchers.IO) {
            try {
                // Load bitmap from URI
                val inputStream = android.content.Context().contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                
                analyzeImage(bitmap, prompt)
            } catch (e: Exception) {
                AssistantResponse(
                    message = "Error loading image: ${e.message}",
                    type = CommandType.INFO,
                    confidence = 0.5f
                )
            }
        }
    }

    /**
     * Build multimodal content (text + image)
     */
    private fun buildVisionContent(text: String, base64Image: String): String {
        // OpenRouter accepts images in this format
        return """
            [
                {
                    "type": "text",
                    "text": "$text"
                },
                {
                    "type": "image_url",
                    "image_url": {
                        "url": "data:image/jpeg;base64,$base64Image"
                    }
                }
            ]
        """.trimIndent()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    override fun supportsOffline(): Boolean = false
    
    override fun getProviderName(): String = "Vision AI"
    
    override fun getMaxTokens(): Int = 4096
}
```

## 10.2 Vision Use Cases

```kotlin
// ai/VisionUseCases.kt
package com.personalassistant.ai

import android.graphics.Bitmap
import com.personalassistant.model.AssistantResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionUseCases @Inject constructor(
    private val visionAIProvider: VisionAIProvider
) {

    /**
     * Describe what's in an image
     */
    suspend fun describeImage(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "Describe what you see in this image in detail.")
    }

    /**
     * Read text from image (OCR)
     */
    suspend fun readTextFromImage(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "Read and transcribe all text visible in this image.")
    }

    /**
     * Identify objects in image
     */
    suspend fun identifyObjects(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "List all objects you can identify in this image.")
    }

    /**
     * Analyze document/receipt
     */
    suspend fun analyzeDocument(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "Analyze this document. Extract key information like dates, amounts, names, and important details.")
    }

    /**
     * Scan QR code / barcode
     */
    suspend fun scanCode(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "Is there a QR code or barcode in this image? If yes, decode it and tell me what it contains.")
    }

    /**
     * Analyze screenshot for UI help
     */
    suspend fun analyzeScreenshot(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "This is a screenshot of an app interface. What screen is this? What can the user do here? Help them navigate.")
    }

    /**
     * Check expiration date
     */
    suspend fun checkExpirationDate(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "Is there an expiration date or best-before date on this product? Read it and tell me if it's still good.")
    }

    /**
     * Identify plant/animal
     */
    suspend fun identifySpecies(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "What plant or animal is this? Provide scientific name and common name if possible.")
    }

    /**
     * Analyze food/nutrition
     */
    suspend fun analyzeFood(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "What food is this? Estimate calories and nutritional information.")
    }

    /**
     * Read medication label
     */
    suspend fun readMedicationLabel(bitmap: Bitmap): AssistantResponse {
        return visionAIProvider.analyzeImage(bitmap, "Read this medication label. What's the drug name, dosage, and instructions?")
    }
}
```

## 10.3 Camera Integration

```kotlin
// ui/camera/CameraManager.kt
package com.personalassistant.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.personalassistant.ai.VisionUseCases
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraManager @Inject constructor() {

    private var currentPhotoPath: String? = null

    /**
     * Register camera launcher in Activity
     */
    fun registerCameraLauncher(
        activity: Activity,
        onImageCaptured: (Uri) -> Unit
    ): ActivityResultLauncher<Uri> {
        return activity.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                currentPhotoPath?.let { path ->
                    val uri = Uri.fromFile(File(path))
                    onImageCaptured(uri)
                }
            }
        }
    }

    /**
     * Register gallery launcher
     */
    fun registerGalleryLauncher(
        activity: Activity,
        onImageSelected: (Uri) -> Unit
    ): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { onImageSelected(it) }
        }
    }

    /**
     * Check camera permission
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Create image file for camera
     */
    fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir(null)
        return File.createTempFile(imageFileName, ".jpg", storageDir).also {
            currentPhotoPath = it.absolutePath
        }
    }

    /**
     * Load bitmap from URI
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compress bitmap for API (reduce size)
     */
    fun compressBitmap(bitmap: Bitmap, maxSizeKB: Int = 500): Bitmap {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        
        var quality = 80
        while (outputStream.toByteArray().size / 1024 > maxSizeKB && quality > 10) {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            quality -= 10
        }
        
        return android.graphics.BitmapFactory.decodeByteArray(
            outputStream.toByteArray(),
            0,
            outputStream.size()
        )
    }
}
```

## 10.4 Vision UI Component

```kotlin
// ui/components/VisionInput.kt
package com.personalassistant.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.personalassistant.ui.camera.CameraManager

@Composable
fun VisionInput(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onAnalyzeClick: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImagePreview by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Image preview if selected
        selectedImageUri?.let { uri ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Selected image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    IconButton(
                        onClick = { 
                            selectedImageUri = null 
                            showImagePreview = false 
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.error,
                                RoundedCornerShape(50)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            // Analyze button
            Button(
                onClick = { selectedImageUri?.let { onAnalyzeClick(it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedImageUri != null
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Image")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Camera and Gallery buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCameraClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Camera")
            }

            OutlinedButton(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gallery")
            }
        }
    }
}
```

## 10.5 Vision Quick Actions

```kotlin
// ui/vision/VisionQuickActions.kt
package com.personalassistant.ui.vision

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VisionQuickActions(
    onActionSelected: (VisionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        VisionAction.Describe,
        VisionAction.ReadText,
        VisionAction.IdentifyObjects,
        VisionAction.AnalyzeDocument,
        VisionAction.ScanCode,
        VisionAction.AnalyzeFood
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        modifier = modifier
    ) {
        items(actions.size) { index ->
            VisionActionCard(
                action = actions[index],
                onClick = { onActionSelected(actions[index]) }
            )
        }
    }
}

@Composable
private fun VisionActionCard(
    action: VisionAction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.name,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = action.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

sealed class VisionAction(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Describe : VisionAction("Describe", androidx.compose.material.icons.Icons.Default.Visibility)
    object ReadText : VisionAction("Read Text", androidx.compose.material.icons.Icons.Default.TextFields)
    object IdentifyObjects : VisionAction("Identify", androidx.compose.material.icons.Icons.Default.Search)
    object AnalyzeDocument : VisionAction("Document", androidx.compose.material.icons.Icons.Default.Description)
    object ScanCode : VisionAction("Scan Code", androidx.compose.material.icons.Icons.Default.QrCodeScanner)
    object AnalyzeFood : VisionAction("Food", androidx.compose.material.icons.Icons.Default.LunchDining)
    object CheckExpiration : VisionAction("Expiry", androidx.compose.material.icons.Icons.Default.Event)
    object IdentifyPlant : VisionAction("Plant", androidx.compose.material.icons.Icons.Default.LocalFlorist)
}
```

## 10.6 Vision Settings

```kotlin
// ui/settings/VisionSettingsScreen.kt
package com.personalassistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.data.preferences.AssistantPreferences

@Composable
fun VisionSettingsScreen(
    preferences: AssistantPreferences,
    onUpdatePreferences: (String, Any) -> Unit
) {
    var visionEnabled by remember { mutableStateOf(true) }
    var defaultVisionModel by remember { mutableStateOf("openai/gpt-4-turbo") }
    var autoCompressImages by remember { mutableStateOf(true) }
    var maxImageSizeKB by remember { mutableIntStateOf(500) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Vision Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingToggle(
            title = "Enable Vision",
            subtitle = "Allow image analysis features",
            checked = visionEnabled,
            onCheckedChange = { 
                visionEnabled = it
                onUpdatePreferences("vision_enabled", it)
            }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingToggle(
            title = "Auto-Compress Images",
            subtitle = "Reduce image size before sending ($maxImageSizeKB KB max)",
            checked = autoCompressImages,
            onCheckedChange = { 
                autoCompressImages = it
                onUpdatePreferences("auto_compress_images", it)
            }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        if (autoCompressImages) {
            SliderWithLabel(
                label = "Max Image Size",
                value = maxImageSizeKB.toFloat(),
                onValueChange = { 
                    maxImageSizeKB = it.toInt()
                    onUpdatePreferences("max_image_size_kb", it.toInt())
                },
                valueRange = 100f..1000f
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Vision Model",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = defaultVisionModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Vision Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("GPT-4 Turbo (Recommended)") },
                    onClick = { 
                        defaultVisionModel = "openai/gpt-4-turbo"
                        onUpdatePreferences("vision_model", defaultVisionModel)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Claude 3 Opus") },
                    onClick = { 
                        defaultVisionModel = "anthropic/claude-3-opus"
                        onUpdatePreferences("vision_model", defaultVisionModel)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Gemini Pro Vision") },
                    onClick = { 
                        defaultVisionModel = "google/gemini-pro-vision"
                        onUpdatePreferences("vision_model", defaultVisionModel)
                        expanded = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "⚠️ Privacy Note",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Images are sent to cloud AI servers. Avoid sharing sensitive personal photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
```

## 10.7 Vision Permission Handling

```kotlin
// util/VisionPermissionHandler.kt
package com.personalassistant.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.personalassistant.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionPermissionHandler @Inject constructor() {

    private val _cameraPermissionStatus = MutableStateFlow<PermissionStatus>(PermissionStatus.NOT_REQUESTED)
    val cameraPermissionStatus: StateFlow<PermissionStatus> = _cameraPermissionStatus

    fun checkCameraPermission(context: Context): PermissionStatus {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val status = if (granted) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }

        _cameraPermissionStatus.value = status
        return status
    }

    fun requestCameraPermission(activity: Activity, requestCode: Int = 1003) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            requestCode
        )
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission(activity: Activity, requestCode: Int = 1004) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(permission),
            requestCode
        )
    }
}
```

## 10.8 Vision Activity Result Handler

```kotlin
// ui/vision/VisionResultHandler.kt
package com.personalassistant.ui.vision

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalassistant.model.AssistantResponse

@Composable
fun VisionResultHandler(
    imageUrl: Uri,
    response: AssistantResponse?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Image preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                contentDescription = "Analyzed image",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Response section
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analyzing image...")
                }
            }
        } else {
            response?.let { resp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Analysis Result",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resp.message,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Confidence: ${(resp.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
```

## 10.9 Vision Examples

### Example 1: Scan Receipt
```kotlin
// User takes photo of receipt
val receiptBitmap = cameraManager.loadBitmapFromUri(context, receiptUri)

// Analyze receipt
val result = visionUseCases.analyzeDocument(receiptBitmap)
// Response: "This is a receipt from Store XYZ. Total: $45.99. Date: March 15, 2024..."
```

### Example 2: Read Street Sign
```kotlin
// User photographs street sign
val signBitmap = cameraManager.loadBitmapFromUri(context, signUri)

// Extract text
val result = visionUseCases.readTextFromImage(signBitmap)
// Response: "STOP - All Way - 4 Way Intersection"
```

### Example 3: Identify Plant
```kotlin
// User photographs unknown plant
val plantBitmap = cameraManager.loadBitmapFromUri(context, plantUri)

// Identify species
val result = visionUseCases.identifySpecies(plantBitmap)
// Response: "This is Monstera deliciosa (Swiss Cheese Plant). It's a tropical..."
```

### Example 4: Check Expiration
```kotlin
// User photographs food product
val productBitmap = cameraManager.loadBitmapFromUri(context, productUri)

// Check expiry
val result = visionUseCases.checkExpirationDate(productBitmap)
// Response: "The expiration date is 04/2024. This product is still good."
```

## 10.10 Vision Model Comparison

| Model | Accuracy | Speed | Cost | Best For |
|-------|----------|-------|------|----------|
| GPT-4 Turbo | ⭐⭐⭐⭐⭐ | Fast | $$ | General vision |
| Claude 3 Opus | ⭐⭐⭐⭐⭐ | Medium | $$$ | Detailed analysis |
| Gemini Pro Vision | ⭐⭐⭐⭐ | Fast | Free | Basic tasks |
| Llava 1.5 | ⭐⭐⭐ | Medium | Free | Open-source |

---

## Summary

Vision capabilities enable your assistant to:
✅ **See images** from camera or gallery  
✅ **Describe scenes** in detail  
✅ **Read text** (OCR) from any image  
✅ **Identify objects**, plants, animals  
✅ **Analyze documents**, receipts, forms  
✅ **Scan QR codes** and barcodes  
✅ **Check expiration dates**  
✅ **Analyze food** and nutrition  

### Quick Start
1. Enable vision in settings
2. Grant camera permission
3. Capture or select image
4. Choose analysis type
5. Get AI-powered insights

---

**Previous**: [OpenRouter Integration](./09-openrouter-integration.md)
