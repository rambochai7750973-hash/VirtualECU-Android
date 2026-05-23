package com.virtualecu.android.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface ECUApi {

    @GET("/api/pids")
    suspend fun getPids(): ResponseBody

    @GET("/api/pid")
    suspend fun setPid(
        @Query("pid") pid: String,
        @Query("value") value: Float
    ): ResponseBody

    @GET("/api/periodic")
    suspend fun getPeriodic(): ResponseBody

    @GET("/api/periodic/{index}/toggle")
    suspend fun toggleMessage(@retrofit2.http.Path("index") index: Int): ResponseBody

    @GET("/api/periodic/all")
    suspend fun toggleAll(
        @Query("enabled") enabled: Int
    ): ResponseBody

    @GET("/api/stats")
    suspend fun getStats(): ResponseBody

    @GET("/api/log")
    suspend fun getLog(): ResponseBody

    @GET("/api/log/clear")
    suspend fun clearLog(): ResponseBody
}
