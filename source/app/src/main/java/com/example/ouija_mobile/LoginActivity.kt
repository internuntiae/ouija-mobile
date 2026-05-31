package com.example.ouija_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        // If server URL is not configured, go to ServerConfigActivity
        if (sessionManager.getServerUrl() == null) {
            startActivity(Intent(this, ServerConfigActivity::class.java))
            finish()
            return
        }

        if (sessionManager.isLoggedIn()) {
            startChats()
            return
        }

        setContentView(R.layout.activity_login)
        apiClient = ApiClient(sessionManager)

        // The backend authenticates by NICKNAME (not email)
        val etNickname = findViewById<EditText>(R.id.etEmail)   // reuse existing view id
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val progressBar = findViewById<View>(R.id.progressBar)

        // Update hint if the layout still says "Email"
        etNickname.hint = "Nickname"

        btnLogin.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            val password = etPassword.text.toString()

            if (nickname.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            apiClient.login(nickname, password,
                onSuccess = { result ->
                    // result.token is the Bearer token used for all subsequent requests
                    sessionManager.saveSession(
                        result.user.id,
                        result.user.nickname,
                        result.user.email ?: "",
                        result.token,
                        result.user.avatarUrl
                    )
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        startChats()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        btnLogin.isEnabled = true
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun startChats() {
        startActivity(Intent(this, ChatsActivity::class.java))
        finish()
    }
}