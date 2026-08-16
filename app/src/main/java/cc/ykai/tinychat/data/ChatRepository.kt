package cc.ykai.tinychat.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class ChatRepository(context: Context) {
  private val database = ChatDatabase(context.applicationContext)
  private val changes = MutableStateFlow(0L)

  fun observeConversations(): Flow<List<Conversation>> =
    changes.map { database.conversations() }.distinctUntilChanged().flowOn(Dispatchers.IO)

  fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
    changes.map { database.messages(conversationId) }.distinctUntilChanged().flowOn(Dispatchers.IO)

  suspend fun messages(conversationId: Long): List<ChatMessage> =
    withContext(Dispatchers.IO) { database.messages(conversationId) }

  suspend fun createConversation(title: String = "新对话"): Long =
    withContext(Dispatchers.IO) {
      val id = database.createConversation(title, System.currentTimeMillis())
      notifyChanged()
      id
    }

  suspend fun renameConversation(id: Long, title: String) {
    withContext(Dispatchers.IO) {
      database.renameConversation(id, title, System.currentTimeMillis())
      notifyChanged()
    }
  }

  suspend fun addMessage(
    conversationId: Long,
    role: MessageRole,
    content: String,
    images: List<MessageImage> = emptyList(),
  ): Long =
    withContext(Dispatchers.IO) {
      val id =
        database.insertMessage(
          conversationId,
          role,
          content,
          images,
          System.currentTimeMillis(),
        )
      notifyChanged()
      id
    }

  suspend fun deleteConversation(id: Long) {
    withContext(Dispatchers.IO) {
      val localImages =
        database.imageSources(id).filterNot {
          it.startsWith("http://") || it.startsWith("https://") || it.startsWith("data:")
        }
      database.deleteConversation(id)
      localImages.forEach { File(it).delete() }
      notifyChanged()
    }
  }

  private fun notifyChanged() {
    changes.update { it + 1 }
  }
}
