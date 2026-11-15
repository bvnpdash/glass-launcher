package com.glass.launcher.dashboard

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import com.glass.launcher.ui.*
import java.text.DateFormat
import java.util.Date

@Composable
fun Dashboard(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val t = LocalGlass.current
    val s by vm.settings.collectAsState()
    val dash = vm.dash
    val weather by dash.weather.collectAsState()
    val battery by dash.battery.collectAsState()
    val events by dash.events.collectAsState()
    val usage by dash.usage.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // refresh only while this page is on screen and the app is in front
    LaunchedEffect(tick) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { dash.refresh(); kotlinx.coroutines.delay(5 * 60_000L) }
        }
    }
    val askLoc = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val askCal = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val frequent = remember(s.recentLaunches, usage) {
        val u = usage?.topPackages?.mapNotNull { p -> vm.apps.value.firstOrNull { it.packageName == p } } ?: emptyList()
        (u + s.recentLaunches.mapNotNull { vm.byKey(it) }).distinctBy { it.key }.take(4)
    }
    val card = s.cardMaterial

    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GText("Today", 26, FontWeight.Light, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

        // weather + battery
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).glass(card, 26.dp).padding(18.dp)) {
                val w = weather
                if (w != null) {
                    GText("${w.tempC}°", 44, FontWeight.ExtraLight); GText(w.label, 13, dim = true)
                } else if (!dash.hasLocation()) {
                    Icon(Icons.Default.WbSunny, null, tint = t.accent)
                    Spacer(Modifier.height(6.dp))
                    Tap("Enable weather") { askLoc.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                } else { GText("—", 44, FontWeight.ExtraLight); GText("Waiting for location", 12, dim = true) }
            }
            Column(Modifier.weight(1f).glass(card, 26.dp).padding(18.dp)) {
                Icon(if (battery.charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd, null, tint = t.accent)
                Spacer(Modifier.height(4.dp))
                GText("${battery.percent}%", 32, FontWeight.Light)
                GText(if (battery.charging) "Charging" else "Battery", 12, dim = true)
            }
        }

        // calendar
        Column(Modifier.fillMaxWidth().glass(card, 26.dp).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, null, tint = t.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                GText("Up next", 12, FontWeight.Medium, dim = true, spacing = 1.5f)
            }
            Spacer(Modifier.height(8.dp))
            if (!dash.hasCalendar()) Tap("Allow calendar access") { askCal.launch(Manifest.permission.READ_CALENDAR) }
            else if (events.isEmpty()) GText("Nothing scheduled", 15, dim = true)
            else events.forEach { e ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GText(if (e.allDay) "All day" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.begin)), 13, dim = true, modifier = Modifier.width(76.dp))
                    GText(e.title, 15, FontWeight.Medium)
                }
            }
        }

        // screen time
        Column(Modifier.fillMaxWidth().glass(card, 26.dp).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timelapse, null, tint = t.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                GText("Screen time", 12, FontWeight.Medium, dim = true, spacing = 1.5f)
            }
            Spacer(Modifier.height(6.dp))
            val u = usage
            if (u != null) GText("${u.totalMinutes / 60}h ${u.totalMinutes % 60}m today", 24, FontWeight.Light)
            else Tap("Grant usage access") {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }

        // frequent apps
        if (frequent.isNotEmpty()) Column(Modifier.fillMaxWidth().glass(card, 26.dp).padding(vertical = 16.dp, horizontal = 10.dp)) {
            GText("Frequent", 12, FontWeight.Medium, dim = true, spacing = 1.5f, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                frequent.forEach { AppIcon(it, vm, 48.dp, tile = false, onClick = { vm.launch(it) }) }
            }
        }

        // quick shortcuts
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Shortcut(Icons.Default.CameraAlt, "Camera") { ctx.startIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
            Shortcut(Icons.Default.Alarm, "Clock") { ctx.startIntent(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
            Shortcut(Icons.Default.Wifi, "Wi-Fi") { ctx.startIntent(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            Shortcut(Icons.Default.Settings, "Settings") { ctx.startIntent(Intent(Settings.ACTION_SETTINGS)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun android.content.Context.startIntent(i: Intent) {
    runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun RowScope.Shortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).glass(GlassMaterial.CRYSTAL, 22.dp, 8.dp).pointerInput(Unit) { detectTapGestures { onClick() } }.padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = LocalGlass.current.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp)); GText(label, 11, dim = true)
    }
}

@Composable
private fun Tap(text: String, onClick: () -> Unit) {
    GText(text, 14, FontWeight.Medium, color = LocalGlass.current.accent,
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { onClick() } }.padding(vertical = 4.dp))
}
