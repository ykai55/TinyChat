package cc.ykai.tinychat

import android.app.Application
import cc.ykai.tinychat.data.ApiConfigStore
import cc.ykai.tinychat.data.ChatRepository
import cc.ykai.tinychat.data.ImageStore
import cc.ykai.tinychat.network.ChatApiClient

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
