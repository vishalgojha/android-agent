package ai.androidassistant.app.propai

data class PropAiTenant(
  val id: String,
  val name: String,
  val role: String,
)

data class PropAiUser(
  val id: String,
  val email: String,
)

data class PropAiAuthResult(
  val token: String,
  val user: PropAiUser?,
  val tenants: List<PropAiTenant>,
)

data class PropAiLicenseStatus(
  val valid: Boolean,
  val status: String?,
  val plan: String?,
  val entitlements: List<String>,
  val expiresAt: String?,
  val graceUntil: String?,
  val refreshAt: String?,
  val deviceLimit: Int?,
  val devicesUsed: Int?,
  val activationToken: String?,
  val message: String?,
  val code: String?,
  val lastValidatedAt: String?,
)

data class PropAiWhatsappMessage(
  val id: String,
  val chatId: String?,
  val sender: String?,
  val text: String,
  val timestampMs: Long,
  val fromMe: Boolean,
)
