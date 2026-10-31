package app.pengwa.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.core.content.IntentCompat
import app.pengwa.ble.decodeIdentity
import app.pengwa.identity.Identity

/**
 * 别人的手机读到我们的 NDEF 标签后，系统会用 NDEF_DISCOVERED 把本 App 拉起来。
 * 意图的内容来自外部，不可信：类型不对、名片编码不合法的都直接丢弃。
 */
object NdefLaunchParser {
    fun peerFrom(intent: Intent): Identity? {
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        val messages = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?: return null
        for (message in messages) {
            for (record in (message as NdefMessage).records) {
                if (record.tnf != NdefRecord.TNF_MIME_MEDIA) continue
                if (String(record.type, Charsets.US_ASCII) != NdefMessages.MIME_TYPE) continue
                decodeIdentity(record.payload)?.let { return it }
            }
        }
        return null
    }
}
