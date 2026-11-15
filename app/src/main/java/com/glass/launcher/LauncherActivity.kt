package com.glass.launcher

import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.glass.launcher.appdrawer.AppDrawer
import com.glass.launcher.appdrawer.LiquidSearch
import com.glass.launcher.customization.CustomizationStudio
import com.glass.launcher.dashboard.Dashboard
import com.glass.launcher.data.GestureAction
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.gestures.Swipe
import com.glass.launcher.gestures.actionFor
import com.glass.launcher.gestures.homeGestures
import com.glass.launcher.glass.LocalBackdrop
import com.glass.launcher.glass.LocalGlass
import com.glass.launcher.glass.glass
import com.glass.launcher.home.HomeScreen
import com.glass.launcher.ui.*
import com.glass.launcher.widgets.WidgetController
import com.glass.launcher.widgets.WidgetPicker
import com.glass.launcher.widgets.WidgetWorkspace

enum class Screen { HOME, DRAWER, SEARCH, WIDGETS, DASHBOARD, STUDIO, OVERVIEW }

class LauncherActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgets = WidgetController(this, vm)
        setContent { LauncherApp(vm, widgets, this) }
    }

    override fun onStart() { super.onStart(); widgets.start() }
    override fun onStop() { super.onStop(); widgets.stop(); vm.flushNow() }

    /** HOME pressed while we are already the foreground launcher. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) LauncherBus.homePressed.tryEmit(Unit)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Required by AppWidgetHost configure/bind flows")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) vm.icons.trim()
    }
}

@Composable
fun LauncherApp(vm: LauncherViewModel, wc: WidgetController, activity: ComponentActivity) {
    val ready by vm.ready.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }
    GlassRoot(vm, animate = screen == Screen.HOME || screen == Screen.SEARCH) {
        if (!ready) return@GlassRoot
        Launcher(vm, wc, screen) { screen = it }
    }
}

@Composable
private fun BoxScope.Launcher(vm: LauncherViewModel, wc: WidgetController, screen: Screen, setScreen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val t = LocalGlass.current
    val bd = LocalBackdrop.current
    val weather by vm.dash.weather.collectAsState()
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // lightweight refresh of battery/weather for the clock; stops when not resumed
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { vm.dash.refresh(); kotlinx.coroutines.delay(10 * 60_000L) }
        }
    }
    LaunchedEffect(Unit) { LauncherBus.homePressed.collect { menu = null; wc.pickerOpen = false; setScreen(Screen.HOME) } }

    fun perform(a: GestureAction) {
        when (a) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER -> setScreen(Screen.DRAWER)
            GestureAction.SEARCH -> setScreen(Screen.SEARCH)
            GestureAction.WIDGETS -> setScreen(Screen.WIDGETS)
            GestureAction.DASHBOARD -> setScreen(Screen.DASHBOARD)
            GestureAction.CUSTOMIZE, GestureAction.SETTINGS -> setScreen(Screen.STUDIO)
            GestureAction.OVERVIEW -> setScreen(Screen.OVERVIEW)
            GestureAction.NOTIFICATIONS -> {
                val ok = runCatching {
                    Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel")
                        .invoke(ctx.getSystemService("statusbar"))
                }.isSuccess
                if (!ok) setScreen(Screen.SEARCH)
            }
            GestureAction.LOCK -> {
                val svc = LauncherBus.lockService
                if (svc?.lock() != true) {
                    Toast.makeText(ctx, "Enable “Glass double-tap lock” in Accessibility settings", Toast.LENGTH_LONG).show()
                    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
    }

    BackHandler(enabled = screen != Screen.HOME || menu != null || wc.pickerOpen) {
        when {
            menu != null -> menu = null
            wc.pickerOpen -> wc.pickerOpen = false
            else -> setScreen(Screen.HOME)
        }
    }

    // wallpaper parallax follows the page (no sensors, no battery cost)
    val parallaxTarget = if (!s.parallax) 0f else when (screen) { Screen.WIDGETS -> -48f; Screen.DASHBOARD -> 48f; else -> 0f }
    val par by animateFloatAsState(parallaxTarget, spring(0.9f, 140f * t.speed), label = "parallax")
    SideEffect { bd.parallaxPx = par }

    // live gesture feedback: read inside graphicsLayer so it never triggers recomposition
    var dragProg by remember { mutableFloatStateOf(0f) }
    var dragDir by remember { mutableStateOf<Swipe?>(null) }
    val atHome = screen == Screen.HOME

    val homeDim by animateFloatAsState(if (atHome) 1f else 0f, spring(1f, 300f * t.speed), label = "homeDim")

    Box(Modifier.fillMaxSize()
        .homeGestures(
            enabled = atHome && menu == null && !wc.pickerOpen,
            onProgress = { d, p -> dragDir = d; dragProg = p },
            onSwipe = { d -> dragProg = 0f; dragDir = null; perform(s.actionFor(d)) },
            onCancel = { dragProg = 0f; dragDir = null },
            onPinch = { dragProg = 0f; dragDir = null; perform(s.pinch) },
        )) {

        // empty-space gestures: double tap & long press
        Box(Modifier.fillMaxSize().pointerInput(s.doubleTap, s.longPress) {
            detectTapGestures(onDoubleTap = { if (atHome) perform(s.doubleTap) }, onLongPress = { if (atHome) perform(s.longPress) })
        })

        // ---- HOME (always composed; recedes behind overlays) ----
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = dragProg.coerceAtMost(1f) * 26.dp.toPx()
            when (dragDir) {
                Swipe.UP -> translationY = -p; Swipe.DOWN -> translationY = p
                Swipe.LEFT -> translationX = -p; Swipe.RIGHT -> translationX = p
                null -> Unit
            }
            val rec = 0.95f + 0.05f * homeDim
            scaleX = rec; scaleY = rec; alpha = 0.25f + 0.75f * homeDim
        }) {
            HomeScreen(vm, weather?.let { "${it.tempC}° · ${it.label}" }, editMode = false, onMenu = { menu = it })
        }

        // soft edge glow while a swipe is in progress
        EdgeGlow(dragDir, dragProg)

        // ---- overlays ----
        Overlay(screen == Screen.DRAWER, from = Edge.BOTTOM) { AppDrawer(vm, onMenu = { menu = it }, onClose = { setScreen(Screen.HOME) }) }
        Overlay(screen == Screen.WIDGETS, from = Edge.RIGHT) {
            WidgetWorkspace(vm, wc)
            NavChip("Home") { setScreen(Screen.HOME) }
        }
        Overlay(screen == Screen.DASHBOARD, from = Edge.LEFT) {
            Dashboard(vm)
            NavChip("Home") { setScreen(Screen.HOME) }
        }
        Overlay(screen == Screen.STUDIO, from = Edge.BOTTOM) { CustomizationStudio(vm, onClose = { setScreen(Screen.HOME) }) }
        Overlay(screen == Screen.OVERVIEW, from = Edge.BOTTOM, scrim = 0.1f) { Overview(vm, setScreen) }

        // search is its own surface: it grows from the top
        AnimatedVisibility(screen == Screen.SEARCH, enter = fadeIn(tween(120)), exit = fadeOut(tween(140))) {
            LiquidSearch(vm, onClose = { setScreen(Screen.HOME) })
        }

        if (wc.pickerOpen) WidgetPicker(wc) { wc.pickerOpen = false }
        menu?.let { m -> AppMenu(m, vm) { menu = null } }
    }
}

private enum class Edge { BOTTOM, LEFT, RIGHT }

@Composable
private fun Overlay(visible: Boolean, from: Edge, scrim: Float = 0.38f, content: @Composable BoxScope.() -> Unit) {
    val speed = LocalGlass.current.speed
    val spec = spring<androidx.compose.ui.unit.IntOffset>(0.86f, 420f * speed)
    val fspec = spring<Float>(1f, 500f * speed)
    val enter = when (from) {
        Edge.BOTTOM -> slideInVertically(spring(0.86f, 420f * speed)) { it / 3 } + fadeIn(fspec) + scaleIn(spring(0.9f, 400f * speed), 0.96f)
        Edge.LEFT -> slideInHorizontally(spring(0.9f, 420f * speed)) { -it } + fadeIn(fspec)
        Edge.RIGHT -> slideInHorizontally(spring(0.9f, 420f * speed)) { it } + fadeIn(fspec)
    }
    val exit = when (from) {
        Edge.BOTTOM -> slideOutVertically(tween((220 / speed).toInt())) { it / 4 } + fadeOut(tween((180 / speed).toInt()))
        Edge.LEFT -> slideOutHorizontally(tween((220 / speed).toInt())) { -it } + fadeOut(tween((160 / speed).toInt()))
        Edge.RIGHT -> slideOutHorizontally(tween((220 / speed).toInt())) { it } + fadeOut(tween((160 / speed).toInt()))
    }
    AnimatedVisibility(visible, enter = enter, exit = exit) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)).pointerInput(Unit) { detectTapGestures { } }) { content() }
    }
}

@Composable
private fun BoxScope.EdgeGlow(dir: Swipe?, progress: Float) {
    if (dir == null || progress <= 0.02f) return
    val t = LocalGlass.current
    val a = (progress * 0.5f).coerceAtMost(0.5f)
    val brush = when (dir) {
        Swipe.UP -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, t.accent.copy(alpha = a)))
        Swipe.DOWN -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(t.accent.copy(alpha = a), Color.Transparent))
        Swipe.LEFT -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, t.accent.copy(alpha = a)))
        Swipe.RIGHT -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(t.accent.copy(alpha = a), Color.Transparent))
    }
    val align = when (dir) { Swipe.UP -> Alignment.BottomCenter; Swipe.DOWN -> Alignment.TopCenter; Swipe.LEFT -> Alignment.CenterEnd; Swipe.RIGHT -> Alignment.CenterStart }
    val m = if (dir == Swipe.UP || dir == Swipe.DOWN) Modifier.fillMaxWidth().height(120.dp) else Modifier.fillMaxHeight().width(90.dp)
    Box(Modifier.align(align).then(m).background(brush))
}

@Composable
private fun BoxScope.NavChip(text: String, onClick: () -> Unit) {
    Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)) {
        GlassPill(text, material = GlassMaterial.LIQUID, onClick = onClick)
    }
}

/** Pinch overview: jump between the launcher's pages. */
@Composable
private fun Overview(vm: LauncherViewModel, go: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).pointerInput(Unit) { detectTapGestures { go(Screen.HOME) } },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GText("Overview", 26, FontWeight.Light)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OverviewCard("Dashboard") { go(Screen.DASHBOARD) }
            OverviewCard("Home") { go(Screen.HOME) }
            OverviewCard("Widgets") { go(Screen.WIDGETS) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OverviewCard("Apps") { go(Screen.DRAWER) }
            OverviewCard("Studio") { go(Screen.STUDIO) }
            OverviewCard("Search") { go(Screen.SEARCH) }
        }
    }
}

@Composable
private fun OverviewCard(label: String, onClick: () -> Unit) {
    Box(Modifier.size(96.dp, 128.dp).glass(GlassMaterial.CRYSTAL, 24.dp, 12.dp).pointerInput(onClick) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center) { GText(label, 14, FontWeight.Medium) }
}
