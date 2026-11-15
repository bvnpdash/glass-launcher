package com.glass.launcher.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Holds the wallpaper in two resolutions (sharp + pre-blurred) so every glass surface can sample
 * "what is behind it" at the correct screen position. This is a real backdrop, not a flat tint.
 */
@Stable
class Backdrop {
    var sharp by mutableStateOf<Bitmap?>(null); private set
    var blurred by mutableStateOf<Bitmap?>(null); private set
    var screenW by mutableFloatStateOf(1080f)
    var screenH by mutableFloatStateOf(2400f)
    /** Horizontal parallax in pixels; driven by pager progress (no sensors = no battery cost). */
    var parallaxPx by mutableFloatStateOf(0f)
    private var lastBlurKey = ""

    /** Fills [m] so that a bitmap of size bw x bh covers the screen (+10% width for parallax). */
    fun fill(m: Matrix, bw: Int, bh: Int, originX: Float, originY: Float) {
        val s = max(screenW * 1.1f / bw, screenH / bh)
        val ox = -(s * bw - screenW) / 2f + parallaxPx
        val oy = -(s * bh - screenH) / 2f
        m.reset()
        m.postScale(s, s)
        m.postTranslate(ox - originX, oy - originY)
    }

    fun fillRoot(m: Matrix, bw: Int, bh: Int) = fill(m, bw, bh, 0f, 0f)

    suspend fun load(context: Context, uri: String, tryDevice: Boolean, blurAmount: Float) {
        val key = "$uri|$tryDevice"
        if (key != lastBlurKey || sharp == null) {
            val bmp = withContext(Dispatchers.IO) { decode(context, uri, tryDevice) }
            lastBlurKey = key
            sharp = bmp
        }
        val src = sharp ?: return
        blurred = withContext(Dispatchers.Default) { blurSmall(src, blurAmount) }
    }

    private fun decode(context: Context, uri: String, tryDevice: Boolean): Bitmap {
        val dm = context.resources.displayMetrics
        val targetW = minOf(dm.widthPixels, 720)
        if (uri.isNotEmpty()) runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetW) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()?.let { return it }
        if (tryDevice) runCatching {
            val d = WallpaperManager.getInstance(context).drawable
            val ratio = d.intrinsicHeight.toFloat() / d.intrinsicWidth
            val b = Bitmap.createBitmap(targetW, (targetW * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, b.width, b.height); d.draw(Canvas(b)); b
        }.getOrNull()?.let { return it }
        return aurora(targetW, (targetW * dm.heightPixels.toFloat() / dm.widthPixels).toInt())
    }

    /** Built-in wallpaper: deep AMOLED base with soft aurora lobes, so the glass always has something to refract. */
    private fun aurora(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.parseColor("#05070D"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun blob(x: Float, y: Float, r: Float, col: Int) {
            p.shader = RadialGradient(x * w, y * h, r * w, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(x * w, y * h, r * w, p)
        }
        blob(0.15f, 0.18f, 0.80f, Color.argb(200, 24, 98, 255))
        blob(0.90f, 0.35f, 0.70f, Color.argb(190, 140, 40, 255))
        blob(0.30f, 0.72f, 0.80f, Color.argb(170, 0, 200, 190))
        blob(0.85f, 0.92f, 0.60f, Color.argb(170, 255, 70, 140))
        blob(0.50f, 0.50f, 0.35f, Color.argb(70, 255, 255, 255))
        return b
    }

    /** Downscale + 3-pass box blur on a ~160px bitmap. Cheap (a few ms) and good enough once upscaled. */
    private fun blurSmall(src: Bitmap, amount: Float): Bitmap {
        val w = 160
        val h = (w * src.height.toFloat() / src.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val r = (1 + amount.coerceIn(0f, 1.5f) * 7f).toInt()
        repeat(3) { boxPass(px, w, h, r, true); boxPass(px, w, h, r, false) }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        return out
    }

    private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val tmp = IntArray(len)
        for (l in 0 until lines) {
            fun idx(i: Int) = if (horizontal) l * w + i else i * w + l
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val v = px[idx(i.coerceIn(0, len - 1))]
                sr += (v shr 16) and 255; sg += (v shr 8) and 255; sb += v and 255
            }
            val n = 2 * r + 1
            for (i in 0 until len) {
                tmp[i] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val add = px[idx((i + r + 1).coerceIn(0, len - 1))]
                val rem = px[idx((i - r).coerceIn(0, len - 1))]
                sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
                sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
                sb += (add and 255) - (rem and 255)
            }
            for (i in 0 until len) px[idx(i)] = tmp[i]
        }
    }
}

val LocalBackdrop = staticCompositionLocalOf { Backdrop() }

/** Position of a glass surface in root coordinates. */
@Stable
class SurfacePos { var offset by mutableStateOf(Offset.Zero) }
