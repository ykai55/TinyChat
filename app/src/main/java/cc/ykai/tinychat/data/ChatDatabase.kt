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
        updated_at INTEGER NOT NULL,
        selected_root_message_id INTEGER
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
        parent_message_id INTEGER,
        selected_child_message_id INTEGER,
        FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX messages_conversation_id ON messages(conversation_id, created_at)")
    createMessageBranchesIndex(db)
    createMessageImagesTable(db)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) createMessageImagesTable(db)
    if (oldVersion < 3) migrateToMessageBranches(db)
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
    val nodes =
      readableDatabase
        .query(
          "messages",
          arrayOf(
            "id",
            "conversation_id",
            "role",
            "content",
            "created_at",
            "parent_message_id",
            "selected_child_message_id",
          ),
          "conversation_id = ?",
          arrayOf(conversationId.toString()),
          null,
          null,
          "created_at ASC, id ASC",
        ).use { cursor ->
          buildList {
            while (cursor.moveToNext()) {
              add(
                MessageNode(
                  id = cursor.getLong(0),
                  conversationId = cursor.getLong(1),
                  role = MessageRole.fromWireName(cursor.getString(2)),
                  content = cursor.getString(3),
                  createdAt = cursor.getLong(4),
                  parentId = cursor.getLongOrNull(5),
                  selectedChildId = cursor.getLongOrNull(6),
                )
              )
            }
          }
        }
    val selectedRootMessageId =
      readableDatabase
        .query(
          "conversations",
          arrayOf("selected_root_message_id"),
          "id = ?",
          arrayOf(conversationId.toString()),
          null,
          null,
          null,
        ).use { cursor ->
          if (cursor.moveToFirst()) cursor.getLongOrNull(0) else null
        }
    return activeMessagePath(nodes, selectedRootMessageId, imagesByMessage)
  }

  fun message(id: Long): ChatMessage? {
    val message =
      readableDatabase
        .query(
          "messages",
          arrayOf("id", "conversation_id", "role", "content", "created_at", "parent_message_id"),
          "id = ?",
          arrayOf(id.toString()),
          null,
          null,
          null,
        ).use { cursor ->
          if (!cursor.moveToFirst()) return@use null
          ChatMessage(
            id = cursor.getLong(0),
            conversationId = cursor.getLong(1),
            role = MessageRole.fromWireName(cursor.getString(2)),
            content = cursor.getString(3),
            createdAt = cursor.getLong(4),
            parentId = cursor.getLongOrNull(5),
          )
        } ?: return null
    val images =
      readableDatabase
        .query(
          "message_images",
          arrayOf("source", "mime_type"),
          "message_id = ?",
          arrayOf(id.toString()),
          null,
          null,
          "id ASC",
        ).use { cursor ->
          buildList {
            while (cursor.moveToNext()) {
              add(MessageImage(source = cursor.getString(0), mimeType = cursor.getString(1)))
            }
          }
        }
    return message.copy(images = images)
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
    parentMessageId: Long?,
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
            if (parentMessageId == null) putNull("parent_message_id")
            else put("parent_message_id", parentMessageId)
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
      if (parentMessageId == null) {
        db.update(
          "conversations",
          ContentValues().apply { put("selected_root_message_id", id) },
          "id = ?",
          arrayOf(conversationId.toString()),
        )
      } else {
        val updated =
          db.update(
            "messages",
            ContentValues().apply { put("selected_child_message_id", id) },
            "id = ? AND conversation_id = ?",
            arrayOf(parentMessageId.toString(), conversationId.toString()),
          )
        require(updated == 1) { "Parent message $parentMessageId does not belong to conversation $conversationId" }
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

  fun selectSibling(messageId: Long, offset: Int): Boolean {
    if (offset == 0) return false
    val db = writableDatabase
    val current =
      db.query(
        "messages",
        arrayOf("conversation_id", "parent_message_id"),
        "id = ?",
        arrayOf(messageId.toString()),
        null,
        null,
        null,
      ).use { cursor ->
        if (!cursor.moveToFirst()) return false
        cursor.getLong(0) to cursor.getLongOrNull(1)
      }
    val siblings =
      db.query(
        "messages",
        arrayOf("id"),
        if (current.second == null) "conversation_id = ? AND parent_message_id IS NULL"
        else "conversation_id = ? AND parent_message_id = ?",
        if (current.second == null) arrayOf(current.first.toString())
        else arrayOf(current.first.toString(), current.second.toString()),
        null,
        null,
        "created_at ASC, id ASC",
      ).use { cursor ->
        buildList {
          while (cursor.moveToNext()) add(cursor.getLong(0))
        }
      }
    val currentIndex = siblings.indexOf(messageId)
    if (currentIndex == -1) return false
    val targetId = siblings.getOrNull(currentIndex + offset) ?: return false
    if (current.second == null) {
      db.update(
        "conversations",
        ContentValues().apply { put("selected_root_message_id", targetId) },
        "id = ?",
        arrayOf(current.first.toString()),
      )
    } else {
      db.update(
        "messages",
        ContentValues().apply { put("selected_child_message_id", targetId) },
        "id = ?",
        arrayOf(current.second.toString()),
      )
    }
    return true
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
    const val DATABASE_VERSION = 3
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

  private fun createMessageBranchesIndex(db: SQLiteDatabase) {
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS messages_parent ON messages(conversation_id, parent_message_id, created_at, id)"
    )
  }

  private fun migrateToMessageBranches(db: SQLiteDatabase) {
    db.execSQL("ALTER TABLE conversations ADD COLUMN selected_root_message_id INTEGER")
    db.execSQL("ALTER TABLE messages ADD COLUMN parent_message_id INTEGER")
    db.execSQL("ALTER TABLE messages ADD COLUMN selected_child_message_id INTEGER")
    createMessageBranchesIndex(db)

    val conversationIds =
      db.query("conversations", arrayOf("id"), null, null, null, null, null).use { cursor ->
        buildList {
          while (cursor.moveToNext()) add(cursor.getLong(0))
        }
      }
    conversationIds.forEach { conversationId ->
      val messageIds =
        db.query(
          "messages",
          arrayOf("id"),
          "conversation_id = ?",
          arrayOf(conversationId.toString()),
          null,
          null,
          "created_at ASC, id ASC",
        ).use { cursor ->
          buildList {
            while (cursor.moveToNext()) add(cursor.getLong(0))
          }
        }
      messageIds.forEachIndexed { index, messageId ->
        db.update(
          "messages",
          ContentValues().apply {
            messageIds.getOrNull(index - 1)?.let { put("parent_message_id", it) }
              ?: putNull("parent_message_id")
            messageIds.getOrNull(index + 1)?.let { put("selected_child_message_id", it) }
              ?: putNull("selected_child_message_id")
          },
          "id = ?",
          arrayOf(messageId.toString()),
        )
      }
      messageIds.firstOrNull()?.let { rootMessageId ->
        db.update(
          "conversations",
          ContentValues().apply { put("selected_root_message_id", rootMessageId) },
          "id = ?",
          arrayOf(conversationId.toString()),
        )
      }
    }
  }
}

internal data class MessageNode(
  val id: Long,
  val conversationId: Long,
  val role: MessageRole,
  val content: String,
  val createdAt: Long,
  val parentId: Long?,
  val selectedChildId: Long?,
)

internal fun activeMessagePath(
  nodes: List<MessageNode>,
  selectedRootMessageId: Long?,
  imagesByMessage: Map<Long, List<MessageImage>> = emptyMap(),
): List<ChatMessage> {
  val nodesById = nodes.associateBy { it.id }
  val siblingsByParent =
    nodes.sortedWith(compareBy(MessageNode::createdAt, MessageNode::id)).groupBy { it.parentId }
  val visited = mutableSetOf<Long>()
  return buildList {
    var messageId = selectedRootMessageId
    while (messageId != null && visited.add(messageId)) {
      val node = nodesById[messageId] ?: break
      val siblings = siblingsByParent[node.parentId].orEmpty()
      add(
        ChatMessage(
          id = node.id,
          conversationId = node.conversationId,
          role = node.role,
          content = node.content,
          createdAt = node.createdAt,
          images = imagesByMessage[node.id].orEmpty(),
          parentId = node.parentId,
          branchIndex = siblings.indexOfFirst { it.id == node.id }.coerceAtLeast(0),
          branchCount = siblings.size.coerceAtLeast(1),
        )
      )
      messageId = node.selectedChildId?.takeIf { childId -> nodesById[childId]?.parentId == node.id }
    }
  }
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
  if (isNull(index)) null else getLong(index)
