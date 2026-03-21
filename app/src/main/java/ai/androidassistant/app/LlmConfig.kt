package ai.androidassistant.app

enum class LlmMode(val rawValue: String) {
  Local("local"),
  Cloud("cloud"),
  ;

  companion object {
    fun fromRawValue(raw: String?): LlmMode {
      return entries.firstOrNull { it.rawValue == raw } ?: Local
    }
  }
}

enum class CloudProvider(
  val id: String,
  val label: String,
  val baseUrl: String,
  val defaultModel: String,
  val openAiCompatible: Boolean,
) {
  OpenAI(
    id = "openai",
    label = "OpenAI",
    baseUrl = "https://api.openai.com",
    defaultModel = "gpt-4o-mini",
    openAiCompatible = true,
  ),
  Anthropic(
    id = "anthropic",
    label = "Anthropic",
    baseUrl = "https://api.anthropic.com",
    defaultModel = "claude-3-5-sonnet-20240620",
    openAiCompatible = false,
  ),
  Groq(
    id = "groq",
    label = "Groq",
    baseUrl = "https://api.groq.com/openai",
    defaultModel = "llama-3.3-70b-versatile",
    openAiCompatible = true,
  ),
  OpenRouter(
    id = "openrouter",
    label = "OpenRouter",
    baseUrl = "https://openrouter.ai/api",
    defaultModel = "meta-llama/llama-3.1-8b-instruct:free",
    openAiCompatible = true,
  ),
  ElevenLabs(
    id = "elevenlabs",
    label = "ElevenLabs",
    baseUrl = "https://api.elevenlabs.io",
    defaultModel = "elevenlabs/auto",
    openAiCompatible = true,
  ),
  ;

  companion object {
    fun fromId(raw: String?): CloudProvider {
      return entries.firstOrNull { it.id == raw } ?: OpenRouter
    }
  }
}
