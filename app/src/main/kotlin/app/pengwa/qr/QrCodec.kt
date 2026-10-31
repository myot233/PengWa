package app.pengwa.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 生成二维码的模块矩阵（不含静区，由界面自己留白）。 */
object QrEncoder {
    fun encode(text: String): BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
}

/**
 * 从相机的一帧亮度数据里识别二维码。CameraX 的 Y 平面每行可能有对齐填充，
 * 所以要带上 [rowStride]。二维码识别与图像旋转无关，不需要按 rotationDegrees 转正。
 */
object QrFrameDecoder {
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))

    fun decode(luma: ByteArray, width: Int, height: Int, rowStride: Int = width): String? {
        val data = if (rowStride == width) luma else compact(luma, width, height, rowStride)
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: NotFoundException) {
            null
        } catch (_: ChecksumException) {
            null
        } catch (_: FormatException) {
            null
        }
    }

    private fun compact(luma: ByteArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val out = ByteArray(width * height)
        for (row in 0 until height) {
            System.arraycopy(luma, row * rowStride, out, row * width, width)
        }
        return out
    }
}
