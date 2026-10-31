package app.pengwa.photo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 拍照流程：相机把整张原图写进应用私有缓存里的临时文件（通过 FileProvider 临时授权给相机），
 * 拍成功后再由我们复制到相册 Pictures/PengWa，并删掉临时文件。
 *
 * 为什么不直接让相机写相册里的"待定"条目：媒体库规定 IS_PENDING 的条目只有创建者能读写，
 * 第三方相机（实测三星相机）打开它会被拒绝并崩溃。临时文件 + FileProvider 是各家相机都支持的标准做法，
 * 也保证取消拍照时相册里不会留下空条目。往自己创建的媒体条目写入在 Android 10+ 不需要存储权限。
 */
object PhotoStorage {
    private const val TAG = "PengWaPhoto"
    private const val ALBUM = "PengWa"

    fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** 相机的临时输出文件。 */
    fun newCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /** 把拍好的临时文件存进相册，成功返回 true。无论成败都会删除临时文件。会读写文件，必须在后台线程调用。 */
    fun saveToGallery(context: Context, file: File): Boolean {
        try {
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "相机没有写出照片：${file.name}")
                return false
            }
            val resolver = context.contentResolver
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "PengWa_$time.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1) // 我们是创建者，复制完成前相册里先不显示
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val target = resolver.insert(collection, values) ?: return false
            return try {
                val out = resolver.openOutputStream(target) ?: error("无法打开相册输出流")
                out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
                resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                Log.i(TAG, "已保存 ${file.length()} 字节 -> $target")
                true
            } catch (e: Exception) {
                Log.w(TAG, "复制到相册失败", e)
                resolver.delete(target, null, null)
                false
            }
        } finally {
            file.delete()
        }
    }

    fun discard(file: File) {
        file.delete()
    }
}
