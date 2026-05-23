package com.virtualecu.android.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var baseUrl = "http://192.168.4.1"
    private var lastError: String? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept", "application/json, text/plain, */*")
                .build()
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                lastError = "Network unreachable: ${e.localizedMessage}"
                throw e
            }
        }
        .build()

    private var retrofit: Retrofit = buildRetrofit()

    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun getApi(): ECUApi = retrofit.create(ECUApi::class.java)

    fun updateBaseUrl(ip: String) {
        baseUrl = if (ip.startsWith("http")) ip else "http://$ip"
        retrofit = buildRetrofit()
        lastError = null
    }

    fun getCurrentBaseUrl(): String = baseUrl
    fun getLastError(): String? = lastError
}
