package app.pengwa.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.pengwa.ble.NearbyPeer
import app.pengwa.ble.PeerState
import app.pengwa.contacts.Contact
import app.pengwa.exchange.ExchangeMode
import app.pengwa.identity.Identity
import app.pengwa.ui.theme.PengWaTheme
import java.util.UUID

@Preview(name = "浅色", widthDp = 360, heightDp = 720, locale = "zh-rCN")
@Preview(name = "深色", widthDp = 360, heightDp = 720, locale = "zh-rCN", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "窄屏大字体", widthDp = 320, heightDp = 640, locale = "zh-rCN", fontScale = 1.5f)
internal annotation class PengWaPreviews

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    PengWaTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

internal data class NfcPreviewState(val name: String, val role: ExchangeMode, val state: NfcState)

internal class NfcPreviewProvider : PreviewParameterProvider<NfcPreviewState> {
    override val values = sequenceOf(
        NfcPreviewState("出示", ExchangeMode.NfcDisplay, NfcState.Ready),
        NfcPreviewState("读取", ExchangeMode.NfcRead, NfcState.Ready),
        NfcPreviewState("NFC 未开启", ExchangeMode.NfcDisplay, NfcState.Disabled),
        NfcPreviewState("不支持 NFC", ExchangeMode.NfcRead, NfcState.Unsupported),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@PengWaPreviews
@Composable
internal fun FaceToFacePagePreview(@PreviewParameter(NfcPreviewProvider::class) sample: NfcPreviewState) {
    var role by remember(sample) { mutableStateOf(sample.role) }
    PreviewSurface {
        FaceToFacePageContent(
            identity = Identity(UUID(0L, 1L), "小棉花"),
            role = role,
            nfcState = sample.state,
            onRoleChange = { role = it },
            onScan = {},
            onOpenNfcSettings = {},
        )
    }
}

internal data class BluetoothPreviewState(
    val name: String,
    val permitted: Boolean = true,
    val denied: Boolean = false,
    val bluetoothOn: Boolean = true,
    val nearby: List<NearbyPeer> = emptyList(),
)

internal class BluetoothPreviewProvider : PreviewParameterProvider<BluetoothPreviewState> {
    override val values = sequenceOf(
        BluetoothPreviewState("尚未授权", permitted = false),
        BluetoothPreviewState("权限被拒绝", permitted = false, denied = true),
        BluetoothPreviewState("蓝牙关闭", bluetoothOn = false),
        BluetoothPreviewState("搜索中"),
        BluetoothPreviewState("附近的人与交换状态", nearby = previewPeers),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@PengWaPreviews
@Composable
internal fun BluetoothPagePreview(@PreviewParameter(BluetoothPreviewProvider::class) sample: BluetoothPreviewState) {
    PreviewSurface {
        BluetoothPageContent(
            permitted = sample.permitted,
            denied = sample.denied,
            bluetoothOn = sample.bluetoothOn,
            nearby = sample.nearby,
            onPermission = {},
            onEnableBluetooth = {},
            onExchange = {},
        )
    }
}

private val previewPeers = PeerState.entries.mapIndexed { index, state ->
    NearbyPeer(
        address = "02:00:00:00:00:0$index",
        name = if (state == PeerState.Discovered) null else "名字很长很长的娃友",
        rssi = -55 - index * 8,
        lastSeenMillis = 0L,
        state = state,
        message = if (state == PeerState.Failed) "对方暂时没有回应，请稍后重试" else null,
    )
}

internal data class ContactsPreviewState(val name: String, val contacts: List<Contact>)

internal class ContactsPreviewProvider : PreviewParameterProvider<ContactsPreviewState> {
    override val values = sequenceOf(
        ContactsPreviewState("空集邮册", emptyList()),
        ContactsPreviewState("长名字与相遇次数", previewContacts.take(3)),
        ContactsPreviewState("可滚动的集邮册", previewContacts),
    )

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@PengWaPreviews
@Composable
internal fun ContactsScreenPreview(@PreviewParameter(ContactsPreviewProvider::class) sample: ContactsPreviewState) {
    PreviewSurface {
        ContactsScreenContent(contacts = sample.contacts) { buttonModifier ->
            CameraButtonContent(onClick = {}, modifier = buttonModifier)
        }
    }
}

private val previewContacts = List(20) { index ->
    Contact(
        id = UUID(0L, index.toLong() + 1),
        displayName = when (index) {
            0 -> "小棉花"
            1 -> "名字很长很长的娃友"
            2 -> "🧸 棉花糖"
            else -> "娃友 ${index + 1}"
        },
        firstMetAt = 1_793_462_400_000L,
        lastMetAt = 1_793_462_400_000L + index * 3_600_000L,
        metCount = if (index == 1) 999 else index + 1,
    )
}
