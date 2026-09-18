package com.openapplocktest.applock

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.max
import kotlin.math.min

/**
 * Masks the protected app's existing task snapshot while Android Recents is
 * visible.
 *
 * This is intentionally separate from LockOverlayManager.
 *
 * - It is used ONLY while Recents/SystemUI is visible.
 * - It is non-touchable, so the Recents card remains clickable.
 * - Its bounds are limited to the protected app's card, so navigation bar and
 *   notification shade are never covered.
 *
 * Android does not expose a public API for a third-party app to replace another
 * app's TaskSnapshot. This accessibility overlay is therefore a best-effort
 * privacy presentation for devices whose Recents hierarchy exposes the app
 * card through Accessibility.
 */
object RecentsPrivacyOverlayManager {

    private var service: AccessibilityService? = null
    private var windowManager: WindowManager? = null
    private var overlayView: RecentsPrivacyCardView? = null
    private var lastProtectedPackage: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Synchronized
    fun attach(service: AccessibilityService) {
        this.service = service
        this.windowManager =
            service.getSystemService(
                android.content.Context.WINDOW_SERVICE
            ) as WindowManager
    }

    @Synchronized
    fun detach(service: AccessibilityService) {
        if (this.service !== service) return

        hideInternal()
        lastProtectedPackage = null
        windowManager = null
        this.service = null
    }

    @Synchronized
    fun setLastProtectedPackage(packageName: String) {
        lastProtectedPackage = packageName
    }

    @Synchronized
    fun showForLastProtectedApp() {
        val currentService = service ?: return
        val packageName = lastProtectedPackage ?: return

        /*
         * SystemUI may dispatch the Recents event before its task-card
         * accessibility tree has finished updating. Retry only a few times;
         * this is not a polling loop and stops as soon as the card is found.
         */
        mainHandler.removeCallbacksAndMessages(null)
        tryShow(currentService, packageName, 0)
    }

    private fun tryShow(
        currentService: AccessibilityService,
        packageName: String,
        attempt: Int
    ) {
        if (service !== currentService) return
        if (lastProtectedPackage != packageName) return

        val nodeBounds = findProtectedTaskCardBounds(
            currentService,
            packageName
        )
        val bounds = nodeBounds
            ?: fallbackCenteredRecentsCardBounds(currentService)

        if (nodeBounds == null) {
            android.util.Log.d(
                "OpenAppLock",
                "Recents card node not exposed; using centered-card fallback"
            )
        }

        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            showAtBounds(currentService, packageName, bounds)
            return
        }

        if (attempt < 4) {
            mainHandler.postDelayed(
                {
                    tryShow(
                        currentService,
                        packageName,
                        attempt + 1
                    )
                },
                50L
            )
        }
    }

    @Synchronized
    fun hide() {
        mainHandler.removeCallbacksAndMessages(null)
        hideInternal()
    }

    private fun hideInternal() {
        val wm = windowManager
        val view = overlayView

        overlayView = null

        if (wm == null || view == null) return

        try {
            wm.removeViewImmediate(view)
        } catch (_: Exception) {
        }
    }

    private fun showAtBounds(
        currentService: AccessibilityService,
        packageName: String,
        bounds: Rect
    ) {
        val wm = windowManager ?: return

        hideInternal()

        val view = try {
            RecentsPrivacyCardView(
                currentService,
                packageName
            )
        } catch (_: Exception) {
            return
        }

        /*
         * IMPORTANT:
         * NOT_TOUCHABLE lets the real Recents card receive the user's tap.
         * We do not use FLAG_LAYOUT_NO_LIMITS or any system-bar flags here.
         */
        val params = WindowManager.LayoutParams(
            max(1, bounds.width()),
            max(1, bounds.height()),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }

        try {
            wm.addView(view, params)
            overlayView = view
            android.util.Log.d(
                "OpenAppLock",
                "Recents privacy mask attached: ${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
            )
        } catch (_: Exception) {
            overlayView = null
        }
    }

    /**
     * Samsung One UI can expose the task snapshot as a SurfaceView/remote
     * hierarchy without exposing the card container as a useful accessibility
     * node. When that happens the label-based search above has no rectangle.
     *
     * In portrait Overview, the app that was just left is the centered card.
     * This fallback masks only that centered card area. It is deliberately
     * disabled for landscape and never uses full-screen/no-limit flags.
     */
    private fun fallbackCenteredRecentsCardBounds(
        service: AccessibilityService
    ): Rect? {
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        if (screenWidth <= 0 || screenHeight <= 0) return null
        if (screenHeight <= screenWidth) return null

        val left = (screenWidth * 0.195f).toInt()
        val right = (screenWidth * 0.805f).toInt()
        val top = (screenHeight * 0.135f).toInt()
        val bottom = (screenHeight * 0.835f).toInt()

        return Rect(left, top, right, bottom)
    }

    private fun findProtectedTaskCardBounds(
        service: AccessibilityService,
        packageName: String
    ): Rect? {
        val appLabel = try {
            val info =
                service.packageManager.getApplicationInfo(
                    packageName,
                    0
                )
            service.packageManager
                .getApplicationLabel(info)
                .toString()
        } catch (_: Exception) {
            return null
        }

        val windows = try {
            service.windows
        } catch (_: Exception) {
            return null
        }

        var best: Rect? = null
        var bestScore = Long.MIN_VALUE

        for (window in windows) {
            val root = try {
                window.root
            } catch (_: Exception) {
                null
            } ?: continue

            val candidate = findCardFromNode(
                root,
                appLabel,
                packageName
            )

            if (candidate != null) {
                val score = scoreCard(candidate, window)
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }

            root.recycle()
        }

        return best
    }

    private fun findCardFromNode(
        root: AccessibilityNodeInfo,
        appLabel: String,
        packageName: String
    ): Rect? {
        val labelLower = appLabel.trim().lowercase()

        fun matches(node: AccessibilityNodeInfo): Boolean {
            val text =
                node.text?.toString()?.trim()?.lowercase() ?: ""
            val description =
                node.contentDescription
                    ?.toString()
                    ?.trim()
                    ?.lowercase()
                    ?: ""

            return text == labelLower ||
                description == labelLower ||
                text.contains(labelLower) ||
                description.contains(labelLower)
        }

        fun walk(
            node: AccessibilityNodeInfo,
            depth: Int
        ): Rect? {
            if (depth > 12) return null

            if (matches(node)) {
                /*
                 * The label itself is normally a small node. Walk upward and
                 * choose the nearest large ancestor that resembles a Recents
                 * card. This avoids covering only the app name.
                 */
                var parent = node.parent
                var level = 0

                while (parent != null && level < 6) {
                    val r = Rect()
                    parent.getBoundsInScreen(r)

                    if (looksLikeTaskCard(r)) {
                        return Rect(r)
                    }

                    val next = parent.parent
                    parent.recycle()
                    parent = next
                    level++
                }

                parent?.recycle()
            }

            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                } ?: continue

                val result = walk(child, depth + 1)
                child.recycle()

                if (result != null) {
                    return result
                }
            }

            return null
        }

        return walk(root, 0)
    }

    private fun looksLikeTaskCard(rect: Rect): Boolean {
        if (rect.width() < 220 || rect.height() < 300) return false

        val screenWidth =
            service?.resources?.displayMetrics?.widthPixels ?: return false
        val screenHeight =
            service?.resources?.displayMetrics?.heightPixels ?: return false

        if (rect.left < 0 ||
            rect.top < 0 ||
            rect.right > screenWidth ||
            rect.bottom > screenHeight
        ) {
            return false
        }

        val area = rect.width().toLong() * rect.height().toLong()
        val screenArea =
            screenWidth.toLong() * screenHeight.toLong()

        if (area > screenArea * 8L / 10L) return false

        val ratio =
            rect.width().toFloat() /
                rect.height().toFloat()

        return ratio in 0.25f..0.85f
    }

    private fun scoreCard(
        rect: Rect,
        window: AccessibilityWindowInfo
    ): Long {
        val screenWidth =
            service?.resources?.displayMetrics?.widthPixels ?: 1
        val screenHeight =
            service?.resources?.displayMetrics?.heightPixels ?: 1

        val centerX = rect.centerX()
        val centerY = rect.centerY()

        val screenCenterX = screenWidth / 2
        val screenCenterY = screenHeight / 2

        val distance =
            kotlin.math.abs(centerX - screenCenterX) +
                kotlin.math.abs(centerY - screenCenterY)

        val area =
            rect.width().toLong() * rect.height().toLong()

        /*
         * Prefer a sizeable card close to the center of the Overview UI.
         */
        return area / 1000L -
            distance.toLong() -
            window.layer.toLong()
    }

    private class RecentsPrivacyCardView(
        context: android.content.Context,
        packageName: String
    ) : View(context) {

        private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }

        private val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

        private val subtitlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(165, 165, 165)
                textAlign = Paint.Align.CENTER
            }

        private val lockPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 4f.dp()
                strokeCap = Paint.Cap.ROUND
            }

        private val icon: Drawable? =
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            val minSize = min(w, h)

            /*
             * Match the screenshot's rounded black task card.
             */
            val radius = min(28f.dp(), minSize * 0.06f)
            canvas.drawRoundRect(
                0f,
                0f,
                w,
                h,
                radius,
                radius,
                backgroundPaint
            )

            val iconSize =
                min(72f.dp(), minSize * 0.18f)

            val iconLeft =
                (w - iconSize) / 2f
            val iconTop =
                h * 0.31f

            icon?.setBounds(
                iconLeft.toInt(),
                iconTop.toInt(),
                (iconLeft + iconSize).toInt(),
                (iconTop + iconSize).toInt()
            )
            icon?.draw(canvas)

            val lockCenterX = w / 2f
            val lockCenterY =
                iconTop + iconSize + h * 0.12f

            val bodyWidth = iconSize * 0.48f
            val bodyHeight = iconSize * 0.50f
            val bodyLeft =
                lockCenterX - bodyWidth / 2f
            val bodyTop =
                lockCenterY - bodyHeight / 2f
            val bodyRight =
                lockCenterX + bodyWidth / 2f
            val bodyBottom =
                lockCenterY + bodyHeight / 2f

            lockPaint.style = Paint.Style.STROKE

            val shackleRect = RectF(
                lockCenterX - bodyWidth * 0.34f,
                bodyTop - bodyHeight * 0.48f,
                lockCenterX + bodyWidth * 0.34f,
                bodyTop + bodyHeight * 0.18f
            )

            canvas.drawArc(
                shackleRect.left,
                shackleRect.top,
                shackleRect.right,
                shackleRect.bottom,
                180f,
                180f,
                false,
                lockPaint
            )

            lockPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                bodyLeft,
                bodyTop,
                bodyRight,
                bodyBottom,
                bodyWidth * 0.15f,
                bodyWidth * 0.15f,
                lockPaint
            )

            val keyholePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }

            canvas.drawCircle(
                lockCenterX,
                lockCenterY,
                bodyWidth * 0.10f,
                keyholePaint
            )

            canvas.drawRect(
                lockCenterX - bodyWidth * 0.035f,
                lockCenterY,
                lockCenterX + bodyWidth * 0.035f,
                lockCenterY + bodyHeight * 0.22f,
                keyholePaint
            )

            titlePaint.textSize =
                max(18f.dp(), minSize * 0.06f)

            canvas.drawText(
                "App Locked",
                w / 2f,
                h * 0.61f,
                titlePaint
            )

            subtitlePaint.textSize =
                max(13f.dp(), minSize * 0.035f)

            canvas.drawText(
                "Unlock to view",
                w / 2f,
                h * 0.67f,
                subtitlePaint
            )
        }

        private fun Float.dp(): Float =
            this * resources.displayMetrics.density
    }
}
