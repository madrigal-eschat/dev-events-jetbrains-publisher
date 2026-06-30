# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

JetBrains IDE plugin that listens for IDE events and publishes them as JSON over MQTT. Part of the broader IDEEvents ecosystem — message format is defined in `../../MESSAGE-FORMAT.md` (topic: `ide-events/{host}`, envelope schema with `version`, `event`, `timestamp`, `source`, `data` fields).

Currently early-stage: scaffolding only. The real implementation (MQTT client, event listeners, settings UI) is yet to be built.

## Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run tests only
./gradlew runIde         # launch sandbox IDE with plugin loaded (slow first run)
./gradlew verifyPlugin   # compatibility checks
```

Run a single test class:
```bash
./gradlew test --tests "com.github.spacepilothannah.SomeTest"
```

## Architecture

**IntelliJ Platform Gradle Plugin** (`org.jetbrains.intellij.platform`) targets IntelliJ IDEA 2025.3.5. Package root: `com.github.spacepilothannah`.

Plugin entry points are declared in `src/main/resources/META-INF/plugin.xml` — extension points for listeners (e.g. `FileDocumentManagerListener`, `VcsListener`, build listeners) must be registered here before they fire.

Current source files are template scaffolding:
- `MyToolWindowFactory` — placeholder tool window (replace with settings/status UI)
- `MyMessageBundle` — i18n helper backed by `messages/MyMessageBundle.properties`

**Extension points to implement** (wire in `plugin.xml`):
- `com.intellij.openapi.fileEditor.FileEditorManagerListener` — file open/close/save
- `com.intellij.task.ProjectTaskListener` — build/test task start/success/fail
- `com.intellij.openapi.vcs.changes.CommitSessionListener` / push listeners — VCS events
- `com.intellij.xdebugger.XDebugSessionListener` — breakpoint hit
- `com.intellij.openapi.application.ApplicationActivationListener` — focus gained/lost

MQTT client library will need to be added to `build.gradle.kts` dependencies. Configuration (broker URL, port, credentials) should be stored via `PropertiesComponent` or a `PersistentStateComponent` service.

Logs from `runIde` appear in `.intellijPlatform/sandbox/*/log/idea.log`.