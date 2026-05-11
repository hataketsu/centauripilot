package com.hataketsu.centauripilot.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SavedPrinter(
    val id: String,
    val nickname: String,
    val host: String,
    val mainboardId: String?
)

/** Tiny JSON-on-disk store for multi-printer support (kept simple — no Room). */
class PrinterStore(private val ctx: Context) {
    private val file get() = ctx.filesDir.resolve("printers.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _printers = MutableStateFlow<List<SavedPrinter>>(emptyList())
    val printers: StateFlow<List<SavedPrinter>> = _printers.asStateFlow()

    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active.asStateFlow()

    init { scope.launch { load() } }

    private fun load() {
        if (!file.exists()) return
        try {
            val txt = file.readText()
            val all = json.decodeFromString(SavedAll.serializer(), txt)
            _printers.value = all.printers
            _active.value = all.activeId ?: all.printers.firstOrNull()?.id
        } catch (_: Exception) {}
    }

    private fun persist() {
        scope.launch {
            try {
                file.writeText(json.encodeToString(SavedAll.serializer(),
                    SavedAll(_printers.value, _active.value)))
            } catch (_: Exception) {}
        }
    }

    fun add(printer: SavedPrinter) {
        _printers.value = (_printers.value.filter { it.id != printer.id }) + printer
        if (_active.value == null) _active.value = printer.id
        persist()
    }

    fun remove(id: String) {
        _printers.value = _printers.value.filter { it.id != id }
        if (_active.value == id) _active.value = _printers.value.firstOrNull()?.id
        persist()
    }

    fun setActive(id: String) {
        _active.value = id
        persist()
    }

    fun activePrinter(): SavedPrinter? = _printers.value.firstOrNull { it.id == _active.value }

    @Serializable
    private data class SavedAll(val printers: List<SavedPrinter>, val activeId: String?)
}
