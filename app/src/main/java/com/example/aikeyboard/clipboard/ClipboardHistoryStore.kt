package com.example.aikeyboard.clipboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local text-only clipboard history.
 *
 * Recent and pinned are represented by one canonical row per full text. Pinning changes the
 * row's state instead of copying it, so pinned items never consume the recent limit.
 */
data class ClipboardItem(
    val id: Long,
    val fullText: String,
    val lastUsedAt: Long,
    val isPinned: Boolean,
) {
    fun preview(maxChars: Int = 140): String {
        val compact = fullText.replace(Regex("\\s+"), " ").trim()
        if (compact.length <= maxChars) return compact
        return compact.take((maxChars - 1).coerceAtLeast(1)).trimEnd() + "…"
    }
}

class ClipboardHistoryStore(context: Context) :
    SQLiteOpenHelper(
        context.applicationContext,
        DATABASE_NAME,
        null,
        DATABASE_VERSION,
    ) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FULL_TEXT TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_LAST_USED_AT INTEGER NOT NULL,
                $COL_IS_PINNED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_clipboard_recent ON $TABLE($COL_IS_PINNED, $COL_LAST_USED_AT DESC)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // V1 has no migration. Future schema changes must preserve pinned rows explicitly.
    }

    @Synchronized
    fun recordCopied(fullText: String): Boolean {
        if (fullText.isBlank() || isLikelySensitive(fullText)) return false
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = findByText(db, fullText)
            if (existing == null) {
                db.insertOrThrow(
                    TABLE,
                    null,
                    ContentValues().apply {
                        put(COL_FULL_TEXT, fullText)
                        put(COL_CREATED_AT, now)
                        put(COL_LAST_USED_AT, now)
                        put(COL_IS_PINNED, 0)
                    },
                )
            } else {
                db.update(
                    TABLE,
                    ContentValues().apply {
                        put(COL_LAST_USED_AT, now)
                        if (!existing.isPinned) put(COL_CREATED_AT, now)
                    },
                    "$COL_ID = ?",
                    arrayOf(existing.id.toString()),
                )
            }
            pruneRecent(db)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun recent(limit: Int = MAX_RECENT_ITEMS): List<ClipboardItem> =
        query(
            selection = "$COL_IS_PINNED = 0",
            selectionArgs = emptyArray(),
            orderBy = "$COL_LAST_USED_AT DESC, $COL_ID DESC",
            limit = limit.coerceIn(1, MAX_RECENT_ITEMS),
        )

    @Synchronized
    fun pinned(offset: Int = 0, limit: Int = DEFAULT_PINNED_PAGE_SIZE): List<ClipboardItem> =
        query(
            selection = "$COL_IS_PINNED = 1",
            selectionArgs = emptyArray(),
            orderBy = "$COL_LAST_USED_AT DESC, $COL_ID DESC",
            limit = limit.coerceIn(1, MAX_PINNED_PAGE_SIZE),
            offset = offset.coerceAtLeast(0),
        )

    @Synchronized
    fun pin(id: Long): Boolean = updatePinState(id, true)

    @Synchronized
    fun unpin(id: Long): Boolean {
        val updated = updatePinState(id, false)
        if (updated) pruneRecent(writableDatabase)
        return updated
    }

    @Synchronized
    fun delete(id: Long): Boolean =
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString())) > 0

    @Synchronized
    fun clearRecent() {
        writableDatabase.delete(TABLE, "$COL_IS_PINNED = 0", null)
    }

    @Synchronized
    fun clearPinned() {
        writableDatabase.delete(TABLE, "$COL_IS_PINNED = 1", null)
    }

    @Synchronized
    fun clearAll() {
        writableDatabase.delete(TABLE, null, null)
    }

    private fun updatePinState(id: Long, pinned: Boolean): Boolean {
        val values = ContentValues().apply {
            put(COL_IS_PINNED, if (pinned) 1 else 0)
            put(COL_LAST_USED_AT, System.currentTimeMillis())
        }
        return writableDatabase.update(
            TABLE,
            values,
            "$COL_ID = ?",
            arrayOf(id.toString()),
        ) > 0
    }

    private fun pruneRecent(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM $TABLE
            WHERE $COL_IS_PINNED = 0
              AND $COL_ID NOT IN (
                  SELECT $COL_ID FROM $TABLE
                  WHERE $COL_IS_PINNED = 0
                  ORDER BY $COL_LAST_USED_AT DESC, $COL_ID DESC
                  LIMIT $MAX_RECENT_ITEMS
              )
            """.trimIndent(),
        )
    }

    private fun query(
        selection: String,
        selectionArgs: Array<String>,
        orderBy: String,
        limit: Int,
        offset: Int = 0,
    ): List<ClipboardItem> {
        val boundedLimit = limit.coerceIn(1, MAX_PINNED_PAGE_SIZE)
        val boundedOffset = offset.coerceAtLeast(0)
        val limitClause = if (offset > 0) "$boundedLimit OFFSET $boundedOffset" else boundedLimit.toString()
        return readableDatabase.query(
            TABLE,
            COLUMNS,
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limitClause,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(readItem(cursor))
            }
        }
    }

    private fun readItem(cursor: android.database.Cursor): ClipboardItem =
        ClipboardItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            fullText = cursor.getString(cursor.getColumnIndexOrThrow(COL_FULL_TEXT)),
            lastUsedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_USED_AT)),
            isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_PINNED)) != 0,
        )

    private data class ExistingItem(val id: Long, val isPinned: Boolean)

    private fun findByText(db: SQLiteDatabase, text: String): ExistingItem? =
        db.query(
            TABLE,
            arrayOf(COL_ID, COL_IS_PINNED),
            "$COL_FULL_TEXT = ?",
            arrayOf(text),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            ExistingItem(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_PINNED)) != 0,
            )
        }

    companion object {
        const val MAX_RECENT_ITEMS = 8
        const val DEFAULT_PINNED_PAGE_SIZE = 40
        private const val MAX_PINNED_PAGE_SIZE = 100

        private const val DATABASE_NAME = "clipboard_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE = "clipboard_items"
        private const val COL_ID = "id"
        private const val COL_FULL_TEXT = "full_text"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_LAST_USED_AT = "last_used_at"
        private const val COL_IS_PINNED = "is_pinned"

        private val COLUMNS = arrayOf(
            COL_ID,
            COL_FULL_TEXT,
            COL_LAST_USED_AT,
            COL_IS_PINNED,
        )

        private val SENSITIVE_MARKERS = Regex(
            "(?i)(password|passwd|passcode|pin|otp|one[- ]?time|verification|auth(?:entication)?|secret|token|api[_ -]?key|bearer)",
        )
        private val CREDENTIAL_SHAPE = Regex("^[A-Za-z0-9_+/.=-]{20,}$")

        /**
         * Conservative local-only screening. Android's explicit sensitivity flag is checked by
         * the IME before this method; this catches common pasted credentials as a second guard.
         */
        fun isLikelySensitive(value: String): Boolean {
            val text = value.trim()
            if (text.length in 4..12 && text.all(Char::isDigit)) return true
            if (SENSITIVE_MARKERS.containsMatchIn(text)) return true
            if (
                text.length >= 20 &&
                CREDENTIAL_SHAPE.matches(text) &&
                text.any(Char::isDigit) &&
                text.any { !it.isLetterOrDigit() }
            ) return true
            return false
        }
    }
}
