package com.nobi.nobistream2.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nobi.nobistream2.BuildConfig

/**
 * Telegram API credentials.
 *
 * Priority: credentials the user saved in Settings (on this phone only) win;
 * otherwise the credentials injected at build time from CI secrets are used
 * (BuildConfig fields, never written in source). Nothing here ever logs or
 * displays the values.
 */
class ApiCredentialsStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "nobistream2_api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Credentials injected at build time; 0 / blank means not built in. */
    val hasBuiltIn: Boolean
        get() = BuildConfig.TELEGRAM_API_ID != 0 &&
            BuildConfig.TELEGRAM_API_HASH.isNotBlank()

    /** True when the user saved credentials in Settings on this phone. */
    val hasSaved: Boolean
        get() = prefs.getString(KEY_API_ID, null)?.toIntOrNull() != null &&
            !prefs.getString(KEY_API_HASH, null).isNullOrBlank()

    fun getApiId(): Int {
        prefs.getString(KEY_API_ID, null)?.toIntOrNull()?.let { return it }
        return BuildConfig.TELEGRAM_API_ID
    }

    fun getApiHash(): String {
        prefs.getString(KEY_API_HASH, null)?.takeIf { it.isNotBlank() }?.let { return it }
        return BuildConfig.TELEGRAM_API_HASH
    }

    fun hasCredentials(): Boolean = getApiId() != 0 && getApiHash().isNotBlank()

    fun save(apiId: Int, apiHash: String) {
        prefs.edit()
            .putString(KEY_API_ID, apiId.toString())
            .putString(KEY_API_HASH, apiHash)
            .apply()
    }

    /** Forget saved credentials; built-in ones (if any) become active again. */
    fun clearSaved() {
        prefs.edit().remove(KEY_API_ID).remove(KEY_API_HASH).apply()
    }

    companion object {
        private const val KEY_API_ID = "api_id"
        private const val KEY_API_HASH = "api_hash"
    }
}
