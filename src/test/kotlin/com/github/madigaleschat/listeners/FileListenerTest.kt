package com.github.madigaleschat.listeners

import com.github.madigaleschat.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class FileListenerTest {

    @Test
    fun `buildFileData FULL includes file path`() {
        val data = buildFileData(EventMode.FULL, "/src/main.kt")
        assertEquals("/src/main.kt", data["file_path"])
    }

    @Test
    fun `buildFileData REDACTED omits file path`() {
        val data = buildFileData(EventMode.REDACTED, "/src/main.kt")
        assertNull(data["file_path"])
    }

    @Test
    fun `buildFileData FULL with null path produces null entry`() {
        val data = buildFileData(EventMode.FULL, null)
        assertNull(data["file_path"])
    }
}
