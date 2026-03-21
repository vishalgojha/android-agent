package ai.androidassistant.app.propai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PropAiLicenseClient(
  private val httpClient: OkHttpClient = defaultHttpClient(),
) {
  suspend fun activate(
    baseUrl: String,
    activationKey: String,
    deviceId: String,
    appVersion: String?,
    deviceName: String?,
  ): Result<PropAiLicenseStatus> {
    val url = "${normalizeBaseUrl(baseUrl)}/v1/activations/activate"
    val payload = buildPayload(
      key = activationKey,
      deviceId = deviceId,
      appVersion = appVersion,
      deviceName = deviceName,
      activationToken = null,
    )
    return postStatus(url, payload)
  }

  suspend fun refresh(
    baseUrl: String,
    activationToken: String,
    deviceId: String,
    appVersion: String?,
    deviceName: String?,
  ): Result<PropAiLicenseStatus> {
    val url = "${normalizeBaseUrl(baseUrl)}/v1/activations/refresh"
    val payload = buildPayload(
      key = null,
      deviceId = deviceId,
      appVersion = appVersion,
      deviceName = deviceName,
      activationToken = activationToken,
    )
    return postStatus(url, payload)
  }

  private suspend fun postStatus(url: String, payload: String): Result<PropAiLicenseStatus> {
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
          val parsed = parseLicenseStatus(bodyText)
          if (parsed != null) return@use parsed
          if (!response.isSuccessful) {
            error("License request failed (${response.code})")
          }
          error("Invalid license response")
        }
      }
    }
  }

  private fun buildPayload(
    key: String?,
    deviceId: String,
    appVersion: String?,
    deviceName: String?,
    activationToken: String?,
  ): String {
    val payload =
      JSONObject()
        .put("deviceId", deviceId)
        .put("appVersion", appVersion)

    if (!activationToken.isNullOrBlank()) {
      payload.put("activationToken", activationToken.trim())
    } else {
      payload.put("token", key?.trim().orEmpty())
    }

    val client =
      JSONObject()
        .put("platform", "android")
        .put("deviceName", deviceName?.trim().orEmpty())
    payload.put("client", client)

    return payload.toString()
  }

  private fun parseLicenseStatus(bodyText: String): PropAiLicenseStatus? {
    return runCatching {
        val json = JSONObject(bodyText)
        val entitlements = parseEntitlements(json.optJSONArray("entitlements"))
        PropAiLicenseStatus(
          valid = json.optBoolean("valid", false),
          status = json.optString("status").ifBlank { null },
          plan = json.optString("plan").ifBlank { null },
          entitlements = entitlements,
          expiresAt = json.optString("expiresAt").ifBlank { null },
          graceUntil = json.optString("graceUntil").ifBlank { null },
          refreshAt = json.optString("refreshAt").ifBlank { null },
          deviceLimit = json.optInt("deviceLimit").takeIf { it > 0 },
          devicesUsed = json.optInt("devicesUsed").takeIf { it >= 0 },
          activationToken = json.optString("activationToken").ifBlank { null },
          message = json.optString("message").ifBlank { null },
          code = json.optString("code").ifBlank { null },
          lastValidatedAt = json.optString("lastValidatedAt").ifBlank { null },
        )
      }
      .getOrNull()
  }

  private fun parseEntitlements(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val entitlements = mutableListOf<String>()
    for (i in 0 until array.length()) {
      val entry = array.optString(i).trim()
      if (entry.isNotEmpty()) entitlements += entry
    }
    return entitlements
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
