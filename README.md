# CentauriPilot

Open-source Android client for **Elegoo Centauri Carbon** (and other Chitubox/SDCP-family FDM 3D printers).

> Status: M1 (read + control + camera + file list/start/delete). M2 will add G-code upload, history & timelapse playback.

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)
![License](https://img.shields.io/badge/license-MIT-blue)
![Build](https://github.com/hataketsu/CentauriPilot/actions/workflows/release.yml/badge.svg)

---

## Tính năng

| Khu vực | Hỗ trợ |
|---|---|
| **Discovery** | UDP broadcast `M99999` → port `3000` (đúng giao thức SDCP, không cần mDNS) |
| **Multi-printer** | Lưu nhiều máy, switch active |
| **Dashboard** | Trạng thái in, progress, layer N/N, ETA, tốc độ — gom luôn cả chỉnh nhiệt/quạt/đèn |
| **Nhiệt độ** | Set Nozzle / Hotbed / Buồng riêng + nút "Tắt tất cả heater" |
| **Quạt** | 3 slider Model / Phụ / Buồng (0-100%) |
| **Đèn** | Toggle đèn buồng ON/OFF |
| **Control** | Pause / Resume / Stop, slider tốc độ in 50-200%, Jog X/Y/Z 1·5·10·50mm, Home riêng X/Y/Z hoặc XYZ |
| **Camera** | MJPEG stream từ `http://<host>:3031/video` (bật/tắt qua Cmd 386, parse `VideoUrl` từ response) |
| **Files** | List `/local`, mở folder, **In** ngay từ app (Cmd 128), xóa file (Cmd 259) |
| **Log tab** | Wire log realtime mọi gói gửi/nhận, filter OUT/IN/STATUS, copy clipboard — debug protocol cực nhanh |

## Screenshots

_(thêm sau khi run thực tế trên máy)_

## Yêu cầu

- Android 8.0 (API 26) trở lên — đã verify trên Android 13
- Cùng mạng LAN với máy in (UDP broadcast cần subnet chung)
- Máy in firmware Centauri Carbon V1.1.x+ (SDCP V3.0.0)

## Cài đặt

Tải APK debug mới nhất từ [**Releases**](../../releases/latest) → `adb install` hoặc copy vào điện thoại rồi mở (allow "Install from unknown sources").

```bash
adb install -r CentauriPilot-*.apk
```

## Build từ source

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

Cần: JDK 21 (full, có `javac`), Android SDK platforms `android-35`, build-tools `35.0.0+`.

## Giao thức (đã reverse-engineer)

Toàn bộ chi tiết trong [`docs/SDCP_PROTOCOL.md`](docs/SDCP_PROTOCOL.md):
- Discovery UDP M99999
- WebSocket `ws://<host>:3030/websocket` envelope
- Toàn bộ Cmd codes (status, control, files, axis, light, fan, temp, video, history)
- Payload format từng lệnh

## Roadmap

- [x] M1: Status + Control + Camera + File list/start/delete + Discovery + Log
- [ ] M2: Upload G-code (chunked Cmd 128/255), file detail thumbnail, history list & timelapse video download, notification khi xong/lỗi (foreground service)
- [ ] M3: Multi-printer dashboard view, charts (temp curves history), backup/restore settings

## Đóng góp

PR welcome. Vui lòng giữ:
- Style: Kotlin 2.0+, Compose Material 3, dark theme only
- Không thêm dependency nặng (current app ~17MB)
- Bất kỳ Cmd code mới nào → ghi vào `docs/SDCP_PROTOCOL.md`

## Disclaimer

Đây là client không chính thức, không liên kết với ELEGOO hay Chitubox. Tự chịu rủi ro khi điều khiển máy in qua mạng.

## License

[MIT](LICENSE)
