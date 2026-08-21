package com.openapplocktest.applock

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.InputFilter

class LockActivity : Activity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
    }

    private lateinit var authenticationManager: AuthenticationManager

    private lateinit var pinInput: EditText
    private lateinit var confirmPinInput: EditText
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authenticationManager =
            AuthenticationManager(applicationContext)

        val targetPackage =
            intent.getStringExtra(EXTRA_TARGET_PACKAGE)
                ?: "Unknown app"

        showAuthenticationScreen(targetPackage)
    }

    private fun showAuthenticationScreen(
        targetPackage: String
    ) {
        val hasPin = authenticationManager.hasPin()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(
                Color.rgb(16, 16, 16)
            )
        }

        val title = TextView(this).apply {
            text = if (hasPin) {
                "App Locked"
            } else {
                "Create AppLock PIN"
            }

            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val message = TextView(this).apply {
            text = if (hasPin) {
                "Enter your AppLock PIN"
            } else {
                "Create a 4 or 6 digit PIN"
            }

            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        pinInput = createPinInput()

        layout.addView(title)

        layout.addView(
            message,
            createLayoutParams(24)
        )

        layout.addView(
            pinInput,
            createLayoutParams(24)
        )

        if (hasPin) {

            actionButton = Button(this).apply {
                text = "Unlock"

                setOnClickListener {
                    verifyPin()
                }
            }

            layout.addView(
                actionButton,
                createLayoutParams(24)
            )

        } else {

            confirmPinInput = createPinInput()

            confirmPinInput.hint = "Confirm PIN"

            layout.addView(
                confirmPinInput,
                createLayoutParams(16)
            )

            actionButton = Button(this).apply {
                text = "Create PIN"

                setOnClickListener {
                    createPin()
                }
            }

            layout.addView(
                actionButton,
                createLayoutParams(24)
            )
        }

        val target = TextView(this).apply {
            text = targetPackage
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        layout.addView(
            target,
            createLayoutParams(20)
        )

        setContentView(layout)
    }

    private fun createPinInput(): EditText {
        return EditText(this).apply {
            hint = "PIN"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)

            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_VARIATION_PASSWORD

            filters = arrayOf(
    InputFilter.LengthFilter(6)
)
        }
    }

    private fun createPin() {

        val pin = pinInput.text.toString()
        val confirmPin =
            confirmPinInput.text.toString()

        if (pin.length != 4 && pin.length != 6) {
            showMessage(
                "PIN must contain 4 or 6 digits"
            )
            return
        }

        if (!pin.all { it.isDigit() }) {
            showMessage(
                "PIN must contain only digits"
            )
            return
        }

        if (pin != confirmPin) {
            showMessage(
                "PINs do not match"
            )
            return
        }

        try {

            authenticationManager.createPin(pin)

            showMessage("PIN created")

            finish()

        } catch (exception: Exception) {

            showMessage(
                exception.message
                    ?: "Unable to create PIN"
            )
        }
    }

    private fun verifyPin() {

        val pin = pinInput.text.toString()

        if (pin.isEmpty()) {
            showMessage("Enter your PIN")
            return
        }

        if (authenticationManager.verifyPin(pin)) {

            showMessage("Unlocked")

            finish()

        } else {

            pinInput.text.clear()

            showMessage("Incorrect PIN")
        }
    }

    private fun createLayoutParams(
        topMargin: Int
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = topMargin
        }
    }

    private fun showMessage(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
}