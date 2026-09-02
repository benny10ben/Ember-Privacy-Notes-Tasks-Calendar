package com.ben.emberr.core.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object EncryptionManager {

    private const val PREFS_NAME = "emberr_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "sqlcipher_passphrase"

    fun getDatabasePassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = sharedPreferences.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }

        val newPassphrase = ByteArray(32)
        SecureRandom().nextBytes(newPassphrase)

        sharedPreferences.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(newPassphrase, Base64.NO_WRAP))
            .apply()

        return newPassphrase
    }
}