package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame

class AppFocusListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("devevents.editor.focus.gained")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("devevents.editor.focus.gained", emptyMap())
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("devevents.editor.focus.lost")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("devevents.editor.focus.lost", emptyMap())
    }
}
