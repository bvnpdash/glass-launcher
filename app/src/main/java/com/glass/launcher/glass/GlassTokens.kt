package com.glass.launcher.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.glass.launcher.data.GlassMaterial
import com.glass.launcher.data.GlassSettings

/** Physical description of each material. Everything is multiplied by the user's global settings. */
@Immutable
data class MaterialSpec(
    val tintAlpha: Float,    // body tint strength
    val blurMix: Float,      // 0 = sharp wallpaper visible, 1 = fully blurred
    val refraction: Float,   // edge lens strength
    val dispersion: Float,   // chromatic split at edges
    val specular: Float,     // sheen + rim light
    val liquid: Float,       // animated distortion
)

fun materialSpec(m: GlassMaterial) = when (m) {
    GlassMaterial.CLEAR -> MaterialSpec(0.06f, 0.18f, 0.55f, 0.0f, 0.45f, 0.0f)
    GlassMaterial.FROSTED -> MaterialSpec(0.14f, 1.00f, 0.25f, 0.0f, 0.30f, 0.0f)
    GlassMaterial.CRYSTAL -> MaterialSpec(0.08f, 0.35f, 0.85f, 1.0f, 0.90f, 0.0f)
    GlassMaterial.LIQUID -> MaterialSpec(0.07f, 0.45f, 0.95f, 0.4f, 0.75f, 1.0f)
}

@Immutable
class GlassTokens(
    val settings: GlassSettings,
    val dark: Boolean,
) {
    val accent: Color = Color.hsv(settings.accentHue, 0.55f, 1f)
    val text: Color = if (dark) Color(0xFFF6F8FC) else Color(0xFF0E1420)
    val textDim: Color = text.copy(alpha = 0.66f)
    val glassTint: Color = if (dark) Color(0xFF0A0F1A) else Color(0xFFF4F7FB)
    /** Global opacity of the glass body; 0 = pure refraction, 1 = dense tint. */
    val body: Float = 0.05f + settings.transparency * 0.55f
    val blurMix: Float = (0.25f + settings.blur * 1.25f).coerceIn(0f, 1.5f)
    val borderStrength: Float = settings.border
    val speed: Float = settings.animSpeed
    fun <T> spec(f: Float, a: T, b: T) = if (f >= 0.5f) a else b
}

val LocalGlass = compositionLocalOf { GlassTokens(GlassSettings(), true) }
