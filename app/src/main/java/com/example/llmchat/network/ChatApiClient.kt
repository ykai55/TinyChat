package com.example.llmchat.network

import com.example.llmchat.data.ApiConfig
import com.example.llmchat.data.ChatMessage
import com.example.llmchat.data.MessageRole
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatApiClient(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.MINUTES)
      .build()
) {
  fun streamReply(
    config: ApiConfig,
    history: List<ChatMessage>,
  ): Flow<ChatStreamEvent> =
    flow {
        val messages =
          buildList {
            if (config.systemPrompt.isNotBlank()) {
              add(ApiMessage(role = "system", content = JsonPrimitive(config.systemPrompt)))
            }
            history.forEach { add(it.toApiMessage()) }
          }
        val body =
          json.encodeToString(
            ChatRequest(
              model = config.model,
              messages = messages,
              tools = if (config.imageModel.isNotBlank()) imageGenerationTools else null,
              toolChoice = if (config.imageModel.isNotBlank()) "auto" else null,
            )
          )
        val requestBuilder =
          Request.Builder()
            .url(resolveChatEndpoint(config.baseUrl))
            .header("Accept", "text/event-stream, application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        addAuthorization(requestBuilder, config)

        val call = client.newCall(requestBuilder.build())
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
          call.execute().use { response ->
            val responseBody = response.body ?: throw LlmApiException("服务返回了空响应")
            if (!response.isSuccessful) {
              val details = errorMessage(responseBody.string())
              throw LlmApiException("请求失败 (${response.code})${details?.let { ": $it" }.orEmpty()}")
            }

            if (response.header("Content-Type").orEmpty().contains("text/event-stream")) {
              val source = responseBody.source()
              while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                parseChatEvents(payload).forEach { emit(it) }
              }
            } else {
              parseChatEvents(responseBody.string()).forEach { emit(it) }
            }
          }
        } catch (error: IOException) {
          currentCoroutineContext().ensureActive()
          throw LlmApiException("网络连接失败: ${error.message.orEmpty()}", error)
        } finally {
          cancellation.dispose()
        }
      }
      .flowOn(Dispatchers.IO)

  suspend fun listModels(config: ApiConfig): List<String> =
    withContext(Dispatchers.IO) {
      val requestBuilder = Request.Builder().url(resolveModelsEndpoint(config.baseUrl)).get()
      addAuthorization(requestBuilder, config)
      val call = client.newCall(requestBuilder.build())
      val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
      try {
        call.execute().use { response ->
          val responseBody = response.body ?: throw LlmApiException("服务返回了空响应")
          val payload = responseBody.string()
          if (!response.isSuccessful) {
            val details = errorMessage(payload)
            throw LlmApiException("获取模型失败 (${response.code})${details?.let { ": $it" }.orEmpty()}")
          }
          parseModelsPayload(payload)
        }
      } catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        throw LlmApiException("网络连接失败: ${error.message.orEmpty()}", error)
      } finally {
        cancellation.dispose()
      }
    }

  suspend fun generateImage(config: ApiConfig, prompt: String): List<GeneratedImage> =
    withContext(Dispatchers.IO) {
      if (config.imageModel.isBlank()) throw LlmApiException("请先配置图片生成模型")
      val body =
        json.encodeToString(
          ImageGenerationRequest(
            model = config.imageModel,
            prompt = prompt,
          )
        )
      val requestBuilder =
        Request.Builder()
          .url(resolveImagesEndpoint(config.baseUrl))
          .post(body.toRequestBody(JSON_MEDIA_TYPE))
      addAuthorization(requestBuilder, config)
      val call = client.newCall(requestBuilder.build())
      val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
      try {
        call.execute().use { response ->
          val responseBody = response.body ?: throw LlmApiException("图片服务返回了空响应")
          val payload = responseBody.string()
          if (!response.isSuccessful) {
            val details = errorMessage(payload)
            throw LlmApiException("生成图片失败 (${response.code})${details?.let { ": $it" }.orEmpty()}")
          }
          parseGeneratedImages(payload)
        }
      } catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        throw LlmApiException("图片生成连接失败: ${error.message.orEmpty()}", error)
      } finally {
        cancellation.dispose()
      }
    }

  private fun addAuthorization(builder: Request.Builder, config: ApiConfig) {
    if (config.apiKey.isNotBlank()) {
      builder.header("Authorization", "Bearer ${config.apiKey}")
    }
  }

  private companion object {
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }
}

sealed interface ChatStreamEvent {
  data class Text(val value: String) : ChatStreamEvent

  data class ToolCallDelta(
    val index: Int,
    val name: String?,
    val arguments: String,
  ) : ChatStreamEvent

  data class Image(val source: String) : ChatStreamEvent
}

data class GeneratedImage(val source: String, val revisedPrompt: String?)

internal fun resolveChatEndpoint(baseUrl: String): String {
  val normalized = baseUrl.trim().trimEnd('/')
  return if (normalized.endsWith("/chat/completions")) normalized
  else "$normalized/chat/completions"
}

internal fun resolveModelsEndpoint(baseUrl: String): String {
  val normalized = baseUrl.trim().trimEnd('/')
  return when {
    normalized.endsWith("/models") -> normalized
    normalized.endsWith("/chat/completions") ->
      normalized.removeSuffix("/chat/completions") + "/models"
    else -> "$normalized/models"
  }
}

internal fun resolveImagesEndpoint(baseUrl: String): String {
  val normalized = baseUrl.trim().trimEnd('/')
  return when {
    normalized.endsWith("/images/generations") -> normalized
    normalized.endsWith("/chat/completions") ->
      normalized.removeSuffix("/chat/completions") + "/images/generations"
    else -> "$normalized/images/generations"
  }
}

internal fun parseChatPayload(payload: String): String? =
  parseChatEvents(payload).filterIsInstance<ChatStreamEvent.Text>().joinToString("") { it.value }
    .ifEmpty { null }

internal fun parseChatEvents(payload: String): List<ChatStreamEvent> {
  if (payload.isBlank()) return emptyList()
  val response =
    try {
      json.decodeFromString<ChatResponse>(payload)
    } catch (error: Exception) {
      throw LlmApiException("无法解析模型响应", error)
    }
  response.error?.message?.let { throw LlmApiException(it) }
  val choice = response.choices.firstOrNull() ?: return emptyList()
  val content = choice.delta?.content ?: choice.message?.content
  return buildList {
    if (content != null) addAll(content.toStreamEvents())
    val toolCalls = choice.delta?.toolCalls ?: choice.message?.toolCalls.orEmpty()
    toolCalls.forEach { call ->
      add(
        ChatStreamEvent.ToolCallDelta(
          index = call.index,
          name = call.function.name,
          arguments = call.function.arguments.orEmpty(),
        )
      )
    }
  }
}

internal fun parseModelsPayload(payload: String): List<String> {
  val response =
    try {
      json.decodeFromString<ModelsResponse>(payload)
    } catch (error: Exception) {
      throw LlmApiException("无法解析模型列表", error)
    }
  response.error?.message?.let { throw LlmApiException(it) }
  return response.data.map { it.id.trim() }.filter { it.isNotEmpty() }.distinct()
}

internal fun parseGeneratedImages(payload: String): List<GeneratedImage> {
  val response =
    try {
      json.decodeFromString<ImageGenerationResponse>(payload)
    } catch (error: Exception) {
      throw LlmApiException("无法解析图片生成响应", error)
    }
  response.error?.message?.let { throw LlmApiException(it) }
  return response.data.mapNotNull { image ->
    val source = image.url ?: image.base64Json?.let { "data:image/png;base64,$it" }
    source?.let { GeneratedImage(it, image.revisedPrompt) }
  }
}

internal fun parseImageToolPrompt(arguments: String): String {
  return try {
    json.parseToJsonElement(arguments).jsonObject["prompt"]?.jsonPrimitive?.contentOrNull
      ?.takeIf { it.isNotBlank() }
      ?: throw LlmApiException("图片工具没有提供提示词")
  } catch (error: LlmApiException) {
    throw error
  } catch (error: Exception) {
    throw LlmApiException("无法解析图片工具参数", error)
  }
}

private fun ChatMessage.toApiMessage(): ApiMessage {
  val requestContent =
    if (role == MessageRole.User && images.isNotEmpty()) {
      buildJsonArray {
        if (content.isNotBlank()) {
          add(buildJsonObject {
            put("type", "text")
            put("text", content)
          })
        }
        images.forEach { image ->
          val imageUrl =
            when {
              image.source.startsWith("data:") || image.source.startsWith("http://") ||
                image.source.startsWith("https://") -> image.source
              else -> {
                val encoded = Base64.getEncoder().encodeToString(File(image.source).readBytes())
                "data:${image.mimeType};base64,$encoded"
              }
            }
          add(buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject {
              put("url", imageUrl)
              put("detail", "auto")
            })
          })
        }
      }
    } else {
      JsonPrimitive(content)
    }
  return ApiMessage(role = role.wireName, content = requestContent)
}

private fun JsonElement.toStreamEvents(): List<ChatStreamEvent> =
  when (this) {
    is JsonPrimitive -> contentOrNull?.let { listOf(ChatStreamEvent.Text(it)) }.orEmpty()
    is JsonArray -> mapNotNull { item -> item.toStreamEvent() }
    else -> emptyList()
  }

private fun JsonElement.toStreamEvent(): ChatStreamEvent? {
  val item = this as? JsonObject ?: return null
  val type = item["type"]?.jsonPrimitive?.contentOrNull
  if (type == "text" || type == "output_text") {
    val text = item["text"]
    val value =
      when (text) {
        is JsonPrimitive -> text.contentOrNull
        is JsonObject -> text["value"]?.jsonPrimitive?.contentOrNull
        else -> null
      }
    return value?.let { ChatStreamEvent.Text(it) }
  }
  val imageUrl = item["image_url"]
  val url =
    when (imageUrl) {
      is JsonPrimitive -> imageUrl.contentOrNull
      is JsonObject -> imageUrl["url"]?.jsonPrimitive?.contentOrNull
      else -> item["url"]?.jsonPrimitive?.contentOrNull
    }
  if (url != null) return ChatStreamEvent.Image(url)
  return item["b64_json"]?.jsonPrimitive?.contentOrNull?.let {
    ChatStreamEvent.Image("data:image/png;base64,$it")
  }
}

private fun errorMessage(payload: String): String? =
  runCatching { json.decodeFromString<ErrorResponse>(payload).error?.message }.getOrNull()

private val json = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
}

private val imageGenerationTools =
  buildJsonArray {
    add(buildJsonObject {
      put("type", "function")
      put("function", buildJsonObject {
        put("name", "generate_image")
        put("description", "Generate an image when the user explicitly asks to create, draw, or make an image.")
        put("parameters", buildJsonObject {
          put("type", "object")
          put("properties", buildJsonObject {
            put("prompt", buildJsonObject { put("type", "string") })
          })
          put("required", buildJsonArray { add(JsonPrimitive("prompt")) })
          put("additionalProperties", false)
        })
      })
    })
  }

@Serializable
private data class ChatRequest(
  val model: String,
  val messages: List<ApiMessage>,
  val stream: Boolean = true,
  val tools: JsonElement? = null,
  @SerialName("tool_choice") val toolChoice: String? = null,
)

@Serializable private data class ApiMessage(val role: String, val content: JsonElement)

@Serializable
private data class ChatResponse(
  val choices: List<ChatChoice> = emptyList(),
  val error: ApiError? = null,
)

@Serializable
private data class ChatChoice(
  val delta: ChatContent? = null,
  val message: ChatContent? = null,
  @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class ChatContent(
  val content: JsonElement? = null,
  @SerialName("tool_calls") val toolCalls: List<ApiToolCall>? = null,
)

@Serializable
private data class ApiToolCall(
  val index: Int = 0,
  val function: ApiFunctionCall = ApiFunctionCall(),
)

@Serializable
private data class ApiFunctionCall(
  val name: String? = null,
  val arguments: String? = null,
)

@Serializable
private data class ImageGenerationRequest(
  val model: String,
  val prompt: String,
  val n: Int = 1,
  @SerialName("response_format") val responseFormat: String = "url",
)

@Serializable
private data class ImageGenerationResponse(
  val data: List<GeneratedImageData> = emptyList(),
  val error: ApiError? = null,
)

@Serializable
private data class GeneratedImageData(
  val url: String? = null,
  @SerialName("b64_json") val base64Json: String? = null,
  @SerialName("revised_prompt") val revisedPrompt: String? = null,
)

@Serializable private data class ApiError(val message: String)

@Serializable private data class ErrorResponse(val error: ApiError? = null)

@Serializable
private data class ModelsResponse(
  val data: List<ApiModel> = emptyList(),
  val error: ApiError? = null,
)

@Serializable private data class ApiModel(val id: String)

class LlmApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
