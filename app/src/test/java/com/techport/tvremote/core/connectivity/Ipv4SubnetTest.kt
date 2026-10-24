package com.techport.tvremote.core.connectivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4SubnetTest {
    private fun subnet(a: Int, b: Int, c: Int, d: Int, prefix: Int) =
        Ipv4Subnet.of(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()), prefix)!!

    @Test
    fun parsesOnlyDottedQuads() {
        assertEquals(0xC0A80114.toInt(), Ipv4Subnet.parseIpv4("192.168.1.20"))
        assertNull(Ipv4Subnet.parseIpv4("192.168.1"))
        assertNull(Ipv4Subnet.parseIpv4("192.168.1.256"))
        assertNull(Ipv4Subnet.parseIpv4("tv.local"))
        assertNull(Ipv4Subnet.parseIpv4("fe80::1"))
        assertNull(Ipv4Subnet.parseIpv4("1.2.3.-4"))
    }

    @Test
    fun membership() {
        val home = subnet(192, 168, 1, 5, 24)
        assertTrue(home.containsHost("192.168.1.20"))
        assertFalse(home.containsHost("192.168.2.20"))
        assertFalse(home.containsHost("simulated"))

        val wide = subnet(10, 1, 2, 3, 8)
        assertTrue(wide.containsHost("10.200.0.1"))
        assertTrue(subnet(10, 0, 0, 1, 0).containsHost("8.8.8.8"))
        assertTrue(subnet(10, 0, 0, 1, 32).containsHost("10.0.0.1"))
        assertFalse(subnet(10, 0, 0, 1, 32).containsHost("10.0.0.2"))
    }

    @Test
    fun rejectsBadInputAndFormats() {
        assertNull(Ipv4Subnet.of(ByteArray(16), 64))
        assertNull(Ipv4Subnet.of(byteArrayOf(1, 2, 3, 4), 33))
        assertEquals("192.168.1.0/24", subnet(192, 168, 1, 5, 24).toString())
    }
}
