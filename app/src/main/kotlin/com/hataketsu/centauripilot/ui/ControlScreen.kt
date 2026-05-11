package com.hataketsu.centauripilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hataketsu.centauripilot.AppViewModel

@Composable
fun ControlScreen(vm: AppViewModel) {
    val client by vm.client.collectAsState()
    val st by vm.status.collectAsState()
    val c = client

    var speedPct by remember(st?.printInfo?.printSpeedPct) {
        mutableStateOf((st?.printInfo?.printSpeedPct ?: 100).toFloat())
    }
    var step by remember { mutableStateOf(10) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard("Lệnh in") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { c?.pausePrint() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text("Pause")
                }
                FilledTonalButton(onClick = { c?.resumePrint() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Resume")
                }
                Button(
                    onClick = { c?.stopPrint() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(4.dp)); Text("Stop") }
            }
            Spacer(Modifier.height(12.dp))
            Text("Tốc độ in: ${speedPct.toInt()}%", color = Color.White)
            Slider(value = speedPct, onValueChange = { speedPct = it }, valueRange = 50f..200f, steps = 14,
                onValueChangeFinished = { c?.setPrintSpeedPct(speedPct.toInt()) })
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("Jog (di chuyển trục)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bước:", color = Color.White, modifier = Modifier.width(60.dp))
                listOf(1, 5, 10, 50).forEach { v ->
                    FilterChip(selected = step == v, onClick = { step = v },
                        label = { Text("${v}mm") }, modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            JogPad(
                onJog = { axis, dir -> c?.jog(axis, if (dir > 0) step else -step) },
                onHomeAxis = { axis -> c?.home(axis) }
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("Camera / Video stream") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { c?.toggleVideoStream(true) }) { Text("Bật stream") }
                FilledTonalButton(onClick = { c?.toggleVideoStream(false) }) { Text("Tắt stream") }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14202F))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            body()
        }
    }
}

@Composable
private fun JogPad(onJog: (String, Int) -> Unit, onHomeAxis: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("XY", color = Color(0xFF8FA1B8), fontSize = 11.sp)
        FilledTonalButton(onClick = { onJog("Y", +1) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { onJog("X", -1) }) { Icon(Icons.Default.KeyboardArrowLeft, null) }
            FilledTonalButton(onClick = { onHomeAxis("XYZ") }) { Icon(Icons.Default.Home, null) }
            FilledTonalButton(onClick = { onJog("X", +1) }) { Icon(Icons.Default.KeyboardArrowRight, null) }
        }
        FilledTonalButton(onClick = { onJog("Y", -1) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
        Spacer(Modifier.height(12.dp))
        Text("Z", color = Color(0xFF8FA1B8), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onJog("Z", +1) }) { Text("Z +") }
            FilledTonalButton(onClick = { onJog("Z", -1) }) { Text("Z -") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onHomeAxis("X") }) { Text("Home X") }
            TextButton(onClick = { onHomeAxis("Y") }) { Text("Home Y") }
            TextButton(onClick = { onHomeAxis("Z") }) { Text("Home Z") }
        }
    }
}
