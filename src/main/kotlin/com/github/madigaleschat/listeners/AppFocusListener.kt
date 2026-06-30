package com.github.madigaleschat.listeners

import com.github.madigaleschat.model.EventMode
import com.github.madigaleschat.mqtt.MqttPublisherService
import com.github.madigaleschat.settings.PluginSettings
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame

class AppFocusListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("editor_focus_gained")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("editor_focus_gained", emptyMap())
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("editor_focus_lost")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("editor_focus_lost", emptyMap())
    }
}
