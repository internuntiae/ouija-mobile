package com.example.ouija_mobile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ouija_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_URL    = "api_url"
        private const val KEY_MEDIA_URL  = "media_url"
        private const val KEY_USER_ID    = "user_id"
        private const val KEY_NICKNAME   = "nickname"
        private const val KEY_EMAIL      = "email"
        private const val KEY_TOKEN      = "session_token"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_THEME      = "app_theme"
    }

    fun saveSession(userId: String, nickname: String, email: String, token: String, avatarUrl: String?) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_EMAIL, email)
            .putString(KEY_TOKEN, token)
            .putString(KEY_AVATAR_URL, avatarUrl)
            .apply()
    }

    fun saveServerUrls(webUrl: String, apiUrl: String, mediaUrl: String) {
        prefs.edit()
            .putString(KEY_SERVER_URL, webUrl)
            .putString(KEY_API_URL, apiUrl)
            .putString(KEY_MEDIA_URL, mediaUrl)
            .apply()
    }

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)
    fun getApiUrl(): String?    = prefs.getString(KEY_API_URL, null)
    fun getMediaUrl(): String?  = prefs.getString(KEY_MEDIA_URL, null)

    fun getUserId(): String?    = prefs.getString(KEY_USER_ID, null)
    fun getNickname(): String?  = prefs.getString(KEY_NICKNAME, null)
    fun getEmail(): String?     = prefs.getString(KEY_EMAIL, null)
    fun getToken(): String?     = prefs.getString(KEY_TOKEN, null)
    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR_URL, null)

    fun isLoggedIn(): Boolean = getUserId() != null && getToken() != null

    /** Saves the theme key, e.g. "Theme.Ouija.Midnight" */
    fun saveTheme(themeKey: String) = prefs.edit().putString(KEY_THEME, themeKey).apply()
    fun getTheme(): String = prefs.getString(KEY_THEME, "Theme.Ouija") ?: "Theme.Ouija"

    fun clearSession() {
        val theme = getTheme()          // preserve theme across logout
        val apiUrl = getApiUrl()
        val webUrl = getServerUrl()
        val mediaUrl = getMediaUrl()
        prefs.edit().clear().apply()
        // restore non-auth prefs
        prefs.edit()
            .putString(KEY_THEME, theme)
            .apply()
        if (apiUrl != null && webUrl != null && mediaUrl != null)
            saveServerUrls(webUrl, apiUrl, mediaUrl!!)
    }
}
