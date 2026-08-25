package com.openapplocktest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

    companion object {

        private const val NOTIFICATION_PERMISSION_REQUEST_CODE =
            3001
    }

    /**
     * Returns the name of the main component registered from JavaScript.
     */
    override fun getMainComponentName(): String =
        "OpenAppLockTest"

    /**
     * Returns the instance of the ReactActivityDelegate.
     */
    override fun createReactActivityDelegate():
        ReactActivityDelegate =
        DefaultReactActivityDelegate(
            this,
            mainComponentName,
            fabricEnabled
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        /*
         * Request notification permission from the
         * main OpenAppLock screen.
         *
         * We intentionally do this here instead of
         * LockActivity because LockActivity can be
         * launched while another application's window
         * is currently in the foreground.
         */
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {

        /*
         * POST_NOTIFICATIONS exists from Android 13.
         */
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val permission =
            Manifest.permission.POST_NOTIFICATIONS

        val permissionState =
            ContextCompat.checkSelfPermission(
                this,
                permission
            )

        /*
         * Already granted.
         */
        if (
            permissionState ==
            PackageManager.PERMISSION_GRANTED
        ) {

            return
        }

        /*
         * Ask Android for the runtime permission.
         */
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                permission
            ),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
            NOTIFICATION_PERMISSION_REQUEST_CODE
        ) {

            val granted =
                grantResults.isNotEmpty() &&
                    grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED

            if (granted) {

                android.util.Log.d(
                    "OpenAppLock",
                    "POST_NOTIFICATIONS permission granted"
                )

            } else {

                android.util.Log.w(
                    "OpenAppLock",
                    "POST_NOTIFICATIONS permission denied"
                )
            }
        }
    }
}