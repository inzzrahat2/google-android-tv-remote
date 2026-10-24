package com.techport.tvremote.protocol.androidtv

import java.util.Locale

/**
 * Syntax checks for a manually typed TV address. Pure string logic (no DNS lookups), so it can run
 * on the main thread while the user types.
 */
object HostValidation {
    /** Returns the host in canonical form (trimmed, brackets removed, lowercase), or null if invalid. */
    fun normalize(input: String): String? {
        var host = input.trim()
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length - 1)
        if (host.isEmpty() || host.length > 253) return null
        return when {
            host.contains(':') -> host.takeIf { isIpv6(it) }?.lowercase(Locale.ROOT)
            host.all { it.isDigit() || it == '.' } -> host.takeIf { isIpv4(it) }
            else -> host.removeSuffix(".").takeIf { isHostname(it) }?.lowercase(Locale.ROOT)
        }
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isIpv4(s: String): Boolean {
        val parts = s.split('.')
        return parts.size == 4 && parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && (p.length == 1 || p[0] != '0') && p.toInt() <= 255
        }
    }

    fun isIpv6(input: String): Boolean {
        val scopeAt = input.indexOf('%')
        val s = if (scopeAt >= 0) {
            val scope = input.substring(scopeAt + 1)
            if (scope.isEmpty() || !scope.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }) return false
            input.substring(0, scopeAt)
        } else {
            input
        }
        // An embedded IPv4 tail (e.g. ::ffff:192.168.1.2) stands for two 16-bit groups.
        val tail = s.substringAfterLast(':', "")
        val pure = if (tail.contains('.')) {
            if (!isIpv4(tail)) return false
            s.dropLast(tail.length) + "0:0"
        } else {
            s
        }
        if (pure.contains(":::")) return false
        val halves = pure.split("::")
        fun groups(part: String) = if (part.isEmpty()) emptyList() else part.split(':')
        fun valid(groups: List<String>) =
            groups.all { g -> g.length in 1..4 && g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
        return when (halves.size) {
            1 -> groups(pure).let { it.size == 8 && valid(it) }
            2 -> (groups(halves[0]) + groups(halves[1])).let { it.size <= 7 && valid(it) }
            else -> false
        }
    }

    fun isHostname(s: String): Boolean {
        if (s.isEmpty() || s.length > 253) return false
        val labels = s.split('.')
        if (labels.all { l -> l.all(Char::isDigit) }) return false
        return labels.all { l ->
            l.length in 1..63 && l.first() != '-' && l.last() != '-' &&
                l.all { (it in 'a'..'z') || (it in 'A'..'Z') || it.isDigit() || it == '-' }
        }
    }
}
