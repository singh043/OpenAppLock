package com.openapplocktest.applock

import android.content.Context

class NotificationProtectedAppsRepository(
    context: Context
) {

    companion object {

        private const val PREFS_NAME =
            "applock_notification_protected_apps"

        private const val KEY_PACKAGES =
            "notification_protected_packages"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

    @Synchronized
    fun getNotificationProtectedApps():
        Set<String> {

        return preferences
            .getStringSet(
                KEY_PACKAGES,
                emptySet()
            )
            ?.toSet()
            ?: emptySet()
    }

    @Synchronized
    fun isNotificationProtected(
        packageName: String
    ): Boolean {

        return getNotificationProtectedApps()
            .contains(
                packageName
            )
    }

    @Synchronized
    fun setNotificationProtected(
        packageName: String,
        enabled: Boolean
    ) {

        val packages =
            getNotificationProtectedApps()
                .toMutableSet()

        if (enabled) {

            packages.add(
                packageName
            )

        } else {

            packages.remove(
                packageName
            )
        }

        preferences
            .edit()
            .putStringSet(
                KEY_PACKAGES,
                packages
            )
            .apply()
    }

    @Synchronized
    fun remove(
        packageName: String
    ) {

        val packages =
            getNotificationProtectedApps()
                .toMutableSet()

        packages.remove(
            packageName
        )

        preferences
            .edit()
            .putStringSet(
                KEY_PACKAGES,
                packages
            )
            .apply()
    }

    @Synchronized
    fun enableByDefault(
        packageName: String
    ) {

        /*
         * New locked apps get notification privacy
         * enabled by default so the existing behavior
         * is preserved.
         */
        if (
            !isNotificationProtected(
                packageName
            )
        ) {

            setNotificationProtected(
                packageName,
                true
            )
        }
    }
}