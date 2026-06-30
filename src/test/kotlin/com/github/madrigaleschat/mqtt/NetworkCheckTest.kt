package com.github.madrigaleschat.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class NetworkCheckTest {
    private fun addr(ip: String) = InetAddress.getByName(ip) as Inet4Address

    private fun check(
        subnet: String,
        vararg ips: String,
    ) = isOnHomeNetwork(subnet, ips.map { addr(it) }.asSequence())

    @Test
    fun `empty subnet always returns true`() {
        assertTrue(isOnHomeNetwork("", emptySequence()))
    }

    @Test
    fun `blank subnet always returns true`() {
        assertTrue(isOnHomeNetwork("   ", emptySequence()))
    }

    @Test
    fun `matching address in subnet returns true`() {
        assertTrue(check("192.168.1.0/24", "192.168.1.42"))
    }

    @Test
    fun `address outside subnet returns false`() {
        assertFalse(check("192.168.1.0/24", "192.168.2.1"))
    }

    @Test
    fun `multiple addresses — one matches`() {
        assertTrue(check("10.0.0.0/8", "192.168.1.1", "10.5.3.2"))
    }

    @Test
    fun `slash-32 matches exact host`() {
        assertTrue(check("192.168.1.100/32", "192.168.1.100"))
        assertFalse(check("192.168.1.100/32", "192.168.1.101"))
    }

    @Test
    fun `slash-0 matches everything`() {
        assertTrue(check("0.0.0.0/0", "1.2.3.4"))
    }

    @Test
    fun `invalid subnet without slash returns false`() {
        assertFalse(check("192.168.1.0", "192.168.1.1"))
    }

    @Test
    fun `invalid prefix length returns false`() {
        assertFalse(check("192.168.1.0/33", "192.168.1.1"))
    }

    @Test
    fun `non-numeric prefix returns false`() {
        assertFalse(check("192.168.1.0/abc", "192.168.1.1"))
    }

    @Test
    fun `no local addresses returns false when subnet set`() {
        assertFalse(check("192.168.1.0/24"))
    }
}
