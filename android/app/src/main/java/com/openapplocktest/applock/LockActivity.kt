package com.openapplocktest.applock

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputFilter
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class LockActivity : Activity() {

    companion object {

        const val EXTRA_TARGET_PACKAGE =
            "target_package"

        const val EXTRA_CHANGE_CREDENTIAL =
            "change_credential"

        const val EXTRA_NEW_LOCK_TYPE =
            "new_lock_type"

        private const val PREFS_NAME =
            "applock_settings"

        private const val KEY_FACE_UNLOCK_ENABLED =
            "face_unlock_enabled"

        private const val KEY_FINGERPRINT_UNLOCK_ENABLED =
            "fingerprint_unlock_enabled"
    }

    private lateinit var authenticationManager:
        AuthenticationManager

    private val biometricPreferences by lazy {
        getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    private lateinit var rootLayout:
        LinearLayout

    private var changeFlowBackArrow:
        View? = null

    private lateinit var actionButton:
        Button

    private var pinInput:
        EditText? = null

    private var passwordInput:
        EditText? = null

    private var confirmInput:
        EditText? = null

    private var patternView:
        PatternView? = null

    private var confirmPatternView:
        PatternView? = null

    private var currentCredentialInput:
        EditText? = null

    private var currentPatternView:
        PatternView? = null

    private var verifiedCurrentCredential:
        String? = null

    private var pendingNewCredential:
        String? = null

    private var targetPackage =
        ""

    private var lockType =
        AuthenticationManager.LOCK_TYPE_PIN

    private var newLockType =
        AuthenticationManager.LOCK_TYPE_PIN

    private var isCreatingCredential =
        false

    private var isChangingCredential =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        authenticationManager =
            AuthenticationManager(
                applicationContext
            )

        targetPackage =
            intent.getStringExtra(
                EXTRA_TARGET_PACKAGE
            ) ?: ""

        isChangingCredential =
            intent.getBooleanExtra(
                EXTRA_CHANGE_CREDENTIAL,
                false
            )

        lockType =
            authenticationManager
                .getLockType()

        newLockType =
            intent.getStringExtra(
                EXTRA_NEW_LOCK_TYPE
            ) ?: lockType

        if (
            isChangingCredential
        ) {
            showCurrentCredentialScreen()
        } else {
            showAuthenticationScreen()
        }
    }

    override fun onNewIntent(
        intent: Intent?
    ) {
        super.onNewIntent(
            intent
        )

        if (
            intent == null
        ) {
            return
        }

        setIntent(
            intent
        )

        targetPackage =
            intent.getStringExtra(
                EXTRA_TARGET_PACKAGE
            ) ?: ""

        isChangingCredential =
            intent.getBooleanExtra(
                EXTRA_CHANGE_CREDENTIAL,
                false
            )

        lockType =
            authenticationManager
                .getLockType()

        newLockType =
            intent.getStringExtra(
                EXTRA_NEW_LOCK_TYPE
            ) ?: lockType

        if (
            isChangingCredential
        ) {
            showCurrentCredentialScreen()
        } else {
            showAuthenticationScreen()
        }
    }

    /*
     * =========================================================
     * NORMAL AUTHENTICATION
     * =========================================================
     */

    private fun showAuthenticationScreen() {

        clearInputReferences()

        val hasPin =
            authenticationManager.hasPin()

        val hasPassword =
            authenticationManager.hasPassword()

        val hasPattern =
            authenticationManager.hasPattern()

        val credentialExists =
            when (lockType) {

                AuthenticationManager
                    .LOCK_TYPE_PIN ->
                    hasPin

                AuthenticationManager
                    .LOCK_TYPE_PASSWORD ->
                    hasPassword

                AuthenticationManager
                    .LOCK_TYPE_PATTERN ->
                    hasPattern

                else ->
                    hasPin
            }

        isCreatingCredential =
            !credentialExists

        rootLayout =
            createRootLayout()

        if (!isCreatingCredential) {
            rootLayout.background =
                createLockScreenBackground()
        }

        if (
            !isCreatingCredential &&
            isBiometricLayerEnabled()
        ) {

            createBiometricFirstScreen()

            setContentView(
                rootLayout
            )

            window.decorView.postDelayed(
                {
                    authenticateBiometric()
                },
                300
            )

            return
        }

        val title =
            if (
                isCreatingCredential
            ) {

                when (lockType) {

                    AuthenticationManager
                        .LOCK_TYPE_PATTERN ->
                        "Create Pattern"

                    AuthenticationManager
                        .LOCK_TYPE_PASSWORD ->
                        "Create Password"

                    else ->
                        "Create AppLock PIN"
                }

            } else {

                "App Locked"
            }

        val subtitle =
            if (
                isCreatingCredential
            ) {

                when (lockType) {

                    AuthenticationManager
                        .LOCK_TYPE_PATTERN ->
                        "Choose a secure pattern with at least 4 points"

                    AuthenticationManager
                        .LOCK_TYPE_PASSWORD ->
                        "Create a password to protect your apps"

                    else ->
                        "Create a secure 4 or 6 digit PIN"
                }

            } else {

                when (lockType) {

                    AuthenticationManager
                        .LOCK_TYPE_PATTERN ->
                        "Draw your pattern to continue"

                    AuthenticationManager
                        .LOCK_TYPE_PASSWORD ->
                        "Enter your password to continue"

                    else ->
                        "Enter your PIN to continue"
                }
            }

        addHeader(
            title,
            subtitle,
            !isCreatingCredential
        )

        when (lockType) {

            AuthenticationManager
                .LOCK_TYPE_PIN -> {

                createPinScreen()
            }

            AuthenticationManager
                .LOCK_TYPE_PASSWORD -> {

                createPasswordScreen()
            }

            AuthenticationManager
                .LOCK_TYPE_PATTERN -> {

                createPatternScreen()
            }

            else -> {

                lockType =
                    AuthenticationManager
                        .LOCK_TYPE_PIN

                createPinScreen()
            }
        }

        setContentView(
            rootLayout
        )

        pinInput?.requestFocus()

        passwordInput?.requestFocus()
    }

    private fun isBiometricLayerEnabled(): Boolean {

        return biometricPreferences.getBoolean(
            KEY_FACE_UNLOCK_ENABLED,
            false
        ) ||
            biometricPreferences.getBoolean(
                KEY_FINGERPRINT_UNLOCK_ENABLED,
                false
            )
    }

    private fun biometricSubtitle(): String {

        val faceEnabled =
            biometricPreferences.getBoolean(
                KEY_FACE_UNLOCK_ENABLED,
                false
            )

        val fingerprintEnabled =
            biometricPreferences.getBoolean(
                KEY_FINGERPRINT_UNLOCK_ENABLED,
                false
            )

        return when {

            faceEnabled && fingerprintEnabled ->
                "Use face or fingerprint to continue"

            faceEnabled ->
                "Use face to continue"

            fingerprintEnabled ->
                "Use fingerprint to continue"

            else ->
                "Use your biometric to continue"
        }
    }

    private fun createBiometricFirstScreen() {

        addHeader(
            "App Locked",
            biometricSubtitle(),
            true
        )

        addSimpleText(
            "Authenticate with biometrics or use your ${displayLockType(lockType)}.",
            14,
            Color.rgb(
                165,
                165,
                165
            ),
            24
        )

        actionButton =
            createButton(
                "Use ${displayLockType(lockType)}"
            )

        actionButton.setOnClickListener {
            showCredentialFallbackScreen()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )
    }

    private fun showCredentialFallbackScreen() {

        clearInputReferences()

        isCreatingCredential =
            false

        rootLayout =
            createRootLayout()

        rootLayout.background =
            createLockScreenBackground()

        val subtitle =
            when (lockType) {

                AuthenticationManager
                    .LOCK_TYPE_PATTERN ->
                    "Draw your pattern to continue"

                AuthenticationManager
                    .LOCK_TYPE_PASSWORD ->
                    "Enter your password to continue"

                else ->
                    "Enter your PIN to continue"
            }

        addHeader(
            "App Locked",
            subtitle,
            true
        )

        when (lockType) {

            AuthenticationManager
                .LOCK_TYPE_PIN -> {

                createPinScreen()
            }

            AuthenticationManager
                .LOCK_TYPE_PASSWORD -> {

                createPasswordScreen()
            }

            AuthenticationManager
                .LOCK_TYPE_PATTERN -> {

                createPatternScreen()
            }

            else -> {

                lockType =
                    AuthenticationManager
                        .LOCK_TYPE_PIN

                createPinScreen()
            }
        }

        setContentView(
            rootLayout
        )

        pinInput?.requestFocus()

        passwordInput?.requestFocus()
    }


    /*
     * =========================================================
     * CHANGE LOCK TYPE / CREDENTIAL
     * =========================================================
     */

    private fun addChangeFlowBackArrow() {

        changeFlowBackArrow
            ?.let { existing ->
                (existing.parent as? ViewGroup)
                    ?.removeView(existing)
            }

        val arrow = BackArrowView(this)

        arrow.setOnClickListener {
            finish()
        }

        val density =
            resources.displayMetrics.density

        val size =
            (48 * density).toInt()

        val params =
            FrameLayout.LayoutParams(
                size,
                size
            )

        params.gravity =
            Gravity.TOP or Gravity.START

        params.leftMargin =
            (10 * density).toInt()

        params.topMargin =
            (10 * density).toInt()

        addContentView(
            arrow,
            params
        )

        changeFlowBackArrow =
            arrow
    }

    private fun showCurrentCredentialScreen() {

        clearInputReferences()

        verifiedCurrentCredential =
            null

        pendingNewCredential =
            null

        rootLayout =
            createRootLayout()

        val currentType =
            authenticationManager
                .getLockType()

        lockType =
            currentType

        // Password -> Password uses the same single-screen change layout
        // as the Change PIN screen: current password, new password,
        // confirm password, Cancel and Change Password.
        if (
            currentType ==
                AuthenticationManager
                    .LOCK_TYPE_PASSWORD &&
            newLockType ==
                AuthenticationManager
                    .LOCK_TYPE_PASSWORD
        ) {
            showCombinedPasswordChangeScreen()
            return
        }

        if (
            currentType ==
                AuthenticationManager
                    .LOCK_TYPE_BIOMETRIC
        ) {

            addHeader(
                "Change ${displayLockType(newLockType)}",
                "Biometric changes will be handled later"
            )

            addSimpleText(
                "Biometric credential changes are not available yet.",
                14,
                Color.rgb(
                    165,
                    165,
                    165
                ),
                20
            )

            actionButton =
                createButton(
                    "Back"
                )

            actionButton.setOnClickListener {
                finish()
            }

            rootLayout.addView(
                actionButton,
                createLayoutParams(
                    0,
                    20
                )
            )

            setContentView(
                rootLayout
            )

            addChangeFlowBackArrow()

            return
        }

        addHeader(
            "Verify Current ${displayLockType(currentType)}",
            "Verify your current ${displayLockType(currentType)} first"
        )

        createCurrentCredentialInput(
            currentType
        )

        actionButton =
            createButton(
                "Verify & Continue"
            )

        actionButton.setOnClickListener {

            verifyCurrentCredentialAndContinue()
        }

        rootLayout.addView(
            actionButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(18)
            }
        )

        addTargetPackage()

        setContentView(
            rootLayout
        )

        addChangeFlowBackArrow()
    }

    private fun showCombinedPasswordChangeScreen() {

        clearInputReferences()

        rootLayout =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dp(32),
                    0,
                    dp(32),
                    0
                )

                setBackgroundColor(
                    Color.rgb(11, 11, 11)
                )
            }

        val header =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                weightSum = 1f
            }

        val title =
            TextView(this).apply {
                text =
                    "Change Password"
                textSize =
                    21f
                typeface =
                    Typeface.DEFAULT_BOLD
                setTextColor(
                    Color.WHITE
                )
                gravity =
                    Gravity.CENTER
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        dp(56),
                        1f
                    ).apply {
                        leftMargin = dp(4)
                        rightMargin = dp(4)
                    }
            }

        header.addView(title)

        rootLayout.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                bottomMargin = dp(10)
            }
        )

        fun addPasswordField(
            labelText: String,
            hintText: String,
            target: (EditText) -> Unit
        ) {
            val label =
                TextView(this).apply {
                    text = labelText
                    textSize = 15f
                    setTextColor(
                        Color.rgb(210, 210, 210)
                    )
                    gravity = Gravity.START
                }

            rootLayout.addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(18)
                    bottomMargin = dp(6)
                }
            )

            val container =
                createInputContainer()

            container.background =
                GradientDrawable().apply {
                    setColor(
                        Color.rgb(16, 16, 16)
                    )
                    cornerRadius =
                        dp(8).toFloat()
                    setStroke(
                        dp(1),
                        Color.rgb(51, 51, 51)
                    )
                }

            container.setPadding(
                0,
                0,
                0,
                0
            )

            val input =
                createEditText(hintText)

            // Match Change PIN text/placeholder size exactly.
            input.textSize = 15f
            input.gravity =
                Gravity.CENTER_VERTICAL or
                    Gravity.START

            input.inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD

            input.filters =
                arrayOf(
                    InputFilter.LengthFilter(64)
                )

            addInputWithVisibilityToggle(
                container,
                input,
                false,
                48,
                true
            )

            // Match the Change PIN input appearance exactly.
            container.background =
                GradientDrawable().apply {
                    setColor(
                        Color.rgb(16, 16, 16)
                    )
                    cornerRadius =
                        dp(8).toFloat()
                    setStroke(
                        dp(1),
                        Color.rgb(51, 51, 51)
                    )
                }

            input.setPadding(
                dp(14),
                dp(4),
                dp(8),
                dp(4)
            )

            target(input)

            rootLayout.addView(
                container,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                )
            )
        }

        addPasswordField(
            "Current Password",
            "Enter current password"
        ) {
            currentCredentialInput = it
        }

        addPasswordField(
            "New Password",
            "Enter new password"
        ) {
            passwordInput = it
        }

        addPasswordField(
            "Confirm New Password",
            "Confirm new password"
        ) {
            confirmInput = it
        }

        val buttonRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val cancelButton =
            Button(this).apply {
                text = "Cancel"
                textSize = 14f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                isAllCaps = false
                background =
                    createRoundedBackground(
                        Color.rgb(51, 51, 51),
                        dp(12).toFloat()
                    )
                minHeight = dp(48)
                minimumHeight = dp(48)
                stateListAnimator = null
                setOnClickListener {
                    finish()
                }
            }

        val changeButton =
            Button(this).apply {
                text = "Change Password"
                textSize = 14f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(16, 16, 16))
                isAllCaps = false
                background =
                    createRoundedBackground(
                        Color.WHITE,
                        dp(12).toFloat()
                    )
                minHeight = dp(48)
                minimumHeight = dp(48)
                stateListAnimator = null
                setOnClickListener {
                    val currentPassword =
                        currentCredentialInput
                            ?.text
                            ?.toString()
                            ?: ""

                    if (currentPassword.isEmpty()) {
                        showPasswordChangeError(
                            "Enter your current password"
                        )
                        return@setOnClickListener
                    }

                    if (
                        !authenticationManager
                            .verifyCredential(
                                AuthenticationManager
                                    .LOCK_TYPE_PASSWORD,
                                currentPassword
                            )
                    ) {
                        currentCredentialInput
                            ?.text
                            ?.clear()
                        showPasswordChangeError(
                            "Incorrect current password"
                        )
                        return@setOnClickListener
                    }

                    verifiedCurrentCredential =
                        currentPassword

                    changePassword()
                }
            }

        buttonRow.addView(
            cancelButton,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                rightMargin = dp(5)
            }
        )

        buttonRow.addView(
            changeButton,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                leftMargin = dp(5)
            }
        )

        rootLayout.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(18)
            }
        )

        setContentView(rootLayout)
        addChangeFlowBackArrow()
    }

    private fun verifyCurrentCredentialAndContinue() {

        val currentCredential =
            getCurrentCredential()

        if (
            currentCredential == null
        ) {
            return
        }

        val valid =
            authenticationManager
                .verifyCredential(
                    lockType,
                    currentCredential
                )

        if (!valid) {

            when (lockType) {

                AuthenticationManager
                    .LOCK_TYPE_PATTERN -> {

                    currentPatternView
                        ?.clearPattern()

                    showPatternError(
                        "Incorrect current pattern"
                    )
                }

                else -> {

                    currentCredentialInput
                        ?.text
                        ?.clear()
                }
            }

            if (
                lockType !=
                    AuthenticationManager.LOCK_TYPE_PATTERN
            ) {
                if (
                    isChangingCredential &&
                    newLockType ==
                        AuthenticationManager.LOCK_TYPE_PIN
                ) {
                    showPinChangeError(
                        "Incorrect current ${displayLockType(lockType)}"
                    )
                } else if (
                    isChangingCredential &&
                    newLockType ==
                        AuthenticationManager.LOCK_TYPE_PASSWORD
                ) {
                    showPasswordChangeError(
                        "Incorrect current ${displayLockType(lockType)}"
                    )
                } else if (
                    isChangingCredential &&
                    newLockType ==
                        AuthenticationManager.LOCK_TYPE_PATTERN
                ) {
                    showPatternError(
                        "Incorrect current ${displayLockType(lockType)}"
                    )
                } else {
                    showMessage(
                        "Incorrect current ${displayLockType(lockType)}"
                    )
                }
            }

            return
        }

        verifiedCurrentCredential =
            currentCredential

        showNewCredentialScreen()
    }

    /*
     * =========================================================
     * NEW CREDENTIAL - SCREEN 1
     * =========================================================
     */

    private fun showNewCredentialScreen() {

        clearInputReferences()

        rootLayout =
            createRootLayout()

        addHeader(
            "New ${displayLockType(newLockType)}",
            "Enter your new ${displayLockType(newLockType)}"
        )

        when (newLockType) {

            AuthenticationManager
                .LOCK_TYPE_PIN -> {

                createNewPinScreen()
            }

            AuthenticationManager
                .LOCK_TYPE_PASSWORD -> {

                createNewPasswordScreen()
            }

            AuthenticationManager
                .LOCK_TYPE_PATTERN -> {

                createNewPatternScreen()
            }

            AuthenticationManager
                .LOCK_TYPE_BIOMETRIC -> {

                addSimpleText(
                    "Biometric changes will be handled later.",
                    14,
                    Color.rgb(
                        165,
                        165,
                        165
                    ),
                    24
                )

                actionButton =
                    createButton(
                        "Back"
                    )

                actionButton.setOnClickListener {
                    finish()
                }

                rootLayout.addView(
                    actionButton,
                    createLayoutParams(
                        0,
                        20
                    )
                )
            }

            else -> {

                showMessage(
                    "Unsupported lock type"
                )

                finish()

                return
            }
        }

        addTargetPackage()

        setContentView(
            rootLayout
        )

        addChangeFlowBackArrow()
    }

    /*
     * =========================================================
     * NEW PIN - SCREEN 1
     * =========================================================
     */

    private fun createNewPinScreen() {

        addSimpleText(
            "New PIN",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            24
        )

        val container =
            createInputContainer()

        pinInput =
            createEditText(
                "Enter new PIN"
            )

        pinInput?.textSize = 15f

        pinInput?.inputType =
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD

        pinInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    6
                )
            )

        addInputWithVisibilityToggle(
            container,
            pinInput!!,
            true,
            48,
            true
        )

        rootLayout.addView(
            container,
            createLayoutParams(
                0,
                12
            )
        )

        addSimpleText(
            "Confirm New PIN",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            18
        )

        val confirmContainer =
            createInputContainer()

        confirmInput =
            createEditText(
                "Confirm new PIN"
            )

        confirmInput?.textSize = 15f

        confirmInput?.inputType =
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD

        confirmInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    6
                )
            )

        addInputWithVisibilityToggle(
            confirmContainer,
            confirmInput!!,
            true,
            48,
            true
        )

        rootLayout.addView(
            confirmContainer,
            createLayoutParams(
                0,
                12
            )
        )

        actionButton =
            createButton(
                "Change PIN"
            )

        actionButton.minHeight =
            dp(48)

        actionButton.minimumHeight =
            dp(48)

        actionButton.minHeight =
            dp(48)

        actionButton.minimumHeight =
            dp(48)

        actionButton.setOnClickListener {
            changePin()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )

        addTargetPackage()

        setContentView(
            rootLayout
        )
    }

    private fun continueToConfirmPin() {

        val newPin =
            pinInput
                ?.text
                ?.toString()
                ?: ""

        if (
            newPin.length != 4 &&
            newPin.length != 6
        ) {

            showMessage(
                "PIN must contain 4 or 6 digits"
            )

            return
        }

        if (
            !newPin.all {
                it.isDigit()
            }
        ) {

            showMessage(
                "PIN must contain only digits"
            )

            return
        }

        pendingNewCredential =
            newPin

        showConfirmPinScreen()
    }

    /*
     * =========================================================
     * CONFIRM PIN - SCREEN 2
     * =========================================================
     */

    private fun showConfirmPinScreen() {

        clearInputReferences()

        rootLayout =
            createRootLayout()

        addHeader(
            "Confirm PIN",
            "Enter your new PIN again"
        )

        addSimpleText(
            "Confirm New PIN",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            24
        )

        val container =
            createInputContainer()

        confirmInput =
            createEditText(
                "Confirm new PIN"
            )

        confirmInput?.inputType =
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD

        confirmInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    6
                )
            )

        addInputWithVisibilityToggle(
            container,
            confirmInput!!,
            true,
            48,
            true
        )

        rootLayout.addView(
            container,
            createLayoutParams(
                0,
                12
            )
        )

        actionButton =
            createButton(
                "Change PIN"
            )

        actionButton.setOnClickListener {

            changePin()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )

        addTargetPackage()

        setContentView(
            rootLayout
        )
    }

    private fun changePin() {

        val currentCredential =
            verifiedCurrentCredential

        val newPin =
            pinInput
                ?.text
                ?.toString()
                ?: ""

        val confirmPin =
            confirmInput
                ?.text
                ?.toString()
                ?: ""

        if (
            currentCredential == null
        ) {

            showPinChangeError(
                "Verify your current credential first"
            )

            return
        }

        if (
            newPin.length != 4 &&
            newPin.length != 6
        ) {

            showPinChangeError(
                "PIN must contain 4 or 6 digits"
            )

            return
        }

        if (
            !newPin.all {
                it.isDigit()
            }
        ) {

            showPinChangeError(
                "PIN must contain only digits"
            )

            return
        }

        if (
            newPin != confirmPin
        ) {

            confirmInput
                ?.text
                ?.clear()

            showPinChangeError(
                "PINs do not match"
            )

            return
        }

        try {

            /*
             * Current PIN / Pattern / Password has already
             * been verified on the previous screen.
             *
             * createPin() stores the new PIN and makes PIN
             * the active lock type.
             *
             * Supports:
             *
             * PIN -> PIN
             * Pattern -> PIN
             * Password -> PIN
             */
            authenticationManager
                .createPin(
                    newPin
                )

            LockSessionManager.clearAll()

            showMessage(
                "PIN changed successfully"
            )

            finish()

        } catch (
            exception: Exception
        ) {

            showPinChangeError(
                exception.message
                    ?: "Unable to change PIN"
            )
        }
    }

    /*
     * =========================================================
     * NEW PASSWORD - SCREEN 1
     * =========================================================
     */

    private fun createNewPasswordScreen() {

        addSimpleText(
            "New Password",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            24
        )

        val container =
            createInputContainer()

        passwordInput =
            createEditText(
                "Enter new password"
            )

        passwordInput?.textSize =
            15f

        passwordInput?.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD

        passwordInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    64
                )
            )

        addInputWithVisibilityToggle(
            container,
            passwordInput!!,
            false,
            48,
            true
        )

        rootLayout.addView(
            container,
            createLayoutParams(
                0,
                12
            )
        )

        addSimpleText(
            "Confirm New Password",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            18
        )

        val confirmContainer =
            createInputContainer()

        confirmInput =
            createEditText(
                "Confirm new password"
            )

        confirmInput?.textSize =
            15f

        confirmInput?.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD

        confirmInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    64
                )
            )

        addInputWithVisibilityToggle(
            confirmContainer,
            confirmInput!!,
            false,
            48,
            true
        )

        rootLayout.addView(
            confirmContainer,
            createLayoutParams(
                0,
                12
            )
        )

        actionButton =
            createButton(
                "Change Password"
            )

        actionButton.minHeight =
            dp(48)

        actionButton.minimumHeight =
            dp(48)

        actionButton.minHeight =
            dp(48)

        actionButton.minimumHeight =
            dp(48)

        actionButton.setOnClickListener {
            changePassword()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )

        addTargetPackage()

        setContentView(
            rootLayout
        )
    }

    private fun continueToConfirmPassword() {

        val newPassword =
            passwordInput
                ?.text
                ?.toString()
                ?: ""

        if (
            newPassword.length < 4
        ) {

            showPasswordChangeError(
                "Password must contain at least 4 characters"
            )

            return
        }

        if (
            newPassword.length > 64
        ) {

            showPasswordChangeError(
                "Password is too long"
            )

            return
        }

        pendingNewCredential =
            newPassword

        showConfirmPasswordScreen()
    }

    /*
     * =========================================================
     * CONFIRM PASSWORD - SCREEN 2
     * =========================================================
     */

    private fun showConfirmPasswordScreen() {

        clearInputReferences()

        rootLayout =
            createRootLayout()

        addHeader(
            "Confirm Password",
            "Enter your new password again"
        )

        addSimpleText(
            "Confirm New Password",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            24
        )

        val container =
            createInputContainer()

        confirmInput =
            createEditText(
                "Confirm new password"
            )

        confirmInput?.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD

        confirmInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    64
                )
            )

        addInputWithVisibilityToggle(
            container,
            confirmInput!!,
            false,
            48,
            true
        )

        rootLayout.addView(
            container,
            createLayoutParams(
                0,
                12
            )
        )

        actionButton =
            createButton(
                "Change Password"
            )

        actionButton.minHeight =
            dp(48)

        actionButton.minimumHeight =
            dp(48)

        actionButton.setOnClickListener {

            changePassword()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )

        addTargetPackage()

        setContentView(
            rootLayout
        )
    }

    private fun changePassword() {

        val currentCredential =
            verifiedCurrentCredential

        val newPassword =
            passwordInput
                ?.text
                ?.toString()
                ?: ""

        val confirmPassword =
            confirmInput
                ?.text
                ?.toString()
                ?: ""

        if (
            currentCredential == null
        ) {

            showPasswordChangeError(
                "Verify your current credential first"
            )

            return
        }

        if (
            newPassword.length == 0 ||
            confirmPassword.length == 0
        ) {

            showPasswordChangeError(
                "All fields are required."
            )

            return
        }

        if (
            newPassword.length < 4
        ) {

            showMessage(
                "Password must contain at least 4 characters"
            )

            return
        }

        if (
            newPassword.length > 64
        ) {

            showMessage(
                "Password is too long"
            )

            return
        }

        if (
            newPassword !=
                confirmPassword
        ) {

            confirmInput
                ?.text
                ?.clear()

            showPasswordChangeError(
                "New Password and Confirm Password do not match."
            )

            return
        }

        if (
            newPassword ==
                currentCredential
        ) {

            showPasswordChangeError(
                "New Password should be different from the current password."
            )

            return
        }

        try {

            authenticationManager
                .changePassword(
                    lockType,
                    currentCredential,
                    newPassword
                )

            LockSessionManager.clearAll()

            showMessage(
                "Password changed successfully"
            )

            finish()

        } catch (
            exception: Exception
        ) {

            showPasswordChangeError(
                exception.message
                    ?: "Unable to change password"
            )
        }
    }

    /*
     * =========================================================
     * NEW PATTERN - SCREEN 1
     * =========================================================
     */

    private fun createNewPatternScreen() {

        addSimpleText(
            "New Pattern",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            24
        )

        patternView =
            PatternView(
                this
            )

        val patternContainer =
            createPatternContainer(
                patternView!!
            )

        rootLayout.addView(
            patternContainer,
            createPatternLayoutParams()
        )

        addSimpleText(
            "Use at least 4 points",
            12,
            Color.rgb(
                120,
                120,
                120
            ),
            10
        )

        actionButton =
            createButton(
                "Continue"
            )

        actionButton.setOnClickListener {

            continueToConfirmPattern()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )
    }

    private fun continueToConfirmPattern() {

        val newPattern =
            patternView
                ?.getPatternString()
                ?: ""

        if (
            newPattern.length < 4
        ) {

            showPatternErrorAndReturn(
                "Pattern must contain at least 4 points"
            )
            return
        }

        pendingNewCredential =
            newPattern

        showConfirmPatternScreen()
    }

    /*
     * =========================================================
     * CONFIRM PATTERN - SCREEN 2
     * =========================================================
     */

    private fun showConfirmPatternScreen() {

        clearInputReferences()

        rootLayout =
            createRootLayout()

        addHeader(
            "Confirm Pattern",
            "Draw the same pattern again"
        )

        addSimpleText(
            "Confirm New Pattern",
            15,
            Color.rgb(
                210,
                210,
                210
            ),
            24
        )

        confirmPatternView =
            PatternView(
                this
            )

        val patternContainer =
            createPatternContainer(
                confirmPatternView!!
            )

        rootLayout.addView(
            patternContainer,
            createPatternLayoutParams()
        )

        addSimpleText(
            "Draw the same pattern again",
            12,
            Color.rgb(
                120,
                120,
                120
            ),
            10
        )

        actionButton =
            createButton(
                "Change Pattern"
            )

        actionButton.setOnClickListener {

            changePattern()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )

        addTargetPackage()

        setContentView(
            rootLayout
        )

        addChangeFlowBackArrow()
    }

    private fun changePattern() {

        val currentCredential =
            verifiedCurrentCredential

        val newPattern =
            pendingNewCredential
                ?: ""

        val confirmPattern =
            confirmPatternView
                ?.getPatternString()
                ?: ""

        if (
            currentCredential == null
        ) {

            showPatternErrorAndReturn(
                "Verify your current credential first"
            )

            return
        }

        if (
            newPattern.length == 0 ||
            confirmPattern.length == 0
        ) {

            showPatternErrorAndReturn(
                "All pattern fields are required."
            )
            return
        }

        if (
            newPattern.length < 4
        ) {

            showPatternErrorAndReturn(
                "New pattern must contain at least 4 points"
            )
            return
        }

        if (
            confirmPattern.length < 4
        ) {

            showPatternErrorAndReturn(
                "Confirm your new pattern"
            )
            return
        }

        if (
            newPattern !=
                confirmPattern
        ) {

            confirmPatternView
                ?.clearPattern()

            showPatternErrorAndReturn(
                "New pattern and confirm pattern do not match"
            )
            return
        }

        if (
            lockType ==
                AuthenticationManager.LOCK_TYPE_PATTERN &&
            newPattern ==
                currentCredential
        ) {

            showPatternErrorAndReturn(
                "New Pattern should be different from the current pattern."
            )
            return
        }

        try {

            authenticationManager
                .changePattern(
                    lockType,
                    currentCredential,
                    newPattern
                )

            LockSessionManager.clearAll()

            showMessage(
                "Pattern changed successfully"
            )

            finish()

        } catch (
            exception: Exception
        ) {

            showPatternErrorAndReturn(
                exception.message
                    ?: "Unable to change pattern"
            )
        }
    }

    private fun showPatternError(
        message: String
    ) {

        if (isFinishing || isDestroyed) {
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Change Pattern")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPatternErrorAndReturn(
        message: String
    ) {

        if (isFinishing || isDestroyed) {
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Change Pattern")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                if (!isFinishing && !isDestroyed) {
                    returnToNewPatternScreen()
                }
            }
            .show()
    }

    private fun returnToNewPatternScreen() {

        pendingNewCredential =
            null

        clearInputReferences()

        rootLayout =
            createRootLayout()

        addHeader(
            "New ${displayLockType(newLockType)}",
            "Enter your new ${displayLockType(newLockType)}"
        )

        createNewPatternScreen()

        addTargetPackage()

        setContentView(
            rootLayout
        )

        addChangeFlowBackArrow()
    }

    /*
     * =========================================================
     * CURRENT CREDENTIAL INPUT
     * =========================================================
     */

    private fun createCurrentCredentialInput(
        currentType: String
    ) {

        addSimpleText(
            "Current ${displayLockType(currentType)}",
            14,
            Color.rgb(
                210,
                210,
                210
            ),
            18
        )

        when (currentType) {

            AuthenticationManager
                .LOCK_TYPE_PIN -> {

                val container =
                    createInputContainer()

                currentCredentialInput =
                    createEditText(
                        "Enter current PIN"
                    )

                currentCredentialInput
                    ?.textSize = 15f

                currentCredentialInput
                    ?.inputType =
                    InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD

                currentCredentialInput
                    ?.filters =
                    arrayOf(
                        InputFilter.LengthFilter(
                            6
                        )
                    )

                addInputWithVisibilityToggle(
                    container,
                    currentCredentialInput!!,
                    true,
                    48,
                    true
                )

                rootLayout.addView(
                    container,
                    createLayoutParams(
                        0,
                        10
                    )
                )
            }

            AuthenticationManager
                .LOCK_TYPE_PASSWORD -> {

                val container =
                    createInputContainer()

                currentCredentialInput =
                    createEditText(
                        "Enter current password"
                    )

                currentCredentialInput
                    ?.textSize = 15f

                currentCredentialInput
                    ?.inputType =
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD

                currentCredentialInput
                    ?.filters =
                    arrayOf(
                        InputFilter.LengthFilter(
                            64
                        )
                    )

                addInputWithVisibilityToggle(
                    container,
                    currentCredentialInput!!,
                    false,
                    48,
                    true
                )

                rootLayout.addView(
                    container,
                    createLayoutParams(
                        0,
                        10
                    )
                )
            }

            AuthenticationManager
                .LOCK_TYPE_PATTERN -> {

                addSimpleText(
                    "Draw your current pattern",
                    14,
                    Color.rgb(
                        165,
                        165,
                        165
                    ),
                    10
                )

                currentPatternView =
                    PatternView(
                        this
                    )

                val patternContainer =
                    createPatternContainer(
                        currentPatternView!!
                    )

                rootLayout.addView(
                    patternContainer,
                    createPatternLayoutParams()
                )

                addSimpleText(
                    "Use at least 4 points",
                    12,
                    Color.rgb(
                        120,
                        120,
                        120
                    ),
                    8
                )
            }
        }
    }

    private fun getCurrentCredential():
        String? {

        return when (lockType) {

            AuthenticationManager
                .LOCK_TYPE_PIN,

            AuthenticationManager
                .LOCK_TYPE_PASSWORD -> {

                val value =
                    currentCredentialInput
                        ?.text
                        ?.toString()
                        ?: ""

                if (
                    value.isEmpty()
                ) {

                    if (
                        isChangingCredential &&
                        newLockType ==
                            AuthenticationManager.LOCK_TYPE_PASSWORD
                    ) {
                        showPasswordChangeError(
                            "Enter your current ${displayLockType(lockType)}"
                        )
                    } else if (
                        isChangingCredential &&
                        newLockType ==
                            AuthenticationManager.LOCK_TYPE_PATTERN
                    ) {
                        showPatternError(
                            "Enter your current ${displayLockType(lockType)}"
                        )
                    } else {
                        showMessage(
                            "Enter your current ${displayLockType(lockType)}"
                        )
                    }

                    null

                } else {

                    value
                }
            }

            AuthenticationManager
                .LOCK_TYPE_PATTERN -> {

                val value =
                    currentPatternView
                        ?.getPatternString()
                        ?: ""

                if (
                    value.length < 4
                ) {

                    if (
                        isChangingCredential &&
                        newLockType ==
                            AuthenticationManager.LOCK_TYPE_PASSWORD
                    ) {
                        showPasswordChangeError(
                            "Draw your current pattern"
                        )
                    } else {
                        showMessage(
                            "Draw your current pattern"
                        )
                    }

                    null

                } else {

                    value
                }
            }

            else -> {

                showMessage(
                    "Current authentication type is not supported"
                )

                null
            }
        }
    }

    /*
     * =========================================================
     * NORMAL PIN
     * =========================================================
     */

    private fun createPinScreen() {

        val container =
            createInputContainer()

        pinInput =
            createEditText(
                "Enter PIN"
            )

        pinInput?.inputType =
            InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_VARIATION_PASSWORD

        pinInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    6
                )
            )

        container.addView(
            pinInput,
            if (isCreatingCredential) {
                createInnerInputParams()
            } else {
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    1f
                )
            }
        )

        rootLayout.addView(
            container,
            createLayoutParams(
                0,
                24
            )
        )

        if (
            isCreatingCredential
        ) {

            val confirmContainer =
                createInputContainer()

            confirmInput =
                createEditText(
                    "Confirm PIN"
                )

            confirmInput?.inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_VARIATION_PASSWORD

            confirmInput?.filters =
                arrayOf(
                    InputFilter.LengthFilter(
                        6
                    )
                )

            confirmContainer.addView(
                confirmInput,
                createInnerInputParams()
            )

            rootLayout.addView(
                confirmContainer,
                createLayoutParams(
                    0,
                    12
                )
            )
        }

        actionButton =
            createButton(
                if (
                    isCreatingCredential
                ) {
                    "Create PIN"
                } else {
                    "Unlock"
                }
            )

        actionButton.setOnClickListener {

            if (
                isCreatingCredential
            ) {
                createPin()
            } else {
                verifyPin()
            }
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )

        if (!isCreatingCredential) {
            actionButton.layoutParams =
                actionButton.layoutParams.apply {
                    height = dp(48)
                }
            actionButton.minHeight = dp(48)
            actionButton.minimumHeight = dp(48)
        }
    }

    private fun createPin() {

        val pin =
            pinInput
                ?.text
                ?.toString()
                ?: ""

        val confirmPin =
            confirmInput
                ?.text
                ?.toString()
                ?: ""

        if (
            pin.length != 4 &&
            pin.length != 6
        ) {

            showMessage(
                "PIN must contain 4 or 6 digits"
            )

            return
        }

        if (
            !pin.all {
                it.isDigit()
            }
        ) {

            showMessage(
                "PIN must contain only digits"
            )

            return
        }

        if (
            pin != confirmPin
        ) {

            showMessage(
                "PINs do not match"
            )

            return
        }

        try {

            authenticationManager
                .createPin(
                    pin
                )

            authenticateAndLaunch()

        } catch (
            exception: Exception
        ) {

            showMessage(
                exception.message
                    ?: "Unable to create PIN"
            )
        }
    }

    private fun verifyPin() {

        val pin =
            pinInput
                ?.text
                ?.toString()
                ?: ""

        if (
            pin.isEmpty()
        ) {

            showMessage(
                "Enter your PIN"
            )

            return
        }

        if (
            authenticationManager
                .verifyPin(
                    pin
                )
        ) {

            authenticateAndLaunch()

        } else {

            pinInput
                ?.text
                ?.clear()

            showMessage(
                "Incorrect PIN"
            )
        }
    }

    /*
     * =========================================================
     * NORMAL PASSWORD
     * =========================================================
     */

    private fun createPasswordScreen() {

        val container =
            createInputContainer()

        passwordInput =
            createEditText(
                "Enter Password"
            )

        passwordInput?.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD

        passwordInput?.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    64
                )
            )

        container.addView(
            passwordInput,
            if (isCreatingCredential) {
                createInnerInputParams()
            } else {
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    1f
                )
            }
        )

        rootLayout.addView(
            container,
            createLayoutParams(
                0,
                24
            )
        )

        if (
            isCreatingCredential
        ) {

            val confirmContainer =
                createInputContainer()

            confirmInput =
                createEditText(
                    "Confirm Password"
                )

            confirmInput?.inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD

            confirmInput?.filters =
                arrayOf(
                    InputFilter.LengthFilter(
                        64
                    )
                )

            confirmContainer.addView(
                confirmInput,
                createInnerInputParams()
            )

            rootLayout.addView(
                confirmContainer,
                createLayoutParams(
                    0,
                    12
                )
            )
        }

        actionButton =
            createButton(
                if (
                    isCreatingCredential
                ) {
                    "Create Password"
                } else {
                    "Unlock"
                }
            )

        actionButton.setOnClickListener {

            if (
                isCreatingCredential
            ) {
                createPassword()
            } else {
                verifyPassword()
            }
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                18
            )
        )

        if (!isCreatingCredential) {
            actionButton.layoutParams =
                actionButton.layoutParams.apply {
                    height = dp(48)
                }
            actionButton.minHeight = dp(48)
            actionButton.minimumHeight = dp(48)
        }
    }

    private fun createPassword() {

        val password =
            passwordInput
                ?.text
                ?.toString()
                ?: ""

        val confirmPassword =
            confirmInput
                ?.text
                ?.toString()
                ?: ""

        if (
            password.length < 4
        ) {

            showMessage(
                "Password must contain at least 4 characters"
            )

            return
        }

        if (
            password.length > 64
        ) {

            showMessage(
                "Password is too long"
            )

            return
        }

        if (
            password !=
                confirmPassword
        ) {

            showMessage(
                "Passwords do not match"
            )

            return
        }

        try {

            authenticationManager
                .createPassword(
                    password
                )

            authenticateAndLaunch()

        } catch (
            exception: Exception
        ) {

            showMessage(
                exception.message
                    ?: "Unable to create password"
            )
        }
    }

    private fun verifyPassword() {

        val password =
            passwordInput
                ?.text
                ?.toString()
                ?: ""

        if (
            password.isEmpty()
        ) {

            showMessage(
                "Enter your password"
            )

            return
        }

        if (
            authenticationManager
                .verifyPassword(
                    password
                )
        ) {

            authenticateAndLaunch()

        } else {

            passwordInput
                ?.text
                ?.clear()

            showMessage(
                "Incorrect password"
            )
        }
    }

    /*
     * =========================================================
     * NORMAL PATTERN
     * =========================================================
     */

    private fun createPatternScreen() {

        addSimpleText(
            if (
                isCreatingCredential
            ) {
                "Draw your unlock pattern"
            } else {
                "Draw your pattern to unlock"
            },
            14,
            Color.rgb(
                210,
                210,
                210
            ),
            22
        )

        patternView =
            PatternView(
                this
            )

        rootLayout.addView(
            createPatternContainer(
                patternView!!
            ),
            createPatternLayoutParams()
        )

        addSimpleText(
            "Use at least 4 points",
            12,
            Color.rgb(
                120,
                120,
                120
            ),
            10
        )

        actionButton =
            createButton(
                if (
                    isCreatingCredential
                ) {
                    "Create Pattern"
                } else {
                    "Unlock"
                }
            )

        actionButton.setOnClickListener {

            if (
                isCreatingCredential
            ) {
                createPattern()
            } else {
                verifyPattern()
            }
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                16
            )
        )
    }

    private fun createPattern() {

        val pattern =
            patternView
                ?.getPatternString()
                ?: ""

        if (
            pattern.length < 4
        ) {

            showMessage(
                "Pattern must contain at least 4 points"
            )

            return
        }

        try {

            authenticationManager
                .createPattern(
                    pattern
                )

            authenticateAndLaunch()

        } catch (
            exception: Exception
        ) {

            showMessage(
                exception.message
                    ?: "Unable to create pattern"
            )
        }
    }

    private fun verifyPattern() {

        val pattern =
            patternView
                ?.getPatternString()
                ?: ""

        if (
            pattern.isEmpty()
        ) {

            showMessage(
                "Draw your pattern"
            )

            return
        }

        if (
            authenticationManager
                .verifyPattern(
                    pattern
                )
        ) {

            authenticateAndLaunch()

        } else {

            patternView
                ?.clearPattern()

            showMessage(
                "Incorrect pattern"
            )
        }
    }

    /*
     * =========================================================
     * BIOMETRIC
     * =========================================================
     */

    private fun createBiometricScreen() {

        addSimpleText(
            "Use biometric authentication to continue",
            14,
            Color.rgb(
                165,
                165,
                165
            ),
            24
        )

        actionButton =
            createButton(
                "Use Biometric"
            )

        actionButton.setOnClickListener {

            authenticateBiometric()
        }

        rootLayout.addView(
            actionButton,
            createLayoutParams(
                0,
                24
            )
        )
    }

    private fun authenticateBiometric() {

        if (
            !isBiometricLayerEnabled()
        ) {

            showCredentialFallbackScreen()

            return
        }

        if (
            Build.VERSION.SDK_INT < 28
        ) {

            showCredentialFallbackScreen()

            return
        }

        val biometricManager =
            getSystemService(
                Context.BIOMETRIC_SERVICE
            ) as android.hardware.biometrics.BiometricManager

        val canAuthenticate =
            if (
                Build.VERSION.SDK_INT >= 30
            ) {

                biometricManager.canAuthenticate(
                    android.hardware.biometrics
                        .BiometricManager
                        .Authenticators
                        .BIOMETRIC_WEAK
                )

            } else {

                @Suppress(
                    "DEPRECATION"
                )
                biometricManager.canAuthenticate()
            }

        if (
            canAuthenticate !=
                android.hardware.biometrics
                    .BiometricManager
                    .BIOMETRIC_SUCCESS
        ) {

            showCredentialFallbackScreen()

            return
        }

        val executor =
            mainExecutor

        val promptBuilder =
            android.hardware.biometrics
                .BiometricPrompt
                .Builder(
                    this
                )
                .setTitle(
                    "OpenAppLock"
                )
                .setSubtitle(
                    biometricSubtitle()
                )

        promptBuilder.setNegativeButton(
            "Use ${displayLockType(lockType)}",
            executor,
            DialogInterface.OnClickListener {
                _, _ ->
                showCredentialFallbackScreen()
            }
        )

        val prompt =
            promptBuilder.build()

        prompt.authenticate(
            CancellationSignal(),
            executor,
            object :
                android.hardware.biometrics
                    .BiometricPrompt
                    .AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result:
                        android.hardware.biometrics
                            .BiometricPrompt
                            .AuthenticationResult
                ) {

                    super.onAuthenticationSucceeded(
                        result
                    )

                    authenticateAndLaunch()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {

                    super.onAuthenticationError(
                        errorCode,
                        errString
                    )

                    if (
                        errorCode ==
                            android.hardware.biometrics
                                .BiometricPrompt
                                .BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode ==
                            android.hardware.biometrics
                                .BiometricPrompt
                                .BIOMETRIC_ERROR_CANCELED
                    ) {
                        // Samsung/Android can report a transient canceled
                        // callback when the biometric operation is dismissed
                        // or interrupted. Do not show it as a user-facing toast.
                        return
                    }

                    showMessage(
                        errString.toString()
                    )
                }

                override fun onAuthenticationFailed() {

                    super.onAuthenticationFailed()

                    // Keep the biometric prompt open so the user can retry.
                    // The system prompt also provides the fallback button.
                }
            }
        )
    }


    /*
     * =========================================================
     * SUCCESS
     * =========================================================
     */

    private fun authenticateAndLaunch() {

        LockSessionManager
            .authenticate(
                targetPackage
            )

        cancelReplacementNotification()

        showMessage(
            "Unlocked"
        )

        launchTargetApplication()
    }

    /*
     * =========================================================
     * UI HELPERS
     * =========================================================
     */

    private fun clearInputReferences() {

        pinInput = null
        passwordInput = null
        confirmInput = null
        patternView = null
        confirmPatternView = null
        currentCredentialInput = null
        currentPatternView = null
    }

    private fun createLockScreenBackground(): Drawable {

        val accent =
            getTargetAppAccentColor()

        return object : Drawable() {

            private val paint =
                Paint(Paint.ANTI_ALIAS_FLAG)

            override fun draw(
                canvas: Canvas
            ) {

                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()

                canvas.drawColor(
                    Color.rgb(10, 10, 10)
                )

                // Curved-header style: a very subtle app-colored
                // ambient shape flows down from the top corners.
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

                canvas.drawPath(
                    headerPath,
                    paint
                )

                // Keep the existing subtle ambient glow around the screen.
                drawGlow(
                    canvas,
                    width * 0.02f,
                    height * 0.22f,
                    width * 0.42f,
                    accent
                )
                drawGlow(
                    canvas,
                    width * 0.98f,
                    height * 0.40f,
                    width * 0.38f,
                    accent
                )
                drawGlow(
                    canvas,
                    width * 0.08f,
                    height * 0.82f,
                    width * 0.34f,
                    accent
                )
                drawGlow(
                    canvas,
                    width * 0.92f,
                    height * 0.76f,
                    width * 0.32f,
                    accent
                )

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
                packageManager.getApplicationInfo(
                    targetPackage,
                    0
                )

            val drawable =
                packageManager.getApplicationIcon(
                    appInfo
                )

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

    private fun createRootLayout():
        LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            gravity =
                Gravity.CENTER

            setPadding(
                dp(28),
                dp(36),
                dp(28),
                dp(36)
            )

            setBackgroundColor(
                Color.rgb(
                    10,
                    10,
                    10
                )
            )
        }
    }

    private fun addHeader(
        titleText: String,
        subtitleText: String,
        showTargetApp: Boolean = false
    ) {

        if (showTargetApp) {

            var appIcon: android.graphics.drawable.Drawable? =
                null

            var appName =
                ""

            try {
                val appInfo =
                    packageManager.getApplicationInfo(
                        targetPackage,
                        0
                    )

                appIcon =
                    packageManager.getApplicationIcon(
                        appInfo
                    )

                appName =
                    packageManager.getApplicationLabel(
                        appInfo
                    ).toString()
            } catch (_: Exception) {
                // Keep the locked screen usable even if app metadata is unavailable.
            }

            if (appIcon != null) {
                val appIconView =
                    ImageView(this).apply {
                        setImageDrawable(appIcon)
                        scaleType =
                            ImageView.ScaleType.FIT_CENTER
                    }

                rootLayout.addView(
                    appIconView,
                    createLayoutParams(
                        72,
                        0
                    )
                )
            }

            if (appName.isNotEmpty()) {
                val appNameView =
                    TextView(this).apply {
                        text =
                            appName
                        textSize =
                            16f
                        setTextColor(
                            Color.WHITE
                        )
                        gravity =
                            Gravity.CENTER
                    }

                rootLayout.addView(
                    appNameView,
                    createLayoutParams(
                        0,
                        6
                    )
                )
            }

        } else {

            val lockIcon =
                TextView(this).apply {

                    text =
                        "🔒"

                    textSize =
                        46f

                    gravity =
                        Gravity.CENTER
                }

            rootLayout.addView(
                lockIcon,
                createLayoutParams(
                    0,
                    0
                )
            )
        }

        val title =
            TextView(this).apply {

                text =
                    titleText

                textSize =
                    27f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER
            }

        rootLayout.addView(
            title,
            createLayoutParams(
                0,
                if (showTargetApp) 14 else 16
            )
        )

        val subtitle =
            TextView(this).apply {

                text =
                    subtitleText

                textSize =
                    14f

                setTextColor(
                    Color.rgb(
                        165,
                        165,
                        165
                    )
                )

                gravity =
                    Gravity.CENTER

                setLineSpacing(
                    dp(2).toFloat(),
                    1f
                )
            }

        rootLayout.addView(
            subtitle,
            createLayoutParams(
                0,
                if (showTargetApp) 10 else 8
            )
        )
    }

    private fun addSimpleText(
        value: String,
        size: Int,
        color: Int,
        topMargin: Int
    ) {

        val text =
            TextView(this).apply {

                text =
                    value

                textSize =
                    size.toFloat()

                setTextColor(
                    color
                )

                gravity =
                    Gravity.CENTER

                setLineSpacing(
                    dp(2).toFloat(),
                    1f
                )
            }

        rootLayout.addView(
            text,
            createLayoutParams(
                0,
                topMargin
            )
        )
    }

    private fun addTargetPackage() {

        if (
            targetPackage.isEmpty()
        ) {
            return
        }

        val target =
            TextView(this).apply {

                text =
                    targetPackage

                textSize =
                    10f

                setTextColor(
                    Color.rgb(
                        70,
                        70,
                        70
                    )
                )

                gravity =
                    Gravity.CENTER
            }

        rootLayout.addView(
            target,
            createLayoutParams(
                0,
                16
            )
        )
    }

    private fun createInputContainer():
        LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL

            gravity =
                Gravity.CENTER_VERTICAL

            background =
                createRoundedBackground(
                    Color.rgb(
                        25,
                        25,
                        25
                    ),
                    dp(16).toFloat()
                )

            setPadding(
                dp(4),
                dp(4),
                dp(4),
                dp(4)
            )
        }
    }

    private fun addInputWithVisibilityToggle(
        container: LinearLayout,
        editText: EditText,
        isPin: Boolean,
        fieldHeightDp: Int = 58,
        changeModeStyle: Boolean = false
    ) {
        if (changeModeStyle) {
            container.background =
                GradientDrawable().apply {
                    setColor(
                        Color.rgb(
                            15,
                            15,
                            15
                        )
                    )
                    cornerRadius =
                        dp(16).toFloat()
                    setStroke(
                        dp(1),
                        Color.rgb(
                            65,
                            65,
                            65
                        )
                    )
                }

            container.setPadding(
                dp(0),
                dp(0),
                dp(0),
                dp(0)
            )
        }

        val inputParams =
            LinearLayout.LayoutParams(
                0,
                dp(fieldHeightDp),
                1f
            )

        if (changeModeStyle) {
            editText.background =
                null
            editText.setPadding(
                dp(18),
                dp(4),
                dp(8),
                dp(4)
            )
        }

        container.addView(
            editText,
            inputParams
        )

        var visible = false

        editText.transformationMethod =
            PasswordTransformationMethod.getInstance()

        val visibilityButton =
            ImageButton(this).apply {
                background = null
                imageTintList = null
                clearColorFilter()
                if (changeModeStyle) {
                    // Exact Change PIN icon sizing: eye-off 26x24, eye-visible 32x24.
                    setPadding(
                        dp(11),
                        dp(12),
                        dp(11),
                        dp(12)
                    )
                } else {
                    setPadding(
                        0,
                        0,
                        0,
                        0
                    )
                }
                scaleType =
                    android.widget.ImageView.ScaleType.FIT_CENTER
                minimumWidth = dp(48)
                minimumHeight = dp(48)
                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                    )
                contentDescription =
                    if (visible) {
                        "Hide ${if (isPin) "PIN" else "password"}"
                    } else {
                        "Show ${if (isPin) "PIN" else "password"}"
                    }
            }

        fun updateIcon() {
            val iconName =
                if (visible) {
                    "eye_visible_icon_ui"
                } else {
                    "eye_off_icon_ui"
                }

            val iconResId =
                resources.getIdentifier(
                    iconName,
                    "drawable",
                    packageName
                )

            if (iconResId == 0) {
                return
            }

            visibilityButton.imageTintList = null
            visibilityButton.clearColorFilter()

            if (changeModeStyle) {
                // Match Change PIN exactly: visible 32x24, hidden 26x24.
                val horizontalPadding =
                    if (visible) dp(8) else dp(11)
                visibilityButton.setPadding(
                    horizontalPadding,
                    dp(12),
                    horizontalPadding,
                    dp(12)
                )
            }

            visibilityButton.setImageResource(iconResId)
            visibilityButton.visibility = View.VISIBLE
            visibilityButton.alpha = 1f
            visibilityButton.invalidate()

            visibilityButton.contentDescription =
                if (visible) {
                    "Hide ${if (isPin) "PIN" else "password"}"
                } else {
                    "Show ${if (isPin) "PIN" else "password"}"
                }
        }

        updateIcon()

        visibilityButton.setOnClickListener {
            visible = !visible
            val selection = editText.selectionStart

            editText.transformationMethod =
                if (visible) {
                    HideReturnsTransformationMethod.getInstance()
                } else {
                    PasswordTransformationMethod.getInstance()
                }

            val safeSelection =
                selection.coerceIn(
                    0,
                    editText.text.length
                )
            editText.setSelection(safeSelection)
            updateIcon()
        }

        container.addView(
            visibilityButton,
            LinearLayout.LayoutParams(
                dp(48),
                dp(fieldHeightDp)
            ).apply {
                gravity =
                    Gravity.CENTER_VERTICAL
            }
        )
    }

    private fun createInnerInputParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            0,
            dp(58),
            1f
        )
    }

    private fun createEditText(
        hintText: String
    ): EditText {

        return EditText(this).apply {

            hint =
                hintText

            textSize =
                18f

            gravity =
                Gravity.CENTER_VERTICAL or
                    Gravity.START

            setTextColor(
                Color.WHITE
            )

            setHintTextColor(
                Color.rgb(
                    110,
                    110,
                    110
                )
            )

            setSingleLine(
                true
            )

            background =
                null

            setPadding(
                dp(14),
                dp(4),
                dp(14),
                dp(4)
            )
        }
    }

    private fun createButton(
        textValue: String
    ): Button {

        return Button(this).apply {

            text =
                textValue

            textSize =
                16f

            typeface =
                Typeface.DEFAULT_BOLD

            setTextColor(
                Color.rgb(
                    10,
                    10,
                    10
                )
            )

            background =
                createRoundedBackground(
                    Color.WHITE,
                    dp(16).toFloat()
                )

            isAllCaps =
                false

            minHeight =
                dp(56)

            minimumHeight =
                dp(56)

            stateListAnimator =
                null
        }
    }

    private fun createPatternContainer(
        pattern: PatternView
    ): LinearLayout {

        return LinearLayout(this).apply {

            orientation =
                LinearLayout.VERTICAL

            gravity =
                Gravity.CENTER

            background =
                createRoundedBackground(
                    Color.rgb(
                        20,
                        20,
                        20
                    ),
                    dp(22).toFloat()
                )

            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            addView(
                pattern,
                LinearLayout.LayoutParams(
                    dp(290),
                    dp(290)
                )
            )
        }
    }

    private fun createPatternLayoutParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            dp(314),
            dp(314)
        ).apply {

            gravity =
                Gravity.CENTER

            topMargin =
                dp(8)
        }
    }

    private fun createRoundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(
                color
            )

            cornerRadius =
                radius
        }
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
        ).toInt()
    }

    private fun createLayoutParams(
        leftMargin: Int,
        topMargin: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {

            this.leftMargin =
                dp(leftMargin)

            this.rightMargin =
                dp(leftMargin)

            this.topMargin =
                dp(topMargin)
        }
    }

    private fun displayLockType(
        value: String
    ): String {

        return when (value) {

            AuthenticationManager
                .LOCK_TYPE_PIN ->
                "PIN"

            AuthenticationManager
                .LOCK_TYPE_PATTERN ->
                "Pattern"

            AuthenticationManager
                .LOCK_TYPE_PASSWORD ->
                "Password"

            AuthenticationManager
                .LOCK_TYPE_BIOMETRIC ->
                "Biometric"

            else ->
                "Credential"
        }
    }

    /*
     * =========================================================
     * OPEN ORIGINAL APP
     * =========================================================
     */

    private fun launchTargetApplication() {

        if (
            targetPackage.isEmpty()
        ) {

            finish()

            return
        }

        try {

            val launchIntent =
                packageManager
                    .getLaunchIntentForPackage(
                        targetPackage
                    )

            if (
                launchIntent == null
            ) {

                showMessage(
                    "Unable to open the app"
                )

                finish()

                return
            }

            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )

            startActivity(
                launchIntent
            )

            finish()

        } catch (
            exception: Exception
        ) {

            showMessage(
                "Unable to open the app"
            )

            finish()
        }
    }

    /*
     * =========================================================
     * NOTIFICATION CLEANUP
     * =========================================================
     */

    private fun cancelReplacementNotification() {

        try {

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as android.app.NotificationManager

            val hash =
                targetPackage.hashCode() and
                    0x7fffffff

            val notificationId =
                70000 +
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

    private fun showPinChangeError(
        message: String
    ) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Change PIN")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPasswordChangeError(
        message: String
    ) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /*
     * =========================================================
     * MESSAGE
     * =========================================================
     */

    private fun showMessage(
        message: String
    ) {

        android.widget.Toast.makeText(
            this,
            message,
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /*
     * =========================================================
     * PATTERN VIEW
     * =========================================================
     */

    private class PatternView(
        context: Context
    ) : View(context) {

        private val dotPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        private val selectedPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        private val linePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        private val points =
            ArrayList<Int>()

        private val pointX =
            FloatArray(9)

        private val pointY =
            FloatArray(9)

        private var currentX =
            0f

        private var currentY =
            0f

        private var drawing =
            false

        init {

            dotPaint.color =
                Color.rgb(
                    120,
                    120,
                    120
                )

            dotPaint.style =
                Paint.Style.FILL

            selectedPaint.color =
                Color.WHITE

            selectedPaint.style =
                Paint.Style.STROKE

            selectedPaint.strokeWidth =
                8f

            linePaint.color =
                Color.WHITE

            linePaint.style =
                Paint.Style.STROKE

            linePaint.strokeWidth =
                7f

            linePaint.strokeCap =
                Paint.Cap.ROUND

            linePaint.strokeJoin =
                Paint.Join.ROUND

            setBackgroundColor(
                Color.rgb(
                    18,
                    18,
                    18
                )
            )
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(
                canvas
            )

            val width =
                width.toFloat()

            val height =
                height.toFloat()

            val size =
                minOf(
                    width,
                    height
                )

            val startX =
                (width - size) / 2f

            val startY =
                (height - size) / 2f

            val spacing =
                size / 4f

            for (
                row in 0..2
            ) {

                for (
                    column in 0..2
                ) {

                    val index =
                        row * 3 +
                            column

                    pointX[index] =
                        startX +
                            spacing +
                            column * spacing

                    pointY[index] =
                        startY +
                            spacing +
                            row * spacing
                }
            }

            val path =
                Path()

            points.forEachIndexed {
                    index,
                    pointIndex ->

                val x =
                    pointX[
                        pointIndex
                    ]

                val y =
                    pointY[
                        pointIndex
                    ]

                if (
                    index == 0
                ) {

                    path.moveTo(
                        x,
                        y
                    )

                } else {

                    path.lineTo(
                        x,
                        y
                    )
                }
            }

            if (
                drawing &&
                points.isNotEmpty()
            ) {

                path.lineTo(
                    currentX,
                    currentY
                )
            }

            canvas.drawPath(
                path,
                linePaint
            )

            for (
                index in 0 until 9
            ) {

                val selected =
                    points.contains(
                        index
                    )

                if (
                    selected
                ) {

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

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {

            when (
                event.action
            ) {

                MotionEvent.ACTION_DOWN -> {

                    clearPattern()

                    drawing =
                        true

                    currentX =
                        event.x

                    currentY =
                        event.y

                    addPointIfNeeded(
                        event.x,
                        event.y
                    )

                    invalidate()

                    return true
                }

                MotionEvent.ACTION_MOVE -> {

                    if (
                        drawing
                    ) {

                        currentX =
                            event.x

                        currentY =
                            event.y

                        addPointIfNeeded(
                            event.x,
                            event.y
                        )

                        invalidate()
                    }

                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    drawing =
                        false

                    invalidate()

                    return true
                }
            }

            return true
        }

        private fun addPointIfNeeded(
            x: Float,
            y: Float
        ) {

            var closest =
                -1

            var closestDistance =
                Float.MAX_VALUE

            for (
                index in 0 until 9
            ) {

                if (
                    points.contains(
                        index
                    )
                ) {
                    continue
                }

                val dx =
                    x -
                        pointX[index]

                val dy =
                    y -
                        pointY[index]

                val distance =
                    dx * dx +
                        dy * dy

                if (
                    distance <
                        closestDistance
                ) {

                    closestDistance =
                        distance

                    closest =
                        index
                }
            }

            if (
                closest >= 0 &&
                closestDistance <=
                    65f * 65f
            ) {

                points.add(
                    closest
                )
            }
        }

        fun getPatternString():
            String {

            return points.joinToString(
                ""
            )
        }

        fun clearPattern() {

            points.clear()

            drawing =
                false

            currentX =
                0f

            currentY =
                0f

            invalidate()
        }
    }
    private class BackArrowView(
        context: Context
    ) : View(context) {

        // Same visual geometry as chevron_left.xml:
        // M15,5 L8,12 L15,19, with a 2dp rounded white stroke.
        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth =
                    2f * resources.displayMetrics.density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val d =
                resources.displayMetrics.density

            // Draw the 24dp chevron centered inside the 48dp touch area.
            val offset =
                12f * d

            val tipX =
                offset + 8f * d

            val endX =
                offset + 15f * d

            val topY =
                offset + 5f * d

            val centerY =
                offset + 12f * d

            val bottomY =
                offset + 19f * d

            canvas.drawLine(
                endX,
                topY,
                tipX,
                centerY,
                paint
            )

            canvas.drawLine(
                tipX,
                centerY,
                endX,
                bottomY,
                paint
            )
        }
    }

}