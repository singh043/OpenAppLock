package com.openapplocktest.applock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class LockOverlayView(
    context: Context,
    private val targetPackage: String
) : LinearLayout(context) {

    private val authenticationManager =
        AuthenticationManager(context.applicationContext)

    private var credentialInput: EditText? = null
    private var patternView: OverlayPatternView? = null
    private var actionButton: Button? = null

    private var lockType =
        authenticationManager.getLockType()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(28), dp(36), dp(28), dp(36))

        background = createLockScreenBackground()

        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setWillNotDraw(false)

        buildHeader()
        buildAuthenticationContent()

        post {
            requestFocus()
        }
    }

    private fun buildHeader() {
        var appIcon: Drawable? = null
        var appName = ""

        try {
            val appInfo = context.packageManager.getApplicationInfo(targetPackage, 0)
            appIcon = context.packageManager.getApplicationIcon(appInfo)
            appName = context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
        }

        if (appIcon != null) {
            addView(
                ImageView(context).apply {
                    setImageDrawable(appIcon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                    bottomMargin = dp(6)
                }
            )
        }

        if (appName.isNotEmpty()) {
            addView(
                TextView(context).apply {
                    text = appName
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                },
                layoutParams(0, 6)
            )
        }

        addView(
            TextView(context).apply {
                text = "App Locked"
                textSize = 27f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            },
            layoutParams(0, 16)
        )

        addView(
            TextView(context).apply {
                text = when (lockType) {
                    AuthenticationManager.LOCK_TYPE_PATTERN ->
                        "Draw your pattern to continue"
                    AuthenticationManager.LOCK_TYPE_PASSWORD ->
                        "Enter your password to continue"
                    else ->
                        "Enter your PIN to continue"
                }
                textSize = 14f
                setTextColor(Color.rgb(165, 165, 165))
                gravity = Gravity.CENTER
            },
            layoutParams(0, 18)
        )
    }

    private fun buildAuthenticationContent() {
        when (lockType) {
            AuthenticationManager.LOCK_TYPE_PATTERN -> buildPattern()
            AuthenticationManager.LOCK_TYPE_PASSWORD -> buildPassword()
            else -> {
                lockType = AuthenticationManager.LOCK_TYPE_PIN
                buildPin()
            }
        }
    }

    private fun buildPin() {
        val container = createInputContainer()

        val input = EditText(context).apply {
            hint = "Enter PIN"
            setHintTextColor(Color.rgb(120, 120, 120))
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.WHITE)
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6))
            setSingleLine(true)
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }

        container.addView(
            input,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        credentialInput = input

        addView(container, layoutParams(0, 18))
        buildUnlockButton()

        post {
            input.requestFocus()
            showKeyboard(input)
        }
    }

    private fun buildPassword() {
        val container = createInputContainer()

        val input = EditText(context).apply {
            hint = "Enter Password"
            setHintTextColor(Color.rgb(120, 120, 120))
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(Color.WHITE)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(64))
            setSingleLine(true)
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }

        container.addView(
            input,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        credentialInput = input

        addView(container, layoutParams(0, 18))
        buildUnlockButton()

        post {
            input.requestFocus()
            showKeyboard(input)
        }
    }

    private fun buildPattern() {
        val pattern = OverlayPatternView(context)
        patternView = pattern

        addView(
            pattern,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(300)
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(4)
            }
        )

        addView(
            TextView(context).apply {
                text = "Use at least 4 points"
                textSize = 12f
                setTextColor(Color.rgb(120, 120, 120))
                gravity = Gravity.CENTER
            },
            layoutParams(0, 10)
        )

        buildUnlockButton()
    }

    private fun buildUnlockButton() {
        val button = Button(context).apply {
            text = "Unlock"
            textSize = 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16, 16, 16))
            isAllCaps = false
            background = roundedBackground(Color.WHITE, dp(12).toFloat())
            minHeight = dp(48)
            minimumHeight = dp(48)
            stateListAnimator = null
            setOnClickListener {
                verify()
            }
        }

        actionButton = button
        addView(button, layoutParams(0, 18))
    }

    private fun verify() {
        val valid = when (lockType) {
            AuthenticationManager.LOCK_TYPE_PASSWORD -> {
                authenticationManager.verifyPassword(
                    credentialInput?.text?.toString() ?: ""
                )
            }

            AuthenticationManager.LOCK_TYPE_PATTERN -> {
                val pattern = patternView?.getPatternString() ?: ""
                pattern.length >= 4 &&
                    authenticationManager.verifyPattern(pattern)
            }

            else -> {
                authenticationManager.verifyPin(
                    credentialInput?.text?.toString() ?: ""
                )
            }
        }

        if (!valid) {
            credentialInput?.text?.clear()
            patternView?.clearPattern()
            return
        }

        LockSessionManager.authenticate(targetPackage)
        hideKeyboard()
        LockOverlayManager.hide()
    }

    private fun createInputContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(
                Color.rgb(16, 16, 16),
                dp(8).toFloat(),
                Color.rgb(51, 51, 51)
            )
            setPadding(0, 0, 0, 0)
        }
    }

    private fun createLockScreenBackground(): Drawable {
        val accent = getTargetAppAccentColor()

        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun draw(canvas: Canvas) {
                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()

                canvas.drawColor(Color.rgb(10, 10, 10))

                val headerPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(width, 0f)
                    lineTo(width, height * 0.16f)
                    cubicTo(
                        width * 0.84f,
                        height * 0.27f,
                        width * 0.34f,
                        height * 0.24f,
                        0f,
                        height * 0.13f
                    )
                    close()
                }

                paint.shader =
                    android.graphics.LinearGradient(
                        0f,
                        0f,
                        width,
                        height * 0.20f,
                        Color.argb(
                            52,
                            Color.red(accent),
                            Color.green(accent),
                            Color.blue(accent)
                        ),
                        Color.argb(
                            8,
                            Color.red(accent),
                            Color.green(accent),
                            Color.blue(accent)
                        ),
                        Shader.TileMode.CLAMP
                    )

                canvas.drawPath(headerPath, paint)

                drawGlow(canvas, width * 0.02f, height * 0.22f, width * 0.42f, accent)
                drawGlow(canvas, width * 0.98f, height * 0.40f, width * 0.38f, accent)
                drawGlow(canvas, width * 0.08f, height * 0.82f, width * 0.34f, accent)
                drawGlow(canvas, width * 0.92f, height * 0.76f, width * 0.32f, accent)

                paint.shader = null
            }

            private fun drawGlow(
                canvas: Canvas,
                x: Float,
                y: Float,
                radius: Float,
                color: Int
            ) {
                paint.shader = RadialGradient(
                    x,
                    y,
                    radius,
                    Color.argb(
                        42,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    ),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )

                canvas.drawRect(
                    0f,
                    0f,
                    bounds.width().toFloat(),
                    bounds.height().toFloat(),
                    paint
                )
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(
                colorFilter: android.graphics.ColorFilter?
            ) {
                paint.colorFilter = colorFilter
            }

            @Suppress("DEPRECATION")
            override fun getOpacity(): Int =
                android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private fun getTargetAppAccentColor(): Int {
        return try {
            val appInfo =
                context.packageManager.getApplicationInfo(targetPackage, 0)

            val drawable =
                context.packageManager.getApplicationIcon(appInfo)

            val size = 48
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    size,
                    size,
                    android.graphics.Bitmap.Config.ARGB_8888
                )

            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)

            var red = 0L
            var green = 0L
            var blue = 0L
            var count = 0L

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) > 80) {
                        red += Color.red(pixel)
                        green += Color.green(pixel)
                        blue += Color.blue(pixel)
                        count++
                    }
                }
            }

            bitmap.recycle()

            if (count == 0L) {
                Color.rgb(70, 70, 70)
            } else {
                val r = (red / count).toInt()
                val g = (green / count).toInt()
                val b = (blue / count).toInt()
                val maxChannel = maxOf(r, g, b)
                val minChannel = minOf(r, g, b)

                if (maxChannel - minChannel < 18) {
                    Color.rgb(85, 85, 85)
                } else {
                    Color.rgb(
                        (r * 1.15f).toInt().coerceAtMost(255),
                        (g * 1.15f).toInt().coerceAtMost(255),
                        (b * 1.15f).toInt().coerceAtMost(255)
                    )
                }
            }
        } catch (_: Exception) {
            Color.rgb(70, 70, 70)
        }
    }

    private fun roundedBackground(
        fill: Int,
        radius: Float,
        stroke: Int? = null
    ): Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (stroke != null) {
                setStroke(dp(1), stroke)
            }
        }
    }

    private fun showKeyboard(view: View) {
        view.postDelayed({
            try {
                view.requestFocus()

                val imm =
                    context.getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as? InputMethodManager

                imm?.showSoftInput(
                    view,
                    InputMethodManager.SHOW_IMPLICIT
                )
            } catch (_: Exception) {
            }
        }, 250)
    }

    private fun hideKeyboard() {
        try {
            val view = credentialInput ?: this

            val imm =
                context.getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as? InputMethodManager

            imm?.hideSoftInputFromWindow(
                view.windowToken,
                0
            )

            clearFocus()
        } catch (_: Exception) {
        }
    }

    private fun layoutParams(
        topMargin: Int,
        bottomMargin: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = dp(topMargin)
            this.bottomMargin = dp(bottomMargin)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private class OverlayPatternView(
        context: Context
    ) : View(context) {

        private val dotPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(120, 120, 120)
                style = Paint.Style.FILL
            }

        private val selectedPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }

        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 7f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        private val points = ArrayList<Int>()
        private val pointX = FloatArray(9)
        private val pointY = FloatArray(9)
        private var currentX = 0f
        private var currentY = 0f
        private var drawing = false

        init {
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val width = width.toFloat()
            val height = height.toFloat()
            val size = minOf(width, height)
            val startX = (width - size) / 2f
            val startY = (height - size) / 2f
            val spacing = size / 4f

            for (row in 0..2) {
                for (column in 0..2) {
                    val index = row * 3 + column
                    pointX[index] = startX + spacing + column * spacing
                    pointY[index] = startY + spacing + row * spacing
                }
            }

            val path = Path()
            points.forEachIndexed { index, pointIndex ->
                val x = pointX[pointIndex]
                val y = pointY[pointIndex]
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            if (drawing && points.isNotEmpty()) {
                path.lineTo(currentX, currentY)
            }

            canvas.drawPath(path, linePaint)

            for (index in 0 until 9) {
                if (points.contains(index)) {
                    canvas.drawCircle(
                        pointX[index],
                        pointY[index],
                        18f,
                        selectedPaint
                    )
                }

                canvas.drawCircle(
                    pointX[index],
                    pointY[index],
                    8f,
                    dotPaint
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    clearPattern()
                    drawing = true
                    currentX = event.x
                    currentY = event.y
                    addPointIfNeeded(event.x, event.y)
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (drawing) {
                        currentX = event.x
                        currentY = event.y
                        addPointIfNeeded(event.x, event.y)
                        invalidate()
                    }
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    drawing = false
                    invalidate()
                    return true
                }
            }

            return true
        }

        private fun addPointIfNeeded(x: Float, y: Float) {
            var closest = -1
            var closestDistance = Float.MAX_VALUE

            for (index in 0 until 9) {
                if (points.contains(index)) continue

                val dx = x - pointX[index]
                val dy = y - pointY[index]
                val distance = dx * dx + dy * dy

                if (distance < closestDistance) {
                    closestDistance = distance
                    closest = index
                }
            }

            if (closest >= 0 && closestDistance <= 65f * 65f) {
                points.add(closest)
            }
        }

        fun getPatternString(): String =
            points.joinToString("")

        fun clearPattern() {
            points.clear()
            drawing = false
            invalidate()
        }
    }
}
