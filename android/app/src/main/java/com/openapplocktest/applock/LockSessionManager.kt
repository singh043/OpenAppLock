package com.openapplocktest.applock

/**
 * Keeps track of the app that has been successfully
 * authenticated during the current unlock session.
 *
 * This is intentionally in-memory.
 *
 * If the phone is restarted, the session is cleared.
 */
object LockSessionManager {

    private var authenticatedPackage: String? = null

    @Synchronized
    fun authenticate(packageName: String) {
        authenticatedPackage = packageName
    }

    @Synchronized
    fun isAuthenticated(packageName: String): Boolean {
        return authenticatedPackage == packageName
    }

    @Synchronized
    fun clear() {
        authenticatedPackage = null
    }

    @Synchronized
    fun clearIfDifferent(packageName: String) {
        if (
            authenticatedPackage != null &&
            authenticatedPackage != packageName
        ) {
            authenticatedPackage = null
        }
    }
}