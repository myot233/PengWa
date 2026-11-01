package app.pengwa.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Renders the same fixed fixtures as the PNG gallery on a device, without using exchange hardware or real contacts. */
class UiPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "home"
        val index = intent.getIntExtra("sample", 0)
        setContent {
            when (screen) {
                "bluetooth" -> BluetoothPagePreview(BluetoothPreviewProvider().values.toList().let { it[index.coerceIn(it.indices)] })
                "contacts" -> ContactsScreenPreview(ContactsPreviewProvider().values.toList().let { it[index.coerceIn(it.indices)] })
                "settings" -> SettingsPreview()
                "request" -> RequestPreview()
                "permission" -> ScannerPermissionPreview()
                "name" -> NameFormPreview()
                "details" -> ContactDetailsPreview()
                else -> FaceToFacePagePreview(NfcPreviewProvider().values.toList().let { it[index.coerceIn(it.indices)] })
            }
        }
    }
}
