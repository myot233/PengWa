package app.pengwa.meeting

import android.os.SystemClock
import app.pengwa.contacts.ContactStore
import app.pengwa.identity.Identity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** 一次成功的相遇，界面据此提示"碰到了 xxx"。[via] 是来源（NFC / 蓝牙），仅用于调试日志。 */
data class MeetingEvent(val name: String, val via: String)

/**
 * 同一个人在窗口时间内只算一次相遇：两部手机贴在一起时，NFC 可能连续触发好几次，
 * 不能让"遇见次数"因此虚高。纯逻辑，时间由调用方传入。
 */
class MeetingDebouncer(private val windowMillis: Long = 10_000L) {
    private val lastSeen = HashMap<UUID, Long>()

    @Synchronized
    fun shouldRecord(id: UUID, nowMillis: Long): Boolean {
        val previous = lastSeen[id]
        if (previous != null && nowMillis - previous < windowMillis) return false
        lastSeen[id] = nowMillis
        return true
    }
}

/**
 * NFC 和蓝牙收到名片后统一走这里：排除自己、防抖、写入集邮册、通知界面。
 * 可能被系统服务（HCE）在后台线程调用，所以写库放在应用级协程里。
 */
class MeetingRecorder(
    private val store: ContactStore,
    private val selfId: () -> UUID,
    private val scope: CoroutineScope,
    private val debouncer: MeetingDebouncer = MeetingDebouncer(),
) {
    private val _events = MutableSharedFlow<MeetingEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MeetingEvent> = _events.asSharedFlow()

    /** 返回 true 表示这次算有效相遇并已开始记录；自己的名片和重复触发返回 false。 */
    fun record(peer: Identity, via: String): Boolean {
        if (peer.id == selfId()) return false
        if (!debouncer.shouldRecord(peer.id, SystemClock.elapsedRealtime())) return false
        scope.launch {
            store.recordMeeting(peer.id, peer.displayName, System.currentTimeMillis())
            _events.emit(MeetingEvent(peer.displayName, via))
        }
        return true
    }
}
