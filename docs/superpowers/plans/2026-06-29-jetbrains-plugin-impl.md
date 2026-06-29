# JetBrains IDE Events Publisher — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a JetBrains IDE plugin that publishes IDE events as JSON over MQTT, with per-event Off/Redacted/Full mode control and a settings page.

**Architecture:** Application-level `MqttPublisherService` owns the Paho async MQTT client (QoS 0). ~15 thin listener classes each check their event mode, build a `Map<String, Any?>` payload (omitting sensitive fields in REDACTED mode), and call the publisher. Settings live in a `PersistentStateComponent`; password uses `PasswordSafe`.

**Tech Stack:** Kotlin, IntelliJ Platform SDK (IU-2025.3.5), IntelliJ Platform Gradle Plugin v2 (`org.jetbrains.intellij.platform`), Eclipse Paho MQTT client 1.2.5, bundled Gson (no extra dep), JUnit 4.

## Global Constraints

- Package root: `com.github.spacepilothannah`
- Source layout: `src/main/kotlin/com/github/spacepilothannah/<subpackage>/ClassName.kt`
- Test layout: `src/test/kotlin/com/github/spacepilothannah/<subpackage>/ClassNameTest.kt`
- Message format contract: `../../MESSAGE-FORMAT.md` — topic `{topicPrefix}/{hostname}` or `{topicPrefix}`; envelope fields `version`, `event`, `timestamp`, `source`, `data`
- MQTT: QoS 0, fire-and-forget, fail silently when broker unreachable
- Gson: never serialize null map values — filter nulls before serialisation
- Event mode enum: `OFF`, `REDACTED`, `FULL` — all events default to `OFF`
- `plugin.xml` plugin ID: `com.github.spacepilothannah.IDEEventsToWebhook`
- PasswordSafe credential key: `com.github.spacepilothannah.IDEEventsToWebhook`
- All events: `task_start`, `task_success`, `task_fail`, `test_start`, `test_success`, `test_fail`, `file_save`, `file_open`, `file_close`, `breakpoint_hit`, `vcs_commit`, `vcs_push`, `vcs_branch_change`, `editor_focus_gained`, `editor_focus_lost`, `inspection_complete`, `key_presses`

---

### Task 1: Scaffolding — delete placeholders, add Paho, create EventMode

**Files:**
- Delete: `src/main/kotlin/MyToolWindowFactory.kt`
- Delete: `src/main/kotlin/MyMessageBundle.kt`
- Delete: `src/main/resources/messages/MyMessageBundle.properties`
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/com/github/spacepilothannah/model/EventMode.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Produces: `EventMode` enum with values `OFF`, `REDACTED`, `FULL` in package `com.github.spacepilothannah.model`

- [ ] **Step 1: Delete placeholder source files**

```bash
rm src/main/kotlin/MyToolWindowFactory.kt
rm src/main/kotlin/MyMessageBundle.kt
rm src/main/resources/messages/MyMessageBundle.properties
```

- [ ] **Step 2: Add Paho dependency to `build.gradle.kts`**

Replace the `dependencies` block:

```kotlin
dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.properties")
        bundledPlugin("org.intellij.plugins.markdown")
    }

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}
```

- [ ] **Step 3: Create `EventMode.kt`**

```kotlin
package com.github.spacepilothannah.model

enum class EventMode { OFF, REDACTED, FULL }
```

- [ ] **Step 4: Strip placeholder registrations from `plugin.xml`**

Replace the entire `plugin.xml` content with a clean base (preserving the `<depends>` blocks):

```xml
<!-- Plugin Configuration File. Read more: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
<idea-plugin>
    <id>com.github.spacepilothannah.IDEEventsToWebhook</id>
    <name>IDE Events Publisher</name>
    <vendor>spacepilothannah</vendor>
    <description>Publishes IDE events to an MQTT broker.</description>

    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
    </extensions>
</idea-plugin>
```

- [ ] **Step 5: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts src/main/kotlin/com/github/spacepilothannah/model/EventMode.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat: add Paho dependency, EventMode enum, strip placeholder scaffold"
```

---

### Task 2: PluginSettings — persistent state + password helpers

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/settings/PluginSettings.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/settings/PluginSettingsTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Produces:
  - `PluginSettings.getInstance(): PluginSettings`
  - `PluginSettings.getEventMode(event: String): EventMode` — returns `OFF` if not set
  - `PluginSettings.setEventMode(event: String, mode: EventMode)`
  - `PluginSettings.allEventsOff(): Boolean`
  - `PluginSettings.ALL_EVENTS: List<String>`
  - `savePassword(password: String)`
  - `getPassword(): String`

- [ ] **Step 1: Write failing test**

Create `src/test/kotlin/com/github/spacepilothannah/settings/PluginSettingsTest.kt`:

```kotlin
package com.github.spacepilothannah.settings

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class PluginSettingsTest {

    private fun freshState() = PluginSettings.State()

    @Test
    fun `default brokerUrl is tcp localhost 1883`() {
        assertEquals("tcp://localhost:1883", freshState().brokerUrl)
    }

    @Test
    fun `default topicPrefix is ide-events`() {
        assertEquals("ide-events", freshState().topicPrefix)
    }

    @Test
    fun `default includeHost is true`() {
        assertTrue(freshState().includeHost)
    }

    @Test
    fun `default includeProject is true`() {
        assertTrue(freshState().includeProject)
    }

    @Test
    fun `getEventMode returns OFF for unknown event`() {
        val settings = PluginSettings()
        assertEquals(EventMode.OFF, settings.getEventMode("file_save"))
    }

    @Test
    fun `setEventMode and getEventMode round-trip`() {
        val settings = PluginSettings()
        settings.setEventMode("file_save", EventMode.FULL)
        assertEquals(EventMode.FULL, settings.getEventMode("file_save"))
    }

    @Test
    fun `allEventsOff returns true when no modes set`() {
        val settings = PluginSettings()
        assertTrue(settings.allEventsOff())
    }

    @Test
    fun `allEventsOff returns false when any event is not OFF`() {
        val settings = PluginSettings()
        settings.setEventMode("file_save", EventMode.REDACTED)
        assertFalse(settings.allEventsOff())
    }

    @Test
    fun `ALL_EVENTS contains all 17 events`() {
        assertEquals(17, PluginSettings.ALL_EVENTS.size)
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.settings.PluginSettingsTest"
```

Expected: compilation error (PluginSettings not found)

- [ ] **Step 3: Create `PluginSettings.kt`**

```kotlin
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
            "vcs_commit", "vcs_push", "vcs_branch_change",
            "editor_focus_gained", "editor_focus_lost",
            "inspection_complete", "key_presses"
        )
    }
}
```

- [ ] **Step 4: Register service in `plugin.xml`**

Inside the `<extensions defaultExtensionNs="com.intellij">` block, add:

```xml
<applicationService
    serviceImplementation="com.github.spacepilothannah.settings.PluginSettings"/>
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew test --tests "com.github.spacepilothannah.settings.PluginSettingsTest"
```

Expected: 9 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/settings/PluginSettings.kt \
        src/test/kotlin/com/github/spacepilothannah/settings/PluginSettingsTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(settings): add PluginSettings persistent state and password helpers"
```

---

### Task 3: MqttPublisherService — MQTT client + envelope builder

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/mqtt/MqttPublisherService.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/mqtt/EnvelopeTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Consumes: `PluginSettings.getInstance()`, `getPassword()`
- Produces:
  - `MqttPublisherService.getInstance(): MqttPublisherService`
  - `MqttPublisherService.publish(eventName: String, data: Map<String, Any?>, project: Project? = null)`
  - `MqttPublisherService.reconfigure()` — disconnect + reconnect with current settings
  - `buildEnvelope(eventName: String, data: Map<String, Any?>, source: Map<String, Any?>): Map<String, Any>` — pure function, testable

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/github/spacepilothannah/mqtt/EnvelopeTest.kt`:

```kotlin
package com.github.spacepilothannah.mqtt

import org.junit.Assert.*
import org.junit.Test

class EnvelopeTest {

    @Test
    fun `buildEnvelope sets version to 1`() {
        val env = buildEnvelope("file_save", mapOf("file_path" to "/a.kt"), mapOf("ide_family" to "jetbrains"))
        assertEquals(1, env["version"])
    }

    @Test
    fun `buildEnvelope sets event name`() {
        val env = buildEnvelope("file_save", emptyMap(), emptyMap())
        assertEquals("file_save", env["event"])
    }

    @Test
    fun `buildEnvelope includes data`() {
        val env = buildEnvelope("file_save", mapOf("file_path" to "/a.kt"), emptyMap())
        @Suppress("UNCHECKED_CAST")
        val data = env["data"] as Map<String, Any?>
        assertEquals("/a.kt", data["file_path"])
    }

    @Test
    fun `filterNulls removes null values from map`() {
        val result = mapOf("a" to "x", "b" to null, "c" to "y").filterNulls()
        assertEquals(mapOf("a" to "x", "c" to "y"), result)
    }

    @Test
    fun `filterNulls removes nulls from nested map`() {
        val result = mapOf("data" to mapOf("x" to null, "y" to 1)).filterNulls()
        @Suppress("UNCHECKED_CAST")
        val nested = (result["data"] as Map<String, Any?>)
        assertFalse(nested.containsKey("x"))
        assertEquals(1, nested["y"])
    }

    @Test
    fun `buildEnvelope timestamp is ISO 8601`() {
        val env = buildEnvelope("x", emptyMap(), emptyMap())
        val ts = env["timestamp"] as String
        assertTrue(ts.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*")))
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.mqtt.EnvelopeTest"
```

Expected: compilation error

- [ ] **Step 3: Create `MqttPublisherService.kt`**

```kotlin
package com.github.spacepilothannah.mqtt

import com.github.spacepilothannah.settings.PluginSettings
import com.github.spacepilothannah.settings.getPassword
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.InetAddress
import java.time.Instant

fun buildEnvelope(
    eventName: String,
    data: Map<String, Any?>,
    source: Map<String, Any?>
): Map<String, Any?> = mapOf(
    "version" to 1,
    "event" to eventName,
    "timestamp" to Instant.now().toString(),
    "source" to source,
    "data" to data
)

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.filterNulls(): Map<String, Any?> =
    filterValues { it != null }
        .mapValues { (_, v) ->
            if (v is Map<*, *>) (v as Map<String, Any?>).filterNulls() else v!!
        }

class MqttPublisherService {

    @Volatile private var client: MqttAsyncClient? = null

    private val hostname: String by lazy {
        runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("unknown")
    }

    private val ideIdentifier: String by lazy {
        runCatching {
            ApplicationInfo.getInstance().fullProductName.lowercase().replace(" ", "-")
        }.getOrDefault("intellij-idea")
    }

    private val gson = GsonBuilder().create()

    init {
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching { connect() }
        }
    }

    @Synchronized
    fun connect() {
        val settings = PluginSettings.getInstance()
        runCatching { client?.disconnect() }
        val newClient = MqttAsyncClient(settings.brokerUrl, settings.clientId, MemoryPersistence())
        val opts = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
            if (settings.username.isNotBlank()) {
                userName = settings.username
                password = getPassword().toCharArray()
            }
        }
        runCatching { newClient.connect(opts) }
        client = newClient
    }

    @Synchronized
    fun disconnect() {
        runCatching { client?.disconnect() }
        client = null
    }

    fun reconfigure() {
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching {
                disconnect()
                connect()
            }
        }
    }

    fun publish(eventName: String, data: Map<String, Any?>, project: Project? = null) {
        val c = client ?: return
        if (!c.isConnected) return

        val settings = PluginSettings.getInstance()

        val source = buildMap<String, Any?> {
            put("ide_family", "jetbrains")
            put("ide", ideIdentifier)
            if (settings.includeHost) put("host", hostname)
            if (settings.includeProject && project != null) put("project", project.name)
        }

        val envelope = buildEnvelope(eventName, data, source).filterNulls()
        val json = gson.toJson(envelope)

        val topic = if (settings.includeHost) "${settings.topicPrefix}/$hostname"
                    else settings.topicPrefix

        runCatching {
            c.publish(topic, MqttMessage(json.toByteArray()).apply { qos = 0 })
        }
    }

    companion object {
        fun getInstance(): MqttPublisherService = service()
    }
}
```

- [ ] **Step 4: Register service in `plugin.xml`**

Add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<applicationService
    serviceImplementation="com.github.spacepilothannah.mqtt.MqttPublisherService"/>
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests "com.github.spacepilothannah.mqtt.EnvelopeTest"
```

Expected: 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/mqtt/MqttPublisherService.kt \
        src/test/kotlin/com/github/spacepilothannah/mqtt/EnvelopeTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(mqtt): add MqttPublisherService with envelope builder"
```

---

### Task 4: Settings UI — panel, configurable, startup notification

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/settings/PluginSettingsPanel.kt`
- Create: `src/main/kotlin/com/github/spacepilothannah/settings/PluginSettingsConfigurable.kt`
- Create: `src/main/kotlin/com/github/spacepilothannah/StartupNotifier.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Consumes: `PluginSettings.getInstance()`, `savePassword()`, `getPassword()`, `MqttPublisherService.reconfigure()`
- Produces: Settings page at `Settings > Tools > IDE Events`

- [ ] **Step 1: Create `PluginSettingsPanel.kt`**

```kotlin
package com.github.spacepilothannah.settings

import com.github.spacepilothannah.model.EventMode
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.net.InetAddress
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

class PluginSettingsPanel {

    private val brokerUrlField = JBTextField()
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    private val topicPrefixField = JBTextField()
    private val resolvedTopicLabel = JBLabel()
    private val includeHostCheckbox = JCheckBox("Include hostname in topic and envelope")
    private val includeProjectCheckbox = JCheckBox("Include project name in envelope")
    private val warningLabel = JBLabel("All events are OFF — plugin will not publish anything").apply {
        foreground = Color(0xBB, 0x33, 0x33)
    }

    private val hostname: String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("unknown")

    private val eventNames = PluginSettings.ALL_EVENTS
    private val tableModel = object : DefaultTableModel(arrayOf("Event", "Mode"), eventNames.size) {
        override fun isCellEditable(row: Int, column: Int) = column == 1
        override fun getColumnClass(col: Int) = if (col == 1) EventMode::class.java else String::class.java
    }
    val table = JBTable(tableModel)

    val panel: JPanel

    init {
        eventNames.forEachIndexed { i, name ->
            tableModel.setValueAt(name, i, 0)
            tableModel.setValueAt(EventMode.OFF, i, 1)
        }

        val combo = JComboBox(EventMode.values())
        table.columnModel.getColumn(1).cellEditor = DefaultCellEditor(combo)
        table.rowHeight = combo.preferredSize.height

        val updateTopicLabel = {
            val prefix = topicPrefixField.text.trim()
            val suffix = if (includeHostCheckbox.isSelected) "/$hostname" else ""
            resolvedTopicLabel.text = "→ $prefix$suffix"
        }

        topicPrefixField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateTopicLabel()
            override fun removeUpdate(e: DocumentEvent) = updateTopicLabel()
            override fun changedUpdate(e: DocumentEvent) = updateTopicLabel()
        })
        includeHostCheckbox.addItemListener { updateTopicLabel() }
        tableModel.addTableModelListener { updateWarning() }

        panel = buildPanel()
        updateTopicLabel()
        updateWarning()
    }

    private fun updateWarning() {
        val allOff = eventNames.indices.all { tableModel.getValueAt(it, 1) == EventMode.OFF }
        warningLabel.isVisible = allOff
    }

    private fun buildPanel(): JPanel {
        val p = JPanel(GridBagLayout())
        var row = 0

        fun addRow(label: String, field: JComponent) {
            val gc = GridBagConstraints().apply {
                gridx = 0; gridy = row; anchor = GridBagConstraints.WEST
                insets = Insets(2, 0, 2, 8)
            }
            p.add(JBLabel(label), gc)
            gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0
            gc.insets = Insets(2, 0, 2, 0)
            p.add(field, gc)
            row++
        }

        addRow("Broker URL:", brokerUrlField)
        addRow("Username:", usernameField)
        addRow("Password:", passwordField)
        addRow("Topic prefix:", topicPrefixField)

        val gc = GridBagConstraints().apply {
            gridx = 1; gridy = row++; anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 6, 0)
        }
        p.add(resolvedTopicLabel, gc)

        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 2
        p.add(includeHostCheckbox, gc)
        gc.gridy = row++
        p.add(includeProjectCheckbox, gc)

        gc.gridy = row++
        p.add(JSeparator(), gc.apply { fill = GridBagConstraints.HORIZONTAL })

        gc.gridy = row++; gc.fill = GridBagConstraints.NONE
        p.add(warningLabel, gc)

        gc.gridy = row; gc.fill = GridBagConstraints.BOTH
        gc.weighty = 1.0
        val scrollPane = JScrollPane(table)
        scrollPane.preferredSize = java.awt.Dimension(400, 300)
        p.add(scrollPane, gc)

        return p
    }

    fun apply(settings: PluginSettings) {
        settings.brokerUrl = brokerUrlField.text.trim()
        settings.username = usernameField.text.trim()
        settings.topicPrefix = topicPrefixField.text.trim()
        settings.includeHost = includeHostCheckbox.isSelected
        settings.includeProject = includeProjectCheckbox.isSelected

        val password = String(passwordField.password)
        if (password.isNotEmpty()) savePassword(password)

        eventNames.forEachIndexed { i, name ->
            val mode = tableModel.getValueAt(i, 1) as EventMode
            settings.setEventMode(name, mode)
        }
    }

    fun reset(settings: PluginSettings) {
        brokerUrlField.text = settings.brokerUrl
        usernameField.text = settings.username
        topicPrefixField.text = settings.topicPrefix
        includeHostCheckbox.isSelected = settings.includeHost
        includeProjectCheckbox.isSelected = settings.includeProject
        eventNames.forEachIndexed { i, name ->
            tableModel.setValueAt(settings.getEventMode(name), i, 1)
        }
        updateWarning()
    }

    fun isModified(settings: PluginSettings): Boolean =
        brokerUrlField.text.trim() != settings.brokerUrl ||
        usernameField.text.trim() != settings.username ||
        topicPrefixField.text.trim() != settings.topicPrefix ||
        includeHostCheckbox.isSelected != settings.includeHost ||
        includeProjectCheckbox.isSelected != settings.includeProject ||
        eventNames.indices.any { i ->
            tableModel.getValueAt(i, 1) as EventMode != settings.getEventMode(eventNames[i])
        }
}
```

- [ ] **Step 2: Create `PluginSettingsConfigurable.kt`**

```kotlin
package com.github.spacepilothannah.settings

import com.github.spacepilothannah.mqtt.MqttPublisherService
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
```

- [ ] **Step 3: Create `StartupNotifier.kt`**

```kotlin
package com.github.spacepilothannah

import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class StartupNotifier : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (PluginSettings.getInstance().allEventsOff()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("IDE Events Publisher")
                .createNotification(
                    "IDE Events publisher is not configured",
                    "Open Settings > Tools > IDE Events to enable event publishing.",
                    NotificationType.WARNING
                )
                .notify(project)
        }
    }
}
```

- [ ] **Step 4: Register all three in `plugin.xml`**

Add inside `<extensions defaultExtensionNs="com.intellij">`:

```xml
<applicationConfigurable
    instance="com.github.spacepilothannah.settings.PluginSettingsConfigurable"
    displayName="IDE Events"
    parentId="tools"/>

<notificationGroup id="IDE Events Publisher"
                   displayType="BALLOON"
                   isLogByDefault="true"/>

<postStartupActivity
    implementation="com.github.spacepilothannah.StartupNotifier"/>
```

- [ ] **Step 5: Compile**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Smoke-test settings UI via runIde**

```bash
./gradlew runIde
```

Open any project → Settings > Tools > IDE Events → verify:
- Broker URL field shows `tcp://localhost:1883`
- All event rows show `OFF`
- Warning label is visible
- Resolved topic label updates as prefix field changes
- Include host checkbox changes resolved topic label

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/settings/PluginSettingsPanel.kt \
        src/main/kotlin/com/github/spacepilothannah/settings/PluginSettingsConfigurable.kt \
        src/main/kotlin/com/github/spacepilothannah/StartupNotifier.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(settings): add settings UI, configurable, and startup notification"
```

---

### Task 5: File listeners — save, open, close

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/FileSaveListener.kt`
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/FileEditorListener.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/listeners/FileListenerTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Consumes: `PluginSettings.getInstance()`, `MqttPublisherService.getInstance().publish(...)`
- Produces: Publishes `file_save`, `file_open`, `file_close` events

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/github/spacepilothannah/listeners/FileListenerTest.kt`:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class FileListenerTest {

    @Test
    fun `buildFileData FULL includes file path`() {
        val data = buildFileData(EventMode.FULL, "/src/main.kt")
        assertEquals("/src/main.kt", data["file_path"])
    }

    @Test
    fun `buildFileData REDACTED omits file path`() {
        val data = buildFileData(EventMode.REDACTED, "/src/main.kt")
        assertNull(data["file_path"])
    }

    @Test
    fun `buildFileData FULL with null path produces null entry`() {
        val data = buildFileData(EventMode.FULL, null)
        assertNull(data["file_path"])
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.FileListenerTest"
```

Expected: compilation error

- [ ] **Step 3: Create `FileSaveListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.roots.ProjectFileIndex

fun buildFileData(mode: EventMode, filePath: String?): Map<String, Any?> =
    mapOf("file_path" to if (mode == EventMode.FULL) filePath else null)

class FileSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("file_save")
        if (mode == EventMode.OFF) return

        val file = FileDocumentManager.getInstance().getFile(document)
        val project = file?.let {
            com.intellij.openapi.project.ProjectLocator.getInstance().guessProjectForFile(it)
        }
        MqttPublisherService.getInstance().publish(
            "file_save",
            buildFileData(mode, file?.path),
            project
        )
    }
}
```

- [ ] **Step 4: Create `FileEditorListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class FileEditorListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("file_open")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            "file_open", buildFileData(mode, file.path), source.project
        )
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("file_close")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            "file_close", buildFileData(mode, file.path), source.project
        )
    }
}
```

- [ ] **Step 5: Register listeners in `plugin.xml`**

Add a `<listeners>` block after `<extensions>`:

```xml
<listeners>
    <listener class="com.github.spacepilothannah.listeners.FileSaveListener"
              topic="com.intellij.openapi.fileEditor.FileDocumentManagerListener"/>
    <listener class="com.github.spacepilothannah.listeners.FileEditorListener"
              topic="com.intellij.openapi.fileEditor.FileEditorManagerListener"/>
</listeners>
```

- [ ] **Step 6: Run tests**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.FileListenerTest"
```

Expected: 3 tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/listeners/FileSaveListener.kt \
        src/main/kotlin/com/github/spacepilothannah/listeners/FileEditorListener.kt \
        src/test/kotlin/com/github/spacepilothannah/listeners/FileListenerTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(listeners): add file save, open, close event listeners"
```

---

### Task 6: Build and test listeners

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/BuildTaskListener.kt`
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/TestRunListener.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/listeners/BuildListenerTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Consumes: `PluginSettings.getInstance()`, `MqttPublisherService.getInstance().publish(...)`
- Produces: Publishes `task_start`, `task_success`, `task_fail`, `test_start`, `test_success`, `test_fail`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/github/spacepilothannah/listeners/BuildListenerTest.kt`:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class BuildListenerTest {

    @Test
    fun `buildTaskData FULL includes name and duration`() {
        val data = buildTaskData(EventMode.FULL, "Build Project", 1234L)
        assertEquals("Build Project", data["name"])
        assertEquals(1234L, data["duration_ms"])
    }

    @Test
    fun `buildTaskData REDACTED omits name, keeps duration`() {
        val data = buildTaskData(EventMode.REDACTED, "Build Project", 1234L)
        assertNull(data["name"])
        assertEquals(1234L, data["duration_ms"])
    }

    @Test
    fun `buildTaskStartData FULL includes name`() {
        val data = buildTaskStartData(EventMode.FULL, "Build Project")
        assertEquals("Build Project", data["name"])
    }

    @Test
    fun `buildTaskStartData REDACTED omits name`() {
        val data = buildTaskStartData(EventMode.REDACTED, "Build Project")
        assertNull(data["name"])
    }

    @Test
    fun `buildTestData FULL returns real counts`() {
        val data = buildTestData(EventMode.FULL, durationMs = 500L, passed = 10, failed = 2, skipped = 1)
        assertEquals(10, data["passed"])
        assertEquals(2, data["failed"])
        assertEquals(1, data["skipped"])
        assertEquals(500L, data["duration_ms"])
    }

    @Test
    fun `buildTestData REDACTED normalises success (failed=0)`() {
        val data = buildTestData(EventMode.REDACTED, durationMs = 500L, passed = 10, failed = 0, skipped = 1)
        assertEquals(1, data["passed"])
        assertEquals(0, data["failed"])
        assertEquals(0, data["skipped"])
    }

    @Test
    fun `buildTestData REDACTED normalises failure (failed greater than 0)`() {
        val data = buildTestData(EventMode.REDACTED, durationMs = 500L, passed = 8, failed = 2, skipped = 0)
        assertEquals(0, data["passed"])
        assertEquals(1, data["failed"])
        assertEquals(0, data["skipped"])
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.BuildListenerTest"
```

Expected: compilation error

- [ ] **Step 3: Create `BuildTaskListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import java.util.concurrent.ConcurrentHashMap

fun buildTaskStartData(mode: EventMode, name: String?): Map<String, Any?> =
    mapOf("name" to if (mode == EventMode.FULL) name else null)

fun buildTaskData(mode: EventMode, name: String?, durationMs: Long?): Map<String, Any?> =
    mapOf(
        "duration_ms" to durationMs,
        "name" to if (mode == EventMode.FULL) name else null
    )

class BuildTaskListener : ProjectTaskListener {

    private val startTimes = ConcurrentHashMap<Int, Long>()

    override fun started(context: ProjectTaskContext) {
        startTimes[System.identityHashCode(context)] = System.currentTimeMillis()

        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("task_start")
        if (mode == EventMode.OFF) return

        val name = context.runConfiguration?.name
        MqttPublisherService.getInstance().publish(
            "task_start", buildTaskStartData(mode, name), context.project
        )
    }

    override fun finished(context: ProjectTaskContext, result: ProjectTaskManager.Result) {
        val startTime = startTimes.remove(System.identityHashCode(context))
        val durationMs = startTime?.let { System.currentTimeMillis() - it }

        val settings = PluginSettings.getInstance()
        val eventName = if (result.hasErrors()) "task_fail" else "task_success"
        val mode = settings.getEventMode(eventName)
        if (mode == EventMode.OFF) return

        val name = context.runConfiguration?.name
        MqttPublisherService.getInstance().publish(
            eventName, buildTaskData(mode, name, durationMs), context.project
        )
    }
}
```

- [ ] **Step 4: Create `TestRunListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy

fun buildTestData(mode: EventMode, durationMs: Long, passed: Int, failed: Int, skipped: Int): Map<String, Any?> =
    if (mode == EventMode.FULL) {
        mapOf("duration_ms" to durationMs, "passed" to passed, "failed" to failed, "skipped" to skipped)
    } else {
        mapOf(
            "duration_ms" to durationMs,
            "passed" to if (failed == 0) 1 else 0,
            "failed" to if (failed > 0) 1 else 0,
            "skipped" to 0
        )
    }

class TestRunListener : SMTRunnerEventsListener {

    override fun onTestingStarted(rootTestProxy: SMTestProxy.SMRootTestProxy) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("test_start")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("test_start", emptyMap())
    }

    override fun onTestingFinished(rootTestProxy: SMTestProxy.SMRootTestProxy) {
        val settings = PluginSettings.getInstance()
        val passed = rootTestProxy.passedCount
        val failed = rootTestProxy.failedCount
        val skipped = rootTestProxy.ignoredCount
        val durationMs = rootTestProxy.duration ?: 0L
        val eventName = if (failed > 0) "test_fail" else "test_success"
        val mode = settings.getEventMode(eventName)
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            eventName, buildTestData(mode, durationMs, passed, failed, skipped)
        )
    }

    // Required interface stubs
    override fun onTestStarted(test: SMTestProxy) {}
    override fun onTestFailed(test: SMTestProxy) {}
    override fun onTestIgnored(test: SMTestProxy) {}
    override fun onTestFinished(test: SMTestProxy) {}
    override fun onSuiteStarted(suite: SMTestProxy) {}
    override fun onSuiteFinished(suite: SMTestProxy) {}
    override fun onCustomProgressTestsCategory(categoryName: String?, count: Int) {}
    override fun onCustomProgressTestStarted() {}
    override fun onCustomProgressTestFailed() {}
    override fun onCustomProgressTestFinished() {}
    override fun onSuiteTreeNodeAdded(testProxy: SMTestProxy) {}
    override fun onSuiteTreeStartCreation() {}
}
```

- [ ] **Step 5: Register listeners in `plugin.xml`**

Inside the existing `<listeners>` block, add:

```xml
<listener class="com.github.spacepilothannah.listeners.BuildTaskListener"
          topic="com.intellij.task.ProjectTaskListener"/>
<listener class="com.github.spacepilothannah.listeners.TestRunListener"
          topic="com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener"/>
```

- [ ] **Step 6: Run tests**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.BuildListenerTest"
```

Expected: 7 tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/listeners/BuildTaskListener.kt \
        src/main/kotlin/com/github/spacepilothannah/listeners/TestRunListener.kt \
        src/test/kotlin/com/github/spacepilothannah/listeners/BuildListenerTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(listeners): add build task and test run event listeners"
```

---

### Task 7: VCS listeners — commit, push, branch change

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/VcsCommitListener.kt`
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/VcsPushListener.kt`
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/VcsBranchListener.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/listeners/VcsListenerTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Produces: Publishes `vcs_commit`, `vcs_push`, `vcs_branch_change`

> **Note:** The exact topic class names for commit and push must be verified against the IntelliJ Platform Explorer at https://jb.gg/ipe before registering in `plugin.xml`. The implementations below use the most likely APIs; adjust topic strings if needed.

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/github/spacepilothannah/listeners/VcsListenerTest.kt`:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class VcsListenerTest {

    @Test
    fun `buildBranchData FULL includes branch name`() {
        val data = buildBranchData(EventMode.FULL, "main")
        assertEquals("main", data["branch"])
    }

    @Test
    fun `buildBranchData REDACTED omits branch name`() {
        val data = buildBranchData(EventMode.REDACTED, "main")
        assertNull(data["branch"])
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.VcsListenerTest"
```

Expected: compilation error

- [ ] **Step 3: Create `VcsCommitListener.kt`**

VCS commit detection uses `CheckinHandlerFactory` declared as an extension. However, for a message-bus listener approach, use `com.intellij.openapi.vcs.impl.VcsInitObject` or `com.intellij.vcs.commit.CommitSessionCollector`. The most reliable approach is a `CheckinHandlerFactory`:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory

class VcsCommitListener : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        return object : CheckinHandler() {
            override fun checkinSuccessful() {
                val settings = PluginSettings.getInstance()
                val mode = settings.getEventMode("vcs_commit")
                if (mode == EventMode.OFF) return
                MqttPublisherService.getInstance().publish("vcs_commit", emptyMap(), panel.project)
            }
        }
    }
}
```

Register as an extension (not a listener) — update Step 5 accordingly.

- [ ] **Step 4: Create `VcsBranchListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
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
```

- [ ] **Step 5: Create `VcsPushListener.kt`**

> **Verify:** Before registering, confirm the push listener topic at https://jb.gg/ipe — search for "push" under `git4idea`. The likely interface is `git4idea.push.GitPushResultNotification` or a `VcsPushListener` topic. If `git4idea` APIs are needed, add `bundledPlugin("git4idea")` to `build.gradle.kts` dependencies.

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.vcs.VcsException

// Placeholder — replace interface with verified push listener interface from jb.gg/ipe
class VcsPushListener {
    fun onPushSuccessful() {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("vcs_push")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("vcs_push", emptyMap())
    }
}
```

- [ ] **Step 6: Register in `plugin.xml`**

`VcsCommitListener` registers as an extension (not a listener):

```xml
<vcsCheckinHandlerFactory
    implementation="com.github.spacepilothannah.listeners.VcsCommitListener"/>
```

`VcsBranchListener` registers as a listener:

```xml
<listener class="com.github.spacepilothannah.listeners.VcsBranchListener"
          topic="com.intellij.openapi.vcs.BranchChangeListener.VCS_BRANCH_CHANGED"/>
```

`VcsPushListener` registration: **defer until push interface is confirmed via jb.gg/ipe.**

- [ ] **Step 7: Run tests**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.VcsListenerTest"
```

Expected: 2 tests PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/listeners/VcsCommitListener.kt \
        src/main/kotlin/com/github/spacepilothannah/listeners/VcsPushListener.kt \
        src/main/kotlin/com/github/spacepilothannah/listeners/VcsBranchListener.kt \
        src/test/kotlin/com/github/spacepilothannah/listeners/VcsListenerTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(listeners): add VCS commit, push, and branch change listeners"
```

---

### Task 8: Debug and focus listeners

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/BreakpointListener.kt`
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/AppFocusListener.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/listeners/BreakpointListenerTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Produces: Publishes `breakpoint_hit`, `editor_focus_gained`, `editor_focus_lost`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/github/spacepilothannah/listeners/BreakpointListenerTest.kt`:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class BreakpointListenerTest {

    @Test
    fun `buildBreakpointData FULL includes file path and line`() {
        val data = buildBreakpointData(EventMode.FULL, "/src/Main.kt", 42)
        assertEquals("/src/Main.kt", data["file_path"])
        assertEquals(42, data["line"])
    }

    @Test
    fun `buildBreakpointData REDACTED omits file path and line`() {
        val data = buildBreakpointData(EventMode.REDACTED, "/src/Main.kt", 42)
        assertNull(data["file_path"])
        assertNull(data["line"])
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.BreakpointListenerTest"
```

Expected: compilation error

- [ ] **Step 3: Create `BreakpointListener.kt`**

`XDebugSessionListener` must be registered per-session. Use `XDebuggerManagerListener` (application-level) to hook each new session, then register the session listener:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener

fun buildBreakpointData(mode: EventMode, filePath: String?, line: Int?): Map<String, Any?> =
    mapOf(
        "file_path" to if (mode == EventMode.FULL) filePath else null,
        "line" to if (mode == EventMode.FULL) line else null
    )

class BreakpointListener : XDebuggerManagerListener {
    override fun processStarted(debugProcess: XDebugProcess) {
        debugProcess.session.addSessionListener(object : XDebugSessionListener {
            override fun sessionPaused() {
                val settings = PluginSettings.getInstance()
                val mode = settings.getEventMode("breakpoint_hit")
                if (mode == EventMode.OFF) return

                val position = debugProcess.session.currentPosition
                val filePath = position?.file?.path
                val line = position?.line?.let { it + 1 } // convert 0-based to 1-based

                MqttPublisherService.getInstance().publish(
                    "breakpoint_hit",
                    buildBreakpointData(mode, filePath, line)
                )
            }
        })
    }
}
```

- [ ] **Step 4: Create `AppFocusListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame

class AppFocusListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("editor_focus_gained")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("editor_focus_gained", emptyMap())
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("editor_focus_lost")
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish("editor_focus_lost", emptyMap())
    }
}
```

- [ ] **Step 5: Register in `plugin.xml`**

Inside the existing `<listeners>` block:

```xml
<listener class="com.github.spacepilothannah.listeners.BreakpointListener"
          topic="com.intellij.xdebugger.XDebuggerManagerListener.TOPIC"/>
<listener class="com.github.spacepilothannah.listeners.AppFocusListener"
          topic="com.intellij.openapi.application.ApplicationActivationListener"/>
```

Note: verify `XDebuggerManagerListener.TOPIC` is the correct topic string — check at https://jb.gg/ipe if registration fails.

- [ ] **Step 6: Run tests**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.BreakpointListenerTest"
```

Expected: 2 tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/listeners/BreakpointListener.kt \
        src/main/kotlin/com/github/spacepilothannah/listeners/AppFocusListener.kt \
        src/test/kotlin/com/github/spacepilothannah/listeners/BreakpointListenerTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(listeners): add breakpoint hit and application focus listeners"
```

---

### Task 9: Inspection listener

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/InspectionListener.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/listeners/InspectionListenerTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Produces: Publishes `inspection_complete` when highlighting/inspection pass finishes

> **Note:** Verify the inspection listener topic at https://jb.gg/ipe — search "daemon". The expected topic is `com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC` with listener interface `DaemonCodeAnalyzer.DaemonListener`.

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/github/spacepilothannah/listeners/InspectionListenerTest.kt`:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class InspectionListenerTest {

    @Test
    fun `buildInspectionData FULL includes real counts`() {
        val data = buildInspectionData(EventMode.FULL, errorCount = 3, warningCount = 14)
        assertEquals(3, data["error_count"])
        assertEquals(14, data["warning_count"])
    }

    @Test
    fun `buildInspectionData REDACTED normalises to 1 when counts greater than 0`() {
        val data = buildInspectionData(EventMode.REDACTED, errorCount = 3, warningCount = 14)
        assertEquals(1, data["error_count"])
        assertEquals(1, data["warning_count"])
    }

    @Test
    fun `buildInspectionData REDACTED normalises to 0 when counts are 0`() {
        val data = buildInspectionData(EventMode.REDACTED, errorCount = 0, warningCount = 0)
        assertEquals(0, data["error_count"])
        assertEquals(0, data["warning_count"])
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.InspectionListenerTest"
```

Expected: compilation error

- [ ] **Step 3: Create `InspectionListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditorManager

fun buildInspectionData(mode: EventMode, errorCount: Int, warningCount: Int): Map<String, Any?> =
    if (mode == EventMode.FULL) {
        mapOf("error_count" to errorCount, "warning_count" to warningCount)
    } else {
        mapOf(
            "error_count" to if (errorCount > 0) 1 else 0,
            "warning_count" to if (warningCount > 0) 1 else 0
        )
    }

class InspectionListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
    override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("inspection_complete")
        if (mode == EventMode.OFF) return

        // Count errors and warnings across all finished editors using DaemonCodeAnalyzer
        var errorCount = 0
        var warningCount = 0
        val analyzer = DaemonCodeAnalyzer.getInstance(project)
        fileEditors.forEach { editor ->
            val file = editor.file ?: return@forEach
            // Use TrafficLightRenderer or HighlightingSessionImpl counters if available.
            // Fallback: use com.intellij.codeInsight.daemon.impl.HighlightInfoType counts
            // For a simpler approach, count via com.intellij.openapi.util.Disposer or
            // DaemonCodeAnalyzerImpl.getFileLevelHighlights — verify at runtime.
            // Minimal working approach: iterate highlights via Document markup
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                .getDocument(file) ?: return@forEach
            val highlights = com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
                .getHighlights(document, HighlightSeverity.WARNING, project)
            highlights.forEach { info ->
                when {
                    info.severity >= HighlightSeverity.ERROR -> errorCount++
                    info.severity >= HighlightSeverity.WARNING -> warningCount++
                }
            }
        }

        MqttPublisherService.getInstance().publish(
            "inspection_complete",
            buildInspectionData(mode, errorCount, warningCount),
            project
        )
    }
}
```

> **Implementation note:** `DaemonCodeAnalyzerImpl.getHighlights(document, minSeverity, project)` is an internal API. If it's inaccessible, use `HighlightLevelUtil` or get highlights via the Document's markup model. Verify at runtime with `./gradlew runIde`.

- [ ] **Step 4: Register in `plugin.xml`**

Inside the existing `<listeners>` block:

```xml
<listener class="com.github.spacepilothannah.listeners.InspectionListener"
          topic="com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC"/>
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.InspectionListenerTest"
```

Expected: 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/listeners/InspectionListener.kt \
        src/test/kotlin/com/github/spacepilothannah/listeners/InspectionListenerTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(listeners): add inspection complete listener"
```

---

### Task 10: Key presses listener

**Files:**
- Create: `src/main/kotlin/com/github/spacepilothannah/listeners/KeyPressListener.kt`
- Create: `src/test/kotlin/com/github/spacepilothannah/listeners/KeyPressListenerTest.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Interfaces:**
- Produces: Publishes `key_presses` once per second when count > 0, once more when count transitions to zero

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/github/spacepilothannah/listeners/KeyPressListenerTest.kt`:

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import org.junit.Assert.*
import org.junit.Test

class KeyPressListenerTest {

    @Test
    fun `buildKeyPressData FULL returns real count`() {
        val data = buildKeyPressData(EventMode.FULL, 37)
        assertEquals(37, data["count"])
    }

    @Test
    fun `buildKeyPressData REDACTED always returns count 1`() {
        val data = buildKeyPressData(EventMode.REDACTED, 37)
        assertEquals(1, data["count"])
    }

    @Test
    fun `zero transition detected when lastCount positive and current zero`() {
        assertTrue(isZeroTransition(lastCount = 5, currentCount = 0))
    }

    @Test
    fun `zero transition not detected when both zero`() {
        assertFalse(isZeroTransition(lastCount = 0, currentCount = 0))
    }

    @Test
    fun `zero transition not detected when current nonzero`() {
        assertFalse(isZeroTransition(lastCount = 5, currentCount = 3))
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.KeyPressListenerTest"
```

Expected: compilation error

- [ ] **Step 3: Create `KeyPressListener.kt`**

```kotlin
package com.github.spacepilothannah.listeners

import com.github.spacepilothannah.model.EventMode
import com.github.spacepilothannah.mqtt.MqttPublisherService
import com.github.spacepilothannah.settings.PluginSettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun buildKeyPressData(mode: EventMode, count: Int): Map<String, Any?> =
    mapOf("count" to if (mode == EventMode.FULL) count else 1)

fun isZeroTransition(lastCount: Int, currentCount: Int): Boolean =
    lastCount > 0 && currentCount == 0

@Service(Service.Level.APP)
class KeyPressListener : TypedActionHandler {

    private val counter = AtomicInteger(0)
    private var lastCount = 0
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ide-events-keypresses").apply { isDaemon = true }
    }
    private var originalHandler: TypedActionHandler? = null

    init {
        scheduler.scheduleAtFixedRate(::tick, 1, 1, TimeUnit.SECONDS)
    }

    fun install(original: TypedActionHandler?) {
        originalHandler = original
    }

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        counter.incrementAndGet()
        originalHandler?.execute(editor, charTyped, dataContext)
    }

    private fun tick() {
        val current = counter.getAndSet(0)
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("key_presses")

        if (mode != EventMode.OFF) {
            if (current > 0) {
                MqttPublisherService.getInstance().publish("key_presses", buildKeyPressData(mode, current))
            } else if (isZeroTransition(lastCount, current)) {
                MqttPublisherService.getInstance().publish("key_presses", mapOf("count" to 0))
            }
        }

        lastCount = current
    }

    companion object {
        fun getInstance(): KeyPressListener = service()
    }
}
```

- [ ] **Step 4: Register service and install handler in `plugin.xml`**

Register as an application service:

```xml
<applicationService
    serviceImplementation="com.github.spacepilothannah.listeners.KeyPressListener"/>
```

Add a `postStartupActivity` to install the `TypedActionHandler` (must run after platform init):

Create `src/main/kotlin/com/github/spacepilothannah/KeyPressInstaller.kt`:

```kotlin
package com.github.spacepilothannah

import com.github.spacepilothannah.listeners.KeyPressListener
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class KeyPressInstaller : ProjectActivity {
    override suspend fun execute(project: Project) {
        val typedAction = EditorActionManager.getInstance().typedAction
        val listener = KeyPressListener.getInstance()
        if (typedAction.handler === listener) return  // already installed (multiple projects)
        listener.install(typedAction.handler)
        typedAction.setupHandler(listener)
    }
}
```

Register in `plugin.xml`:

```xml
<postStartupActivity
    implementation="com.github.spacepilothannah.KeyPressInstaller"/>
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests "com.github.spacepilothannah.listeners.KeyPressListenerTest"
```

Expected: 5 tests PASS

- [ ] **Step 6: Run all tests**

```bash
./gradlew test
```

Expected: all tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/spacepilothannah/listeners/KeyPressListener.kt \
        src/main/kotlin/com/github/spacepilothannah/KeyPressInstaller.kt \
        src/test/kotlin/com/github/spacepilothannah/listeners/KeyPressListenerTest.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat(listeners): add key presses listener with zero-transition detection"
```

---

### Task 11: Integration smoke test via runIde

**Files:** none created

- [ ] **Step 1: Start a local MQTT broker**

```bash
# Using mosquitto (brew install mosquitto if needed)
mosquitto -v
```

Or use Docker:

```bash
docker run -p 1883:1883 eclipse-mosquitto
```

- [ ] **Step 2: Subscribe to all events**

```bash
mosquitto_sub -t "ide-events/#" -v
```

- [ ] **Step 3: Launch sandbox IDE**

```bash
./gradlew runIde
```

- [ ] **Step 4: Configure the plugin**

Settings > Tools > IDE Events:
- Broker URL: `tcp://localhost:1883`
- Set all events to `FULL`
- Click OK

Expected: warning label disappears, no startup notification on next project open.

- [ ] **Step 5: Verify events fire**

Perform these actions in the sandbox IDE and confirm MQTT messages arrive:

| Action | Expected event |
|---|---|
| Save a file (Cmd+S) | `file_save` |
| Open a file | `file_open` |
| Close a file tab | `file_close` |
| Run build (Cmd+F9) | `task_start`, then `task_success` or `task_fail` |
| Run tests | `test_start`, then `test_success` or `test_fail` |
| Type in editor | `key_presses` (after ~1s) |
| Switch git branch | `vcs_branch_change` |
| Commit | `vcs_commit` |
| Click away from IDE | `editor_focus_lost` |
| Click back into IDE | `editor_focus_gained` |

- [ ] **Step 6: Verify REDACTED mode strips fields**

Set `file_save` to REDACTED. Save a file. Confirm `file_path` is absent from the MQTT payload.

- [ ] **Step 7: Final commit**

```bash
git add -A
git commit -m "chore: update CLAUDE.md with final build commands and architecture notes"
```