package com.github.madigaleschat.listeners

import com.github.madigaleschat.model.EventMode
import com.github.madigaleschat.mqtt.MqttPublisherService
import com.github.madigaleschat.settings.PluginSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.impl.DocumentMarkupModel

fun buildInspectionData(mode: EventMode, errorCount: Int, warningCount: Int): Map<String, Any?> =
    if (mode == EventMode.FULL) {
        mapOf("error_count" to errorCount, "warning_count" to warningCount)
    } else {
        mapOf(
            "error_count" to if (errorCount > 0) 1 else 0,
            "warning_count" to if (warningCount > 0) 1 else 0
        )
    }

class InspectionListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
    override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("inspection_complete")
        if (mode == EventMode.OFF) return

        var errorCount = 0
        var warningCount = 0

        fileEditors.forEach { editor ->
            val file = editor.file ?: return@forEach
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@forEach
            val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return@forEach
            markupModel.allHighlighters.forEach { highlighter ->
                val info = highlighter.errorStripeTooltip as? HighlightInfo ?: return@forEach
                when {
                    info.severity >= HighlightSeverity.ERROR -> errorCount++
                    info.severity >= HighlightSeverity.WARNING -> warningCount++
                }
            }
        }

        MqttPublisherService.getInstance().publish(
            "inspection_complete",
            buildInspectionData(mode, errorCount, warningCount),
            project
        )
    }
}
