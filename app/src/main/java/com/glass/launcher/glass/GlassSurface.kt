package com.glass.launcher.glass

import android.graphics.*
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glass.launcher.data.GlassMaterial
import androidx.compose.ui.graphics.Color as CColor

/** Single animation clock shared by all liquid surfaces. Only liquid draws read it, so nothing else invalidates. */
@Stable
class GlassClock { var seconds by mutableFloatStateOf(0f) }
val LocalGlassClock = staticCompositionLocalOf { GlassClock() }

/** AGSL path. Isolated in its own class so API < 33 devices never load RuntimeShader. */
@androidx.annotation.RequiresApi(33)
private class AgslRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = Matrix()
    private val shader = RuntimeShader(GLASS_AGSL)
    private var sharpShader: BitmapShader? = null
    private var softShader: BitmapShader? = null
    private var boundSharp: Bitmap? = null
    private var boundSoft: Bitmap? = null

    private fun bind(sharp: Bitmap, soft: Bitmap) {
        if (boundSharp !== sharp) {
            sharpShader = BitmapShader(sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR }
            boundSharp = sharp
        }
        if (boundSoft !== soft) {
            softShader = BitmapShader(soft, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR }
            boundSoft = soft
        }
    }

    fun draw(c: Canvas, w: Float, h: Float, r: Float, dpx: Float, pos: Offset, bd: Backdrop, sharp: Bitmap, soft: Bitmap,
             t: GlassTokens, spec: MaterialSpec, mixv: Float, tintA: Float, time: Float, touch: Offset, touchAmt: Float) {
        bind(sharp, soft)
        val tint = t.glassTint; val accent = t.accent
        bd.fill(tmp, sharp.width, sharp.height, pos.x, pos.y); sharpShader!!.setLocalMatrix(tmp)
        bd.fill(tmp, soft.width, soft.height, pos.x, pos.y); softShader!!.setLocalMatrix(tmp)
        val s = shader
        s.setInputShader("sharp", sharpShader!!)
        s.setInputShader("soft", softShader!!)
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("radius", r)
        s.setFloatUniform("time", time)
        s.setFloatUniform("touch", touch.x, touch.y)
        s.setFloatUniform("touchAmt", touchAmt)
        s.setFloatUniform("tint", tint.red, tint.green, tint.blue, tintA)
        s.setFloatUniform("accent", accent.red, accent.green, accent.blue, 1f)
        s.setFloatUniform("mixv", mixv)
        s.setFloatUniform("refr", spec.refraction * (0.4f + t.settings.border * 0.8f))
        s.setFloatUniform("disp", spec.dispersion)
        s.setFloatUniform("spec", spec.specular)
        s.setFloatUniform("liquid", spec.liquid)
        s.setFloatUniform("border", t.borderStrength)
        s.setFloatUniform("dpx", dpx)
        paint.shader = s
        c.drawRoundRect(0f, 0f, w, h, r, r, paint)
        paint.shader = null
    }
}

private class GlassPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val tmp = Matrix()
    private val path = Path()
    private val rect = RectF()
    private var agsl: Any? = null

    fun draw(
        c: Canvas, w: Float, h: Float, radius: Float, dpx: Float,
        pos: Offset, bd: Backdrop, t: GlassTokens, spec: MaterialSpec,
        time: Float, touch: Offset, touchAmt: Float,
    ) {
        val sharp = bd.sharp ?: return
        val soft = bd.blurred ?: return
        val r = radius.coerceAtMost(minOf(w, h) / 2f)
        val mixv = (spec.blurMix * t.blurMix).coerceIn(0f, 1f)
        val tintA = (spec.tintAlpha + t.body * 0.55f).coerceIn(0f, 0.85f)
        val tint = t.glassTint
        val accent = t.accent

        if (Build.VERSION.SDK_INT >= 33) {
            val ag = (agsl ?: AgslRenderer().also { agsl = it }) as AgslRenderer
            ag.draw(c, w, h, r, dpx, pos, bd, sharp, soft, t, spec, mixv, tintA, time, touch, touchAmt)
            return
        }

        // ---- Fallback (API 26-32): pre-blurred wallpaper clipped to the pane + gradients ----
        c.save()
        rect.set(0f, 0f, w, h)
        path.reset(); path.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(path)
        paint.alpha = 255
        bd.fill(tmp, sharp.width, sharp.height, pos.x, pos.y); c.drawBitmap(sharp, tmp, paint)
        paint.alpha = (mixv * 255).toInt()
        bd.fill(tmp, soft.width, soft.height, pos.x, pos.y); c.drawBitmap(soft, tmp, paint)
        paint.alpha = 255
        c.drawColor(CColor(tint.red, tint.green, tint.blue, tintA).toArgb())
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.55f,
            Color.argb((spec.specular * 46).toInt(), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(rect, paint)
        paint.shader = null
        c.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpx * 1.1f
        paint.shader = LinearGradient(0f, 0f, w, h,
            Color.argb((t.borderStrength * 235).toInt(), 255, 255, 255),
            accent.copy(alpha = t.borderStrength * 0.45f).toArgb(), Shader.TileMode.CLAMP)
        c.drawRoundRect(dpx * 0.55f, dpx * 0.55f, w - dpx * 0.55f, h - dpx * 0.55f, r, r, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }
}

/**
 * The reusable glass material. Apply to any layout:
 *   Box(Modifier.glass(GlassMaterial.LIQUID, cornerRadius = 32.dp))
 * [cornerRadius] = Dp.Infinity gives a pill / circle.
 */
fun Modifier.glass(
    material: GlassMaterial,
    cornerRadius: Dp = 28.dp,
    shadowElevation: Dp = 10.dp,
    touchReactive: Boolean = true,
): Modifier = composed {
    val tokens = LocalGlass.current
    val backdrop = LocalBackdrop.current
    val clock = LocalGlassClock.current
    val density = LocalDensity.current
    val spec = remember(material) { materialSpec(material) }
    val painter = remember { GlassPainter() }
    var pos by remember { mutableStateOf(Offset.Zero) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val amt = remember { Animatable(0f) }
    var down by remember { mutableStateOf(false) }
    LaunchedEffect(down) { amt.animateTo(if (down) 1f else 0f, tween((260 / tokens.speed).toInt())) }

    val radiusPx = if (cornerRadius == Dp.Infinity) 100000f else with(density) { cornerRadius.toPx() }
    val shape = if (cornerRadius == Dp.Infinity) androidx.compose.foundation.shape.CircleShape
    else androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)

    this
        .shadow(shadowElevation, shape, clip = false,
            ambientColor = CColor.Black.copy(alpha = 0.35f), spotColor = CColor.Black.copy(alpha = 0.45f))
        .onGloballyPositioned { pos = it.positionInRoot() }
        .then(
            if (touchReactive) Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = e.changes.firstOrNull() ?: continue
                        if (ch.pressed) { touch = ch.position; down = true } else down = false
                    }
                }
            } else Modifier
        )
        .drawBehind {
            drawGlass(painter, radiusPx, density.density, pos, backdrop, tokens, spec,
                if (spec.liquid > 0f || amt.value > 0f) clock.seconds else 0f, touch, amt.value)
        }
}

private fun DrawScope.drawGlass(
    p: GlassPainter, radius: Float, dpx: Float, pos: Offset, bd: Backdrop, t: GlassTokens,
    spec: MaterialSpec, time: Float, touch: Offset, amt: Float,
) {
    p.draw(drawContext.canvas.nativeCanvas, size.width, size.height, radius, dpx, pos, bd, t, spec, time, touch, amt)
}
