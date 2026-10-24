package com.techport.tvremote.service.connection

import kotlin.math.min
import kotlin.random.Random

/** Exponential backoff with jitter so a sleeping TV or rebooting router is not hammered. */
data class ReconnectPolicy(
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 30_000,
    val maxAttempts: Int = 8,
    val jitterRatio: Double = 0.2,
) {
    fun delayFor(attempt: Int, random: Random = Random.Default): Long {
        val exp = initialDelayMs shl (attempt - 1).coerceIn(0, 16)
        val base = min(exp, maxDelayMs)
        val jitter = (base * jitterRatio * (random.nextDouble() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(0)
    }
}
