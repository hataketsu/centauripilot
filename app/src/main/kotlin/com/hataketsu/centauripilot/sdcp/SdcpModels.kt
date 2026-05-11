package com.hataketsu.centauripilot.sdcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SdcpEnvelope(
    @SerialName("Id") val id: String = "",
    @SerialName("Data") val data: JsonElement? = null,
    @SerialName("Topic") val topic: String = "",
    @SerialName("Status") val status: StatusBlock? = null,
    @SerialName("Attributes") val attributes: JsonElement? = null,
    @SerialName("MainboardID") val mainboardId: String? = null,
    @SerialName("TimeStamp") val timeStamp: Long? = null
)

@Serializable
data class StatusBlock(
    @SerialName("CurrentStatus") val currentStatus: List<Int> = emptyList(),
    @SerialName("TempOfHotbed") val tempOfHotbed: Double = 0.0,
    @SerialName("TempOfNozzle") val tempOfNozzle: Double = 0.0,
    @SerialName("TempOfBox") val tempOfBox: Double = 0.0,
    @SerialName("TempTargetHotbed") val tempTargetHotbed: Double = 0.0,
    @SerialName("TempTargetNozzle") val tempTargetNozzle: Double = 0.0,
    @SerialName("TempTargetBox") val tempTargetBox: Double = 0.0,
    @SerialName("CurrenCoord") val currentCoord: String = "0,0,0",
    @SerialName("ZOffset") val zOffset: Double = 0.0,
    @SerialName("CurrentFanSpeed") val currentFanSpeed: FanSpeed = FanSpeed(),
    @SerialName("LightStatus") val lightStatus: LightStatus = LightStatus(),
    @SerialName("PrintInfo") val printInfo: PrintInfo = PrintInfo(),
    @SerialName("TimeLapseStatus") val timeLapseStatus: Int = 0,
    @SerialName("PlatFormType") val platformType: Int = 0
)

@Serializable
data class FanSpeed(
    @SerialName("ModelFan") val modelFan: Int = 0,
    @SerialName("AuxiliaryFan") val auxiliaryFan: Int = 0,
    @SerialName("BoxFan") val boxFan: Int = 0
)

@Serializable
data class LightStatus(
    @SerialName("SecondLight") val secondLight: Int = 0,
    @SerialName("RgbLight") val rgbLight: List<Int> = listOf(0, 0, 0)
)

@Serializable
data class PrintInfo(
    @SerialName("Status") val status: Int = 0,
    @SerialName("CurrentLayer") val currentLayer: Int = 0,
    @SerialName("TotalLayer") val totalLayer: Int = 0,
    @SerialName("CurrentTicks") val currentTicks: Double = 0.0,
    @SerialName("TotalTicks") val totalTicks: Double = 0.0,
    @SerialName("Filename") val filename: String = "",
    @SerialName("TaskId") val taskId: String = "",
    @SerialName("PrintSpeedPct") val printSpeedPct: Int = 100,
    @SerialName("Progress") val progress: Int = 0
)

object PrintStatusCode {
    fun label(code: Int): String = when (code) {
        0 -> "Idle"
        1 -> "Homing"
        2 -> "Dropping Sculpt"
        3 -> "Exposuring"
        4 -> "Lifting"
        5 -> "Pausing"
        6 -> "Paused"
        7 -> "Stopping"
        8 -> "Stopped"
        9 -> "Complete"
        10 -> "File Checking"
        13 -> "Printing"
        16 -> "Print Calibrating"
        17 -> "Auto Bed Leveling"
        18 -> "Heating"
        19 -> "Preheating"
        20 -> "Resuming"
        else -> "Status $code"
    }
}

object CurrentStatusCode {
    fun label(code: Int): String = when (code) {
        0 -> "Idle"
        1 -> "Printing"
        2 -> "File Transferring"
        3 -> "Exposure Testing"
        4 -> "Devices Testing"
        else -> "S$code"
    }
}
