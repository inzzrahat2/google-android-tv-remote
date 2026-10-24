package com.techport.tvremote.protocol.core

import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.domain.model.PairingState
import com.techport.tvremote.domain.model.RemoteCapabilities
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.domain.model.TvTextField
import com.techport.tvremote.domain.model.VolumeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One remote-control protocol. Everything above this interface is protocol-agnostic.
 *
 * Implementations throw [com.techport.tvremote.domain.model.RemoteException] for expected failures.
 */
interface RemoteProtocol {
    /** Persisted in [TvDevice.protocolId]; never change it once shipped. */
    val id: String
    val displayName: String

    /** Whether talking to devices needs LAN access (and so the local network permission). */
    val usesLocalNetwork: Boolean

    /** Discovery source for this protocol, or null if it cannot discover devices. */
    val discovery: DiscoverySource?

    /** True if a session can be opened without pairing first. */
    suspend fun isPaired(device: TvDevice): Boolean

    /** Starts pairing with a discovered/manual device. */
    fun createPairing(target: DiscoveredDevice): PairingSession

    /** Opens a long-lived session. Suspends until the device is ready for commands. */
    suspend fun openSession(device: TvDevice): RemoteSession

    /** Deletes stored credentials for [device]. */
    suspend fun forget(device: TvDevice)
}

interface RemoteSession {
    /** Address actually connected to, when it can differ from the saved one (e.g. after re-resolving). */
    val connectedHost: String? get() = null
    val connectedPort: Int? get() = null

    val capabilities: StateFlow<RemoteCapabilities>

    /** Null until/unless the device reports it. */
    val volume: StateFlow<VolumeState?>

    /** Package of the foreground app on the TV, when reported. */
    val currentApp: StateFlow<String?>

    /** The TV's focused text field, or null when none is focused / not reported. */
    val textField: StateFlow<TvTextField?>

    suspend fun execute(command: RemoteCommand)

    /**
     * Opens a voice session (audio in [com.techport.tvremote.domain.model.VoiceFormat]).
     * Returns null if the device does not support voice; throws RemoteException on failure.
     */
    suspend fun startVoice(): VoiceSession?

    /** Round-trip time in ms, or null when the protocol has no way to measure it. */
    suspend fun measureLatency(): Long?

    /** Suspends until the session ends. Returns the reason, or null on a deliberate [close]. */
    suspend fun awaitClosed(): RemoteError?

    fun close()
}

interface VoiceSession {
    /** Sends a chunk of PCM audio. Chunking/padding to protocol limits is the adapter's job. */
    suspend fun send(pcm: ByteArray)

    /** Ends the utterance; the TV then processes the query. */
    suspend fun end()
}

interface PairingSession {
    val state: StateFlow<PairingState>

    /** Connects and asks the TV to show a code. */
    suspend fun start()

    suspend fun submitCode(code: String)

    fun cancel()
}

interface DiscoverySource {
    /** Emits the current set of visible devices while collected; stops scanning on cancel. */
    fun discover(): Flow<List<DiscoveredDevice>>
}
