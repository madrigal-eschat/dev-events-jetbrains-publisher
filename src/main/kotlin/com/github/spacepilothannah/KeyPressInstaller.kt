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
