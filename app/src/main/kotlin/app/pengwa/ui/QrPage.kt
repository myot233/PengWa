package app.pengwa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pengwa.R
import app.pengwa.identity.Identity
import app.pengwa.pengWa
import app.pengwa.qr.QrEncoder
import app.pengwa.qr.QrPayload

/**
 * 二维码页：展示我的二维码，也可以扫对方的。二维码里是与 NFC、蓝牙同一张名片的编码。
 * 扫描时相机才会打开；离开这一页（或点关闭）立即关掉相机。
 */
@Composable
fun QrPage(identity: Identity, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 故意不用 rememberSaveable：离开这一页再回来，应该回到二维码而不是直接亮相机
    var scanning by remember { mutableStateOf(false) }

    if (scanning) {
        QrScanner(
            onResult = { text ->
                scanning = false
                val peer = QrPayload.decode(text)
                when {
                    peer == null -> onMessage(context.getString(R.string.qr_invalid))
                    // 成功时由相遇记录器统一弹"碰到了 xxx"；返回 false 说明是自己的码或刚刚扫过
                    !context.pengWa.meetings.record(peer, "二维码") -> onMessage(context.getString(R.string.qr_self_or_duplicate))
                }
            },
            onClose = { scanning = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QrCodeView(text = remember(identity) { QrPayload.encode(identity) }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.qr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(onClick = { scanning = true }, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.qr_scan))
        }
    }
}

/** 把文字画成二维码。固定黑码白底，不随深色主题变化，否则很多扫码器识别不了。 */
@Composable
fun QrCodeView(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrEncoder.encode(text) }
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(24.dp), // 二维码周围必须留白（静区），否则识别率会明显下降
    ) {
        val modules = matrix.width
        val cell = size.minDimension / modules
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix[x, y]) {
                    // 多画半个像素，避免相邻模块之间出现细缝
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
