package com.techport.tvremote.core.connectivity

/** An IPv4 network, e.g. 192.168.1.0/24. Pure Kotlin so it can be unit tested. */
data class Ipv4Subnet(val network: Int, val prefixLength: Int) {
    init {
        require(prefixLength in 0..32) { "prefixLength out of range: $prefixLength" }
    }

    private val mask: Int get() = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))

    operator fun contains(address: Int): Boolean = (address and mask) == (network and mask)

    /** True when [host] is an IPv4 literal inside this subnet. Host names are never resolved. */
    fun containsHost(host: String): Boolean = parseIpv4(host)?.let { it in this } ?: false

    override fun toString(): String = "${formatIpv4(network and mask)}/$prefixLength"

    companion object {
        fun of(address: ByteArray, prefixLength: Int): Ipv4Subnet? {
            if (address.size != 4 || prefixLength !in 0..32) return null
            val value = address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            return Ipv4Subnet(value, prefixLength)
        }

        /** Parses a dotted-quad IPv4 literal; null for anything else (including host names). */
        fun parseIpv4(text: String): Int? {
            val parts = text.trim().split('.')
            if (parts.size != 4) return null
            var value = 0
            for (part in parts) {
                if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return null
                val octet = part.toInt()
                if (octet > 255) return null
                value = (value shl 8) or octet
            }
            return value
        }

        private fun formatIpv4(value: Int): String =
            (3 downTo 0).joinToString(".") { ((value ushr (it * 8)) and 0xFF).toString() }
    }
}
