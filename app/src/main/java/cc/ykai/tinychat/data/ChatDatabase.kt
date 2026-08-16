package cc.ykai.tinychat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class ChatDatabase(context: Context) :
  SQLiteOpenHelper(context, "llm_chat.db", null, DATABASE_VERSION) {

  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE conversations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL(
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
    db.execSQL("CREATE INDEX messages_conversation_id ON messages(conversation_id, created_at)")
    createMessageImagesTable(db)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) createMessageImagesTable(db)
  }

  fun conversations(): List<Conversation> =
    readableDatabase
      .query(
        "conversations",
        arrayOf("id", "title", "created_at", "updated_at"),
        null,
        null,
        null,
        null,
        "updated_at DESC",
      ).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              Conversation(
                id = cursor.getLong(0),
                title = cursor.getString(1),
                createdAt = cursor.getLong(2),
                updatedAt = cursor.getLong(3),
              )
            )
          }
        }
      }

  fun messages(conversationId: Long): List<ChatMessage> {
    val imagesByMessage =
      readableDatabase
        .rawQuery(
          """
          SELECT message_images.message_id, message_images.source, message_images.mime_type
          FROM message_images
          INNER JOIN messages ON messages.id = message_images.message_id
          WHERE messages.conversation_id = ?
          ORDER BY message_images.id ASC
          """.trimIndent(),
          arrayOf(conversationId.toString()),
        ).use { cursor ->
          buildMap<Long, MutableList<MessageImage>> {
            while (cursor.moveToNext()) {
              getOrPut(cursor.getLong(0)) { mutableListOf() }
                .add(MessageImage(source = cursor.getString(1), mimeType = cursor.getString(2)))
            }
          }
        }
    return readableDatabase
      .query(
        "messages",
        arrayOf("id", "conversation_id", "role", "content", "created_at"),
        "conversation_id = ?",
        arrayOf(conversationId.toString()),
        null,
        null,
        "created_at ASC, id ASC",
      ).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              ChatMessage(
                id = cursor.getLong(0),
                conversationId = cursor.getLong(1),
                role = MessageRole.fromWireName(cursor.getString(2)),
                content = cursor.getString(3),
                createdAt = cursor.getLong(4),
                images = imagesByMessage[cursor.getLong(0)].orEmpty(),
              )
            )
          }
        }
      }
  }

  fun createConversation(title: String, now: Long): Long =
    writableDatabase.insertOrThrow(
      "conversations",
      null,
      ContentValues().apply {
        put("title", title)
        put("created_at", now)
        put("updated_at", now)
      },
    )

  fun renameConversation(id: Long, title: String, now: Long) {
    writableDatabase.update(
      "conversations",
      ContentValues().apply {
        put("title", title)
        put("updated_at", now)
      },
      "id = ?",
      arrayOf(id.toString()),
    )
  }

  fun insertMessage(
    conversationId: Long,
    role: MessageRole,
    content: String,
    images: List<MessageImage>,
    now: Long,
  ): Long {
    val db = writableDatabase
    db.beginTransaction()
    return try {
      val id =
        db.insertOrThrow(
          "messages",
          null,
          ContentValues().apply {
            put("conversation_id", conversationId)
            put("role", role.wireName)
            put("content", content)
            put("created_at", now)
          },
        )
      images.forEach { image ->
        db.insertOrThrow(
          "message_images",
          null,
          ContentValues().apply {
            put("message_id", id)
            put("source", image.source)
            put("mime_type", image.mimeType)
          },
        )
      }
      db.update(
        "conversations",
        ContentValues().apply { put("updated_at", now) },
        "id = ?",
        arrayOf(conversationId.toString()),
      )
      db.setTransactionSuccessful()
      id
    } finally {
      db.endTransaction()
    }
  }

  fun deleteConversation(id: Long) {
    writableDatabase.delete("conversations", "id = ?", arrayOf(id.toString()))
  }

  fun imageSources(conversationId: Long): List<String> =
    readableDatabase
      .rawQuery(
        """
        SELECT message_images.source
        FROM message_images
        INNER JOIN messages ON messages.id = message_images.message_id
        WHERE messages.conversation_id = ?
        """.trimIndent(),
        arrayOf(conversationId.toString()),
      ).use { cursor ->
        buildList {
          while (cursor.moveToNext()) add(cursor.getString(0))
        }
      }

  private companion object {
    const val DATABASE_VERSION = 2
  }

  private fun createMessageImagesTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS message_images (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        message_id INTEGER NOT NULL,
        source TEXT NOT NULL,
        mime_type TEXT NOT NULL,
        FOREIGN KEY(message_id) REFERENCES messages(id) ON DELETE CASCADE
      )
      """.trimIndent()
    )
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS message_images_message_id ON message_images(message_id, id)"
    )
  }
}
