package com.glass.launcher.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.glass.launcher.EMPTY
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import com.glass.launcher.ui.*

/** Drag state shared by the grid and the dock so an icon can move between them. */
@Stable
class DragState {
    var key by mutableStateOf<String?>(null)
    var pos by mutableStateOf(Offset.Zero)         // finger position in root coords
    var from by mutableStateOf(MenuSource.HOME)
    var dropRequested by mutableStateOf(false)
    val active get() = key != null
}

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    weather: String?,
    editMode: Boolean,
    onMenu: (MenuRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by vm.settings.collectAsState()
    val apps by vm.apps.collectAsState()
    val t = LocalGlass.current
    val density = LocalDensity.current
    val drag = remember { DragState() }
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val cols = s.gridCols
    val rows = s.gridRows
    val cells = remember(s.homeApps, cols, rows) {
        List(cols * rows) { s.homeApps.getOrNull(it) ?: EMPTY }
    }
    val dock = s.dockApps.take(s.dockCount)

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ClockBlock(vm, weather, Modifier.fillMaxWidth().padding(top = 36.dp))
            Spacer(Modifier.height(18.dp))

            // ---- icon grid ----
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)
                    .onGloballyPositioned { gridBounds = it.boundsInRoot() }
            ) {
                val cellW = maxWidth / cols
                val cellH = (maxHeight / rows).coerceAtMost(cellW * 1.35f)
                val iconSize = (minOf(cellW.value, cellH.value) * 0.62f).dp.coerceAtMost(64.dp)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
                    for (r in 0 until rows) Row(Modifier.fillMaxWidth().height(cellH)) {
                        for (c in 0 until cols) {
                            val idx = r * cols + c
                            val key = cells[idx]
                            Box(Modifier.width(cellW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                val app = byKey[key]
                                if (app != null) HomeIcon(app, vm, iconSize, MenuSource.HOME, drag, onMenu, ghost = drag.key == key)
                            }
                        }
                    }
                }
                // drop highlight
                if (drag.active && gridBounds.contains(drag.pos)) {
                    val cw = with(density) { cellW.toPx() }; val ch = with(density) { cellH.toPx() }
                    val cx = ((drag.pos.x - gridBounds.left) / cw).toInt().coerceIn(0, cols - 1)
                    val cy = ((drag.pos.y - gridBounds.top) / ch).toInt().coerceIn(0, rows - 1)
                    Box(Modifier.offset(cellW * cx, cellH * cy).size(cellW, cellH).padding(6.dp)
                        .glass(GlassMaterial.CLEAR, 22.dp, 0.dp, touchReactive = false))
                }
            }

            // ---- dock ----
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                val pressedDock = drag.active && dockBounds.contains(drag.pos)
                val scale by animateFloatAsState(if (pressedDock) 1.04f else 1f, spring(0.7f, 400f * t.speed), label = "dock")
                Row(
                    Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                        .onGloballyPositioned { dockBounds = it.boundsInRoot() }
                        .glass(s.dockMaterial, androidx.compose.ui.unit.Dp.Infinity, 18.dp)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .defaultMinSize(minHeight = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    dock.forEach { key ->
                        val app = byKey[key] ?: return@forEach
                        HomeIcon(app, vm, 52.dp, MenuSource.DOCK, drag, onMenu, tile = false, label = false, ghost = drag.key == key)
                    }
                    if (dock.isEmpty()) GText("Drag apps here", 12, dim = true, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }

        // ---- floating dragged icon ----
        val dk = drag.key
        val dApp = dk?.let { byKey[it] }
        if (dApp != null) {
            val sizePx = with(density) { (60.dp * s.iconSize).toPx() }
            Box(Modifier.zIndex(10f)
                .offset { androidx.compose.ui.unit.IntOffset((drag.pos.x - sizePx / 2).toInt(), (drag.pos.y - sizePx / 2 - 36).toInt()) }
                .graphicsLayer { scaleX = 1.18f; scaleY = 1.18f; alpha = 0.95f }) {
                AppIcon(dApp, vm, 60.dp, showLabel = false)
            }
        }
    }

    DropHandler(drag, vm, gridBounds, dockBounds, cols, rows, dock.size, s.dockCount)
}

@Composable
private fun HomeIcon(
    app: AppInfo, vm: LauncherViewModel, size: androidx.compose.ui.unit.Dp, source: MenuSource,
    drag: DragState, onMenu: (MenuRequest) -> Unit, tile: Boolean = true, label: Boolean = true,
    ghost: Boolean = false,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableFloatStateOf(0f) }
    AppIcon(
        app, vm, size, showLabel = label, tile = tile,
        modifier = Modifier.alpha(if (ghost) 0f else 1f).onGloballyPositioned { origin = it.boundsInRoot().center },
        onClick = { vm.launch(app) },
        onDragStart = { moved = 0f; drag.key = app.key; drag.from = source; drag.pos = origin },
        onDrag = { d -> drag.pos += d; moved += d.getDistance() },
        onDragEnd = {
            if (moved < 12f) { drag.key = null; onMenu(MenuRequest(app, origin, source)) }
            else drag.dropRequested = true
        },
    )
}

@Composable
private fun DropHandler(
    drag: DragState, vm: LauncherViewModel, grid: Rect, dock: Rect, cols: Int, rows: Int, dockSize: Int, dockMax: Int,
) {
    if (!drag.dropRequested) return
    val key = drag.key
    val p = drag.pos
    SideEffect {
        drag.dropRequested = false
        drag.key = null
        if (key == null) return@SideEffect
        when {
            grid.contains(p) -> {
                val cw = grid.width / cols; val ch = grid.height / rows
                val cx = ((p.x - grid.left) / cw).toInt().coerceIn(0, cols - 1)
                val cy = ((p.y - grid.top) / ch).toInt().coerceIn(0, rows - 1)
                vm.placeOnHome(key, cy * cols + cx)
            }
            dock.inflate(40f).contains(p) -> {
                val idx = if (dock.width <= 0f) dockSize else (((p.x - dock.left) / dock.width) * (dockSize + 1)).toInt()
                vm.placeOnDock(key, idx.coerceIn(0, dockSize))
            }
            else -> Unit  // dropped outside: snap back
        }
    }
}

private fun Rect.inflate(d: Float) = Rect(left - d, top - d, right + d, bottom + d)
