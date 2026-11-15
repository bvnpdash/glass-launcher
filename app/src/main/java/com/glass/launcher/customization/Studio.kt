package com.glass.launcher.customization

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.*
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import com.glass.launcher.ui.*

private val TABS = listOf("Glass", "Layout", "Wallpaper", "Gestures")

@Composable
fun CustomizationStudio(vm: LauncherViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GText("Studio", 26, FontWeight.Light, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).glass(GlassMaterial.CLEAR, Dp.Infinity, 4.dp).pointerInput(Unit) { detectTapGestures { onClose() } },
                contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Close", tint = LocalGlass.current.text, modifier = Modifier.size(20.dp)) }
        }

        LivePreview(vm, Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(14.dp))

        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TABS.forEachIndexed { i, n -> GlassPill(n, selected = tab == i) { tab = i } }
        }
        Spacer(Modifier.height(6.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (tab) {
                0 -> GlassTab(vm, s)
                1 -> LayoutTab(vm, s)
                2 -> WallpaperTab(vm, s)
                else -> GestureTab(vm, s)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- live preview
@Composable
private fun LivePreview(vm: LauncherViewModel, modifier: Modifier) {
    val s by vm.settings.collectAsState()
    val apps by vm.apps.collectAsState()
    val shown = remember(s.homeApps, s.dockApps, apps) {
        (s.homeApps + s.dockApps).mapNotNull { vm.byKey(it) }.distinct().take(8)
    }
    Column(modifier.fillMaxWidth().height(190.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
        .border(1.dp, Color.White.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        ClockBlockPreview(vm)
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(bottom = 12.dp).glass(s.dockMaterial, Dp.Infinity, 10.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            shown.take(s.dockCount).forEach { AppIcon(it, vm, 34.dp, showLabel = false, tile = false) }
        }
    }
}

@Composable private fun ClockBlockPreview(vm: LauncherViewModel) {
    // scaled-down clock: same component, so the style toggle is visible instantly
    Box(Modifier.height(100.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.wrapContentHeight(unbounded = true).scale(0.6f)) { ClockBlock(vm, null) }
    }
}


// ---------------------------------------------------------------- tabs
@Composable
private fun GlassTab(vm: LauncherViewModel, s: GlassSettings) {
    SliderRow("Transparency", s.transparency, 0f..1f, "Clearer", "Denser") { v -> vm.edit { it.copy(transparency = v) } }
    SliderRow("Blur intensity", s.blur, 0f..1f, "Sharp", "Soft") { v -> vm.edit { it.copy(blur = v) } }
    SliderRow("Border intensity", s.border, 0f..1f, "Subtle", "Bright") { v -> vm.edit { it.copy(border = v) } }
    SliderRow("Accent lighting", s.accentHue, 0f..360f, "", "", hue = true) { v -> vm.edit { it.copy(accentHue = v) } }
    Section("Materials")
    MaterialRow("Dock", s.dockMaterial) { m -> vm.edit { it.copy(dockMaterial = m) } }
    MaterialRow("Cards", s.cardMaterial) { m -> vm.edit { it.copy(cardMaterial = m) } }
    Section("Appearance")
    ChipRow(listOf("System", "Light", "Dark"), s.appearance) { i -> vm.edit { it.copy(appearance = i) } }
    SwitchRow("Glass frame around widgets", s.glassWidgets) { v -> vm.edit { it.copy(glassWidgets = v) } }
    SliderRow("Animation speed", s.animSpeed, 0.5f..2f, "Calm", "Quick") { v -> vm.edit { it.copy(animSpeed = v) } }
    SwitchRow("Liquid motion & ambient light", s.ambientFx) { v -> vm.edit { it.copy(ambientFx = v) } }
}

@Composable
private fun LayoutTab(vm: LauncherViewModel, s: GlassSettings) {
    val packs by vm.iconPacks.collectAsState()
    Section("Clock")
    ChipRow(listOf("Thin", "Stacked", "Capsule"), s.clockStyle) { i -> vm.edit { it.copy(clockStyle = i) } }
    SliderRow("Icon size", s.iconSize, 0.7f..1.3f, "Small", "Large") { v -> vm.edit { it.copy(iconSize = v) } }
    Section("Grid columns")
    ChipRow(listOf("3", "4", "5", "6"), s.gridCols - 3) { i -> vm.edit { it.copy(gridCols = i + 3) } }
    Section("Grid rows")
    ChipRow(listOf("3", "4", "5", "6"), s.gridRows - 3) { i -> vm.edit { it.copy(gridRows = i + 3) } }
    Section("Dock apps")
    ChipRow(listOf("4", "5"), s.dockCount - 4) { i -> vm.edit { it.copy(dockCount = i + 4) } }
    Section("Icon pack")
    val names = listOf("System icons") + packs.map { it.label }
    val sel = if (s.iconPack.isEmpty()) 0 else packs.indexOfFirst { it.packageName == s.iconPack } + 1
    ChipRow(names, sel.coerceAtLeast(0)) { i -> vm.edit { it.copy(iconPack = if (i == 0) "" else packs[i - 1].packageName) } }
    if (packs.isEmpty()) GText("No ADW/Nova-compatible icon packs installed.", 12, dim = true)
}

@Composable
private fun WallpaperTab(vm: LauncherViewModel, s: GlassSettings) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.edit { it.copy(wallpaperUri = uri.toString(), useSystemWallpaper = false) }
        }
    }
    Section("Source")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassPill("Choose image") { pick.launch(arrayOf("image/*")) }
        GlassPill("System wallpaper", selected = s.useSystemWallpaper && s.wallpaperUri.isEmpty()) {
            vm.edit { it.copy(wallpaperUri = "", useSystemWallpaper = true) }
        }
        GlassPill("Aurora", selected = !s.useSystemWallpaper && s.wallpaperUri.isEmpty()) {
            vm.edit { it.copy(wallpaperUri = "", useSystemWallpaper = false) }
        }
    }
    SliderRow("Dimming", s.dim, 0f..0.7f, "None", "Dark") { v -> vm.edit { it.copy(dim = v) } }
    SwitchRow("Parallax", s.parallax) { v -> vm.edit { it.copy(parallax = v) } }
}

@Composable
private fun GestureTab(vm: LauncherViewModel, s: GlassSettings) {
    GText("Tap a gesture to cycle its action.", 12, dim = true)
    GestureRow("Swipe up", s.swipeUp) { a -> vm.edit { it.copy(swipeUp = a) } }
    GestureRow("Swipe down", s.swipeDown) { a -> vm.edit { it.copy(swipeDown = a) } }
    GestureRow("Swipe left", s.swipeLeft) { a -> vm.edit { it.copy(swipeLeft = a) } }
    GestureRow("Swipe right", s.swipeRight) { a -> vm.edit { it.copy(swipeRight = a) } }
    GestureRow("Double tap", s.doubleTap) { a -> vm.edit { it.copy(doubleTap = a) } }
    GestureRow("Long press", s.longPress) { a -> vm.edit { it.copy(longPress = a) } }
    GestureRow("Pinch", s.pinch) { a -> vm.edit { it.copy(pinch = a) } }
    val ctx = LocalContext.current
    Spacer(Modifier.height(8.dp))
    GlassPill("Enable lock-screen service (for double tap)") {
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    GText("Double-tap lock needs Android 9+ and the accessibility service. It never reads your screen.", 11, dim = true)
}

// ---------------------------------------------------------------- controls
@Composable private fun Section(text: String) =
    GText(text.uppercase(), 11, FontWeight.Medium, dim = true, spacing = 1.8f, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, lo: String, hi: String, hue: Boolean = false, onChange: (Float) -> Unit) {
    val t = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        GText(label, 14, FontWeight.Medium)
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(
            thumbColor = if (hue) Color.hsv(value, 0.55f, 1f) else t.accent, activeTrackColor = t.accent.copy(alpha = 0.8f),
            inactiveTrackColor = Color.White.copy(alpha = 0.16f)))
        if (!hue) Row(Modifier.fillMaxWidth()) { GText(lo, 11, dim = true, modifier = Modifier.weight(1f)); GText(hi, 11, dim = true) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GText(label, 14, FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = t.accent.copy(alpha = 0.8f), checkedThumbColor = Color.White))
    }
}

@Composable
private fun ChipRow(items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, n -> GlassPill(n, selected = i == selected) { onPick(i) } }
    }
}

@Composable
private fun MaterialRow(label: String, current: GlassMaterial, onPick: (GlassMaterial) -> Unit) {
    GText(label, 14, FontWeight.Medium)
    ChipRow(GlassMaterial.values().map { it.label }, current.ordinal) { onPick(GlassMaterial.values()[it]) }
}

@Composable
private fun GestureRow(label: String, current: GestureAction, onPick: (GestureAction) -> Unit) {
    val all = GestureAction.values()
    Row(Modifier.fillMaxWidth().glass(GlassMaterial.CLEAR, 18.dp, 4.dp, touchReactive = false)
        .pointerInput(current) { detectTapGestures { onPick(all[(current.ordinal + 1) % all.size]) } }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GText(label, 14, FontWeight.Medium, modifier = Modifier.weight(1f))
        GText(current.label, 14, color = LocalGlass.current.accent)
    }
}
