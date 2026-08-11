package com.deepseek.personal.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HistoryStore(context: Context) {

    private val db = DbHelper(context)

    fun listConversations(): List<Conversation> {
        val out = mutableListOf<Conversation>()
        db.readableDatabase.query(
            "conversations", null, "deleted_at IS NULL", null, null, null, "updated_at DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out += Conversation(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                    model = c.getString(c.getColumnIndexOrThrow("model")),
                    thinking = c.getInt(c.getColumnIndexOrThrow("thinking")) == 1
                )
            }
        }
        return out
    }

    fun createConversation(title: String, model: String, thinking: Boolean): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", title)
            put("created_at", now)
            put("updated_at", now)
            put("model", model)
            put("thinking", if (thinking) 1 else 0)
        }
        return db.writableDatabase.insert("conversations", null, values)
    }

    fun deleteConversation(id: Long) {
        // 软删除：移入回收站，5 分钟后由 purgeExpiredTrash 彻底删除
        db.writableDatabase.update(
            "conversations",
            ContentValues().apply { put("deleted_at", System.currentTimeMillis()) },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun restoreConversation(id: Long) {
        db.writableDatabase.update(
            "conversations",
            ContentValues().apply { putNull("deleted_at") },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun listTrash(): List<Pair<Conversation, Long>> {
        val out = mutableListOf<Pair<Conversation, Long>>()
        db.readableDatabase.query(
            "conversations", null, "deleted_at IS NOT NULL", null, null, null, "deleted_at DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out += Conversation(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                    model = c.getString(c.getColumnIndexOrThrow("model")),
                    thinking = c.getInt(c.getColumnIndexOrThrow("thinking")) == 1
                ) to c.getLong(c.getColumnIndexOrThrow("deleted_at"))
            }
        }
        return out
    }

    /** 彻底删除超过 ttl 的回收站会话。 */
    fun purgeExpiredTrash(now: Long, ttl: Long) {
        val cutoff = now - ttl
        val w = db.writableDatabase
        val expired = mutableListOf<Long>()
        w.query(
            "conversations", arrayOf("id"), "deleted_at IS NOT NULL AND deleted_at < ?",
            arrayOf(cutoff.toString()), null, null, null
        ).use { c ->
            while (c.moveToNext()) expired += c.getLong(0)
        }
        expired.forEach { id ->
            w.delete("messages", "conv_id = ?", arrayOf(id.toString()))
            w.delete("conversations", "id = ?", arrayOf(id.toString()))
        }
    }

    fun deleteConversationHard(id: Long) {
        val w = db.writableDatabase
        w.delete("messages", "conv_id = ?", arrayOf(id.toString()))
        w.delete("conversations", "id = ?", arrayOf(id.toString()))
    }

    fun renameConversation(id: Long, title: String) {
        db.writableDatabase.update(
            "conversations",
            ContentValues().apply {
                put("title", title)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun touchConversation(id: Long) {
        db.writableDatabase.update(
            "conversations",
            ContentValues().apply { put("updated_at", System.currentTimeMillis()) },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun updateConversationModel(id: Long, model: String, thinking: Boolean) {
        db.writableDatabase.update(
            "conversations",
            ContentValues().apply {
                put("model", model)
                put("thinking", if (thinking) 1 else 0)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun loadMessages(convId: Long): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        db.readableDatabase.query(
            "messages", null, "conv_id = ?", arrayOf(convId.toString()),
            null, null, "id ASC"
        ).use { c ->
            while (c.moveToNext()) {
                out += ChatMessage(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    role = c.getString(c.getColumnIndexOrThrow("role")),
                    content = c.getString(c.getColumnIndexOrThrow("content")) ?: "",
                    reasoning = c.getString(c.getColumnIndexOrThrow("reasoning")) ?: "",
                    timestamp = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return out
    }

    fun insertMessage(convId: Long, msg: ChatMessage): Long {
        val values = ContentValues().apply {
            put("conv_id", convId)
            put("role", msg.role)
            put("content", msg.content)
            put("reasoning", msg.reasoning)
            put("created_at", msg.timestamp)
        }
        return db.writableDatabase.insert("messages", null, values)
    }

    fun updateMessage(id: Long, content: String, reasoning: String) {
        db.writableDatabase.update(
            "messages",
            ContentValues().apply {
                put("content", content)
                put("reasoning", reasoning)
            },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun deleteMessage(id: Long) {
        db.writableDatabase.delete("messages", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        val w = db.writableDatabase
        w.delete("messages", null, null)
        w.delete("conversations", null, null)
    }

    fun listMemories(): List<Memory> {
        val out = mutableListOf<Memory>()
        db.readableDatabase.query(
            "memories", null, null, null, null, null, "created_at DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out += Memory(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    content = c.getString(c.getColumnIndexOrThrow("content")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return out
    }

    fun insertMemory(content: String): Long {
        val values = ContentValues().apply {
            put("content", content)
            put("created_at", System.currentTimeMillis())
        }
        return db.writableDatabase.insert("memories", null, values)
    }

    fun deleteMemory(id: Long) {
        db.writableDatabase.delete("memories", "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllMemories() {
        db.writableDatabase.delete("memories", null, null)
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, "deepseek.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE conversations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    model TEXT NOT NULL,
                    thinking INTEGER NOT NULL DEFAULT 1,
                    deleted_at INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conv_id INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    reasoning TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_messages_conv ON messages(conv_id)")
            db.execSQL(
                """
                CREATE TABLE memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN deleted_at INTEGER")
            }
        }
    }
}
