package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class InspectionListenerTest {

    @Test
    fun `buildInspectionData FULL includes real counts`() {
        val data = buildInspectionData(EventMode.FULL, errorCount = 3, warningCount = 14)
        assertEquals(3, data["error_count"])
        assertEquals(14, data["warning_count"])
    }

    @Test
    fun `buildInspectionData REDACTED normalises to 1 when counts greater than 0`() {
        val data = buildInspectionData(EventMode.REDACTED, errorCount = 3, warningCount = 14)
        assertEquals(1, data["error_count"])
        assertEquals(1, data["warning_count"])
    }

    @Test
    fun `buildInspectionData REDACTED normalises to 0 when counts are 0`() {
        val data = buildInspectionData(EventMode.REDACTED, errorCount = 0, warningCount = 0)
        assertEquals(0, data["error_count"])
        assertEquals(0, data["warning_count"])
    }
}
