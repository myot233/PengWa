package app.pengwa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pengwa.pengWa
import java.util.UUID
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.pengWa.contactStore

    val contacts = store.contacts

    init {
        viewModelScope.launch { store.load() }
    }

    /** 调试用：塞几条假数据，方便在只有一台手机时检查列表界面。 */
    fun addDemoContacts() {
        val names = listOf("小熊", "Nana", "兔兔", "Kiyo", "狐狸娃")
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            names.forEachIndexed { index, name ->
                val dayMillis = 24L * 60 * 60 * 1000
                store.recordMeeting(UUID.randomUUID(), name, now - (index + 1) * dayMillis)
            }
            // 让第一个多碰两次，检查"遇见 N 次"的显示
            val first = store.contacts.value.firstOrNull() ?: return@launch
            repeat(2) { store.recordMeeting(first.id, first.displayName, now) }
        }
    }
}
