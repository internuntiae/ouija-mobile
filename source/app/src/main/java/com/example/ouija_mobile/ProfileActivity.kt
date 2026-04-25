package com.example.ouija_mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(sessionManager)

        val tvNickname = findViewById<TextView>(R.id.tvNickname)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val tvInitials = findViewById<TextView>(R.id.tvInitials)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        tvNickname.text = sessionManager.getNickname() ?: ""
        tvEmail.text = sessionManager.getEmail() ?: ""
        tvInitials.text = (sessionManager.getNickname() ?: "?").take(2).uppercase()

        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> true
                R.id.nav_chats -> {
                    startActivity(Intent(this, ChatsActivity::class.java))
                    finish()
                    false
                }
                else -> false
            }
        }

        val userId = sessionManager.getUserId()
        if (userId != null) {
            apiClient.getUser(userId,
                onSuccess = { user ->
                    runOnUiThread {
                        tvNickname.text = user.nickname
                        tvEmail.text = user.email
                        tvInitials.text = user.nickname.take(2).uppercase()
                    }
                },
                onError = { /* use cached data */ }
            )
        }

        btnLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
