package app.pengwa.ble

import app.pengwa.identity.Identity

/** 被申请方对一次申请的处理状态。[code] 是通过「状态特征」传给申请方的单字节编码。 */
enum class ExchangeStatus(val code: Byte) {
    /** 申请已送达，正在等被申请者决定。 */
    Pending(0),

    /** 被申请者同意了，申请方现在可以读取名片。 */
    Accepted(1),

    /** 被申请者拒绝了（或因为刚拒绝过这个人而自动拒绝）。 */
    Declined(2),

    /** 超时没有人处理。 */
    Expired(3),

    /** 这个连接还没有发出过申请。 */
    None(-1),
    ;

    companion object {
        fun fromCode(code: Byte): ExchangeStatus? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 蓝牙服务端的"申请—同意"状态机：别人要和我交换，必须先出示一张合法名片发出**申请**，
 * 我同意之后才允许它读取我的名片。纯 Kotlin，线程安全（蓝牙回调来自 Binder 线程，决定来自界面线程）。
 *
 * 状态流转：None → Pending → Accepted / Declined / Expired。只有 Pending 状态下才能做决定。
 * 与 NFC 私有协议的"先出示名片才给名片"一脉相承，只是多了一步被申请者的同意。
 *
 * [K] 是连接对象的标识（真机上是设备地址）。
 */
class GattExchangeSessions<K : Any>(private val capacity: Int = 16) {
    private class Entry(val peer: Identity, var status: ExchangeStatus)

    // 按插入顺序保存；超过容量时丢掉最早的，防止被大量连接撑爆内存
    private val entries = LinkedHashMap<K, Entry>()

    /** 对方发来申请：名片合法就记为 Pending 并返回对方的名片；不合法返回 null 且不放行。 */
    @Synchronized
    fun onRequest(key: K, data: ByteArray?): Identity? {
        val peer = decodeIdentity(data) ?: return null
        entries.remove(key)
        entries[key] = Entry(peer, ExchangeStatus.Pending)
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return peer
    }

    @Synchronized
    fun status(key: K): ExchangeStatus = entries[key]?.status ?: ExchangeStatus.None

    @Synchronized
    fun peerOf(key: K): Identity? = entries[key]?.peer

    /** 被申请者做出决定。只有 Pending 才生效，成功返回对方的名片，否则返回 null。 */
    @Synchronized
    fun decide(key: K, accept: Boolean): Identity? {
        val entry = entries[key] ?: return null
        if (entry.status != ExchangeStatus.Pending) return null
        entry.status = if (accept) ExchangeStatus.Accepted else ExchangeStatus.Declined
        return entry.peer
    }

    /** 超时：Pending 变为 Expired。返回是否真的变了。 */
    @Synchronized
    fun expire(key: K): Boolean {
        val entry = entries[key] ?: return false
        if (entry.status != ExchangeStatus.Pending) return false
        entry.status = ExchangeStatus.Expired
        return true
    }

    /** 只有同意了才允许读取我的名片。 */
    @Synchronized
    fun canRead(key: K): Boolean = entries[key]?.status == ExchangeStatus.Accepted

    /** 连接断开：移除记录。返回断开时是否还有未决定的申请（界面据此撤掉提示）。 */
    @Synchronized
    fun onDisconnected(key: K): Boolean = entries.remove(key)?.status == ExchangeStatus.Pending
}
