package com.openapplocktest.applock

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class AppLockModule(
    reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(
    reactContext
) {

    companion object {

        private const val PREFS_NAME =
            "applock_settings"

        private const val AUTH_PREFS_NAME =
            "applock_auth"

        private const val KEY_SHOW_LOCK_NOTIFICATION =
            "show_lock_notification"

        private const val KEY_LOCK_BEHAVIOR =
            "lock_behavior"

        private const val KEY_LOCK_TYPE =
            "lock_type"

        private const val KEY_PENDING_LOCK_TYPE =
            "pending_lock_type"

        private const val LOCK_TYPE_PIN =
            "pin"

        private const val LOCK_TYPE_PATTERN =
            "pattern"

        private const val LOCK_TYPE_PASSWORD =
            "password"

        private const val LOCK_TYPE_BIOMETRIC =
            "biometric"

        private const val LOCK_BEHAVIOR_IMMEDIATE =
            "immediate"

        private const val LOCK_BEHAVIOR_AFTER_SCREEN_LOCK =
            "after_screen_lock"

        private const val REPLACEMENT_NOTIFICATION_ID_BASE =
            70000
    }

    private val appContext =
        reactContext.applicationContext

    private val repository =
        ProtectedAppsRepository(
            appContext
        )

    private val notificationRepository =
        NotificationProtectedAppsRepository(
            appContext
        )

    private val preferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /*
     * AuthenticationManager stores the actual
     * authentication credentials and lock type
     * in applock_auth.
     *
     * LockActivity also reads lock type from
     * AuthenticationManager, so AppLockModule
     * must use the same preference storage.
     */
    private val authenticationPreferences =
        appContext.getSharedPreferences(
            AUTH_PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val authenticationManager =
        AuthenticationManager(
            appContext
        )

    override fun getName(): String {
        return "AppLockModule"
    }

    /*
     * ---------------------------------------------------------
     * PIN
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun hasPin(
        promise: Promise
    ) {

        try {

            promise.resolve(
                authenticationManager.hasPin()
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "HAS_PIN_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun createPin(
        pin: String,
        promise: Promise
    ) {

        try {

            authenticationManager.createPin(
                pin
            )

            /*
             * AuthenticationManager already sets
             * PIN as the lock type when creating
             * the first PIN.
             *
             * Do not overwrite an existing
             * selected lock type.
             */
            if (
                !authenticationPreferences.contains(
                    KEY_LOCK_TYPE
                )
            ) {

                authenticationPreferences
                    .edit()
                    .putString(
                        KEY_LOCK_TYPE,
                        LOCK_TYPE_PIN
                    )
                    .apply()
            }

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "CREATE_PIN_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun verifyPin(
        pin: String,
        promise: Promise
    ) {

        try {

            promise.resolve(
                authenticationManager.verifyPin(
                    pin
                )
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "VERIFY_PIN_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun changePin(
        currentPin: String,
        newPin: String,
        promise: Promise
    ) {

        try {

            authenticationManager.changePin(
                currentPin,
                newPin
            )

            LockSessionManager.clearAll()

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "CHANGE_PIN_ERROR",
                exception.message,
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * LOCK TYPE
     * ---------------------------------------------------------
     *
     * Supported:
     *
     * pin
     * pattern
     * password
     * biometric
     *
     * The lock type is stored in the same
     * applock_auth preferences used by
     * AuthenticationManager / LockActivity.
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun getLockType(
        promise: Promise
    ) {

        try {

            var lockType =
                authenticationPreferences.getString(
                    KEY_LOCK_TYPE,
                    null
                )

            /*
             * Migration support:
             *
             * An earlier version stored lock type
             * in applock_settings.
             *
             * If it exists there and does not yet
             * exist in applock_auth, migrate it.
             */
            if (
                lockType.isNullOrEmpty()
            ) {

                val oldLockType =
                    preferences.getString(
                        KEY_LOCK_TYPE,
                        null
                    )

                lockType =
                    oldLockType
                        ?: LOCK_TYPE_PIN

                authenticationPreferences
                    .edit()
                    .putString(
                        KEY_LOCK_TYPE,
                        lockType
                    )
                    .apply()
            }

            promise.resolve(
                lockType
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "GET_LOCK_TYPE_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun setLockType(
        lockType: String,
        promise: Promise
    ) {

        try {

            if (
                lockType != LOCK_TYPE_PIN &&
                lockType != LOCK_TYPE_PATTERN &&
                lockType != LOCK_TYPE_PASSWORD &&
                lockType != LOCK_TYPE_BIOMETRIC
            ) {

                promise.reject(
                    "INVALID_LOCK_TYPE",
                    "Invalid lock type"
                )

                return
            }

            /*
             * IMPORTANT:
             * Do not make the new lock type active yet.
             *
             * The user still has to verify the CURRENT
             * credential and create the NEW credential.
             * If we changed KEY_LOCK_TYPE here,
             * LockActivity would think the new credential
             * is already active and would show the normal
             * authentication screen instead of the
             * change-credential screen.
             */
            authenticationPreferences
                .edit()
                .putString(
                    KEY_PENDING_LOCK_TYPE,
                    lockType
                )
                .apply()

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "SET_LOCK_TYPE_ERROR",
                exception.message,
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * LOCK TYPE SETUP
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun openLockTypeSetup(
        promise: Promise
    ) {
        try {
            val pendingType =
                authenticationPreferences.getString(
                    KEY_PENDING_LOCK_TYPE,
                    null
                )

            if (
                pendingType.isNullOrEmpty()
            ) {
                promise.reject(
                    "NO_PENDING_LOCK_TYPE",
                    "No lock type change is pending."
                )
                return
            }

            if (
                pendingType != LOCK_TYPE_PIN &&
                pendingType != LOCK_TYPE_PATTERN &&
                pendingType != LOCK_TYPE_PASSWORD &&
                pendingType != LOCK_TYPE_BIOMETRIC
            ) {
                promise.reject(
                    "INVALID_PENDING_LOCK_TYPE",
                    "Invalid pending lock type."
                )
                return
            }

            val intent =
                Intent(
                    reactApplicationContext,
                    LockActivity::class.java
                ).apply {
                    putExtra(
                        LockActivity.EXTRA_CHANGE_CREDENTIAL,
                        true
                    )
                    putExtra(
                        LockActivity.EXTRA_NEW_LOCK_TYPE,
                        pendingType
                    )
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            reactApplicationContext
                .startActivity(
                    intent
                )

            promise.resolve(
                true
            )
        } catch (
            exception: Exception
        ) {
            promise.reject(
                "OPEN_LOCK_TYPE_SETUP_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun isLockTypeConfigured(
        lockType: String,
        promise: Promise
    ) {
        try {
            val configured =
                when (lockType) {
                    LOCK_TYPE_PIN ->
                        authenticationManager.hasPin() &&
                            authenticationManager.getLockType() ==
                            LOCK_TYPE_PIN

                    LOCK_TYPE_PATTERN ->
                        authenticationManager.hasPattern() &&
                            authenticationManager.getLockType() ==
                            LOCK_TYPE_PATTERN

                    LOCK_TYPE_PASSWORD ->
                        authenticationManager.hasPassword() &&
                            authenticationManager.getLockType() ==
                            LOCK_TYPE_PASSWORD

                    LOCK_TYPE_BIOMETRIC ->
                        authenticationManager.getLockType() ==
                            LOCK_TYPE_BIOMETRIC

                    else -> false
                }

            if (configured) {
                authenticationPreferences
                    .edit()
                    .remove(
                        KEY_PENDING_LOCK_TYPE
                    )
                    .apply()
            }

            promise.resolve(
                configured
            )
        } catch (
            exception: Exception
        ) {
            promise.reject(
                "LOCK_TYPE_CONFIGURED_ERROR",
                exception.message,
                exception
            )
        }
    }

        /*
     * ---------------------------------------------------------
     * INSTALLED APPS
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun getInstalledApps(
        promise: Promise
    ) {

        try {

            val packageManager =
                reactApplicationContext
                    .packageManager

            val intent =
                Intent(
                    Intent.ACTION_MAIN
                ).apply {

                    addCategory(
                        Intent.CATEGORY_LAUNCHER
                    )
                }

            val resolvedApps =
                packageManager
                    .queryIntentActivities(
                        intent,
                        0
                    )

            val apps =
                Arguments.createArray()

            resolvedApps
                .map {
                    it.activityInfo
                        .applicationInfo
                }
                .distinctBy {
                    it.packageName
                }
                .filter {
                    it.packageName !=
                        reactApplicationContext
                            .packageName
                }
                .sortedBy {

                    packageManager
                        .getApplicationLabel(
                            it
                        )
                        .toString()
                        .lowercase()
                }
                .forEach { applicationInfo ->

                    val packageName =
                        applicationInfo.packageName

                    val app =
                        Arguments.createMap()

                    app.putString(
                        "packageName",
                        packageName
                    )

                    app.putString(
                        "appName",
                        packageManager
                            .getApplicationLabel(
                                applicationInfo
                            )
                            .toString()
                    )

                    app.putBoolean(
                        "isProtected",
                        repository.isProtected(
                            packageName
                        )
                    )

                    app.putBoolean(
                        "isNotificationProtected",
                        notificationRepository
                            .isNotificationProtected(
                                packageName
                            )
                    )

                    val iconData =
                        try {

                            val drawable =
                                applicationInfo
                                    .loadIcon(
                                        packageManager
                                    )

                            drawableToDataUri(
                                drawable
                            )

                        } catch (
                            exception: Exception
                        ) {

                            null
                        }

                    if (
                        iconData != null
                    ) {

                        app.putString(
                            "icon",
                            iconData
                        )
                    }

                    apps.pushMap(
                        app
                    )
                }

            promise.resolve(
                apps
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "GET_APPS_ERROR",
                exception.message,
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * APP ICON CONVERSION
     * ---------------------------------------------------------
     */

    private fun drawableToDataUri(
        drawable: Drawable
    ): String {

        val width =
            if (
                drawable.intrinsicWidth > 0
            ) {
                drawable.intrinsicWidth
            } else {
                96
            }

        val height =
            if (
                drawable.intrinsicHeight > 0
            ) {
                drawable.intrinsicHeight
            } else {
                96
            }

        val safeWidth =
            width.coerceAtMost(
                192
            )

        val safeHeight =
            height.coerceAtMost(
                192
            )

        val bitmap =
            Bitmap.createBitmap(
                safeWidth,
                safeHeight,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(
                bitmap
            )

        drawable.setBounds(
            0,
            0,
            safeWidth,
            safeHeight
        )

        drawable.draw(
            canvas
        )

        val outputStream =
            java.io.ByteArrayOutputStream()

        bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            outputStream
        )

        bitmap.recycle()

        val encoded =
            android.util.Base64.encodeToString(
                outputStream.toByteArray(),
                android.util.Base64.NO_WRAP
            )

        return "data:image/png;base64,$encoded"
    }

    /*
     * ---------------------------------------------------------
     * PROTECTED APPS
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun getProtectedApps(
        promise: Promise
    ) {

        try {

            val apps =
                Arguments.createArray()

            repository
                .getProtectedApps()
                .forEach { packageName ->

                    apps.pushString(
                        packageName
                    )
                }

            promise.resolve(
                apps
            )

        } catch (
            exception: Exception
        ) {

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

            repository.addProtectedApp(
                packageName
            )

            notificationRepository
                .enableByDefault(
                    packageName
                )

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

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

            repository.removeProtectedApp(
                packageName
            )

            notificationRepository.remove(
                packageName
            )

            cancelReplacementNotification(
                packageName
            )

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "REMOVE_PROTECTED_APP_ERROR",
                exception.message,
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * PER-APP NOTIFICATION PRIVACY
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun isNotificationProtectedApp(
        packageName: String,
        promise: Promise
    ) {

        try {

            promise.resolve(
                notificationRepository
                    .isNotificationProtected(
                        packageName
                    )
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "IS_NOTIFICATION_PROTECTED_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun setNotificationProtectedApp(
        packageName: String,
        enabled: Boolean,
        promise: Promise
    ) {

        try {

            notificationRepository
                .setNotificationProtected(
                    packageName,
                    enabled
                )

            if (!enabled) {

                cancelReplacementNotification(
                    packageName
                )
            }

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "SET_NOTIFICATION_PROTECTED_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun getNotificationProtectedApps(
        promise: Promise
    ) {

        try {

            val apps =
                Arguments.createArray()

            notificationRepository
                .getNotificationProtectedApps()
                .forEach { packageName ->

                    apps.pushString(
                        packageName
                    )
                }

            promise.resolve(
                apps
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "GET_NOTIFICATION_PROTECTED_APPS_ERROR",
                exception.message,
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * ACCESSIBILITY
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun isAccessibilityServiceEnabled(
        promise: Promise
    ) {

        try {

            val accessibilityServices =
                Settings.Secure.getString(
                    reactApplicationContext
                        .contentResolver,
                    Settings.Secure
                        .ENABLED_ACCESSIBILITY_SERVICES
                )

            val expectedService =
                ComponentName(
                    reactApplicationContext,
                    AppLockAccessibilityService::class.java
                ).flattenToString()

            val enabled =
                accessibilityServices
                    ?.split(":")
                    ?.any {
                        it.equals(
                            expectedService,
                            ignoreCase = true
                        )
                    }
                    ?: false

            promise.resolve(
                enabled
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "ACCESSIBILITY_CHECK_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun openAccessibilitySettings() {

        val intent =
            Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        reactApplicationContext
            .startActivity(
                intent
            )
    }

    /*
     * ---------------------------------------------------------
     * GLOBAL NOTIFICATION SETTING
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun getShowLockNotification(
        promise: Promise
    ) {

        try {

            val enabled =
                preferences.getBoolean(
                    KEY_SHOW_LOCK_NOTIFICATION,
                    true
                )

            promise.resolve(
                enabled
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "GET_NOTIFICATION_SETTING_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun setShowLockNotification(
        enabled: Boolean,
        promise: Promise
    ) {

        try {

            preferences
                .edit()
                .putBoolean(
                    KEY_SHOW_LOCK_NOTIFICATION,
                    enabled
                )
                .apply()

            if (!enabled) {

                notificationRepository
                    .getNotificationProtectedApps()
                    .forEach { packageName ->

                        cancelReplacementNotification(
                            packageName
                        )
                    }
            }

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "SET_NOTIFICATION_SETTING_ERROR",
                exception.message,
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * NOTIFICATION ACCESS
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun isNotificationListenerEnabled(
        promise: Promise
    ) {

        try {

            val notificationManager =
                reactApplicationContext
                    .getSystemService(
                        Context.NOTIFICATION_SERVICE
                    ) as NotificationManager

            val component =
                ComponentName(
                    reactApplicationContext,
                    AppLockNotificationListenerService::class.java
                )

            val enabled =
                notificationManager
                    .isNotificationListenerAccessGranted(
                        component
                    )

            promise.resolve(
                enabled
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "NOTIFICATION_ACCESS_CHECK_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun openNotificationAccessSettings() {

        val intent =
            Intent(
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        reactApplicationContext
            .startActivity(
                intent
            )
    }

    /*
     * ---------------------------------------------------------
     * LOCK BEHAVIOR
     * ---------------------------------------------------------
     */

    @ReactMethod
    fun getLockBehavior(
        promise: Promise
    ) {

        try {

            val behavior =
                preferences.getString(
                    KEY_LOCK_BEHAVIOR,
                    LOCK_BEHAVIOR_IMMEDIATE
                )
                    ?: LOCK_BEHAVIOR_IMMEDIATE

            promise.resolve(
                behavior
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "GET_LOCK_BEHAVIOR_ERROR",
                exception.message,
                exception
            )
        }
    }

    @ReactMethod
    fun setLockBehavior(
        behavior: String,
        promise: Promise
    ) {

        try {

            if (
                behavior !=
                    LOCK_BEHAVIOR_IMMEDIATE &&
                behavior !=
                    LOCK_BEHAVIOR_AFTER_SCREEN_LOCK
            ) {

                promise.reject(
                    "INVALID_LOCK_BEHAVIOR",
                    "Invalid lock behavior"
                )

                return
            }

            preferences
                .edit()
                .putString(
                    KEY_LOCK_BEHAVIOR,
                    behavior
                )
                .apply()

            if (
                behavior ==
                    LOCK_BEHAVIOR_IMMEDIATE
            ) {

                LockSessionManager.clear()
            }

            promise.resolve(
                true
            )

        } catch (
            exception: Exception
        ) {

            promise.reject(
                "SET_LOCK_BEHAVIOR_ERROR",
                exception.message,
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * REPLACEMENT NOTIFICATION CLEANUP
     * ---------------------------------------------------------
     */

    private fun cancelReplacementNotification(
        packageName: String
    ) {

        try {

            val notificationManager =
                appContext.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            val hash =
                packageName.hashCode() and
                    0x7fffffff

            val notificationId =
                REPLACEMENT_NOTIFICATION_ID_BASE +
                    (hash % 10000)

            notificationManager.cancel(
                notificationId
            )

        } catch (
            exception: Exception
        ) {
            // Ignore cleanup errors.
        }
    }
}