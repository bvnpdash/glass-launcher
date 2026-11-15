package com.glass.launcher.dashboard

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Process
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

data class WeatherInfo(val tempC: Int, val label: String)
data class BatteryInfo(val percent: Int, val charging: Boolean)
data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)
data class UsageInfo(val totalMinutes: Int, val topPackages: List<String>)

/** Pull-based and lazy: nothing runs in the background. Called only while a screen showing it is visible. */
class DashboardRepository(private val ctx: Context) {
    val weather = MutableStateFlow<WeatherInfo?>(null)
    val battery = MutableStateFlow(BatteryInfo(100, false))
    val events = MutableStateFlow<List<CalEvent>>(emptyList())
    val usage = MutableStateFlow<UsageInfo?>(null)
    private var weatherAt = 0L

    fun hasLocation() = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasCalendar() = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun hasUsage(): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun readBattery() {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        battery.value = BatteryInfo((level * 100f / scale).toInt(),
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        readBattery()
        if (hasCalendar()) runCatching { events.value = queryEvents() }
        if (hasUsage()) runCatching { usage.value = queryUsage() }
        if (hasLocation() && System.currentTimeMillis() - weatherAt > 30 * 60_000L) runCatching { fetchWeather() }
    }

    private fun queryEvents(): List<CalEvent> {
        val now = System.currentTimeMillis()
        val out = ArrayList<CalEvent>()
        val c = CalendarContract.Instances.query(ctx.contentResolver,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
            now, now + 36L * 3600_000L)
        c.use {
            while (it.moveToNext()) out += CalEvent(it.getString(0) ?: "Event", it.getLong(1), it.getLong(2), it.getInt(3) == 1)
        }
        return out.sortedBy { it.begin }.take(3)
    }

    private fun queryUsage(): UsageInfo {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis())
            .filter { it.totalTimeInForeground > 0 && it.packageName != ctx.packageName }
        val merged = stats.groupBy { it.packageName }.mapValues { e -> e.value.sumOf { it.totalTimeInForeground } }
        return UsageInfo((merged.values.sum() / 60_000L).toInt(), merged.entries.sortedByDescending { it.value }.take(6).map { it.key })
    }

    private fun fetchWeather() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() } ?: return
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${"%.2f".format(java.util.Locale.US, loc.latitude)}" +
            "&longitude=${"%.2f".format(java.util.Locale.US, loc.longitude)}&current=temperature_2m,weather_code&timezone=auto")
        val con = url.openConnection() as HttpURLConnection
        con.connectTimeout = 6000; con.readTimeout = 6000
        try {
            val cur = JSONObject(con.inputStream.bufferedReader().readText()).getJSONObject("current")
            weather.value = WeatherInfo(Math.round(cur.getDouble("temperature_2m")).toInt(), label(cur.getInt("weather_code")))
            weatherAt = System.currentTimeMillis()
        } finally { con.disconnect() }
    }

    private fun label(code: Int) = when (code) {
        0 -> "Clear"; 1, 2 -> "Mostly clear"; 3 -> "Overcast"; 45, 48 -> "Fog"
        in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"
        in 95..99 -> "Thunderstorm"; else -> "Cloudy"
    }
}
