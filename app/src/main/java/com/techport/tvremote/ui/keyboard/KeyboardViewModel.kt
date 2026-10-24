package com.techport.tvremote.ui.keyboard

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techport.tvremote.core.clipboard.ClipboardRead
import com.techport.tvremote.core.clipboard.ClipboardReader
import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteKey
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.remote.RemoteController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class KeyboardAvailability {
    READY,
    NOT_CONNECTED,

    /** Connected, but this TV/protocol can't take text input. */
    UNSUPPORTED,
}

/** One-off notices shown in a snackbar. */
enum class KeyboardMessage { CLIPBOARD_DISABLED, CLIPBOARD_EMPTY, CLIPBOARD_TRUNCATED }

data class KeyboardUiState(
    val availability: KeyboardAvailability = KeyboardAvailability.NOT_CONNECTED,
    /** The TV field's label (e.g. "Search YouTube"), when the TV reports one. */
    val fieldLabel: String? = null,
    val canMoveCursor: Boolean = false,
    val sendOnEnter: Boolean = true,
    val clipboardEnabled: Boolean = false,
) {
    val enabled: Boolean get() = availability == KeyboardAvailability.READY
}

/**
 * Types on the TV through the phone's own keyboard.
 *
 * The phone field is the source of truth; every change is diffed against what the TV already
 * has and sent through [TextSyncer]. Edits are applied at the TV's cursor, which we assume sits
 * at the end of our text (true unless the user moves it with the arrow keys; see [moveCursor]).
 *
 * The baseline resets whenever the TV focuses a different field or the connection drops, so we
 * never send DELs into a field we didn't type in. When the TV reports the newly focused field's
 * current text, the phone field starts with it so the user can edit it.
 */
class KeyboardViewModel(
    private val controller: RemoteController,
    private val settings: StateFlow<AppSettings>,
    private val clipboard: ClipboardReader,
) : ViewModel() {

    private val syncer = TextSyncer(viewModelScope, controller::send)

    private val _input = MutableStateFlow(TextFieldValue(""))
    val input: StateFlow<TextFieldValue> = _input.asStateFlow()

    private val _message = MutableStateFlow<KeyboardMessage?>(null)
    val message: StateFlow<KeyboardMessage?> = _message.asStateFlow()

    val uiState: StateFlow<KeyboardUiState> =
        combine(controller.state, controller.textField, settings) { state, field, s ->
            uiStateOf(state, field, s)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), current())

    init {
        controller.textField.value?.let { baseline(it.value) }
        viewModelScope.launch {
            var previous = controller.textField.value
            controller.textField.collect { field ->
                if (isDifferentField(previous, field)) baseline(field?.value.orEmpty())
                previous = field
            }
        }
        viewModelScope.launch {
            var wasConnected = controller.state.value is ConnectionState.Connected
            controller.state.collect { state ->
                val connected = state is ConnectionState.Connected
                if (wasConnected && !connected) baseline("")
                wasConnected = connected
            }
        }
    }

    fun onValueChange(value: TextFieldValue) {
        if (!current().enabled) return
        // Hardware keyboards and some IMEs deliver Enter as a newline in the text.
        if ('\n' in value.text) {
            setText(value.text.replace("\n", ""))
            submit()
            return
        }
        _input.value = value
        syncer.update(value.text)
    }

    /** IME action (Send/Done). Sends ENTER when the user enabled "Send on Enter". */
    fun onImeAction(): Boolean {
        if (!current().enabled) return false
        return if (settings.value.sendOnEnter) {
            submit()
            true
        } else {
            syncer.flush()
            false
        }
    }

    /** Toolbar Enter: always sends ENTER, then starts a fresh entry. */
    fun submit() {
        if (!current().enabled) return
        syncer.flush()
        controller.send(RemoteCommand.Key(RemoteKey.ENTER))
        baseline("")
    }

    /** Removes the last character we typed, or presses DEL on the TV when there's nothing local. */
    fun backspace() {
        if (!current().enabled) return
        val text = _input.value.text
        if (text.isEmpty()) {
            controller.send(RemoteCommand.Key(RemoteKey.DEL))
        } else {
            setText(BackspaceUnits.dropLast(text, 1))
        }
    }

    fun space() {
        if (!current().enabled) return
        setText(_input.value.text + " ")
    }

    /**
     * Moves the TV cursor. The phone can't see where it ends up, so afterwards the current text is
     * treated as committed: later typing is inserted (and backspace deletes) at the TV's cursor.
     */
    fun moveCursor(right: Boolean) {
        if (!current().canMoveCursor) return
        syncer.flush()
        controller.send(RemoteCommand.Key(if (right) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT))
        syncer.reset(_input.value.text)
    }

    fun escape() {
        if (!current().enabled) return
        syncer.flush()
        controller.send(RemoteCommand.Key(RemoteKey.ESCAPE))
    }

    /** Deletes everything we typed into the TV field. */
    fun clear() {
        if (!current().enabled) return
        setText("")
    }

    /** Explicit user action: reads the clipboard (if allowed) and appends it to the TV field. */
    fun pasteFromClipboard() {
        if (!current().enabled) return
        when (val read = clipboard.read()) {
            ClipboardRead.Disabled -> _message.value = KeyboardMessage.CLIPBOARD_DISABLED
            ClipboardRead.Empty -> _message.value = KeyboardMessage.CLIPBOARD_EMPTY
            is ClipboardRead.Text -> {
                setText(_input.value.text + read.text)
                if (read.truncated) _message.value = KeyboardMessage.CLIPBOARD_TRUNCATED
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    /** Sends anything still pending, e.g. when the screen closes. */
    fun flush() = syncer.flush()

    override fun onCleared() {
        syncer.flush()
    }

    /** Read fresh on every action so a stale (unsubscribed) [uiState] can't let input through. */
    private fun current() = uiStateOf(controller.state.value, controller.textField.value, settings.value)

    private fun setText(text: String) {
        _input.value = TextFieldValue(text, TextRange(text.length))
        syncer.update(text)
    }

    private fun baseline(text: String) {
        syncer.reset(text)
        _input.value = TextFieldValue(text, TextRange(text.length))
    }

    private companion object {
        fun isDifferentField(previous: TvTextField?, current: TvTextField?): Boolean =
            (previous == null) != (current == null) || previous?.label != current?.label

        fun uiStateOf(state: ConnectionState, field: TvTextField?, s: AppSettings): KeyboardUiState {
            val caps: RemoteCapabilities? = (state as? ConnectionState.Connected)?.capabilities
            val availability = when {
                caps == null -> KeyboardAvailability.NOT_CONNECTED
                !caps.keyboard -> KeyboardAvailability.UNSUPPORTED
                else -> KeyboardAvailability.READY
            }
            return KeyboardUiState(
                availability = availability,
                fieldLabel = field?.label?.takeIf { it.isNotBlank() },
                canMoveCursor = availability == KeyboardAvailability.READY && caps?.dpad == true,
                sendOnEnter = s.sendOnEnter,
                clipboardEnabled = s.clipboardEnabled,
            )
        }
    }
}
