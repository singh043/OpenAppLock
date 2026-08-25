package com.openapplocktest.applock

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.EditText
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

        private const val AUTH_PREFS_NAME =
            "applock_auth"

        private const val KEY_LOCK_TYPE =
            "lock_type"

        private const val KEY_PENDING_LOCK_TYPE =
            "pending_lock_type"
    }

    private lateinit var authenticationManager:
        AuthenticationManager

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

                AuthenticationManager
                    .LOCK_TYPE_BIOMETRIC ->
                    true

                else ->
                    hasPin
            }

        isCreatingCredential =
            !credentialExists

        rootLayout =
            createRootLayout()

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

                    AuthenticationManager
                        .LOCK_TYPE_BIOMETRIC ->
                        "Enable Biometric"

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

                    AuthenticationManager
                        .LOCK_TYPE_BIOMETRIC ->
                        "Use your biometric to enable AppLock"

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

                    AuthenticationManager
                        .LOCK_TYPE_BIOMETRIC ->
                        "Use biometric authentication to continue"

                    else ->
                        "Enter your PIN to continue"
                }
            }

        addHeader(
            title,
            subtitle
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

            AuthenticationManager
                .LOCK_TYPE_BIOMETRIC -> {

                createBiometricScreen()
            }

            else -> {

                lockType =
                    AuthenticationManager
                        .LOCK_TYPE_PIN

                createPinScreen()
            }
        }

        addTargetPackage()

        setContentView(
            rootLayout
        )

        pinInput?.requestFocus()

        passwordInput?.requestFocus()

        if (
            lockType ==
                AuthenticationManager
                    .LOCK_TYPE_BIOMETRIC
        ) {

            window.decorView.postDelayed(
                {
                    authenticateBiometric()
                },
                300
            )
        }
    }

    /*
     * =========================================================
     * CHANGE LOCK TYPE / CREDENTIAL
     * =========================================================
     */

    private fun showCurrentCredentialScreen() {

        clearInputReferences()

        verifiedCurrentCredential =
            null

        pendingNewCredential =
            null

        rootLayout =
            createRootLayout()

        addChangeFlowBackButton()

        val currentType =
            authenticationManager
                .getLockType()

        lockType =
            currentType

        if (
            currentType ==
                AuthenticationManager
                    .LOCK_TYPE_BIOMETRIC
        ) {

            addHeader(
                "Change ${displayLockType(newLockType)}",
                "Use your biometric to confirm this change"
            )

            addSimpleText(
                "Authenticate with your fingerprint or face to continue.",
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
                    "Authenticate"
                )

            actionButton.setOnClickListener {
                authenticateBiometricForChange()
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
                }

                else -> {

                    currentCredentialInput
                        ?.text
                        ?.clear()
                }
            }

            showMessage(
                "Incorrect current ${displayLockType(lockType)}"
            )

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

        addChangeFlowBackButton()

        addHeader(
            "New ${displayLockType(newLockType)}",
            if (newLockType == AuthenticationManager.LOCK_TYPE_BIOMETRIC) {
                ""
            } else {
                "Enter your new ${displayLockType(newLockType)}"
            }
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
                    "Your biometric is already verified. Authenticate to enable biometric",
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
                        "Authenticate"
                    )

                actionButton.setOnClickListener {
                    authenticateBiometricForSetup()
                }

                rootLayout.addView(
                    actionButton,
                    createLayoutParams(
                        0,
                        20
                    )
                )

                window.decorView.postDelayed(
                    {
                        authenticateBiometricForSetup()
                    },
                    300
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
            createInnerInputParams()
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

        addChangeFlowBackButton()

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

        container.addView(
            confirmInput,
            createInnerInputParams()
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

            showMessage(
                "Verify your current credential first"
            )

            return
        }

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

        if (
            newPin != confirmPin
        ) {

            confirmInput
                ?.text
                ?.clear()

            showMessage(
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

            showMessage(
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
            createInnerInputParams()
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

        actionButton =
            createButton(
                "Change Password"
            )

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

        addChangeFlowBackButton()

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

        container.addView(
            confirmInput,
            createInnerInputParams()
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

            showMessage(
                "Verify your current credential first"
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

            showMessage(
                "Passwords do not match"
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

            showMessage(
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

            showMessage(
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

        addChangeFlowBackButton()

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

            showMessage(
                "Verify your current credential first"
            )

            return
        }

        if (
            newPattern.length < 4
        ) {

            showMessage(
                "New pattern must contain at least 4 points"
            )

            return
        }

        if (
            confirmPattern.length < 4
        ) {

            showMessage(
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

            showMessage(
                "Patterns do not match"
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

            showMessage(
                exception.message
                    ?: "Unable to change pattern"
            )
        }
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

                container.addView(
                    currentCredentialInput,
                    createInnerInputParams()
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

                container.addView(
                    currentCredentialInput,
                    createInnerInputParams()
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

                    showMessage(
                        "Enter your current ${displayLockType(lockType)}"
                    )

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

                    showMessage(
                        "Draw your current pattern"
                    )

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
            createInnerInputParams()
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
            createInnerInputParams()
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
            Build.VERSION.SDK_INT < 28
        ) {

            showMessage(
                "Biometric authentication is not supported on this Android version"
            )

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

            showMessage(
                "Biometric authentication is not available"
            )

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
                    "Authenticate to unlock"
                )

        promptBuilder.setNegativeButton(
            "Cancel",
            executor,
            DialogInterface.OnClickListener {
                _, _ ->
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

                    showMessage(
                        errString.toString()
                    )
                }

                override fun onAuthenticationFailed() {

                    super.onAuthenticationFailed()

                    showMessage(
                        "Biometric not recognized"
                    )
                }
            }
        )
    }

    private fun authenticateBiometricForSetup() {

        if (
            Build.VERSION.SDK_INT < 28
        ) {
            showMessage(
                "Biometric authentication is not supported on this Android version"
            )

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
            showMessage(
                "Biometric authentication is not available"
            )

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
                    "Confirm biometric to enable AppLock"
                )

        promptBuilder.setNegativeButton(
            "Cancel",
            executor,
            DialogInterface.OnClickListener {
                _, _ ->
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

                    completeBiometricSetup()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {

                    super.onAuthenticationError(
                        errorCode,
                        errString
                    )

                    showMessage(
                        errString.toString()
                    )
                }

                override fun onAuthenticationFailed() {

                    super.onAuthenticationFailed()

                    showMessage(
                        "Biometric not recognized"
                    )
                }
            }
        )
    }

    private fun completeBiometricSetup() {

        val authenticationPreferences =
            applicationContext.getSharedPreferences(
                AUTH_PREFS_NAME,
                Context.MODE_PRIVATE
            )

        authenticationPreferences
            .edit()
            .putString(
                KEY_LOCK_TYPE,
                AuthenticationManager
                    .LOCK_TYPE_BIOMETRIC
            )
            .remove(
                KEY_PENDING_LOCK_TYPE
            )
            .apply()

        LockSessionManager.clearAll()

        finish()
    }

    private fun authenticateBiometricForChange() {

        if (
            Build.VERSION.SDK_INT < 28
        ) {
            showMessage(
                "Biometric authentication is not supported on this Android version"
            )

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
            showMessage(
                "Biometric authentication is not available"
            )

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
                    "Authenticate to change lock type"
                )

        promptBuilder.setNegativeButton(
            "Cancel",
            executor,
            DialogInterface.OnClickListener {
                _, _ ->
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

                    verifiedCurrentCredential =
                        "BIOMETRIC_VERIFIED"

                    showNewCredentialScreen()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {

                    super.onAuthenticationError(
                        errorCode,
                        errString
                    )

                    showMessage(
                        errString.toString()
                    )
                }

                override fun onAuthenticationFailed() {

                    super.onAuthenticationFailed()

                    showMessage(
                        "Biometric not recognized"
                    )
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

    private fun addChangeFlowBackButton() {

        window.decorView.post {

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

            arrow.isClickable =
                true

            arrow.isFocusable =
                true

            arrow.visibility =
                View.VISIBLE

            arrow.alpha =
                1f

            arrow.elevation =
                20f * density

            addContentView(
                arrow,
                params
            )

            arrow.bringToFront()

            changeFlowBackArrow =
                arrow
        }
    }

    private class BackArrowView(
        context: Context
    ) : View(context) {

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth =
                    2f * resources.displayMetrics.density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        override fun onDraw(
            canvas: Canvas
        ) {
            super.onDraw(canvas)

            val d =
                resources.displayMetrics.density

            // 24dp chevron centered in the 48dp touch area.
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

    private fun addHeader(
        titleText: String,
        subtitleText: String
    ) {

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
                16
            )
        )

        if (subtitleText.isNotBlank()) {
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
                    8
                )
            )
        }
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

    private fun createInnerInputParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
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
                Gravity.CENTER

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
}