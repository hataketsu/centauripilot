package com.hataketsu.centauripilot

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hataketsu.centauripilot.data.PrinterStore
import com.hataketsu.centauripilot.data.SavedPrinter
import com.hataketsu.centauripilot.sdcp.ConnState
import com.hataketsu.centauripilot.sdcp.DiscoveredPrinter
import com.hataketsu.centauripilot.sdcp.PrinterDiscovery
import com.hataketsu.centauripilot.sdcp.SdcpClient
import com.hataketsu.centauripilot.sdcp.StatusBlock
import com.hataketsu.centauripilot.sdcp.WireLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = PrinterStore(app)
    val printers: StateFlow<List<SavedPrinter>> = store.printers
    val activeId: StateFlow<String?> = store.active

    val wireLog = WireLog(capacity = 800)

    private val _discovered = MutableStateFlow<List<DiscoveredPrinter>>(emptyList())
    val discovered: StateFlow<List<DiscoveredPrinter>> = _discovered.asStateFlow()

    private val _client = MutableStateFlow<SdcpClient?>(null)
    val client: StateFlow<SdcpClient?> = _client.asStateFlow()

    private val _conn = MutableStateFlow(ConnState.Disconnected)
    val conn: StateFlow<ConnState> = _conn.asStateFlow()

    private val _status = MutableStateFlow<StatusBlock?>(null)
    val status: StateFlow<StatusBlock?> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _videoUrl = MutableStateFlow<String?>(null)
    val videoUrl: StateFlow<String?> = _videoUrl.asStateFlow()

    init {
        viewModelScope.launch {
            store.active.collectLatest { id ->
                bindTo(store.printers.value.firstOrNull { it.id == id })
            }
        }
    }

    private var bound: SdcpClient? = null
    private fun bindTo(p: SavedPrinter?) {
        bound?.close()
        bound = null
        _client.value = null
        _status.value = null
        _videoUrl.value = null
        _conn.value = ConnState.Disconnected
        wireLog.clear()
        if (p == null) return
        val c = SdcpClient(p.host, p.mainboardId, wireLog)
        bound = c
        _client.value = c
        viewModelScope.launch { c.state.collectLatest { _conn.value = it } }
        viewModelScope.launch { c.status.collectLatest { _status.value = it } }
        viewModelScope.launch { c.lastError.collectLatest { _error.value = it } }
        viewModelScope.launch { c.videoUrl.collectLatest { _videoUrl.value = it } }
        c.connect()
    }

    fun reconnect() {
        bound?.disconnect()
        bound?.connect()
    }

    fun startDiscovery(ctx: Context) {
        viewModelScope.launch {
            PrinterDiscovery.discover(ctx).collectLatest { _discovered.value = it }
        }
    }

    fun addPrinter(nickname: String, host: String, mid: String?) {
        val p = SavedPrinter(id = UUID.randomUUID().toString(), nickname = nickname, host = host, mainboardId = mid)
        store.add(p)
        store.setActive(p.id)
    }

    fun selectPrinter(id: String) = store.setActive(id)
    fun removePrinter(id: String) = store.remove(id)
}
