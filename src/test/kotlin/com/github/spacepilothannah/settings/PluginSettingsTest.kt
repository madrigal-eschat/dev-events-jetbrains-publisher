package com.github.spacepilothannah.settings

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class PluginSettingsTest {

    private fun freshState() = PluginSettings.State()

    @Test
    fun `default brokerUrl is tcp localhost 1883`() {
        assertEquals("tcp://localhost:1883", freshState().brokerUrl)
    }

    @Test
    fun `default topicPrefix is ide-events`() {
        assertEquals("ide-events", freshState().topicPrefix)
    }

    @Test
    fun `default includeHost is true`() {
        assertTrue(freshState().includeHost)
    }

    @Test
    fun `default includeProject is true`() {
        assertTrue(freshState().includeProject)
    }

    @Test
    fun `getEventMode returns OFF for unknown event`() {
        val settings = PluginSettings()
        assertEquals(EventMode.OFF, settings.getEventMode("file_save"))
    }

    @Test
    fun `setEventMode and getEventMode round-trip`() {
        val settings = PluginSettings()
        settings.setEventMode("file_save", EventMode.FULL)
        assertEquals(EventMode.FULL, settings.getEventMode("file_save"))
    }

    @Test
    fun `allEventsOff returns true when no modes set`() {
        val settings = PluginSettings()
        assertTrue(settings.allEventsOff())
    }

    @Test
    fun `allEventsOff returns false when any event is not OFF`() {
        val settings = PluginSettings()
        settings.setEventMode("file_save", EventMode.REDACTED)
        assertFalse(settings.allEventsOff())
    }

    @Test
    fun `ALL_EVENTS contains all 16 events`() {
        assertEquals(16, PluginSettings.ALL_EVENTS.size)
    }
}
