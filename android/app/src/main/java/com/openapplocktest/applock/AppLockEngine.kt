package com.openapplocktest.applock

import android.content.Context
import android.content.Intent
import android.util.Log

class AppLockEngine(context: Context) {

    companion object {
        private const val TAG = "OpenAppLockEngine"
    }

    private val appContext = context.applicationContext

    private val protectedAppsRepository =
        ProtectedAppsRepository(appContext)

    private var lastForegroundPackage: String? = null

    fun onForegroundPackageChanged(packageName: String) {

        // Ignore duplicate accessibility events.
        if (packageName == lastForegroundPackage) {
            return
        }

        lastForegroundPackage = packageName

        Log.d(
            TAG,
            "Foreground app changed: $packageName"
        )

        /*
         * OpenAppLock itself is not a protected app.
         *
         * More importantly, while LockActivity is displayed,
         * Android may report OpenAppLock as the foreground app.
         * We must not clear the authenticated target because
         * of that temporary transition.
         */
        if (
            packageName ==
            appContext.packageName
        ) {
            Log.d(
                TAG,
                "Ignoring OpenAppLock foreground event"
            )
            return
        }

        /*
         * If the user moves to another app, the previous
         * authenticated session is no longer valid.
         */
        LockSessionManager.clearIfDifferent(
            packageName
        )

        /*
         * If this app is already authenticated during the
         * current session, allow it to remain open.
         */
        if (
            LockSessionManager.isAuthenticated(
                packageName
            )
        ) {
            Log.d(
                TAG,
                "App already authenticated: $packageName"
            )
            return
        }

        /*
         * Check whether the foreground app is protected.
         */
        if (
            protectedAppsRepository.isProtected(
                packageName
            )
        ) {

            Log.d(
                TAG,
                "PROTECTED APP DETECTED: $packageName"
            )

            launchLockActivity(packageName)

        } else {

            Log.d(
                TAG,
                "App is not protected: $packageName"
            )
        }
    }

    private fun launchLockActivity(
        packageName: String
    ) {

        val intent =
            Intent(
                appContext,
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

        appContext.startActivity(intent)

        Log.d(
            TAG,
            "LockActivity launched for: $packageName"
        )
    }

    fun addProtectedApp(
        packageName: String
    ) {

        protectedAppsRepository
            .addProtectedApp(packageName)

        Log.d(
            TAG,
            "Added protected app: $packageName"
        )
    }

    fun removeProtectedApp(
        packageName: String
    ) {

        protectedAppsRepository
            .removeProtectedApp(packageName)

        Log.d(
            TAG,
            "Removed protected app: $packageName"
        )
    }

    fun getProtectedApps(): Set<String> {
        return protectedAppsRepository
            .getProtectedApps()
    }
}