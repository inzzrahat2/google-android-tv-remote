package com.techport.tvremote.domain.model

sealed interface PairingState {
    data object Idle : PairingState
    data object Starting : PairingState

    /** The TV is showing a code; the user must type it. */
    data class AwaitingCode(val codeLength: Int, val alphabet: CodeAlphabet, val attemptFailed: Boolean = false) :
        PairingState

    data object Verifying : PairingState
    data class Paired(val device: TvDevice) : PairingState
    data class Failed(val error: RemoteError) : PairingState
}

enum class CodeAlphabet(val chars: String) {
    NUMERIC("0123456789"),
    HEX("0123456789ABCDEF"),
}
