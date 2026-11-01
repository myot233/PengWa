package app.pengwa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PengWaPreviews
@Composable
internal fun FaceToFaceScreenshots(@PreviewParameter(NfcPreviewProvider::class) sample: NfcPreviewState) {
    FaceToFacePagePreview(sample)
}

@PreviewTest
@PengWaPreviews
@Composable
internal fun BluetoothScreenshots(@PreviewParameter(BluetoothPreviewProvider::class) sample: BluetoothPreviewState) {
    BluetoothPagePreview(sample)
}

@PreviewTest
@PengWaPreviews
@Composable
internal fun ContactsScreenshots(@PreviewParameter(ContactsPreviewProvider::class) sample: ContactsPreviewState) {
    ContactsScreenPreview(sample)
}

@PreviewTest
@PengWaPreviews
@Composable
internal fun SettingsScreenshots() { SettingsPreview() }

@PreviewTest
@PengWaPreviews
@Composable
internal fun RequestScreenshots() { RequestPreview() }

@PreviewTest
@PengWaPreviews
@Composable
internal fun NameFormScreenshots() { NameFormPreview() }

@PreviewTest
@PengWaPreviews
@Composable
internal fun ScannerPermissionScreenshots() { ScannerPermissionPreview() }

@PreviewTest
@PengWaPreviews
@Composable
internal fun ContactDetailsScreenshots() { ContactDetailsPreview() }

@PreviewTest
@androidx.compose.ui.tooling.preview.Preview(name = "2 倍字体", widthDp = 320, heightDp = 640, locale = "zh-rCN", fontScale = 2f)
@androidx.compose.ui.tooling.preview.Preview(name = "横屏", widthDp = 720, heightDp = 360, locale = "zh-rCN")
@androidx.compose.ui.tooling.preview.Preview(name = "平板", widthDp = 800, heightDp = 1024, locale = "zh-rCN")
@Composable
internal fun ResponsiveScreenshots() { FaceToFacePagePreview(NfcPreviewState("出示", app.pengwa.exchange.ExchangeMode.NfcDisplay, NfcState.Ready)) }
