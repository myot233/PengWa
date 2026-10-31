package app.pengwa.contacts

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 集邮册的本地存储。直接用系统自带的 SQLite，不引入 Room（省体积和注解处理器）。
 * 所有写入都在 IO 线程完成，并刷新 [contacts]，界面只需订阅这个流。
 */
class ContactStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "pengwa.db", null, 1) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE contacts (
                id TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL,
                first_met_at INTEGER NOT NULL,
                last_met_at INTEGER NOT NULL,
                met_count INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_contacts_last_met ON contacts(last_met_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun load() = withContext(Dispatchers.IO) {
        _contacts.value = queryAll()
    }

    /** 记录一次相遇：新朋友插入；老朋友更新名字、最近时间并把次数加一。 */
    suspend fun recordMeeting(id: UUID, displayName: String, at: Long) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val updated = db.compileStatement(
                "UPDATE contacts SET display_name = ?, last_met_at = ?, met_count = met_count + 1 WHERE id = ?",
            ).use { statement ->
                statement.bindString(1, displayName)
                statement.bindLong(2, at)
                statement.bindString(3, id.toString())
                statement.executeUpdateDelete()
            }
            if (updated == 0) {
                val values = ContentValues().apply {
                    put("id", id.toString())
                    put("display_name", displayName)
                    put("first_met_at", at)
                    put("last_met_at", at)
                    put("met_count", 1)
                }
                db.insertOrThrow("contacts", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _contacts.value = queryAll()
    }

    private fun queryAll(): List<Contact> {
        val result = mutableListOf<Contact>()
        readableDatabase.rawQuery(
            "SELECT id, display_name, first_met_at, last_met_at, met_count FROM contacts ORDER BY last_met_at DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Contact(
                    id = UUID.fromString(cursor.getString(0)),
                    displayName = cursor.getString(1),
                    firstMetAt = cursor.getLong(2),
                    lastMetAt = cursor.getLong(3),
                    metCount = cursor.getInt(4),
                )
            }
        }
        return result
    }
}
