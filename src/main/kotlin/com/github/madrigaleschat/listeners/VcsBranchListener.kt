package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.openapi.vcs.BranchChangeListener

fun buildBranchData(mode: EventMode, branch: String?): Map<String, Any?> =
    mapOf("branch" to if (mode == EventMode.FULL) branch else null)

class VcsBranchListener : BranchChangeListener {
    override fun branchWillChange(branchName: String) {}

    override fun branchHasChanged(branchName: String) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("vcs_branch_change")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            "vcs_branch_change", buildBranchData(mode, branchName)
        )
    }
}
