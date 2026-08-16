package cc.ykai.tinychat.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.ykai.tinychat.data.ApiConfig
import cc.ykai.tinychat.data.ApiConfigStore
import cc.ykai.tinychat.data.ChatMessage
import cc.ykai.tinychat.data.ChatRepository
import cc.ykai.tinychat.data.Conversation
import cc.ykai.tinychat.data.ImageStore
import cc.ykai.tinychat.data.MessageImage
import cc.ykai.tinychat.data.MessageRole
import cc.ykai.tinychat.data.isImageModel
import cc.ykai.tinychat.network.ChatApiClient
import cc.ykai.tinychat.network.ChatStreamEvent
import cc.ykai.tinychat.network.LlmApiException
import cc.ykai.tinychat.network.parseImageToolPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
  private val repository: ChatRepository,
  private val configStore: ApiConfigStore,
  private val imageStore: ImageStore,
  private val apiClient: ChatApiClient,
) : ViewModel() {
  private val selectedConversationId = MutableStateFlow<Long?>(null)
  private val generation = MutableStateFlow(Generation())
  private val generationStarting = MutableStateFlow(false)
  private val isChangingBranch = MutableStateFlow(false)
  private val errorMessage = MutableStateFlow<String?>(null)
  private val config = MutableStateFlow(configStore.load())
  private val modelCatalog = MutableStateFlow(ModelCatalog())
  private val pendingImages = MutableStateFlow<List<MessageImage>>(emptyList())
  private val isImportingImages = MutableStateFlow(false)
  private var sendJob: Job? = null
  private var modelsJob: Job? = null
  private val messageTreeMutex = Mutex()

  private val conversations =
    repository
      .observeConversations()
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  private val messages =
    selectedConversationId
      .flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val uiState: StateFlow<ChatUiState> =
    combine(conversations, selectedConversationId, messages, generation, errorMessage) {
        conversationList,
        selectedId,
        messageList,
        activeGeneration,
        error,
      ->
        ChatUiState(
          conversations = conversationList,
          selectedConversationId = selectedId,
          messages = messageList,
          generatingConversationId = activeGeneration.conversationId,
          streamingUserMessageId = activeGeneration.userMessageId,
          streamingText = activeGeneration.text,
          errorMessage = error,
          config = config.value,
        )
      }
      .combine(config) { state, currentConfig -> state.copy(config = currentConfig) }
      .combine(generationStarting) { state, isStarting ->
        state.copy(isPreparingGeneration = isStarting)
      }
      .combine(isChangingBranch) { state, changingBranch ->
        state.copy(isChangingBranch = changingBranch)
      }
      .combine(modelCatalog) { state, catalog ->
        state.copy(
          availableModels = catalog.models,
          isLoadingModels = catalog.isLoading,
          modelsError = catalog.error,
        )
      }
      .combine(pendingImages) { state, images -> state.copy(pendingImages = images) }
      .combine(isImportingImages) { state, importing -> state.copy(isImportingImages = importing) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(config = config.value))

  init {
    viewModelScope.launch {
      conversations.collect { current ->
        val selected = selectedConversationId.value
        if (selected == null || current.none { it.id == selected }) {
          selectedConversationId.value = current.firstOrNull()?.id
        }
      }
    }
  }

  fun newConversation() {
    viewModelScope.launch {
      errorMessage.value = null
      selectedConversationId.value = repository.createConversation()
    }
  }

  fun selectConversation(id: Long) {
    selectedConversationId.value = id
    errorMessage.value = null
  }

  fun deleteConversation(id: Long) {
    if (generationStarting.value || isChangingBranch.value) return
    viewModelScope.launch {
      if (generation.value.conversationId == id) sendJob?.cancelAndJoin()
      repository.deleteConversation(id)
      if (selectedConversationId.value == id) selectedConversationId.value = null
    }
  }

  fun send(rawPrompt: String): Boolean {
    val prompt = rawPrompt.trim()
    val attachedImages = pendingImages.value
    val selectedId = selectedConversationId.value
    if (prompt.isEmpty() && attachedImages.isEmpty()) return false
    return launchGeneration {
      val activeConversationId =
        if (selectedId == null) {
          repository.createConversation(titleFor(prompt.ifBlank { "图片对话" })).also {
            selectedConversationId.value = it
          }
        } else {
          val currentMessages = repository.messages(selectedId)
          if (currentMessages.isEmpty()) {
            repository.renameConversation(selectedId, titleFor(prompt.ifBlank { "图片对话" }))
          }
          selectedId
        }
      val parentMessageId = repository.messages(activeConversationId).lastOrNull()?.id
      val userMessageId =
        repository.addMessage(
          activeConversationId,
          MessageRole.User,
          prompt,
          attachedImages,
          parentMessageId,
        )
      pendingImages.value = emptyList()
      GenerationTarget(activeConversationId, userMessageId, prompt)
    }
  }

  fun editUserMessage(messageId: Long, rawContent: String) {
    val content = rawContent.trim()
    launchGeneration {
      val message = repository.message(messageId) ?: throw LlmApiException("找不到要编辑的消息")
      if (message.role != MessageRole.User) throw LlmApiException("只能编辑用户消息")
      if (content.isEmpty() && message.images.isEmpty()) return@launchGeneration null
      if (content == message.content) return@launchGeneration null
      if (repository.messages(message.conversationId).none { it.id == message.id }) {
        throw LlmApiException("这条消息已不在当前分支")
      }
      val editedMessageId =
        repository.addMessage(
          message.conversationId,
          MessageRole.User,
          content,
          message.images,
          message.parentId,
        )
      GenerationTarget(message.conversationId, editedMessageId, content)
    }
  }

  fun retryMessage(messageId: Long) {
    launchGeneration {
      val message = repository.message(messageId) ?: throw LlmApiException("找不到要重试的消息")
      val user =
        if (message.role == MessageRole.User) {
          message
        } else {
          message.parentId?.let { repository.message(it) }
        }
      if (user?.role != MessageRole.User) throw LlmApiException("找不到这条回复对应的用户消息")
      GenerationTarget(user.conversationId, user.id, user.content)
    }
  }

  fun selectMessageBranch(messageId: Long, offset: Int) {
    if (isChangingBranch.value || generationStarting.value || generation.value.conversationId != null) return
    isChangingBranch.value = true
    viewModelScope.launch {
      try {
        messageTreeMutex.withLock {
          if (generationStarting.value || generation.value.conversationId != null) return@withLock
          repository.selectSibling(messageId, offset)
          errorMessage.value = null
        }
      } finally {
        isChangingBranch.value = false
      }
    }
  }

  private fun launchGeneration(prepare: suspend () -> GenerationTarget?): Boolean {
    if (isChangingBranch.value || generationStarting.value || sendJob?.isActive == true || isImportingImages.value) {
      return false
    }
    val currentConfig = config.value
    if (!currentConfig.isReady) {
      errorMessage.value = "请先在设置中填写有效的 API 地址和模型名称"
      return false
    }

    errorMessage.value = null
    generationStarting.value = true
    sendJob =
      viewModelScope.launch {
        var target: GenerationTarget? = null
        var generatedText = ""
        try {
          val activeTarget = messageTreeMutex.withLock { prepare() } ?: return@launch
          target = activeTarget
          generation.value =
            Generation(
              conversationId = activeTarget.conversationId,
              userMessageId = activeTarget.userMessageId,
            )
          generationStarting.value = false

          val activePath = repository.messages(activeTarget.conversationId)
          val userMessageIndex = activePath.indexOfFirst { it.id == activeTarget.userMessageId }
          if (userMessageIndex == -1) throw LlmApiException("找不到当前消息分支")
          val history = activePath.take(userMessageIndex + 1)
          val explicitImagePrompt = explicitImagePrompt(activeTarget.prompt)
          if (explicitImagePrompt != null) {
            generation.value = generation.value.copy(text = "正在生成图片…")
            val generated = apiClient.generateImage(currentConfig, explicitImagePrompt)
            val savedImages = generated.map { imageStore.saveGeneratedImage(it.source) }
            if (savedImages.isEmpty()) throw LlmApiException("图片模型没有返回图片")
            persistAssistantMessage(
              activeTarget.conversationId,
              activeTarget.userMessageId,
              "已生成图片",
              savedImages,
            )
          } else {
            var toolName: String? = null
            val toolArguments = StringBuilder()
            val responseImages = mutableListOf<String>()
            apiClient.streamReply(currentConfig, history).collect { event ->
              when (event) {
                is ChatStreamEvent.Text -> {
                  generatedText += event.value
                  generation.value = generation.value.copy(text = generatedText)
                }
                is ChatStreamEvent.ToolCallDelta -> {
                  if (event.name != null) toolName = event.name
                  toolArguments.append(event.arguments)
                }
                is ChatStreamEvent.Image -> responseImages += event.source
              }
            }

            if (toolName == "generate_image") {
              generation.value = generation.value.copy(text = "正在生成图片…")
              val imagePrompt = parseImageToolPrompt(toolArguments.toString())
              val generated = apiClient.generateImage(currentConfig, imagePrompt)
              val savedImages = generated.map { imageStore.saveGeneratedImage(it.source) }
              if (savedImages.isEmpty()) throw LlmApiException("图片模型没有返回图片")
              persistAssistantMessage(
                activeTarget.conversationId,
                activeTarget.userMessageId,
                generatedText.ifBlank { "已生成图片" },
                savedImages,
              )
            } else {
              val savedImages = responseImages.map { imageStore.saveGeneratedImage(it) }
              if (generatedText.isBlank() && savedImages.isEmpty()) {
                throw LlmApiException("模型没有返回文本或图片内容")
              }
              persistAssistantMessage(
                activeTarget.conversationId,
                activeTarget.userMessageId,
                generatedText,
                savedImages,
              )
            }
          }
        } catch (cancelled: CancellationException) {
          if (generatedText.isNotBlank()) {
            target?.let {
              persistAssistantMessage(it.conversationId, it.userMessageId, generatedText)
            }
          }
          throw cancelled
        } catch (error: Exception) {
          if (generatedText.isNotBlank()) {
            target?.let {
              persistAssistantMessage(it.conversationId, it.userMessageId, generatedText)
            }
          }
          errorMessage.value = "生成失败：${error.message ?: "未知错误"}"
        } finally {
          if (generation.value.conversationId == target?.conversationId) generation.value = Generation()
          generationStarting.value = false
        }
      }
    return true
  }

  private suspend fun persistAssistantMessage(
    conversationId: Long,
    userMessageId: Long,
    content: String,
    images: List<MessageImage> = emptyList(),
  ) {
    withContext(NonCancellable) {
      val messageId =
        repository.addMessage(
          conversationId,
          MessageRole.Assistant,
          content,
          images,
          userMessageId,
        )
      val scopeJob = viewModelScope.coroutineContext[Job]
      if (scopeJob?.isActive != true || generation.value.conversationId != conversationId) {
        return@withContext
      }
      val messageVisible =
        viewModelScope.async {
          combine(messages, selectedConversationId) { messageList, selectedId ->
              selectedId != conversationId || messageList.any { it.id == messageId }
            }
            .first { it }
        }
      try {
        messageVisible.await()
      } catch (_: CancellationException) {
        // ViewModel teardown cancels this sibling observer while the message remains persisted.
      }
    }
  }

  fun stopGeneration() {
    sendJob?.cancel()
  }

  fun addImages(uris: List<Uri>) {
    val remainingSlots = 4 - pendingImages.value.size
    if (remainingSlots <= 0 || uris.isEmpty()) return
    viewModelScope.launch {
      isImportingImages.value = true
      try {
        uris.take(remainingSlots).forEach { uri ->
          val image = imageStore.importImage(uri)
          pendingImages.value += image
        }
      } catch (error: Exception) {
        errorMessage.value = "读取图片失败：${error.message ?: "未知错误"}"
      } finally {
        isImportingImages.value = false
      }
    }
  }

  fun removePendingImage(image: MessageImage) {
    if (generationStarting.value || sendJob?.isActive == true) return
    pendingImages.value = pendingImages.value - image
    viewModelScope.launch { imageStore.delete(image) }
  }

  fun saveConfig(newConfig: ApiConfig) {
    configStore.save(newConfig)
    config.value = configStore.load()
    errorMessage.value = null
  }

  fun refreshModels(candidateConfig: ApiConfig = config.value) {
    if (!candidateConfig.baseUrl.startsWith("http://") &&
      !candidateConfig.baseUrl.startsWith("https://")) {
      modelCatalog.value = ModelCatalog(error = "请先填写有效的 API Base URL")
      return
    }
    modelsJob?.cancel()
    modelsJob =
      viewModelScope.launch {
        modelCatalog.value = ModelCatalog(isLoading = true)
        try {
          val models = apiClient.listModels(candidateConfig)
          modelCatalog.value =
            if (models.isEmpty()) ModelCatalog(error = "服务没有返回可用模型")
            else ModelCatalog(models = models)
          val savedConfig = config.value
          if (savedConfig.imageModel.isBlank() &&
            savedConfig.baseUrl.trimEnd('/') == candidateConfig.baseUrl.trim().trimEnd('/') &&
            savedConfig.apiKey == candidateConfig.apiKey.trim()) {
            models.firstOrNull(::isImageModel)?.let { imageModel ->
              saveConfig(savedConfig.copy(imageModel = imageModel))
            }
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          modelCatalog.value = ModelCatalog(error = error.message ?: "获取模型失败")
        }
      }
  }

  fun switchModel(model: String) {
    val selectedModel = model.trim()
    if (selectedModel.isEmpty()) return
    saveConfig(config.value.copy(model = selectedModel))
  }

  fun clearError() {
    errorMessage.value = null
  }
}

data class ChatUiState(
  val conversations: List<Conversation> = emptyList(),
  val selectedConversationId: Long? = null,
  val messages: List<ChatMessage> = emptyList(),
  val generatingConversationId: Long? = null,
  val isPreparingGeneration: Boolean = false,
  val isChangingBranch: Boolean = false,
  val streamingUserMessageId: Long? = null,
  val streamingText: String = "",
  val errorMessage: String? = null,
  val config: ApiConfig = ApiConfig(),
  val availableModels: List<String> = emptyList(),
  val isLoadingModels: Boolean = false,
  val modelsError: String? = null,
  val pendingImages: List<MessageImage> = emptyList(),
  val isImportingImages: Boolean = false,
) {
  val isGenerating: Boolean
    get() = isPreparingGeneration || generatingConversationId != null

  val isGeneratingCurrentConversation: Boolean
    get() = generatingConversationId != null && generatingConversationId == selectedConversationId

  val selectedConversation: Conversation?
    get() = conversations.firstOrNull { it.id == selectedConversationId }
}

private data class Generation(
  val conversationId: Long? = null,
  val userMessageId: Long? = null,
  val text: String = "",
)

private data class GenerationTarget(
  val conversationId: Long,
  val userMessageId: Long,
  val prompt: String,
)

private data class ModelCatalog(
  val models: List<String> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
)

internal fun titleFor(prompt: String): String {
  val singleLine = prompt.replace(Regex("\\s+"), " ").trim()
  return if (singleLine.length <= 28) singleLine else singleLine.take(28) + "…"
}

internal fun explicitImagePrompt(prompt: String): String? {
  val trimmed = prompt.trim()
  val prefixes = listOf("/image", "/画图", "/生图")
  val prefix = prefixes.firstOrNull { trimmed.equals(it, true) || trimmed.startsWith("$it ", true) }
    ?: return null
  return trimmed.removePrefix(prefix).trim().ifBlank {
    throw LlmApiException("请在 $prefix 后描述要生成的图片")
  }
}
