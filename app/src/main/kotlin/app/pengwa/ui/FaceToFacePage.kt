package app.pengwa.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pengwa.R
import app.pengwa.exchange.ExchangeMode
import app.pengwa.identity.Identity
import app.pengwa.pengWa
import app.pengwa.qr.QrEncoder
import app.pengwa.qr.QrPayload

/**
 * NFC 与二维码共用的面对面交换页，扫一扫一键打开相机。
 * Pager 可能保留离屏组合，所以用 [selected] 关闭离屏相机并重置扫码状态。
 */
@Composable
fun FaceToFacePage(
    identity: Identity,
    selected: Boolean,
    role: ExchangeMode,
    onRoleChange: (ExchangeMode) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // 故意不用 rememberSaveable：离开这一页再回来，应该回到二维码而不是直接亮相机
    var scanning by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { if (!selected) scanning = false }
    BackHandler(enabled = selected && scanning) { scanning = false }

    val mode by context.pengWa.exchangeMode.mode.collectAsStateWithLifecycle()
    BrightnessLock(enabled = selected && (mode == ExchangeMode.NfcDisplay || mode == ExchangeMode.NfcRead) && !scanning)

    if (selected && scanning) {
        QrScanner(
            onResult = { text ->
                scanning = false
                val peer = QrPayload.decode(text)
                when {
                    peer == null -> onMessage(resources.getString(R.string.qr_invalid))
                    // 成功时由相遇记录器统一弹"碰到了 xxx"；返回 false 说明是自己的码或刚刚扫过
                    !context.pengWa.meetings.record(peer, "二维码") -> onMessage(resources.getString(R.string.qr_self_or_duplicate))
                }
            },
            onClose = { scanning = false },
            modifier = modifier,
        )
        return
    }

    FaceToFacePageContent(
        identity = identity,
        role = role,
        nfcState = rememberNfcState(),
        onRoleChange = onRoleChange,
        onOpenNfcSettings = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
        onScan = { scanning = true },
        modifier = modifier,
    )
}

@Composable
internal fun FaceToFacePageContent(
    identity: Identity,
    role: ExchangeMode,
    nfcState: NfcState,
    onRoleChange: (ExchangeMode) -> Unit,
    onOpenNfcSettings: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NfcStatus(state = nfcState, onOpenSettings = onOpenNfcSettings)
            Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                listOf(
                    ExchangeMode.NfcDisplay to R.string.nfc_role_display,
                    ExchangeMode.NfcRead to R.string.nfc_role_read,
                ).forEach { (candidate, label) ->
                    Row(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                            selected = role == candidate,
                            onClick = { onRoleChange(candidate) },
                            role = Role.RadioButton,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = role == candidate, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Text(
                text = stringResource(if (role == ExchangeMode.NfcDisplay) R.string.nfc_display_hint else R.string.nfc_read_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            QrCodeView(
                text = remember(identity) { QrPayload.encode(identity) },
                modifier = Modifier.widthIn(max = 256.dp).fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onScan, modifier = Modifier.padding(top = 16.dp).widthIn(max = 320.dp).fillMaxWidth()) {
            Text(stringResource(R.string.qr_scan))
        }
        Text(
            text = stringResource(R.string.face_to_face_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
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
