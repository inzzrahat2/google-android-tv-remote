package com.techport.tvremote.protocol.androidtv

import com.techport.tvremote.core.logging.AppLogger
import com.techport.tvremote.domain.model.CodeAlphabet
import com.techport.tvremote.domain.model.DiscoveredDevice
import com.techport.tvremote.domain.model.PairingState
import com.techport.tvremote.domain.model.RemoteError
import com.techport.tvremote.domain.model.TvDevice
import com.techport.tvremote.protocol.androidtv.wire.PoloConfiguration
import com.techport.tvremote.protocol.androidtv.wire.PoloEncoding
import com.techport.tvremote.protocol.androidtv.wire.PoloEncodingType
import com.techport.tvremote.protocol.androidtv.wire.PoloMessage
import com.techport.tvremote.protocol.androidtv.wire.PoloOptions
import com.techport.tvremote.protocol.androidtv.wire.PoloPairingRequest
import com.techport.tvremote.protocol.androidtv.wire.PoloRole
import com.techport.tvremote.protocol.androidtv.wire.PoloStatus
import com.techport.tvremote.protocol.androidtv.wire.ProtoException
import com.techport.tvremote.protocol.core.PairingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Polo pairing on port 6467 (see PROTOCOLS.md):
 * PairingRequest → Options → Configuration (TV shows a code) → Secret → SecretAck.
 *
 * On success the TV's certificate fingerprint is pinned for [target]'s id. The code the user types
 * exists only as a parameter of [submitCode]; it is never logged or stored.
 */
class AndroidTvPairingSession internal constructor(
    private val target: DiscoveredDevice,
    private val clientName: String,
    private val logger: AppLogger,
    private val pins: CertificatePinStore,
    private val openChannel: suspend () -> SecureChannel,
    private val readTimeoutMs: Long = READ_TIMEOUT_MS,
) : PairingSession {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    override val state: StateFlow<PairingState> = _state.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var channel: SecureChannel? = null

    @Volatile
    private var cancelled = false

    override suspend fun start() = mutex.withLock {
        when (_state.value) {
            PairingState.Starting, is PairingState.AwaitingCode, PairingState.Verifying, is PairingState.Paired -> return@withLock
            else -> Unit
        }
        cancelled = false
        _state.value = PairingState.Starting
        try {
            val ch = openChannel()
            channel = ch
            if (cancelled) {
                closeChannel()
                return@withLock
            }
            exchange(ch, PoloMessage(pairingRequest = PoloPairingRequest(SERVICE_NAME, clientName))) {
                it.pairingRequestAck != null
            }
            val encoding = PoloEncoding(PoloEncodingType.HEXADECIMAL, PairingSecret.CODE_LENGTH)
            exchange(ch, PoloMessage(options = PoloOptions(inputEncodings = listOf(encoding), preferredRole = PoloRole.INPUT))) {
                it.options != null
            }
            exchange(ch, PoloMessage(configuration = PoloConfiguration(encoding, PoloRole.INPUT))) { it.configurationAck }
            logger.i(TAG, "${target.name} is showing a pairing code")
            _state.value = awaitingCode(attemptFailed = false)
        } catch (e: Throwable) {
            fail(e)
        }
    }

    override suspend fun submitCode(code: String) = mutex.withLock {
        val ch = channel
        if (_state.value !is PairingState.AwaitingCode || ch == null) return@withLock
        val normalized = PairingSecret.normalize(code)
        val secret = normalized?.let { PairingSecret.compute(ch.clientKey, ch.serverKey, it) }
        if (secret == null) {
            // Mistyped: the checksum byte does not match. Nothing is sent; the TV keeps its code.
            _state.value = awaitingCode(attemptFailed = true)
            return@withLock
        }
        _state.value = PairingState.Verifying
        try {
            exchange(ch, PoloMessage(secret = secret)) { it.secretAck != null }
            pins.put(target.id, ch.serverFingerprint)
            closeChannel()
            logger.i(TAG, "Paired with ${target.name}")
            _state.value = PairingState.Paired(
                TvDevice(
                    id = target.id,
                    name = target.name,
                    host = target.host,
                    port = target.port,
                    protocolId = AndroidTvProtocol.ID,
                    model = target.model,
                ),
            )
        } catch (e: Throwable) {
            fail(e)
        }
    }

    override fun cancel() {
        cancelled = true
        closeChannel()
        _state.value = PairingState.Idle
    }

    private suspend fun exchange(ch: SecureChannel, message: PoloMessage, accept: (PoloMessage) -> Boolean) {
        ch.transport.send(message.encode())
        val reply = PoloMessage.decode(withTimeout(readTimeoutMs) { ch.transport.receive() })
        if (reply.status != PoloStatus.OK) throw PoloStatusException(reply.status)
        if (!accept(reply)) throw ProtoException("Unexpected pairing reply ${reply.describe()}")
    }

    private fun fail(e: Throwable) {
        closeChannel()
        if (cancelled) {
            _state.value = PairingState.Idle
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            return
        }
        val error = when (e) {
            is TimeoutCancellationException -> RemoteError.TIMEOUT
            is CancellationException -> {
                _state.value = PairingState.Idle
                throw e
            }
            is PoloStatusException -> RemoteError.PAIRING_REJECTED
            else -> mapIoError(e, established = false)
        }
        logger.w(TAG, "Pairing with ${target.name} failed: $error", e.takeUnless { it is PoloStatusException })
        if (e is PoloStatusException) logger.w(TAG, "TV answered pairing with status ${e.status}")
        _state.value = PairingState.Failed(error)
    }

    private fun closeChannel() {
        channel?.transport?.close()
        channel = null
    }

    private fun awaitingCode(attemptFailed: Boolean) =
        PairingState.AwaitingCode(PairingSecret.CODE_LENGTH, CodeAlphabet.HEX, attemptFailed)

    private class PoloStatusException(val status: Int) : Exception("Polo status $status")

    internal companion object {
        const val TAG = "AtvPairing"
        const val SERVICE_NAME = "atvremote"
        const val READ_TIMEOUT_MS = 10_000L
    }
}
