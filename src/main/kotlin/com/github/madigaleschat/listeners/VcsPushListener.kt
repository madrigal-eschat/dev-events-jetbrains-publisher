package com.github.madigaleschat.listeners

import com.github.madigaleschat.model.EventMode
import com.github.madigaleschat.mqtt.MqttPublisherService
import com.github.madigaleschat.settings.PluginSettings

// TODO: Placeholder — replace with the correct push listener interface from https://jb.gg/ipe
// Search for "push" under git4idea to find the verified interface and topic string.
// Do not register in plugin.xml until the correct interface is confirmed.
class VcsPushListener {
    fun onPushSuccessful() {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("vcs_push")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("vcs_push", emptyMap())
    }
}
