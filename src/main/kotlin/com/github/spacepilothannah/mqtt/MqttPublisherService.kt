package com.github.spacepilothannah.mqtt

import com.github.spacepilothannah.settings.PluginSettings
import com.github.spacepilothannah.settings.getPassword
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
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

fun allLocalIpv4Addresses(): Sequence<Inet4Address> =
    NetworkInterface.getNetworkInterfaces()?.asSequence()
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?: emptySequence()

private fun ByteArray.toInt32() = fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

fun isOnHomeNetwork(
    subnet: String,
    localAddresses: Sequence<Inet4Address> = allLocalIpv4Addresses()
): Boolean {
    if (subnet.isBlank()) return true
    val slash = subnet.indexOf('/')
    if (slash == -1) return false
    val prefixLen = subnet.substring(slash + 1).toIntOrNull() ?: return false
    if (prefixLen !in 0..32) return false
    val networkBytes = runCatching {
        InetAddress.getByName(subnet.substring(0, slash))
    }.getOrNull()?.takeIf { it is Inet4Address }?.address ?: return false
    val mask = if (prefixLen == 0) 0 else (-1 shl (32 - prefixLen))
    val networkInt = networkBytes.toInt32() and mask
    return localAddresses.any { addr -> addr.address.toInt32() and mask == networkInt }
}

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
            ApplicationNamesInfo.getInstance().productName.lowercase().replace(" ", "-")
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
        val mqttPassword = getPassword()
        val opts = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
            if (settings.username.isNotBlank()) {
                userName = settings.username
                password = mqttPassword.toCharArray()
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
        if (!isOnHomeNetwork(settings.homeSubnet)) return

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
