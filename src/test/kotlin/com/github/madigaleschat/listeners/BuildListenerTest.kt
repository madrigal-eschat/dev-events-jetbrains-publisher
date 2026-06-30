package com.github.madigaleschat.listeners

import com.github.madigaleschat.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class BuildListenerTest {

    @Test
    fun `buildTaskData FULL includes name and duration`() {
        val data = buildTaskData(EventMode.FULL, "Build Project", 1234L)
        assertEquals("Build Project", data["name"])
        assertEquals(1234L, data["duration_ms"])
    }

    @Test
    fun `buildTaskData REDACTED omits name, keeps duration`() {
        val data = buildTaskData(EventMode.REDACTED, "Build Project", 1234L)
        assertNull(data["name"])
        assertEquals(1234L, data["duration_ms"])
    }

    @Test
    fun `buildTaskStartData FULL includes name`() {
        val data = buildTaskStartData(EventMode.FULL, "Build Project")
        assertEquals("Build Project", data["name"])
    }

    @Test
    fun `buildTaskStartData REDACTED omits name`() {
        val data = buildTaskStartData(EventMode.REDACTED, "Build Project")
        assertNull(data["name"])
    }

    @Test
    fun `buildTestData FULL returns real counts`() {
        val data = buildTestData(EventMode.FULL, durationMs = 500L, passed = 10, failed = 2, skipped = 1)
        assertEquals(10, data["passed"])
        assertEquals(2, data["failed"])
        assertEquals(1, data["skipped"])
        assertEquals(500L, data["duration_ms"])
    }

    @Test
    fun `buildTestData REDACTED normalises success (failed=0)`() {
        val data = buildTestData(EventMode.REDACTED, durationMs = 500L, passed = 10, failed = 0, skipped = 1)
        assertEquals(1, data["passed"])
        assertEquals(0, data["failed"])
        assertEquals(0, data["skipped"])
    }

    @Test
    fun `buildTestData REDACTED normalises failure (failed greater than 0)`() {
        val data = buildTestData(EventMode.REDACTED, durationMs = 500L, passed = 8, failed = 2, skipped = 0)
        assertEquals(0, data["passed"])
        assertEquals(1, data["failed"])
        assertEquals(0, data["skipped"])
    }
}
