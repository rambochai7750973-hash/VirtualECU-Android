package com.virtualecu.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.virtualecu.android.api.RetrofitClient
import com.virtualecu.android.model.PeriodicMessage
import com.virtualecu.android.model.PidInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
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
    val baseIp: String = "192.168.4.1",
    val rawPidsResponse: String = "",
    val rawStatsResponse: String = "",
    val rawPeriodicResponse: String = ""
)

class ECUViewModel : ViewModel() {

    private val _state = MutableStateFlow(ECUState())
    val state: StateFlow<ECUState> = _state.asStateFlow()
    private val gson = Gson()

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
                val bodyString = safeReadBody(RetrofitClient.getApi().getPids())
                parsePids(bodyString)
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
        _state.value = _state.value.copy(
            connected = false, pids = emptyList(), error = null,
            rawPidsResponse = "", rawStatsResponse = "", rawPeriodicResponse = ""
        )
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

            val pidsRaw = safeReadBody(api.getPids())
            parsePids(pidsRaw)

            val periodicRaw = safeReadBody(api.getPeriodic())
            parsePeriodic(periodicRaw)

            val statsRaw = safeReadBody(api.getStats())
            parseStats(statsRaw)

            _state.value = _state.value.copy(connected = true, error = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Poll error: ${formatError(e)}",
                connected = false
            )
            pollingJob?.cancel()
        }
    }

    private fun parsePids(raw: String) {
        _state.value = _state.value.copy(rawPidsResponse = raw)
        try {
            val root = JsonParser.parseString(raw)
            val obj = when {
                root.isJsonObject -> root.asJsonObject
                root.isJsonArray -> {
                    val map = JsonObject()
                    root.asJsonArray.forEach { item ->
                        val el = item.asJsonObject
                        val pid = el.get("pid")?.asString ?: return@forEach
                        val value = el.get("value")?.asFloat ?: el.get("v")?.asFloat ?: return@forEach
                        map.addProperty(pid, value)
                    }
                    map
                }
                else -> JsonObject()
            }

            val pidsCombined = pidDefinitions.map { def ->
                val value = extractPidValue(obj, def.pid)
                def.copy(value = value)
            }
            _state.value = _state.value.copy(pids = pidsCombined)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "PID parse error: ${e.localizedMessage}")
        }
    }

    private fun extractPidValue(obj: JsonObject, pid: String): Float {
        obj.get(pid)?.let { return it.asFloat }
        obj.get(pid.trimStart('0'))?.let { return it.asFloat }
        val pidInt = pid.toIntOrNull()
        if (pidInt != null) obj.get(pidInt.toString())?.let { return it.asFloat }
        obj.entrySet().forEach { (key, value) ->
            if (key.equals(pid, ignoreCase = true)) return value.asFloat
            if (key.trimStart('0') == pid.trimStart('0')) return value.asFloat
        }
        return 0f
    }

    private fun parsePeriodic(raw: String) {
        _state.value = _state.value.copy(rawPeriodicResponse = raw)
        try {
            val root = JsonParser.parseString(raw)
            val msgs: List<PeriodicMessage> = when {
                root.isJsonArray -> {
                    root.asJsonArray.map { el ->
                        val obj = el.asJsonObject
                        PeriodicMessage(
                            id = obj.get("id")?.asString ?: "",
                            name = obj.get("name")?.asString ?: obj.get("desc")?.asString ?: "",
                            interval = obj.get("interval")?.asInt ?: obj.get("ms")?.asInt ?: 0,
                            enabled = obj.get("enabled")?.asBoolean ?: obj.get("active")?.asBoolean ?: false
                        )
                    }
                }
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    val messagesArr = obj.getAsJsonArray("messages")
                        ?: obj.getAsJsonArray("periodic")
                        ?: obj.getAsJsonArray("list")
                    messagesArr?.map { el ->
                        val o = el.asJsonObject
                        PeriodicMessage(
                            id = o.get("id")?.asString ?: "",
                            name = o.get("name")?.asString ?: "",
                            interval = o.get("interval")?.asInt ?: 0,
                            enabled = o.get("enabled")?.asBoolean ?: true
                        )
                    } ?: emptyList()
                }
                else -> emptyList()
            }
            _state.value = _state.value.copy(messages = msgs)
        } catch (_: Exception) { }
    }

    private fun parseStats(raw: String) {
        _state.value = _state.value.copy(rawStatsResponse = raw)
        try {
            val obj = JsonParser.parseString(raw).asJsonObject
            _state.value = _state.value.copy(
                txCount = obj.get("txCount")?.asLong ?: obj.get("tx")?.asLong ?: obj.get("txcount")?.asLong ?: 0,
                rxCount = obj.get("rxCount")?.asLong ?: obj.get("rx")?.asLong ?: obj.get("rxcount")?.asLong ?: 0,
                uptime = obj.get("uptime")?.asLong ?: obj.get("uptimeSeconds")?.asLong ?: obj.get("up")?.asLong ?: 0
            )
        } catch (_: Exception) { }
    }

    private suspend fun safeReadBody(body: ResponseBody): String {
        return try {
            body.string()
        } catch (e: Exception) {
            throw Exception("Failed to read response: ${e.localizedMessage}")
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
            is JsonSyntaxException -> "Invalid JSON: ${e.localizedMessage}"
            else -> "${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun setPidOverride(pid: String, value: Float) {
        viewModelScope.launch {
            try { RetrofitClient.getApi().setPid(pid, value) } catch (_: Exception) { }
        }
    }

    fun toggleAllMessages(enabled: Boolean) {
        viewModelScope.launch {
            try { RetrofitClient.getApi().toggleAll(if (enabled) 1 else 0) } catch (_: Exception) { }
        }
    }

    fun toggleMessage(index: Int) {
        viewModelScope.launch {
            try { RetrofitClient.getApi().toggleMessage(index) } catch (_: Exception) { }
        }
    }

    fun fetchLog() {
        viewModelScope.launch {
            try {
                val logText = safeReadBody(RetrofitClient.getApi().getLog())
                val cleaned = logText
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .trim('"')
                _state.value = _state.value.copy(log = cleaned)
            } catch (_: Exception) { }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            try { RetrofitClient.getApi().clearLog() } catch (_: Exception) { }
            _state.value = _state.value.copy(log = "")
        }
    }

    fun refreshPids() {
        viewModelScope.launch {
            try {
                val raw = safeReadBody(RetrofitClient.getApi().getPids())
                parsePids(raw)
            } catch (_: Exception) { }
        }
    }
}
