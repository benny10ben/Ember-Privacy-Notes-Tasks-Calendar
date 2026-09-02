package com.ben.emberr.domain.ai.external

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

actual class SecureAiKeyStorage(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    actual fun getConfig(provider: ExternalAiProvider): ExternalAiProviderConfig? {
        val raw = preferences.getString(configKey(provider), null) ?: return null
        return try {
            json.decodeFromString(ExternalAiProviderConfig.serializer(), raw)
        } catch (cause: SerializationException) {
            null
        }
    }

    actual fun saveConfig(provider: ExternalAiProvider, config: ExternalAiProviderConfig) {
        preferences.edit()
            .putString(configKey(provider), json.encodeToString(ExternalAiProviderConfig.serializer(), config))
            .apply()
    }

    actual fun clearConfig(provider: ExternalAiProvider) {
        preferences.edit().remove(configKey(provider)).apply()
    }

    actual fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun configKey(provider: ExternalAiProvider) = "ai_provider_config_${provider.name}"

    private companion object {
        const val PREFERENCES_FILE_NAME = "emberr_ai_key_vault"
    }
}
