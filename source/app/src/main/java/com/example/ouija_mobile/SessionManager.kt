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
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password" // stored for Basic Auth header
        private const val KEY_AVATAR_URL = "avatar_url"
    }

    fun saveSession(userId: String, nickname: String, email: String, password: String, avatarUrl: String?) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_AVATAR_URL, avatarUrl)
            .apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getNickname(): String? = prefs.getString(KEY_NICKNAME, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)
    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR_URL, null)

    fun isLoggedIn(): Boolean = getUserId() != null

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
