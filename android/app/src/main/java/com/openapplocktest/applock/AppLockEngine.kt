package com.openapplocktest.applock

import android.util.Log

class AppLockEngine {

    companion object {
        private const val TAG = "OpenAppLockEngine"

        // Temporary test package.
        // We will replace this with the user's selected apps later.
        private const val TEST_PROTECTED_PACKAGE = "org.telegram.messenger"
    }

    private var lastForegroundPackage: String? = null

    fun onForegroundPackageChanged(packageName: String) {
        // Ignore duplicate accessibility events.
        if (packageName == lastForegroundPackage) {
            return
        }

        lastForegroundPackage = packageName

        Log.d(TAG, "Foreground app changed: $packageName")

        if (isProtected(packageName)) {
            Log.d(TAG, "PROTECTED APP DETECTED: $packageName")
        } else {
            Log.d(TAG, "App is not protected: $packageName")
        }
    }

    private fun isProtected(packageName: String): Boolean {
        return packageName == TEST_PROTECTED_PACKAGE
    }
}