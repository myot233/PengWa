package app.pengwa

import android.app.Application
import android.content.Context
import app.pengwa.contacts.ContactStore
import app.pengwa.identity.IdentityRepository
import app.pengwa.meeting.MeetingRecorder
import app.pengwa.nfc.TagShareGate
import app.pengwa.notification.PengWaNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 全应用共享的单例都挂在这里。身份、集邮册、相遇记录器各只有一份，
 * 避免多个入口（界面、蓝牙、NFC 服务、通知）各建一份造成首次启动时 GUID 生成竞争。
 */
class PengWaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val identityRepository: IdentityRepository by lazy { IdentityRepository(this) }
    val contactStore: ContactStore by lazy { ContactStore(this) }
    val meetings: MeetingRecorder by lazy {
        MeetingRecorder(contactStore, selfId = { identityRepository.id }, scope = applicationScope)
    }

    /** "展示名片"开关：名片页在前台时才让别人的手机读到我们的标签。 */
    val tagShareGate = TagShareGate()

    override fun onCreate() {
        super.onCreate()
        // 进程被系统杀掉后重新启动时，恢复用户开启的常驻通知
        PengWaNotification.refresh(this)
    }
}

/** 任意 Context 取到应用单例。 */
val Context.pengWa: PengWaApplication
    get() = applicationContext as PengWaApplication
