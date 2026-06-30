# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

JetBrains IDE plugin that listens for IDE events and publishes them as CloudEvents 1.0 envelopes over MQTT. Part of the broader IDEEvents ecosystem — message format is defined in `../../MESSAGE-FORMAT.md`.

## Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run tests only
./gradlew runIde         # launch sandbox IDE with plugin loaded (slow first run)
./gradlew verifyPlugin   # compatibility checks
```

Run a single test class:
```bash
./gradlew test --tests "com.github.madrigaleschat.SomeTest"
```

**Lint (run before every commit):**
```bash
find src -name "*.kt" -print0 | xargs -0 ktlint          # check
find src -name "*.kt" -print0 | xargs -0 ktlint --format  # auto-fix
```

ktlint does not auto-fix wildcard imports (`standard:no-wildcard-imports`) — expand those manually.

## Architecture

**Package root:** `com.github.madrigaleschat`  
**Platform:** IntelliJ Platform Gradle Plugin targeting IntelliJ IDEA 2025.3.5  
**MQTT library:** Eclipse Paho (`org.eclipse.paho.client.mqttv3`)

### Data flow

1. An IntelliJ extension point fires (build, file, VCS, debugger, focus, keypress)
2. The listener calls `MqttPublisherService.getInstance().publish(eventName, data, project?)`
3. `publish()` checks: MQTT connected? On home network? Event mode ≠ OFF?
4. Wraps data in a CloudEvents 1.0 envelope via `buildEnvelope()` and publishes JSON to the MQTT topic

### Key components

**`MqttPublisherService`** — application-level service (registered in `plugin.xml`). Connects on init in a pooled thread. `publish()` is fire-and-forget (QoS 0). Call `reconfigure()` after settings change.

**`PluginSettings`** — `PersistentStateComponent` stored in `DevEventsPublisher.xml`. Password stored separately via IDE `PasswordSafe`. Each event has an `EventMode` (OFF / REDACTED / FULL) stored as a string map. `FULL_ONLY_EVENTS` identifies events with no sensitive fields where REDACTED degrades to FULL.

**`isOnHomeNetwork(subnet)`** — CIDR check against all local IPv4 addresses. Blank subnet = always publish.

**`buildEnvelope()`** — produces a CloudEvents 1.0 map. Topic is `{topicPrefix}/{hostname}` when `includeHost=true`; source is `editor/{host}/jetbrains/{ide-product}`.

**`StartupNotifier`** — warns at project open if all events are OFF.

**`KeyPressInstaller`** — installs a raw key press listener via `IdeEventQueue`.

### Settings UI

`PluginSettingsConfigurable` → `PluginSettingsPanel`. Per-event mode is a `JComboBox` inside a `JBTable` using `DefaultCellEditor`. Settings UI lives at **Settings > Tools > IDE Events**.

### Registering new listeners

All extension points must be declared in `src/main/resources/META-INF/plugin.xml` before they fire. Each listener calls `MqttPublisherService` directly — no intermediate bus.

Logs from `runIde` appear in `.intellijPlatform/sandbox/*/log/idea.log`.
