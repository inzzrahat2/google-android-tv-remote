package com.techport.tvremote.protocol.androidtv.wire

/*
 * Pairing ("Polo") messages, port 6467. Field numbers from AOSP
 * external/google-tv-pairing-protocol polo.proto. Only what pairing uses is modelled.
 */

internal object PoloStatus {
    const val OK = 200
    const val ERROR = 400
    const val BAD_CONFIGURATION = 401
    const val BAD_SECRET = 402
}

internal object PoloEncodingType {
    const val HEXADECIMAL = 3
}

internal object PoloRole {
    const val INPUT = 1
}

internal data class PoloEncoding(val type: Int, val symbolLength: Int)

internal data class PoloPairingRequest(val serviceName: String, val clientName: String?)

internal data class PoloPairingRequestAck(val serverName: String?)

internal data class PoloOptions(
    val inputEncodings: List<PoloEncoding> = emptyList(),
    val outputEncodings: List<PoloEncoding> = emptyList(),
    val preferredRole: Int? = null,
)

internal data class PoloConfiguration(val encoding: PoloEncoding, val clientRole: Int)

/** OuterMessage. Exactly one payload is expected per message. */
internal class PoloMessage(
    val protocolVersion: Int = 2,
    val status: Int = PoloStatus.OK,
    val pairingRequest: PoloPairingRequest? = null,
    val pairingRequestAck: PoloPairingRequestAck? = null,
    val options: PoloOptions? = null,
    val configuration: PoloConfiguration? = null,
    val configurationAck: Boolean = false,
    /** Secret hash. Never log. */
    val secret: ByteArray? = null,
    val secretAck: ByteArray? = null,
) {
    fun encode(): ByteArray = ProtoWriter.encode {
        int(1, protocolVersion)
        int(2, status)
        pairingRequest?.let { r ->
            message(10) {
                string(1, r.serviceName)
                r.clientName?.let { string(2, it) }
            }
        }
        pairingRequestAck?.let { a -> message(11) { a.serverName?.let { string(1, it) } } }
        options?.let { o ->
            message(20) {
                o.inputEncodings.forEach { e -> message(1) { encoding(e) } }
                o.outputEncodings.forEach { e -> message(2) { encoding(e) } }
                o.preferredRole?.let { int(3, it) }
            }
        }
        configuration?.let { c ->
            message(30) {
                message(1) { encoding(c.encoding) }
                int(2, c.clientRole)
            }
        }
        if (configurationAck) message(31) {}
        secret?.let { s -> message(40) { bytes(1, s) } }
        secretAck?.let { s -> message(41) { bytes(1, s) } }
    }

    /** Short description for logs. Never includes secrets. */
    fun describe(): String {
        val payload = when {
            pairingRequest != null -> "pairing_request"
            pairingRequestAck != null -> "pairing_request_ack"
            options != null -> "options"
            configuration != null -> "configuration"
            configurationAck -> "configuration_ack"
            secret != null -> "secret"
            secretAck != null -> "secret_ack"
            else -> "empty"
        }
        return "polo{status=$status, $payload}"
    }

    companion object {
        fun decode(bytes: ByteArray): PoloMessage {
            var version = 0
            var status = 0
            var request: PoloPairingRequest? = null
            var requestAck: PoloPairingRequestAck? = null
            var options: PoloOptions? = null
            var configuration: PoloConfiguration? = null
            var configurationAck = false
            var secret: ByteArray? = null
            var secretAck: ByteArray? = null
            ProtoReader(bytes).forEachField { field ->
                when (field) {
                    1 -> version = int()
                    2 -> status = int()
                    10 -> request = decodePairingRequest(message())
                    11 -> requestAck = decodePairingRequestAck(message())
                    20 -> options = decodeOptions(message())
                    30 -> configuration = decodeConfiguration(message())
                    31 -> { message(); configurationAck = true }
                    40 -> secret = decodeSecret(message())
                    41 -> secretAck = decodeSecret(message())
                    else -> skip()
                }
            }
            return PoloMessage(version, status, request, requestAck, options, configuration, configurationAck, secret, secretAck)
        }

        private fun ProtoWriter.encoding(e: PoloEncoding) {
            int(1, e.type)
            int(2, e.symbolLength)
        }

        private fun decodeEncoding(r: ProtoReader): PoloEncoding {
            var type = 0
            var length = 0
            r.forEachField { field ->
                when (field) {
                    1 -> type = int()
                    2 -> length = int()
                    else -> skip()
                }
            }
            return PoloEncoding(type, length)
        }

        private fun decodePairingRequest(r: ProtoReader): PoloPairingRequest {
            var service = ""
            var client: String? = null
            r.forEachField { field ->
                when (field) {
                    1 -> service = string()
                    2 -> client = string()
                    else -> skip()
                }
            }
            return PoloPairingRequest(service, client)
        }

        private fun decodePairingRequestAck(r: ProtoReader): PoloPairingRequestAck {
            var name: String? = null
            r.forEachField { field -> if (field == 1) name = string() else skip() }
            return PoloPairingRequestAck(name)
        }

        private fun decodeOptions(r: ProtoReader): PoloOptions {
            val input = mutableListOf<PoloEncoding>()
            val output = mutableListOf<PoloEncoding>()
            var role: Int? = null
            r.forEachField { field ->
                when (field) {
                    1 -> input += decodeEncoding(message())
                    2 -> output += decodeEncoding(message())
                    3 -> role = int()
                    else -> skip()
                }
            }
            return PoloOptions(input, output, role)
        }

        private fun decodeConfiguration(r: ProtoReader): PoloConfiguration {
            var encoding = PoloEncoding(0, 0)
            var role = 0
            r.forEachField { field ->
                when (field) {
                    1 -> encoding = decodeEncoding(message())
                    2 -> role = int()
                    else -> skip()
                }
            }
            return PoloConfiguration(encoding, role)
        }

        private fun decodeSecret(r: ProtoReader): ByteArray {
            var secret = ByteArray(0)
            r.forEachField { field -> if (field == 1) secret = bytes() else skip() }
            return secret
        }
    }
}
