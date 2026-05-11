package com.hataketsu.centauripilot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hataketsu.centauripilot.AppViewModel
import com.hataketsu.centauripilot.sdcp.WireDir

@Composable
fun LogScreen(vm: AppViewModel) {
    val log by vm.wireLog.items.collectAsState()
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("ALL") }

    LaunchedEffect(log.size, autoScroll) {
        if (autoScroll && log.isNotEmpty()) listState.scrollToItem(log.size - 1)
    }

    val filtered = remember(log, filter) {
        when (filter) {
            "OUT" -> log.filter { it.dir == WireDir.OUT }
            "IN"  -> log.filter { it.dir == WireDir.IN }
            "STATUS" -> log.filter { it.dir == WireDir.IN && it.text.contains("sdcp/status/") }
            "OTHER" -> log.filter { !(it.dir == WireDir.IN && it.text.contains("sdcp/status/")) }
            else -> log
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0F1A)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wire log (${filtered.size}/${log.size})", color = Color.White,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            FilterChip(selected = autoScroll, onClick = { autoScroll = !autoScroll },
                label = { Text("Auto") })
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { copyAll(ctx, filtered.joinToString("\n") { e ->
                "${e.tsFormatted()} ${e.dir} ${e.cmd ?: ""} ${e.text}"
            }) }) { Icon(Icons.Default.ContentCopy, "copy", tint = Color.White) }
            IconButton(onClick = { vm.wireLog.clear() }) {
                Icon(Icons.Default.Delete, "clear", tint = Color(0xFFFF6B6B))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            listOf("ALL", "OUT", "IN", "STATUS", "OTHER").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f },
                    label = { Text(f, fontSize = 11.sp) })
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(filtered) { e ->
                val (color, prefix) = when (e.dir) {
                    WireDir.OUT -> Color(0xFFF2C037) to "→"
                    WireDir.IN -> Color(0xFF4FC3F7) to "←"
                    WireDir.INFO -> Color(0xFF8FA1B8) to "ℹ"
                    WireDir.WARN -> Color(0xFFFFA726) to "⚠"
                    WireDir.ERROR -> Color(0xFFFF6B6B) to "✕"
                }
                val label = buildString {
                    append("$prefix ${e.tsFormatted()} ")
                    if (e.cmd != null) append("[Cmd ${e.cmd}] ")
                    append(e.text)
                }
                Text(
                    label,
                    color = color,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }
    }
}

private fun copyAll(ctx: Context, txt: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("centauri-pilot-log", txt))
}
