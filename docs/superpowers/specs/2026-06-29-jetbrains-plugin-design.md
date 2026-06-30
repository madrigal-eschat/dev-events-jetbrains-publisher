# JetBrains IDE Events Publisher — Design Spec

**Date:** 2026-06-29  
**Status:** Approved

## Overview

A JetBrains IDE plugin that listens for IDE events and publishes them as JSON over MQTT. Follows the shared message format defined in `../../MESSAGE-FORMAT.md`. Built on Eclipse Paho async client (QoS 0, fire-and-forget). No tool window — configuration lives entirely in the IDE Settings page.

---

## Package Structure

```
com.github.madrigaleschat
├── settings/
│   ├── PluginSettings              PersistentStateComponent (application service)
│   ├── PluginSettingsConfigurable  IDE Settings entry point (Settings > Tools > IDE Events)
│   └── PluginSettingsPanel         Swing panel for the settings form
├── mqtt/
│   └── MqttPublisherService        Application service; owns MqttAsyncClient lifecycle
├── listeners/                      ~15 thin listener classes (one per event type/group)
└── model/
    └── EventMode                   enum: OFF, REDACTED, FULL
```

`MyToolWindowFactory` and `MyMessageBundle` are deleted — entirely replaced.

---

## Settings (`PluginSettings`)

`PersistentStateComponent<PluginSettings>` application service. Serialised to IDE state storage.

| Field | Type | Default | Notes |
|---|---|---|---|
| `brokerUrl` | `String` | `"tcp://localhost:1883"` | |
| `username` | `String` | `""` | |
| `clientId` | `String` | UUID (generated once) | Persisted so reconnects reuse same ID |
| `topicPrefix` | `String` | `"ide-events"` | |
| `includeHost` | `Boolean` | `true` | Controls envelope `source.host` and topic structure |
| `includeProject` | `Boolean` | `true` | Controls envelope `source.project` |
| `eventModes` | `Map<String, EventMode>` | all keys → `OFF` | Key = event name e.g. `"file_save"` |

**Password** is stored and retrieved via JetBrains `PasswordSafe` (`CredentialAttributes` keyed on plugin ID `com.github.madrigaleschat.DevEventsPublisher`). Never written to state storage.

### Topic resolution

- `includeHost = true` → topic: `{topicPrefix}/{hostname}`
- `includeHost = false` → topic: `{topicPrefix}`

---

## Settings UI (`PluginSettingsPanel`)

Fields shown:
- Broker URL text field
- Username text field
- Password field (masked)
- Topic prefix text field
- Read-only resolved-topic label below prefix (e.g. `→ ide-events/my-machine`); updates live when prefix or `includeHost` changes
- Include host checkbox
- Include project checkbox
- Per-event table: one row per event name, columns: **Event** | **Mode** (Off / Redacted / Full combo per row)
- Warning label (red/amber) visible when all events are `OFF`, hidden otherwise

**On plugin startup:** if all events are `OFF`, fire an IDE notification:  
> "IDE Events publisher is not configured — open Settings > Tools > IDE Events"

---

## MQTT Publisher (`MqttPublisherService`)

Application-level service. Lazily connects on first publish.

- Client: `MqttAsyncClient` (Paho), `isAutomaticReconnect = true`
- QoS: 0 (fire-and-forget; dropped silently if broker unavailable)
- JSON: IntelliJ's bundled Gson, `serializeNulls = false` (null fields absent from output)
- Reconnects on broker URL / credential change in settings

### `publish(eventName, data, project?)`

Builds envelope:

```json
{
  "version": 1,
  "event": "<eventName>",
  "timestamp": "<ISO 8601>",
  "source": {
    "host": "<hostname>",
    "project": "<projectName>",
    "ide_family": "jetbrains",
    "ide": "<detected from ApplicationInfo>"
  },
  "data": { }
}
```

- `source.host` included only if `includeHost = true`
- `source.project` included only if `includeProject = true` and `project != null`
- `ide` detected at runtime from `ApplicationInfo.getInstance()`

---

## Listeners

### Redaction contract

Each listener:
1. Reads its `EventMode` from `PluginSettings.eventModes` — returns immediately if `OFF`
2. Builds `Map<String, Any?>` payload; in `REDACTED` mode, sensitive fields are `null` (Gson omits them)
3. Calls `MqttPublisherService.publish(eventName, data, project?)`

### Redaction rules

| Event | Sensitive fields | Redacted behaviour |
|---|---|---|
| `task_start` | `name` | omit `name` |
| `task_success` | `name` | omit `name`; keep `duration_ms` |
| `task_fail` | `name`, `exit_code` | omit both; keep `duration_ms` |
| `test_success` / `test_fail` | `passed`, `failed`, `skipped` | normalise: success→`1,0,0`; fail→`0,1,0` |
| `file_save` / `file_open` / `file_close` | `file_path` | omit `file_path` |
| `breakpoint_hit` | `file_path`, `line` | omit both |
| `vcs_branch_change` | `branch` | omit `branch` |
| `inspection_complete` | `error_count`, `warning_count` | normalise: `1` if any, `0` if none |
| `key_presses` | `count` | always emit `count: 1` |
| `vcs_commit`, `vcs_push`, `test_start`, `editor_focus_gained`, `editor_focus_lost` | none | Redacted = Full |

### Key presses implementation

`TypedActionHandler` wrapper increments an atomic counter per keypress. A `ScheduledExecutorService` (1-second period) checks:
- If count > 0: publish `key_presses` with current count, reset counter
- If count is 0 and previous tick count was > 0: publish once more with `count: 0` (zero-transition event per spec)

Track `lastCount` across ticks to detect the zero transition.

---

## `plugin.xml` Wiring

```xml
<applicationService serviceImplementation="...settings.PluginSettings"/>
<applicationService serviceImplementation="...mqtt.MqttPublisherService"/>

<applicationConfigurable instance="...settings.PluginSettingsConfigurable"
                         displayName="IDE Events" parentId="tools"/>

<listeners>
  <!-- one entry per listener, bound to its platform interface -->
  <listener class="...listeners.FileSaveListener"
            topic="com.intellij.openapi.fileEditor.FileDocumentManagerListener"/>
  <listener class="...listeners.FileEditorListener"
            topic="com.intellij.openapi.fileEditor.FileEditorManagerListener"/>
  <listener class="...listeners.BuildTaskListener"
            topic="com.intellij.task.ProjectTaskListener"/>
  <listener class="...listeners.TestRunListener"
            topic="com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener"/>
  <listener class="...listeners.VcsCommitListener"
            topic="com.intellij.vcs.commit.CommitSessionListener"/>
  <listener class="...listeners.VcsPushListener"
            topic="<!-- TBD: confirm push listener topic during implementation -->"/>
  <listener class="...listeners.VcsBranchListener"
            topic="com.intellij.dvcs.repo.VcsRepositoryMappingListener"/>
  <listener class="...listeners.BreakpointListener"
            topic="com.intellij.xdebugger.XDebugSessionListener"/>
  <listener class="...listeners.AppFocusListener"
            topic="com.intellij.openapi.application.ApplicationActivationListener"/>
  <listener class="...listeners.InspectionListener"
            topic="<!-- TBD: confirm inspection problem listener topic during implementation -->"/>
</listeners>
```

`TypedActionHandler` (key presses) registered programmatically via `ActionManager` at service init — not via XML.

---

## Dependencies to add to `build.gradle.kts`

```kotlin
implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
```

Gson is bundled with IntelliJ Platform — no additional dependency needed.
