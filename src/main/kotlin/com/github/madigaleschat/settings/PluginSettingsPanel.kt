package com.github.madigaleschat.settings

import com.github.madigaleschat.model.EventMode
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
    private val homeSubnetField = JBTextField().apply { emptyText.text = "e.g. 192.168.1.0/24 (empty = always publish)" }
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

    private fun setAllModes(mode: EventMode) {
        table.cellEditor?.stopCellEditing()
        eventNames.forEachIndexed { i, name ->
            val effective = if (mode == EventMode.REDACTED && name in PluginSettings.FULL_ONLY_EVENTS)
                EventMode.FULL else mode
            tableModel.setValueAt(effective, i, 1)
        }
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

        gc.gridwidth = 1; gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0.0
        gc.insets = Insets(2, 0, 2, 8)
        p.add(JBLabel("Home subnet:"), gc)
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0
        gc.insets = Insets(2, 0, 2, 0)
        p.add(homeSubnetField, gc)
        row++

        gc.gridy = row++
        p.add(JSeparator(), gc.apply { fill = GridBagConstraints.HORIZONTAL })

        gc.gridy = row++; gc.fill = GridBagConstraints.NONE
        p.add(warningLabel, gc)

        val buttonStrip = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("Set All Off").apply { addActionListener { setAllModes(EventMode.OFF) } })
            add(Box.createHorizontalStrut(4))
            add(JButton("Set All Redacted").apply { addActionListener { setAllModes(EventMode.REDACTED) } })
            add(Box.createHorizontalStrut(4))
            add(JButton("Set All Full").apply { addActionListener { setAllModes(EventMode.FULL) } })
            add(Box.createHorizontalGlue())
        }
        gc.gridy = row++; gc.fill = GridBagConstraints.HORIZONTAL
        p.add(buttonStrip, gc)

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
        settings.homeSubnet = homeSubnetField.text.trim()

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
        homeSubnetField.text = settings.homeSubnet
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
        homeSubnetField.text.trim() != settings.homeSubnet ||
        eventNames.indices.any { i ->
            tableModel.getValueAt(i, 1) as EventMode != settings.getEventMode(eventNames[i])
        }
}
