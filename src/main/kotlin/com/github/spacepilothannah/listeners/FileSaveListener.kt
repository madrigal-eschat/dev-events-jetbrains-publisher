package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
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
