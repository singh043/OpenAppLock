package com.openapplocktest.applock

/**
 * Keeps track of authentication during the current
 * OpenAppLock session.
 *
 * Everything is intentionally kept in memory.
 *
 * Therefore:
 *
 * - Phone restart clears authentication.
 * - App process restart clears authentication.
 * - No PIN is stored here.
 *
 * There are two independent authentication states:
 *
 * 1. Protected-app authentication
 * 2. OpenAppLock settings authentication
 */
object LockSessionManager {

    private var authenticatedPackage: String? = null

    private var settingsAuthenticated =
        false

    /*
     * ---------------------------------------------------------
     * Protected application authentication
     * ---------------------------------------------------------
     */

    @Synchronized
    fun authenticate(
        packageName: String
    ) {
        authenticatedPackage =
            packageName
    }

    @Synchronized
    fun isAuthenticated(
        packageName: String
    ): Boolean {
        return authenticatedPackage ==
            packageName
    }

    @Synchronized
    fun clear() {
        authenticatedPackage = null
    }

    @Synchronized
    fun clearIfDifferent(
        packageName: String
    ) {
        if (
            authenticatedPackage != null &&
            authenticatedPackage !=
                packageName
        ) {
            authenticatedPackage = null
        }
    }

    /*
     * ---------------------------------------------------------
     * OpenAppLock settings authentication
     * ---------------------------------------------------------
     */

    @Synchronized
    fun authenticateSettings() {
        settingsAuthenticated =
            true
    }

    @Synchronized
    fun isSettingsAuthenticated():
        Boolean {
        return settingsAuthenticated
    }

    @Synchronized
    fun clearSettingsAuthentication() {
        settingsAuthenticated =
            false
    }

    @Synchronized
    fun clearAll() {
        authenticatedPackage = null
        settingsAuthenticated = false
    }
}