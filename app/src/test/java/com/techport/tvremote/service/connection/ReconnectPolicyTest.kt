package com.techport.tvremote.service.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun growsExponentiallyAndCaps() {
        val p = ReconnectPolicy(initialDelayMs = 500, maxDelayMs = 4_000, jitterRatio = 0.0)
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 4_000L), (1..5).map { p.delayFor(it) })
    }

    @Test
    fun jitterStaysInBounds() {
        val p = ReconnectPolicy(initialDelayMs = 1_000, jitterRatio = 0.2)
        repeat(200) {
            val d = p.delayFor(1)
            assertTrue(d in 800..1_200)
        }
    }
}
