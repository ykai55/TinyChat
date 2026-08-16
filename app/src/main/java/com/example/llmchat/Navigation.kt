package com.example.llmchat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llmchat.ui.main.ChatScreen
import com.example.llmchat.ui.main.ChatViewModel
import com.example.llmchat.ui.settings.SettingsScreen
import com.example.llmchat.ui.settings.ModelPickerDialog
import com.example.llmchat.data.isImageModel

@Composable
fun LlmChatApp(viewModel: ChatViewModel) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showSettings by rememberSaveable { mutableStateOf(false) }
  var showModelPicker by rememberSaveable { mutableStateOf(false) }

  if (showSettings) {
    SettingsScreen(
      config = state.config,
      availableModels = state.availableModels,
      isLoadingModels = state.isLoadingModels,
      modelsError = state.modelsError,
      onBack = { showSettings = false },
      onSave = {
        viewModel.saveConfig(it)
        showSettings = false
      },
      onRefreshModels = viewModel::refreshModels,
    )
  } else {
    ChatScreen(
      state = state,
      onNewConversation = viewModel::newConversation,
      onSelectConversation = viewModel::selectConversation,
      onDeleteConversation = viewModel::deleteConversation,
      onSend = viewModel::send,
      onStop = viewModel::stopGeneration,
      onAddImages = viewModel::addImages,
      onRemovePendingImage = viewModel::removePendingImage,
      onOpenSettings = { showSettings = true },
      onOpenModelPicker = { showModelPicker = true },
      onErrorShown = viewModel::clearError,
    )
    if (showModelPicker) {
      LaunchedEffect(Unit) { viewModel.refreshModels() }
      ModelPickerDialog(
        currentModel = state.config.model,
        models = state.availableModels.filterNot(::isImageModel),
        isLoading = state.isLoadingModels,
        error = state.modelsError,
        onSelect = {
          viewModel.switchModel(it)
          showModelPicker = false
        },
        onRefresh = { viewModel.refreshModels() },
        onDismiss = { showModelPicker = false },
      )
    }
  }
}
