package app.pengwa.ble

import app.pengwa.identity.Identity
import java.util.UUID

/** 一条等待我处理的交换申请。[key] 是申请方的连接标识（设备地址）。 */
data class IncomingRequest(
    val key: String,
    val peer: Identity,
    val receivedAtMillis: Long,
    /** 仅调试用：模拟出来的申请，没有真实的蓝牙连接。 */
    val simulated: Boolean = false,
)

/** 收到申请后该怎么处理。 */
sealed interface Submission {
    /** 弹出提示，让用户决定。 */
    data object Prompt : Submission

    /** 直接拒绝，不打扰用户。 */
    data class Decline(val reason: DeclineReason) : Submission
}

enum class DeclineReason {
    /** 这个人刚被我拒绝过，冷却期内再申请自动拒绝。 */
    RecentlyDeclined,

    /** 同时等待处理的申请太多了，防止被骚扰。 */
    TooManyPending,
}

/**
 * 被申请方的待处理申请队列与防骚扰规则。纯 Kotlin，时间由调用方传入。
 *  - 同时最多 [maxPending] 条待处理，超出的直接拒绝；
 *  - 拒绝过的人（按名片里的 ID 认人，不是按设备地址，地址可能会变）在 [declineCooldownMillis] 内再申请，自动拒绝；
 *  - 同一个连接重复申请只保留一条；
 *  - 超过 [expireAfterMillis] 没处理的算过期。
 */
class IncomingRequests(
    private val maxPending: Int = 3,
    private val declineCooldownMillis: Long = 10 * 60_000L,
    val expireAfterMillis: Long = 60_000L,
) {
    private val pending = LinkedHashMap<String, IncomingRequest>()
    private val declinedAt = HashMap<UUID, Long>()

    @Synchronized
    fun submit(request: IncomingRequest): Submission {
        val declined = declinedAt[request.peer.id]
        if (declined != null && request.receivedAtMillis - declined < declineCooldownMillis) {
            return Submission.Decline(DeclineReason.RecentlyDeclined)
        }
        if (request.key !in pending && pending.size >= maxPending) {
            return Submission.Decline(DeclineReason.TooManyPending)
        }
        pending[request.key] = request
        return Submission.Prompt
    }

    /** 用户做出决定。返回这条申请（不存在返回 null）。拒绝会进入冷却。 */
    @Synchronized
    fun decide(key: String, accept: Boolean, now: Long): IncomingRequest? {
        val request = pending.remove(key) ?: return null
        if (!accept) declinedAt[request.peer.id] = now
        return request
    }

    /** 对方撤销或断开：直接移除，不算拒绝，不进冷却。 */
    @Synchronized
    fun cancel(key: String): IncomingRequest? = pending.remove(key)

    /** 取出所有已过期的申请并移除。 */
    @Synchronized
    fun takeExpired(now: Long): List<IncomingRequest> {
        val expired = pending.values.filter { now - it.receivedAtMillis >= expireAfterMillis }
        expired.forEach { pending.remove(it.key) }
        return expired
    }

    @Synchronized
    fun clear() {
        pending.clear()
    }

    /** 按收到的先后顺序。 */
    @Synchronized
    fun snapshot(): List<IncomingRequest> = pending.values.sortedBy { it.receivedAtMillis }
}

/** 本次应该做什么：是否广播、是否扫描。 */
data class BlePlan(val advertise: Boolean, val scan: Boolean) {
    companion object {
        val Off = BlePlan(advertise = false, scan = false)
    }
}

/**
 * 蓝牙交换什么时候该运行：
 *  - 主页停在蓝牙页，并且三项蓝牙权限齐全：广播 + 扫描（前台，低延迟）；
 *  - 否则，用户在设置里打开了"后台广播"，并且有广播权限：只广播（后台，被动等别人来申请，不扫描）；
 *  - 系统蓝牙没开，或者以上都不满足：全部关闭。
 */
object BleRunPolicy {
    fun plan(
        pageActive: Boolean,
        backgroundBroadcast: Boolean,
        allPermitted: Boolean,
        advertisePermitted: Boolean,
        bluetoothOn: Boolean,
    ): BlePlan = when {
        !bluetoothOn -> BlePlan.Off
        pageActive && allPermitted -> BlePlan(advertise = true, scan = true)
        backgroundBroadcast && advertisePermitted -> BlePlan(advertise = true, scan = false)
        else -> BlePlan.Off
    }
}
