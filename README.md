# TinyChat

TinyChat 是一个原生 Android LLM 客户端。用户可以连接自己的 OpenAI 兼容 API，在本地管理多会话、切换模型，并进行文本、图片和 Markdown 对话。

## 功能

- OpenAI 兼容的 Chat Completions API
- SSE 流式回复和停止生成
- 多会话创建、切换、自动命名和删除
- 从 `/models` 获取模型列表并快速切换
- 支持手动填写未出现在模型列表中的模型 ID
- GFM Markdown 渲染，包括标题、列表、表格、任务列表、链接和代码块
- Android Photo Picker 多图选择，无需申请相册权限
- OpenAI `text + image_url` 多模态图片输入
- 对话模型通过 Function Calling 自动触发图片生成
- OpenAI `/images/generations` 图片生成接口
- Markdown 网络图片、结构化图片和 Data URL 图片输出
- 图片全屏预览及本地持久化
- 浅色与深色主题

## 系统要求

- Android 8.0（API 26）或更高版本
- JDK 17
- 支持相关能力的 OpenAI 兼容 API 服务

## API 兼容性

TinyChat 使用以下接口：

```text
GET  {baseUrl}/models
POST {baseUrl}/chat/completions
POST {baseUrl}/images/generations
```

如果 Base URL 已经是完整的 `/chat/completions` 地址，应用会自动推导模型列表和图片生成地址。

文本和图片输入采用 OpenAI Chat Completions content parts 格式：

```json
{
  "role": "user",
  "content": [
    { "type": "text", "text": "描述这张图片" },
    {
      "type": "image_url",
      "image_url": { "url": "data:image/jpeg;base64,...", "detail": "auto" }
    }
  ]
}
```

自动生图通过 `generate_image` Function Calling 实现。对话模型判断用户是否明确要求生成图片；触发后，应用使用设置中的图片模型调用 `/images/generations`。对于不支持工具调用的服务，也可以使用显式命令：

```text
/image 一只在月球上的橘猫
/生图 水彩风格的海边小镇
```

不同服务对多模态、工具调用和图片生成的支持程度可能不同。仅文本服务仍可正常用于普通对话。

## 使用方法

1. 打开右上角“模型设置”。
2. 填写 API Base URL 和 API Key。
3. 点击“获取可用模型”。
4. 选择对话模型和图片生成模型，也可以直接输入自定义模型 ID。
5. 保存配置并开始对话。

点击聊天页顶部的当前模型名称，可以快速刷新和切换对话模型。

## 本地数据与隐私

- 会话、消息和图片保存在应用私有目录。
- 相册图片会缩放到最长边 1600px 后再发送。
- API Key 保存在应用私有 SharedPreferences 中。
- 应用关闭了 Android 云备份和设备迁移备份。
- 请求内容只会发送到用户配置的 API 服务。
- 删除会话时会同步清理该会话保存的本地图片。

## 构建

```bash
./gradlew assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试和 Lint：

```bash
./gradlew testDebugUnitTest lintDebug
```

## 技术栈

- Kotlin 2.3
- Jetpack Compose + Material 3
- Android SQLite
- Kotlin Coroutines + Flow
- OkHttp
- Kotlinx Serialization
- Coil 3
- Multiplatform Markdown Renderer

## 项目结构

```text
app/src/main/java/com/example/llmchat/
├── data/       # 会话数据库、API 配置和图片存储
├── network/    # Chat Completions、模型列表和图片生成客户端
├── theme/      # Compose 主题
├── ui/main/    # 会话列表、聊天和附件界面
└── ui/settings/# API 与模型设置
```
