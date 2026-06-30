package com.github.madrigaleschat.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeTest {
    private fun env(
        type: String = "devevents.file.saved",
        data: Map<String, Any?> = emptyMap(),
        source: String = "editor/jeff/jetbrains/intellij-idea",
        subject: String? = null,
    ) = buildEnvelope(type, data, source, subject)

    @Test
    fun `buildEnvelope sets specversion to 1_0`() {
        assertEquals("1.0", env()["specversion"])
    }

    @Test
    fun `buildEnvelope sets type`() {
        assertEquals("devevents.file.saved", env()["type"])
    }

    @Test
    fun `buildEnvelope sets sourcetype to editor`() {
        assertEquals("editor", env()["sourcetype"])
    }

    @Test
    fun `buildEnvelope sets source`() {
        assertEquals("editor/jeff/jetbrains/intellij-idea", env()["source"])
    }

    @Test
    fun `buildEnvelope id is a UUID`() {
        val id = env()["id"] as String
        assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `buildEnvelope each call gets unique id`() {
        assertNotEquals(env()["id"], env()["id"])
    }

    @Test
    fun `buildEnvelope time is ISO 8601`() {
        val time = env()["time"] as String
        assertTrue(time.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*")))
    }

    @Test
    fun `buildEnvelope includes data`() {
        val result = env(data = mapOf("file_path" to "/a.kt"))
        @Suppress("UNCHECKED_CAST")
        assertEquals("/a.kt", (result["data"] as Map<String, Any?>)["file_path"])
    }

    @Test
    fun `buildEnvelope includes subject when provided`() {
        assertEquals("~/projects/foo", env(subject = "~/projects/foo")["subject"])
    }

    @Test
    fun `buildEnvelope subject is null when omitted`() {
        assertNull(env(subject = null)["subject"])
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
}
