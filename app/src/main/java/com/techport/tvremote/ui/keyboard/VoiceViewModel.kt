package com.techport.tvremote.ui.keyboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techport.tvremote.core.voice.VoiceOutcome
import com.techport.tvremote.core.voice.VoiceRecorder
import com.techport.tvremote.domain.model.RemoteException
import com.techport.tvremote.domain.remote.RemoteController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VoicePhase {
    IDLE,

    /** Asking the TV to open a voice session. */
    STARTING,
    LISTENING,

    /** Stop requested; sending the tail of the audio and ending the utterance. */
    FINISHING,
}

enum class VoiceMessage { UNSUPPORTED, NO_PERMISSION, MIC_UNAVAILABLE, MIC_FAILED, FAILED }

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    /** Started by a short tap: keeps listening until tapped again (or the time limit). */
    val handsFree: Boolean = false,
    val message: VoiceMessage? = null,
)

/**
 * Drives one voice utterance at a time: hold-to-talk, with tap-to-toggle as a fallback.
 *
 * Gesture contract (see [VoiceButton]): [onPressStart] on touch-down and [onPressEnd] on release,
 * with `held = true` if the finger stayed down long enough to count as hold-to-talk.
 */
class VoiceViewModel(
    private val controller: RemoteController,
    private val recorder: VoiceRecorder,
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var stop: AtomicBoolean? = null

    /** True when the current press started the session (so its release decides hold vs tap). */
    private var pressStarted = false

    fun onPressStart() {
        val phase = _state.value.phase
        when {
            (phase == VoicePhase.LISTENING || phase == VoicePhase.STARTING) && _state.value.handsFree -> {
                pressStarted = false
                stop()
            }
            phase == VoicePhase.IDLE -> {
                pressStarted = true
                start()
            }
            else -> pressStarted = false
        }
    }

    fun onPressEnd(held: Boolean) {
        if (!pressStarted) return
        pressStarted = false
        if (held) stop() else _state.value = _state.value.copy(handsFree = true)
    }

    /** Ends the utterance and lets the TV process it. */
    fun stop() {
        val signal = stop ?: return
        signal.set(true)
        if (_state.value.phase == VoicePhase.LISTENING || _state.value.phase == VoicePhase.STARTING) {
            _state.value = _state.value.copy(phase = VoicePhase.FINISHING, handsFree = false)
        }
    }

    /** Releases the microphone immediately (screen left, app backgrounded). */
    fun cancel() {
        job?.cancel()
    }

    fun onPermissionDenied() {
        _state.value = VoiceUiState(message = VoiceMessage.NO_PERMISSION)
    }

    fun messageShown() {
        _state.value = _state.value.copy(message = null)
    }

    private fun start() {
        val signal = AtomicBoolean(false)
        stop = signal
        _state.value = VoiceUiState(phase = VoicePhase.STARTING)
        job = viewModelScope.launch {
            var message: VoiceMessage? = null
            try {
                val session = try {
                    controller.startVoice()
                } catch (e: RemoteException) {
                    message = VoiceMessage.FAILED
                    null
                }
                if (session == null) {
                    if (message == null) message = VoiceMessage.UNSUPPORTED
                    return@launch
                }
                if (!signal.get()) _state.value = _state.value.copy(phase = VoicePhase.LISTENING)
                message = when (recorder.stream(session, signal)) {
                    VoiceOutcome.SENT -> null
                    VoiceOutcome.NO_PERMISSION -> VoiceMessage.NO_PERMISSION
                    VoiceOutcome.MIC_UNAVAILABLE -> VoiceMessage.MIC_UNAVAILABLE
                    VoiceOutcome.MIC_FAILED -> VoiceMessage.MIC_FAILED
                    VoiceOutcome.SEND_FAILED -> VoiceMessage.FAILED
                }
            } catch (e: CancellationException) {
                message = null
                throw e
            } finally {
                if (stop === signal) stop = null
                _state.value = VoiceUiState(message = message)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
