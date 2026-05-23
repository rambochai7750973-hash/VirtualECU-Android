package com.virtualecu.android.model

data class RawPidEntry(
    val key: String,
    val rawValue: String,
    val displayName: String
)

data class PeriodicMessage(
    val id: String,
    val name: String,
    val interval: Int,
    val enabled: Boolean
)
