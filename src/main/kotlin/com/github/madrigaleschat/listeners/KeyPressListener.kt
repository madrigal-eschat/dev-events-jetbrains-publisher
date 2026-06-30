package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun buildKeyPressData(
    mode: EventMode,
    count: Int,
): Map<String, Any?> = mapOf("count" to if (mode == EventMode.FULL) count else 1)

fun isZeroTransition(
    lastCount: Int,
    currentCount: Int,
): Boolean = lastCount > 0 && currentCount == 0

@Service(Service.Level.APP)
class KeyPressListener : TypedActionHandler {
    private val counter = AtomicInteger(0)
    private var lastCount = 0
    private val scheduler =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "ide-events-keypresses").apply { isDaemon = true }
        }
    private var originalHandler: TypedActionHandler? = null

    init {
        scheduler.scheduleAtFixedRate(::tick, 1, 1, TimeUnit.SECONDS)
    }

    fun install(original: TypedActionHandler?) {
        originalHandler = original
    }

    override fun execute(
        editor: Editor,
        charTyped: Char,
        dataContext: DataContext,
    ) {
        counter.incrementAndGet()
        originalHandler?.execute(editor, charTyped, dataContext)
    }

    private fun tick() {
        val current = counter.getAndSet(0)
        val settings = PluginSettings.getInstance()
        val mode = settings.getEventMode("devevents.keypresses")

        if (mode != EventMode.OFF) {
            if (current > 0) {
                MqttPublisherService.getInstance().publish("devevents.keypresses", buildKeyPressData(mode, current))
            } else if (isZeroTransition(lastCount, current)) {
                MqttPublisherService.getInstance().publish("devevents.keypresses", mapOf("count" to 0))
            }
        }

        lastCount = current
    }

    companion object {
        fun getInstance(): KeyPressListener = service()
    }
}
