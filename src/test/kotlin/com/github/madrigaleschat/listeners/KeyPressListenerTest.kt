package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPressListenerTest {
    @Test
    fun `buildKeyPressData FULL returns real count`() {
        val data = buildKeyPressData(EventMode.FULL, 37)
        assertEquals(37, data["count"])
    }

    @Test
    fun `buildKeyPressData REDACTED always returns count 1`() {
        val data = buildKeyPressData(EventMode.REDACTED, 37)
        assertEquals(1, data["count"])
    }

    @Test
    fun `zero transition detected when lastCount positive and current zero`() {
        assertTrue(isZeroTransition(lastCount = 5, currentCount = 0))
    }

    @Test
    fun `zero transition not detected when both zero`() {
        assertFalse(isZeroTransition(lastCount = 0, currentCount = 0))
    }

    @Test
    fun `zero transition not detected when current nonzero`() {
        assertFalse(isZeroTransition(lastCount = 5, currentCount = 3))
    }
}
