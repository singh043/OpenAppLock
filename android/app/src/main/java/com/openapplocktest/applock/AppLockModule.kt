package com.openapplocktest.applock

import android.content.Intent
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class AppLockModule(
    reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val repository =
        ProtectedAppsRepository(reactContext.applicationContext)

    override fun getName(): String {
        return "AppLockModule"
    }

    @ReactMethod
    fun getInstalledApps(promise: Promise) {
        try {
            val packageManager = reactApplicationContext.packageManager

            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolvedApps = packageManager.queryIntentActivities(
                intent,
                0
            )

            val apps = Arguments.createArray()

            resolvedApps
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != reactApplicationContext.packageName }
                .sortedBy {
                    packageManager.getApplicationLabel(it).toString()
                }
                .forEach { applicationInfo ->

                    val app = Arguments.createMap()

                    app.putString(
                        "packageName",
                        applicationInfo.packageName
                    )

                    app.putString(
                        "appName",
                        packageManager
                            .getApplicationLabel(applicationInfo)
                            .toString()
                    )

                    app.putBoolean(
                        "isProtected",
                        repository.isProtected(
                            applicationInfo.packageName
                        )
                    )

                    apps.pushMap(app)
                }

            promise.resolve(apps)

        } catch (exception: Exception) {
            promise.reject(
                "GET_APPS_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun getProtectedApps(promise: Promise) {
        try {
            val apps = Arguments.createArray()

            repository
                .getProtectedApps()
                .forEach { packageName ->
                    apps.pushString(packageName)
                }

            promise.resolve(apps)

        } catch (exception: Exception) {
            promise.reject(
                "GET_PROTECTED_APPS_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun addProtectedApp(
        packageName: String,
        promise: Promise
    ) {
        try {
            repository.addProtectedApp(packageName)
            promise.resolve(true)

        } catch (exception: Exception) {
            promise.reject(
                "ADD_PROTECTED_APP_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun removeProtectedApp(
        packageName: String,
        promise: Promise
    ) {
        try {
            repository.removeProtectedApp(packageName)
            promise.resolve(true)

        } catch (exception: Exception) {
            promise.reject(
                "REMOVE_PROTECTED_APP_ERROR",
                exception.message,
                exception
            )
        }
    }
}