package com.github.madigaleschat.settings

import com.github.madigaleschat.mqtt.MqttPublisherService
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class PluginSettingsConfigurable : Configurable {
    private var settingsPanel: PluginSettingsPanel? = null

    override fun getDisplayName() = "IDE Events"

    override fun createComponent(): JComponent {
        val p = PluginSettingsPanel()
        p.reset(PluginSettings.getInstance())
        settingsPanel = p
        return p.panel
    }

    override fun isModified(): Boolean =
        settingsPanel?.isModified(PluginSettings.getInstance()) ?: false

    override fun apply() {
        settingsPanel?.apply(PluginSettings.getInstance())
        MqttPublisherService.getInstance().reconfigure()
    }

    override fun reset() {
        settingsPanel?.reset(PluginSettings.getInstance())
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
