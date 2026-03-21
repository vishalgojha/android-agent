package ai.androidassistant.app.chat

import android.content.Context
import java.io.File
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatMemoryStore(
  context: Context,
  private val json: Json,
) {
  private val dir = File(context.filesDir, "chat_memory")
  private val sessionsFile = File(dir, "sessions.json")

  fun loadSessions(): List<ChatSessionEntry> {
    ensureDir()
    if (!sessionsFile.exists()) {
      return listOf(defaultMainSession())
    }
    val contents = sessionsFile.readText().trim()
    if (contents.isEmpty()) {
      return listOf(defaultMainSession())
    }
    return try {
      val stored = json.decodeFromString<List<StoredSession>>(contents)
      stored.map { it.toEntry() }.ifEmpty { listOf(defaultMainSession()) }
    } catch (_: Throwable) {
      listOf(defaultMainSession())
    }
  }

  fun loadSession(sessionKey: String): List<ChatMessage> {
    ensureDir()
    val file = sessionFile(sessionKey)
    if (!file.exists()) return emptyList()
    val contents = file.readText().trim()
    if (contents.isEmpty()) return emptyList()
    return try {
      val history = json.decodeFromString<StoredHistory>(contents)
      history.messages.map { it.toMessage() }
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveSession(
    sessionKey: String,
    messages: List<ChatMessage>,
    displayName: String?,
    maxMessages: Int = 200,
  ) {
    ensureDir()
    val trimmedKey = sessionKey.trim().ifEmpty { "main" }
    val now = System.currentTimeMillis()
    val updated =
      loadSessions()
        .filterNot { it.key == trimmedKey }
        .toMutableList()
        .apply {
          add(ChatSessionEntry(key = trimmedKey, updatedAtMs = now, displayName = displayName))
        }
        .sortedByDescending { it.updatedAtMs ?: 0L }
    writeSessions(updated)

    val prunedMessages =
      if (messages.size > maxMessages) {
        messages.takeLast(maxMessages)
      } else {
        messages
      }
    val storedHistory =
      StoredHistory(
        sessionKey = trimmedKey,
        messages = prunedMessages.map { it.toStored() },
      )
    writeSessionFile(trimmedKey, storedHistory)
  }

  private fun defaultMainSession(): ChatSessionEntry {
    return ChatSessionEntry(key = "main", updatedAtMs = System.currentTimeMillis(), displayName = "Main")
  }

  private fun ensureDir() {
    if (!dir.exists()) {
      dir.mkdirs()
    }
  }

  private fun writeSessions(sessions: List<ChatSessionEntry>) {
    val stored = sessions.map { StoredSession.fromEntry(it) }
    val payload = json.encodeToString(stored)
    writeAtomically(sessionsFile, payload)
  }

  private fun writeSessionFile(sessionKey: String, history: StoredHistory) {
    val file = sessionFile(sessionKey)
    val payload = json.encodeToString(history)
    writeAtomically(file, payload)
  }

  private fun sessionFile(sessionKey: String): File {
    val safeKey =
      sessionKey.trim().ifEmpty { "main" }
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9_-]"), "_")
    return File(dir, "session_$safeKey.json")
  }

  private fun writeAtomically(file: File, contents: String) {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(contents)
    if (file.exists()) {
      file.delete()
    }
    tmp.renameTo(file)
  }
}

@Serializable
private data class StoredHistory(
  val sessionKey: String,
  val messages: List<StoredMessage>,
)

@Serializable
private data class StoredSession(
  val key: String,
  val updatedAtMs: Long?,
  val displayName: String? = null,
) {
  fun toEntry(): ChatSessionEntry = ChatSessionEntry(key = key, updatedAtMs = updatedAtMs, displayName = displayName)

  companion object {
    fun fromEntry(entry: ChatSessionEntry): StoredSession =
      StoredSession(
        key = entry.key,
        updatedAtMs = entry.updatedAtMs,
        displayName = entry.displayName,
      )
  }
}

@Serializable
private data class StoredMessage(
  val id: String,
  val role: String,
  val content: List<StoredContent>,
  val timestampMs: Long? = null,
) {
  fun toMessage(): ChatMessage =
    ChatMessage(
      id = id,
      role = role,
      content = content.map { it.toContent() },
      timestampMs = timestampMs,
    )
}

@Serializable
private data class StoredContent(
  val type: String = "text",
  val text: String? = null,
  val mimeType: String? = null,
  val fileName: String? = null,
  val base64: String? = null,
) {
  fun toContent(): ChatMessageContent =
    ChatMessageContent(
      type = type,
      text = text,
      mimeType = mimeType,
      fileName = fileName,
      base64 = base64,
    )
}

private fun ChatMessage.toStored(): StoredMessage =
  StoredMessage(
    id = id,
    role = role,
    content =
      content.map { item ->
        StoredContent(
          type = item.type,
          text = item.text,
          mimeType = item.mimeType,
          fileName = item.fileName,
          // Avoid persisting large binary payloads for attachments.
          base64 = if (item.type == "text") item.base64 else null,
        )
      },
    timestampMs = timestampMs,
  )
