package com.example.ouija_mobile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors

class ProfileActivity : BaseActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var apiClient: ApiClient

    private lateinit var ivAvatar: ImageView
    private lateinit var tvInitials: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        sessionManager = SessionManager(this)
        apiClient = ApiClient(sessionManager)

        // ── User info ──────────────────────────────────────────────
        tvInitials = findViewById(R.id.tvInitials)
        ivAvatar   = findViewById(R.id.ivAvatar)
        val tvNickname = findViewById<TextView>(R.id.tvNickname)
        val tvEmail    = findViewById<TextView>(R.id.tvEmail)
        val tvStatus   = findViewById<TextView>(R.id.tvStatus)

        val cached = sessionManager.getNickname() ?: ""
        tvNickname.text = cached
        tvEmail.text    = sessionManager.getEmail() ?: ""
        tvInitials.text = cached.take(2).uppercase()
        tvStatus.text   = ""

        // Load cached avatar if available
        sessionManager.getAvatarUrl()?.let { loadAvatarFromUrl(it) }

        val userId = sessionManager.getUserId()
        if (userId != null) {
            apiClient.getUser(userId,
                onSuccess = { user ->
                    runOnUiThread {
                        tvNickname.text = user.nickname
                        tvEmail.text    = user.email ?: sessionManager.getEmail() ?: ""
                        tvInitials.text = user.nickname.take(2).uppercase()
                        tvStatus.text   = formatStatus(user.status)
                        user.avatarUrl?.let { loadAvatarFromUrl(it) }
                    }
                },
                onError = {}
            )
        }

        // ── Friends ────────────────────────────────────────────────
        val friendsRecycler = findViewById<RecyclerView>(R.id.recyclerFriends)
        val tvFriendsEmpty  = findViewById<TextView>(R.id.tvFriendsEmpty)
        val friendsAdapter  = FriendAdapter(userId ?: "")
        friendsRecycler.layoutManager = LinearLayoutManager(this)
        friendsRecycler.adapter = friendsAdapter

        if (userId != null) {
            apiClient.getFriends(userId,
                onSuccess = { friendships ->
                    val accepted = friendships.filter { it.status == "ACCEPTED" }
                    runOnUiThread {
                        tvFriendsEmpty.visibility = if (accepted.isEmpty()) View.VISIBLE else View.GONE
                        friendsRecycler.visibility = if (accepted.isEmpty()) View.GONE else View.VISIBLE
                        friendsAdapter.setFriends(accepted)
                    }
                },
                onError = {
                    runOnUiThread { tvFriendsEmpty.visibility = View.VISIBLE }
                }
            )
        }

        // ── Change server URL ──────────────────────────────────────
        val btnChangeUrl = findViewById<Button>(R.id.btnChangeUrl)
        val tvCurrentUrl = findViewById<TextView>(R.id.tvCurrentUrl)
        tvCurrentUrl.text = sessionManager.getApiUrl() ?: "nie ustawiono"

        btnChangeUrl.setOnClickListener {
            val input = EditText(this).apply {
                setText(sessionManager.getApiUrl() ?: "")
                hint = "http://10.0.2.2:3001"
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(this)
                .setTitle("Zmień adres serwera")
                .setView(input)
                .setPositiveButton("Zapisz") { _, _ ->
                    val newUrl = input.text.toString().trim().removeSuffix("/")
                    if (newUrl.isNotEmpty()) {
                        sessionManager.saveServerUrls(
                            newUrl.replace(":3001", ":3000"),
                            newUrl,
                            newUrl.replace(":3001", ":3001") + "/media"
                        )
                        tvCurrentUrl.text = newUrl
                        Toast.makeText(this, "Zapisano — zaloguj się ponownie", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        // ── Theme picker ───────────────────────────────────────────
        val btnTheme = findViewById<Button>(R.id.btnTheme)
        btnTheme.setOnClickListener { showThemePicker() }

        // ── Bottom nav ─────────────────────────────────────────────
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> true
                R.id.nav_chats   -> {
                    startActivity(Intent(this, ChatsActivity::class.java))
                    finish(); false
                }
                else -> false
            }
        }

        // ── Logout ─────────────────────────────────────────────────
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            apiClient.logout {
                sessionManager.clearSession()
                runOnUiThread {
                    startActivity(Intent(this, ServerConfigActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            }
        }
    }

    // ── Avatar loading ─────────────────────────────────────────────────────────

    private fun loadAvatarFromUrl(url: String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val inputStream: InputStream = URL(url).openStream()
                val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
                runOnUiThread {
                    ivAvatar.setImageBitmap(bitmap)
                    ivAvatar.visibility = View.VISIBLE
                    tvInitials.visibility = View.GONE
                }
            } catch (_: Exception) {
                // Keep initials visible on error
            }
        }
    }

    // ── Theme ──────────────────────────────────────────────────────────────────

    private fun showThemePicker() {
        data class ThemeOption(val label: String, val key: String, val bg: Int, val accent: Int)

        val themes = listOf(
            ThemeOption("🌑  Dark",           "Theme.Ouija",           0xFF111111.toInt(), 0xFFFFFFFF.toInt()),
            ThemeOption("☀️  Light",          "Theme.Ouija.Light",     0xFFF5F5F5.toInt(), 0xFF111111.toInt()),
            ThemeOption("🌙  Midnight Blue",  "Theme.Ouija.Midnight",  0xFF0D1117.toInt(), 0xFF58A6FF.toInt()),
            ThemeOption("🌲  Forest",         "Theme.Ouija.Forest",    0xFF0F1A12.toInt(), 0xFF56D364.toInt()),
            ThemeOption("🌸  Rose",           "Theme.Ouija.Rose",      0xFF1A0E14.toInt(), 0xFFFF79C6.toInt()),
            ThemeOption("🌅  Solarized",      "Theme.Ouija.Solarized", 0xFFFDF6E3.toInt(), 0xFF268BD2.toInt()),
        )
        val current = sessionManager.getTheme()
        val currentIdx = themes.indexOfFirst { it.key == current }.coerceAtLeast(0)

        val names = themes.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Wybierz motyw")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                val chosen = themes[which]
                sessionManager.saveTheme(chosen.key)
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    override fun recreate() {
        finish()
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun formatStatus(status: String) = when (status) {
        "ONLINE"    -> "🟢 Online"
        "OFFLINE"   -> "⚫ Offline"
        "AWAY"      -> "🟡 Zaraz wracam"
        "BUSY"      -> "🔴 Nie przeszkadzać"
        "INVISIBLE" -> "👻 Niewidoczny"
        else        -> status
    }
}

// ── FriendAdapter ─────────────────────────────────────────────────────────────

class FriendAdapter(private val myUserId: String) :
    RecyclerView.Adapter<FriendAdapter.VH>() {

    private val items = mutableListOf<Friendship>()

    fun setFriends(newList: List<Friendship>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = items[oldItemPosition]
                val new = newList[newItemPosition]
                return old.userId == new.userId && old.friendId == new.friendId
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newList[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items.clear()
        items.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], myUserId)
    override fun getItemCount() = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvInitials = view.findViewById<TextView>(R.id.tvInitials)
        private val tvName     = view.findViewById<TextView>(R.id.tvName)
        private val tvStatus   = view.findViewById<TextView>(R.id.tvStatus)

        fun bind(f: Friendship, myUserId: String) {
            val other = if (f.userId == myUserId) f.friend else f.user
            tvName.text     = other.nickname
            tvInitials.text = other.nickname.take(2).uppercase()
            tvStatus.text   = when (other.status) {
                "ONLINE"    -> "🟢"
                "AWAY"      -> "🟡"
                "BUSY"      -> "🔴"
                "INVISIBLE" -> "👻"
                else        -> "⚫"
            }
        }
    }
}
