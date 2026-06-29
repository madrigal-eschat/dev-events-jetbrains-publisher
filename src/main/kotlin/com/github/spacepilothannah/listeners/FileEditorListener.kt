package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class FileEditorListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("file_open")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            "file_open", buildFileData(mode, file.path), source.project
        )
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("file_close")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            "file_close", buildFileData(mode, file.path), source.project
        )
    }
}
