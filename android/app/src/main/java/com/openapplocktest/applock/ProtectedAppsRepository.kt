package com.openapplocktest.applock

import android.content.Context

class ProtectedAppsRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "applock_preferences"
        private const val KEY_PROTECTED_APPS = "protected_apps"
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun getProtectedApps(): Set<String> {
        return preferences.getStringSet(
            KEY_PROTECTED_APPS,
            emptySet()
        )?.toSet() ?: emptySet()
    }

    fun isProtected(packageName: String): Boolean {
        return getProtectedApps().contains(packageName)
    }

    fun addProtectedApp(packageName: String) {
        val apps = getProtectedApps().toMutableSet()

        if (apps.add(packageName)) {
            saveProtectedApps(apps)
        }
    }

    fun removeProtectedApp(packageName: String) {
        val apps = getProtectedApps().toMutableSet()

        if (apps.remove(packageName)) {
            saveProtectedApps(apps)
        }
    }

    private fun saveProtectedApps(apps: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_PROTECTED_APPS, apps)
            .apply()
    }
}