package com.example.llmchat

import android.app.Application
import com.example.llmchat.data.ApiConfigStore
import com.example.llmchat.data.ChatRepository
import com.example.llmchat.data.ImageStore
import com.example.llmchat.network.ChatApiClient

class LlmChatApplication : Application() {
  val container: AppContainer by lazy {
    AppContainer(
      chatRepository = ChatRepository(this),
      configStore = ApiConfigStore(this),
      imageStore = ImageStore(this),
      apiClient = ChatApiClient(),
    )
  }
}

data class AppContainer(
  val chatRepository: ChatRepository,
  val configStore: ApiConfigStore,
  val imageStore: ImageStore,
  val apiClient: ChatApiClient,
)
