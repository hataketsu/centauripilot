package com.hataketsu.centauripilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hataketsu.centauripilot.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private data class RemoteFile(val name: String, val size: Long?, val isDir: Boolean)

@Composable
fun FilesScreen(vm: AppViewModel) {
    val client by vm.client.collectAsState()
    val log by vm.wireLog.items.collectAsState()
    var currentDir by remember { mutableStateOf("/local") }
    var selected by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Parse latest GET_PRINTER_FILE_LIST response from wire log
    val files: List<RemoteFile> = remember(log) {
        val last = log.lastOrNull {
            it.text.contains("sdcp/response") && it.text.contains("\"Cmd\":258")
        } ?: return@remember emptyList<RemoteFile>()
        try {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(last.text).jsonObject
            val data = obj["Data"]?.jsonObject?.get("Data")?.jsonObject
            val list = data?.get("FileList")?.jsonArray ?: return@remember emptyList()
            list.mapNotNull { e ->
                val o = e.jsonObject
                val name = o["name"]?.jsonPrimitive?.contentOrNull
                    ?: o["FileName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val type = o["type"]?.jsonPrimitive?.intOrNull
                    ?: o["FileType"]?.jsonPrimitive?.intOrNull
                val size = o["size"]?.jsonPrimitive?.longOrNullSafe()
                    ?: o["FileSize"]?.jsonPrimitive?.longOrNullSafe()
                RemoteFile(name, size, isDir = (type == 0))
            }
        } catch (_: Exception) { emptyList() }
    }

    // Auto-detect successful delete (Cmd 259, Ack=0) → re-fetch list automatically
    val lastDeleteResp = remember(log) {
        log.lastOrNull { it.text.contains("sdcp/response") && it.text.contains("\"Cmd\":259") }
    }
    LaunchedEffect(lastDeleteResp?.ts) {
        val resp = lastDeleteResp ?: return@LaunchedEffect
        // Look for Ack:0 in payload
        if (resp.text.contains("\"Ack\":0")) {
            delay(300)
            client?.fetchFileList(currentDir)
            toast = "Đã xóa"
        } else if (resp.text.contains("\"Ack\":")) {
            toast = "Xóa lỗi (Ack ≠ 0)"
        }
    }

    LaunchedEffect(currentDir, client, refreshTick) { client?.fetchFileList(currentDir) }
    LaunchedEffect(toast) { if (toast != null) { delay(2000); toast = null } }

    fun pathOf(name: String): String =
        "${currentDir.removePrefix("/local")}/$name".replace("//", "/").ifEmpty { "/$name" }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currentDir, color = Color.White, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Default.Refresh, null, tint = Color.White)
            }
            if (currentDir != "/local") {
                IconButton(onClick = { currentDir = currentDir.substringBeforeLast('/').ifEmpty { "/local" } }) {
                    Icon(Icons.Default.ArrowUpward, null, tint = Color.White)
                }
            }
        }
        HorizontalDivider(color = Color(0xFF223247))
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Trống / chưa nhận file list. Bấm Refresh.",
                    color = Color(0xFF8FA1B8), fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(files) { f ->
                    val isSel = selected == f.name
                    ListItem(
                        headlineContent = { Text(f.name, color = Color.White, fontSize = 14.sp) },
                        supportingContent = {
                            Text(if (f.isDir) "Folder" else "${(f.size ?: 0) / 1024} KB",
                                color = Color(0xFF8FA1B8), fontSize = 11.sp)
                        },
                        leadingContent = {
                            Icon(
                                if (f.isDir) Icons.Default.Folder else Icons.Default.Description,
                                null, tint = if (f.isDir) Color(0xFFF2C037) else Color(0xFF4FC3F7)
                            )
                        },
                        trailingContent = if (isSel) {{ Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50)) }} else null,
                        colors = ListItemDefaults.colors(containerColor = if (isSel) Color(0xFF1B2A3E) else Color(0xFF14202F)),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (f.isDir) {
                            TextButton(onClick = { currentDir = "$currentDir/${f.name}".replace("//","/") }) {
                                Text("Mở")
                            }
                        } else {
                            TextButton(onClick = { selected = f.name; client?.fetchFileDetail(pathOf(f.name)) }) {
                                Text("Chọn")
                            }
                            TextButton(onClick = { client?.startPrint(pathOf(f.name)) }) {
                                Text("In", color = Color(0xFFF2C037))
                            }
                            TextButton(onClick = { confirmDelete = f.name }) {
                                Text("Xóa", color = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }
        }
        // Snackbar-like toast
        toast?.let {
            Surface(color = Color(0xFF1B2A3E),
                modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text(it, color = Color.White, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
    }

    // Confirm delete dialog
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Color(0xFF14202F),
            title = { Text("Xóa file?", color = Color.White) },
            text = { Text(name, color = Color(0xFF8FA1B8), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    client?.deleteFiles(listOf(pathOf(name)))
                    confirmDelete = null
                    toast = "Đang xóa…"
                }) { Text("Xóa", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Hủy") }
            }
        )
    }
}

private fun JsonPrimitive.longOrNullSafe(): Long? = try { content.toLong() } catch (_: Exception) { null }
