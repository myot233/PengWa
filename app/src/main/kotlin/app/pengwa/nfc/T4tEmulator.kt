package app.pengwa.nfc

/**
 * NFC Forum Type 4 Tag 的模拟：让任何手机把我们当成一张存着 NDEF 消息的标签来读。
 * 流程（读卡方）：SELECT NDEF 应用 → SELECT 能力文件 E103 → READ BINARY 读 15 字节
 *               → SELECT NDEF 文件 E104 → READ BINARY 读长度和内容。
 *
 * [armed] 为 false 时拒绝 SELECT 应用，读卡方看到的就是"这不是一张 NDEF 标签"。
 * 纯 Kotlin，不依赖 Android。
 */
class T4tEmulator(
    private val message: () -> ByteArray?,
    private val armed: () -> Boolean,
) {
    private var applicationSelected = false
    private var selectedFile: Int? = null
    private var ndefFile: ByteArray = ByteArray(0)

    fun reset() {
        applicationSelected = false
        selectedFile = null
        ndefFile = ByteArray(0)
    }

    fun process(command: ByteArray): ByteArray {
        if (command.size < 4) return ApduProtocol.SW_WRONG_LENGTH
        if (command[0] != CLA) return ApduProtocol.SW_CLA_NOT_SUPPORTED
        return when (command[1]) {
            INS_SELECT -> select(command)
            INS_READ_BINARY -> readBinary(command)
            INS_UPDATE_BINARY -> SW_SECURITY_STATUS_NOT_SATISFIED // 只读标签
            else -> ApduProtocol.SW_INS_NOT_SUPPORTED
        }
    }

    private fun select(command: ByteArray): ByteArray = when (command[2]) {
        P1_BY_NAME -> selectApplication(command)
        P1_BY_FILE_ID -> selectFile(command)
        else -> SW_INCORRECT_P1P2
    }

    private fun selectApplication(command: ByteArray): ByteArray {
        selectedFile = null
        val aid = ApduProtocol.commandData(command)
        val allowed = aid != null && aid.contentEquals(AID) && armed() && message() != null
        applicationSelected = allowed
        return if (allowed) ApduProtocol.SW_OK else ApduProtocol.SW_FILE_NOT_FOUND
    }

    private fun selectFile(command: ByteArray): ByteArray {
        if (!applicationSelected) return ApduProtocol.SW_CONDITIONS_NOT_SATISFIED
        val data = ApduProtocol.commandData(command)
        if (data == null || data.size != 2) return ApduProtocol.SW_WRONG_LENGTH
        val fileId = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        return when (fileId) {
            FILE_CC -> {
                selectedFile = FILE_CC
                ApduProtocol.SW_OK
            }
            FILE_NDEF -> {
                // 选中时就定格内容，读的过程中名字被改了也不会读出前后不一致的数据
                val current = message() ?: return ApduProtocol.SW_FILE_NOT_FOUND
                ndefFile = byteArrayOf((current.size shr 8).toByte(), current.size.toByte()) + current
                selectedFile = FILE_NDEF
                ApduProtocol.SW_OK
            }
            else -> ApduProtocol.SW_FILE_NOT_FOUND
        }
    }

    private fun readBinary(command: ByteArray): ByteArray {
        val file = when (selectedFile) {
            FILE_CC -> CAPABILITY_CONTAINER
            FILE_NDEF -> ndefFile
            else -> return ApduProtocol.SW_CONDITIONS_NOT_SATISFIED
        }
        val offset = ((command[2].toInt() and 0x7F) shl 8) or (command[3].toInt() and 0xFF)
        // Le=0 表示最多 256 字节；没有 Le 时也按 256 处理
        val requested = if (command.size >= 5) (command[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        if (offset > file.size) return SW_WRONG_PARAMETERS
        val end = minOf(offset + requested, file.size)
        return ApduProtocol.withStatus(file.copyOfRange(offset, end), ApduProtocol.SW_OK)
    }

    companion object {
        /** NFC Forum NDEF Tag Application 的标准 AID：D2760000850101。 */
        val AID: ByteArray = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
        )

        const val FILE_CC = 0xE103
        const val FILE_NDEF = 0xE104

        /** 单次 READ BINARY 最多返回的字节数（能力文件里声明给读卡方的 MLe）。 */
        const val MAX_READ = 59

        /**
         * 能力文件（15 字节）：长度 15、映射版本 2.0、MLe 59、MLc 52、
         * NDEF 文件控制 TLV（文件 E104、最大 255 字节、读不限制、禁止写）。
         */
        val CAPABILITY_CONTAINER: ByteArray = byteArrayOf(
            0x00, 0x0F,
            0x20,
            0x00, MAX_READ.toByte(),
            0x00, 0x34,
            0x04, 0x06,
            0xE1.toByte(), 0x04,
            0x00, 0xFF.toByte(),
            0x00,
            0xFF.toByte(),
        )

        private const val CLA: Byte = 0x00
        private const val INS_SELECT: Byte = 0xA4.toByte()
        private const val INS_READ_BINARY: Byte = 0xB0.toByte()
        private const val INS_UPDATE_BINARY: Byte = 0xD6.toByte()
        private const val P1_BY_NAME: Byte = 0x04
        private const val P1_BY_FILE_ID: Byte = 0x00

        private val SW_SECURITY_STATUS_NOT_SATISFIED = byteArrayOf(0x69, 0x82.toByte())
        private val SW_INCORRECT_P1P2 = byteArrayOf(0x6A, 0x86.toByte())
        private val SW_WRONG_PARAMETERS = byteArrayOf(0x6B, 0x00)
    }
}

/**
 * 一个 HCE 服务要同时响应两个 AID：我们的私有 AID（两边都装了 App 时的双向交换）
 * 和标准的 NDEF 标签 AID（让别人的系统直接拉起 App）。按最近一次成功的 SELECT 决定后续命令交给谁。
 */
class CardRouter(
    private val exchange: ApduHandler,
    private val tag: T4tEmulator,
) {
    private enum class Active { None, Exchange, Tag }

    private var active = Active.None

    fun reset() {
        active = Active.None
        exchange.reset()
        tag.reset()
    }

    fun process(command: ByteArray): ByteArray {
        if (ApduProtocol.isSelect(command)) return selectApplication(command)
        return when (active) {
            Active.Exchange -> exchange.process(command)
            Active.Tag -> tag.process(command)
            Active.None -> ApduProtocol.SW_CONDITIONS_NOT_SATISFIED
        }
    }

    private fun selectApplication(command: ByteArray): ByteArray {
        val aid = ApduProtocol.commandData(command)
        reset()
        return when {
            aid != null && aid.contentEquals(ApduProtocol.AID) -> {
                active = Active.Exchange
                exchange.process(command)
            }
            aid != null && aid.contentEquals(T4tEmulator.AID) -> {
                val response = tag.process(command)
                if (ApduProtocol.isSuccess(response)) active = Active.Tag
                response
            }
            else -> ApduProtocol.SW_FILE_NOT_FOUND
        }
    }
}

/**
 * "展示名片"开关：只有名片页在前台时才为 true，此时别人的手机才能把我们当 NDEF 标签读到。
 * 离开页面、退到后台、进程被杀都会回到 false。服务与界面在同一进程，用 volatile 即可。
 */
class TagShareGate {
    @Volatile
    var armed: Boolean = false
        private set

    fun arm() {
        armed = true
    }

    fun disarm() {
        armed = false
    }
}
