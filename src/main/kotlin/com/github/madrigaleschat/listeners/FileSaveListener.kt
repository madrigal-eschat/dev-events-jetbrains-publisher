package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener

fun buildFileData(mode: EventMode, filePath: String?): Map<String, Any?> =
    mapOf("file_path" to if (mode == EventMode.FULL) filePath else null)

class FileSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("devevents.file.saved")
        if (mode == EventMode.OFF) return

        val file = FileDocumentManager.getInstance().getFile(document)
        val project = file?.let {
            com.intellij.openapi.project.ProjectLocator.getInstance().guessProjectForFile(it)
        }
        MqttPublisherService.getInstance().publish(
            "devevents.file.saved",
            buildFileData(mode, file?.path),
            project
        )
    }
}
