package cc.ykai.tinychat.data

import android.content.Context
import androidx.core.content.edit

class ApiConfigStore(context: Context) {
  private val preferences = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)

  fun load(): ApiConfig =
    ApiConfig(
      baseUrl = preferences.getString("base_url", null) ?: ApiConfig().baseUrl,
      apiKey = preferences.getString("api_key", "").orEmpty(),
      model = preferences.getString("model", "").orEmpty(),
      imageModel = preferences.getString("image_model", "").orEmpty(),
      systemPrompt = preferences.getString("system_prompt", "").orEmpty(),
    )

  fun save(config: ApiConfig) {
    preferences.edit {
      putString("base_url", config.baseUrl.trim().trimEnd('/'))
      putString("api_key", config.apiKey.trim())
      putString("model", config.model.trim())
      putString("image_model", config.imageModel.trim())
      putString("system_prompt", config.systemPrompt.trim())
    }
  }
}
