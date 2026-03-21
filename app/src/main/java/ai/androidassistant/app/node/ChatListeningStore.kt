@file:Suppress("DEPRECATION")

package ai.androidassistant.app.node

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CHAT_LISTENING_PREFS = "androidassistant.chatlistening.secure"
private const val CHAT_LISTENING_MESSAGES_KEY = "chat.listening.messages"
private const val MAX_STORED_MESSAGES = 300
private const val MAX_AGE_MS = 1000L * 60L * 60L * 48L

@Serializable
data class ChatListeningMessage(
  val packageName: String,
  val sender: String?,
  val conversation: String?,
  val text: String,
  val timestampMs: Long,
)

class ChatListeningStore(context: Context) {
  private val appContext = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true }
  private val lock = Any()

  private val prefs by lazy {
    val masterKey =
      MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
      appContext,
      CHAT_LISTENING_PREFS,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  @Volatile private var cached: MutableList<ChatListeningMessage>? = null

  fun addMessage(message: ChatListeningMessage) {
    val text = message.text.trim()
    if (text.isEmpty()) return
    val safeMessage = message.copy(text = text)
    synchronized(lock) {
      val items = loadUnlocked()
      items.add(safeMessage)
      val pruned = pruneUnlocked(items)
      saveUnlocked(pruned)
    }
  }

  fun recentMessages(
    limit: Int,
    withinMs: Long? = null,
    packageFilters: List<String> = emptyList(),
  ): List<ChatListeningMessage> {
    val normalizedFilters = packageFilters.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    val now = System.currentTimeMillis()
    val cutoff = withinMs?.let { now - it } ?: Long.MIN_VALUE
    return synchronized(lock) {
      val items = loadUnlocked()
      val pruned = pruneUnlocked(items)
      if (pruned !== items) {
        saveUnlocked(pruned)
      }
      pruned
        .asSequence()
        .filter { it.timestampMs >= cutoff }
        .filter { entry ->
          if (normalizedFilters.isEmpty()) true
          else normalizedFilters.any { filter -> entry.packageName.lowercase().contains(filter) }
        }
        .sortedByDescending { it.timestampMs }
        .take(limit)
        .toList()
    }
  }

  private fun loadUnlocked(): MutableList<ChatListeningMessage> {
    cached?.let { return it }
    val raw = prefs.getString(CHAT_LISTENING_MESSAGES_KEY, null)
    val decoded =
      runCatching {
        if (raw.isNullOrBlank()) emptyList()
        else json.decodeFromString<List<ChatListeningMessage>>(raw)
      }.getOrElse { emptyList() }
    val mutable = decoded.toMutableList()
    cached = mutable
    return mutable
  }

  private fun saveUnlocked(messages: MutableList<ChatListeningMessage>) {
    cached = messages
    val encoded = json.encodeToString(messages)
    prefs.edit { putString(CHAT_LISTENING_MESSAGES_KEY, encoded) }
  }

  private fun pruneUnlocked(messages: MutableList<ChatListeningMessage>): MutableList<ChatListeningMessage> {
    val cutoff = System.currentTimeMillis() - MAX_AGE_MS
    val filtered = messages.filter { it.timestampMs >= cutoff }
    val trimmed =
      if (filtered.size > MAX_STORED_MESSAGES) {
        filtered
          .sortedByDescending { it.timestampMs }
          .take(MAX_STORED_MESSAGES)
      } else {
        filtered
      }
    if (trimmed.size == messages.size && trimmed == messages) {
      return messages
    }
    return trimmed.toMutableList()
  }
}
