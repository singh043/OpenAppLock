package com.openapplocktest.applock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

class AppLockEngine(context: Context) {
    companion object {
        private const val TAG = "OpenAppLockEngine"
        private const val PREFS_NAME = "applock_settings"
        private const val KEY_LOCK_BEHAVIOR = "lock_behavior"

        const val LOCK_BEHAVIOR_IMMEDIATE = "immediate"
        const val LOCK_BEHAVIOR_AFTER_SCREEN_LOCK = "after_screen_lock"
    }

    private val appContext = context.applicationContext
    private val protectedAppsRepository = ProtectedAppsRepository(appContext)
    private val preferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyguardManager by lazy {
        appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private var lastForegroundPackage: String? = null
    private var packageBeforeScreenLock: String? = null
    private var screenLocked = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenLocked = true
                    packageBeforeScreenLock = lastForegroundPackage
                    lastForegroundPackage = null

                    // Keep the phone's own lock screen above AppLock.
                    LockOverlayManager.hide()

                    if (getLockBehavior() == LOCK_BEHAVIOR_AFTER_SCREEN_LOCK) {
                        LockSessionManager.clear()
                    }
                }

                Intent.ACTION_SCREEN_ON -> {
                    // SCREEN_ON is not the same as USER_UNLOCKED.
                    LockOverlayManager.hide()
                }

                Intent.ACTION_USER_UNLOCKED -> {
                    if (!keyguardManager.isKeyguardLocked) {
                        screenLocked = false

                        val packageToRecheck = packageBeforeScreenLock
                        packageBeforeScreenLock = null

                        if (!packageToRecheck.isNullOrEmpty()) {
                            onForegroundPackageChanged(packageToRecheck)
                        }
                    }
                }
            }
        }
    }

    init {
        appContext.registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_UNLOCKED)
            }
        )
    }

    fun onForegroundPackageChanged(packageName: String) {
        if (screenLocked && !keyguardManager.isKeyguardLocked) {
            screenLocked = false
        }

        if (screenLocked || keyguardManager.isKeyguardLocked) {
            LockOverlayManager.hide()
            return
        }

        // Never treat our own package as leaving the protected app.
        if (packageName == appContext.packageName) return

        val shownTarget = LockOverlayManager.currentTargetPackage()

        /*
         * A lock Activity is an authentication barrier, not a normal
         * foreground-app transition. If the same protected package is still
         * the target, do not dismiss the lock just because another window
         * callback arrived during Activity/keyboard transitions.
         */
        if (shownTarget == packageName &&
            !LockSessionManager.isAuthenticated(packageName)
        ) {
            lastForegroundPackage = packageName
            if (!LockOverlayManager.isShowingFor(packageName)) {
                Log.d(TAG, "LOCK UI MISSING - retrying: $packageName")
                AppLockAccessibilityService.showLockOverlay(packageName)
            }
            return
        }

        if (packageName == lastForegroundPackage) {
            if (
                !LockSessionManager.isAuthenticated(packageName) &&
                protectedAppsRepository.isProtected(packageName) &&
                !LockOverlayManager.isShowingFor(packageName)
            ) {
                Log.d(TAG, "LOCK UI MISSING - retrying: $packageName")
                AppLockAccessibilityService.showLockOverlay(packageName)
            }
            return
        }

        lastForegroundPackage = packageName
        Log.d(TAG, "Foreground app changed: $packageName")

        if (shownTarget != null && shownTarget != packageName) {
            /*
             * A different package event can be a stale accessibility callback
             * from the underlying/task transition while our lock Activity is
             * still actually on screen.
             *
             * Do NOT dismiss an active authentication barrier in that case.
             * If the user really leaves the protected app (Home/another app),
             * LockScreenActivity will be paused first, and the later foreground
             * event is then allowed to hide it.
             */
            if (LockScreenActivity.isResumed()) {
                Log.d(
                    TAG,
                    "Ignoring stale package event while lock is resumed: $packageName"
                )
                return
            }

            // The lock Activity is no longer visible, so the user actually
            // moved away from the protected app.
            LockOverlayManager.hide()
        }

        val lockBehavior = getLockBehavior()
        if (lockBehavior == LOCK_BEHAVIOR_IMMEDIATE) {
            LockSessionManager.clearIfDifferent(packageName)
        }

        if (LockSessionManager.isAuthenticated(packageName)) return
        if (!protectedAppsRepository.isProtected(packageName)) return

        Log.d(TAG, "PROTECTED APP DETECTED: $packageName")
        AppLockAccessibilityService.showLockOverlay(packageName)
    }

    private fun getLockBehavior(): String {
        return preferences.getString(
            KEY_LOCK_BEHAVIOR,
            LOCK_BEHAVIOR_IMMEDIATE
        ) ?: LOCK_BEHAVIOR_IMMEDIATE
    }

    fun setLockBehavior(behavior: String) {
        if (
            behavior != LOCK_BEHAVIOR_IMMEDIATE &&
            behavior != LOCK_BEHAVIOR_AFTER_SCREEN_LOCK
        ) return

        preferences.edit()
            .putString(KEY_LOCK_BEHAVIOR, behavior)
            .apply()

        if (behavior == LOCK_BEHAVIOR_IMMEDIATE) {
            LockSessionManager.clear()
        }
    }

    fun addProtectedApp(packageName: String) {
        protectedAppsRepository.addProtectedApp(packageName)
    }

    fun removeProtectedApp(packageName: String) {
        protectedAppsRepository.removeProtectedApp(packageName)
    }

    fun getProtectedApps(): Set<String> {
        return protectedAppsRepository.getProtectedApps()
    }
}
