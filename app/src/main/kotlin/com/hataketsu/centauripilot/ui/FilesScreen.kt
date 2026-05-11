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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

    // Parse latest GET_PRINTER_FILE_LIST response from wire log
    val files: List<RemoteFile> = remember(log) {
        val last = log.lastOrNull {
            it.text.contains("sdcp/response") && it.text.contains("\"Cmd\":258")
        } ?: return@remember emptyList<RemoteFile>()
        try {
            val raw = last.text
            // crude: find FileList array in JSON
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
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
        } catch (e: Exception) { emptyList() }
    }

    LaunchedEffect(currentDir, client) { client?.fetchFileList(currentDir) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currentDir, color = Color.White, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            IconButton(onClick = { client?.fetchFileList(currentDir) }) {
                Icon(Icons.Default.Refresh, null, tint = Color.White)
            }
            if (currentDir != "/local") {
                IconButton(onClick = { currentDir = currentDir.substringBeforeLast('/').ifEmpty { "/local" } }) {
                    Icon(Icons.Default.ArrowUpward, null, tint = Color.White)
                }
            }
        }
        Divider(color = Color(0xFF223247))
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
                            .also { /* clickable via Surface below */ }
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (f.isDir) {
                            TextButton(onClick = { currentDir = "$currentDir/${f.name}".replace("//","/") }) {
                                Text("Mở")
                            }
                        } else {
                            TextButton(onClick = { selected = f.name; client?.fetchFileDetail("${currentDir.removePrefix("/local")}/${f.name}".replace("//","/")) }) {
                                Text("Chọn")
                            }
                            TextButton(onClick = {
                                client?.startPrint("${currentDir.removePrefix("/local")}/${f.name}".replace("//","/"))
                            }) { Text("In", color = Color(0xFFF2C037)) }
                            TextButton(onClick = { client?.deleteFiles(listOf("${currentDir.removePrefix("/local")}/${f.name}".replace("//","/"))) }) {
                                Text("Xóa", color = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun JsonPrimitive.longOrNullSafe(): Long? = try { content.toLong() } catch (_: Exception) { null }
