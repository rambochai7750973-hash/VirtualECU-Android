package com.virtualecu.android.api

import com.virtualecu.android.model.LogResponse
import com.virtualecu.android.model.PeriodicResponse
import com.virtualecu.android.model.PidResponse
import com.virtualecu.android.model.StatsResponse
import com.virtualecu.android.model.ToggleResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ECUApi {

    @GET("/api/pids")
    suspend fun getPids(): PidResponse

    @GET("/api/pid")
    suspend fun setPid(
        @Query("pid") pid: String,
        @Query("value") value: Float
    ): ToggleResponse

    @GET("/api/periodic")
    suspend fun getPeriodic(): PeriodicResponse

    @GET("/api/periodic/all")
    suspend fun toggleAll(
        @Query("enabled") enabled: Int
    ): ToggleResponse

    @GET("/api/stats")
    suspend fun getStats(): StatsResponse

    @GET("/api/log")
    suspend fun getLog(): LogResponse

    @GET("/api/log/clear")
    suspend fun clearLog(): ToggleResponse
}
