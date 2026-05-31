package com.example.ouija_mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import java.io.IOException

private data class UrlsResponse(
    @SerializedName("web")   val web: String,
    @SerializedName("api")   val api: String,
    @SerializedName("media") val media: String
)

class ServerConfigActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        // Only skip this screen if fully logged in
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, ChatsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_server_config)

        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Pre-fill with saved URL if there is one, otherwise default to emulator address
        etServerUrl.setText(sessionManager.getServerUrl() ?: "http://10.0.2.2:3000")

        btnConnect.setOnClickListener {
            val url = etServerUrl.text.toString().trim().removeSuffix("/")
            if (url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                fetchUrls(url, btnConnect, progressBar)
            } else {
                Toast.makeText(this, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fetch /urls from the web server, save all three URLs, then proceed to login
    private fun fetchUrls(webUrl: String, button: Button, progress: ProgressBar) {
        button.isEnabled = false
        progress.visibility = View.VISIBLE

        val request = Request.Builder()
            .url("$webUrl/urls")
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
                val bodyStr = response.body?.string() ?: ""
                runOnUiThread {
                    button.isEnabled = true
                    progress.visibility = View.GONE
                }
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@ServerConfigActivity, getString(R.string.error_server_unreachable), Toast.LENGTH_LONG).show()
                    }
                    return
                }
                runCatching { gson.fromJson(bodyStr, UrlsResponse::class.java) }
                    .onSuccess { urls ->
                        sessionManager.saveServerUrls(urls.web, urls.api, urls.media)
                        runOnUiThread {
                            startActivity(Intent(this@ServerConfigActivity, LoginActivity::class.java))
                            finish()
                        }
                    }
                    .onFailure {
                        runOnUiThread {
                            Toast.makeText(this@ServerConfigActivity, getString(R.string.error_server_unreachable), Toast.LENGTH_LONG).show()
                        }
                    }
            }
        })
    }
}