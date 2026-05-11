package com.hataketsu.centauripilot.sdcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WireDir { OUT, IN, INFO, WARN, ERROR }

data class WireEntry(
    val ts: Long,
    val dir: WireDir,
    val cmd: Int?,
    val text: String
) {
    fun tsFormatted(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ts))
}

/** Ring-buffer log of WS traffic shared across the app for the Log tab. */
class WireLog(private val capacity: Int = 500) {
    private val _items = MutableStateFlow<List<WireEntry>>(emptyList())
    val items: StateFlow<List<WireEntry>> = _items.asStateFlow()

    fun send(cmd: Int, raw: String) = push(WireDir.OUT, cmd, raw)
    fun recv(raw: String) {
        // try extract cmd from response for nicer labelling
        val cmd = Regex("\"Cmd\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        push(WireDir.IN, cmd, raw)
    }
    fun info(msg: String) = push(WireDir.INFO, null, msg)
    fun warn(msg: String) = push(WireDir.WARN, null, msg)
    fun error(msg: String) = push(WireDir.ERROR, null, msg)
    fun clear() { _items.value = emptyList() }

    private fun push(dir: WireDir, cmd: Int?, text: String) {
        val entry = WireEntry(System.currentTimeMillis(), dir, cmd, text)
        val cur = _items.value
        _items.value = (cur + entry).takeLast(capacity)
    }
}
