package com.github.madrigaleschat

import com.github.madrigaleschat.settings.PluginSettings
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
