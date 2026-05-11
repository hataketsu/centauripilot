package com.hataketsu.centauripilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hataketsu.centauripilot.AppViewModel
import com.hataketsu.centauripilot.sdcp.ConnState
import com.hataketsu.centauripilot.sdcp.CurrentStatusCode
import com.hataketsu.centauripilot.sdcp.PrintStatusCode
import com.hataketsu.centauripilot.sdcp.StatusBlock
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    val printers by vm.printers.collectAsState()
    val activeId by vm.activeId.collectAsState()
    var showPrinterPicker by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val active = printers.firstOrNull { it.id == activeId }
                    Column {
                        Text("Centauri Pilot", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        if (active != null) Text(active.nickname, fontSize = 12.sp, color = Color(0xFF8FA1B8))
                    }
                },
                actions = {
                    IconButton(onClick = { showPrinterPicker = true }) {
                        Icon(Icons.Default.Devices, contentDescription = "printers")
                    }
                    IconButton(onClick = { vm.reconnect() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF14202F))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF14202F)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, null) }, label = { Text("Dashboard") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Tune, null) }, label = { Text("Control") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Videocam, null) }, label = { Text("Camera") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Files") })
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.Terminal, null) }, label = { Text("Log") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0A0F1A))) {
            if (printers.isEmpty()) {
                EmptyState(onAdd = { showAdd = true })
            } else {
                when (tab) {
                    0 -> DashboardScreen(vm)
                    1 -> ControlScreen(vm)
                    2 -> CameraScreen(vm)
                    3 -> FilesScreen(vm)
                    4 -> LogScreen(vm)
                }
            }
        }
    }

    if (showPrinterPicker) {
        PrinterPickerSheet(
            vm = vm,
            onDismiss = { showPrinterPicker = false },
            onAdd = { showPrinterPicker = false; showAdd = true }
        )
    }
    if (showAdd) {
        AddPrinterSheet(vm = vm, onDismiss = { showAdd = false })
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Print, null, modifier = Modifier.size(72.dp), tint = Color(0xFFF2C037))
        Spacer(Modifier.height(16.dp))
        Text("Chưa có máy in nào", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Thêm máy in bằng IP hoặc tự động dò trong mạng LAN",
            color = Color(0xFF8FA1B8), fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) { Text("Thêm máy in") }
    }
}

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val conn by vm.conn.collectAsState()
    val st by vm.status.collectAsState()
    val err by vm.error.collectAsState()
    val client by vm.client.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        ConnBadge(conn)
        Spacer(Modifier.height(12.dp))
        if (st == null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14202F))) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(if (conn == ConnState.Connecting) "Đang kết nối…" else "Chờ dữ liệu trạng thái…",
                        color = Color(0xFF8FA1B8))
                }
            }
        } else {
            StatusCard(st!!)
            Spacer(Modifier.height(12.dp))
            TempControlCard(st!!, client)
            Spacer(Modifier.height(12.dp))
            FanControlCard(st!!, client)
            Spacer(Modifier.height(12.dp))
            LightCard(st!!, client)
            Spacer(Modifier.height(12.dp))
            PositionCard(st!!)
        }
        if (err != null) {
            Spacer(Modifier.height(12.dp))
            Text("⚠️ ${err}", color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ConnBadge(conn: ConnState) {
    val (color, label) = when (conn) {
        ConnState.Connected -> Color(0xFF4CAF50) to "Đã kết nối"
        ConnState.Connecting -> Color(0xFFFFA726) to "Đang kết nối"
        ConnState.Error -> Color(0xFFFF6B6B) to "Lỗi"
        else -> Color(0xFF8FA1B8) to "Mất kết nối"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun StatusCard(st: StatusBlock) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14202F))) {
        Column(Modifier.padding(16.dp)) {
            val cur = st.currentStatus.firstOrNull() ?: 0
            Text(CurrentStatusCode.label(cur), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(PrintStatusCode.label(st.printInfo.status), color = Color(0xFFF2C037), fontSize = 13.sp)
            if (st.printInfo.totalLayer > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { st.printInfo.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFF2C037), trackColor = Color(0xFF223247)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${st.printInfo.progress}%", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Layer ${st.printInfo.currentLayer}/${st.printInfo.totalLayer}",
                        color = Color(0xFF8FA1B8), fontSize = 13.sp)
                }
                val remain = ((st.printInfo.totalTicks - st.printInfo.currentTicks).coerceAtLeast(0.0))
                Text("Còn ~${formatTicks(remain)}  •  Tốc độ ${st.printInfo.printSpeedPct}%",
                    color = Color(0xFF8FA1B8), fontSize = 12.sp)
                if (st.printInfo.filename.isNotEmpty())
                    Text("📄 ${st.printInfo.filename}", color = Color(0xFF8FA1B8), fontSize = 12.sp)
            }
        }
    }
}

private fun formatTicks(ms: Double): String {
    val s = (ms / 1000).toLong()
    val h = s / 3600; val m = (s % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun TempControlCard(st: StatusBlock, client: com.hataketsu.centauripilot.sdcp.SdcpClient?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14202F))) {
        Column(Modifier.padding(16.dp)) {
            Text("Nhiệt độ", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            TempControlRow("Nozzle", st.tempOfNozzle, st.tempTargetNozzle, Color(0xFFFF8A65)) {
                client?.setTempNozzle(it)
            }
            TempControlRow("Hotbed", st.tempOfHotbed, st.tempTargetHotbed, Color(0xFFFFCA28)) {
                client?.setTempBed(it)
            }
            TempControlRow("Buồng", st.tempOfBox, st.tempTargetBox, Color(0xFF4FC3F7)) {
                client?.setTempBox(it)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                client?.setTempNozzle(0); client?.setTempBed(0); client?.setTempBox(0)
            }) { Text("Tắt tất cả heater", color = Color(0xFFFF6B6B)) }
        }
    }
}

@Composable
private fun TempControlRow(
    label: String, cur: Double, tgt: Double, color: Color,
    onApply: (Int) -> Unit
) {
    var text by remember(tgt) { mutableStateOf(tgt.roundToInt().toString()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text("Hiện ${cur.roundToInt()}° / mục tiêu ${tgt.roundToInt()}°",
                color = Color(0xFF8FA1B8), fontSize = 11.sp)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) text = it },
            modifier = Modifier.width(80.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.width(6.dp))
        Button(onClick = { onApply(text.toIntOrNull() ?: 0) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Set", fontSize = 13.sp)
        }
    }
}

@Composable
private fun FanControlCard(st: StatusBlock, client: com.hataketsu.centauripilot.sdcp.SdcpClient?) {
    var model by remember(st.currentFanSpeed.modelFan) { mutableStateOf(st.currentFanSpeed.modelFan.toFloat()) }
    var aux by remember(st.currentFanSpeed.auxiliaryFan) { mutableStateOf(st.currentFanSpeed.auxiliaryFan.toFloat()) }
    var box by remember(st.currentFanSpeed.boxFan) { mutableStateOf(st.currentFanSpeed.boxFan.toFloat()) }
    fun apply() = client?.setFanSpeed(model.toInt(), aux.toInt(), box.toInt())

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14202F))) {
        Column(Modifier.padding(16.dp)) {
            Text("Quạt", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FanSliderRow("Model", model, { model = it }, { apply() })
            FanSliderRow("Phụ", aux, { aux = it }, { apply() })
            FanSliderRow("Buồng", box, { box = it }, { apply() })
        }
    }
}

@Composable
private fun FanSliderRow(label: String, v: Float, onChange: (Float) -> Unit, onCommit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label ${v.toInt()}%", color = Color.White, modifier = Modifier.width(100.dp), fontSize = 13.sp)
        Slider(value = v, onValueChange = onChange, valueRange = 0f..100f,
            onValueChangeFinished = onCommit, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LightCard(st: StatusBlock, client: com.hataketsu.centauripilot.sdcp.SdcpClient?) {
    val on = st.lightStatus.secondLight == 1
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14202F))) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lightbulb, null,
                tint = if (on) Color(0xFFF2C037) else Color(0xFF8FA1B8))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Đèn buồng", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(if (on) "BẬT" else "TẮT", color = Color(0xFF8FA1B8), fontSize = 12.sp)
            }
            Switch(checked = on, onCheckedChange = { client?.setLight(chamberOn = it) })
        }
    }
}

@Composable
private fun PositionCard(st: StatusBlock) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14202F))) {
        Column(Modifier.padding(16.dp)) {
            Text("Vị trí & Z-offset", color = Color(0xFF8FA1B8), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("XYZ: ${st.currentCoord}", color = Color.White, fontSize = 13.sp)
            Text("Z-offset: ${"%.3f".format(st.zOffset)} mm", color = Color.White, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrinterPickerSheet(vm: AppViewModel, onDismiss: () -> Unit, onAdd: () -> Unit) {
    val printers by vm.printers.collectAsState()
    val active by vm.activeId.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14202F)) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Máy in", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            printers.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = p.id == active, onClick = { vm.selectPrinter(p.id); onDismiss() })
                    Column(Modifier.weight(1f)) {
                        Text(p.nickname, color = Color.White)
                        Text(p.host, color = Color(0xFF8FA1B8), fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.removePrinter(p.id) }) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF6B6B))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Thêm máy in")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPrinterSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var nick by remember { mutableStateOf("Centauri Carbon") }
    var ip by remember { mutableStateOf("") }
    val discovered by vm.discovered.collectAsState()
    LaunchedEffect(Unit) { vm.startDiscovery(ctx) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14202F)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Thêm máy in", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = nick, onValueChange = { nick = it },
                label = { Text("Tên gọi") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = ip, onValueChange = { ip = it },
                label = { Text("IP máy in (ví dụ 192.168.1.125)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                if (ip.isNotBlank()) {
                    vm.addPrinter(nick.ifBlank { ip }, ip.trim(), null)
                    onDismiss()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Lưu") }
            Spacer(Modifier.height(20.dp))
            Text("Tự động dò trong mạng (mDNS)", color = Color(0xFF8FA1B8), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (discovered.isEmpty()) {
                Text("Đang quét…", color = Color(0xFF8FA1B8), fontSize = 13.sp)
            } else {
                discovered.forEach { d ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A3E)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Print, null, tint = Color(0xFFF2C037))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, color = Color.White, fontSize = 14.sp)
                                Text("${d.host}:${d.port}", color = Color(0xFF8FA1B8), fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                vm.addPrinter(d.name, d.host, d.mainboardId)
                                onDismiss()
                            }) { Text("Thêm") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
