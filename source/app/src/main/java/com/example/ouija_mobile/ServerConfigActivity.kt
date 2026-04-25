package com.example.ouija_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import okhttp3.*
import java.io.IOException

class ServerConfigActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        // If server URL is already set, we might still want to check connectivity if coming from splash
        // But for now, let's follow the requirement of adding the activity.
        if (sessionManager.getServerUrl() != null) {
            if (sessionManager.isLoggedIn()) {
                startActivity(Intent(this, ChatsActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
            return
        }

        setContentView(R.layout.activity_server_config)

        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        etServerUrl.setText("http://10.0.2.2:3000")

        btnConnect.setOnClickListener {
            val url = etServerUrl.text.toString().trim().removeSuffix("/")
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                checkServerConnectivity(url, btnConnect, progressBar)
            } else {
                Toast.makeText(this, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkServerConnectivity(url: String, button: Button, progress: ProgressBar) {
        button.isEnabled = false
        progress.visibility = View.VISIBLE

        // We check /api/users as a "ping" since it should be available
        val request = Request.Builder()
            .url("$url/api/users")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    button.isEnabled = true
                    progress.visibility = View.GONE
                    Toast.makeText(this@ServerConfigActivity, getString(R.string.error_server_unreachable), Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // We don't care about the response code much, as long as the server responded (even with 401)
                runOnUiThread {
                    button.isEnabled = true
                    progress.visibility = View.GONE
                    sessionManager.saveServerUrl(url)
                    startActivity(Intent(this@ServerConfigActivity, LoginActivity::class.java))
                    finish()
                }
            }
        })
    }
}
