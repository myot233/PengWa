package app.pengwa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PengWaPreviews
@Composable
internal fun NfcScreenshots(@PreviewParameter(NfcPreviewProvider::class) sample: NfcPreviewState) {
    NfcPagePreview(sample)
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
internal fun QrScreenshots() {
    QrPagePreview()
}
