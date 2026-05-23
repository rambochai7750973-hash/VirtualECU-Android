package com.virtualecu.android.model

import com.google.gson.annotations.SerializedName

data class PidInfo(
    val pid: String,
    val name: String,
    val unit: String,
    val value: Float,
    val min: Float,
    val max: Float
)

data class PidResponse(
    @SerializedName("04") val engineLoad: Float = 0f,
    @SerializedName("05") val coolantTemp: Float = 0f,
    @SerializedName("0B") val intakePress: Float = 0f,
    @SerializedName("0C") val engineRpm: Float = 0f,
    @SerializedName("0D") val vehicleSpeed: Float = 0f,
    @SerializedName("0F") val intakeAirTemp: Float = 0f,
    @SerializedName("10") val maf: Float = 0f,
    @SerializedName("11") val throttlePos: Float = 0f,
    @SerializedName("21") val distanceDtc: Float = 0f,
    @SerializedName("2F") val fuelLevel: Float = 0f,
    @SerializedName("31") val odometer: Float = 0f,
    @SerializedName("46") val ambientTemp: Float = 0f,
    @SerializedName("5C") val oilTemp: Float = 0f
)

data class PeriodicMessage(
    val id: String,
    val name: String,
    val interval: Int,
    val enabled: Boolean
)

data class PeriodicResponse(
    val messages: List<PeriodicMessage>
)

data class StatsResponse(
    val txCount: Long,
    val rxCount: Long,
    val uptime: Long
)

data class LogResponse(
    val log: String
)

data class ToggleResponse(
    val success: Boolean
)
