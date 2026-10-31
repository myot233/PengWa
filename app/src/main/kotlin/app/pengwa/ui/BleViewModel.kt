package app.pengwa.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.pengwa.ble.BleExchangeManager
import app.pengwa.identity.Identity
import app.pengwa.pengWa
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val identities = application.pengWa.identityRepository
    private val meetings = application.pengWa.meetings
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val logLines = mutableStateListOf<String>()
    var advertising by mutableStateOf(false)
        private set
    var scanning by mutableStateOf(false)
        private set

    private val manager = BleExchangeManager(
        context = application,
        identityProvider = { identities.load() },
        onPeer = ::onPeer,
        log = ::log,
    )

    fun log(line: String) {
        // 蓝牙回调不在主线程，状态列表只能在主线程改
        mainHandler.post {
            logLines.add(0, "${timeFormat.format(Date())}  $line")
            while (logLines.size > MAX_LOG_LINES) logLines.removeAt(logLines.lastIndex)
        }
    }

    fun toggleAdvertising() {
        if (manager.isAdvertising) manager.stopAdvertising() else manager.startAdvertising()
        advertising = manager.isAdvertising
    }

    fun toggleScanning() {
        if (manager.isScanning) manager.stopScanning() else manager.startScanning()
        scanning = manager.isScanning
    }

    private fun onPeer(peer: Identity, how: String) {
        val recorded = meetings.record(peer, "蓝牙：$how")
        log(
            if (recorded) {
                "收到名片：${peer.displayName}（${peer.id.toString().take(8)}）— $how"
            } else {
                "忽略：自己的名片或刚刚已经碰过"
            },
        )
    }

    override fun onCleared() {
        manager.stopAll()
    }

    private companion object {
        const val MAX_LOG_LINES = 100
    }
}
