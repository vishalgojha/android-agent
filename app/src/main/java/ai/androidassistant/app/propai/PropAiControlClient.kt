package ai.androidassistant.app.propai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class PropAiControlClient(
  private val httpClient: OkHttpClient = defaultHttpClient(),
) {
  suspend fun login(
    baseUrl: String,
    email: String,
    password: String,
  ): Result<PropAiAuthResult> {
    val url = "${normalizeBaseUrl(baseUrl)}/v1/auth/login"
    val payload =
      JSONObject()
        .put("email", email.trim())
        .put("password", password)
        .toString()
    return postJson(url, payload)
  }

  suspend fun register(
    baseUrl: String,
    email: String,
    password: String,
    tenantName: String,
  ): Result<PropAiAuthResult> {
    val url = "${normalizeBaseUrl(baseUrl)}/v1/auth/register"
    val payload =
      JSONObject()
        .put("email", email.trim())
        .put("password", password)
        .put("tenantName", tenantName.trim())
        .toString()
    return postJson(url, payload)
  }

  suspend fun me(
    baseUrl: String,
    token: String,
  ): Result<PropAiAuthResult> {
    val url = "${normalizeBaseUrl(baseUrl)}/v1/me"
    return withContext(Dispatchers.IO) {
      runCatching {
        val request =
          Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer ${token.trim()}")
            .build()

        httpClient.newCall(request).execute().use { response ->
          val bodyText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val message = parseErrorMessage(bodyText)
            error("Control API request failed (${response.code}): $message")
          }
          parseAuthResponse(bodyText)
        }
      }
    }
  }

  suspend fun listWhatsappMessages(
    baseUrl: String,
    token: String,
    limit: Int,
    withinMs: Long,
  ): Result<List<PropAiWhatsappMessage>> {
    val url =
      "${normalizeBaseUrl(baseUrl)}/v1/whatsapp/messages?limit=$limit&withinMs=$withinMs"
    return withContext(Dispatchers.IO) {
      runCatching {
        val request =
          Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
          val bodyText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val message = parseErrorMessage(bodyText)
            error("Control API request failed (${response.code}): $message")
          }
          parseWhatsappMessages(bodyText)
        }
      }
    }
  }

  suspend fun sendWhatsappMessage(
    baseUrl: String,
    token: String,
    chatId: String,
    message: String,
  ): Result<Unit> {
    val url = "${normalizeBaseUrl(baseUrl)}/v1/whatsapp/send"
    val payload =
      JSONObject()
        .put("chatId", chatId.trim())
        .put("text", message)
        .toString()
    return withContext(Dispatchers.IO) {
      runCatching {
        val request =
          Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
          val bodyText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val message = parseErrorMessage(bodyText)
            error("Control API request failed (${response.code}): $message")
          }
        }
      }
    }
  }

  private suspend fun postJson(url: String, payload: String): Result<PropAiAuthResult> {
    return withContext(Dispatchers.IO) {
      runCatching {
        val request =
          Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
          val bodyText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val message = parseErrorMessage(bodyText)
            error("Control API request failed (${response.code}): $message")
          }
          parseAuthResponse(bodyText)
        }
      }
    }
  }

  private fun parseAuthResponse(bodyText: String): PropAiAuthResult {
    val json = JSONObject(bodyText)
    val token = json.optString("token", "")
    val userJson = json.optJSONObject("user")
    val user =
      if (userJson != null) {
        PropAiUser(
          id = userJson.optString("id", ""),
          email = userJson.optString("email", ""),
        )
      } else {
        null
      }

    val tenants = mutableListOf<PropAiTenant>()
    val tenantsArray = json.optJSONArray("tenants")
    if (tenantsArray != null) {
      tenants += parseTenantArray(tenantsArray)
    } else {
      json.optJSONObject("tenant")?.let { tenantJson ->
        parseTenant(tenantJson)?.let { tenants += it }
      }
    }

    return PropAiAuthResult(
      token = token,
      user = user,
      tenants = tenants,
    )
  }

  private fun parseWhatsappMessages(bodyText: String): List<PropAiWhatsappMessage> {
    return runCatching {
        val root = JSONObject(bodyText)
        val array = root.optJSONArray("messages") ?: JSONArray()
        parseWhatsappMessageArray(array)
      }
      .getOrElse {
        runCatching {
          val array = JSONArray(bodyText)
          parseWhatsappMessageArray(array)
        }.getOrElse { emptyList() }
      }
  }

  private fun parseWhatsappMessageArray(array: JSONArray): List<PropAiWhatsappMessage> {
    val messages = mutableListOf<PropAiWhatsappMessage>()
    for (i in 0 until array.length()) {
      val item = array.optJSONObject(i) ?: continue
      parseWhatsappMessage(item)?.let { messages += it }
    }
    return messages
  }

  private fun parseWhatsappMessage(json: JSONObject): PropAiWhatsappMessage? {
    val id =
      json.optString("id", json.optString("messageId", "")).trim()
    val text =
      json.optString("text", json.optString("body", "")).trim()
    val chatId =
      json.optString("chatId", json.optString("threadId", "")).trim().ifEmpty { null }
    val sender =
      json.optString("sender", json.optString("from", "")).trim().ifEmpty { null }
    val fromMe = json.optBoolean("fromMe", false)
    val timestampMs = parseTimestampMs(json)

    if (id.isEmpty() && text.isEmpty()) return null

    return PropAiWhatsappMessage(
      id = id.ifEmpty { UUID.randomUUID().toString() },
      chatId = chatId,
      sender = sender,
      text = text,
      timestampMs = timestampMs,
      fromMe = fromMe,
    )
  }

  private fun parseTimestampMs(json: JSONObject): Long {
    val raw = json.opt("timestampMs") ?: json.opt("timestamp") ?: json.opt("time")
    val parsed =
      when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
      }
    return if (parsed in 1..9_999_999_999L) parsed * 1000L else parsed
  }

  private fun parseTenantArray(array: JSONArray): List<PropAiTenant> {
    val tenants = mutableListOf<PropAiTenant>()
    for (i in 0 until array.length()) {
      val tenantJson = array.optJSONObject(i) ?: continue
      parseTenant(tenantJson)?.let { tenants += it }
    }
    return tenants
  }

  private fun parseTenant(json: JSONObject): PropAiTenant? {
    val id = json.optString("id", "").trim()
    val name = json.optString("name", "").trim()
    if (id.isEmpty() && name.isEmpty()) return null
    val role = json.optString("role", "").trim()
    return PropAiTenant(id = id, name = name, role = role)
  }

  private fun parseErrorMessage(bodyText: String): String {
    return runCatching {
        val json = JSONObject(bodyText)
        json.optString("error").ifBlank { bodyText }
      }
      .getOrElse { bodyText }
  }

  private fun normalizeBaseUrl(raw: String): String {
    return raw.trim().trimEnd('/')
  }

  companion object {
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun defaultHttpClient(): OkHttpClient {
      return OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()
    }
  }
}
