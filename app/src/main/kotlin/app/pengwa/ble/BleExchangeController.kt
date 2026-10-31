package app.pengwa.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.pengwa.identity.Identity
import app.pengwa.notification.PengWaNotification
import app.pengwa.notification.RequestNotifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 蓝牙交换的总控：全应用只有这一个，持有唯一的 [BleExchangeManager]。
 *
 * 什么时候运行由 [BleRunPolicy.plan] 决定，有两种场景：
 *  - **蓝牙页**（前台）：广播 + 扫描，把扫到的人整理成 [nearby] 列表，用户点选某人就向他**申请**交换；
 *  - **后台广播**（用户在设置里打开，由前台服务保活）：只广播、不扫描，等别人来申请。
 *
 * **被申请的一方必须同意才会交换**：收到申请进入 [incoming]，应用在前台时界面弹对话框，
 * 在后台时发带"同意 / 拒绝"按钮的通知；调用 [respond] 做决定。NFC 和二维码不需要这一步。
 *
 * 所有状态变化入口都收敛到 [refresh]，可重复调用。[setPageActive] / [refresh] / [exchangeWith] / [respond]
 * 必须在主线程调用。
 */
class BleExchangeController(
    context: Context,
    identityProvider: () -> Identity?,
    /** 对方名片到手时回调；返回 true 表示算一次有效相遇（自己的名片或刚碰过的返回 false）。 */
    private val onPeer: (peer: Identity, how: String) -> Boolean,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val list = NearbyList()
    private val requests = IncomingRequests()
    private val simulatedCounter = AtomicInteger()

    private val _nearby = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val nearby: StateFlow<List<NearbyPeer>> = _nearby.asStateFlow()

    private val _incoming = MutableStateFlow<List<IncomingRequest>>(emptyList())

    /** 等我决定的交换申请，按收到的先后排列。 */
    val incoming: StateFlow<List<IncomingRequest>> = _incoming.asStateFlow()

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _bluetoothOn = MutableStateFlow(isBluetoothOn())
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    private val manager = BleExchangeManager(
        context = appContext,
        identityProvider = identityProvider,
        listener = object : BleExchangeListener {
            override fun onDeviceSeen(address: String, rssi: Int) {
                list.onSeen(address, rssi, SystemClock.elapsedRealtime())
                schedulePublish()
            }

            override fun onPreview(address: String, name: String?) {
                list.onPreview(address, name)
                schedulePublish()
            }

            override fun onExchangeWaiting(address: String) {
                list.onWaiting(address)
                schedulePublish()
            }

            override fun onExchanged(address: String, peer: Identity?, error: String?) {
                list.onExchanged(address, peer?.displayName, error)
                mainHandler.post {
                    if (peer != null) handlePeer(peer, "对方同意了我的申请") else log("交换失败：$error")
                }
                schedulePublish()
            }

            override fun onIncomingRequest(address: String, peer: Identity): RequestReply =
                submitIncoming(IncomingRequest(address, peer, SystemClock.elapsedRealtime()))

            override fun onIncomingRequestEnded(address: String) {
                if (requests.cancel(address) != null) {
                    mainHandler.post {
                        RequestNotifier.cancel(appContext, address)
                        publishIncoming()
                    }
                }
            }
        },
        log = ::log,
    )

    private var pageActive = false
    private var appForeground = false
    private val publishScheduled = AtomicBoolean(false)

    private val pruneTask = object : Runnable {
        override fun run() {
            if (list.prune(SystemClock.elapsedRealtime())) publish()
            if (manager.isScanning) mainHandler.postDelayed(this, PRUNE_INTERVAL_MS)
        }
    }

    init {
        // 系统蓝牙被开关时重新决定启停。随进程存活，不需要反注册
        ContextCompat.registerReceiver(
            appContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    refresh()
                }
            },
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    // ------------------------------------------------------------------ 启停

    /** 主页是否停在蓝牙页。停在这一页时才广播 + 扫描，并列出附近的人。 */
    fun setPageActive(value: Boolean) {
        if (value == pageActive) return
        pageActive = value
        refresh()
    }

    /** 应用回到前台 / 退到后台：前台用对话框提示申请，后台用通知提示。 */
    fun setAppForeground(value: Boolean) {
        if (value == appForeground) return
        appForeground = value
        requests.snapshot().forEach { if (value) RequestNotifier.cancel(appContext, it.key) else RequestNotifier.show(appContext, it) }
    }

    fun isBackgroundBroadcastEnabled(): Boolean = BroadcastSettings.isEnabled(appContext)

    /** 设置里的"后台蓝牙广播"开关：打开会启动前台服务并挂上常驻通知，关闭则停掉。 */
    fun setBackgroundBroadcast(enabled: Boolean) {
        BroadcastSettings.setEnabled(appContext, enabled)
        if (enabled) BleBroadcastService.start(appContext) else BleBroadcastService.stop(appContext)
        PengWaNotification.refresh(appContext)
        refresh()
    }

    fun observeBackgroundBroadcast(onChange: (Boolean) -> Unit): () -> Unit = BroadcastSettings.observe(appContext, onChange)

    fun refresh() {
        val bluetoothOn = isBluetoothOn()
        _bluetoothOn.value = bluetoothOn
        val plan = BleRunPolicy.plan(
            pageActive = pageActive,
            // 只有前台服务真的在跑，后台广播才算数（服务是它的保活凭证）
            backgroundBroadcast = BroadcastSettings.isEnabled(appContext) && BleBroadcastService.isRunning,
            allPermitted = BlePermissions.granted(appContext),
            advertisePermitted = BlePermissions.grantedForBroadcast(appContext),
            bluetoothOn = bluetoothOn,
        )
        runCatching {
            if (plan.advertise) {
                // 前台蓝牙页用低延迟，后台只广播用低功耗
                manager.startAdvertising(lowPower = !plan.scan)
            } else if (manager.isAdvertising) {
                manager.stopAdvertising()
                clearIncoming() // GATT 服务关了，没决定的申请连接已经断了
            }
            if (plan.scan) {
                if (!manager.isScanning && manager.startScanning()) mainHandler.postDelayed(pruneTask, PRUNE_INTERVAL_MS)
            } else if (manager.isScanning) {
                manager.stopScanning()
                list.clear()
                publish()
            }
        }.onFailure { log("蓝牙操作失败：${it.message}") }
        _running.value = manager.isAdvertising || manager.isScanning
    }

    // ------------------------------------------------------------------ 我去申请别人

    /** 用户在列表里点选了某个人：向他申请交换，等他同意。 */
    fun exchangeWith(address: String) {
        if (!list.beginExchange(address)) return
        publish()
        manager.requestExchange(address)
    }

    // ------------------------------------------------------------------ 别人来申请我

    /** 蓝牙线程调用：按防骚扰规则决定是提示用户还是直接拒绝。 */
    private fun submitIncoming(request: IncomingRequest): RequestReply =
        when (val result = requests.submit(request)) {
            Submission.Prompt -> {
                mainHandler.post { onRequestAdded(request) }
                RequestReply.Prompt
            }
            is Submission.Decline -> {
                log(
                    when (result.reason) {
                        DeclineReason.RecentlyDeclined -> "${request.peer.displayName} 刚被我拒绝过，自动拒绝"
                        DeclineReason.TooManyPending -> "待处理的申请太多，自动拒绝 ${request.peer.displayName}"
                    },
                )
                RequestReply.Decline
            }
        }

    private fun onRequestAdded(request: IncomingRequest) {
        publishIncoming()
        if (!appForeground) RequestNotifier.show(appContext, request)
        mainHandler.postDelayed(::expireOverdue, requests.expireAfterMillis + 500)
    }

    /** 用户（或通知按钮）对一条申请做出决定。同意时才把对方记入集邮册。 */
    fun respond(key: String, accept: Boolean) {
        val request = requests.decide(key, accept, SystemClock.elapsedRealtime()) ?: return
        RequestNotifier.cancel(appContext, key)
        val peer = if (request.simulated) request.peer else manager.decideRequest(key, accept)
        if (accept) {
            if (peer != null) handlePeer(peer, "我同意了对方的申请") else log("对方已经离开，没能完成交换")
        } else {
            log("已拒绝 ${request.peer.displayName} 的申请")
        }
        publishIncoming()
    }

    /** 超过期限没人处理的申请：让对方读到"没有回应"，并撤掉提示。 */
    private fun expireOverdue() {
        requests.takeExpired(SystemClock.elapsedRealtime()).forEach {
            manager.expireRequest(it.key)
            RequestNotifier.cancel(appContext, it.key)
            log("${it.peer.displayName} 的申请超时未处理")
        }
        publishIncoming()
    }

    private fun clearIncoming() {
        requests.snapshot().forEach { RequestNotifier.cancel(appContext, it.key) }
        requests.clear()
        publishIncoming()
    }

    /** 仅调试用：[delayMillis] 毫秒后模拟收到一个交换申请，方便在只有一台手机时验证界面和通知。 */
    fun debugSimulateIncomingRequest(name: String, delayMillis: Long) {
        mainHandler.postDelayed(
            {
                val request = IncomingRequest(
                    key = "sim-${simulatedCounter.incrementAndGet()}",
                    peer = Identity(UUID.randomUUID(), name),
                    receivedAtMillis = SystemClock.elapsedRealtime(),
                    simulated = true,
                )
                if (requests.submit(request) == Submission.Prompt) onRequestAdded(request)
            },
            delayMillis,
        )
    }

    // ------------------------------------------------------------------ 其它

    private fun handlePeer(peer: Identity, how: String) {
        val recorded = onPeer(peer, how)
        log(
            if (recorded) {
                "收到名片：${peer.displayName}（${peer.id.toString().take(8)}）— $how"
            } else {
                "忽略：自己的名片或刚刚已经碰过"
            },
        )
    }

    /** 扫描回调很频繁，合并成每 300 毫秒最多刷新一次界面，并顺便为新设备排队读取预览。 */
    private fun schedulePublish() {
        if (!publishScheduled.compareAndSet(false, true)) return
        mainHandler.postDelayed(
            {
                publishScheduled.set(false)
                if (manager.isScanning) list.takePreviewCandidates().forEach(manager::requestPreview)
                publish()
            },
            PUBLISH_DELAY_MS,
        )
    }

    private fun publish() {
        _nearby.value = list.snapshot()
    }

    private fun publishIncoming() {
        _incoming.value = requests.snapshot()
    }

    private fun isBluetoothOn(): Boolean =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    /** 日志可能来自蓝牙回调线程，StateFlow 的更新是线程安全的。 */
    private fun log(line: String) {
        val stamped = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}  $line"
        _logLines.update { (listOf(stamped) + it).take(MAX_LOG_LINES) }
    }

    private companion object {
        const val MAX_LOG_LINES = 100
        const val PUBLISH_DELAY_MS = 300L
        const val PRUNE_INTERVAL_MS = 3_000L
    }
}
