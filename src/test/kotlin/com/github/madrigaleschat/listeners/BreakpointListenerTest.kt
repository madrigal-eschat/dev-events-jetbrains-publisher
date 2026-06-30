package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakpointListenerTest {
    @Test
    fun `buildBreakpointData FULL includes file path and line`() {
        val data = buildBreakpointData(EventMode.FULL, "/src/Main.kt", 42)
        assertEquals("/src/Main.kt", data["file_path"])
        assertEquals(42, data["line"])
    }

    @Test
    fun `buildBreakpointData REDACTED omits file path and line`() {
        val data = buildBreakpointData(EventMode.REDACTED, "/src/Main.kt", 42)
        assertNull(data["file_path"])
        assertNull(data["line"])
    }
}
