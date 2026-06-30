package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class VcsListenerTest {

    @Test
    fun `buildBranchData FULL includes branch name`() {
        val data = buildBranchData(EventMode.FULL, "main")
        assertEquals("main", data["branch"])
    }

    @Test
    fun `buildBranchData REDACTED omits branch name`() {
        val data = buildBranchData(EventMode.REDACTED, "main")
        assertNull(data["branch"])
    }
}
