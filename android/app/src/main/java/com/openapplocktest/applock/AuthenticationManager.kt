package com.openapplocktest.applock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

class AuthenticationManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "applock_auth"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun hasPin(): Boolean {
        return preferences.contains(KEY_PIN_HASH) &&
            preferences.contains(KEY_PIN_SALT)
    }

    fun createPin(pin: String) {
        require(pin.length == 4 || pin.length == 6) {
            "PIN must contain 4 or 6 digits"
        }

        require(pin.all { it.isDigit() }) {
            "PIN must contain only digits"
        }

        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val hash = hashPin(pin, salt)

        preferences.edit()
            .putString(KEY_PIN_HASH, bytesToHex(hash))
            .putString(KEY_PIN_SALT, bytesToHex(salt))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash =
            preferences.getString(KEY_PIN_HASH, null)
                ?: return false

        val storedSalt =
            preferences.getString(KEY_PIN_SALT, null)
                ?: return false

        val salt = hexToBytes(storedSalt)
        val calculatedHash = hashPin(pin, salt)

        return MessageDigest.isEqual(
            hexToBytes(storedHash),
            calculatedHash
        )
    }

    private fun hashPin(
        pin: String,
        salt: ByteArray
    ): ByteArray {

        val digest = MessageDigest.getInstance("SHA-256")

        digest.update(salt)

        return digest.digest(
            pin.toByteArray(Charsets.UTF_8)
        )
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") {
            "%02x".format(it)
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}