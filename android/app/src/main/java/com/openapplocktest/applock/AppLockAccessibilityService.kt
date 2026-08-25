package com.openapplocktest.applock

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OpenAppLock"
    }

    private val appLockEngine by lazy {
      AppLockEngine(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }

        val packageName = event.packageName?.toString()

        if (!packageName.isNullOrEmpty()) {
            appLockEngine.onForegroundPackageChanged(packageName)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
}