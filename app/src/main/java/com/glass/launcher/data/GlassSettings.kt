package com.glass.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("glass_settings")

enum class GestureAction(val label: String) {
    NONE("Nothing"), APP_DRAWER("App drawer"), NOTIFICATIONS("Notifications"),
    SEARCH("Search"), WIDGETS("Widget workspace"), DASHBOARD("Smart dashboard"),
    LOCK("Lock screen"), CUSTOMIZE("Customize home"), OVERVIEW("Home overview"),
    SETTINGS("Customization studio")
}

enum class GlassMaterial(val label: String) { CLEAR("Clear"), FROSTED("Frosted"), CRYSTAL("Crystal"), LIQUID("Liquid") }

data class GlassSettings(
    val transparency: Float = 0.55f,   // 0 = invisible fill, 1 = dense
    val blur: Float = 0.5f,            // 0..1 -> 0..40dp radius
    val border: Float = 0.6f,
    val accentHue: Float = 205f,
    val animSpeed: Float = 1f,         // 0.5 slow .. 2 fast
    val iconSize: Float = 1f,          // 0.7 .. 1.3
    val gridCols: Int = 4,
    val gridRows: Int = 4,
    val dockCount: Int = 4,
    val clockStyle: Int = 0,           // 0 thin, 1 stacked, 2 compact
    val appearance: Int = 0,           // 0 system, 1 light, 2 dark
    val wallpaperUri: String = "",
    val useSystemWallpaper: Boolean = false,
    val dim: Float = 0.2f,
    val parallax: Boolean = true,
    val ambientFx: Boolean = true,
    val iconPack: String = "",
    val dockMaterial: GlassMaterial = GlassMaterial.LIQUID,
    val cardMaterial: GlassMaterial = GlassMaterial.CLEAR,
    val iconMaterial: GlassMaterial = GlassMaterial.CRYSTAL,
    val swipeUp: GestureAction = GestureAction.APP_DRAWER,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeLeft: GestureAction = GestureAction.WIDGETS,
    val swipeRight: GestureAction = GestureAction.DASHBOARD,
    val doubleTap: GestureAction = GestureAction.LOCK,
    val longPress: GestureAction = GestureAction.CUSTOMIZE,
    val pinch: GestureAction = GestureAction.OVERVIEW,
    val homeApps: List<String> = emptyList(),   // "pkg/class"
    val dockApps: List<String> = emptyList(),
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val recentSearches: List<String> = emptyList(),
    val recentLaunches: List<String> = emptyList(),
    val widgets: List<WidgetPlacement> = emptyList(),
    val glassWidgets: Boolean = true,
    val seeded: Boolean = false,
)

data class WidgetPlacement(val id: Int, val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int) {
    fun encode() = "$id,$cellX,$cellY,$spanX,$spanY"
    companion object {
        fun decode(s: String): WidgetPlacement? = runCatching {
            val p = s.split(","); WidgetPlacement(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt())
        }.getOrNull()
    }
}

class SettingsRepository(private val context: Context) {
    private object K {
        val transparency = floatPreferencesKey("transparency")
        val blur = floatPreferencesKey("blur")
        val border = floatPreferencesKey("border")
        val accentHue = floatPreferencesKey("accentHue")
        val animSpeed = floatPreferencesKey("animSpeed")
        val iconSize = floatPreferencesKey("iconSize")
        val gridCols = intPreferencesKey("gridCols")
        val gridRows = intPreferencesKey("gridRows")
        val dockCount = intPreferencesKey("dockCount")
        val clockStyle = intPreferencesKey("clockStyle")
        val appearance = intPreferencesKey("appearance")
        val wallpaperUri = stringPreferencesKey("wallpaperUri")
        val useSystemWallpaper = booleanPreferencesKey("useSystemWallpaper")
        val dim = floatPreferencesKey("dim")
        val parallax = booleanPreferencesKey("parallax")
        val ambientFx = booleanPreferencesKey("ambientFx")
        val iconPack = stringPreferencesKey("iconPack")
        val dockMaterial = stringPreferencesKey("dockMaterial")
        val cardMaterial = stringPreferencesKey("cardMaterial")
        val iconMaterial = stringPreferencesKey("iconMaterial")
        val swipeUp = stringPreferencesKey("swipeUp")
        val swipeDown = stringPreferencesKey("swipeDown")
        val swipeLeft = stringPreferencesKey("swipeLeft")
        val swipeRight = stringPreferencesKey("swipeRight")
        val doubleTap = stringPreferencesKey("doubleTap")
        val longPress = stringPreferencesKey("longPress")
        val pinch = stringPreferencesKey("pinch")
        val homeApps = stringPreferencesKey("homeApps")
        val dockApps = stringPreferencesKey("dockApps")
        val favorites = stringPreferencesKey("favorites")
        val hidden = stringPreferencesKey("hidden")
        val recentSearches = stringPreferencesKey("recentSearches")
        val recentLaunches = stringPreferencesKey("recentLaunches")
        val widgets = stringPreferencesKey("widgets")
        val glassWidgets = booleanPreferencesKey("glassWidgets")
        val seeded = booleanPreferencesKey("seeded")
    }

    private fun lines(s: String?) = s?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    private inline fun <reified E : Enum<E>> enumOf(s: String?, d: E) =
        s?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: d

    val flow: Flow<GlassSettings> = context.store.data.map { p ->
        val d = GlassSettings()
        GlassSettings(
            transparency = p[K.transparency] ?: d.transparency,
            blur = p[K.blur] ?: d.blur,
            border = p[K.border] ?: d.border,
            accentHue = p[K.accentHue] ?: d.accentHue,
            animSpeed = p[K.animSpeed] ?: d.animSpeed,
            iconSize = p[K.iconSize] ?: d.iconSize,
            gridCols = p[K.gridCols] ?: d.gridCols,
            gridRows = p[K.gridRows] ?: d.gridRows,
            dockCount = p[K.dockCount] ?: d.dockCount,
            clockStyle = p[K.clockStyle] ?: d.clockStyle,
            appearance = p[K.appearance] ?: d.appearance,
            wallpaperUri = p[K.wallpaperUri] ?: "",
            useSystemWallpaper = p[K.useSystemWallpaper] ?: d.useSystemWallpaper,
            dim = p[K.dim] ?: d.dim,
            parallax = p[K.parallax] ?: d.parallax,
            ambientFx = p[K.ambientFx] ?: d.ambientFx,
            iconPack = p[K.iconPack] ?: "",
            dockMaterial = enumOf(p[K.dockMaterial], d.dockMaterial),
            cardMaterial = enumOf(p[K.cardMaterial], d.cardMaterial),
            iconMaterial = enumOf(p[K.iconMaterial], d.iconMaterial),
            swipeUp = enumOf(p[K.swipeUp], d.swipeUp),
            swipeDown = enumOf(p[K.swipeDown], d.swipeDown),
            swipeLeft = enumOf(p[K.swipeLeft], d.swipeLeft),
            swipeRight = enumOf(p[K.swipeRight], d.swipeRight),
            doubleTap = enumOf(p[K.doubleTap], d.doubleTap),
            longPress = enumOf(p[K.longPress], d.longPress),
            pinch = enumOf(p[K.pinch], d.pinch),
            homeApps = lines(p[K.homeApps]),
            dockApps = lines(p[K.dockApps]),
            favorites = lines(p[K.favorites]),
            hidden = lines(p[K.hidden]).toSet(),
            recentSearches = lines(p[K.recentSearches]),
            recentLaunches = lines(p[K.recentLaunches]),
            widgets = lines(p[K.widgets]).mapNotNull { WidgetPlacement.decode(it) },
            glassWidgets = p[K.glassWidgets] ?: d.glassWidgets,
            seeded = p[K.seeded] ?: false,
        )
    }

    suspend fun update(block: (GlassSettings) -> GlassSettings) {
        context.store.edit { p ->
            val cur = readNow(p)
            val n = block(cur)
            p[K.transparency] = n.transparency; p[K.blur] = n.blur; p[K.border] = n.border
            p[K.accentHue] = n.accentHue; p[K.animSpeed] = n.animSpeed; p[K.iconSize] = n.iconSize
            p[K.gridCols] = n.gridCols; p[K.gridRows] = n.gridRows; p[K.dockCount] = n.dockCount
            p[K.clockStyle] = n.clockStyle; p[K.appearance] = n.appearance
            p[K.wallpaperUri] = n.wallpaperUri; p[K.useSystemWallpaper] = n.useSystemWallpaper
            p[K.dim] = n.dim; p[K.parallax] = n.parallax; p[K.ambientFx] = n.ambientFx
            p[K.iconPack] = n.iconPack
            p[K.dockMaterial] = n.dockMaterial.name; p[K.cardMaterial] = n.cardMaterial.name
            p[K.iconMaterial] = n.iconMaterial.name
            p[K.swipeUp] = n.swipeUp.name; p[K.swipeDown] = n.swipeDown.name
            p[K.swipeLeft] = n.swipeLeft.name; p[K.swipeRight] = n.swipeRight.name
            p[K.doubleTap] = n.doubleTap.name; p[K.longPress] = n.longPress.name; p[K.pinch] = n.pinch.name
            p[K.homeApps] = n.homeApps.joinToString("\n"); p[K.dockApps] = n.dockApps.joinToString("\n")
            p[K.favorites] = n.favorites.joinToString("\n"); p[K.hidden] = n.hidden.joinToString("\n")
            p[K.recentSearches] = n.recentSearches.joinToString("\n")
            p[K.recentLaunches] = n.recentLaunches.joinToString("\n")
            p[K.widgets] = n.widgets.joinToString("\n") { it.encode() }
            p[K.glassWidgets] = n.glassWidgets; p[K.seeded] = n.seeded
        }
    }

    // Re-use the mapping logic on a raw Preferences snapshot (inside edit{}).
    private fun readNow(p: Preferences): GlassSettings {
        val d = GlassSettings()
        return GlassSettings(
            transparency = p[K.transparency] ?: d.transparency, blur = p[K.blur] ?: d.blur,
            border = p[K.border] ?: d.border, accentHue = p[K.accentHue] ?: d.accentHue,
            animSpeed = p[K.animSpeed] ?: d.animSpeed, iconSize = p[K.iconSize] ?: d.iconSize,
            gridCols = p[K.gridCols] ?: d.gridCols, gridRows = p[K.gridRows] ?: d.gridRows,
            dockCount = p[K.dockCount] ?: d.dockCount, clockStyle = p[K.clockStyle] ?: d.clockStyle,
            appearance = p[K.appearance] ?: d.appearance, wallpaperUri = p[K.wallpaperUri] ?: "",
            useSystemWallpaper = p[K.useSystemWallpaper] ?: d.useSystemWallpaper,
            dim = p[K.dim] ?: d.dim, parallax = p[K.parallax] ?: d.parallax,
            ambientFx = p[K.ambientFx] ?: d.ambientFx, iconPack = p[K.iconPack] ?: "",
            dockMaterial = enumOf(p[K.dockMaterial], d.dockMaterial),
            cardMaterial = enumOf(p[K.cardMaterial], d.cardMaterial),
            iconMaterial = enumOf(p[K.iconMaterial], d.iconMaterial),
            swipeUp = enumOf(p[K.swipeUp], d.swipeUp), swipeDown = enumOf(p[K.swipeDown], d.swipeDown),
            swipeLeft = enumOf(p[K.swipeLeft], d.swipeLeft), swipeRight = enumOf(p[K.swipeRight], d.swipeRight),
            doubleTap = enumOf(p[K.doubleTap], d.doubleTap), longPress = enumOf(p[K.longPress], d.longPress),
            pinch = enumOf(p[K.pinch], d.pinch),
            homeApps = lines(p[K.homeApps]), dockApps = lines(p[K.dockApps]),
            favorites = lines(p[K.favorites]), hidden = lines(p[K.hidden]).toSet(),
            recentSearches = lines(p[K.recentSearches]), recentLaunches = lines(p[K.recentLaunches]),
            widgets = lines(p[K.widgets]).mapNotNull { WidgetPlacement.decode(it) },
            glassWidgets = p[K.glassWidgets] ?: d.glassWidgets, seeded = p[K.seeded] ?: false,
        )
    }
}
