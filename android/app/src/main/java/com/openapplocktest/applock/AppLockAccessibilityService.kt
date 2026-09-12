package com.openapplocktest.applock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OpenAppLock"

        @Volatile
        private var serviceInstance: AppLockAccessibilityService? = null

        @Volatile
        private var pendingLockTarget: String? = null

        fun prepareForLock(packageName: String) {
            pendingLockTarget = packageName

            val service = serviceInstance

            if (service == null) {
                Log.w(
                    TAG,
                    "Cannot prepare lock: accessibility service is not connected"
                )
                return
            }

            Log.d(
                TAG,
                "Preparing lock by moving to Home before LockActivity: $packageName"
            )

            val movedHome = service.performGlobalAction(
                GLOBAL_ACTION_HOME
            )

            if (!movedHome) {
                Log.w(
                    TAG,
                    "GLOBAL_ACTION_HOME was not accepted for: $packageName"
                )

                service.launchPendingLockActivityIfReady(
                    packageName
                )
            }
        }

        fun clearPendingLock(packageName: String? = null) {
            if (
                packageName == null ||
                pendingLockTarget == packageName
            ) {
                pendingLockTarget = null
            }
        }
    }

    private val appLockEngine by lazy {
        AppLockEngine(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInstance = this

        Log.d(
            TAG,
            "Accessibility Service connected"
        )
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (event == null) {
            return
        }

        if (
            event.eventType !=
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString()

        if (packageName.isNullOrEmpty()) {
            return
        }

        /*
         * If a protected app was just detected, Home has been requested
         * before LockActivity is shown. Wait for the Home/Launcher window
         * so the protected app cannot finish another Activity transition
         * underneath the lock screen.
         */
        val pendingTarget = pendingLockTarget

        if (pendingTarget != null) {
            if (
                packageName == pendingTarget
            ) {
                Log.d(
                    TAG,
                    "Ignoring target foreground while waiting for Home: $packageName"
                )

                return
            }

            if (
                packageName == "com.openapplocktest.applock"
            ) {
                Log.d(
                    TAG,
                    "LockActivity became foreground for pending target: $pendingTarget"
                )

                return
            }

            if (
                packageName == "com.android.systemui"
            ) {
                return
            }

            if (isHomePackage(packageName, event)) {
                launchPendingLockActivityIfReady(
                    pendingTarget
                )
                return
            }

            /*
             * Some launchers expose a package that cannot be resolved via
             * the HOME intent. If the event looks like a launcher window,
             * accept it as Home as well.
             */
            if (isLauncherLikeEvent(event)) {
                launchPendingLockActivityIfReady(
                    pendingTarget
                )
                return
            }

            /*
             * Do not feed unrelated transition events to the engine while
             * the protection flow is waiting for Home.
             */
            return
        }

        /*
         * OpenAppLock's own lock activity is not a real app exit.
         */
        if (
            packageName == "com.openapplocktest.applock"
        ) {
            return
        }

        /*
         * System UI notification shade changes are not app exits.
         * Keep explicit Recents/Overview transitions flowing through.
         */
        if (
            packageName == "com.android.systemui" &&
            !isRecentsEvent(event)
        ) {
            return
        }

        appLockEngine.onForegroundPackageChanged(
            packageName
        )
    }

    private fun launchPendingLockActivityIfReady(
        packageName: String
    ) {
        if (
            pendingLockTarget != packageName
        ) {
            return
        }

        pendingLockTarget = null

        val intent =
            Intent(
                this,
                LockActivity::class.java
            ).apply {
                putExtra(
                    LockActivity.EXTRA_TARGET_PACKAGE,
                    packageName
                )
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }

        try {
            startActivity(intent)

            Log.d(
                TAG,
                "LockActivity launched after Home for: $packageName"
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to launch LockActivity for: $packageName",
                exception
            )
        }
    }

    private fun isHomePackage(
        packageName: String,
        event: AccessibilityEvent
    ): Boolean {
        val homeIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }

        val resolveInfo =
            packageManager.resolveActivity(
                homeIntent,
                0
            )

        val resolvedPackage =
            resolveInfo?.activityInfo?.packageName

        if (
            resolvedPackage != null &&
            packageName == resolvedPackage
        ) {
            return true
        }

        return isLauncherLikeEvent(event)
    }

    private fun isLauncherLikeEvent(
        event: AccessibilityEvent
    ): Boolean {
        val className =
            event.className
                ?.toString()
                ?.lowercase()
                ?: ""

        val contentDescription =
            event.contentDescription
                ?.toString()
                ?.lowercase()
                ?: ""

        val text =
            event.text
                ?.joinToString(" ")
                ?.lowercase()
                ?: ""

        return className.contains("launcher") ||
            className.contains("home") ||
            contentDescription.contains("launcher") ||
            contentDescription.contains("home") ||
            text.contains("launcher")
    }

    private fun isRecentsEvent(
        event: AccessibilityEvent
    ): Boolean {
        val className =
            event.className
                ?.toString()
                ?.lowercase()
                ?: ""

        val contentDescription =
            event.contentDescription
                ?.toString()
                ?.lowercase()
                ?: ""

        val text =
            event.text
                ?.joinToString(" ")
                ?.lowercase()
                ?: ""

        return className.contains("recent") ||
            className.contains("overview") ||
            contentDescription.contains("recent") ||
            contentDescription.contains("overview") ||
            text.contains("recent") ||
            text.contains("overview")
    }

    override fun onInterrupt() {
        Log.d(
            TAG,
            "Accessibility Service interrupted"
        )
    }

    override fun onDestroy() {
        pendingLockTarget = null

        if (serviceInstance === this) {
            serviceInstance = null
        }

        super.onDestroy()
    }
}
