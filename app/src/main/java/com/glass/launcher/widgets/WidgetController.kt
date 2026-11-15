package com.glass.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.glass.launcher.LauncherViewModel
import com.glass.launcher.data.WidgetPlacement
import kotlin.math.ceil

const val WIDGET_COLS = 4

/** Real AppWidgetHost: allocation, bind permission, configure activity, removal. */
class WidgetController(private val activity: Activity, private val vm: LauncherViewModel) {
    private val host = GlassWidgetHost(activity, 1024)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    var pickerOpen by mutableStateOf(false)
    /** Dp size of one workspace cell; set by the workspace so new widgets get sensible spans. */
    var cellDp = 80f
    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    fun start() { runCatching { host.startListening() } }
    fun stop() { runCatching { host.stopListening() } }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(id: Int, onLongPress: () -> Unit): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        host.pendingLongPress = onLongPress
        return host.createView(activity, id, info)
    }

    fun beginAdd(info: AppWidgetProviderInfo) {
        pickerOpen = false
        val id = host.allocateAppWidgetId()
        pendingId = id; pendingInfo = info
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) afterBind()
        else runCatching {
            activity.startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), REQ_BIND)
        }.onFailure { cancel() }
    }

    private fun afterBind() {
        val info = pendingInfo ?: return
        if (info.configure != null) {
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIG, null) }
                .onFailure { commit() }   // some providers refuse; the widget still works unconfigured
        } else commit()
    }

    fun reconfigure(id: Int) {
        if (info(id)?.configure != null)
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_RECONFIG, null) }
    }

    /** Returns true if the result belonged to us. */
    fun onActivityResult(req: Int, res: Int): Boolean {
        when (req) {
            REQ_BIND -> if (res == Activity.RESULT_OK) afterBind() else cancel()
            REQ_CONFIG -> if (res == Activity.RESULT_OK) commit() else cancel()
            REQ_RECONFIG -> Unit
            else -> return false
        }
        return true
    }

    private fun cancel() { if (pendingId != -1) host.deleteAppWidgetId(pendingId); pendingId = -1; pendingInfo = null }

    private fun commit() {
        val info = pendingInfo ?: return
        val id = pendingId
        pendingId = -1; pendingInfo = null
        val d = activity.resources.displayMetrics.density
        val sx = ceil((info.minWidth / d) / cellDp).toInt().coerceIn(1, WIDGET_COLS)
        val sy = ceil((info.minHeight / d) / cellDp).toInt().coerceIn(1, 6)
        vm.edit { s ->
            val (x, y) = freeSpot(s.widgets, sx, sy)
            s.copy(widgets = s.widgets + WidgetPlacement(id, x, y, sx, sy))
        }
    }

    fun remove(id: Int) {
        host.deleteAppWidgetId(id)
        vm.edit { s -> s.copy(widgets = s.widgets.filter { it.id != id }) }
    }

    companion object {
        const val REQ_BIND = 7101
        const val REQ_CONFIG = 7102
        const val REQ_RECONFIG = 7103

        fun overlaps(a: WidgetPlacement, x: Int, y: Int, sx: Int, sy: Int) =
            x < a.cellX + a.spanX && x + sx > a.cellX && y < a.cellY + a.spanY && y + sy > a.cellY

        fun freeSpot(list: List<WidgetPlacement>, sx: Int, sy: Int): Pair<Int, Int> {
            for (y in 0..60) for (x in 0..(WIDGET_COLS - sx)) if (list.none { overlaps(it, x, y, sx, sy) }) return x to y
            return 0 to 0
        }
    }
}

/** AppWidgetHost whose views report long presses (child widgets would otherwise swallow them). */
class GlassWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    var pendingLongPress: (() -> Unit)? = null
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LongPressHostView(context).also { it.onLongPress = pendingLongPress }
}

class LongPressHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val run = Runnable { fired = true; onLongPress?.invoke() }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false; downX = ev.x; downY = ev.y
                handler.postDelayed(run, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (Math.abs(ev.x - downX) > slop || Math.abs(ev.y - downY) > slop) handler.removeCallbacks(run)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(run)
        }
        return fired
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = fired || super.onTouchEvent(event)
}
