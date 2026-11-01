package app.pengwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pengwa.R
import app.pengwa.exchange.ExchangeMode
import app.pengwa.identity.Identity
import app.pengwa.identity.NameError
import app.pengwa.pengWa
import kotlinx.coroutines.launch

/** 主页内三种交换方式的页序。左右滑动切换，停在哪一页就只用哪一种。 */
const val PAGE_NFC = 0
const val PAGE_BLUETOOTH = 1
const val PAGE_QR = 2
private const val PAGE_COUNT = 3

private fun modeForPage(page: Int, nfcRole: ExchangeMode) = when (page) {
    PAGE_NFC -> nfcRole
    PAGE_BLUETOOTH -> ExchangeMode.Bluetooth
    else -> ExchangeMode.Qr
}

/**
 * 主页：顶部是我的名片，下面是 NFC / 蓝牙 / 二维码三个可左右滑动的页。
 * 没有设置显示名或正在改名时，显示设置表单。三种方式交换的是同一张名片。
 */
@Composable
fun HomeScreen(
    viewModel: IdentityViewModel,
    page: Int,
    onPageSettled: (Int) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = viewModel.identity
    var editing by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (identity == null || editing) {
        NameForm(
            initialName = identity?.displayName.orEmpty(),
            canCancel = identity != null,
            onSave = { name ->
                val error = viewModel.saveDisplayName(name)
                if (error == null) editing = false
                error
            },
            onCancel = { editing = false },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        HomeHeader(identity, onEdit = { editing = true }, onSettings = { showSettings = true })
        ExchangePager(
            identity = identity,
            initialPage = page,
            onPageSettled = onPageSettled,
            onMessage = onMessage,
            modifier = Modifier.weight(1f),
        )
    }

    if (showSettings) SettingsSheet(onDismiss = { showSettings = false })
}

@Composable
private fun HomeHeader(identity: Identity, onEdit: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(identity.displayName, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(
                text = stringResource(R.string.home_id_short, identity.id.toString().take(8)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.home_edit)) }
        TextButton(onClick = onSettings) { Text(stringResource(R.string.home_settings)) }
    }
}

@Composable
private fun ExchangePager(
    identity: Identity,
    initialPage: Int,
    onPageSettled: (Int) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGE_COUNT })
    var nfcRole by rememberSaveable { mutableStateOf(ExchangeMode.NfcDisplay) }
    val titles = listOf(R.string.page_nfc, R.string.page_bluetooth, R.string.page_qr)

    // 以"停稳"的那一页为准，滑动过程中经过的页不会被启用
    val settled = pagerState.settledPage
    LaunchedEffect(settled) { onPageSettled(settled) }

    // 滑到哪个就只用哪个；离开主页、退到后台、息屏都回到"都不用"
    val modeState = context.pengWa.exchangeMode
    LifecycleResumeEffect(settled, nfcRole) {
        modeState.set(modeForPage(settled, nfcRole))
        onPauseOrDispose { modeState.set(ExchangeMode.None) }
    }

    // 停在二维码页时自动拉高并锁定亮度（含保持常亮），切走、退到后台都会自动恢复
    val mode by modeState.mode.collectAsStateWithLifecycle()
    BrightnessLock(enabled = mode == ExchangeMode.Qr)

    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(title)) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                PAGE_NFC -> NfcPage(
                    role = nfcRole,
                    onRoleChange = { nfcRole = it },
                    onShowQr = { scope.launch { pagerState.animateScrollToPage(PAGE_QR) } },
                )
                PAGE_BLUETOOTH -> BluetoothPage()
                else -> QrPage(identity = identity, onMessage = onMessage)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            BackgroundBroadcastToggle()
            NotificationToggle()
        }
    }
}

/** 设置或修改显示名的表单。 */
@Composable
private fun NameForm(
    initialName: String,
    canCancel: Boolean,
    onSave: (String) -> NameError?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var error by rememberSaveable { mutableStateOf<NameError?>(null) }

    fun submit() {
        error = onSave(name)
    }

    Column(
        modifier = modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(if (canCancel) R.string.edit_name else R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.setup_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = null
            },
            label = { Text(stringResource(R.string.display_name_label)) },
            isError = error != null,
            supportingText = error?.let { { Text(stringResource(it.messageRes())) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(stringResource(R.string.save))
        }
        if (canCancel) {
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

private fun NameError.messageRes(): Int = when (this) {
    NameError.Empty -> R.string.name_error_empty
    NameError.TooLong -> R.string.name_error_too_long
    NameError.InvalidChar -> R.string.name_error_invalid_char
}
