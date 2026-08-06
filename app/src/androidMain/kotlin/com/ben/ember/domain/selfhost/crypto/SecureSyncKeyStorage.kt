package com.ben.ember.domain.selfhost.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ben.ember.domain.selfhost.webdav.SelfHostServerCredentials
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Base64

actual class SecureSyncKeyStorage(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual fun saveEncryptionKey(key: ByteArray) {
        preferences.edit()
            .putString(KEY_ENCRYPTION_KEY, Base64.getEncoder().encodeToString(key))
            .apply()
    }

    actual fun getEncryptionKey(): ByteArray? {
        val encoded = preferences.getString(KEY_ENCRYPTION_KEY, null) ?: return null
        return Base64.getDecoder().decode(encoded)
    }

    actual fun saveServerCredentials(credentials: SelfHostServerCredentials) {
        preferences.edit()
            .putString(
                KEY_SERVER_CREDENTIALS,
                json.encodeToString(SelfHostServerCredentials.serializer(), credentials)
            )
            .apply()
    }

    actual fun getServerCredentials(): SelfHostServerCredentials? {
        val raw = preferences.getString(KEY_SERVER_CREDENTIALS, null) ?: return null
        return try {
            json.decodeFromString(SelfHostServerCredentials.serializer(), raw)
        } catch (cause: SerializationException) {
            null
        }
    }

    actual fun clearAll() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_FILE_NAME = "ember_selfhost_sync_vault"
        const val KEY_ENCRYPTION_KEY = "selfhost_encryption_key"
        const val KEY_SERVER_CREDENTIALS = "selfhost_server_credentials"
    }
}