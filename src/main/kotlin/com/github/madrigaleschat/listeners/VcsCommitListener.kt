package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory

class VcsCommitListener : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return object : CheckinHandler() {
            override fun checkinSuccessful() {
                val settings = PluginSettings.getInstance()
                val mode = settings.getEventMode("devevents.vcs.committed")
                if (mode == EventMode.OFF) return
                MqttPublisherService.getInstance().publish("devevents.vcs.committed", emptyMap(), panel.project)
            }
        }
    }
}
