package com.openapplocktest.applock

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View

/**
 * Samsung One UI 6.1 / Android 14 Recents privacy mask.
 *
 * The One UI Recents task cards are not exposed as AccessibilityNodeInfo
 * children on this device. This implementation therefore attaches one
 * accessibility overlay to the active One UI Home window and draws a
 * mask over the RECENTLY ACTIVE (center) task card.
 *
 * It is intentionally limited to the Recents window. It does not touch
 * LockScreenActivity, navigation bars, notification shade, or authentication.
 */
object RecentsPrivacyMaskManager {
    private const val TAG = "OpenAppLock"
    private const val ONE_UI_LAUNCHER = "com.sec.android.app.launcher"

    @Volatile private var host: SurfaceControlViewHost? = null
    @Volatile private var surface: SurfaceControl? = null
    @Volatile private var maskedPackage: String? = null

    @Synchronized
    fun show(service: AccessibilityService, targetPackage: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "RECENTS V5 skipped: API < 34")
            return
        }

        if (targetPackage.isBlank() || targetPackage == service.packageName) return

        val window = findOneUiWindow(service) ?: run {
            Log.d(TAG, "RECENTS V5: One UI Home window not found")
            return
        }

        val bounds = android.graphics.Rect()
        window.getBoundsInScreen(bounds)

        if (bounds.width() <= 0 || bounds.height() <= 0) {
            Log.d(TAG, "RECENTS V5: invalid One UI bounds=$bounds")
            return
        }

        Log.d(
            TAG,
            "RECENTS V5 TARGET: package=$targetPackage " +
                "windowId=${window.id} bounds=$bounds"
        )

        val display = service.display ?: run {
            Log.d(TAG, "RECENTS V5: display unavailable")
            return
        }

        releaseLocked()

        val localWidth = bounds.width()
        val localHeight = bounds.height()

        /*
         * Samsung One UI 6.x portrait Recents puts the most-recent task
         * in the center. Use proportions rather than hard-coded pixels so
         * this remains valid on the 1080x2408 display and similar density.
         *
         * The card is intentionally inset from the One UI Home window so
         * the search/header, Close all button, dock and navigation area stay
         * untouched.
         */
        val card = RectF(
            localWidth * 0.18f,
            localHeight * 0.12f,
            localWidth * 0.82f,
            localHeight * 0.76f
        )

        val maskView = RecentsMaskView(
            service,
            card,
            service.getStringSafeAppLabel(targetPackage)
        )

        try {
            val newHost = SurfaceControlViewHost(
                service,
                display,
                null as IBinder?
            )

            newHost.setView(maskView, localWidth, localHeight)

            val packageObject = newHost.getSurfacePackage()
            val newSurface = packageObject?.getSurfaceControl()

            if (newSurface == null) {
                newHost.release()
                Log.d(TAG, "RECENTS V5: SurfaceControl unavailable")
                return
            }

            service.attachAccessibilityOverlayToWindow(
                window.id,
                newSurface
            )

            SurfaceControl.Transaction()
                .setLayer(newSurface, Int.MAX_VALUE)
                .apply()

            host = newHost
            surface = newSurface
            maskedPackage = targetPackage

            Log.d(
                TAG,
                "RECENTS V5 ATTACHED: windowId=${window.id} " +
                    "card=$card package=$targetPackage"
            )
        } catch (e: Exception) {
            Log.e(TAG, "RECENTS V5 attach failed", e)
            releaseLocked()
        }
    }

    @Synchronized
    fun hide() {
        releaseLocked()
    }

    @Synchronized
    private fun releaseLocked() {
        val oldSurface = surface

        try {
            if (oldSurface != null && oldSurface.isValid) {
                SurfaceControl.Transaction()
                    .reparent(oldSurface, null)
                    .apply()
            }
        } catch (_: Exception) {
        }

        try {
            host?.release()
        } catch (_: Exception) {
        }

        surface = null
        host = null
        maskedPackage = null
    }

    private fun findOneUiWindow(
        service: AccessibilityService
    ): android.view.accessibility.AccessibilityWindowInfo? {
        return try {
            service.windows.firstOrNull { window ->
                val pkg = try {
                    window.root?.packageName?.toString()
                } catch (_: Exception) {
                    null
                }

                pkg == ONE_UI_LAUNCHER &&
                    window.type ==
                        android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.isActive
            } ?: service.windows.firstOrNull { window ->
                val pkg = try {
                    window.root?.packageName?.toString()
                } catch (_: Exception) {
                    null
                }

                pkg == ONE_UI_LAUNCHER &&
                    window.type ==
                        android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
            }
        } catch (e: Exception) {
            Log.e(TAG, "RECENTS V5: unable to find One UI window", e)
            null
        }
    }

    private class RecentsMaskView(
        context: android.content.Context,
        private val card: RectF,
        private val appLabel: String
    ) : View(context) {

        private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawRoundRect(
                card,
                dp(28f),
                dp(28f),
                blackPaint
            )

            val cx = card.centerX()
            val cy = card.centerY()

            // Simple lock symbol.
            val lockWidth = dp(38f)
            val lockHeight = dp(32f)
            val lockLeft = cx - lockWidth / 2f
            val lockTop = cy - dp(64f)

            blackPaint.color = Color.WHITE
            canvas.drawRoundRect(
                RectF(
                    lockLeft,
                    lockTop,
                    lockLeft + lockWidth,
                    lockTop + lockHeight
                ),
                dp(4f),
                dp(4f),
                blackPaint
            )

            blackPaint.style = Paint.Style.STROKE
            blackPaint.strokeWidth = dp(5f)
            canvas.drawArc(
                RectF(
                    cx - dp(12f),
                    lockTop - dp(20f),
                    cx + dp(12f),
                    lockTop + dp(10f)
                ),
                180f,
                180f,
                false,
                blackPaint
            )
            blackPaint.style = Paint.Style.FILL

            textPaint.textSize = dp(24f)
            canvas.drawText(
                "App Locked",
                cx,
                cy + dp(10f),
                textPaint
            )

            textPaint.textSize = dp(16f)
            textPaint.typeface = android.graphics.Typeface.DEFAULT
            canvas.drawText(
                "Unlock to view",
                cx,
                cy + dp(40f),
                textPaint
            )
        }

        private fun dp(value: Float): Float =
            value * resources.displayMetrics.density
    }

    private fun AccessibilityService.getStringSafeAppLabel(
        packageName: String
    ): String {
        return try {
            packageManager
                .getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                )
                .toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}
