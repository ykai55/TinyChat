package cc.ykai.tinychat.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cc.ykai.tinychat.data.ApiConfig
import cc.ykai.tinychat.data.isImageModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  config: ApiConfig,
  availableModels: List<String>,
  isLoadingModels: Boolean,
  modelsError: String?,
  onBack: () -> Unit,
  onSave: (ApiConfig) -> Unit,
  onRefreshModels: (ApiConfig) -> Unit,
) {
  var baseUrl by rememberSaveable { mutableStateOf(config.baseUrl) }
  var apiKey by rememberSaveable { mutableStateOf(config.apiKey) }
  var model by rememberSaveable { mutableStateOf(config.model) }
  var imageModel by rememberSaveable { mutableStateOf(config.imageModel) }
  var systemPrompt by rememberSaveable { mutableStateOf(config.systemPrompt) }
  var showApiKey by rememberSaveable { mutableStateOf(false) }
  var modelsExpanded by rememberSaveable { mutableStateOf(false) }
  var imageModelsExpanded by rememberSaveable { mutableStateOf(false) }
  val validUrl = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")
  val canSave = validUrl && model.isNotBlank()
  val chatModels = availableModels.filterNot(::isImageModel)
  val imageModels = availableModels.filter(::isImageModel)

  LaunchedEffect(config.imageModel) {
    if (imageModel.isBlank() && config.imageModel.isNotBlank()) imageModel = config.imageModel
  }

  BackHandler(onBack = onBack)
  Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("模型设置") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        "OpenAI 兼容接口",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        "支持 OpenAI、DeepSeek、Ollama 等兼容 Chat Completions 协议的服务。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
      )

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Base URL") },
            placeholder = { Text("https://api.openai.com/v1") },
            supportingText = { Text("应用会自动追加 /chat/completions") },
            isError = baseUrl.isNotBlank() && !validUrl,
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
              ),
          )
          OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key（本地模型可留空）") },
            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
            trailingIcon = {
              IconButton(onClick = { showApiKey = !showApiKey }) {
                Icon(
                  if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                  contentDescription = if (showApiKey) "隐藏 API Key" else "显示 API Key",
                )
              }
            },
            visualTransformation =
              if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions =
              KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
              ),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = {
                onRefreshModels(
                  ApiConfig(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    imageModel = imageModel,
                    systemPrompt = systemPrompt,
                  )
                )
              },
              enabled = validUrl && !isLoadingModels,
            ) {
              if (isLoadingModels) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
              }
              Spacer(Modifier.width(8.dp))
              Text(if (isLoadingModels) "正在获取" else "获取可用模型")
            }
            when {
              modelsError != null ->
                Text(
                  modelsError,
                  modifier = Modifier.weight(1f),
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall,
                )
              availableModels.isNotEmpty() ->
                Text(
                  "已获取 ${availableModels.size} 个",
                  color = MaterialTheme.colorScheme.primary,
                  style = MaterialTheme.typography.bodySmall,
                )
            }
          }
          Box {
            OutlinedTextField(
              value = model,
              onValueChange = { model = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("模型名称") },
              placeholder = { Text("可选择或输入自定义模型") },
              trailingIcon = {
                IconButton(
                  onClick = { modelsExpanded = true },
                  enabled = chatModels.isNotEmpty(),
                ) {
                  Icon(Icons.Outlined.ArrowDropDown, contentDescription = "选择模型")
                }
              },
              supportingText = { Text("支持直接输入未出现在列表中的模型 ID") },
              singleLine = true,
              keyboardOptions =
                KeyboardOptions(
                  autoCorrectEnabled = false,
                  keyboardType = KeyboardType.Ascii,
                ),
            )
            DropdownMenu(
              expanded = modelsExpanded && chatModels.isNotEmpty(),
              onDismissRequest = { modelsExpanded = false },
              modifier = Modifier.fillMaxWidth(0.82f).heightIn(max = 320.dp),
            ) {
              chatModels.forEach { availableModel ->
                DropdownMenuItem(
                  text = { Text(availableModel) },
                  onClick = {
                    model = availableModel
                    modelsExpanded = false
                  },
                )
              }
            }
          }
          Box {
            OutlinedTextField(
              value = imageModel,
              onValueChange = { imageModel = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("图片生成模型") },
              placeholder = { Text("例如 gpt-image-1") },
              trailingIcon = {
                IconButton(
                  onClick = { imageModelsExpanded = true },
                  enabled = imageModels.isNotEmpty(),
                ) {
                  Icon(Icons.Outlined.ArrowDropDown, contentDescription = "选择图片模型")
                }
              },
              supportingText = { Text("用于对话模型调用生图工具；留空则关闭自动生图") },
              singleLine = true,
              keyboardOptions =
                KeyboardOptions(
                  autoCorrectEnabled = false,
                  keyboardType = KeyboardType.Ascii,
                ),
            )
            DropdownMenu(
              expanded = imageModelsExpanded && imageModels.isNotEmpty(),
              onDismissRequest = { imageModelsExpanded = false },
              modifier = Modifier.fillMaxWidth(0.82f).heightIn(max = 240.dp),
            ) {
              imageModels.forEach { availableModel ->
                DropdownMenuItem(
                  text = { Text(availableModel) },
                  onClick = {
                    imageModel = availableModel
                    imageModelsExpanded = false
                  },
                )
              }
            }
          }
        }
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
          Text("系统提示词", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("例如：你是一名简洁、严谨的中文助手。") },
            minLines = 3,
            maxLines = 8,
          )
        }
      }

      if (baseUrl.startsWith("http://")) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Top,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
          Text(
            "当前使用 HTTP 明文连接。不要通过该连接发送 API Key 或敏感内容。",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }

      Text(
        "API Key 和会话记录保存在应用私有存储中，应用已关闭系统云备份。请求内容只会发送到你配置的服务。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
      )
      Button(
        onClick = {
          onSave(
            ApiConfig(
              baseUrl = baseUrl,
              apiKey = apiKey,
              model = model,
              imageModel = imageModel,
              systemPrompt = systemPrompt,
            )
          )
        },
        enabled = canSave,
        modifier = Modifier.fillMaxWidth().height(52.dp),
      ) {
        Text("保存配置")
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}
