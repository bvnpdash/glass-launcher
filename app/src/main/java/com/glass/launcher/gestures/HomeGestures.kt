package com.glass.launcher.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import com.glass.launcher.data.GestureAction
import com.glass.launcher.data.GlassSettings
import kotlin.math.abs
import kotlin.math.hypot

enum class Swipe { UP, DOWN, LEFT, RIGHT }

/**
 * Observes (never consumes) pointer events on the Initial pass, so icons, dock and widgets keep working.
 * Emits one of: swipe (with live progress), pinch-in, or nothing. Taps / long-press / double-tap are handled by
 * the background layer so they only fire on empty space.
 */
fun Modifier.homeGestures(
    enabled: Boolean,
    onProgress: (Swipe, Float) -> Unit,   // 0..1 as the finger travels, for visual feedback
    onSwipe: (Swipe) -> Unit,
    onCancel: () -> Unit,
    onPinch: () -> Unit,
): Modifier = composed {
    val progress by rememberUpdatedState(onProgress)
    val swipe by rememberUpdatedState(onSwipe)
    val cancel by rememberUpdatedState(onCancel)
    val pinch by rememberUpdatedState(onPinch)
    val slop = LocalViewConfiguration.current.touchSlop
    if (!enabled) return@composed Modifier
    Modifier.pointerInput(Unit) {
        val commit = 96.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = Offset.Zero
            var dir: Swipe? = null
            var pinched = false
            var startSpan = 0f
            var lastPos = down.position
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = ev.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    val span = hypot(pressed[0].position.x - pressed[1].position.x, pressed[0].position.y - pressed[1].position.y)
                    if (startSpan == 0f) startSpan = span
                    if (!pinched && span < startSpan * 0.7f) { pinched = true; pinch() }
                    continue
                }
                if (pinched || startSpan != 0f) continue
                val p = pressed[0].position
                total += p - lastPos; lastPos = p
                if (dir == null && total.getDistance() > slop * 1.5f) {
                    // only treat as a navigation swipe when strongly directional (don't fight sloppy taps)
                    dir = if (abs(total.y) > abs(total.x) * 1.2f) { if (total.y < 0) Swipe.UP else Swipe.DOWN }
                    else if (abs(total.x) > abs(total.y) * 1.2f) { if (total.x < 0) Swipe.LEFT else Swipe.RIGHT }
                    else null
                }
                dir?.let {
                    val dist = when (it) { Swipe.UP -> -total.y; Swipe.DOWN -> total.y; Swipe.LEFT -> -total.x; Swipe.RIGHT -> total.x }
                    progress(it, (dist / commit).coerceIn(0f, 1.2f))
                }
            }
            val d = dir
            if (!pinched && d != null) {
                val dist = when (d) { Swipe.UP -> -total.y; Swipe.DOWN -> total.y; Swipe.LEFT -> -total.x; Swipe.RIGHT -> total.x }
                if (dist >= commit) swipe(d) else cancel()
            } else cancel()
        }
    }
}

fun GlassSettings.actionFor(s: Swipe): GestureAction = when (s) {
    Swipe.UP -> swipeUp; Swipe.DOWN -> swipeDown; Swipe.LEFT -> swipeLeft; Swipe.RIGHT -> swipeRight
}
