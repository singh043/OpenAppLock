package com.openapplocktest.applock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OpenAppLock"

        @Volatile
        private var serviceInstance: AppLockAccessibilityService? = null

        fun showLockOverlay(packageName: String) {
            serviceInstance?.let {
                LockOverlayManager.show(packageName)
            }
        }

        fun hideLockOverlay(packageName: String? = null) {
            if (packageName == null) {
                LockOverlayManager.hide()
            } else {
                LockOverlayManager.hideIfTarget(packageName)
            }
        }
    }

    private val appLockEngine by lazy {
        AppLockEngine(applicationContext)
    }

    private val inputMethodManager by lazy {
        applicationContext.getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
    }

    private fun isInputMethodPackage(packageName: String): Boolean {
        return try {
            inputMethodManager.enabledInputMethodList.any { info ->
                info.packageName == packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        LockOverlayManager.attach(this)
        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString()
        if (packageName.isNullOrEmpty()) return

        if (packageName == applicationContext.packageName) {
            return
        }

        /*
         * Some in-app popups/dialogs bring the keyboard (IME) to the
         * foreground for a moment. That is still part of the current app
         * interaction and must not be treated as leaving the protected app.
         */
        if (isInputMethodPackage(packageName)) {
            Log.d(
                TAG,
                "Ignoring input method foreground event: $packageName"
            )
            return
        }

        /*
         * Some protected apps (for example Telegram) can open Android's
         * Credential Manager as an in-app/system dialog. The credential
         * picker temporarily becomes the foreground package, but the user
         * has not actually left the protected app. Do not let Immediate mode
         * clear the authenticated session or trigger the lock again.
         */
        if (packageName == "com.android.credentialmanager") {
            Log.d(
                TAG,
                "Ignoring Credential Manager foreground event: $packageName"
            )
            return
        }

        // Notification shade should not count as leaving an unlocked app.
        if (
            packageName == "com.android.systemui" &&
            !isRecentsEvent(event)
        ) {
            return
        }

        // Recents must be usable. Once a protected target is selected,
        // the next target event will immediately recreate the overlay.
        if (
            packageName == "com.android.systemui" &&
            isRecentsEvent(event)
        ) {
            LockOverlayManager.hide()
            appLockEngine.onForegroundPackageChanged(packageName)
            return
        }

        appLockEngine.onForegroundPackageChanged(packageName)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        LockOverlayManager.detach(this)
        if (serviceInstance === this) {
            serviceInstance = null
        }
        super.onDestroy()
    }

    private fun isRecentsEvent(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString()?.lowercase() ?: ""
        val contentDescription =
            event.contentDescription?.toString()?.lowercase() ?: ""
        val text = event.text?.joinToString(" ")?.lowercase() ?: ""

        return className.contains("recent") ||
            className.contains("overview") ||
            contentDescription.contains("recent") ||
            contentDescription.contains("overview") ||
            text.contains("recent") ||
            text.contains("overview")
    }
}
