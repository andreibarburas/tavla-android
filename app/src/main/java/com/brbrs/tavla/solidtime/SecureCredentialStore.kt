package com.brbrs.tavla.solidtime

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Solidtime personal access token is a long-lived secret, so unlike the rest of Tavla's
 * settings it is kept in [EncryptedSharedPreferences] (AES-256, key held in the Android Keystore)
 * rather than DataStore or the Nextcloud settings file. It never leaves the device and never
 * syncs. The non-secret connection details (server URL, chosen organization and the matching
 * member id) live here too so they stay together and are wiped atomically on disconnect.
 *
 * If the encrypted store can't be opened (a rare keystore corruption, e.g. after a device
 * restore), we fall back to a plain prefs file so the app keeps working; [usingFallback] lets the
 * UI warn that the token isn't encrypted in that case.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile var usingFallback: Boolean = false
        private set

    private val prefs: SharedPreferences by lazy { openPrefs() }

    private fun openPrefs(): SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "tavla_solidtime_secure",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs: ${e.message}")
        usingFallback = true
        context.getSharedPreferences("tavla_solidtime_fallback", Context.MODE_PRIVATE)
    }

    var serverUrl: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_URL, value).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_TOKEN, value).apply()

    var organizationId: String?
        get() = prefs.getString(KEY_ORG, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_ORG, value).apply()

    var organizationName: String?
        get() = prefs.getString(KEY_ORG_NAME, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_ORG_NAME, value).apply()

    var memberId: String?
        get() = prefs.getString(KEY_MEMBER, null)
        set(value) = prefs.edit().putStringOrRemove(KEY_MEMBER, value).apply()

    /** True once a server URL, token, organization and member id are all present. */
    val isConnected: Boolean
        get() = !serverUrl.isNullOrBlank() && !token.isNullOrBlank() &&
            !organizationId.isNullOrBlank() && !memberId.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun SharedPreferences.Editor.putStringOrRemove(key: String, value: String?): SharedPreferences.Editor =
        if (value == null) remove(key) else putString(key, value)

    companion object {
        private const val TAG = "SolidtimeSecure"
        private const val KEY_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_ORG = "organization_id"
        private const val KEY_ORG_NAME = "organization_name"
        private const val KEY_MEMBER = "member_id"
    }
}
