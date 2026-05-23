package com.virtualecu.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonSyntaxException
import com.virtualecu.android.api.RetrofitClient
import com.virtualecu.android.model.LogResponse
import com.virtualecu.android.model.PeriodicMessage
import com.virtualecu.android.model.PeriodicResponse
import com.virtualecu.android.model.PidInfo
import com.virtualecu.android.model.PidResponse
import com.virtualecu.android.model.StatsResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class ECUState(
    val pids: List<PidInfo> = emptyList(),
    val messages: List<PeriodicMessage> = emptyList(),
    val txCount: Long = 0,
    val rxCount: Long = 0,
    val uptime: Long = 0,
    val log: String = "",
    val connected: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val baseIp: String = "192.168.4.1"
)

class ECUViewModel : ViewModel() {

    private val _state = MutableStateFlow(ECUState())
    val state: StateFlow<ECUState> = _state.asStateFlow()

    private val pidDefinitions = listOf(
        PidInfo("04", "Engine Load", "%", 0f, 0f, 100f),
        PidInfo("05", "Coolant Temp", "°C", 0f, -40f, 215f),
        PidInfo("0B", "Intake Press", "kPa", 0f, 0f, 255f),
        PidInfo("0C", "Engine RPM", "rpm", 0f, 0f, 8000f),
        PidInfo("0D", "Vehicle Speed", "km/h", 0f, 0f, 255f),
        PidInfo("0F", "Intake Air T", "°C", 0f, -40f, 215f),
        PidInfo("10", "MAF", "g/s", 0f, 0f, 655f),
        PidInfo("11", "Throttle Pos", "%", 0f, 0f, 100f),
        PidInfo("21", "Distance DTC", "km", 0f, 0f, 65535f),
        PidInfo("2F", "Fuel Level", "%", 0f, 0f, 100f),
        PidInfo("31", "Odometer", "km", 0f, 0f, 4.29e9f),
        PidInfo("46", "Ambient Temp", "°C", 0f, -40f, 215f),
        PidInfo("5C", "Oil Temp", "°C", 0f, -40f, 215f)
    )

    private val rawToPidMap: Map<String, (PidResponse) -> Float> = mapOf(
        "04" to { it.engineLoad },
        "05" to { it.coolantTemp },
        "0B" to { it.intakePress },
        "0C" to { it.engineRpm },
        "0D" to { it.vehicleSpeed },
        "0F" to { it.intakeAirTemp },
        "10" to { it.maf },
        "11" to { it.throttlePos },
        "21" to { it.distanceDtc },
        "2F" to { it.fuelLevel },
        "31" to { it.odometer },
        "46" to { it.ambientTemp },
        "5C" to { it.oilTemp }
    )

    private var pollingJob: Job? = null

    fun setBaseIp(ip: String) {
        _state.value = _state.value.copy(baseIp = ip)
        RetrofitClient.updateBaseUrl(ip)
    }

    fun connect() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                RetrofitClient.updateBaseUrl(_state.value.baseIp)
                RetrofitClient.getApi().getPids()
                _state.value = _state.value.copy(connected = true, loading = false, error = null)
                startPolling()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    connected = false,
                    loading = false,
                    error = formatError(e)
                )
            }
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
        _state.value = _state.value.copy(connected = false, pids = emptyList(), error = null)
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                fetchAll()
                delay(1000)
            }
        }
    }

    private suspend fun fetchAll() {
        try {
            val api = RetrofitClient.getApi()

            val pidsResponse: PidResponse = try {
                api.getPids()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "PID fetch failed: ${formatError(e)}")
                return
            }

            val pidsCombined = pidDefinitions.map { def ->
                val value = rawToPidMap[def.pid]?.invoke(pidsResponse) ?: def.value
                def.copy(value = value)
            }

            val periodicResponse: PeriodicResponse = try {
                api.getPeriodic()
            } catch (e: Exception) { PeriodicResponse(emptyList()) }

            val stats: StatsResponse = try {
                api.getStats()
            } catch (e: Exception) { StatsResponse(0, 0, 0) }

            _state.value = _state.value.copy(
                pids = pidsCombined,
                messages = periodicResponse.messages,
                txCount = stats.txCount,
                rxCount = stats.rxCount,
                uptime = stats.uptime,
                connected = true,
                error = null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Poll error: ${formatError(e)}",
                connected = false
            )
            pollingJob?.cancel()
        }
    }

    private fun formatError(e: Exception): String {
        return when (e) {
            is HttpException -> {
                val code = e.code()
                val body = try { e.response()?.errorBody()?.string() ?: "no body" } catch (_: Exception) { "unreadable" }
                "HTTP $code: $body"
            }
            is SocketTimeoutException -> "Connection timed out. Check if ECU is powered on."
            is ConnectException -> "Connection refused. Check IP address."
            is UnknownHostException -> "Unknown host. Check IP address."
            is JsonSyntaxException -> "Invalid response from ECU: ${e.localizedMessage}"
            else -> "${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun setPidOverride(pid: String, value: Float) {
        viewModelScope.launch {
            try {
                RetrofitClient.getApi().setPid(pid, value)
            } catch (_: Exception) { }
        }
    }

    fun toggleAllMessages(enabled: Boolean) {
        viewModelScope.launch {
            try {
                RetrofitClient.getApi().toggleAll(if (enabled) 1 else 0)
            } catch (_: Exception) { }
        }
    }

    fun fetchLog() {
        viewModelScope.launch {
            try {
                val logResponse: LogResponse = RetrofitClient.getApi().getLog()
                _state.value = _state.value.copy(log = logResponse.log)
            } catch (_: Exception) { }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            try {
                RetrofitClient.getApi().clearLog()
                _state.value = _state.value.copy(log = "")
            } catch (_: Exception) { }
        }
    }
}
