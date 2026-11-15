package com.glass.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val packageName: String, val label: String)

/** Loads app icons once, scaled to a fixed size, with optional ADW/Nova-style icon pack support. */
class IconCache(private val context: Context) {
    private val px = (context.resources.displayMetrics.density * 64).toInt()
    private val memory = object : LruCache<String, ImageBitmap>(maxMemKb()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }
    private var packMap: Map<String, String> = emptyMap()   // "pkg/class" -> drawable name
    private var packRes: android.content.res.Resources? = null
    private var packPkg: String = ""

    private fun maxMemKb() = (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()

    fun trim() = memory.evictAll()

    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = LinkedHashSet<String>()
        for (action in listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")) {
            pm.queryIntentActivities(Intent(action), 0).forEach { seen += it.activityInfo.packageName }
        }
        return seen.map { IconPackInfo(it, pm.getApplicationInfo(it, 0).loadLabel(pm).toString()) }
    }

    suspend fun selectPack(pkg: String) = withContext(Dispatchers.IO) {
        memory.evictAll()
        if (pkg.isEmpty()) { packMap = emptyMap(); packRes = null; packPkg = ""; return@withContext }
        runCatching {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val map = HashMap<String, String>()
            val id = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (id != 0) res.getXml(id) else
                android.util.Xml.newPullParser().also {
                    it.setInput(res.assets.open("appfilter.xml"), "utf-8")
                }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null) {
                        // component="ComponentInfo{pkg/class}"
                        val c = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        map[c] = dr
                    }
                }
                ev = parser.next()
            }
            packMap = map; packRes = res; packPkg = pkg
        }.onFailure { packMap = emptyMap(); packRes = null; packPkg = "" }
    }

    suspend fun load(app: AppInfo): ImageBitmap = withContext(Dispatchers.IO) {
        val ck = packPkg + "|" + app.key
        memory.get(ck) ?: run {
            val d = fromPack(app) ?: context.packageManager.getActivityIcon(app.component)
            toBitmap(d).asImageBitmap().also { memory.put(ck, it) }
        }
    }

    fun cached(app: AppInfo): ImageBitmap? = memory.get(packPkg + "|" + app.key)

    private fun fromPack(app: AppInfo): Drawable? {
        val res = packRes ?: return null
        val name = packMap[app.key] ?: return null
        val id = res.getIdentifier(name, "drawable", packPkg)
        return if (id != 0) runCatching { res.getDrawable(id, null) }.getOrNull() else null
    }

    private fun toBitmap(d: Drawable): Bitmap {
        if (d is BitmapDrawable && d.bitmap.width <= px * 2) return d.bitmap
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, px, px); d.draw(c)
        return bmp
    }
}
