package cc.ykai.tinychat.data

data class Conversation(
  val id: Long,
  val title: String,
  val createdAt: Long,
  val updatedAt: Long,
)

data class ChatMessage(
  val id: Long,
  val conversationId: Long,
  val role: MessageRole,
  val content: String,
  val createdAt: Long,
  val images: List<MessageImage> = emptyList(),
)

data class MessageImage(val source: String, val mimeType: String)

fun isImageModel(model: String): Boolean =
  model.contains("image", ignoreCase = true) || model.contains("dall-e", ignoreCase = true)

enum class MessageRole(val wireName: String) {
  User("user"),
  Assistant("assistant");

  companion object {
    fun fromWireName(value: String): MessageRole = entries.first { it.wireName == value }
  }
}

data class ApiConfig(
  val baseUrl: String = "https://api.openai.com/v1",
  val apiKey: String = "",
  val model: String = "",
  val imageModel: String = "",
  val systemPrompt: String = "",
) {
  val isReady: Boolean
    get() =
      (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) && model.isNotBlank()
}
