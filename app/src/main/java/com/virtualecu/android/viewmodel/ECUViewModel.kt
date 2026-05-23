package com.virtualecu.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.virtualecu.android.api.RetrofitClient
import com.virtualecu.android.model.PeriodicMessage
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

data class RawPidEntry(
    val key: String,
    val rawValue: String,
    val displayName: String
)

data class ECUState(
    val pids: List<RawPidEntry> = emptyList(),
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
    private val gson = Gson()

    private val knownPids = mapOf(
        "04" to "Engine Load",   "05" to "Coolant Temp",
        "0B" to "Intake Press",  "0C" to "Engine RPM",
        "0D" to "Vehicle Speed", "0F" to "Intake Air T",
        "10" to "MAF",           "11" to "Throttle Pos",
        "21" to "Distance DTC",  "2F" to "Fuel Level",
        "31" to "Odometer",      "46" to "Ambient Temp",
        "5C" to "Oil Temp"
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
                val raw = safeReadBody(RetrofitClient.getApi().getPids())
                parsePids(raw)
                _state.value = _state.value.copy(connected = true, loading = false, error = null)
                startPolling()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    connected = false, loading = false, error = formatError(e)
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
            parsePids(safeReadBody(api.getPids()))

            val periodicRaw = safeReadBody(api.getPeriodic())
            parsePeriodic(periodicRaw)

            val statsRaw = safeReadBody(api.getStats())
            parseStats(statsRaw)

            _state.value = _state.value.copy(connected = true, error = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Poll error: ${formatError(e)}", connected = false
            )
            pollingJob?.cancel()
        }
    }

    private fun parsePids(raw: String) {
        try {
            val root: JsonElement = JsonParser().parse(raw)
            val entries = mutableListOf<RawPidEntry>()

            when {
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    for (key in obj.keySet()) {
                        val value = extractNumber(obj.get(key))
                        val display = knownPids[key] ?: knownPids[key.trimStart('0')] ?: key
                        entries.add(RawPidEntry(key, value, display))
                    }
                }
                root.isJsonArray -> {
                    val arr = root.asJsonArray
                    for (i in 0 until arr.size()) {
                        val el = arr[i]
                        when {
                            el.isJsonObject -> {
                                val item = el.asJsonObject
                                val key = item.get("pid")?.asString
                                    ?: item.get("id")?.asString
                                    ?: item.get("key")?.asString
                                    ?: i.toString()
                                val value = extractNumber(
                                    item.get("value") ?: item.get("v")
                                        ?: item.get("val") ?: item.get("data")
                                )
                                val display = knownPids[key] ?: knownPids[key.trimStart('0')] ?: key
                                entries.add(RawPidEntry(key, value, display))
                            }
                            el.isJsonPrimitive -> {
                                entries.add(RawPidEntry(i.toString(), el.asString, "Item $i"))
                            }
                        }
                    }
                }
            }

            entries.sortBy { it.key }
            _state.value = _state.value.copy(pids = entries)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                pids = listOf(RawPidEntry("error", e.localizedMessage ?: "Parse error", "Parse Error")),
                error = "PID parse: ${e.localizedMessage}"
            )
        }
    }

    private fun extractNumber(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return "--"
        return try {
            when {
                el.isJsonPrimitive -> {
                    val p = el.asJsonPrimitive
                    when {
                        p.isNumber -> {
                            val d = p.asDouble
                            if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
                        }
                        p.isString -> {
                            val s = p.asString
                            s.toDoubleOrNull()?.let { d ->
                                if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.1f", d)
                            } ?: s
                        }
                        else -> p.asString
                    }
                }
                else -> el.toString()
            }
        } catch (_: Exception) { el.toString() }
    }

    private fun parsePeriodic(raw: String) {
        try {
            val root: JsonElement = JsonParser().parse(raw)
            val msgs = mutableListOf<PeriodicMessage>()

            when {
                root.isJsonArray -> {
                    val arr = root.asJsonArray
                    for (i in 0 until arr.size()) {
                        val o = arr[i].asJsonObject
                        msgs.add(PeriodicMessage(
                            id = getStr(o, "id", ""),
                            name = getStr(o, "name", getStr(o, "desc", "")),
                            interval = getInt(o, "interval", getInt(o, "ms", 0)),
                            enabled = getBool(o, "enabled", getBool(o, "active", false))
                        ))
                    }
                }
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    val arr = obj.getAsJsonArray("messages")
                        ?: obj.getAsJsonArray("periodic")
                        ?: obj.getAsJsonArray("list")
                    if (arr != null) {
                        for (i in 0 until arr.size()) {
                            val o = arr[i].asJsonObject
                            msgs.add(PeriodicMessage(
                                id = getStr(o, "id", ""),
                                name = getStr(o, "name", ""),
                                interval = getInt(o, "interval", 0),
                                enabled = getBool(o, "enabled", true)
                            ))
                        }
                    }
                }
            }
            _state.value = _state.value.copy(messages = msgs)
        } catch (_: Exception) { }
    }

    private fun parseStats(raw: String) {
        try {
            val obj = JsonParser().parse(raw).asJsonObject
            _state.value = _state.value.copy(
                txCount = getLong(obj, "txCount", getLong(obj, "tx", getLong(obj, "txcount", 0L))),
                rxCount = getLong(obj, "rxCount", getLong(obj, "rx", getLong(obj, "rxcount", 0L))),
                uptime = getLong(obj, "uptime", getLong(obj, "uptimeSeconds", getLong(obj, "up", 0L)))
            )
        } catch (_: Exception) { }
    }

    private fun getStr(o: JsonObject, key: String, fallback: String): String {
        val el = o.get(key)
        return if (el != null && !el.isJsonNull) el.asString else fallback
    }

    private fun getInt(o: JsonObject, key: String, fallback: Int): Int {
        val el = o.get(key)
        return if (el != null && !el.isJsonNull) el.asInt else fallback
    }

    private fun getLong(o: JsonObject, key: String, fallback: Long): Long {
        val el = o.get(key)
        return if (el != null && !el.isJsonNull) el.asLong else fallback
    }

    private fun getBool(o: JsonObject, key: String, fallback: Boolean): Boolean {
        val el = o.get(key)
        return if (el != null && !el.isJsonNull) el.asBoolean else fallback
    }

    private suspend fun safeReadBody(body: ResponseBody): String {
        return try {
            body.string()
        } catch (e: Exception) {
            throw Exception("Read response failed: ${e.localizedMessage}")
        }
    }

    private fun formatError(e: Exception): String {
        return when (e) {
            is HttpException -> {
                val code = e.code()
                val body = try { e.response()?.errorBody()?.string() ?: "no body" } catch (_: Exception) { "unreadable" }
                "HTTP $code: $body"
            }
            is SocketTimeoutException -> "Timeout. Check if ECU is powered on."
            is ConnectException -> "Connection refused. Check IP."
            is UnknownHostException -> "Unknown host. Check IP."
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

    fun fetchLog() {
        viewModelScope.launch {
            try {
                val raw = safeReadBody(RetrofitClient.getApi().getLog())
                val cleaned = raw.replace("\\n", "\n").replace("\\\"", "\"").trim('"')
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
}
