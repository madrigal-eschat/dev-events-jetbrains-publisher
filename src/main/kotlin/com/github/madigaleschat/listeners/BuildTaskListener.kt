package com.github.madigaleschat.listeners

import com.github.madigaleschat.model.EventMode
import com.github.madigaleschat.mqtt.MqttPublisherService
import com.github.madigaleschat.settings.PluginSettings
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import java.util.concurrent.ConcurrentHashMap

fun buildTaskStartData(mode: EventMode, name: String?): Map<String, Any?> =
    mapOf("name" to if (mode == EventMode.FULL) name else null)

fun buildTaskData(mode: EventMode, name: String?, durationMs: Long?): Map<String, Any?> =
    mapOf(
        "duration_ms" to durationMs,
        "name" to if (mode == EventMode.FULL) name else null
    )

class BuildTaskListener : ProjectTaskListener {

    private val startTimes = ConcurrentHashMap<Int, Long>()

    override fun started(context: ProjectTaskContext) {
        startTimes[System.identityHashCode(context)] = System.currentTimeMillis()

        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("task_start")
        if (mode == EventMode.OFF) return

        val name = context.runConfiguration?.name
        MqttPublisherService.getInstance().publish(
            "task_start", buildTaskStartData(mode, name)
        )
    }

    override fun finished(result: ProjectTaskManager.Result) {
        val context = result.context
        val startTime = startTimes.remove(System.identityHashCode(context))
        val durationMs = startTime?.let { System.currentTimeMillis() - it }

        val settings = PluginSettings.getInstance()
        val eventName = if (result.hasErrors()) "task_fail" else "task_success"
        val mode = settings.getEventMode(eventName)
        if (mode == EventMode.OFF) return

        val name = context.runConfiguration?.name
        MqttPublisherService.getInstance().publish(
            eventName, buildTaskData(mode, name, durationMs)
        )
    }
}
