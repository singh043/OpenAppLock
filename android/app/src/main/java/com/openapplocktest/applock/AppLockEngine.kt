package com.openapplocktest.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

class AppLockEngine(context: Context) {

    companion object {
        private const val TAG =
            "OpenAppLockEngine"

        private const val PREFS_NAME =
            "applock_settings"

        private const val KEY_LOCK_BEHAVIOR =
            "lock_behavior"

        const val LOCK_BEHAVIOR_IMMEDIATE =
            "immediate"

        const val LOCK_BEHAVIOR_AFTER_SCREEN_LOCK =
            "after_screen_lock"
    }

    private val appContext =
        context.applicationContext

    private val protectedAppsRepository =
        ProtectedAppsRepository(appContext)

    private val preferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private var lastForegroundPackage:
        String? = null

    private var screenLocked =
        false

    private val screenStateReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                when (intent?.action) {

                    Intent.ACTION_SCREEN_OFF -> {

                        screenLocked = true

                        Log.d(
                            TAG,
                            "Screen locked"
                        )

                        /*
                         * Once the phone is locked, the
                         * authenticated session is cleared.
                         */
                        if (
                            getLockBehavior() ==
                            LOCK_BEHAVIOR_AFTER_SCREEN_LOCK
                        ) {

                            LockSessionManager.clear()

                            Log.d(
                                TAG,
                                "Authentication session cleared after screen lock"
                            )
                        }
                    }

                    Intent.ACTION_SCREEN_ON -> {

                        screenLocked = false

                        Log.d(
                            TAG,
                            "Screen turned on"
                        )
                    }

                    Intent.ACTION_USER_UNLOCKED -> {

                        screenLocked = false

                        Log.d(
                            TAG,
                            "Device unlocked"
                        )
                    }
                }
            }
        }

    init {

        val filter =
            IntentFilter().apply {

                addAction(
                    Intent.ACTION_SCREEN_OFF
                )

                addAction(
                    Intent.ACTION_SCREEN_ON
                )

                addAction(
                    Intent.ACTION_USER_UNLOCKED
                )
            }

        appContext.registerReceiver(
            screenStateReceiver,
            filter
        )
    }

    fun onForegroundPackageChanged(
        packageName: String
    ) {

        /*
         * Ignore duplicate accessibility events.
         */
        if (
            packageName ==
            lastForegroundPackage
        ) {
            return
        }

        lastForegroundPackage =
            packageName

        Log.d(
            TAG,
            "Foreground app changed: $packageName"
        )

        /*
         * Ignore OpenAppLock itself.
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
         * If the phone is currently locked,
         * don't process foreground changes.
         */
        if (screenLocked) {

            Log.d(
                TAG,
                "Ignoring foreground event while screen is locked"
            )

            return
        }

        val lockBehavior =
            getLockBehavior()

        /*
         * IMMEDIATE MODE
         *
         * Moving away from an authenticated app
         * immediately clears its authentication.
         */
        if (
            lockBehavior ==
            LOCK_BEHAVIOR_IMMEDIATE
        ) {

            LockSessionManager
                .clearIfDifferent(
                    packageName
                )
        }

        /*
         * AFTER SCREEN LOCK MODE
         *
         * Authentication remains valid while the
         * user moves between apps.
         */
        if (
            lockBehavior ==
            LOCK_BEHAVIOR_AFTER_SCREEN_LOCK
        ) {

            if (
                LockSessionManager
                    .isAuthenticated(
                        packageName
                    )
            ) {

                Log.d(
                    TAG,
                    "App already authenticated: $packageName"
                )

                return
            }
        }

        /*
         * If this app is authenticated, allow it.
         */
        if (
            LockSessionManager
                .isAuthenticated(
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
         * Check whether the foreground app
         * is protected.
         */
        if (
            protectedAppsRepository
                .isProtected(
                    packageName
                )
        ) {

            Log.d(
                TAG,
                "PROTECTED APP DETECTED: $packageName"
            )

            launchLockActivity(
                packageName
            )

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
                    LockActivity
                        .EXTRA_TARGET_PACKAGE,
                    packageName
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }

        appContext.startActivity(
            intent
        )

        Log.d(
            TAG,
            "LockActivity launched for: $packageName"
        )
    }

    private fun getLockBehavior():
        String {

        return preferences.getString(
            KEY_LOCK_BEHAVIOR,
            LOCK_BEHAVIOR_IMMEDIATE
        ) ?: LOCK_BEHAVIOR_IMMEDIATE
    }

    fun setLockBehavior(
        behavior: String
    ) {

        if (
            behavior !=
                LOCK_BEHAVIOR_IMMEDIATE &&
            behavior !=
                LOCK_BEHAVIOR_AFTER_SCREEN_LOCK
        ) {

            return
        }

        preferences
            .edit()
            .putString(
                KEY_LOCK_BEHAVIOR,
                behavior
            )
            .apply()

        /*
         * Changing to immediate mode should
         * invalidate the current session.
         */
        if (
            behavior ==
            LOCK_BEHAVIOR_IMMEDIATE
        ) {

            LockSessionManager.clear()
        }

        Log.d(
            TAG,
            "Lock behavior changed: $behavior"
        )
    }

    fun addProtectedApp(
        packageName: String
    ) {

        protectedAppsRepository
            .addProtectedApp(
                packageName
            )

        Log.d(
            TAG,
            "Added protected app: $packageName"
        )
    }

    fun removeProtectedApp(
        packageName: String
    ) {

        protectedAppsRepository
            .removeProtectedApp(
                packageName
            )

        Log.d(
            TAG,
            "Removed protected app: $packageName"
        )
    }

    fun getProtectedApps():
        Set<String> {

        return protectedAppsRepository
            .getProtectedApps()
    }
}