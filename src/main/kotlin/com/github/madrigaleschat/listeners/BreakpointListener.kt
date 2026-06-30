package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener

fun buildBreakpointData(
    mode: EventMode,
    filePath: String?,
    line: Int?,
): Map<String, Any?> =
    mapOf(
        "file_path" to if (mode == EventMode.FULL) filePath else null,
        "line" to if (mode == EventMode.FULL) line else null,
    )

class BreakpointListener : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
        debugProcess.session.addSessionListener(
            object : XDebugSessionListener {
                override fun sessionPaused() {
                    val settings = PluginSettings.getInstance()
                    val mode = settings.getEventMode("devevents.breakpoint.hit")
                    if (mode == EventMode.OFF) return

                    val position = debugProcess.session.currentPosition
                    val filePath = position?.file?.path
                    val line = position?.line?.let { it + 1 } // convert 0-based to 1-based

                    MqttPublisherService.getInstance().publish(
                        "devevents.breakpoint.hit",
                        buildBreakpointData(mode, filePath, line),
                    )
                }
            },
        )
    }
}
