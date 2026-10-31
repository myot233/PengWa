package app.pengwa.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.pengwa.R
import app.pengwa.photo.PhotoStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 调用系统相机拍照，整张原图由相机应用直接写进临时文件，拍成功后原样复制进相册，不经过任何压缩。
 * 画质（像素、HDR、夜景）完全由系统相机的设置决定。
 *
 * 因为应用声明了 CAMERA 权限（扫码用），系统要求调用系统相机前本应用也必须已获得该权限，所以这里先申请。
 */
@Composable
fun CameraButton(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 相机在前台时本应用可能被系统回收；保存临时文件路径，回来后才能收尾
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        Log.i("PengWaPhoto", "相机返回 taken=$taken file=$pendingPath")
        val file = pendingPath?.let(::File) ?: return@rememberLauncherForActivityResult
        pendingPath = null
        if (!taken) {
            PhotoStorage.discard(file)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) { PhotoStorage.saveToGallery(context, file) }
            onMessage(context.getString(if (saved) R.string.photo_saved else R.string.photo_create_failed))
        }
    }

    fun launchCamera() {
        val file = PhotoStorage.newCaptureFile(context)
        pendingPath = file.absolutePath
        try {
            takePicture.launch(PhotoStorage.uriFor(context, file))
        } catch (_: ActivityNotFoundException) {
            pendingPath = null
            PhotoStorage.discard(file)
            onMessage(context.getString(R.string.camera_not_found))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else onMessage(context.getString(R.string.camera_permission_denied))
    }

    ExtendedFloatingActionButton(
        modifier = modifier,
        onClick = {
            if (hasCameraPermission(context)) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        icon = { Text("📷") },
        text = { Text(stringResource(R.string.camera_button)) },
    )
}
