package app.pengwa.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * [enabled] 为 true 期间，把本窗口的亮度拉到最高并锁定、屏幕保持常亮；变为 false 或离开组合时立即恢复。
 *
 * 用的是窗口自己的亮度覆盖：不需要"修改系统设置"权限，不会改动用户的系统亮度值，
 * 系统亮度滑块和自动亮度在此期间都不会把它调暗。展示二维码时亮度太低，对方的相机就扫不出来；
 * 屏幕自己熄灭同样会让对方扫不到，所以顺带保持常亮。
 */
@Composable
fun BrightnessLock(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (!enabled || window == null) {
            onDispose { }
        } else {
            val previousBrightness = window.attributes.screenBrightness
            window.attributes = window.attributes.also {
                it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                window.attributes = window.attributes.also { it.screenBrightness = previousBrightness }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
