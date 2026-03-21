package ai.androidassistant.app.cloud

import ai.androidassistant.app.CloudProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CloudChatMessage(
  val role: String,
  val content: String,
)

class CloudLlmClient(
  private val httpClient: OkHttpClient = defaultHttpClient(),
) {
  suspend fun generateResponse(
    provider: CloudProvider,
    apiKey: String,
    model: String,
    messages: List<CloudChatMessage>,
    systemPrompt: String,
  ): Result<String> {
    if (apiKey.isBlank()) {
      return Result.failure(IllegalStateException("Missing API key"))
    }
    if (model.isBlank()) {
      return Result.failure(IllegalStateException("Missing model name"))
    }

    return withContext(Dispatchers.IO) {
      runCatching {
        val (url, payload, headers) = buildRequest(provider, apiKey, model, messages, systemPrompt)
        val request =
          Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA))
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()

        httpClient.newCall(request).execute().use { response ->
          val bodyText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val message = parseErrorMessage(bodyText)
            error("Cloud request failed (${response.code}): $message")
          }
          val answer = parseResponse(provider, bodyText)
          if (answer.isBlank()) {
            error("Cloud response was empty")
          }
          answer
        }
      }
    }
  }


  private fun buildRequest(
    provider: CloudProvider,
    apiKey: String,
    model: String,
    messages: List<CloudChatMessage>,
    systemPrompt: String,
  ): Triple<String, String, Map<String, String>> {
    return if (provider.openAiCompatible) {
      val url = "${provider.baseUrl}/v1/chat/completions"
      val payloadMessages = buildMessagesArray(systemPrompt, messages)
      val payload =
        JSONObject()
          .put("model", model)
          .put("messages", payloadMessages)
          .put("temperature", 0.7)
          .put("max_tokens", 256)
          .toString()

      val headers =
        buildMap<String, String> {
          put("Authorization", "Bearer $apiKey")
          put("Accept", "application/json")
          if (provider == CloudProvider.OpenRouter) {
            put("HTTP-Referer", "https://propai-sync.local")
            put("X-Title", "PropAi Sync")
          }
          if (provider == CloudProvider.ElevenLabs) {
            put("xi-api-key", apiKey)
          }
        }
      Triple(url, payload, headers)
    } else {
      val url = "${provider.baseUrl}/v1/messages"
      val payloadMessages = buildMessagesArray(null, messages)
      val payload =
        JSONObject()
          .put("model", model)
          .put("max_tokens", 256)
          .put("temperature", 0.7)
          .put("system", systemPrompt)
          .put("messages", payloadMessages)
          .toString()

      val headers =
        mapOf(
          "x-api-key" to apiKey,
          "anthropic-version" to "2023-06-01",
          "Accept" to "application/json",
        )
      Triple(url, payload, headers)
    }
  }

  private fun parseResponse(provider: CloudProvider, bodyText: String): String {
    val json = JSONObject(bodyText)
    return if (provider.openAiCompatible) {
      val choices = json.optJSONArray("choices") ?: JSONArray()
      val message = choices.optJSONObject(0)?.optJSONObject("message")
      val content = message?.opt("content")
      when (content) {
        is String -> content.trim()
        is JSONArray -> parseContentArray(content)
        else -> ""
      }
    } else {
      val content = json.optJSONArray("content") ?: JSONArray()
      parseContentArray(content)
    }
  }

  private fun parseContentArray(array: JSONArray): String {
    val builder = StringBuilder()
    for (i in 0 until array.length()) {
      val item = array.optJSONObject(i) ?: continue
      val text = item.optString("text")
      if (text.isNotBlank()) {
        builder.append(text)
      }
    }
    return builder.toString().trim()
  }

  private fun parseErrorMessage(bodyText: String): String {
    return runCatching {
        val json = JSONObject(bodyText)
        json.optJSONObject("error")?.optString("message")?.ifBlank { bodyText } ?: bodyText
      }
      .getOrElse { bodyText }
  }

  companion object {
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun defaultHttpClient(): OkHttpClient {
      return OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(70, TimeUnit.SECONDS)
        .writeTimeout(70, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()
    }
  }

  private fun buildMessagesArray(
    systemPrompt: String?,
    messages: List<CloudChatMessage>,
  ): JSONArray {
    val payloadMessages = JSONArray()
    if (!systemPrompt.isNullOrBlank()) {
      payloadMessages.put(JSONObject().put("role", "system").put("content", systemPrompt))
    }
    messages.forEach { message ->
      val content = message.content.trim()
      if (content.isNotEmpty()) {
        payloadMessages.put(JSONObject().put("role", message.role).put("content", content))
      }
    }
    return payloadMessages
  }

}
