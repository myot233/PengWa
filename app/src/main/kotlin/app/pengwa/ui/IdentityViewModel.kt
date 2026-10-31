package app.pengwa.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.pengwa.identity.Identity
import app.pengwa.identity.NameError
import app.pengwa.identity.normalizeDisplayName
import app.pengwa.identity.validateDisplayName
import app.pengwa.notification.PengWaNotification
import app.pengwa.pengWa

class IdentityViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.pengWa.identityRepository

    var identity: Identity? by mutableStateOf(repository.load())
        private set

    /** 保存显示名；通过校验返回 null，否则返回错误原因且不保存。存的是规范化后的名字。 */
    fun saveDisplayName(raw: String): NameError? {
        validateDisplayName(raw)?.let { return it }
        repository.saveDisplayName(normalizeDisplayName(raw))
        identity = repository.load()
        PengWaNotification.refresh(getApplication()) // 通知栏上的名字同步更新
        return null
    }
}
