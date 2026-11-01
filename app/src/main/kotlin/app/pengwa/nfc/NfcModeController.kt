package app.pengwa.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.util.Log
import app.pengwa.exchange.ExchangeMode

/** 前台 NFC 的唯一配置入口。切换角色先释放旧配置，避免 Reader Mode 与 discovery 覆盖同时生效。 */
class NfcModeController(
    private val activity: Activity,
    private val onTag: NfcAdapter.ReaderCallback,
) {
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(activity) }
    private var readerRequested = false
    private var discoveryRequested = false
    private var preferredRequested = false
    private var current = ExchangeMode.None

    /** 由主线程调用；进入角色时 Activity 必须已 resumed，暂停时在 super.onPause 之前释放。 */
    fun apply(mode: ExchangeMode) {
        val next = when (mode) {
            ExchangeMode.NfcDisplay, ExchangeMode.NfcRead -> mode
            else -> ExchangeMode.None
        }
        if (next == current && !readerRequested && !discoveryRequested && !preferredRequested) return
        if (next == current && next != ExchangeMode.None) return
        release()
        if (readerRequested || discoveryRequested || preferredRequested) return
        val nfc = adapter ?: return
        if (next == ExchangeMode.None || !nfc.isEnabled) return
        if (next == ExchangeMode.NfcDisplay &&
            !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        ) {
            Log.w(TAG, "HCE unavailable; use reader role or QR")
            return
        }
        current = next
        Log.d(TAG, "NFC role=$next sdk=${Build.VERSION.SDK_INT}")
        when (next) {
            ExchangeMode.NfcRead -> {
                readerRequested = true
                attempt("enableReaderMode") {
                    nfc.enableReaderMode(
                        activity, onTag,
                        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                        null,
                    )
                }
            }
            ExchangeMode.NfcDisplay -> {
                attempt("setPreferredService") {
                    val emulation = CardEmulation.getInstance(nfc)
                    preferredRequested = true
                    val accepted = emulation.setPreferredService(
                        activity, ComponentName(activity, PengWaHostApduService::class.java),
                    )
                    preferredRequested = accepted
                    if (accepted) Log.d(TAG, "setPreferredService=true")
                    else Log.w(TAG, "setPreferredService=false; system routing may take priority")
                }
                if (Build.VERSION.SDK_INT >= 35) {
                    discoveryRequested = true
                    attempt("disable polling, keep listening") {
                        nfc.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_DISABLE, NfcAdapter.FLAG_LISTEN_KEEP)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun release() {
        val nfc = adapter ?: return
        current = ExchangeMode.None
        if (readerRequested) attempt("disableReaderMode") {
            nfc.disableReaderMode(activity)
            readerRequested = false
        }
        if (discoveryRequested && Build.VERSION.SDK_INT >= 35) attempt("resetDiscoveryTechnology") {
            nfc.resetDiscoveryTechnology(activity)
            discoveryRequested = false
        }
        if (preferredRequested) attempt("unsetPreferredService") {
            val removed = CardEmulation.getInstance(nfc).unsetPreferredService(activity)
            Log.d(TAG, "unsetPreferredService=$removed")
            preferredRequested = !removed
        }
    }

    private inline fun attempt(operation: String, block: () -> Unit) {
        runCatching(block)
            .onSuccess { Log.d(TAG, operation) }
            .onFailure { Log.w(TAG, "$operation failed", it) }
    }

    private companion object {
        const val TAG = "PengWaNfc"
    }
}
