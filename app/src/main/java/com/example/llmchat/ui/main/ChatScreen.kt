package com.example.llmchat.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llmchat.data.ChatMessage
import com.example.llmchat.data.Conversation
import com.example.llmchat.data.MessageRole
import com.example.llmchat.data.MessageImage
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  state: ChatUiState,
  onNewConversation: () -> Unit,
  onSelectConversation: (Long) -> Unit,
  onDeleteConversation: (Long) -> Unit,
  onSend: (String) -> Unit,
  onStop: () -> Unit,
  onAddImages: (List<android.net.Uri>) -> Unit,
  onRemovePendingImage: (MessageImage) -> Unit,
  onOpenSettings: () -> Unit,
  onOpenModelPicker: () -> Unit,
  onErrorShown: () -> Unit,
) {
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
      onAddImages(uris)
    }

  LaunchedEffect(state.errorMessage) {
    val error = state.errorMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(error)
    onErrorShown()
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ConversationDrawer(
        conversations = state.conversations,
        selectedId = state.selectedConversationId,
        onNewConversation = {
          onNewConversation()
          scope.launch { drawerState.close() }
        },
        onSelectConversation = {
          onSelectConversation(it)
          scope.launch { drawerState.close() }
        },
        onDeleteConversation = onDeleteConversation,
      )
    },
  ) {
    Scaffold(
      contentWindowInsets = WindowInsets.systemBars,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        CenterAlignedTopAppBar(
          title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = state.selectedConversation?.title ?: "流光对话",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
              )
              if (state.config.model.isNotBlank()) {
                Row(
                  modifier =
                    Modifier.clip(MaterialTheme.shapes.small)
                      .clickable(onClick = onOpenModelPicker)
                      .padding(horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = state.config.model,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                  )
                  Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = "切换模型",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          },
          navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
              Icon(Icons.Outlined.Menu, contentDescription = "打开会话列表")
            }
          },
          actions = {
            IconButton(onClick = onNewConversation) {
              Icon(Icons.Outlined.Add, contentDescription = "新建会话")
            }
            IconButton(onClick = onOpenSettings) {
              Icon(Icons.Outlined.Settings, contentDescription = "模型设置")
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
      },
    ) { padding ->
      Column(
        modifier =
          Modifier.fillMaxSize()
            .padding(padding),
      ) {
        MessageList(
          conversationId = state.selectedConversationId,
          messages = state.messages,
          streamingText = if (state.isGeneratingCurrentConversation) state.streamingText else "",
          isGenerating = state.isGeneratingCurrentConversation,
          onSuggestion = onSend,
          modifier = Modifier.weight(1f),
        )
        MessageComposer(
          conversationId = state.selectedConversationId,
          isGenerating = state.isGenerating,
          configReady = state.config.isReady,
          pendingImages = state.pendingImages,
          isImportingImages = state.isImportingImages,
          onPickImages = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          onRemovePendingImage = onRemovePendingImage,
          onSend = onSend,
          onStop = onStop,
        )
      }
    }
  }
}

@Composable
private fun ConversationDrawer(
  conversations: List<Conversation>,
  selectedId: Long?,
  onNewConversation: () -> Unit,
  onSelectConversation: (Long) -> Unit,
  onDeleteConversation: (Long) -> Unit,
) {
  ModalDrawerSheet(modifier = Modifier.width(310.dp).fillMaxHeight()) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          modifier = Modifier.size(38.dp),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Outlined.ChatBubbleOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text("流光对话", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text("本地会话", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(Modifier.height(20.dp))
      OutlinedButton(onClick = onNewConversation, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("新建会话")
      }
    }
    HorizontalDivider()
    if (conversations.isEmpty()) {
      Text(
        "暂无历史会话",
        modifier = Modifier.padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        items(conversations, key = { it.id }) { conversation ->
          NavigationDrawerItem(
            label = {
              Column {
                Text(
                  conversation.title,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  formatTime(conversation.updatedAt),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            selected = conversation.id == selectedId,
            onClick = { onSelectConversation(conversation.id) },
            badge = {
              IconButton(onClick = { onDeleteConversation(conversation.id) }) {
                Icon(
                  Icons.Outlined.DeleteOutline,
                  contentDescription = "删除 ${conversation.title}",
                  modifier = Modifier.size(18.dp),
                )
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun MessageList(
  conversationId: Long?,
  messages: List<ChatMessage>,
  streamingText: String,
  isGenerating: Boolean,
  onSuggestion: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (messages.isEmpty() && !isGenerating) {
    WelcomePanel(onSuggestion = onSuggestion, modifier = modifier)
    return
  }

  val listState = rememberLazyListState()
  val itemCount = messages.size + if (isGenerating) 1 else 0
  LaunchedEffect(conversationId, messages.lastOrNull()?.id, itemCount, streamingText.length) {
    if (itemCount > 0) listState.scrollToItem(itemCount - 1)
  }
  LazyColumn(
    state = listState,
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(messages, key = { it.id }) { MessageBubble(it.role, it.content, it.images) }
    if (isGenerating) {
      item(key = "streaming") {
        MessageBubble(
          role = MessageRole.Assistant,
          content = streamingText,
          images = emptyList(),
          showCursor = true,
        )
      }
    }
  }
}

@Composable
private fun WelcomePanel(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
      Surface(
        modifier = Modifier.size(64.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      Spacer(Modifier.height(20.dp))
      Text("你的模型，你的对话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      Text(
        "连接任意 OpenAI 兼容接口，会话记录只保存在本机。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(Modifier.height(28.dp))
      listOf("帮我制定一份今日计划", "解释一个我不熟悉的概念", "把这段想法整理成清单").forEach { suggestion ->
        Surface(
          modifier =
            Modifier.fillMaxWidth()
              .padding(vertical = 5.dp)
              .clip(MaterialTheme.shapes.large)
              .selectable(selected = false, onClick = { onSuggestion(suggestion) }),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Text(suggestion, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
        }
      }
    }
  }
}

@Composable
private fun MessageBubble(
  role: MessageRole,
  content: String,
  images: List<MessageImage>,
  showCursor: Boolean = false,
) {
  val isUser = role == MessageRole.User
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    if (!isUser) {
      Surface(
        modifier = Modifier.size(30.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text("L", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
      }
      Spacer(Modifier.width(10.dp))
    }
    Surface(
      modifier = Modifier.fillMaxWidth(if (isUser) 0.84f else 0.9f),
      shape =
        if (isUser) MaterialTheme.shapes.extraLarge
        else MaterialTheme.shapes.large,
      color =
        if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
      Column {
        if (images.isNotEmpty()) {
          MessageImages(images = images, modifier = Modifier.padding(8.dp))
        }
        if (content.isNotBlank() || showCursor) {
          if (isUser) {
            SelectionContainer {
              Text(
                text = content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
              )
            }
          } else {
            val markdown =
              if (content.isEmpty() && showCursor) "正在思考…"
              else content + if (showCursor) " ▍" else ""
            Markdown(
              content = markdown,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
              imageTransformer = Coil3ImageTransformerImpl,
              typography =
                markdownTypography(
                  h1 = MaterialTheme.typography.headlineMedium,
                  h2 = MaterialTheme.typography.headlineSmall,
                  h3 = MaterialTheme.typography.titleLarge,
                  h4 = MaterialTheme.typography.titleMedium,
                  h5 = MaterialTheme.typography.titleSmall,
                  h6 = MaterialTheme.typography.labelLarge,
                ),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MessageImages(images: List<MessageImage>, modifier: Modifier = Modifier) {
  var preview by remember { mutableStateOf<MessageImage?>(null) }
  if (images.size == 1) {
    val image = images.first()
    AsyncImage(
      model = imageModel(image),
      contentDescription = "消息图片",
      modifier =
        modifier.fillMaxWidth().height(220.dp)
          .clip(MaterialTheme.shapes.large)
          .clickable { preview = image },
      contentScale = ContentScale.Crop,
    )
  } else {
    Row(
      modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      images.forEach { image ->
        AsyncImage(
          model = imageModel(image),
          contentDescription = "消息图片",
          modifier =
            Modifier.size(150.dp)
              .clip(MaterialTheme.shapes.large)
              .clickable { preview = image },
          contentScale = ContentScale.Crop,
        )
      }
    }
  }
  preview?.let { image ->
    Dialog(onDismissRequest = { preview = null }) {
      Surface(
        modifier = Modifier.fillMaxWidth().clickable { preview = null },
        shape = MaterialTheme.shapes.large,
        color = Color.Black,
      ) {
        AsyncImage(
          model = imageModel(image),
          contentDescription = "图片预览",
          modifier = Modifier.fillMaxWidth(),
          contentScale = ContentScale.Fit,
        )
      }
    }
  }
}

@Composable
private fun MessageComposer(
  conversationId: Long?,
  isGenerating: Boolean,
  configReady: Boolean,
  pendingImages: List<MessageImage>,
  isImportingImages: Boolean,
  onPickImages: () -> Unit,
  onRemovePendingImage: (MessageImage) -> Unit,
  onSend: (String) -> Unit,
  onStop: () -> Unit,
) {
  var draft by rememberSaveable(conversationId) { mutableStateOf("") }

  fun submit() {
    if ((draft.isBlank() && pendingImages.isEmpty()) || isGenerating || isImportingImages) return
    onSend(draft)
    if (configReady) draft = ""
  }

  Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
    Column(modifier = Modifier.fillMaxWidth()) {
      if (pendingImages.isNotEmpty() || isImportingImages) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          pendingImages.forEach { image ->
            Box {
              AsyncImage(
                model = imageModel(image),
                contentDescription = "待发送图片",
                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
              )
              FilledIconButton(
                onClick = { onRemovePendingImage(image) },
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
              ) {
                Icon(Icons.Outlined.Close, contentDescription = "移除图片", modifier = Modifier.size(14.dp))
              }
            }
          }
          if (isImportingImages) CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(onClick = onPickImages, enabled = !isGenerating && pendingImages.size < 4) {
          Icon(Icons.Outlined.PhotoLibrary, contentDescription = "选择图片")
        }
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          modifier = Modifier.weight(1f),
          placeholder = { Text(if (configReady) "输入消息" else "请先配置模型") },
          minLines = 1,
          maxLines = 6,
          shape = MaterialTheme.shapes.extraLarge,
          keyboardOptions =
            KeyboardOptions(
              capitalization = KeyboardCapitalization.Sentences,
              keyboardType = KeyboardType.Text,
              imeAction = ImeAction.Send,
            ),
          keyboardActions = KeyboardActions(onSend = { submit() }),
          colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent),
        )
        FilledIconButton(
          onClick = if (isGenerating) onStop else ::submit,
          enabled =
            isGenerating || (!isImportingImages && (draft.isNotBlank() || pendingImages.isNotEmpty())),
          modifier = Modifier.size(50.dp),
        ) {
          Icon(
            imageVector = if (isGenerating) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.Send,
            contentDescription = if (isGenerating) "停止生成" else "发送",
          )
        }
      }
    }
  }
}

private fun imageModel(image: MessageImage): Any =
  when {
    image.source.startsWith("http://") || image.source.startsWith("https://") ||
      image.source.startsWith("data:") -> image.source
    else -> File(image.source)
  }

private val drawerTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun formatTime(timestamp: Long): String =
  Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(drawerTimeFormatter)
