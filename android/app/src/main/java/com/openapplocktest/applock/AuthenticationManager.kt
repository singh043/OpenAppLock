package com.openapplocktest.applock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

class AuthenticationManager(
    context: Context
) {

    companion object {

        private const val PREFS_NAME =
            "applock_auth"

        private const val KEY_LOCK_TYPE =
            "lock_type"

        private const val KEY_PIN_HASH =
            "pin_hash"

        private const val KEY_PIN_SALT =
            "pin_salt"

        private const val KEY_PASSWORD_HASH =
            "password_hash"

        private const val KEY_PASSWORD_SALT =
            "password_salt"

        private const val KEY_PATTERN_HASH =
            "pattern_hash"

        private const val KEY_PATTERN_SALT =
            "pattern_salt"

        const val LOCK_TYPE_PIN =
            "pin"

        const val LOCK_TYPE_PATTERN =
            "pattern"

        const val LOCK_TYPE_PASSWORD =
            "password"

        const val LOCK_TYPE_BIOMETRIC =
            "biometric"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

    /*
     * ---------------------------------------------------------
     * LOCK TYPE
     * ---------------------------------------------------------
     */

    fun getLockType(): String {

        return preferences.getString(
            KEY_LOCK_TYPE,
            LOCK_TYPE_PIN
        )
            ?: LOCK_TYPE_PIN
    }

    private fun setStoredLockType(
        lockType: String
    ) {

        require(
            lockType ==
                LOCK_TYPE_PIN ||
                lockType ==
                LOCK_TYPE_PATTERN ||
                lockType ==
                LOCK_TYPE_PASSWORD ||
                lockType ==
                LOCK_TYPE_BIOMETRIC
        ) {
            "Invalid lock type"
        }

        preferences
            .edit()
            .putString(
                KEY_LOCK_TYPE,
                lockType
            )
            .apply()
    }

    /*
     * ---------------------------------------------------------
     * GENERIC CURRENT CREDENTIAL VERIFICATION
     * ---------------------------------------------------------
     *
     * Used when changing lock type.
     *
     * Example:
     *
     * Current lock = PIN
     * New lock     = Pattern
     *
     * verifyCredential(
     *     PIN,
     *     current PIN
     * )
     *
     * ---------------------------------------------------------
     */

    fun verifyCredential(
        lockType: String,
        credential: String
    ): Boolean {

        return when (lockType) {

            LOCK_TYPE_PIN -> {

                verifyPin(
                    credential
                )
            }

            LOCK_TYPE_PATTERN -> {

                verifyPattern(
                    credential
                )
            }

            LOCK_TYPE_PASSWORD -> {

                verifyPassword(
                    credential
                )
            }

            LOCK_TYPE_BIOMETRIC -> {

                false
            }

            else -> {

                false
            }
        }
    }

    /*
     * ---------------------------------------------------------
     * PIN
     * ---------------------------------------------------------
     */

    fun hasPin(): Boolean {

        return preferences.contains(
            KEY_PIN_HASH
        ) &&
            preferences.contains(
                KEY_PIN_SALT
            )
    }

    fun createPin(
        pin: String
    ) {

        validatePin(
            pin
        )

        val salt =
            generateSalt()

        val hash =
            hashValue(
                pin,
                salt
            )

        preferences
            .edit()
            .putString(
                KEY_PIN_HASH,
                bytesToHex(
                    hash
                )
            )
            .putString(
                KEY_PIN_SALT,
                bytesToHex(
                    salt
                )
            )
            .putString(
                KEY_LOCK_TYPE,
                LOCK_TYPE_PIN
            )
            .apply()
    }

    fun verifyPin(
        pin: String
    ): Boolean {

        return verifyValue(
            pin,
            KEY_PIN_HASH,
            KEY_PIN_SALT
        )
    }

    fun changePin(
        currentPin: String,
        newPin: String
    ) {

        if (
            !verifyPin(
                currentPin
            )
        ) {

            throw IllegalArgumentException(
                "Current PIN is incorrect"
            )
        }

        validatePin(
            newPin
        )

        val salt =
            generateSalt()

        val hash =
            hashValue(
                newPin,
                salt
            )

        /*
         * Credential + active lock type are saved
         * together.
         *
         * If validation/current credential fails,
         * nothing is changed.
         */
        preferences
            .edit()
            .putString(
                KEY_PIN_HASH,
                bytesToHex(
                    hash
                )
            )
            .putString(
                KEY_PIN_SALT,
                bytesToHex(
                    salt
                )
            )
            .putString(
                KEY_LOCK_TYPE,
                LOCK_TYPE_PIN
            )
            .apply()
    }

    /*
     * ---------------------------------------------------------
     * PASSWORD
     * ---------------------------------------------------------
     */

    fun hasPassword(): Boolean {

        return preferences.contains(
            KEY_PASSWORD_HASH
        ) &&
            preferences.contains(
                KEY_PASSWORD_SALT
            )
    }

    fun createPassword(
        password: String
    ) {

        validatePassword(
            password
        )

        val salt =
            generateSalt()

        val hash =
            hashValue(
                password,
                salt
            )

        preferences
            .edit()
            .putString(
                KEY_PASSWORD_HASH,
                bytesToHex(
                    hash
                )
            )
            .putString(
                KEY_PASSWORD_SALT,
                bytesToHex(
                    salt
                )
            )
            .apply()
    }

    fun verifyPassword(
        password: String
    ): Boolean {

        return verifyValue(
            password,
            KEY_PASSWORD_HASH,
            KEY_PASSWORD_SALT
        )
    }

    /*
     * ---------------------------------------------------------
     * CHANGE PASSWORD
     * ---------------------------------------------------------
     *
     * Current credential can be:
     *
     * PIN
     * Pattern
     * Password
     *
     * This is required because user can change:
     *
     * PIN -> Password
     * Pattern -> Password
     * Password -> Password
     *
     * ---------------------------------------------------------
     */

    fun changePassword(
        currentLockType: String,
        currentCredential: String,
        newPassword: String
    ) {

        if (
            !verifyCredential(
                currentLockType,
                currentCredential
            )
        ) {

            throw IllegalArgumentException(
                "Current $currentLockType is incorrect"
            )
        }

        validatePassword(
            newPassword
        )

        val salt =
            generateSalt()

        val hash =
            hashValue(
                newPassword,
                salt
            )

        /*
         * Save the new password and make Password
         * the active lock type only after the
         * current credential has been verified.
         */
        preferences
            .edit()
            .putString(
                KEY_PASSWORD_HASH,
                bytesToHex(
                    hash
                )
            )
            .putString(
                KEY_PASSWORD_SALT,
                bytesToHex(
                    salt
                )
            )
            .putString(
                KEY_LOCK_TYPE,
                LOCK_TYPE_PASSWORD
            )
            .apply()
    }

    /*
     * ---------------------------------------------------------
     * PATTERN
     * ---------------------------------------------------------
     */

    fun hasPattern(): Boolean {

        return preferences.contains(
            KEY_PATTERN_HASH
        ) &&
            preferences.contains(
                KEY_PATTERN_SALT
            )
    }

    fun createPattern(
        pattern: String
    ) {

        validatePattern(
            pattern
        )

        val salt =
            generateSalt()

        val hash =
            hashValue(
                pattern,
                salt
            )

        preferences
            .edit()
            .putString(
                KEY_PATTERN_HASH,
                bytesToHex(
                    hash
                )
            )
            .putString(
                KEY_PATTERN_SALT,
                bytesToHex(
                    salt
                )
            )
            .apply()
    }

    fun verifyPattern(
        pattern: String
    ): Boolean {

        return verifyValue(
            pattern,
            KEY_PATTERN_HASH,
            KEY_PATTERN_SALT
        )
    }

    /*
     * ---------------------------------------------------------
     * CHANGE PATTERN
     * ---------------------------------------------------------
     *
     * Current credential can be:
     *
     * PIN
     * Pattern
     * Password
     *
     * Examples:
     *
     * PIN -> Pattern
     * Pattern -> Pattern
     * Password -> Pattern
     *
     * ---------------------------------------------------------
     */

    fun changePattern(
        currentLockType: String,
        currentCredential: String,
        newPattern: String
    ) {

        if (
            !verifyCredential(
                currentLockType,
                currentCredential
            )
        ) {

            throw IllegalArgumentException(
                "Current $currentLockType is incorrect"
            )
        }

        validatePattern(
            newPattern
        )

        val salt =
            generateSalt()

        val hash =
            hashValue(
                newPattern,
                salt
            )

        /*
         * Save new pattern and active lock type
         * together after current credential
         * verification succeeds.
         */
        preferences
            .edit()
            .putString(
                KEY_PATTERN_HASH,
                bytesToHex(
                    hash
                )
            )
            .putString(
                KEY_PATTERN_SALT,
                bytesToHex(
                    salt
                )
            )
            .putString(
                KEY_LOCK_TYPE,
                LOCK_TYPE_PATTERN
            )
            .apply()
    }

    /*
     * ---------------------------------------------------------
     * CHANGE CURRENT CREDENTIAL WITHOUT CHANGING TYPE
     * ---------------------------------------------------------
     *
     * These methods are useful for:
     *
     * Change PIN
     * Change Pattern
     * Change Password
     *
     * when the selected type remains the same.
     * ---------------------------------------------------------
     */

    fun changeCurrentPassword(
        currentPassword: String,
        newPassword: String
    ) {

        changePassword(
            LOCK_TYPE_PASSWORD,
            currentPassword,
            newPassword
        )
    }

    fun changeCurrentPattern(
        currentPattern: String,
        newPattern: String
    ) {

        changePattern(
            LOCK_TYPE_PATTERN,
            currentPattern,
            newPattern
        )
    }

    /*
     * ---------------------------------------------------------
     * VALIDATION
     * ---------------------------------------------------------
     */

    private fun validatePin(
        pin: String
    ) {

        require(
            pin.length == 4 ||
                pin.length == 6
        ) {
            "PIN must contain 4 or 6 digits"
        }

        require(
            pin.all {
                it.isDigit()
            }
        ) {
            "PIN must contain only digits"
        }
    }

    private fun validatePassword(
        password: String
    ) {

        require(
            password.length >= 4
        ) {
            "Password must contain at least 4 characters"
        }

        require(
            password.length <= 64
        ) {
            "Password must contain at most 64 characters"
        }
    }

    private fun validatePattern(
        pattern: String
    ) {

        require(
            pattern.length >= 4
        ) {
            "Pattern must contain at least 4 points"
        }

        require(
            pattern.length <= 9
        ) {
            "Invalid pattern"
        }

        require(
            pattern.all {
                it in '0'..'8'
            }
        ) {
            "Invalid pattern"
        }

        require(
            pattern.toSet().size ==
                pattern.length
        ) {
            "Pattern contains duplicate points"
        }
    }

    /*
     * ---------------------------------------------------------
     * HASHING
     * ---------------------------------------------------------
     */

    private fun generateSalt():
        ByteArray {

        return ByteArray(
            16
        ).also {

            SecureRandom()
                .nextBytes(
                    it
                )
        }
    }

    private fun hashValue(
        value: String,
        salt: ByteArray
    ): ByteArray {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        digest.update(
            salt
        )

        return digest.digest(
            value.toByteArray(
                Charsets.UTF_8
            )
        )
    }

    private fun verifyValue(
        value: String,
        hashKey: String,
        saltKey: String
    ): Boolean {

        val storedHash =
            preferences.getString(
                hashKey,
                null
            )
                ?: return false

        val storedSalt =
            preferences.getString(
                saltKey,
                null
            )
                ?: return false

        return try {

            val salt =
                hexToBytes(
                    storedSalt
                )

            val calculatedHash =
                hashValue(
                    value,
                    salt
                )

            MessageDigest.isEqual(
                hexToBytes(
                    storedHash
                ),
                calculatedHash
            )

        } catch (
            exception: Exception
        ) {

            false
        }
    }

    /*
     * ---------------------------------------------------------
     * HEX
     * ---------------------------------------------------------
     */

    private fun bytesToHex(
        bytes: ByteArray
    ): String {

        return bytes.joinToString(
            ""
        ) {

            "%02x".format(
                it
            )
        }
    }

    private fun hexToBytes(
        hex: String
    ): ByteArray {

        return hex
            .chunked(
                2
            )
            .map {

                it.toInt(
                    16
                ).toByte()
            }
            .toByteArray()
    }
}