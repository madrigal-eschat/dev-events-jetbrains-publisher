package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class FileEditorListener : FileEditorManagerListener {
    override fun fileOpened(
        source: FileEditorManager,
        file: VirtualFile,
    ) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("devevents.file.opened")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            "devevents.file.opened",
            buildFileData(mode, file.path),
            source.project,
        )
    }

    override fun fileClosed(
        source: FileEditorManager,
        file: VirtualFile,
    ) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("devevents.file.closed")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            "devevents.file.closed",
            buildFileData(mode, file.path),
            source.project,
        )
    }
}
