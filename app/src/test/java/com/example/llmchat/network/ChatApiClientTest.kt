package com.example.llmchat.network

import com.example.llmchat.data.ApiConfig
import com.example.llmchat.data.ChatMessage
import com.example.llmchat.data.MessageRole
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test

class ChatApiClientTest {
  @Test
  fun streamReply_requestsAndEmitsStreamingChunks() = runBlocking {
    val server = MockWebServer()
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
          val requestBody = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
          return if (requestBody["stream"]?.jsonPrimitive?.booleanOrNull == true) {
            MockResponse()
              .setHeader("Content-Type", "text/event-stream")
              .setBody(
                """
                data: {"choices":[{"delta":{"content":"first "}}]}

                data: {"choices":[{"delta":{"content":"second"}}]}

                data: [DONE]

                """.trimIndent()
              )
          } else {
            MockResponse()
              .setHeader("Content-Type", "application/json")
              .setBody("""{"choices":[{"message":{"content":"first second"}}]}""")
          }
        }
      }
    server.start()

    try {
      val events =
        ChatApiClient()
          .streamReply(
            config = ApiConfig(baseUrl = server.url("/v1").toString(), model = "test-model"),
            history =
              listOf(
                ChatMessage(
                  id = 1,
                  conversationId = 1,
                  role = MessageRole.User,
                  content = "test",
                  createdAt = 0,
                )
              ),
          )
          .filterIsInstance<ChatStreamEvent.Text>()
          .map { it.value }
          .toList()

      assertEquals(listOf("first ", "second"), events)
    } finally {
      server.shutdown()
    }
  }

  @Test
  fun endpoint_appendsChatCompletionsPath() {
    assertEquals(
      "https://example.com/v1/chat/completions",
      resolveChatEndpoint("https://example.com/v1/"),
    )
  }

  @Test
  fun endpoint_keepsFullEndpoint() {
    assertEquals(
      "https://example.com/v1/chat/completions",
      resolveChatEndpoint("https://example.com/v1/chat/completions"),
    )
  }

  @Test
  fun modelsEndpoint_replacesChatCompletionsPath() {
    assertEquals(
      "https://example.com/v1/models",
      resolveModelsEndpoint("https://example.com/v1/chat/completions"),
    )
  }

  @Test
  fun modelsEndpoint_appendsModelsPath() {
    assertEquals("https://example.com/v1/models", resolveModelsEndpoint("https://example.com/v1/"))
  }

  @Test
  fun imagesEndpoint_replacesChatCompletionsPath() {
    assertEquals(
      "https://example.com/v1/images/generations",
      resolveImagesEndpoint("https://example.com/v1/chat/completions"),
    )
  }

  @Test
  fun payload_readsStreamingDelta() {
    val payload = """{"choices":[{"delta":{"content":"你好"}}]}"""

    assertEquals("你好", parseChatPayload(payload))
  }

  @Test
  fun payload_readsNonStreamingMessage() {
    val payload = """{"choices":[{"message":{"content":"完成","tool_calls":null}}]}"""

    assertEquals("完成", parseChatPayload(payload))
  }

  @Test
  fun modelsPayload_readsUniqueModelIds() {
    val payload =
      """{"object":"list","data":[{"id":"gpt-5"},{"id":"gpt-5-mini"},{"id":"gpt-5"}]}"""

    assertEquals(listOf("gpt-5", "gpt-5-mini"), parseModelsPayload(payload))
  }

  @Test
  fun payload_readsImageToolCall() {
    val payload =
      """{"choices":[{"message":{"content":null,"tool_calls":[{"type":"function","function":{"name":"generate_image","arguments":"{\"prompt\":\"一只猫\"}"}}]}}]}"""

    val event = parseChatEvents(payload).single() as ChatStreamEvent.ToolCallDelta
    assertEquals("generate_image", event.name)
    assertEquals("一只猫", parseImageToolPrompt(event.arguments))
  }

  @Test
  fun payload_readsStructuredImageContent() {
    val payload =
      """{"choices":[{"message":{"content":[{"type":"image_url","image_url":{"url":"https://example.com/image.png"}}]}}]}"""

    val event = parseChatEvents(payload).single() as ChatStreamEvent.Image
    assertEquals("https://example.com/image.png", event.source)
  }

  @Test
  fun generatedImages_supportsBase64Response() {
    val payload = """{"data":[{"b64_json":"YWJj","revised_prompt":"orange cat"}]}"""

    assertEquals(
      listOf(GeneratedImage("data:image/png;base64,YWJj", "orange cat")),
      parseGeneratedImages(payload),
    )
  }
}
