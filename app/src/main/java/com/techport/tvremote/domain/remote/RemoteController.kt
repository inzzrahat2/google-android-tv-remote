package com.techport.tvremote.domain.remote

import com.techport.tvremote.domain.model.ConnectionState
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import com.techport.tvremote.protocol.core.VoiceSession
import kotlinx.coroutines.flow.StateFlow

/** The only remote-control surface the UI talks to. */
interface RemoteController {
    val state: StateFlow<ConnectionState>
    val volume: StateFlow<VolumeState?>
    val textField: StateFlow<TvTextField?>

    fun connect(device: TvDevice)

    /** Retries the current device immediately (e.g. user tapped "Try again"). */
    fun retry()
    fun disconnect()

    /** Non-blocking and ordered. Dropped (and logged) if no session is open. */
    fun send(command: RemoteCommand)

    suspend fun measureLatency(): Long?

    /** Null when not connected or voice unsupported. */
    suspend fun startVoice(): VoiceSession?
}
