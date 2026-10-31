package app.pengwa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.pengwa.R
import app.pengwa.identity.Identity
import app.pengwa.identity.NameError

/** 没有显示名时进入设置；有显示名时展示名片，可点"修改"重新设置。 */
@Composable
fun IdentityScreen(viewModel: IdentityViewModel, modifier: Modifier = Modifier) {
    val identity = viewModel.identity
    var editing by rememberSaveable { mutableStateOf(false) }

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
    } else {
        ProfileCard(identity = identity, onEdit = { editing = true }, modifier = modifier)
    }
}

@Composable
private fun ProfileCard(identity: Identity, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = identity.displayName, style = MaterialTheme.typography.displayMedium)
        Text(
            text = stringResource(R.string.my_id_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = identity.id.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        TextButton(onClick = onEdit, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.edit_name))
        }
        NfcStatus(modifier = Modifier.padding(top = 32.dp))
        NotificationToggle(modifier = Modifier.padding(top = 24.dp))
    }
}

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
            text = stringResource(
                if (canCancel) R.string.edit_name else R.string.setup_title,
            ),
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
