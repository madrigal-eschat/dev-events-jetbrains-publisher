package com.github.madigaleschat.mqtt

import org.junit.Assert.*
import org.junit.Test

class EnvelopeTest {

    @Test
    fun `buildEnvelope sets version to 1`() {
        val env = buildEnvelope("file_save", mapOf("file_path" to "/a.kt"), mapOf("ide_family" to "jetbrains"))
        assertEquals(1, env["version"])
    }

    @Test
    fun `buildEnvelope sets event name`() {
        val env = buildEnvelope("file_save", emptyMap(), emptyMap())
        assertEquals("file_save", env["event"])
    }

    @Test
    fun `buildEnvelope includes data`() {
        val env = buildEnvelope("file_save", mapOf("file_path" to "/a.kt"), emptyMap())
        @Suppress("UNCHECKED_CAST")
        val data = env["data"] as Map<String, Any?>
        assertEquals("/a.kt", data["file_path"])
    }

    @Test
    fun `filterNulls removes null values from map`() {
        val result = mapOf("a" to "x", "b" to null, "c" to "y").filterNulls()
        assertEquals(mapOf("a" to "x", "c" to "y"), result)
    }

    @Test
    fun `filterNulls removes nulls from nested map`() {
        val result = mapOf("data" to mapOf("x" to null, "y" to 1)).filterNulls()
        @Suppress("UNCHECKED_CAST")
        val nested = (result["data"] as Map<String, Any?>)
        assertFalse(nested.containsKey("x"))
        assertEquals(1, nested["y"])
    }

    @Test
    fun `buildEnvelope timestamp is ISO 8601`() {
        val env = buildEnvelope("x", emptyMap(), emptyMap())
        val ts = env["timestamp"] as String
        assertTrue(ts.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*")))
    }
}
