package app.pengwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pengwa.R
import app.pengwa.exchange.ExchangeMode
import app.pengwa.identity.Identity
import app.pengwa.identity.NameError
import app.pengwa.pengWa
import app.pengwa.ui.components.PengWaIcon
import app.pengwa.ui.components.PengWaIcons
import app.pengwa.ui.theme.PageGutter

/** 我的名片：碰一碰与扫码共用一页，附近娃娃由底部导航进入。 */
@Composable
fun HomeScreen(
    viewModel: IdentityViewModel,
    role: ExchangeMode,
    onRoleChange: (ExchangeMode) -> Unit,
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

    ExchangeSession(role)
    Column(modifier = modifier.fillMaxSize()) {
        HomeHeader(identity, onEdit = { editing = true }, onSettings = { showSettings = true })
        FaceToFacePage(
            identity = identity, role = role, onRoleChange = onRoleChange,
            onMessage = onMessage, modifier = Modifier.weight(1f),
        )
    }

    if (showSettings) SettingsSheet(onDismiss = { showSettings = false })
}

@Composable
internal fun HomeHeader(identity: Identity, onEdit: () -> Unit, onSettings: () -> Unit) {
    val compact = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() < 480.dp }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = if (compact) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (!compact) Text(stringResource(R.string.my_card), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(identity.displayName, style = MaterialTheme.typography.headlineSmall, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (!compact) Text(stringResource(R.string.home_id_short, identity.id.toString().take(8)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
        IconButton(onClick = onEdit) { PengWaIcon(PengWaIcons.Edit, contentDescription = stringResource(R.string.home_edit)) }
        IconButton(onClick = onSettings) { PengWaIcon(PengWaIcons.Settings, contentDescription = stringResource(R.string.home_settings)) }
    }
}

/** Stops the outgoing hardware session before another destination starts its own. */
@Composable
internal fun ExchangeSession(mode: ExchangeMode) {
    val state = LocalContext.current.pengWa.exchangeMode
    LifecycleResumeEffect(mode) {
        state.set(mode)
        onPauseOrDispose { state.set(ExchangeMode.None) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SettingsContent(backgroundBroadcast = { BackgroundBroadcastToggle() }, notification = { NotificationToggle() })
    }
}

@Composable
internal fun SettingsContent(backgroundBroadcast: @Composable () -> Unit, notification: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = PageGutter, end = PageGutter, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) { backgroundBroadcast() }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp)) { notification() }
        }
    }
}

/** 设置或修改显示名的表单。 */
@Composable
internal fun NameForm(
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
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = PageGutter, vertical = 24.dp),
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
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) {
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
