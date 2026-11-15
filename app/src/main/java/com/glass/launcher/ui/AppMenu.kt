package com.glass.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import kotlin.math.roundToInt

enum class MenuSource { HOME, DOCK, DRAWER }
data class MenuRequest(val app: AppInfo, val anchor: Offset, val source: MenuSource)

private data class Entry(val label: String, val danger: Boolean = false, val action: () -> Unit)

@Composable
fun AppMenu(req: MenuRequest, vm: LauncherViewModel, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsState()
    val key = req.app.key
    val shortcuts = remember(req) { vm.repo.shortcuts(req.app) }
    val entries = buildList {
        shortcuts.forEach { sc -> add(Entry(sc.shortLabel?.toString() ?: "Shortcut") { vm.repo.startShortcut(sc) }) }
        when (req.source) {
            MenuSource.HOME -> add(Entry("Remove from home") { vm.removeFromHome(key) })
            MenuSource.DOCK -> add(Entry("Remove from dock") { vm.removeFromDock(key) })
            MenuSource.DRAWER -> {
                add(Entry("Add to home") { vm.addToHome(key) })
                add(Entry("Add to dock") { vm.placeOnDock(key, s.dockApps.size) })
            }
        }
        add(Entry(if (key in s.favorites) "Remove favourite" else "Favourite") { vm.toggleFavorite(key) })
        add(Entry("Hide app") { vm.hide(key) })
        add(Entry("App info") { vm.repo.openAppInfo(req.app) })
        add(Entry("Uninstall", danger = true) { vm.repo.uninstall(req.app) })
    }
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val w = 224.dp
    val h = (entries.size * 44 + 16).dp
    val wPx = with(density) { w.toPx() }; val hPx = with(density) { h.toPx() }
    val screenW = with(density) { conf.screenWidthDp.dp.toPx() }
    val x = (req.anchor.x - wPx / 2).coerceIn(with(density) { 12.dp.toPx() }, screenW - wPx - with(density) { 12.dp.toPx() })
    val above = req.anchor.y - hPx - with(density) { 44.dp.toPx() }
    val y = if (above > with(density) { 48.dp.toPx() }) above else req.anchor.y + with(density) { 44.dp.toPx() }

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(0.72f, 420f)) }
    val t = LocalGlass.current

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f * enter.value))
        .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }) {
        Column(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(w)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, if (y < req.anchor.y) 1f else 0f)
                    scaleX = 0.82f + 0.18f * enter.value; scaleY = scaleX; alpha = enter.value.coerceIn(0f, 1f)
                }
                .glass(GlassMaterial.FROSTED, 24.dp, 20.dp, touchReactive = false)
                .pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 8.dp),
        ) {
            entries.forEach { e ->
                Box(Modifier.fillMaxWidth().height(44.dp)
                    .pointerInput(e) { detectTapGestures(onTap = { onDismiss(); e.action() }) }.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart) {
                    GText(e.label, 15, FontWeight.Normal, color = if (e.danger) Color(0xFFFF8A80) else null)
                }
            }
        }
    }
}
