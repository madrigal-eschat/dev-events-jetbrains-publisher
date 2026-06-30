package com.github.spacepilothannah.settings

import com.github.spacepilothannah.model.EventMode
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.util.UUID

private const val CREDENTIAL_SERVICE_NAME = "com.github.spacepilothannah.IDEEventsToWebhook"

fun savePassword(password: String) {
    val attrs = CredentialAttributes(CREDENTIAL_SERVICE_NAME)
    PasswordSafe.instance.set(attrs, Credentials("mqtt", password))
}

fun getPassword(): String =
    PasswordSafe.instance.getPassword(CredentialAttributes(CREDENTIAL_SERVICE_NAME)) ?: ""

@State(
    name = "com.github.spacepilothannah.IDEEventsToWebhook",
    storages = [Storage("IDEEventsToWebhook.xml")]
)
class PluginSettings : PersistentStateComponent<PluginSettings.State> {

    data class State(
        var brokerUrl: String = "tcp://localhost:1883",
        var username: String = "",
        var clientId: String = UUID.randomUUID().toString(),
        var topicPrefix: String = "ide-events",
        var includeHost: Boolean = true,
        var includeProject: Boolean = true,
        var homeSubnet: String = "",
        var eventModes: MutableMap<String, String> = mutableMapOf()
    )

    private var _state = State()

    override fun getState(): State = _state
    override fun loadState(state: State) { _state = state }

    var brokerUrl: String get() = _state.brokerUrl; set(v) { _state.brokerUrl = v }
    var username: String get() = _state.username; set(v) { _state.username = v }
    var clientId: String get() = _state.clientId; set(v) { _state.clientId = v }
    var topicPrefix: String get() = _state.topicPrefix; set(v) { _state.topicPrefix = v }
    var includeHost: Boolean get() = _state.includeHost; set(v) { _state.includeHost = v }
    var includeProject: Boolean get() = _state.includeProject; set(v) { _state.includeProject = v }
    var homeSubnet: String get() = _state.homeSubnet; set(v) { _state.homeSubnet = v }

    fun getEventMode(event: String): EventMode =
        _state.eventModes[event]?.let { runCatching { EventMode.valueOf(it) }.getOrNull() } ?: EventMode.OFF

    fun setEventMode(event: String, mode: EventMode) { _state.eventModes[event] = mode.name }

    fun allEventsOff(): Boolean = ALL_EVENTS.all { getEventMode(it) == EventMode.OFF }

    companion object {
        fun getInstance(): PluginSettings = service()

        val ALL_EVENTS = listOf(
            "task_start", "task_success", "task_fail",
            "test_start", "test_success", "test_fail",
            "file_save", "file_open", "file_close",
            "breakpoint_hit",
            "vcs_commit", "vcs_branch_change",
            "editor_focus_gained", "editor_focus_lost",
            "inspection_complete", "key_presses"
        )

        // Events with no sensitive fields — REDACTED is identical to FULL for these.
        val FULL_ONLY_EVENTS = setOf("vcs_commit", "test_start", "editor_focus_gained", "editor_focus_lost")
    }
}
