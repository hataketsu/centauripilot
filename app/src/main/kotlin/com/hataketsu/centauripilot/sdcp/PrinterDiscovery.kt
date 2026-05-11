package com.hataketsu.centauripilot.sdcp

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class DiscoveredPrinter(
    val name: String,
    val host: String,
    val port: Int,
    val mainboardId: String?,
    val brand: String? = null,
    val firmware: String? = null
)

/**
 * SDCP printer discovery via UDP broadcast probe — the actual protocol used by
 * the Elegoo/Chitubox web UI and Klipper-on-Centauri family.
 *
 * Method:
 *   send "M99999" UDP to 255.255.255.255:3000
 *   printers reply with JSON envelope containing MainboardIP / MainboardID / MachineName / BrandName / FirmwareVersion
 *
 * mDNS is NOT advertised by this firmware (verified empirically), so don't bother.
 */
object PrinterDiscovery {
    private const val PROBE_PORT = 3000
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun discover(ctx: Context): Flow<List<DiscoveredPrinter>> = callbackFlow {
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("centauripilot-disco").apply {
            setReferenceCounted(true); acquire()
        }
        val found = LinkedHashMap<String, DiscoveredPrinter>()
        val scope = CoroutineScope(Dispatchers.IO)

        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(java.net.InetSocketAddress(0))
                soTimeout = 1000
            }
        } catch (e: Exception) {
            trySend(emptyList())
            close(); return@callbackFlow
        }

        // Receiver loop
        val recvJob = scope.launch {
            val buf = ByteArray(8192)
            while (isClosedForSend.not()) {
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(pkt)
                    val body = String(pkt.data, 0, pkt.length).trim()
                    val printer = parseReply(body, pkt.address.hostAddress ?: "")
                    if (printer != null) {
                        found[printer.host] = printer
                        trySend(found.values.toList())
                    }
                } catch (_: java.net.SocketTimeoutException) { /* keep listening */ }
                  catch (_: Exception) { break }
            }
        }

        // Periodic probe loop
        val probeJob = scope.launch {
            val msg = "M99999".toByteArray()
            val broadcasts = collectBroadcastAddrs(ctx)
            repeat(10) {
                broadcasts.forEach { addr ->
                    try {
                        socket.send(DatagramPacket(msg, msg.size, addr, PROBE_PORT))
                    } catch (_: Exception) {}
                }
                kotlinx.coroutines.delay(1500)
            }
        }

        awaitClose {
            try { recvJob.cancel() } catch (_: Exception) {}
            try { probeJob.cancel() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
            try { lock.release() } catch (_: Exception) {}
        }
    }

    private fun parseReply(body: String, fromAddr: String): DiscoveredPrinter? {
        if (!body.startsWith("{")) return null
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val data = obj["Data"]?.jsonObject ?: return null
            val mid = data["MainboardID"]?.jsonPrimitive?.contentOrNull
            val ip = data["MainboardIP"]?.jsonPrimitive?.contentOrNull ?: fromAddr
            val name = data["MachineName"]?.jsonPrimitive?.contentOrNull
                ?: data["Name"]?.jsonPrimitive?.contentOrNull ?: "Printer"
            val brand = data["BrandName"]?.jsonPrimitive?.contentOrNull
            val fw = data["FirmwareVersion"]?.jsonPrimitive?.contentOrNull
            DiscoveredPrinter(
                name = name, host = ip, port = 3030, mainboardId = mid,
                brand = brand, firmware = fw
            )
        } catch (_: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun collectBroadcastAddrs(ctx: Context): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        // Always include the literal 255.255.255.255 (works on most APs)
        try { result.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}

        // Per-interface broadcast (more reliable on segmented networks)
        try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return result
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    addr.broadcast?.let { result.add(it) }
                }
            }
        } catch (_: Exception) {}
        return result.distinct()
    }
}
