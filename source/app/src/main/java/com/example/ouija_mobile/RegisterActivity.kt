package com.example.ouija_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(sessionManager)

        val etNickname = findViewById<EditText>(R.id.etNickname)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val progressBar = findViewById<View>(R.id.progressBar)

        btnRegister.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (nickname.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Backend requires min 8 characters
            if (password.length < 8) {
                Toast.makeText(this, "Hasło musi mieć min. 8 znaków", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            // POST /api/auth/register — returns user object (no token yet)
            // After registration, we log in automatically to get a token
            apiClient.register(email, password, nickname,
                onSuccess = { _ ->
                    // Registration succeeded — now log in to get the session token
                    apiClient.login(nickname, password,
                        onSuccess = { result ->
                            sessionManager.saveSession(
                                result.user.id,
                                result.user.nickname,
                                result.user.email ?: email,
                                result.token,
                                result.user.avatarUrl
                            )
                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                startActivity(Intent(this, ChatsActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                })
                            }
                        },
                        onError = { loginError ->
                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                btnRegister.isEnabled = true
                                // Registration worked but auto-login failed — redirect to login screen
                                Toast.makeText(this, "Konto utworzone. Zaloguj się.", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        }
                    )
                },
                onError = { error ->
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        btnRegister.isEnabled = true
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        tvLogin.setOnClickListener { finish() }
    }
}