package app.pengwa.identity

import android.content.Context
import java.util.UUID

/**
 * 身份的本地存储。数据量只有两个字符串，用 SharedPreferences 即可，不额外引入依赖。
 * 清除应用数据或卸载重装后会生成新的 GUID（清单里已关闭 allowBackup）。
 */
class IdentityRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("identity", Context.MODE_PRIVATE)

    /** 首次访问时生成并持久化，之后永远返回同一个值。 */
    val id: UUID by lazy {
        prefs.getString(KEY_ID, null)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { prefs.edit().putString(KEY_ID, it.toString()).commit() }
    }

    /** 还没设置过显示名时返回 null，界面据此进入首次设置。 */
    fun load(): Identity? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return Identity(id, name)
    }

    fun saveDisplayName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "display_name"
    }
}
