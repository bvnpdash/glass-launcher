package com.glass.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.data.WidgetPlacement
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import com.glass.launcher.ui.GText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun WidgetWorkspace(vm: LauncherViewModel, wc: WidgetController, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsState()
    var selected by remember { mutableIntStateOf(-1) }
    BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val cell = (maxWidth - 32.dp) / WIDGET_COLS
        SideEffect { wc.cellDp = cell.value }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                GText("Widgets", 26, FontWeight.Light, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).glass(GlassMaterial.LIQUID, androidx.compose.ui.unit.Dp.Infinity, 6.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { wc.pickerOpen = true }) },
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Add widget", tint = LocalGlass.current.text) }
            }
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { selected = -1 }) }) {
                val rows = maxOf(6, (s.widgets.maxOfOrNull { it.cellY + it.spanY } ?: 0) + 2)
                Box(Modifier.size(cell * WIDGET_COLS, cell * rows)) {
                    s.widgets.forEach { w ->
                        key(w.id) { WidgetCell(vm, wc, w, s.widgets, cell, selected == w.id, s.glassWidgets,
                            onSelect = { selected = w.id }, onDone = { selected = -1 }) }
                    }
                }
                if (s.widgets.isEmpty()) Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                    GText("Tap + to add a widget", 15, dim = true)
                }
            }
        }
    }
}

@Composable
private fun WidgetCell(
    vm: LauncherViewModel, wc: WidgetController, w: WidgetPlacement, all: List<WidgetPlacement>,
    cell: androidx.compose.ui.unit.Dp, selected: Boolean, glassFrame: Boolean,
    onSelect: () -> Unit, onDone: () -> Unit,
) {
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val view = remember(w.id) { wc.createView(w.id) { onSelect() } }
    if (view == null) { LaunchedEffect(w.id) { wc.remove(w.id) }; return }

    var drag by remember { mutableStateOf(Offset.Zero) }
    var grow by remember { mutableStateOf(Offset.Zero) }
    val baseX by animateIntAsSpring(w.cellX * cellPx.toInt())
    val baseY by animateIntAsSpring(w.cellY * cellPx.toInt())
    val wPx = (w.spanX * cellPx + grow.x).coerceAtLeast(cellPx)
    val hPx = (w.spanY * cellPx + grow.y).coerceAtLeast(cellPx)
    val wDp = with(density) { wPx.toDp() }; val hDp = with(density) { hPx.toDp() }
    val accent = LocalGlass.current.accent
    val others = all.filter { it.id != w.id }

    Box(Modifier.offset { IntOffset(baseX + drag.x.roundToInt(), baseY + drag.y.roundToInt()) }.size(wDp, hDp).padding(4.dp)) {
        if (glassFrame) Box(Modifier.fillMaxSize().glass(GlassMaterial.CLEAR, 24.dp, 8.dp, touchReactive = false))
        AndroidView(
            factory = { (view.parent as? ViewGroup)?.removeView(view); view },
            modifier = Modifier.fillMaxSize().padding(if (glassFrame) 4.dp else 0.dp).clip(RoundedCornerShape(if (glassFrame) 20.dp else 24.dp)),
            update = {
                val ww = wDp.value.toInt(); val hh = hDp.value.toInt()
                it.updateAppWidgetSize(Bundle(), ww, hh, ww, hh)
            })
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, accent, RoundedCornerShape(24.dp)).background(accent.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .pointerInput(w.id, cellPx) {
                    detectDragGestures(
                        onDrag = { c, d -> c.consume(); drag += d },
                        onDragEnd = {
                            val nx = ((w.cellX * cellPx + drag.x) / cellPx).roundToInt().coerceIn(0, WIDGET_COLS - w.spanX)
                            val ny = ((w.cellY * cellPx + drag.y) / cellPx).roundToInt().coerceAtLeast(0)
                            drag = Offset.Zero
                            if (others.none { WidgetController.overlaps(it, nx, ny, w.spanX, w.spanY) })
                                vm.edit { s -> s.copy(widgets = s.widgets.map { if (it.id == w.id) it.copy(cellX = nx, cellY = ny) else it }) }
                        },
                        onDragCancel = { drag = Offset.Zero })
                })
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wc.info(w.id)?.configure != null) RoundBtn(Icons.Default.Settings) { wc.reconfigure(w.id) }
                RoundBtn(Icons.Default.Delete) { onDone(); wc.remove(w.id) }
                RoundBtn(Icons.Default.Check) { onDone() }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp)
                .glass(GlassMaterial.CRYSTAL, androidx.compose.ui.unit.Dp.Infinity, 4.dp, touchReactive = false)
                .pointerInput(w.id, cellPx) {
                    detectDragGestures(
                        onDrag = { c, d -> c.consume(); grow += d },
                        onDragEnd = {
                            val nsx = ((w.spanX * cellPx + grow.x) / cellPx).roundToInt().coerceIn(1, WIDGET_COLS - w.cellX)
                            val nsy = ((w.spanY * cellPx + grow.y) / cellPx).roundToInt().coerceIn(1, 8)
                            grow = Offset.Zero
                            if (others.none { WidgetController.overlaps(it, w.cellX, w.cellY, nsx, nsy) })
                                vm.edit { s -> s.copy(widgets = s.widgets.map { if (it.id == w.id) it.copy(spanX = nsx, spanY = nsy) else it }) }
                        },
                        onDragCancel = { grow = Offset.Zero })
                }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.OpenInFull, "Resize", tint = LocalGlass.current.text, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun animateIntAsSpring(target: Int): State<Int> =
    androidx.compose.animation.core.animateIntAsState(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "cell")

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).glass(GlassMaterial.FROSTED, androidx.compose.ui.unit.Dp.Infinity, 4.dp, touchReactive = false)
        .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = LocalGlass.current.text, modifier = Modifier.size(18.dp))
    }
}

/** Picker listing every home-screen widget provider on the device with its real preview. */
@Composable
fun WidgetPicker(wc: WidgetController, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val providers = remember { wc.providers().filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
        contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f).glass(GlassMaterial.FROSTED, 32.dp, 24.dp, touchReactive = false)
            .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().padding(top = 18.dp)) {
            GText("Add a widget", 20, FontWeight.Light, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(providers, key = { it.provider.flattenToString() }) { p ->
                    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, p) {
                        value = withContext(Dispatchers.IO) {
                            runCatching { (p.loadPreviewImage(ctx, 0) ?: p.loadIcon(ctx, 0))?.toBitmap(360, 240)?.asImageBitmap() }.getOrNull()
                        }
                    }
                    val label = remember(p) { p.loadLabel(ctx.packageManager) }
                    val app = remember(p) { runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p.provider.packageName, 0)).toString() }.getOrDefault("") }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))
                        .pointerInput(p) { detectTapGestures(onTap = { wc.beginAdd(p) }) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(110.dp, 74.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                            preview?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            GText(label.toString(), 15, FontWeight.Medium)
                            GText(app, 12, dim = true)
                            GText("${(p.minWidth / density / wc.cellDp).let { kotlin.math.ceil(it).toInt().coerceIn(1, WIDGET_COLS) }} × " +
                                "${(p.minHeight / density / wc.cellDp).let { kotlin.math.ceil(it).toInt().coerceIn(1, 6) }}", 12, dim = true)
                        }
                    }
                }
            }
        }
    }
}
