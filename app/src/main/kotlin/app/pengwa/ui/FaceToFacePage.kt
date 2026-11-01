package app.pengwa.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pengwa.R
import app.pengwa.exchange.ExchangeMode
import app.pengwa.identity.Identity
import app.pengwa.pengWa
import app.pengwa.qr.QrEncoder
import app.pengwa.qr.QrPayload
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.PageGutter

/**
 * NFC 与二维码共用的面对面交换页，扫一扫一键打开相机。
 * 离开主页时组合销毁，相机与亮度覆盖随之释放。
 */
@Composable
fun FaceToFacePage(
    identity: Identity,
    role: ExchangeMode,
    onRoleChange: (ExchangeMode) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // 故意不用 rememberSaveable：离开这一页再回来，应该回到二维码而不是直接亮相机
    var scanning by remember { mutableStateOf(false) }
    BackHandler(enabled = scanning) { scanning = false }

    val mode by context.pengWa.exchangeMode.mode.collectAsStateWithLifecycle()
    BrightnessLock(enabled = (mode == ExchangeMode.NfcDisplay || mode == ExchangeMode.NfcRead) && !scanning)

    if (scanning) {
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 300.dp && maxWidth >= 480.dp
        if (compact) {
            Row(Modifier.fillMaxSize().padding(horizontal = PageGutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NfcInstructions(role, nfcState, onRoleChange)
                    }
                    ScanButton(onScan, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Column(Modifier.widthIn(max = 216.dp).weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val edge = minOf(200.dp, maxWidth - 16.dp, maxHeight - 16.dp).coerceAtLeast(48.dp)
                        ExchangeQrCard(identity, onExpand = { expanded = true }, showHint = false, codeSize = edge)
                    }
                    NfcStatus(nfcState, onOpenNfcSettings)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = PageGutter, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NfcInstructions(role, nfcState, onRoleChange)
                    ExchangeQrCard(identity, onExpand = { expanded = true })
                    NfcStatus(nfcState, onOpenNfcSettings)
                }
                ScanButton(onScan, Modifier.padding(top = 12.dp).fillMaxWidth())
            }
        }
    }
    if (expanded) QrCardDialog(identity) { expanded = false }
}

@Composable
private fun NfcInstructions(role: ExchangeMode, nfcState: NfcState, onRoleChange: (ExchangeMode) -> Unit) {
    if (nfcState != NfcState.Unsupported) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .padding(4.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                ExchangeMode.NfcDisplay to R.string.nfc_role_display,
                ExchangeMode.NfcRead to R.string.nfc_role_read,
            ).forEach { (candidate, label) ->
                val selected = role == candidate
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp).selectable(
                            selected = selected, onClick = { onRoleChange(candidate) }, role = Role.RadioButton,
                        ).padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Text(
            text = stringResource(if (role == ExchangeMode.NfcDisplay) R.string.nfc_display_hint else R.string.nfc_read_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScanButton(onScan: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onScan, shape = MaterialTheme.shapes.medium, modifier = modifier.heightIn(min = 52.dp)) {
        PengWaIcon(PengWaIcons.Qr, tint = MaterialTheme.colorScheme.onPrimary)
        Text(stringResource(R.string.qr_scan), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ExchangeQrCard(identity: Identity, onExpand: () -> Unit, modifier: Modifier = Modifier, showHint: Boolean = true, codeSize: Dp = 200.dp) {
    Surface(
        onClick = onExpand, modifier = modifier.widthIn(max = if (showHint) 340.dp else codeSize + 16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(if (showHint) 12.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QrCodeView(remember(identity) { QrPayload.encode(identity) }, if (showHint) Modifier.widthIn(max = 200.dp).fillMaxWidth() else Modifier.size(codeSize))
            if (showHint) Text(stringResource(R.string.qr_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun QrCardDialog(identity: Identity, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.End) {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.qr_close))
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val edge = minOf(maxWidth, maxHeight)
                    QrCodeView(remember(identity) { QrPayload.encode(identity) }, Modifier.size(edge))
                }
            }
        }
    }
}

/** 把文字画成二维码。固定黑码白底，不随深色主题变化，否则很多扫码器识别不了。 */
@Composable
fun QrCodeView(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) {
        val matrix = QrEncoder.encode(text)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.qr_code_label),
        filterQuality = FilterQuality.None,
        modifier = modifier.aspectRatio(1f)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(24.dp), // Fixed quiet zone; neither the code nor its white backing follows the theme.
    )
}
