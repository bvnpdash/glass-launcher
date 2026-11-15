package com.glass.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/** Optional. Only used to perform GLOBAL_ACTION_LOCK_SCREEN (API 28+) for the double-tap gesture. */
class LockAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { LauncherBus.lockService = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { LauncherBus.lockService = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun lock(): Boolean =
        Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
}
