package com.techport.tvremote.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.AppShortcut

/**
 * Add/edit dialog. [onSave] validates and returns the error to show, or null once saved
 * (the caller then closes the dialog).
 */
@Composable
fun ShortcutDialog(
    initial: AppShortcut?,
    onSave: (label: String, link: String) -> ShortcutInputError?,
    onDismiss: () -> Unit,
) {
    var label by rememberSaveable { mutableStateOf(initial?.label.orEmpty()) }
    var link by rememberSaveable { mutableStateOf(initial?.link.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<ShortcutInputError?>(null) }
    val labelError = error?.takeIf { it == ShortcutInputError.LABEL_BLANK || it == ShortcutInputError.LABEL_TOO_LONG }
    val linkError = error?.takeIf { labelError == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.apps_dialog_add_title else R.string.apps_dialog_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.apps_field_label)) },
                    singleLine = true,
                    isError = labelError != null,
                    supportingText = labelError?.let { { Text(errorText(it)) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.apps_field_link)) },
                    placeholder = { Text(stringResource(R.string.apps_field_link_placeholder)) },
                    singleLine = true,
                    isError = linkError != null,
                    supportingText = { Text(linkError?.let { errorText(it) } ?: stringResource(R.string.apps_link_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onSave(label, link) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun errorText(error: ShortcutInputError): String = when (error) {
    ShortcutInputError.LABEL_BLANK -> stringResource(R.string.apps_error_label_blank)
    ShortcutInputError.LABEL_TOO_LONG -> stringResource(R.string.apps_error_label_long, MAX_LABEL_LENGTH)
    ShortcutInputError.LINK_BLANK -> stringResource(R.string.apps_error_link_blank)
    ShortcutInputError.LINK_INVALID -> stringResource(R.string.apps_error_link_invalid)
    ShortcutInputError.LINK_SCHEME_NOT_ALLOWED -> stringResource(R.string.apps_error_link_scheme)
}
