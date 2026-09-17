package com.openapplocktest.applock

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.WindowManager

/**
 * Starts the dedicated lock Activity.
 *
 * IMPORTANT: No accessibility overlay or system-bar code is used here.
 */
object LockOverlayManager {
    private var service: AccessibilityService? = null
    private var windowManager: WindowManager? = null
    private var targetPackage: String? = null
    private var launchInProgress = false

    @Synchronized
    fun attach(service: AccessibilityService) {
        this.service = service
        this.windowManager =
            service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @Synchronized
    fun detach(service: AccessibilityService) {
        if (this.service !== service) return

        targetPackage = null
        launchInProgress = false
        LockScreenActivity.finishLockScreen()
        windowManager = null
        this.service = null
    }

    @Synchronized
    fun currentTargetPackage(): String? = targetPackage

    @Synchronized
    fun isShowingFor(packageName: String): Boolean {
        return targetPackage == packageName && LockScreenActivity.isResumed()
    }

    @Synchronized
    fun show(packageName: String) {
        val currentService = service ?: return

        if (LockSessionManager.isAuthenticated(packageName)) return

        // Same lock is already being launched.
        if (targetPackage == packageName && launchInProgress) return

        targetPackage = packageName
        launchInProgress = true

        try {
            val intent = Intent(
                currentService,
                LockScreenActivity::class.java
            ).apply {
                putExtra(
                    LockScreenActivity.EXTRA_TARGET_PACKAGE,
                    packageName
                )

                /*
                 * Do NOT use MULTIPLE_TASK here. Android documents that it
                 * unconditionally creates another task. NEW_TASK alone can
                 * bring the existing lock task back to the foreground.
                 */
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }

            currentService.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(
                "OpenAppLock",
                "Unable to start LockScreenActivity",
                e
            )
            targetPackage = null
        } finally {
            launchInProgress = false
        }
    }

    @Synchronized
    fun hide() {
        targetPackage = null
        launchInProgress = false
        LockScreenActivity.finishLockScreen()
    }

    @Synchronized
    fun hideIfTarget(packageName: String) {
        if (targetPackage == packageName) {
            targetPackage = null
            launchInProgress = false
            LockScreenActivity.finishLockScreen()
        }
    }

    @Synchronized
    internal fun onLockScreenActivityDestroyed(activity: LockScreenActivity) {
        if (LockScreenActivity.isCurrent(activity)) {
            targetPackage = null
            launchInProgress = false
        }
    }
}
