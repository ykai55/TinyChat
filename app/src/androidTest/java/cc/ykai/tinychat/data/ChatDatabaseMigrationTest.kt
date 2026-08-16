package cc.ykai.tinychat.data

import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChatDatabaseMigrationTest {
  private val context
    get() = InstrumentationRegistry.getInstrumentation().context

  @Before
  fun createVersionTwoDatabase() {
    context.deleteDatabase(DATABASE_NAME)
    context.openOrCreateDatabase(DATABASE_NAME, 0, null).use { database ->
      database.execSQL(
        """
        CREATE TABLE conversations (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          title TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )
        """.trimIndent()
      )
      database.execSQL(
        """
        CREATE TABLE messages (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          conversation_id INTEGER NOT NULL,
          role TEXT NOT NULL,
          content TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
        )
        """.trimIndent()
      )
      database.execSQL(
        """
        CREATE TABLE message_images (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          message_id INTEGER NOT NULL,
          source TEXT NOT NULL,
          mime_type TEXT NOT NULL,
          FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE
        )
        """.trimIndent()
      )
      database.insertOrThrow(
        "conversations",
        null,
        ContentValues().apply {
          put("id", 1)
          put("title", "迁移测试")
          put("created_at", 1)
          put("updated_at", 2)
        },
      )
      insertMessage(database, id = 10, role = "user", content = "旧问题", createdAt = 1)
      insertMessage(database, id = 11, role = "assistant", content = "旧回答", createdAt = 2)
      database.insertOrThrow(
        "message_images",
        null,
        ContentValues().apply {
          put("message_id", 11)
          put("source", "https://example.com/image.png")
          put("mime_type", "image/png")
        },
      )
      database.version = 2
    }
  }

  @After
  fun deleteDatabase() {
    context.deleteDatabase(DATABASE_NAME)
  }

  @Test
  fun migratesLinearHistoryAndPreservesBranchSelections() {
    ChatDatabase(context).use { database ->
      val migrated = database.messages(1)
      assertEquals(listOf(10L, 11L), migrated.map { it.id })
      assertEquals(null, migrated[0].parentId)
      assertEquals(10L, migrated[1].parentId)
      assertEquals("https://example.com/image.png", migrated[1].images.single().source)

      val editedUserId =
        database.insertMessage(1, MessageRole.User, "新问题", emptyList(), null, now = 3)
      assertEquals(listOf(editedUserId), database.messages(1).map { it.id })
      assertEquals(2, database.messages(1).single().branchCount)

      database.selectSibling(editedUserId, -1)
      assertEquals(listOf(10L, 11L), database.messages(1).map { it.id })

      val retriedAssistantId =
        database.insertMessage(1, MessageRole.Assistant, "新回答", emptyList(), 10, now = 4)
      val retriedPath = database.messages(1)
      assertEquals(listOf(10L, retriedAssistantId), retriedPath.map { it.id })
      assertEquals(2, retriedPath.last().branchCount)

      database.selectSibling(retriedAssistantId, -1)
      assertEquals(listOf(10L, 11L), database.messages(1).map { it.id })
    }
  }

  private fun insertMessage(
    database: android.database.sqlite.SQLiteDatabase,
    id: Long,
    role: String,
    content: String,
    createdAt: Long,
  ) {
    database.insertOrThrow(
      "messages",
      null,
      ContentValues().apply {
        put("id", id)
        put("conversation_id", 1)
        put("role", role)
        put("content", content)
        put("created_at", createdAt)
      },
    )
  }

  private companion object {
    const val DATABASE_NAME = "llm_chat.db"
  }
}
