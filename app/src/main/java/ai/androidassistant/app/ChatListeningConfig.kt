package ai.androidassistant.app

data class ChatSourcePreset(
  val label: String,
  val packageName: String,
)

val knownChatSourcePresets: List<ChatSourcePreset> =
  listOf(
    ChatSourcePreset(label = "WhatsApp", packageName = "com.whatsapp"),
    ChatSourcePreset(label = "WhatsApp Business", packageName = "com.whatsapp.w4b"),
    ChatSourcePreset(label = "Google Messages", packageName = "com.google.android.apps.messaging"),
    ChatSourcePreset(label = "Telegram", packageName = "org.telegram.messenger"),
    ChatSourcePreset(label = "Signal", packageName = "org.thoughtcrime.securesms"),
    ChatSourcePreset(label = "Messenger", packageName = "com.facebook.orca"),
    ChatSourcePreset(label = "Discord", packageName = "com.discord"),
    ChatSourcePreset(label = "Slack", packageName = "com.Slack"),
  )

private val packageNamePattern = Regex("""^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$""")

fun parseListInput(raw: String): List<String> =
  raw
    .split(',', '\n')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

fun sanitizeChatListeningPackages(values: List<String>): List<String> =
  values
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { candidate ->
      if (candidate.contains('.') && candidate == candidate.lowercase()) {
        candidate
      } else {
        knownChatSourcePresets.firstOrNull { it.packageName.equals(candidate, ignoreCase = true) }?.packageName
          ?: candidate
      }
    }
    .filter { packageNamePattern.matches(it) }
    .distinct()
    .sorted()

fun sanitizeChatListeningConversationFilters(values: List<String>): List<String> =
  values
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .sortedBy { it.lowercase() }

fun isKnownChatPackage(packageName: String): Boolean =
  knownChatSourcePresets.any { it.packageName.equals(packageName, ignoreCase = true) }

fun isPassiveChatListeningEnabledForNotification(
  packageName: String,
  title: String?,
  text: String?,
  selectedPackages: List<String>,
  conversationFilters: List<String>,
): Boolean {
  val normalizedPackage = packageName.trim()
  if (normalizedPackage.isEmpty()) return false

  val normalizedSelectedPackages =
    sanitizeChatListeningPackages(selectedPackages).map { it.lowercase() }.toSet()
  if (normalizedSelectedPackages.isEmpty()) return false
  if (normalizedPackage.lowercase() !in normalizedSelectedPackages) return false

  val normalizedFilters =
    sanitizeChatListeningConversationFilters(conversationFilters).map { it.lowercase() }
  if (normalizedFilters.isEmpty()) return true

  val haystacks =
    listOfNotNull(title?.trim(), text?.trim())
      .filter { it.isNotEmpty() }
      .map { it.lowercase() }
  if (haystacks.isEmpty()) return false

  return normalizedFilters.any { filter -> haystacks.any { it.contains(filter) } }
}
