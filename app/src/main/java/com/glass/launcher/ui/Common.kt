package com.glass.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.AppInfo
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.glass.*
import java.text.SimpleDateFormat
import java.util.*

/** Provides tokens, backdrop and the shared liquid clock; draws the wallpaper under everything. */
@Composable
fun GlassRoot(vm: LauncherViewModel, animate: Boolean, content: @Composable BoxScope.() -> Unit) {
    val s by vm.settings.collectAsState()
    val dark = when (s.appearance) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val tokens = remember(s, dark) { GlassTokens(s, dark) }
    val clock = remember { GlassClock() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val running = animate && s.ambientFx
    LaunchedEffect(running, s.animSpeed) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val start = withFrameNanos { it }
            var skip = false
            while (true) {
                withFrameNanos { t ->
                    skip = !skip   // ~half frame-rate is plenty for slow liquid motion
                    if (!skip) clock.seconds = (t - start) / 1e9f * s.animSpeed
                }
            }
        }
    }
    CompositionLocalProvider(LocalGlass provides tokens, LocalBackdrop provides vm.backdrop, LocalGlassClock provides clock) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged {
            vm.backdrop.screenW = it.width.toFloat(); vm.backdrop.screenH = it.height.toFloat()
        }) {
            WallpaperLayer(clock, running)
            content()
        }
    }
}

@Composable
fun WallpaperLayer(clock: GlassClock, animated: Boolean) {
    val t = LocalGlass.current
    val bd = LocalBackdrop.current
    val m = remember { android.graphics.Matrix() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        val b = bd.sharp
        if (b != null) {
            bd.fillRoot(m, b.width, b.height)
            drawIntoCanvas { it.nativeCanvas.drawBitmap(b, m, paint) }
        }
        // ambient light: a slow accent glow that drifts when animated
        val drift = if (animated) kotlin.math.sin(clock.seconds * 0.12f) else 0f
        drawRect(Brush.radialGradient(
            listOf(t.accent.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * (0.5f + 0.25f * drift), size.height * 0.12f), radius = size.width * 0.9f))
        // readability dim + vignette
        val dim = t.settings.dim
        drawRect(if (t.dark) Color.Black.copy(alpha = dim) else Color.White.copy(alpha = dim * 0.45f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (t.dark) 0.35f else 0.12f))))
    }
}

val GlassTextStyle @Composable get() = TextStyle(
    color = LocalGlass.current.text,
    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 2f), 6f),
)

@Composable
fun GText(text: String, size: Int = 14, weight: FontWeight = FontWeight.Normal, dim: Boolean = false,
          modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = 1, spacing: Float = 0f,
          color: Color? = null) {
    val t = LocalGlass.current
    val c = color ?: if (dim) t.textDim else t.text
    Text(text, modifier, color = c, fontSize = size.sp, fontWeight = weight,
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis, letterSpacing = spacing.sp,
        style = GlassTextStyle.copy(color = c))
}

/** Cheap (non-shader) glass tile used behind app icons: many on screen, so no per-icon shader. */
@Composable
fun AppIcon(
    app: AppInfo, vm: LauncherViewModel, size: Dp = 56.dp, showLabel: Boolean = true,
    modifier: Modifier = Modifier, tile: Boolean = true, hidden: Boolean = false,
    onClick: () -> Unit = {}, onLongClick: (() -> Unit)? = null,
    onDragStart: (() -> Unit)? = null, onDrag: ((Offset) -> Unit)? = null, onDragEnd: (() -> Unit)? = null,
) {
    val t = LocalGlass.current
    val epoch by vm.iconEpoch.collectAsState()
    val icon by produceState<ImageBitmap?>(vm.icons.cached(app), app, epoch) { value = vm.icons.load(app) }
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val speed = t.speed
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f,
        spring(dampingRatio = 0.62f, stiffness = 520f * speed), label = "iconScale")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween((180 / speed).toInt()), label = "iconGlow")
    val shape = RoundedCornerShape(size * 0.36f)
    val tileA = 0.05f + t.settings.transparency * 0.10f
    Column(
        modifier.scale(scale).alpha(if (hidden) 0.45f else 1f).pointerInput(app.key) {
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { onClick() },
                onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick?.invoke() },
            )
        }.then(if (onDragStart != null) Modifier.pointerInput(app.key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart() },
                onDrag = { c, d -> c.consume(); onDrag?.invoke(d) },
                onDragEnd = { onDragEnd?.invoke() }, onDragCancel = { onDragEnd?.invoke() })
        } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val s = size * t.settings.iconSize
        Box(
            Modifier.size(s).then(if (tile) Modifier
                .clip(shape)
                .background(Brush.linearGradient(listOf(
                    Color.White.copy(alpha = tileA + 0.06f + glow * 0.10f), Color.White.copy(alpha = tileA * 0.4f))))
                .border(0.8.dp, Brush.linearGradient(listOf(
                    Color.White.copy(alpha = 0.55f * t.borderStrength + glow * 0.25f),
                    t.accent.copy(alpha = 0.22f * t.borderStrength))), shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            icon?.let { Image(it, null, Modifier.size(s * if (tile) 0.74f else 1f)) }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            GText(app.label, 11, modifier = Modifier.widthIn(max = size * 1.5f), align = TextAlign.Center)
        }
    }
}

@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, material: GlassMaterial = GlassMaterial.CLEAR,
              selected: Boolean = false, onClick: () -> Unit) {
    val t = LocalGlass.current
    Box(
        modifier.glass(material, Dp.Infinity, 6.dp).pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
            .then(if (selected) Modifier.border(1.dp, t.accent.copy(alpha = 0.9f), CircleShape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { GText(text, 13, FontWeight.Medium) }
}

@Composable
fun ClockBlock(vm: LauncherViewModel, weather: String?, modifier: Modifier = Modifier) {
    val t = LocalGlass.current
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000 - value % 60_000 + 20) }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val hm = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)).uppercase() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (t.settings.clockStyle) {
            1 -> { // stacked
                val (h, m) = hm.split(":")
                DigitRow(h, 96, FontWeight.ExtraLight); DigitRow(m, 96, FontWeight.Thin, dim = true)
            }
            2 -> Box(Modifier.glass(GlassMaterial.CLEAR, Dp.Infinity, 8.dp).padding(horizontal = 26.dp, vertical = 8.dp)) {
                DigitRow(hm, 44, FontWeight.Light)
            }
            else -> DigitRow(hm, 92, FontWeight.Thin)
        }
        Spacer(Modifier.height(2.dp))
        GText(date, 13, FontWeight.Medium, dim = true, spacing = 2.2f)
        if (weather != null) { Spacer(Modifier.height(4.dp)); GText(weather, 13, dim = true) }
    }
}

@Composable
private fun DigitRow(s: String, sp: Int, weight: FontWeight, dim: Boolean = false) {
    val t = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        s.forEach { ch ->
            AnimatedContent(ch, transitionSpec = {
                (slideInVertically(tween((320 / t.speed).toInt())) { it / 3 } + fadeIn(tween((320 / t.speed).toInt()))) togetherWith
                    (slideOutVertically(tween((220 / t.speed).toInt())) { -it / 3 } + fadeOut(tween((160 / t.speed).toInt())))
            }, label = "digit") { c ->
                Text(c.toString(), color = if (dim) t.textDim else t.text, fontSize = sp.sp, fontWeight = weight,
                    fontFamily = FontFamily.SansSerif, letterSpacing = (-1).sp, lineHeight = (sp * 1.05f).sp,
                    style = GlassTextStyle.copy(color = if (dim) t.textDim else t.text))
            }
        }
    }
}
