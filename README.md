# Dev Events Publisher (JetBrains plugin)

A JetBrains IDE plugin that listens for IDE events and publishes them as
CloudEvents 1.0 envelopes over MQTT. It's part of the Dev Events ecosystem —
the envelope format is defined in the shared message-format spec used by all
Dev Events publishers and consumers.

## What it does

The plugin listens for build, test, file, VCS, debugger, focus, and keypress
events, and publishes each one (fire-and-forget, QoS 0) to the MQTT topic
`{topicPrefix}/{hostname}`. Every event is wrapped in a CloudEvents envelope,
with `source` set to `editor/{host}/jetbrains/{ide-product}`.

Events tracked (toggle per-event in settings):

| Event | Trigger |
|---|---|
| `devevents.task.started/succeeded/failed` | Build/run task lifecycle |
| `devevents.test.started/succeeded/failed` | Test run lifecycle |
| `devevents.file.saved/opened/closed` | File editor activity |
| `devevents.breakpoint.hit` | Debugger stop |
| `devevents.vcs.committed` | VCS commit (via checkin handler) |
| `devevents.vcs.branch.changed` | Branch switch |
| `devevents.editor.focus.gained/lost` | IDE window (de)activation |
| `devevents.keypresses` | Raw keypress stream (via `IdeEventQueue`) |

## Install

The plugin isn't on JetBrains Marketplace yet — publishing there is still a
manual step (see the task list). Until it is, build and install it yourself:

```bash
./gradlew buildPlugin
```

This produces a zip under `build/distributions/`. In your IDE, go to
**Settings > Plugins > ⚙️ > Install Plugin from Disk...** and pick the zip.

## Configuration

All settings live under **Settings > Tools > IDE Events**.

- **Broker connection** — the MQTT broker URL (`tcp://host:1883`), username,
  password, and client ID. The password is stored via the IDE's PasswordSafe,
  not in the plain settings XML.
- **Topic prefix** — defaults to `ide-events`. The published topic is
  `{prefix}/{hostname}` when *include host* is enabled.
- **Home subnet (CIDR)** — if set, the plugin only publishes when one of your
  local IPv4 addresses matches this CIDR, so you can avoid leaking events
  outside your home network. Leave it blank to always publish.
- **Per-event mode** — each event can independently be set to OFF, REDACTED,
  or FULL. A few events have no sensitive fields to begin with (VCS commit,
  test started, focus gained/lost), so REDACTED behaves the same as FULL for
  those.
- If every event is turned off, a startup notification warns you when you
  open a project.

## Development

### Requirements

- IntelliJ IDEA 2025.3.5+ (plugin targets this platform version)
- JDK per Gradle toolchain (see `build.gradle.kts`)

### Local MQTT broker

A throwaway Mosquitto broker for manual testing:

```bash
docker compose up
```

Listens on `localhost:1883`, config at `mosquitto/mosquitto.conf`.

### Common tasks

```bash
./gradlew build          # compile + test
./gradlew test           # run tests only
./gradlew runIde         # launch sandbox IDE with plugin loaded (slow first run)
./gradlew verifyPlugin   # compatibility checks
```

Single test class:

```bash
./gradlew test --tests "com.github.madrigaleschat.SomeTest"
```

Predefined Run/Debug configs under `.run/` expose these same tasks in the IDE
(Run Plugin, Run Tests, Run Verifications). Sandbox logs land in
`.intellijPlatform/sandbox/*/log/idea.log`.

### Lint

Run before every commit:

```bash
find src -name "*.kt" -print0 | xargs -0 ktlint          # check
find src -name "*.kt" -print0 | xargs -0 ktlint --format  # auto-fix
```

ktlint won't auto-fix wildcard imports (`standard:no-wildcard-imports`) — expand
those by hand.

### Commits & releases

Commit messages follow Conventional Commits and are checked by commitlint.
Releases are handled by semantic-release running on `main`: it bumps the
version in `gradle.properties`, updates the changelog, and creates the GitHub
release. Don't bump the version by hand.

### Architecture

- **`MqttPublisherService`** is the application-level service that connects
  to the broker on startup, from a pooled thread. `publish()` is
  fire-and-forget: it checks whether the plugin is connected, whether you're
  on your home network, and whether the event's mode isn't OFF, before
  sending. Call `reconfigure()` after settings change.
- **`PluginSettings`** is a `PersistentStateComponent` stored in
  `DevEventsPublisher.xml`. The password is kept separately via PasswordSafe.
- **`isOnHomeNetwork(subnet)`** checks the CIDR against your local IPv4
  addresses.
- **`buildEnvelope()`** builds the CloudEvents envelope.
- **Listeners**, under `src/main/kotlin/.../listeners/`, exist one per
  extension point and each call `MqttPublisherService` directly — there's no
  intermediate event bus. New listeners must also be registered in
  `src/main/resources/META-INF/plugin.xml` before they'll fire.
- **The settings UI** is `PluginSettingsConfigurable` backed by
  `PluginSettingsPanel`; the per-event mode picker is a `JComboBox` inside a
  `JBTable`.

## Publishing (maintainer)

Publishing to the Marketplace isn't automated yet (see the task list). For
now, do it manually:

```bash
./gradlew publishPlugin
```

This needs a `PUBLISH_TOKEN` environment variable set to a JetBrains
Marketplace token. Alternatively, upload the built zip yourself via the
[Plugin Repository upload page][jb:upload].

[jb:upload]: https://plugins.jetbrains.com/plugin/upload
