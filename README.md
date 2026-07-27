# Dev Events Publisher (JetBrains plugin)

JetBrains IDE plugin listens IDE events, publish as CloudEvents 1.0 over MQTT.
Part IDEEvents ecosystem — envelope format defined in the shared message-format
spec used by all publishers/consumers in that ecosystem.

## What it does

Plugin hooks build/test/file/VCS/debugger/focus/keypress events, fire-and-forget
publish (QoS 0) to MQTT topic `{topicPrefix}/{hostname}`. Each event wrapped in
CloudEvents envelope, `source` field `editor/{host}/jetbrains/{ide-product}`.

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

Not yet on JetBrains Marketplace (distribution automation still hand-run — see
task list). Until then, build and install manually:

```bash
./gradlew buildPlugin
```

Produces a zip under `build/distributions/`. In IDE: **Settings > Plugins >
⚙️ > Install Plugin from Disk...**, pick the zip.

## Configuration

**Settings > Tools > IDE Events**.

- **Broker connection** — URL (`tcp://host:1883`), username, password (stored via
  IDE PasswordSafe, not in plain settings XML), client ID.
- **Topic prefix** — default `ide-events`; published topic is `{prefix}/{hostname}`
  when *include host* is on.
- **Home subnet (CIDR)** — if set, plugin only publishes when a local IPv4 matches
  the CIDR (e.g. don't leak events off your home LAN). Blank = always publish.
- **Per-event mode** — each event independently OFF / REDACTED / FULL. Some events
  have no sensitive fields (VCS commit, test-started, focus gained/lost) — for
  those REDACTED silently behaves as FULL.
- If every event is OFF, a startup notification warns you on project open.

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

Conventional Commits, enforced by commitlint. Release automated via
semantic-release on `main` (version bump in `gradle.properties`, changelog,
GitHub release) — no manual version bumps.

### Architecture

- **`MqttPublisherService`** — app-level service, connects on init in pooled
  thread. `publish()` fire-and-forget, checks: connected? on home network?
  event mode ≠ OFF? Call `reconfigure()` after settings change.
- **`PluginSettings`** — `PersistentStateComponent`, stored `DevEventsPublisher.xml`.
  Password kept separately via PasswordSafe.
- **`isOnHomeNetwork(subnet)`** — CIDR check against local IPv4s.
- **`buildEnvelope()`** — builds the CloudEvents map.
- **Listeners** (`src/main/kotlin/.../listeners/`) — one per extension point,
  each calls `MqttPublisherService` directly, no intermediate bus. New
  listeners must be registered in `src/main/resources/META-INF/plugin.xml`
  before they'll fire.
- **Settings UI** — `PluginSettingsConfigurable` → `PluginSettingsPanel`,
  per-event mode is a `JComboBox` in a `JBTable`.

## Publishing (maintainer)

Not yet automated (see task list). For now:

```bash
./gradlew publishPlugin
```

Needs `PUBLISH_TOKEN` env var (JetBrains Marketplace token). Or upload the
built zip manually via the [Plugin Repository upload page][jb:upload].

[jb:upload]: https://plugins.jetbrains.com/plugin/upload
