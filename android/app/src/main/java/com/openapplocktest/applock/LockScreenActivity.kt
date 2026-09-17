package com.openapplocktest.applock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import java.lang.ref.WeakReference

class LockScreenActivity : Activity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"

        @Volatile
        private var current: WeakReference<LockScreenActivity>? = null

        fun isAlive(): Boolean {
            val activity = current?.get() ?: return false
            return !activity.isFinishing &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 ||
                    !activity.isDestroyed)
        }

        @Volatile
        private var resumed = false

        fun isResumed(): Boolean = resumed

        fun finishLockScreen() {
            val activity = current?.get() ?: return
            activity.runOnUiThread {
                if (!activity.isFinishing) {
                    activity.finish()
                }
            }
        }

        internal fun isCurrent(activity: LockScreenActivity): Boolean {
            return current?.get() === activity
        }
    }

    private var targetPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        current = WeakReference(this)
        configureSystemBars()
        loadLockView(intent)
    }

    private fun loadLockView(intent: Intent) {
        targetPackage =
            intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""

        if (targetPackage.isEmpty()) {
            finish()
            return
        }

        val lockView = LockOverlayView(this, targetPackage)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lockView.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars()
                )

                view.setPadding(
                    dp(28) + bars.left,
                    dp(36) + bars.top,
                    dp(28) + bars.right,
                    dp(36) + bars.bottom
                )

                insets
            }
        }

        setContentView(lockView)
    }

    /* DO NOT CHANGE: this is the working system-bar/navigation behavior. */
    private fun configureSystemBars() {
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_VISIBLE
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    override fun onBackPressed() {
        // Back must not bypass the app lock.
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        resumed = false
        LockOverlayManager.onLockScreenActivityDestroyed(this)

        if (isCurrent(this)) {
            current = null
        }

        super.onDestroy()
    }
}
