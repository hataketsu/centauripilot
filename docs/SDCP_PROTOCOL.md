# SDCP Protocol — Elegoo Centauri Carbon (reverse-engineered)

> Source: traffic capture + `main.js` of stock web UI at `http://<printer>/` (`runtime.js` chunk map → `590.js` controller logic → `main.js` module 543 enum + module 5025 envelope builder). Firmware tested: **V1.1.46**, ProtocolVersion **V3.0.0**.

This documents what CentauriPilot uses. Mileage on other Elegoo / Chitubox printers (Saturn, Mars, etc.) varies — most cmd codes are shared but payload fields may differ.

---

## 1. Discovery (UDP broadcast)

Send a UDP packet with literal body `M99999` to **port 3000** on the broadcast address (255.255.255.255 or per-interface broadcast).

Each printer on the LAN unicasts back a JSON envelope:

```json
{
  "Id": "979d4C788A4a78bC777A870F1A02867A",
  "Data": {
    "Name": "Centauri Carbon",
    "MachineName": "Centauri Carbon",
    "BrandName": "ELEGOO",
    "MainboardIP": "192.168.1.125",
    "MainboardID": "385b1f950107625d00004c0000000000",
    "ProtocolVersion": "V3.0.0",
    "FirmwareVersion": "V1.1.46"
  }
}
```

Use `MainboardIP` to build the WS URL, save `MainboardID` for later (not currently required in outgoing envelopes — see §2).

mDNS (`_sdcp._tcp` / `_http._tcp`) is **not** advertised by this firmware. Don't waste effort there.

---

## 2. WebSocket envelope

Endpoint: `ws://<MainboardIP>:3030/websocket`

### Outgoing (request)

```json
{
  "Id": "",
  "Data": {
    "Cmd": <int>,
    "Data": { ...cmd-specific... },
    "RequestID": "<uuid hex, dashes stripped>",
    "MainboardID": "",
    "TimeStamp": <ms since epoch>,
    "From": 1
  }
}
```

- `MainboardID` is **empty string** in outgoing — the printer fills it in on response. Don't waste a round-trip getting it first.
- No `Topic` field on outgoing.
- `From: 1` = "remote client".

### Incoming (response, async status, attributes, errors)

```json
{
  "Id": "979d...",
  "Data": { "Cmd": <int>, "Data": {"Ack": 0, ...}, "RequestID": "...", "MainboardID": "...", "TimeStamp": ... },
  "Topic": "sdcp/response/<MainboardID>"
}
```

Or status broadcast (auto-pushed):

```json
{
  "Status": { ...StatusBlock... },
  "MainboardID": "...",
  "TimeStamp": ...,
  "Topic": "sdcp/status/<MainboardID>"
}
```

Topics seen: `sdcp/response/<MID>`, `sdcp/status/<MID>`, `sdcp/attributes/<MID>`, `sdcp/error/<MID>`.

### Ack semantics

In `Data.Data.Ack`: **0 = success**, anything else = printer-side error code (translation strings exist in `i18n` bundles like `networkDeviceManager.control.axisError.<code>`).

---

## 3. Cmd codes

From module `543` of `main.js`:

```text
0    GET_PRINTER_STATUS
1    GET_PRINTER_ATTR
64   SEND_PRINTER_DISCONNECT
128  SEND_PRINTER_START_PRINT
129  SEND_PRINTER_SUSPEND_PRINT      (pause)
130  SEND_PRINTER_STOP_PRINT
131  SEND_PRINTER_RESTORE_PRINT      (resume)
134  GET_BLACKOUT_STATUS
135  SEND_BLACKOUT_ACTION
192  SEND_PRINTER_EDIT_NAME
255  SEND_PRINTER_SEND_FILE_END
257  EDIT_PRINTER_FILE_NAME
258  GET_PRINTER_FILE_LIST
259  DELETE_PRINTER_FILE_LIST
260  GET_PRINTER_FILE_DETAIL
320  GET_PRINTER_HISTORY_ID
321  GET_PRINTER_TASK_DETAIL
322  DELETE_PRINTER_HISTORY
323  GET_PRINTER_HISTORY_VIDEO
386  EDIT_PRINTER_VIDEO_STREAMING
387  EDIT_PRINTER_TIME_LAPSE_STATUS
401  EDIT_PRINTER_AXIS_NUMBER        (jog)
402  EDIT_PRINTER_AXIS_ZERO          (home)
403  EDIT_PRINTER_STATUS_DATA        (temps / fan / light / speed — multiplexed)
```

### Cmd 128 — START_PRINT

```json
{
  "Filename": "/path/to/file.gcode",
  "StartLayer": 0,
  "Calibration_switch": 1,
  "PrintPlatformType": 0,
  "Tlp_Switch": 0
}
```

`Calibration_switch=1` = run hotbed auto-leveling probe at print start (recommended for first print of a session).

### Cmd 129 / 130 / 131 — pause / stop / resume

No `Data` payload required (empty `{}`). Just the envelope.

### Cmd 258 — GET_PRINTER_FILE_LIST

```json
{ "Url": "/local" }
```

Subpaths supported: `/local/subdir`.

### Cmd 259 — DELETE_PRINTER_FILE_LIST

```json
{ "FileList": ["/path1.gcode", "/path2.gcode"] }
```

### Cmd 260 — GET_PRINTER_FILE_DETAIL

```json
{ "Url": "/file.gcode" }
```

Response `Data.FileInfo` contains `Thumbnail` (base64 PNG), `EstTime` (seconds), `EstWeight` (grams).

### Cmd 386 — EDIT_PRINTER_VIDEO_STREAMING

```json
{ "Enable": 1 }   // or 0 to stop
```

Response `Data.VideoUrl` = MJPEG URL (e.g. `"192.168.1.125:3031/video"` — **scheme stripped**, prepend `http://`). Some firmware variants return WebRTC (`Capabilities: ["VIDEO_WEBRTC"]`).

### Cmd 401 — JOG axis

```json
{ "Axis": "X", "Step": 10 }     // also "Y", "Z"; negative Step = reverse
```

Refuses while `CurrentStatus` includes `1` (printing).

### Cmd 402 — HOME axis

```json
{ "Axis": "X" }    // or "Y", "Z", "XYZ"
```

### Cmd 403 — multiplexed setter

Send ONE field per request (web UI does this), printer accepts any subset:

```json
{ "TempTargetNozzle": 200 }
{ "TempTargetHotbed": 60 }
{ "TempTargetBox": 35 }
{ "PrintSpeedPct": 100 }
{ "TargetFanSpeed": { "ModelFan": 80, "AuxiliaryFan": 0, "BoxFan": 50 } }
{ "LightStatus":   { "SecondLight": 1, "RgbLight": [255, 200, 100] } }
```

---

## 4. Status block (auto-pushed every ~2s; also reply to Cmd 0)

```json
{
  "Status": {
    "CurrentStatus": [0],
    "TimeLapseStatus": 0,
    "PlatFormType": 0,
    "TempOfHotbed": 30.3,
    "TempOfNozzle": 31.3,
    "TempOfBox": 29.4,
    "TempTargetHotbed": 0,
    "TempTargetNozzle": 0,
    "TempTargetBox": 0,
    "CurrenCoord": "202.00,264.50,99.70",
    "CurrentFanSpeed": { "ModelFan": 0, "AuxiliaryFan": 0, "BoxFan": 0 },
    "ZOffset": 0.07,
    "LightStatus": { "SecondLight": 1, "RgbLight": [0,0,0] },
    "PrintInfo": {
      "Status": 9, "CurrentLayer": 79, "TotalLayer": 79,
      "CurrentTicks": 4015.5, "TotalTicks": 4075,
      "Filename": "", "TaskId": "",
      "PrintSpeedPct": 100, "Progress": 0
    }
  },
  "MainboardID": "...",
  "TimeStamp": 1778504086,
  "Topic": "sdcp/status/<MID>"
}
```

`CurrentStatus[0]`: 0=Idle, 1=Printing, 2=File Transferring, 3=Exposure Testing, 4=Devices Testing.

`PrintInfo.Status` (sub-state during print): 0=Idle, 1=Homing, 2=Drop, 3=Expose, 4=Lift, 5=Pausing, 6=Paused, 7=Stopping, 8=Stopped, 9=Complete, 10=Checking file, 13=Printing, 16=Calibrating, 17=ABL, 18=Heating, 19=Preheat, 20=Resuming.

---

## 5. Heartbeat / connection keepalive

Client periodically sends Cmd 0 every 2 seconds (matches web UI). WebSocket ping frame at 15s on OkHttp handles dead-peer detection.

---

## 6. Camera

After successful Cmd 386 enable, fetch `http://<host>:3031/video` as `multipart/x-mixed-replace; boundary=--foo` MJPEG. Each part is a full JPEG (SOI `FFD8` … EOI `FFD9`).

Some hardware variants advertise `Capabilities: ["VIDEO_WEBRTC"]` in their attributes block and serve WebRTC — not yet supported in CentauriPilot.

---

## 7. Files structure

`/local` is the root on internal storage. Files seen in responses use either:

```json
{ "name": "file.gcode", "type": 1, "size": 12345 }
```

or the older variant:

```json
{ "FileName": "file.gcode", "FileType": 1, "FileSize": 12345 }
```

`type/FileType`: 0 = folder, 1 = file.

---

## 8. References

- Web UI bundle source files (de-minified manually):
  - `runtime.b70be9be0b8213a3c98e.js` — chunk map
  - `main.a2c4cc1113991bcca762.js` — module 543 (Cmd enum), 5025 (envelope builder)
  - `590.ad983f7d75c06e07fff5.js` — printer-control component (where each Cmd is fired from with its exact payload shape)

Drop CentauriPilot into the **Log** tab and toggle a feature on the stock web UI side-by-side to find any cmd not documented here.
