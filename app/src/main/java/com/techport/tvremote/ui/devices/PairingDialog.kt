package com.techport.tvremote.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techport.tvremote.R
import com.techport.tvremote.domain.model.CodeAlphabet
import com.techport.tvremote.domain.model.PairingState
import com.techport.tvremote.ui.components.text

/** The code is held only in this dialog's state and handed straight to the protocol; it is never stored or logged. */
@Composable
fun PairingDialog(ui: PairingUi, onSubmit: (String) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    var code by remember(ui.target.id) { mutableStateOf("") }
    val state = ui.state

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pairing_title, ui.target.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    PairingState.Idle, PairingState.Starting -> Progress(stringResource(R.string.pairing_starting))
                    PairingState.Verifying -> Progress(stringResource(R.string.pairing_verifying))
                    is PairingState.AwaitingCode -> CodeEntry(
                        state = state,
                        code = code,
                        simulatedCode = ui.simulatedCode,
                        onCodeChange = { input ->
                            code = input.uppercase().filter { it in state.alphabet.chars }.take(state.codeLength)
                            if (code.length == state.codeLength) {
                                onSubmit(code)
                                code = ""
                            }
                        },
                        onDone = { if (code.length == state.codeLength) onSubmit(code) },
                    )
                    is PairingState.Failed -> {
                        val text = state.error.text()
                        Text(stringResource(text.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(text.body), style = MaterialTheme.typography.bodyMedium)
                    }
                    is PairingState.Paired -> Unit
                }
            }
        },
        confirmButton = {
            if (state is PairingState.Failed) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CodeEntry(
    state: PairingState.AwaitingCode,
    code: String,
    simulatedCode: String?,
    onCodeChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Text(stringResource(R.string.pairing_enter_code), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text(stringResource(R.string.pairing_code_label)) },
        singleLine = true,
        isError = state.attemptFailed,
        supportingText = if (state.attemptFailed) {
            { Text(stringResource(R.string.pairing_wrong_code)) }
        } else {
            null
        },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = if (state.alphabet == CodeAlphabet.NUMERIC) KeyboardType.NumberPassword else KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    if (simulatedCode != null) {
        Text(
            stringResource(R.string.pairing_simulated_hint, simulatedCode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Progress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}
