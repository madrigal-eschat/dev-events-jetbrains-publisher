package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener

fun buildBreakpointData(mode: EventMode, filePath: String?, line: Int?): Map<String, Any?> =
    mapOf(
        "file_path" to if (mode == EventMode.FULL) filePath else null,
        "line" to if (mode == EventMode.FULL) line else null
    )

class BreakpointListener : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
        debugProcess.session.addSessionListener(object : XDebugSessionListener {
            override fun sessionPaused() {
                val settings = PluginSettings.getInstance()
                val mode = settings.getEventMode("breakpoint_hit")
                if (mode == EventMode.OFF) return

                val position = debugProcess.session.currentPosition
                val filePath = position?.file?.path
                val line = position?.line?.let { it + 1 } // convert 0-based to 1-based

                MqttPublisherService.getInstance().publish(
                    "breakpoint_hit",
                    buildBreakpointData(mode, filePath, line)
                )
            }
        })
    }
}
