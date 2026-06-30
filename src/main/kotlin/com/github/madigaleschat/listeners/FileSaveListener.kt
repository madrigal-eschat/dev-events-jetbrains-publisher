package com.github.madigaleschat.listeners

import com.github.madigaleschat.model.EventMode
import com.github.madigaleschat.mqtt.MqttPublisherService
import com.github.madigaleschat.settings.PluginSettings
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener

fun buildFileData(mode: EventMode, filePath: String?): Map<String, Any?> =
    mapOf("file_path" to if (mode == EventMode.FULL) filePath else null)

class FileSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("file_save")
        if (mode == EventMode.OFF) return

        val file = FileDocumentManager.getInstance().getFile(document)
        val project = file?.let {
            com.intellij.openapi.project.ProjectLocator.getInstance().guessProjectForFile(it)
        }
        MqttPublisherService.getInstance().publish(
            "file_save",
            buildFileData(mode, file?.path),
            project
        )
    }
}
