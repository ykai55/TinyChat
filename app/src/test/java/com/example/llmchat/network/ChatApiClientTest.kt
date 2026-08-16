package com.example.llmchat.network

import junit.framework.TestCase.assertEquals
import org.junit.Test

class ChatApiClientTest {
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
