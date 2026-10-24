package com.techport.tvremote.protocol.androidtv

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * The Polo pairing secret:
 * `SHA-256(hex(clientN) ‖ hex(clientE) ‖ hex(serverN) ‖ hex(serverE) ‖ code[2..6])`, where hex(x) is
 * x's unsigned big-endian bytes (its uppercase hex left-padded to even length). The TV accepts it only
 * if `hash[0] == code[0..2]`, which lets us reject a mistyped code without a round trip.
 */
internal object PairingSecret {
    const val CODE_LENGTH = 6

    /** Normalises user input; null if it is not exactly six hex digits. */
    fun normalize(code: String): String? {
        val c = code.trim().uppercase()
        return c.takeIf { it.length == CODE_LENGTH && it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } }
    }

    /** Secret for [code] (already normalised), or null when the code's checksum byte does not match. */
    fun compute(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
        require(code.length == CODE_LENGTH)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(hexBytes(client.modulus))
        digest.update(hexBytes(client.publicExponent))
        digest.update(hexBytes(server.modulus))
        digest.update(hexBytes(server.publicExponent))
        digest.update(decodeHex(code.substring(2)))
        val hash = digest.digest()
        val check = code.substring(0, 2).toInt(16)
        return hash.takeIf { (it[0].toInt() and 0xFF) == check }
    }

    /** Uppercase hex of [value], left-padded with '0' to even length, decoded to bytes. */
    fun hexBytes(value: BigInteger): ByteArray {
        require(value.signum() >= 0)
        val hex = value.toString(16).uppercase()
        return decodeHex(if (hex.length % 2 == 0) hex else "0$hex")
    }

    private fun decodeHex(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
