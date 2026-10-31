package app.pengwa.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import app.pengwa.identity.Identity

/**
 * BLE 名片交换的 Android 实现：
 *  - 服务端：广播服务 UUID，对方连上来后可读取我的名片、写入它的名片；
 *  - 客户端：扫描带该服务 UUID 的广播，连接后读取对方名片并把自己的名片写回去。
 * 广播里不放 ID 和名字（人人可见），连上之后才交换。
 *
 * 所有方法要求调用前已取得 [BlePermissions] 里的权限，因此整体抑制 MissingPermission。
 * 回调发生在系统的 Binder / 蓝牙线程，[onPeer] 和 [log] 的实现需要自行切线程。
 */
@SuppressLint("MissingPermission")
class BleExchangeManager(
    context: Context,
    private val identityProvider: () -> Identity?,
    private val onPeer: (peer: Identity, how: String) -> Unit,
    private val log: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private val lock = Any()
    private val activeClients = HashSet<String>()
    private val lastContactAt = HashMap<String, Long>()

    val isAdvertising: Boolean get() = advertiseCallback != null
    val isScanning: Boolean get() = scanCallback != null

    // ---------------------------------------------------------------- 广播 + GATT 服务端

    fun startAdvertising(): Boolean {
        if (advertiseCallback != null) return true
        val adapter = adapter?.takeIf { it.isEnabled }
            ?: return fail("蓝牙未开启")
        val advertiser = adapter.bluetoothLeAdvertiser
            ?: return fail("此设备不支持 BLE 广播")
        if (!openGattServer()) return false

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                log("广播已开始（连接模式=${settingsInEffect.isConnectable}）")
            }

            override fun onStartFailure(errorCode: Int) {
                log("广播失败：${advertiseErrorName(errorCode)}")
                advertiseCallback = null
                closeGattServer()
            }
        }
        advertiseCallback = callback
        advertiser.startAdvertising(settings, data, callback)
        return true
    }

    fun stopAdvertising() {
        advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
        advertiseCallback = null
        closeGattServer()
        log("广播已停止")
    }

    private fun openGattServer(): Boolean {
        if (gattServer != null) return true
        val server = bluetoothManager?.openGattServer(appContext, serverCallback)
            ?: return fail("无法打开 GATT 服务")
        val service = BluetoothGattService(
            BleProtocol.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleProtocol.IDENTITY_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                BleProtocol.EXCHANGE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        server.addService(service)
        gattServer = server
        return true
    }

    private fun closeGattServer() {
        gattServer?.close()
        gattServer = null
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            log("GATT 服务已注册（status=$status）")
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            log("[服务端] ${device.address} ${if (newState == BluetoothProfile.STATE_CONNECTED) "已连接" else "已断开"}")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val server = gattServer ?: return
            val value = identityProvider()?.let(::encodeIdentity)
            when {
                characteristic.uuid != BleProtocol.IDENTITY_CHAR_UUID ->
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                value == null ->
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                offset > value.size ->
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                else ->
                    server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            // 名片最长 49 字节，协商过 MTU 后一次写完；分段写（preparedWrite）不支持
            val peer = if (characteristic.uuid == BleProtocol.EXCHANGE_CHAR_UUID && !preparedWrite && offset == 0) {
                decodeIdentity(value)
            } else {
                null
            }
            if (responseNeeded) {
                val status = if (peer != null) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                gattServer?.sendResponse(device, requestId, status, offset, null)
            }
            if (peer != null) {
                onPeer(peer, "对方连上来并写入了名片")
            } else {
                log("[服务端] 收到不合法的名片数据，已拒绝")
            }
        }
    }

    // ---------------------------------------------------------------- 扫描 + GATT 客户端

    fun startScanning(): Boolean {
        if (scanCallback != null) return true
        val adapter = adapter?.takeIf { it.isEnabled }
            ?: return fail("蓝牙未开启")
        val scanner = adapter.bluetoothLeScanner
            ?: return fail("此设备不支持 BLE 扫描")

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDeviceFound(result.device, result.rssi)
            }

            override fun onScanFailed(errorCode: Int) {
                log("扫描失败：errorCode=$errorCode")
                scanCallback = null
            }
        }
        scanCallback = callback
        scanner.startScan(filters, settings, callback)
        log("扫描已开始")
        return true
    }

    fun stopScanning() {
        scanCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
        log("扫描已停止")
    }

    private fun onDeviceFound(device: BluetoothDevice, rssi: Int) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (device.address in activeClients) return
            val last = lastContactAt[device.address]
            if (last != null && now - last < CONTACT_COOLDOWN_MS) return
            lastContactAt[device.address] = now
            activeClients += device.address
        }
        log("发现 ${device.address}（RSSI $rssi），开始连接")
        device.connectGatt(appContext, false, ClientCallback(device.address), BluetoothDevice.TRANSPORT_LE)
    }

    private inner class ClientCallback(private val address: String) : BluetoothGattCallback() {
        private var gatt: BluetoothGatt? = null
        private var finished = false
        private val timeout = Runnable { gatt?.disconnect() }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            this.gatt = gatt
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    mainHandler.postDelayed(timeout, CLIENT_TIMEOUT_MS)
                    // 名片最长 49 字节，默认 MTU 23 一次放不下，先协商大一点
                    if (!gatt.requestMtu(REQUESTED_MTU)) gatt.discoverServices()
                }
                else -> finish(gatt, "连接结束（status=$status）")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(BleProtocol.SERVICE_UUID)
                ?.getCharacteristic(BleProtocol.IDENTITY_CHAR_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                log("[客户端] 对方不是碰碰娃或服务发现失败")
                gatt.disconnect()
                return
            }
            gatt.readCharacteristic(characteristic)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Android 13 以下走这个旧回调
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleRead(gatt, characteristic, characteristic.value, status)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleRead(gatt, characteristic, value, status)
        }

        private fun handleRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
            val peer = if (status == BluetoothGatt.GATT_SUCCESS) decodeIdentity(value) else null
            if (peer == null) {
                log("[客户端] 读到的名片不合法（status=$status）")
                gatt.disconnect()
                return
            }
            onPeer(peer, "我连上对方并读到了名片")

            // 顺便把我的名片写回去，一次连接完成双向交换
            val exchange = gatt.getService(BleProtocol.SERVICE_UUID)?.getCharacteristic(BleProtocol.EXCHANGE_CHAR_UUID)
            val me = identityProvider()
            if (exchange == null || me == null) {
                gatt.disconnect()
                return
            }
            writeExchange(gatt, exchange, encodeIdentity(me))
        }

        @Suppress("DEPRECATION")
        private fun writeExchange(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = value
                gatt.writeCharacteristic(characteristic)
            }
            if (!started) gatt.disconnect()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            log("[客户端] 写回我的名片：${if (status == BluetoothGatt.GATT_SUCCESS) "成功" else "失败 status=$status"}")
            gatt.disconnect()
        }

        private fun finish(gatt: BluetoothGatt, reason: String) {
            if (finished) return
            finished = true
            mainHandler.removeCallbacks(timeout)
            gatt.close()
            synchronized(lock) { activeClients -= address }
            log("[客户端] $address $reason")
        }
    }

    // ---------------------------------------------------------------- 其它

    fun stopAll() {
        if (isAdvertising) stopAdvertising()
        if (isScanning) stopScanning()
    }

    private fun fail(message: String): Boolean {
        log(message)
        return false
    }

    private fun advertiseErrorName(code: Int) = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "数据过长"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播实例已达上限"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "已经在广播"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "内部错误"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持"
        else -> "未知错误 $code"
    }

    private companion object {
        const val REQUESTED_MTU = 64
        const val CONTACT_COOLDOWN_MS = 10_000L
        const val CLIENT_TIMEOUT_MS = 15_000L
    }
}
