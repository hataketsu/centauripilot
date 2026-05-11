package com.hataketsu.centauripilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hataketsu.centauripilot.AppViewModel

@Composable
fun CameraScreen(vm: AppViewModel) {
    val printers by vm.printers.collectAsState()
    val active by vm.activeId.collectAsState()
    val host = printers.firstOrNull { it.id == active }?.host
    val client by vm.client.collectAsState()
    val videoUrl by vm.videoUrl.collectAsState()
    var enabled by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        if (host == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa chọn máy in", color = Color(0xFF8FA1B8))
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Camera @ $host", color = Color(0xFF8FA1B8), fontSize = 12.sp,
                modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = {
                enabled = !enabled
                client?.toggleVideoStream(enabled)
            }) {
                Icon(if (enabled) Icons.Default.VideocamOff else Icons.Default.Videocam, null)
                Spacer(Modifier.width(4.dp))
                Text(if (enabled) "Stop stream" else "Bật stream")
            }
        }
        Spacer(Modifier.height(4.dp))
        // Prefer Cmd-386-returned URL (parsed by client). Fallback to MJPEG :3031/video.
        val mjpegUrl = videoUrl ?: "http://$host:3031/video"
        Text(mjpegUrl, color = Color(0xFF4FC3F7), fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        MjpegView(host = host, urlOverride = mjpegUrl, modifier = Modifier.fillMaxSize())
    }
}
