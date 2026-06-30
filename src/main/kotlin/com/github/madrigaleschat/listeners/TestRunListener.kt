package com.github.madrigaleschat.listeners

import com.github.madrigaleschat.model.EventMode
import com.github.madrigaleschat.mqtt.MqttPublisherService
import com.github.madrigaleschat.settings.PluginSettings
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy

fun buildTestData(mode: EventMode, durationMs: Long, passed: Int, failed: Int, skipped: Int): Map<String, Any?> =
    if (mode == EventMode.FULL) {
        mapOf("duration_ms" to durationMs, "passed" to passed, "failed" to failed, "skipped" to skipped)
    } else {
        val normPassed: Int = if (failed == 0) 1 else 0
        val normFailed: Int = if (failed > 0) 1 else 0
        val normSkipped: Int = 0
        mapOf(
            "duration_ms" to durationMs,
            "passed" to normPassed,
            "failed" to normFailed,
            "skipped" to normSkipped
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
        val allTests = rootTestProxy.allTests
        val passed = allTests.count { it.isPassed }
        val failed = allTests.count { it.isDefect }
        val skipped = allTests.count { it.isIgnored }
        val durationMs = rootTestProxy.duration ?: 0L
        val eventName = if (failed > 0) "test_fail" else "test_success"
        val mode = settings.getEventMode(eventName)
        if (mode == EventMode.OFF) return
        MqttPublisherService.getInstance().publish(
            eventName, buildTestData(mode, durationMs, passed, failed, skipped)
        )
    }

    override fun onTestsCountInSuite(count: Int) {}
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
    override fun onSuiteTreeStarted(testProxy: SMTestProxy) {}
}
