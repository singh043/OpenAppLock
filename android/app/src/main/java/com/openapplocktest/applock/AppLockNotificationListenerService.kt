package com.openapplocktest.applock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AppLockNotificationListenerService :
    NotificationListenerService() {

    companion object {

        private const val TAG =
            "OpenAppLockNotification"

        private const val CHANNEL_ID =
            "applock_locked_notifications_v3"

        private const val CHANNEL_NAME =
            "Locked App Notifications"

        private const val CHANNEL_DESCRIPTION =
            "Notifications from locked apps"

        private const val NOTIFICATION_ID_BASE =
            41000

        private const val APPLock_NOTIFICATION_EXTRA =
            "applock_notification"

        private const val APPLock_NOTIFICATION_VALUE =
            "true"

        private const val PREFS_NAME =
            "applock_settings"

        private const val KEY_SHOW_LOCK_NOTIFICATION =
            "show_lock_notification"
    }

    private lateinit var protectedAppsRepository:
        ProtectedAppsRepository

    private lateinit var notificationRepository:
        NotificationProtectedAppsRepository

    override fun onCreate() {
        super.onCreate()

        protectedAppsRepository =
            ProtectedAppsRepository(
                applicationContext
            )

        notificationRepository =
            NotificationProtectedAppsRepository(
                applicationContext
            )

        createNotificationChannel()

        Log.d(
            TAG,
            "Notification listener created"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        createNotificationChannel()

        Log.d(
            TAG,
            "Notification listener connected"
        )
    }

    /*
     * ---------------------------------------------------------
     * NOTIFICATION CHANNEL
     * ---------------------------------------------------------
     */

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O
        ) {
            return
        }

        val notificationManager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        /*
         * Delete old OpenAppLock channels.
         */
        notificationManager.deleteNotificationChannel(
            "applock_lock_channel"
        )

        notificationManager.deleteNotificationChannel(
            "applock_locked_notifications_v2"
        )

        /*
         * LOW importance means the OpenAppLock
         * replacement notification should not create
         * a heads-up notification.
         */
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    CHANNEL_DESCRIPTION

                setSound(
                    null,
                    null
                )

                enableVibration(
                    false
                )

                setShowBadge(
                    false
                )

                lockscreenVisibility =
                    android.app.Notification
                        .VISIBILITY_PRIVATE
            }

        notificationManager
            .createNotificationChannel(
                channel
            )

        Log.d(
            TAG,
            "Notification channel ready"
        )
    }

    /*
     * ---------------------------------------------------------
     * NOTIFICATION POSTED
     * ---------------------------------------------------------
     */

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        val packageName =
            sbn.packageName

        /*
         * Never process OpenAppLock's own notifications.
         */
        if (
            packageName ==
                applicationContext.packageName
        ) {
            return
        }

        /*
         * Ignore anything that isn't protected.
         */
        if (
            !protectedAppsRepository
                .isProtected(
                    packageName
                )
        ) {
            return
        }

        /*
         * Ignore our replacement notification.
         */
        if (
            isOurNotification(
                sbn
            )
        ) {
            return
        }

        /*
         * Global notification privacy switch.
         */
        val preferences =
            applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        val globalEnabled =
            preferences.getBoolean(
                KEY_SHOW_LOCK_NOTIFICATION,
                true
            )

        if (!globalEnabled) {

            Log.d(
                TAG,
                "Global notification privacy OFF: $packageName"
            )

            return
        }

        /*
         * Individual app notification privacy.
         *
         * This MUST use NotificationProtectedAppsRepository.
         *
         * The previous implementation incorrectly used:
         *
         * hide_notifications_<package>
         *
         * which was unrelated to the repository used by
         * AppLockModule.
         */
        if (
            !notificationRepository
                .isNotificationProtected(
                    packageName
                )
        ) {

            Log.d(
                TAG,
                "Notification privacy OFF for app: $packageName"
            )

            /*
             * If an old replacement notification exists,
             * remove it now.
             */
            cancelReplacementNotification(
                packageName
            )

            return
        }

        /*
         * -----------------------------------------------------
         * IMPORTANT LOCK STATE CHECK
         * -----------------------------------------------------
         *
         * If this app has already been authenticated during
         * the current AppLock session, its notifications should
         * behave normally.
         *
         * Immediate mode:
         * authentication is cleared when the app is locked
         * again.
         *
         * After-screen-lock mode:
         * authentication remains until the phone is locked.
         */
        if (
            LockSessionManager
                .isAuthenticated(
                    packageName
                )
        ) {

            Log.d(
                TAG,
                "App currently authenticated; allowing notification: $packageName"
            )

            return
        }

        /*
         * The app is protected, notification privacy is enabled,
         * and the app is currently locked.
         *
         * Hide the original notification and show our
         * replacement notification.
         */
        Log.d(
            TAG,
            "Hiding notification from locked app: $packageName"
        )

        hideOriginalNotification(
            sbn
        )

        postReplacementNotification(
            packageName
        )
    }

    /*
     * ---------------------------------------------------------
     * CHECK OUR NOTIFICATION
     * ---------------------------------------------------------
     */

    private fun isOurNotification(
        sbn: StatusBarNotification
    ): Boolean {

        return sbn.notification
            .extras
            ?.getString(
                APPLock_NOTIFICATION_EXTRA
            ) ==
            APPLock_NOTIFICATION_VALUE
    }

    /*
     * ---------------------------------------------------------
     * HIDE ORIGINAL NOTIFICATION
     * ---------------------------------------------------------
     */

    private fun hideOriginalNotification(
        sbn: StatusBarNotification
    ) {

        try {

            cancelNotification(
                sbn.key
            )

            Log.d(
                TAG,
                "Original notification cancelled: ${sbn.key}"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "Unable to cancel original notification",
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * REPLACEMENT NOTIFICATION
     * ---------------------------------------------------------
     */

    private fun postReplacementNotification(
        packageName: String
    ) {

        val notificationId =
            notificationIdForPackage(
                packageName
            )

        /*
         * Prevent repeated calls for the same app from
         * creating multiple notifications.
         *
         * notify() with the same ID updates the existing
         * notification instead of creating another one.
         */

        val appName =
            getApplicationName(
                packageName
            )

        val lockIntent =
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
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                notificationId,
                lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_lock_lock
                )
                .setContentTitle(
                    "$appName notification hidden"
                )
                .setContentText(
                    "Unlock to view notification"
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(
                    true
                )
                .setOngoing(
                    false
                )
                .setOnlyAlertOnce(
                    true
                )
                .setSilent(
                    true
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_LOW
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_STATUS
                )
                .setVisibility(
                    NotificationCompat
                        .VISIBILITY_PRIVATE
                )
                .addExtras(
                    android.os.Bundle().apply {

                        putString(
                            APPLock_NOTIFICATION_EXTRA,
                            APPLock_NOTIFICATION_VALUE
                        )
                    }
                )
                .build()

        try {

            NotificationManagerCompat
                .from(this)
                .notify(
                    notificationId,
                    notification
                )

            Log.d(
                TAG,
                "Replacement notification posted: $packageName"

            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "Unable to post replacement notification",
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * APPLICATION NAME
     * ---------------------------------------------------------
     */

    private fun getApplicationName(
        packageName: String
    ): String {

        return try {

            val applicationInfo =
                packageManager
                    .getApplicationInfo(
                        packageName,
                        0
                    )

            packageManager
                .getApplicationLabel(
                    applicationInfo
                )
                .toString()

        } catch (
            exception: Exception
        ) {

            packageName
        }
    }

    /*
     * ---------------------------------------------------------
     * NOTIFICATION ID
     * ---------------------------------------------------------
     */

    private fun notificationIdForPackage(
        packageName: String
    ): Int {

        val hash =
            packageName.hashCode() and
                0x7fffffff

        return NOTIFICATION_ID_BASE +
            (hash % 10000)
    }

    /*
     * ---------------------------------------------------------
     * CANCEL REPLACEMENT
     * ---------------------------------------------------------
     */

    private fun cancelReplacementNotification(
        packageName: String
    ) {

        try {

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.cancel(
                notificationIdForPackage(
                    packageName
                )
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "Unable to cancel replacement notification",
                exception
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * NOTIFICATION REMOVED
     * ---------------------------------------------------------
     */

    override fun onNotificationRemoved(
        sbn: StatusBarNotification
    ) {

        Log.d(
            TAG,
            "Notification removed: ${sbn.packageName}"
        )
    }
}