package com.hataketsu.centauripilot.sdcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ConnState { Disconnected, Connecting, Connected, Error }

/**
 * SDCP WebSocket client — verified against Chitubox/Elegoo web UI main.js.
 *
 * Endpoint: ws://<host>:3030/websocket
 *
 * Wire format (matches `getMsgBodyString`):
 *   {"Id":"","Data":{"Cmd":N,"Data":{...},"RequestID":"<uuid-no-dash>","MainboardID":"","TimeStamp":<ms>,"From":1}}
 * No Topic field on outgoing request.
 *
 * Cmd codes (confirmed by RE of main.js Cmd enum 543 module):
 *   0   GET_PRINTER_STATUS
 *   1   GET_PRINTER_ATTR
 *   64  SEND_PRINTER_DISCONNECT
 *   128 SEND_PRINTER_START_PRINT     {Filename, StartLayer:0, Calibration_switch, PrintPlatformType, Tlp_Switch...}
 *   129 SEND_PRINTER_SUSPEND_PRINT   (pause; no payload)
 *   130 SEND_PRINTER_STOP_PRINT      (no payload)
 *   131 SEND_PRINTER_RESTORE_PRINT   (resume; no payload)
 *   134 GET_BLACKOUT_STATUS
 *   135 SEND_BLACKOUT_ACTION
 *   192 SEND_PRINTER_EDIT_NAME
 *   255 SEND_PRINTER_SEND_FILE_END
 *   257 EDIT_PRINTER_FILE_NAME
 *   258 GET_PRINTER_FILE_LIST        {Url:"/local"}
 *   259 DELETE_PRINTER_FILE_LIST     {FileList:[...]}
 *   260 GET_PRINTER_FILE_DETAIL      {Url:filename}
 *   320 GET_PRINTER_HISTORY_ID
 *   321 GET_PRINTER_TASK_DETAIL      {Id:[...]}
 *   322 DELETE_PRINTER_HISTORY       {Id:[...]}
 *   323 GET_PRINTER_HISTORY_VIDEO    {Url:taskId}
 *   386 EDIT_PRINTER_VIDEO_STREAMING {Enable:0|1}
 *   387 EDIT_PRINTER_TIME_LAPSE_STATUS
 *   401 EDIT_PRINTER_AXIS_NUMBER     {Axis:"X|Y|Z", Step:N}     <- jog
 *   402 EDIT_PRINTER_AXIS_ZERO       {Axis:"X|Y|Z|XYZ"}         <- home
 *   403 EDIT_PRINTER_STATUS_DATA     {TempTargetNozzle|TempTargetHotbed|TempTargetBox|
 *                                     TargetFanSpeed|LightStatus|PrintSpeedPct}
 */
class SdcpClient(
    val host: String,
    private var mainboardId: String? = null,
    private val log: WireLog
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null
    private var heartbeatJob: Job? = null

    private val _state = MutableStateFlow(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _status = MutableStateFlow<StatusBlock?>(null)
    val status: StateFlow<StatusBlock?> = _status.asStateFlow()

    private val _attributes = MutableStateFlow<JsonObject?>(null)
    val attributes: StateFlow<JsonObject?> = _attributes.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _videoUrl = MutableStateFlow<String?>(null)
    val videoUrl: StateFlow<String?> = _videoUrl.asStateFlow()

    fun mainboardId(): String? = mainboardId

    fun connect() {
        if (_state.value == ConnState.Connecting || _state.value == ConnState.Connected) return
        _state.value = ConnState.Connecting
        log.info("→ ws://$host:3030/websocket connecting…")
        val req = Request.Builder().url("ws://$host:3030/websocket").build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnState.Connected
                _lastError.value = null
                log.info("✓ connected")
                send(0); send(1); send(320)
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    while (true) { delay(2000); send(0) }
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) { handleMessage(text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = ConnState.Disconnected
                log.info("× closing $code $reason")
                webSocket.close(1000, null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnState.Error
                _lastError.value = t.message ?: t.javaClass.simpleName
                log.error("× failure: ${t.message}")
                heartbeatJob?.cancel()
            }
        })
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        ws?.close(1000, "bye")
        ws = null
        _state.value = ConnState.Disconnected
    }

    fun close() { disconnect(); scope.cancel() }

    private fun handleMessage(text: String) {
        log.recv(text)
        try {
            val env = json.decodeFromString(SdcpEnvelope.serializer(), text)
            if (env.mainboardId != null && mainboardId == null) mainboardId = env.mainboardId
            when {
                env.topic.startsWith("sdcp/status/") -> env.status?.let { _status.value = it }
                env.topic.startsWith("sdcp/attributes/") -> {
                    (env.attributes as? JsonObject)?.let { _attributes.value = it }
                }
                env.topic.startsWith("sdcp/error/") -> _lastError.value = text.take(400)
                env.topic.startsWith("sdcp/response/") -> {
                    // Response with VideoUrl in Cmd 386 ack
                    try {
                        val dataObj = (env.data as? JsonObject)
                        val cmd = dataObj?.get("Cmd")?.toString()?.toIntOrNull() ?: -1
                        val ack = (dataObj?.get("Data") as? JsonObject)?.get("Ack")?.toString()?.toIntOrNull() ?: -1
                        if (ack != 0) log.warn("Cmd $cmd ack=$ack")
                        if (cmd == 386 && ack == 0) {
                            val v = (dataObj?.get("Data") as? JsonObject)?.get("VideoUrl")
                            if (v is JsonPrimitive) {
                                val raw = v.content
                                _videoUrl.value = when {
                                    raw.isBlank() -> null
                                    raw.startsWith("http://") || raw.startsWith("https://") -> raw
                                    raw.startsWith("rtsp://") || raw.startsWith("webrtc://") -> raw
                                    else -> "http://$raw"
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            log.error("parse: ${e.message}")
        }
    }

    /** Build + send exactly like web UI: MainboardID="", no Topic. */
    fun send(cmd: Int, data: JsonObject = JsonObject(emptyMap())) {
        val obj = buildJsonObject {
            put("Id", "")
            put("Data", buildJsonObject {
                put("Cmd", cmd)
                put("Data", data)
                put("RequestID", UUID.randomUUID().toString().replace("-", ""))
                put("MainboardID", "")
                put("TimeStamp", System.currentTimeMillis())
                put("From", 1)
            })
        }
        val text = obj.toString()
        log.send(cmd, text)
        ws?.send(text)
    }

    // ---------- High-level commands ----------
    fun refreshStatus() = send(0)
    fun refreshAttr() = send(1)
    fun refreshHistory() = send(320)
    fun fetchFileList(url: String = "/local") = send(258, buildJsonObject { put("Url", url) })
    fun fetchFileDetail(url: String) = send(260, buildJsonObject { put("Url", url) })
    fun deleteFiles(names: List<String>) = send(259, buildJsonObject {
        put("FileList", JsonArray(names.map { JsonPrimitive(it) }))
    })

    fun pausePrint() = send(129)
    fun resumePrint() = send(131)
    fun stopPrint() = send(130)

    fun startPrint(filename: String, calibration: Boolean = true, platformType: Int = 0, timelapse: Boolean = false) =
        send(128, buildJsonObject {
            put("Filename", filename)
            put("StartLayer", 0)
            put("Calibration_switch", if (calibration) 1 else 0)
            put("PrintPlatformType", platformType)
            put("Tlp_Switch", if (timelapse) 1 else 0)
        })

    fun setPrintSpeedPct(pct: Int) = send(403, buildJsonObject { put("PrintSpeedPct", pct) })

    fun setTempNozzle(v: Int) = send(403, buildJsonObject { put("TempTargetNozzle", v) })
    fun setTempBed(v: Int) = send(403, buildJsonObject { put("TempTargetHotbed", v) })
    fun setTempBox(v: Int) = send(403, buildJsonObject { put("TempTargetBox", v) })

    fun setFanSpeed(model: Int? = null, aux: Int? = null, box: Int? = null) {
        val fan = buildJsonObject {
            put("ModelFan", model ?: 0)
            put("AuxiliaryFan", aux ?: 0)
            put("BoxFan", box ?: 0)
        }
        send(403, buildJsonObject { put("TargetFanSpeed", fan) })
    }

    fun setLight(chamberOn: Boolean? = null, rgb: Triple<Int, Int, Int>? = null) {
        val light = buildJsonObject {
            chamberOn?.let { put("SecondLight", if (it) 1 else 0) }
            rgb?.let {
                put("RgbLight", JsonArray(listOf(
                    JsonPrimitive(it.first), JsonPrimitive(it.second), JsonPrimitive(it.third))))
            }
        }
        send(403, buildJsonObject { put("LightStatus", light) })
    }

    fun jog(axis: String, step: Int) = send(401, buildJsonObject {
        put("Axis", axis); put("Step", step)
    })

    fun home(axis: String = "XYZ") = send(402, buildJsonObject { put("Axis", axis) })

    fun toggleVideoStream(enable: Boolean) =
        send(386, buildJsonObject { put("Enable", if (enable) 1 else 0) })
}
