package com.example.llmchat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelPickerDialog(
  currentModel: String,
  models: List<String>,
  isLoading: Boolean,
  error: String?,
  onSelect: (String) -> Unit,
  onRefresh: () -> Unit,
  onDismiss: () -> Unit,
) {
  var customModel by rememberSaveable(currentModel) { mutableStateOf(currentModel) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("切换模型")
        IconButton(onClick = onRefresh, enabled = !isLoading) {
          if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Outlined.Refresh, contentDescription = "刷新模型列表")
          }
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (error != null) {
          Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (models.isNotEmpty()) {
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
            items(models, key = { it }) { model ->
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onSelect(model) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(selected = model == currentModel, onClick = { onSelect(model) })
                Text(model, modifier = Modifier.weight(1f))
              }
            }
          }
          HorizontalDivider()
        } else if (!isLoading && error == null) {
          Text("点击刷新，从 API 获取可用模型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
          value = customModel,
          onValueChange = { customModel = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("自定义模型 ID") },
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onSelect(customModel) }, enabled = customModel.isNotBlank()) {
        Text("使用此模型")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}
